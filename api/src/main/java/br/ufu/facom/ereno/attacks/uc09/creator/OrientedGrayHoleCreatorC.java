package br.ufu.facom.ereno.attacks.uc09.creator;

import java.util.ArrayList;
import java.util.Locale;
import java.util.logging.Logger;

import br.ufu.facom.ereno.benign.uc00.creator.MessageCreator;
import br.ufu.facom.ereno.config.AttackConfig;
import br.ufu.facom.ereno.config.ConfigLoader;
import br.ufu.facom.ereno.general.IED;
import br.ufu.facom.ereno.general.ProtectionIED;
import br.ufu.facom.ereno.messages.Goose;
import br.ufu.facom.ereno.util.Labels;

/**
 * Oriented grayhole (uc09), configurable. Unlike the uc08 grayhole, which
 * drops messages anywhere in the stream, this one only acts right after a
 * status change (stNum differs from the previous message): the burst of
 * retransmissions that carries the new state is what gets dropped, so the
 * subscriber misses or learns late about the event.
 *
 * <p>Ported from the hard-coded OrientedGrayHoleCreator on dev-carlos
 * (discardRate=15, toDiscardPackets=5, RANDOMIC_MESSAGE). Approaches:
 * <ul>
 *   <li>{@code RANDOMIC_MESSAGE}: after a change, each of the next
 *       {@code burstLen} messages is dropped with probability {@code discardProb}.
 *   <li>{@code RANDOMIC_BURST}: after a change, with probability
 *       {@code discardProb} the whole burst of {@code burstLen} messages is dropped.
 *   <li>{@code DETERMINISTIC_BURST}: after every change the whole burst is dropped.
 * </ul>
 * The burst starts at the changed message itself. Every survivor is emitted
 * as a copy labelled oriented_grayhole, as uc08 does, until the segment holds
 * {@code targetMessageCount} messages. All randomness goes through
 * {@link ConfigLoader#RNG}, so a seed reproduces the segment.
 */
public class OrientedGrayHoleCreatorC implements MessageCreator {

    public enum Approach { RANDOMIC_MESSAGE, RANDOMIC_BURST, DETERMINISTIC_BURST }

    private static final Logger LOGGER = Logger.getLogger(OrientedGrayHoleCreatorC.class.getName());

    private final ArrayList<Goose> legitimateMessages;
    private final AttackConfig config;

    public OrientedGrayHoleCreatorC(ArrayList<Goose> legitimateMessages, AttackConfig config) {
        this.legitimateMessages = legitimateMessages;
        this.config = config;
    }

    /** Parses the approach name; unknown values fail loudly rather than silently changing the attack. */
    public static Approach parseApproach(String name) {
        try {
            return Approach.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException("Unknown oriented grayhole approach: " + name
                    + " (expected RANDOMIC_MESSAGE, RANDOMIC_BURST or DETERMINISTIC_BURST)");
        }
    }

    @Override
    public void generate(IED ied, int targetMessageCount) {
        Approach approach = parseApproach(config.getString("approach", "RANDOMIC_MESSAGE"));
        double discardProb = config.getProbability("discardProb", 0.15);
        int burstMin = config.getRangeMinInt("burstLen", 5);
        int burstMax = config.getRangeMaxInt("burstLen", 5);
        if (burstMin < 1 || burstMax < burstMin) {
            throw new IllegalArgumentException("burstLen must satisfy 1 <= min <= max, got " + burstMin + ".." + burstMax);
        }

        ProtectionIED out = (ProtectionIED) ied;
        int added = 0;
        int dropped = 0;
        int changes = 0;
        // Remaining messages of the current post-change window, and whether
        // the whole window was already decided to be dropped (burst approaches).
        int windowLeft = 0;
        boolean dropWholeWindow = false;

        for (int i = 0; i < legitimateMessages.size() && added < targetMessageCount; i++) {
            Goose goose = legitimateMessages.get(i);

            boolean statusChange = i > 0 && goose.getStNum() != legitimateMessages.get(i - 1).getStNum();
            if (statusChange && windowLeft == 0) {
                changes++;
                windowLeft = IED.randomBetween(burstMin, burstMax);
                switch (approach) {
                    case DETERMINISTIC_BURST:
                        dropWholeWindow = true;
                        break;
                    case RANDOMIC_BURST:
                        dropWholeWindow = ConfigLoader.RNG.nextDouble() < discardProb;
                        break;
                    default:
                        dropWholeWindow = false;
                }
            }

            boolean drop = false;
            if (windowLeft > 0) {
                drop = approach == Approach.RANDOMIC_MESSAGE
                        ? ConfigLoader.RNG.nextDouble() < discardProb
                        : dropWholeWindow;
                windowLeft--;
            }

            if (drop) {
                dropped++;
                continue;
            }
            Goose survivor = goose.copy();
            survivor.setLabel(Labels.LABELS[11]); // "oriented_grayhole"
            out.addMessage(survivor);
            added++;
        }

        final int fChanges = changes, fDropped = dropped, fAdded = added;
        LOGGER.info(() -> "Oriented grayhole (" + approach + "): " + fChanges + " status changes, "
                + fDropped + " messages dropped, " + fAdded + " kept");
        if (changes == 0) {
            LOGGER.warning("No status change in the benign window: the segment has no dropped messages. "
                    + "Raise the benign faultProbability or the number of benign messages.");
        }
    }
}
