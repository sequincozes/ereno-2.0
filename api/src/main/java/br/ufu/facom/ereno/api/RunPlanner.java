package br.ufu.facom.ereno.api;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Turns a validated RunSpec into the action configs the core already runs:
 * one create_benign and one create_attack_dataset, plus one attack JSON per
 * segment. Everything is written inside the run folder with absolute paths,
 * so a run never reads or writes the shared config/ or target/ trees and can
 * be replayed from its folder alone.
 */
public final class RunPlanner {

    public static final class Step {
        public final String name;
        public final Path config;

        Step(String name, Path config) {
            this.name = name;
            this.config = config;
        }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final Path electricalSourcesRoot;

    public RunPlanner(Path electricalSourcesRoot) {
        this.electricalSourcesRoot = electricalSourcesRoot.toAbsolutePath().normalize();
    }

    public List<Step> plan(RunSpec spec, Path runDir) throws IOException {
        Path stepsDir = runDir.resolve("steps");
        Path attacksDir = stepsDir.resolve("attacks");
        Path benignDir = runDir.resolve("benign");
        Path outputDir = runDir.resolve("output");
        Files.createDirectories(attacksDir);

        List<Step> steps = new ArrayList<>();

        Path benignConfig = stepsDir.resolve("create_benign.json");
        write(benignConfig, benignAction(spec, benignDir));
        steps.add(new Step("create_benign", benignConfig));

        Path attackConfig = stepsDir.resolve("create_attack_dataset.json");
        write(attackConfig, attackAction(spec, benignDir, outputDir, attacksDir));
        steps.add(new Step("create_attack_dataset", attackConfig));

        return steps;
    }

    /** Name CreateBenignAction gives its ARFF output once the seed is set (see BenignDataManager). */
    public static String benignFileName(RunSpec spec) {
        return spec.randomSeed + "_" + spec.benign.faultProbability + "%fault_benign_data.arff";
    }

    public static String outputFileName(RunSpec spec) {
        String base = slug(spec.name);
        if (base.isEmpty()) base = "dataset";
        return base + "." + spec.output.format;
    }

    private JsonObject benignAction(RunSpec spec, Path benignDir) {
        JsonObject root = new JsonObject();
        root.addProperty("action", "create_benign");
        root.addProperty("description", "ERENO UI run: benign traffic");

        JsonObject output = new JsonObject();
        output.addProperty("directory", benignDir.toString());
        JsonArray formats = new JsonArray();
        formats.add("arff");
        output.add("formats", formats);
        output.addProperty("enableTracking", false);
        root.add("output", output);

        JsonObject generation = new JsonObject();
        generation.addProperty("numberOfMessages", spec.benign.numberOfMessages);
        generation.addProperty("faultProbability", spec.benign.faultProbability);
        generation.addProperty("svStrategy", spec.benign.svStrategy);
        generation.addProperty("svSamplesPerGoose",
                spec.benign.svSamplesPerGoose == null ? 80 : spec.benign.svSamplesPerGoose);
        root.add("generation", generation);

        root.add("gooseFlow", GSON.toJsonTree(spec.gooseFlow));
        root.add("setupIED", GSON.toJsonTree(spec.setupIED));

        JsonObject sources = new JsonObject();
        sources.addProperty("directory", electricalSourcesRoot.resolve(spec.benign.electricalSources).toString());
        sources.addProperty("pattern", "SILVIO_*.out");
        root.add("electricalSources", sources);

        root.addProperty("randomSeed", spec.randomSeed);
        return root;
    }

    private JsonObject attackAction(RunSpec spec, Path benignDir, Path outputDir, Path attacksDir) throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("action", "create_attack_dataset");
        root.addProperty("description", "ERENO UI run: labelled attack dataset");

        JsonObject input = new JsonObject();
        input.addProperty("benignDataPath", benignDir.resolve(benignFileName(spec)).toString());
        input.addProperty("verifyBenignData", true);
        input.addProperty("useLegacy", false);
        root.add("input", input);

        JsonObject output = new JsonObject();
        output.addProperty("directory", outputDir.toString());
        output.addProperty("filename", outputFileName(spec));
        output.addProperty("format", spec.output.format);
        output.addProperty("enableTracking", false);
        root.add("output", output);

        JsonObject structure = new JsonObject();
        structure.addProperty("messagesPerSegment", spec.datasetStructure.messagesPerSegment);
        structure.addProperty("benignMessagesPerSegment",
                spec.datasetStructure.benignMessagesPerSegment == null ? 0 : spec.datasetStructure.benignMessagesPerSegment);
        structure.addProperty("includeBenignSegment", spec.datasetStructure.includeBenignSegment);
        structure.addProperty("shuffleSegments", spec.datasetStructure.shuffleSegments);
        structure.addProperty("binaryClassification", spec.datasetStructure.binaryClassification);
        root.add("datasetStructure", structure);

        JsonArray segments = new JsonArray();
        Map<String, Integer> seen = new HashMap<>();
        for (int i = 0; i < spec.attacks.size(); i++) {
            JsonObject cfg = spec.attacks.get(i).config.deepCopy();
            String type = cfg.get("attackType").getAsString().toLowerCase(Locale.ROOT);
            String base = AttackCatalog.ucFor(type) + "_" + type;
            int n = seen.merge(base, 1, Integer::sum);
            // The uc prefix is what labels the segment; the suffix only keeps names unique.
            String segmentName = n == 1 ? base : base + "_" + n;

            Path attackFile = attacksDir.resolve(String.format(Locale.ROOT, "%02d_%s.json", i + 1, segmentName));
            write(attackFile, cfg);

            JsonObject seg = new JsonObject();
            seg.addProperty("name", segmentName);
            seg.addProperty("enabled", true);
            seg.addProperty("attackConfig", attackFile.toString());
            segments.add(seg);
        }
        root.add("attackSegments", segments);
        root.addProperty("randomSeed", spec.randomSeed);
        return root;
    }

    static String slug(String s) {
        if (s == null) return "";
        String out = s.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        return out.length() > 60 ? out.substring(0, 60) : out;
    }

    private static void write(Path file, JsonObject json) throws IOException {
        Files.createDirectories(file.getParent());
        Files.write(file, GSON.toJson(json).getBytes(StandardCharsets.UTF_8));
    }
}
