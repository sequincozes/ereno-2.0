package br.ufu.facom.ereno.attacks.uc03.creator;

import br.ufu.facom.ereno.config.AttackConfig;
import br.ufu.facom.ereno.general.ProtectionIED;
import br.ufu.facom.ereno.benign.uc00.creator.MessageCreator;
import br.ufu.facom.ereno.dataExtractors.GSVDatasetWriter;
import br.ufu.facom.ereno.general.IED;
import br.ufu.facom.ereno.messages.Goose;

import java.util.ArrayList;
import java.util.logging.Logger;

import static br.ufu.facom.ereno.general.IED.randomBetween;

/** Configurable masquerade fake fault creator (uc03) */
public class MasqueradeFakeFaultCreatorC implements MessageCreator {

    private static final int SV_PREFIX_COLUMNS = 19;
    private static final int ANALOG_FIRST = 1;
    private static final int ANALOG_LAST = 6;
    private static final int TRAP_FIRST = 13;
    private static final int TRAP_LAST = 18;

    private final ArrayList<Goose> legitimateMessages;
    private final AttackConfig config;

    public MasqueradeFakeFaultCreatorC(ArrayList<Goose> legitimateMessages, AttackConfig config) {
        this.legitimateMessages = legitimateMessages;
        this.config = config;
    }

    @Override
    public void generate(IED ied, int numInstances) {
        // Resolve config knobs once
        double faultProbability = config.getNestedProb("fault", "prob", 0.6);
        int delayMin = config.getNestedRangeMinInt("fault", "durationMs", 50);
        int delayMax = config.getNestedRangeMaxInt("fault", "durationMs", 800);

        boolean hasCbStatus = config.getRaw().has("cbStatus");
        int cbStatusOnFault = config.getInt("cbStatus", 1);
        boolean incrementStNum = config.getBoolean("incrementStNumOnFault", true);
        String sqnumMode = config.getString("sqnumMode", "fast");
        int[] ttlMsValues = config.getIntArray("ttlMsValues", new int[0]);

        double analogDeltaMin = config.getNestedRangeMin("analog", "deltaAbs", 0.0);
        double analogDeltaMax = config.getNestedRangeMax("analog", "deltaAbs", 0.0);
        boolean perturbAnalog = config.hasObject("analog") && analogDeltaMax > 0.0;

        double trapMultMin = config.getNestedRangeMin("trapArea", "multiplier", 1.0);
        double trapMultMax = config.getNestedRangeMax("trapArea", "multiplier", 1.0);
        double trapSpikeProb = config.getNestedProb("trapArea", "spikeProb", 0.0);
        boolean perturbTrap = config.hasObject("trapArea") && (trapMultMax > 1.0 || trapSpikeProb > 0.0);

        int emitted = 0;
        int skipped = 0;
        for (int i = 0; i <= numInstances; i++) {
            int idx = randomBetween(0, legitimateMessages.size() - 1);
            Goose g = legitimateMessages.get(idx).copy();
            g.setLabel(GSVDatasetWriter.label[3]); // masquerade fake fault

            if (Math.random() >= faultProbability) {
                skipped++;
                continue;
            }

            // 1) Network-delay component (already in original implementation).
            int delayMs = randomBetween(delayMin, delayMax);
            g.setTimestamp(g.getTimestamp() + (delayMs / 1000.0));

            // 2) Flip circuit-breaker status to the configured "fault" value
            //    so the row no longer matches the periodic cbStatus the
            //    benign IED publishes.
            if (hasCbStatus) {
                g.setCbStatus(cbStatusOnFault);
            }

            // 3) Bump stNum (an IEC 61850 status change) and reset sqNum so
            //    the burst that follows looks like a fresh state event.
            if (incrementStNum) {
                g.setStNum(g.getStNum() + 1);
                if ("fast".equalsIgnoreCase(sqnumMode)) {
                    g.setSqNum(0);
                } else {
                    g.setSqNum(g.getSqNum() + 1);
                }
                // The "t" field marks the timestamp of the last status
                // change; align it with the (delayed) message timestamp.
                g.setT(g.getTimestamp());
            }

            // 4) Pick a TTL value from the configured set, if any.
            if (ttlMsValues.length > 0) {
                int pick = ttlMsValues[randomBetween(0, ttlMsValues.length - 1)];
                g.setGooseTimeAllowedtoLive(pick);
            }

            // 5) Perturb the SV prefix (analog samples + trap-area sums) so
            //    the 19 leading dataset columns reflect the fake fault.
            String svPrefix = g.getSvPrefixCsv();
            if (svPrefix != null && (perturbAnalog || perturbTrap)) {
                String mutated = perturbSvPrefix(svPrefix,
                        perturbAnalog, analogDeltaMin, analogDeltaMax,
                        perturbTrap, trapMultMin, trapMultMax, trapSpikeProb);
                if (mutated != null) {
                    g.setSvPrefixCsv(mutated);
                }
            }

            ProtectionIED mf = (ProtectionIED) ied;
            mf.addMessage(g.copy());
            emitted++;
        }

        final int emittedFinal = emitted;
        final int skippedFinal = skipped;
        Logger.getLogger("MasqueradeFakeFaultCreatorC").info(
                () -> "Masquerade fake fault: emitted=" + emittedFinal
                        + " skipped(prob)=" + skippedFinal);
    }

    /**
     * Mutate the analog and/or trap-area cells of an SV prefix CSV row.
     * Returns {@code null} if the prefix doesn't have the expected 19-column
     * shape (in which case the caller should keep the original).
     */
    static String perturbSvPrefix(String svPrefix,
                                  boolean perturbAnalog, double analogMin, double analogMax,
                                  boolean perturbTrap, double trapMin, double trapMax,
                                  double spikeProb) {
        String[] cells = svPrefix.split(",", -1);
        if (cells.length != SV_PREFIX_COLUMNS) {
            return null;
        }

        if (perturbAnalog) {
            for (int i = ANALOG_FIRST; i <= ANALOG_LAST; i++) {
                Double v = parseDoubleOrNull(cells[i]);
                if (v == null) continue;
                double delta = analogMin + (analogMax - analogMin) * Math.random();
                if (Math.random() < 0.5) delta = -delta;
                cells[i] = Double.toString(v + delta);
            }
        }

        if (perturbTrap) {
            for (int i = TRAP_FIRST; i <= TRAP_LAST; i++) {
                Double v = parseDoubleOrNull(cells[i]);
                if (v == null) continue;
                double mult = trapMin + (trapMax - trapMin) * Math.random();
                double mutated = v * mult;
                if (Math.random() < spikeProb) {
                    // Sign-flip the spike half the time so trap sums can
                    // swing either way around the periodic baseline.
                    mutated += (Math.random() < 0.5 ? 1.0 : -1.0) * Math.abs(v) * trapMax;
                }
                cells[i] = Double.toString(mutated);
            }
        }

        return String.join(",", cells);
    }

    private static Double parseDoubleOrNull(String s) {
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException | NullPointerException e) {
            return null;
        }
    }
}
