package br.ufu.facom.ereno.attacks.uc06.creator;

import br.ufu.facom.ereno.config.AttackConfig;
import br.ufu.facom.ereno.general.ProtectionIED;
import br.ufu.facom.ereno.benign.uc00.creator.MessageCreator;
import br.ufu.facom.ereno.dataExtractors.GSVDatasetWriter;
import br.ufu.facom.ereno.general.IED;
import br.ufu.facom.ereno.messages.Goose;

import java.util.ArrayList;
import java.util.logging.Logger;

import static br.ufu.facom.ereno.general.IED.randomBetween;

/**
 * Configurable high-stNum injection creator (uc06).
 */
public class HighStNumInjectionCreatorC implements MessageCreator {

    private final ArrayList<Goose> legitimateMessages;
    private final AttackConfig config;

    public HighStNumInjectionCreatorC(ArrayList<Goose> legitimateMessages, AttackConfig config) {
        this.legitimateMessages = legitimateMessages;
        this.config = config;
    }

    @Override
    public void generate(IED ied, int numInstances) {
        int stMin = config.getRangeMinInt("jump", 5);
        int stMax = config.getRangeMaxInt("jump", 200);
        double sqnumResetProb = config.getProbability("sqnumResetProb", 0.0);
        int sqDeltaMin = config.getRangeMinInt("sqNumDelta", 0);
        int sqDeltaMax = Math.max(sqDeltaMin, config.getRangeMaxInt("sqNumDelta", 0));
        boolean randomizeTimestamp = config.getBoolean("randomizeTimestamp", false);
        // ttlOverride is nested {valuesMs:[...], prob:..}
        int[] ttlValuesMs = parseTtlValues();
        double ttlProb = config.getNestedProb("ttlOverride", "prob", 0.0);

        double minTs = legitimateMessages.get(0).getTimestamp();
        double maxTs = legitimateMessages.get(legitimateMessages.size() - 1).getTimestamp();
        boolean canRandomizeTs = randomizeTimestamp && maxTs > minTs;

        int emitted = 0;
        for (int i = 0; i <= numInstances; i++) {
            int idx = randomBetween(0, legitimateMessages.size() - 1);
            Goose g = legitimateMessages.get(idx).copy();
            g.setLabel(GSVDatasetWriter.label[6]);

            // 1) Elevated stNum (existing behavior)
            int stNum = randomBetween(stMin, stMax);
            g.setStNum(stNum);

            // 2) sqNum: either reset to 0 (with prob) or add a configured
            //    delta (legacy uc06 used [0,5]).
            if (sqnumResetProb > 0.0 && Math.random() < sqnumResetProb) {
                g.setSqNum(0);
            } else if (sqDeltaMax > 0) {
                g.setSqNum(g.getSqNum() + randomBetween(sqDeltaMin, sqDeltaMax));
            }

            // 3) Optional TTL override
            if (ttlValuesMs.length > 0 && ttlProb > 0.0 && Math.random() < ttlProb) {
                int pick = ttlValuesMs[randomBetween(0, ttlValuesMs.length - 1)];
                g.setGooseTimeAllowedtoLive(pick);
            }

            // 4) Optional timestamp randomization across the legitimate
            //    window (legacy uc06 behavior).
            if (canRandomizeTs) {
                g.setTimestamp(randomBetween(minTs, maxTs));
            }

            ProtectionIED h = (ProtectionIED) ied;
            h.addMessage(g.copy());
            emitted++;
        }

        final int emittedFinal = emitted;
        Logger.getLogger("HighStNumInjectionCreatorC").info(
                () -> "uc06 high-stnum injection: emitted=" + emittedFinal
                        + " jump=[" + stMin + "," + stMax + "]"
                        + " sqnumResetProb=" + sqnumResetProb
                        + " sqNumDelta=[" + sqDeltaMin + "," + sqDeltaMax + "]"
                        + " randomizeTimestamp=" + randomizeTimestamp
                        + " ttlProb=" + ttlProb
                        + " ttlValues=" + ttlValuesMs.length);
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
