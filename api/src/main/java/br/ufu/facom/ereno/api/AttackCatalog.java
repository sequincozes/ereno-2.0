package br.ufu.facom.ereno.api;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * attackType -> use-case prefix ("uc01"...). The prefix is what
 * CreateAttackDatasetAction.getLabelForSegmentName reads to label a segment,
 * so the run planner names every segment "{uc}_{attackType}".
 *
 * Keep in sync with CreateAttackDatasetAction.createAttackDevice and
 * util.Labels when an attack is added.
 */
public final class AttackCatalog {

    private static final Map<String, String> UC_BY_TYPE;

    static {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("random_replay", "uc01");
        m.put("inverse_replay", "uc02");
        m.put("masquerade_fault", "uc03");
        m.put("masquerade_normal", "uc04");
        m.put("injection", "uc05");
        m.put("random_injection", "uc05");
        m.put("high_stnum_injection", "uc06");
        m.put("flooding", "uc07");
        m.put("high_rate_stnum_injection", "uc07");
        m.put("grayhole", "uc08");
        m.put("greyhole", "uc08");
        m.put("delayed_replay", "uc10");
        m.put("delayed_replay_batch_dump", "uc10");
        m.put("delayed_replay_backoff", "uc10");
        m.put("delayed_replay_double_drop", "uc10");
        m.put("programmable", "uc11");
        UC_BY_TYPE = Collections.unmodifiableMap(m);
    }

    private AttackCatalog() {}

    /** @return the use-case prefix, or null when the attackType is unknown. */
    public static String ucFor(String attackType) {
        if (attackType == null) return null;
        return UC_BY_TYPE.get(attackType.toLowerCase(Locale.ROOT));
    }

    public static Map<String, String> all() {
        return UC_BY_TYPE;
    }
}
