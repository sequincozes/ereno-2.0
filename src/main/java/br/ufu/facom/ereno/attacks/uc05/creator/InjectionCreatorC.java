package br.ufu.facom.ereno.attacks.uc05.creator;

import br.ufu.facom.ereno.config.AttackConfig;
import br.ufu.facom.ereno.general.ProtectionIED;
import br.ufu.facom.ereno.benign.uc00.creator.MessageCreator;
import br.ufu.facom.ereno.dataExtractors.GSVDatasetWriter;
import br.ufu.facom.ereno.general.IED;
import br.ufu.facom.ereno.messages.Goose;

import java.util.ArrayList;
import java.util.Random;
import java.util.logging.Logger;

import static br.ufu.facom.ereno.general.IED.randomBetween;

/** Configurable injection creator (uc05) */
public class InjectionCreatorC implements MessageCreator {

    private final ArrayList<Goose> legitimateMessages;
    private final AttackConfig config;
    private final Random rng; // null => fall back to Math.random()/IED.randomBetween

    public InjectionCreatorC(ArrayList<Goose> legitimateMessages, AttackConfig config) {
        this.legitimateMessages = legitimateMessages;
        this.config = config;
        this.rng = config.getRaw().has("randomSeed")
                ? new Random(config.getInt("randomSeed", 0))
                : null;
    }

    @Override
    public void generate(IED ied, int numInstances) {
        int configCap = config.getInt("numInjectedMessages", Integer.MAX_VALUE);
        int total = Math.min(numInstances, configCap);
        String pattern = config.getString("injectionPattern", "random").toLowerCase();
        int legitSize = legitimateMessages.size();
        if (legitSize <= 0) {
            return;
        }

        ProtectionIED injector = (ProtectionIED) ied;
        int emitted = 0;

        switch (pattern) {
            case "uniform": {
                // Evenly stride through the legitimate window; this produces
                // injections that are temporally spread instead of bunched.
                for (int i = 0; i < total; i++) {
                    int idx = (int) Math.floor((double) i * legitSize / Math.max(1, total));
                    if (idx >= legitSize) idx = legitSize - 1;
                    injector.addMessage(makeClonedInjection(idx));
                    emitted++;
                }
                break;
            }
            case "burst": {
                // Cluster injections around random anchor indices. Burst length
                // is small (3..7 by default) so a few hot zones are produced.
                int burstMin = 3;
                int burstMax = 7;
                while (emitted < total) {
                    int anchor = nextInt(legitSize);
                    int burstLen = Math.min(total - emitted, burstMin + nextInt(burstMax - burstMin + 1));
                    for (int b = 0; b < burstLen; b++) {
                        int idx = Math.min(legitSize - 1, anchor + b);
                        injector.addMessage(makeClonedInjection(idx));
                        emitted++;
                    }
                }
                break;
            }
            case "synthetic": {
                // Legacy-equivalent mode: build brand-new GOOSE messages
                // with randomized fields drawn from the configured ranges.
                int stMin = config.getRangeMinInt("stNum", 0);
                int stMax = config.getRangeMaxInt("stNum", 100);
                int sqMin = config.getRangeMinInt("sqNum", 0);
                int sqMax = config.getRangeMaxInt("sqNum", 100);
                int ttlMin = config.getRangeMinInt("ttlMs", 10000);
                int ttlMax = config.getRangeMaxInt("ttlMs", 12000);
                int confMin = config.getRangeMinInt("confRev", 0);
                int confMax = config.getRangeMaxInt("confRev", 100);
                int[] cbValues = parseCbStatusValues();

                double minTs = legitimateMessages.get(0).getTimestamp();
                double maxTs = legitimateMessages.get(legitSize - 1).getTimestamp();
                if (minTs >= maxTs) {
                    throw new IllegalArgumentException(
                            "Cannot build synthetic injections: legitimate timestamps are not strictly increasing"
                                    + " (min=" + minTs + " max=" + maxTs + ")");
                }

                for (int i = 0; i < total; i++) {
                    double timestamp = randomBetween(minTs, maxTs);
                    double t = randomBetween(minTs, maxTs);
                    int stNum = randomBetween(stMin, stMax);
                    int sqNum = randomBetween(sqMin, sqMax);
                    int cbStatus = cbValues[nextInt(cbValues.length)];
                    int ttl = randomBetween(ttlMin, ttlMax);
                    int confRev = randomBetween(confMin, confMax);

                    Goose synthetic = new Goose(cbStatus, stNum, sqNum, timestamp, t,
                            GSVDatasetWriter.label[5]);
                    synthetic.setConfRev(confRev);
                    synthetic.setGooseTimeAllowedtoLive(ttl);
                    // Borrow the SV prefix of the time-nearest legitimate
                    // message so the 19 SV columns aren't blank.
                    synthetic.setSvPrefixCsv(nearestSvPrefix(timestamp));
                    injector.addMessage(synthetic);
                    emitted++;
                }
                break;
            }
            case "random":
            default: {
                for (int i = 0; i < total; i++) {
                    injector.addMessage(makeClonedInjection(nextInt(legitSize)));
                    emitted++;
                }
                break;
            }
        }

        final int emittedFinal = emitted;
        Logger.getLogger("InjectionCreatorC").info(
                () -> "uc05 injection: pattern=" + pattern + " emitted=" + emittedFinal
                        + " (cap=" + configCap + ", numInstances=" + numInstances + ")");
    }

    private Goose makeClonedInjection(int legitIndex) {
        Goose g = legitimateMessages.get(legitIndex).copy();
        g.setLabel(GSVDatasetWriter.label[5]);
        return g;
    }

    /**
     * Pick the nearest legitimate message by timestamp and return its
     * SV prefix CSV (for synthetic injections that don't have one of their own).
     */
    private String nearestSvPrefix(double timestamp) {
        Goose best = null;
        double bestDelta = Double.POSITIVE_INFINITY;
        for (Goose g : legitimateMessages) {
            if (g.getSvPrefixCsv() == null) continue;
            double d = Math.abs(g.getTimestamp() - timestamp);
            if (d < bestDelta) {
                bestDelta = d;
                best = g;
            }
        }
        return best != null ? best.getSvPrefixCsv() : null;
    }

    private int[] parseCbStatusValues() {
        if (config.hasObject("cbStatus")) {
            com.google.gson.JsonObject obj = config.getObject("cbStatus");
            if (obj.has("values") && obj.get("values").isJsonArray()) {
                com.google.gson.JsonArray arr = obj.getAsJsonArray("values");
                int[] out = new int[arr.size()];
                for (int i = 0; i < arr.size(); i++) out[i] = arr.get(i).getAsInt();
                if (out.length > 0) return out;
            }
        }
        return new int[]{0, 1};
    }

    private int nextInt(int boundExclusive) {
        if (boundExclusive <= 0) return 0;
        if (rng != null) return rng.nextInt(boundExclusive);
        return randomBetween(0, boundExclusive - 1);
    }
}
