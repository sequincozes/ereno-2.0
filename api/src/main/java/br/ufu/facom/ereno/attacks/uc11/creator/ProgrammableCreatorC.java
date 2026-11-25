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
 * Programmable attack creator (uc11): applies a declarative list of rules to
 * copies of legitimate GOOSE messages. This is the "behaviours outside the
 * catalogue" path — a new behaviour is DATA (rules in the attack config),
 * never a new Java class per behaviour.
 *
 * <p>Rules live under {@code "rules"} as a JSON OBJECT keyed by slot name
 * ({@code {"r0": {...}, "r1": {...}}}), not an array — the Python side models
 * config paths as dotted dict keys ({@code rules.r0.fraction}) and has no
 * array-index support, so a dict of slots keeps both sides on one path model.
 * Slots are applied in sorted key order for determinism.
 *
 * <p>Each rule is {@code {op, field, value, fraction, when}}:
 * <ul>
 *   <li>{@code op}: {@code set} | {@code add} | {@code scale} (mutation) or
 *       {@code drop} | {@code duplicate} (stream cardinality)
 *   <li>{@code field}: one of {@link #FIELD_ALLOWLIST}; read by the mutation
 *       ops only, ignored by {@code drop}/{@code duplicate}
 *   <li>{@code value}: the operand — the mutation operand, or the number of
 *       EXTRA copies for {@code duplicate}
 *   <li>{@code fraction}: probability in [0,1] that the rule fires
 *   <li>{@code when}: {@code {field, cmp, value}} — the rule only fires on
 *       messages where the condition holds ({@code cmp=always} = every
 *       message, the neutral default)
 * </ul>
 *
 * <h2>Timing is a mutation of the time fields, not an emission-order trick</h2>
 *
 * <p>The written stream is NOT in creator-emission order: messages leave
 * through a {@code PriorityQueue<EthernetFrame>} whose {@code compareTo} sorts
 * by {@code timestamp}, and the delta features ({@code timestampDiff},
 * {@code tDiff}, {@code delay}) are computed between consecutive messages in
 * THAT order. Buffering or swapping messages here would be erased by the
 * queue. So delaying a message is {@code add} on {@code timestamp} with a
 * positive operand, and reordering it is the same op with a negative one —
 * no dedicated {@code delay}/{@code reorder} op exists, nor could one work.
 * {@code t} and {@code timestamp} are in SECONDS (10 ms = {@code 0.01}).
 *
 * <p>{@code set} and {@code scale} are refused on the time fields: those are
 * absolute wall-clock values, and setting or scaling one relocates the message
 * to an arbitrary point of the capture rather than shifting it. Only
 * {@code add} carries a meaning there.
 *
 * <p>This is the last line of the guardrail: even though the Python side
 * validates the rules against the capability catalogue before they reach here,
 * an unknown field, op or comparator throws {@link IllegalArgumentException}
 * rather than being silently ignored — a generation with a bad rule fails
 * loudly.
 */
public class ProgrammableCreatorC implements MessageCreator {

    // Campos que o creator sabe ler e escrever. Manter em sincronia com o
    // catálogo de capacidades do lado Python (config/capabilities/programmable).
    private static final String[] FIELD_ALLOWLIST =
            {"cbStatus", "stNum", "sqNum", "gooseTimeAllowedtoLive", "confRev", "t", "timestamp"};

    /** Campos de tempo: valor absoluto em segundos, só `add` faz sentido. */
    private static final String[] TIME_FIELDS = {"t", "timestamp"};

    /** Teto de cópias extras por regra `duplicate` — evita explodir o trace. */
    private static final int MAX_EXTRA_COPIES = 10;

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
        int dropped = 0;
        for (int i = 0; i <= numInstances; i++) {
            int idx = IED.randomBetween(0, legitimateMessages.size() - 1);
            Goose g = legitimateMessages.get(idx).copy();
            g.setLabel(Labels.LABELS[10]); // "programmable"

            // A seleção de TODAS as regras é avaliada contra a mensagem
            // original: qual mensagem uma regra mira não pode depender do que
            // o slot anterior já mutou, ou a ordem dos slots viraria parte do
            // comportamento. As mutações, essas sim, encadeiam em ordem.
            Goose original = g.copy();

            boolean drop = false;
            int extraCopies = 0;
            for (JsonObject rule : rules.values()) {
                if (!conditionHolds(original, rule)) {
                    continue;
                }
                double fraction = rule.has("fraction") ? rule.get("fraction").getAsDouble() : 1.0;
                if (ConfigLoader.RNG.nextDouble() >= fraction) {
                    continue;
                }
                String op = rule.has("op") ? rule.get("op").getAsString() : null;
                if ("drop".equals(op)) {
                    drop = true;
                    break; // nada mais importa: a mensagem não existe
                }
                if ("duplicate".equals(op)) {
                    extraCopies += readExtraCopies(rule);
                    continue;
                }
                applyMutation(g, rule);
            }

            if (drop) {
                dropped++;
                continue;
            }
            for (int copy = 0; copy <= extraCopies; copy++) {
                ((ProtectionIED) ied).addMessage(g.copy());
                emitted++;
            }
        }

        final int emittedFinal = emitted;
        final int droppedFinal = dropped;
        final int ruleCount = rules.size();
        Logger.getLogger("ProgrammableCreatorC").info(
                () -> "Programmable attack: emitted=" + emittedFinal
                        + " dropped=" + droppedFinal
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

    /**
     * A regra mira esta mensagem? Sem bloco {@code when}, mira todas — é o
     * comportamento anterior à seleção, preservado para configs antigas.
     */
    private static boolean conditionHolds(Goose original, JsonObject rule) {
        if (!rule.has("when") || !rule.get("when").isJsonObject()) {
            return true;
        }
        JsonObject when = rule.getAsJsonObject("when");
        String cmp = when.has("cmp") ? when.get("cmp").getAsString() : "always";
        if ("always".equals(cmp)) {
            return true;
        }
        String field = when.has("field") ? when.get("field").getAsString() : null;
        double observed = readField(original, field);
        double expected = when.has("value") ? when.get("value").getAsDouble() : 0.0;
        switch (cmp) {
            case "eq": return observed == expected;
            case "ne": return observed != expected;
            case "gt": return observed > expected;
            case "lt": return observed < expected;
            case "gte": return observed >= expected;
            case "lte": return observed <= expected;
            default:
                throw new IllegalArgumentException(
                        "Unknown programmable comparator: " + cmp
                                + " (allowed: always, eq, ne, gt, lt, gte, lte)");
        }
    }

    /** Cópias extras de uma regra {@code duplicate}, validadas no limite. */
    private static int readExtraCopies(JsonObject rule) {
        double value = rule.has("value") ? rule.get("value").getAsDouble() : 0.0;
        int copies = (int) Math.round(value);
        if (copies < 1 || copies > MAX_EXTRA_COPIES) {
            throw new IllegalArgumentException(
                    "Programmable 'duplicate' needs 'value' extra copies in [1, "
                            + MAX_EXTRA_COPIES + "], got: " + value);
        }
        return copies;
    }

    private static void applyMutation(Goose g, JsonObject rule) {
        String field = rule.has("field") ? rule.get("field").getAsString() : null;
        String op = rule.has("op") ? rule.get("op").getAsString() : null;
        double value = rule.has("value") ? rule.get("value").getAsDouble() : 0.0;

        if (isTimeField(field) && !"add".equals(op)) {
            throw new IllegalArgumentException(
                    "Programmable op '" + op + "' is not allowed on the absolute time field '"
                            + field + "' - only 'add' (a signed shift in seconds) is.");
        }

        double current = readField(g, field);
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
        writeField(g, field, mutated);
    }

    private static boolean isTimeField(String field) {
        for (String timeField : TIME_FIELDS) {
            if (timeField.equals(field)) {
                return true;
            }
        }
        return false;
    }

    private static double readField(Goose g, String field) {
        if (field == null) {
            throw new IllegalArgumentException("Programmable rule has no 'field'.");
        }
        switch (field) {
            case "cbStatus": return g.getCbStatus();
            case "stNum": return g.getStNum();
            case "sqNum": return g.getSqNum();
            case "gooseTimeAllowedtoLive": return g.getGooseTimeAllowedtoLive();
            case "confRev": return g.getConfRev();
            case "t": return g.getT();
            case "timestamp": return g.getTimestamp();
            default:
                throw new IllegalArgumentException(
                        "Unknown programmable field: " + field + " (allowed: "
                                + String.join(", ", FIELD_ALLOWLIST) + ")");
        }
    }

    /** Campos inteiros arredondam; os de tempo guardam a precisão em segundos. */
    private static void writeField(Goose g, String field, double value) {
        switch (field) {
            case "cbStatus": g.setCbStatus((int) Math.round(value)); break;
            case "stNum": g.setStNum((int) Math.round(value)); break;
            case "sqNum": g.setSqNum((int) Math.round(value)); break;
            case "gooseTimeAllowedtoLive": g.setGooseTimeAllowedtoLive((int) Math.round(value)); break;
            case "confRev": g.setConfRev((int) Math.round(value)); break;
            case "t": g.setT(value); break;
            case "timestamp": g.setTimestamp(value); break;
            default:
                throw new IllegalArgumentException("Unknown programmable field: " + field);
        }
    }
}
