package br.ufu.facom.ereno.attacks.uc11.creator;

import br.ufu.facom.ereno.benign.uc00.creator.MessageCreator;
import br.ufu.facom.ereno.config.AttackConfig;
import br.ufu.facom.ereno.config.ConfigLoader;
import br.ufu.facom.ereno.general.IED;
import br.ufu.facom.ereno.general.ProtectionIED;
import br.ufu.facom.ereno.messages.Goose;
import br.ufu.facom.ereno.util.Labels;

import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;

/**
 * Programmable attack creator (uc11): applies a declarative list of mutation
 * rules to copies of legitimate GOOSE messages. This is the "behaviours outside
 * the catalogue" path (Phase 2.1) — a new behaviour is DATA (a rules array in
 * the attack config), never a new Java class per behaviour.
 *
 * <p>Rules live under {@code "rules"} as a JSON OBJECT keyed by slot name
 * ({@code {"r0": {...}, "r1": {...}}}), not an array — the Python side models
 * config paths as dotted dict keys ({@code rules.r0.fraction}) and has no
 * array-index support, so a dict of slots keeps both sides on one path model.
 * Slots are applied in sorted key order for determinism.
 *
 * <p>Scope of this first cut: mutation only. Each rule is
 * {@code {op, field, value, fraction}}:
 * <ul>
 *   <li>{@code op}: {@code set} | {@code add} | {@code scale}
 *   <li>{@code field}: one of the integer GOOSE fields this creator can touch
 *       (see {@link #FIELD_ALLOWLIST})
 *   <li>{@code value}: the operand (a number)
 *   <li>{@code fraction}: probability in [0,1] that the rule fires on a message
 * </ul>
 *
 * <p>This is the last line of the guardrail: even though the Python side
 * validates the rules against the capability catalogue before they reach here,
 * an unknown field or op throws {@link IllegalArgumentException} rather than
 * being silently ignored — a generation with a bad rule fails loudly.
 */
public class ProgrammableCreatorC implements MessageCreator {

    // Só campos inteiros com getter/setter em Goose. Manter em sincronia com o
    // catálogo de capacidades do lado Python (config/capabilities/programmable).
    private static final String[] FIELD_ALLOWLIST =
            {"cbStatus", "stNum", "sqNum", "gooseTimeAllowedtoLive", "confRev"};

    private final ArrayList<Goose> legitimateMessages;
    private final AttackConfig config;

    public ProgrammableCreatorC(ArrayList<Goose> legitimateMessages, AttackConfig config) {
        this.legitimateMessages = legitimateMessages;
        this.config = config;
    }

    @Override
    public void generate(IED ied, int numInstances) {
        TreeMap<String, JsonObject> rules = resolveRules();

        int emitted = 0;
        for (int i = 0; i <= numInstances; i++) {
            int idx = IED.randomBetween(0, legitimateMessages.size() - 1);
            Goose g = legitimateMessages.get(idx).copy();
            g.setLabel(Labels.LABELS[10]); // "programmable"

            for (JsonObject rule : rules.values()) {
                double fraction = rule.has("fraction") ? rule.get("fraction").getAsDouble() : 1.0;
                if (ConfigLoader.RNG.nextDouble() < fraction) {
                    applyRule(g, rule);
                }
            }

            ((ProtectionIED) ied).addMessage(g.copy());
            emitted++;
        }

        final int emittedFinal = emitted;
        final int ruleCount = rules.size();
        Logger.getLogger("ProgrammableCreatorC").info(
                () -> "Programmable attack: emitted=" + emittedFinal
                        + " over " + ruleCount + " rule(s)");
    }

    /** Slots ordenados por chave (determinismo), cada um um objeto de regra. */
    private TreeMap<String, JsonObject> resolveRules() {
        JsonObject raw = config.getRaw();
        if (!raw.has("rules") || !raw.get("rules").isJsonObject()) {
            throw new IllegalArgumentException(
                    "Programmable attack config has no 'rules' object.");
        }
        TreeMap<String, JsonObject> rules = new TreeMap<>();
        for (Map.Entry<String, com.google.gson.JsonElement> e
                : raw.getAsJsonObject("rules").entrySet()) {
            rules.put(e.getKey(), e.getValue().getAsJsonObject());
        }
        if (rules.isEmpty()) {
            throw new IllegalArgumentException(
                    "Programmable attack config has an empty 'rules' object.");
        }
        return rules;
    }

    private static void applyRule(Goose g, JsonObject rule) {
        String field = rule.has("field") ? rule.get("field").getAsString() : null;
        String op = rule.has("op") ? rule.get("op").getAsString() : null;
        double value = rule.has("value") ? rule.get("value").getAsDouble() : 0.0;

        int current = readField(g, field);
        double mutated;
        if ("set".equals(op)) {
            mutated = value;
        } else if ("add".equals(op)) {
            mutated = current + value;
        } else if ("scale".equals(op)) {
            mutated = current * value;
        } else {
            throw new IllegalArgumentException("Unknown programmable op: " + op);
        }
        writeField(g, field, (int) Math.round(mutated));
    }

    private static int readField(Goose g, String field) {
        if (field == null) {
            throw new IllegalArgumentException("Programmable rule has no 'field'.");
        }
        switch (field) {
            case "cbStatus": return g.getCbStatus();
            case "stNum": return g.getStNum();
            case "sqNum": return g.getSqNum();
            case "gooseTimeAllowedtoLive": return g.getGooseTimeAllowedtoLive();
            case "confRev": return g.getConfRev();
            default:
                throw new IllegalArgumentException(
                        "Unknown programmable field: " + field + " (allowed: "
                                + String.join(", ", FIELD_ALLOWLIST) + ")");
        }
    }

    private static void writeField(Goose g, String field, int value) {
        switch (field) {
            case "cbStatus": g.setCbStatus(value); break;
            case "stNum": g.setStNum(value); break;
            case "sqNum": g.setSqNum(value); break;
            case "gooseTimeAllowedtoLive": g.setGooseTimeAllowedtoLive(value); break;
            case "confRev": g.setConfRev(value); break;
            default:
                throw new IllegalArgumentException("Unknown programmable field: " + field);
        }
    }
}
