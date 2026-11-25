package br.ufu.facom.ereno.api;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/**
 * Checks a RunSpec before it is queued, so a bad field is reported to the
 * client by name instead of failing minutes later inside a child process.
 * The core still re-checks what it reads; this is the friendly front door.
 */
public final class RunSpecValidator {

    public static final class FieldError {
        public final String field;
        public final String message;

        FieldError(String field, String message) {
            this.field = field;
            this.message = message;
        }
    }

    private static final Pattern MAC = Pattern.compile("^([0-9A-Fa-f]{2}[:-]){5}[0-9A-Fa-f]{2}$");
    private static final Pattern HEX = Pattern.compile("^(0x|0X)?[0-9A-Fa-f]{1,8}$");
    private static final Pattern RESOLUTION = Pattern.compile("^res\\d+$");
    private static final Set<String> FORMATS = Set.of("csv", "arff");
    private static final Set<String> SV_STRATEGIES = Set.of("DECIMATED", "UPSTREAM_FAITHFUL");

    // Mirrors ProgrammableCreatorC (uc11).
    private static final Set<String> PROG_OPS = Set.of("set", "add", "scale", "drop", "duplicate");
    private static final Set<String> PROG_FIELDS = Set.of(
            "cbStatus", "stNum", "sqNum", "gooseTimeAllowedtoLive", "confRev", "t", "timestamp");
    private static final Set<String> PROG_TIME_FIELDS = Set.of("t", "timestamp");
    private static final Set<String> PROG_CMPS = Set.of("always", "eq", "ne", "gt", "lt", "gte", "lte");

    static final int MAX_MESSAGES = 5_000_000;

    private final Path electricalSourcesRoot;

    public RunSpecValidator(Path electricalSourcesRoot) {
        this.electricalSourcesRoot = electricalSourcesRoot;
    }

    public List<FieldError> validate(RunSpec spec) {
        List<FieldError> errors = new ArrayList<>();
        if (spec == null) {
            errors.add(new FieldError("", "Empty run spec"));
            return errors;
        }
        if (spec.randomSeed == null) {
            errors.add(new FieldError("randomSeed", "Required, so the run can be reproduced"));
        }
        if (spec.name != null && spec.name.length() > 120) {
            errors.add(new FieldError("name", "At most 120 characters"));
        }

        if (spec.output == null || spec.output.format == null || !FORMATS.contains(spec.output.format)) {
            errors.add(new FieldError("output.format", "Must be csv or arff"));
        }

        validateBenign(spec.benign, errors);
        validateSetupIED(spec.setupIED, errors);
        validateGooseFlow(spec.gooseFlow, errors);
        validateStructure(spec.datasetStructure, errors);
        validateAttacks(spec.attacks, errors);
        return errors;
    }

    private void validateBenign(RunSpec.Benign b, List<FieldError> errors) {
        if (b == null) {
            errors.add(new FieldError("benign", "Required"));
            return;
        }
        intRange(errors, "benign.numberOfMessages", b.numberOfMessages, 1, MAX_MESSAGES);
        intRange(errors, "benign.faultProbability", b.faultProbability, 0, 100);
        if (b.svStrategy == null || !SV_STRATEGIES.contains(b.svStrategy)) {
            errors.add(new FieldError("benign.svStrategy", "Must be DECIMATED or UPSTREAM_FAITHFUL"));
        }
        if ("DECIMATED".equals(b.svStrategy)) {
            intRange(errors, "benign.svSamplesPerGoose", b.svSamplesPerGoose, 80, 4763);
        }
        if (b.electricalSources == null || !RESOLUTION.matcher(b.electricalSources).matches()) {
            errors.add(new FieldError("benign.electricalSources", "Must be a folder name like res10"));
        } else if (!Files.isDirectory(electricalSourcesRoot.resolve(b.electricalSources))) {
            errors.add(new FieldError("benign.electricalSources",
                    "No folder electrical-sources/" + b.electricalSources));
        }
    }

    private static void validateSetupIED(RunSpec.SetupIED s, List<FieldError> errors) {
        if (s == null) {
            errors.add(new FieldError("setupIED", "Required"));
            return;
        }
        required(errors, "setupIED.iedName", s.iedName);
        required(errors, "setupIED.gocbRef", s.gocbRef);
        required(errors, "setupIED.datSet", s.datSet);
        Double min = number(errors, "setupIED.minTime", s.minTime);
        Double max = number(errors, "setupIED.maxTime", s.maxTime);
        if (min != null && max != null && min > max) {
            errors.add(new FieldError("setupIED.maxTime", "Must be >= minTime"));
        }
        number(errors, "setupIED.timestamp", s.timestamp);
        number(errors, "setupIED.stNum", s.stNum);
        number(errors, "setupIED.sqNum", s.sqNum);
    }

    private static void validateGooseFlow(RunSpec.GooseFlow g, List<FieldError> errors) {
        if (g == null) {
            errors.add(new FieldError("gooseFlow", "Required"));
            return;
        }
        required(errors, "gooseFlow.goID", g.goID);
        pattern(errors, "gooseFlow.ethSrc", g.ethSrc, MAC, "Must be a MAC address like 01:0c:cd:01:2f:80");
        pattern(errors, "gooseFlow.ethDst", g.ethDst, MAC, "Must be a MAC address like 01:0c:cd:01:2f:81");
        pattern(errors, "gooseFlow.ethType", g.ethType, HEX, "Must be hexadecimal, e.g. 0x88B8");
        pattern(errors, "gooseFlow.gooseAppid", g.gooseAppid, HEX, "Must be hexadecimal, e.g. 0x00003001");
        pattern(errors, "gooseFlow.TPID", g.TPID, HEX, "Must be hexadecimal, e.g. 0x8100");
    }

    private static void validateStructure(RunSpec.DatasetStructure d, List<FieldError> errors) {
        if (d == null) {
            errors.add(new FieldError("datasetStructure", "Required"));
            return;
        }
        intRange(errors, "datasetStructure.messagesPerSegment", d.messagesPerSegment, 1, MAX_MESSAGES);
        if (d.benignMessagesPerSegment != null) {
            intRange(errors, "datasetStructure.benignMessagesPerSegment", d.benignMessagesPerSegment, 0, MAX_MESSAGES);
        }
    }

    private static void validateAttacks(List<RunSpec.Attack> attacks, List<FieldError> errors) {
        if (attacks == null || attacks.isEmpty()) {
            errors.add(new FieldError("attacks", "Add at least one attack"));
            return;
        }
        for (int i = 0; i < attacks.size(); i++) {
            String base = "attacks[" + i + "]";
            RunSpec.Attack a = attacks.get(i);
            if (a == null || a.config == null) {
                errors.add(new FieldError(base + ".config", "Required"));
                continue;
            }
            JsonObject cfg = a.config;
            String type = stringOrNull(cfg, "attackType");
            if (type == null) {
                errors.add(new FieldError(base + ".config.attackType", "Required"));
                continue;
            }
            if (AttackCatalog.ucFor(type) == null) {
                errors.add(new FieldError(base + ".config.attackType", "Unknown attack type: " + type));
                continue;
            }
            checkRanges(cfg, base + ".config", errors);
            if ("programmable".equalsIgnoreCase(type)) {
                validateProgrammable(cfg, base + ".config", errors);
            }
        }
    }

    /** Every {min, max} pair anywhere in the attack config must satisfy min <= max. */
    private static void checkRanges(JsonObject obj, String path, List<FieldError> errors) {
        if (obj.has("min") && obj.has("max") && isNumber(obj.get("min")) && isNumber(obj.get("max"))) {
            if (obj.get("min").getAsDouble() > obj.get("max").getAsDouble()) {
                errors.add(new FieldError(path + ".max", "Must be >= min"));
            }
        }
        for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
            String childPath = path + "." + e.getKey();
            JsonElement v = e.getValue();
            if (v.isJsonObject()) {
                checkRanges(v.getAsJsonObject(), childPath, errors);
            } else if (isNumber(v) && e.getKey().toLowerCase().endsWith("prob") && v.getAsDouble() < 0) {
                errors.add(new FieldError(childPath, "Must be >= 0"));
            }
        }
    }

    private static void validateProgrammable(JsonObject cfg, String path, List<FieldError> errors) {
        if (!cfg.has("rules") || !cfg.get("rules").isJsonObject() || cfg.getAsJsonObject("rules").size() == 0) {
            errors.add(new FieldError(path + ".rules", "Must be an object of rule slots, e.g. {\"r0\": {...}}"));
            return;
        }
        for (Map.Entry<String, JsonElement> slot : cfg.getAsJsonObject("rules").entrySet()) {
            String rp = path + ".rules." + slot.getKey();
            if (!slot.getValue().isJsonObject()) {
                errors.add(new FieldError(rp, "Must be an object"));
                continue;
            }
            JsonObject rule = slot.getValue().getAsJsonObject();
            String op = stringOrNull(rule, "op");
            if (op == null || !PROG_OPS.contains(op)) {
                errors.add(new FieldError(rp + ".op", "Must be one of " + sorted(PROG_OPS)));
                continue;
            }
            if (rule.has("fraction")) {
                if (!isNumber(rule.get("fraction"))
                        || rule.get("fraction").getAsDouble() < 0 || rule.get("fraction").getAsDouble() > 1) {
                    errors.add(new FieldError(rp + ".fraction", "Must be a number in [0, 1]"));
                }
            }
            boolean mutation = op.equals("set") || op.equals("add") || op.equals("scale");
            if (mutation) {
                String field = stringOrNull(rule, "field");
                if (field == null || !PROG_FIELDS.contains(field)) {
                    errors.add(new FieldError(rp + ".field", "Must be one of " + sorted(PROG_FIELDS)));
                } else if (PROG_TIME_FIELDS.contains(field) && !op.equals("add")) {
                    errors.add(new FieldError(rp + ".op", "Time fields (t, timestamp) only accept add"));
                }
                if (!rule.has("value") || !isNumber(rule.get("value"))) {
                    errors.add(new FieldError(rp + ".value", "Must be a number"));
                }
            }
            if (op.equals("duplicate")) {
                if (!rule.has("value") || !isNumber(rule.get("value"))
                        || rule.get("value").getAsDouble() < 1 || rule.get("value").getAsDouble() > 10) {
                    errors.add(new FieldError(rp + ".value", "Extra copies must be between 1 and 10"));
                }
            }
            if (rule.has("when")) {
                if (!rule.get("when").isJsonObject()) {
                    errors.add(new FieldError(rp + ".when", "Must be an object {field, cmp, value}"));
                    continue;
                }
                JsonObject when = rule.getAsJsonObject("when");
                String cmp = stringOrNull(when, "cmp");
                if (cmp == null) cmp = "always";
                if (!PROG_CMPS.contains(cmp)) {
                    errors.add(new FieldError(rp + ".when.cmp", "Must be one of " + sorted(PROG_CMPS)));
                } else if (!cmp.equals("always")) {
                    String wf = stringOrNull(when, "field");
                    if (wf == null || !PROG_FIELDS.contains(wf)) {
                        errors.add(new FieldError(rp + ".when.field", "Must be one of " + sorted(PROG_FIELDS)));
                    }
                    if (!when.has("value") || !isNumber(when.get("value"))) {
                        errors.add(new FieldError(rp + ".when.value", "Must be a number"));
                    }
                }
            }
        }
    }

    // --- helpers -------------------------------------------------------------

    private static void intRange(List<FieldError> errors, String field, Integer v, int min, int max) {
        if (v == null) {
            errors.add(new FieldError(field, "Required"));
        } else if (v < min || v > max) {
            errors.add(new FieldError(field, "Must be between " + min + " and " + max));
        }
    }

    private static void required(List<FieldError> errors, String field, String v) {
        if (v == null || v.trim().isEmpty()) {
            errors.add(new FieldError(field, "Required"));
        }
    }

    private static Double number(List<FieldError> errors, String field, String v) {
        if (v == null || v.trim().isEmpty()) {
            errors.add(new FieldError(field, "Required"));
            return null;
        }
        try {
            return Double.parseDouble(v.trim());
        } catch (NumberFormatException e) {
            errors.add(new FieldError(field, "Must be a number"));
            return null;
        }
    }

    private static void pattern(List<FieldError> errors, String field, String v, Pattern p, String message) {
        if (v == null || v.trim().isEmpty()) {
            errors.add(new FieldError(field, "Required"));
        } else if (!p.matcher(v.trim()).matches()) {
            errors.add(new FieldError(field, message));
        }
    }

    private static boolean isNumber(JsonElement e) {
        return e != null && e.isJsonPrimitive() && ((JsonPrimitive) e).isNumber();
    }

    private static String stringOrNull(JsonObject o, String key) {
        if (!o.has(key) || !o.get(key).isJsonPrimitive()) return null;
        return o.get(key).getAsString();
    }

    private static String sorted(Set<String> s) {
        String[] a = s.toArray(new String[0]);
        Arrays.sort(a);
        return String.join(", ", a);
    }

    /** Convenience for HTTP responses: {"errors": [{field, message}, ...]}. */
    public static Map<String, Object> toBody(List<FieldError> errors) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "Invalid run spec");
        body.put("errors", errors);
        return body;
    }
}
