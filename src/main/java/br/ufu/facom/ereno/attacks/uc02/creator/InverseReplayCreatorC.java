package br.ufu.facom.ereno.attacks.uc02.creator;

import br.ufu.facom.ereno.config.AttackConfig;
import br.ufu.facom.ereno.general.ProtectionIED;
import br.ufu.facom.ereno.dataExtractors.GSVDatasetWriter;
import br.ufu.facom.ereno.benign.uc00.creator.MessageCreator;
import br.ufu.facom.ereno.general.IED;
import br.ufu.facom.ereno.messages.Goose;

import java.util.ArrayList;
import java.util.logging.Logger;

import static br.ufu.facom.ereno.general.IED.randomBetween;

/**
 * Configurable inverse replay creator (uc02).
 *
 * <p>Honors the following knobs from
 * {@code config/attacks/uc02_inverse_replay.json}:
 * <ul>
 *   <li>{@code delayMs.{min,max}} – attacker latency injected on the replay
 *       (already supported).</li>
 *   <li>{@code blockLen.{min,max}} – on each detected status change, replay
 *       a block of {@code blockLen} consecutive legitimate messages instead
 *       of a single one. Defaults to a block of length 1 (legacy behavior).</li>
 *   <li>{@code burst.{prob,min,max,gapMs.{min,max}}} – with the configured
 *       probability, append an extra burst of replays separated by gaps
 *       sampled from {@code gapMs}.</li>
 *   <li>{@code ttlOverride.{valuesMs,prob}} – with the configured probability,
 *       overwrite the replayed message's {@code gooseTimeAllowedtoLive} with
 *       a randomly chosen entry from {@code valuesMs}.</li>
 * </ul>
 */
public class InverseReplayCreatorC implements MessageCreator {

    private final ArrayList<Goose> legitimateMessages;
    private final AttackConfig config;

    public InverseReplayCreatorC(ArrayList<Goose> legitimateMessages, AttackConfig config) {
        this.legitimateMessages = legitimateMessages;
        this.config = config;
    }

    @Override
    public void generate(IED ied, int numReplayInstances) {
        ProtectionIED victim = (ProtectionIED) ied;

        double minMs = config.getDelayMinMs(300);
        double maxMs = config.getDelayMaxMs(5000);

        int blockMin = Math.max(1, config.getNestedRangeMinInt("blockLen", "min", 1));
        int blockMax = Math.max(blockMin, config.getNestedRangeMaxInt("blockLen", "max", 1));

        boolean burstEnabled = config.hasObject("burst");
        double burstProb = config.getNestedProb("burst", "prob", 0.0);
        int burstMin = config.getNestedRangeMinInt("burst", "min", 0);
        int burstMax = config.getNestedRangeMaxInt("burst", "max", 0);
        double burstGapMinMs = config.getNestedRangeMin("burst", "gapMs", 0.0);
        double burstGapMaxMs = Math.max(burstGapMinMs,
                config.getNestedRangeMax("burst", "gapMs", burstGapMinMs));

        int[] ttlValuesMs = parseTtlValues();
        double ttlProb = config.getNestedProb("ttlOverride", "prob", 0.0);

        int emitted = 0;
        outer:
        for (int replayMessageIndex = 0; replayMessageIndex <= numReplayInstances; replayMessageIndex++) {

            int randomIndex = randomBetween(0, legitimateMessages.size() - 1);
            Goose seed = legitimateMessages.get(randomIndex).copy();
            seed.setLabel(GSVDatasetWriter.label[2]);

            for (int nextLegitimateIndex = randomIndex + 1; nextLegitimateIndex < legitimateMessages.size(); nextLegitimateIndex++) {
                Goose nextLegit = legitimateMessages.get(nextLegitimateIndex);
                if (seed.getCbStatus() == nextLegit.getCbStatus()) continue;

                // Status changed at nextLegitimateIndex. Replay a block of
                // up to blockLen messages anchored at the seed, each with
                // attacker delay applied relative to nextLegit's time.
                int blockLen = randomBetween(blockMin, blockMax);
                double anchor = nextLegit.getTimestamp();

                for (int b = 0; b < blockLen; b++) {
                    int srcIdx = Math.max(0, randomIndex - b); // walk backwards through legit history
                    Goose copy = legitimateMessages.get(srcIdx).copy();
                    copy.setLabel(GSVDatasetWriter.label[2]);

                    double attackerDelaySec = randomBetween((int) minMs, (int) maxMs) / 1000.0;
                    copy.setTimestamp(anchor + attackerDelaySec);

                    maybeApplyTtl(copy, ttlValuesMs, ttlProb);

                    if (victim.getNumberOfMessages() >= numReplayInstances) break outer;
                    victim.addMessage(copy);
                    emitted++;
                }

                // Optional burst suffix.
                if (burstEnabled && burstProb > 0.0 && Math.random() < burstProb && burstMax > 0) {
                    int burstLen = randomBetween(Math.max(1, burstMin), Math.max(1, burstMax));
                    double cursor = anchor + (randomBetween((int) minMs, (int) maxMs) / 1000.0);
                    for (int b = 0; b < burstLen; b++) {
                        Goose burstMsg = seed.copy();
                        double gapMs = (burstGapMaxMs > 0)
                                ? burstGapMinMs + Math.random() * (burstGapMaxMs - burstGapMinMs)
                                : 0.0;
                        cursor += gapMs / 1000.0;
                        burstMsg.setTimestamp(cursor);
                        maybeApplyTtl(burstMsg, ttlValuesMs, ttlProb);

                        if (victim.getNumberOfMessages() >= numReplayInstances) break outer;
                        victim.addMessage(burstMsg);
                        emitted++;
                    }
                }

                break; // move on to the next outer replay seed
            }
        }

        final int emittedFinal = emitted;
        Logger.getLogger("InverseReplayCreatorC").info(
                () -> "uc02 inverse replay: emitted=" + emittedFinal
                        + " block=[" + blockMin + "," + blockMax + "]"
                        + " burstProb=" + burstProb
                        + " ttlProb=" + ttlProb);
    }

    private static void maybeApplyTtl(Goose g, int[] values, double prob) {
        if (values.length == 0 || prob <= 0.0) return;
        if (Math.random() < prob) {
            int pick = values[randomBetween(0, values.length - 1)];
            g.setGooseTimeAllowedtoLive(pick);
        }
    }

    private int[] parseTtlValues() {
        if (!config.hasObject("ttlOverride")) return new int[0];
        com.google.gson.JsonObject obj = config.getObject("ttlOverride");
        if (!obj.has("valuesMs") || !obj.get("valuesMs").isJsonArray()) return new int[0];
        com.google.gson.JsonArray arr = obj.getAsJsonArray("valuesMs");
        int[] out = new int[arr.size()];
        for (int i = 0; i < arr.size(); i++) out[i] = arr.get(i).getAsInt();
        return out;
    }
}
