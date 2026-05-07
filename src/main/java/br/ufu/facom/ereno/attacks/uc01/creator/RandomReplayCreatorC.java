package br.ufu.facom.ereno.attacks.uc01.creator;

import java.util.ArrayList;
import java.util.logging.Logger;

import br.ufu.facom.ereno.benign.uc00.creator.MessageCreator;
import br.ufu.facom.ereno.config.AttackConfig;
import br.ufu.facom.ereno.dataExtractors.GSVDatasetWriter;
import br.ufu.facom.ereno.general.IED;
import static br.ufu.facom.ereno.general.IED.randomBetween;
import br.ufu.facom.ereno.messages.Goose;

/**
 * Configurable variant of RandomReplayCreator that reads parameters from attack config file
 */
public class RandomReplayCreatorC implements MessageCreator {
    ArrayList<Goose> legitimateMessages;
    private float timeTakenByAttacker = 1;
    private final AttackConfig config;

    public RandomReplayCreatorC(ArrayList<Goose> legitimateMessages, AttackConfig config) {
        this.legitimateMessages = legitimateMessages;
        this.config = config;
    }

    @Override
    public void generate(IED ied, int numReplayInstances) {

        // Burst / reorder / TTL knobs (resolved once)
        boolean burstEnabled = config.hasObject("burst");
        double burstProb = config.getNestedProb("burst", "prob", 0.0);
        int burstMin = config.getNestedRangeMinInt("burst", "min", 0);
        int burstMax = config.getNestedRangeMaxInt("burst", "max", 0);
        double burstGapMinMs = config.getNestedRangeMin("burst", "gapMs", 0.0);
        double burstGapMaxMs = Math.max(burstGapMinMs,
                config.getNestedRangeMax("burst", "gapMs", burstGapMinMs));

        double reorderProb = config.getProbability("reorderProb", 0.0);

        int[] ttlValuesMs = parseTtlValues();
        double ttlProb = config.getNestedProb("ttlOverride", "prob", 0.0);

        Goose previousMessage = legitimateMessages.get(legitimateMessages.size() - 1);

        int emitted = 0;
        int reorders = 0;
        int bursts = 0;
        for (int i = 0; i < numReplayInstances; i++) {
            // Resolve sampling window
            double windowMinS = config.getWindowMinS(2.0);
            double windowMaxS = config.getWindowMaxS(8.0);

            Goose lastLegitimate = legitimateMessages.get(legitimateMessages.size() - 1);
            double windowSeconds = randomBetween((int)(windowMinS * 1000), (int)(windowMaxS * 1000)) / 1000.0;
            double cutoffTime = lastLegitimate.getTimestamp() - windowSeconds;

            int startIndex = legitimateMessages.size() - 1;
            for (int j = legitimateMessages.size() - 1; j >= 0; j--) {
                if (legitimateMessages.get(j).getTimestamp() < cutoffTime) {
                    startIndex = j + 1;
                    break;
                }
            }
            startIndex = Math.max(0, startIndex);

            int randomIndex = randomBetween(startIndex, legitimateMessages.size() - 1);
            Goose randomGoose = legitimateMessages.get(randomIndex).copy();
            randomGoose.setLabel(GSVDatasetWriter.label[1]);

            // Adjust stNum based on cb-status delta vs previous message
            int stNumAdjustment;
            if (randomGoose.getCbStatus() != previousMessage.getCbStatus()) {
                stNumAdjustment = randomBetween(0, 1);
            } else {
                stNumAdjustment = 0;
            }
            randomGoose.setStNum(previousMessage.getStNum() + stNumAdjustment);

            int sqNumAdjustment = randomBetween(1, 3);
            randomGoose.setSqNum(previousMessage.getSqNum() + sqNumAdjustment);

            // Inter-message delay calibrated to recent legitimate cadence,
            // bounded by the configured delayMs range.
            float typicalDelay = 0.1f;
            if (legitimateMessages.size() >= 2) {
                int recentIdx = Math.max(0, legitimateMessages.size() - 10);
                double sumDelays = 0;
                int count = 0;
                for (int j = recentIdx + 1; j < legitimateMessages.size(); j++) {
                    sumDelays += legitimateMessages.get(j).getTimestamp() - legitimateMessages.get(j-1).getTimestamp();
                    count++;
                }
                if (count > 0) {
                    typicalDelay = (float)(sumDelays / count);
                }
            }

            double delayMinMs = config.getDelayMinMs(50);
            double delayMaxMs = config.getDelayMaxMs(500);

            float variation = typicalDelay * randomBetween(-30, 50) / 100.0f;
            float calculatedDelay = typicalDelay + variation;
            timeTakenByAttacker = Math.max((float)(delayMinMs/1000.0),
                                   Math.min((float)(delayMaxMs/1000.0), calculatedDelay));

            // Optional reorder: place this replay BEFORE the previous one to produce a negative timestampDiff. Otherwise advance forward.
            boolean doReorder = reorderProb > 0.0 && Math.random() < reorderProb;
            if (doReorder) {
                double backStep = (delayMinMs + Math.random() * Math.max(1.0, delayMaxMs - delayMinMs)) / 1000.0;
                randomGoose.setTimestamp(previousMessage.getTimestamp() - backStep);
                reorders++;
            } else {
                randomGoose.setTimestamp(previousMessage.getTimestamp() + timeTakenByAttacker);
            }

            // Maintain "t" (last status change) consistency
            if (randomGoose.getCbStatus() == previousMessage.getCbStatus()) {
                randomGoose.setT(previousMessage.getT());
            } else {
                randomGoose.setT(randomGoose.getTimestamp());
            }

            maybeApplyTtl(randomGoose, ttlValuesMs, ttlProb);

            ied.addMessage(randomGoose.copy());
            emitted++;
            previousMessage = randomGoose;

            // Optional burst suffix
            if (burstEnabled && burstProb > 0.0 && Math.random() < burstProb && burstMax > 0) {
                int burstLen = randomBetween(Math.max(1, burstMin), Math.max(1, burstMax));
                bursts++;
                double cursor = previousMessage.getTimestamp();
                for (int b = 0; b < burstLen && emitted < numReplayInstances; b++) {
                    Goose burstMsg = previousMessage.copy();
                    burstMsg.setLabel(GSVDatasetWriter.label[1]);
                    double gapMs = (burstGapMaxMs > 0)
                            ? burstGapMinMs + Math.random() * (burstGapMaxMs - burstGapMinMs)
                            : 0.0;
                    cursor += gapMs / 1000.0;
                    burstMsg.setTimestamp(cursor);
                    burstMsg.setSqNum(previousMessage.getSqNum() + 1);
                    maybeApplyTtl(burstMsg, ttlValuesMs, ttlProb);
                    ied.addMessage(burstMsg.copy());
                    emitted++;
                    previousMessage = burstMsg;
                    i++; // count burst replays toward numReplayInstances
                }
            }
        }

        final int emittedFinal = emitted;
        final int reordersFinal = reorders;
        final int burstsFinal = bursts;
        Logger.getLogger("RandomReplayCreatorC").info(
                () -> "uc01 random replay: emitted=" + emittedFinal
                        + " reorders=" + reordersFinal
                        + " bursts=" + burstsFinal
                        + " burstProb=" + burstProb
                        + " reorderProb=" + reorderProb
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
