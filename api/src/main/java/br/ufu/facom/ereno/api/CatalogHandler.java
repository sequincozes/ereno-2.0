package br.ufu.facom.ereno.api;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

/**
 * GET /api/catalog: what the UI needs to build its forms, read from the core
 * itself so a new attack template or electrical-source resolution shows up
 * without touching the frontend.
 *
 *   attacks           every config/attacks/*.json (top level) with its uc and template
 *   electricalSources folders under electrical-sources/
 *   defaults          benign/IED/GOOSE/dataset-structure defaults from config/actions/
 */
final class CatalogHandler implements HttpHandler {

    private static final Logger LOG = Logger.getLogger(CatalogHandler.class.getName());
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().serializeNulls().create();

    private final Path configRoot;
    private final Path electricalSourcesRoot;

    CatalogHandler(Path configRoot, Path electricalSourcesRoot) {
        this.configRoot = configRoot;
        this.electricalSourcesRoot = electricalSourcesRoot;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        try {
            if (Config.Http.handlePreflight(ex)) return;
            if (!Config.Http.checkAuth(ex)) return;
            if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
                Config.Http.sendError(ex, 405, "Method not allowed");
                return;
            }
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("attacks", attacks());
            body.put("electricalSources", resolutions());
            body.put("svStrategies", List.of("DECIMATED", "UPSTREAM_FAITHFUL"));
            body.put("formats", List.of("csv", "arff"));
            body.put("defaults", defaults());
            Config.Http.sendJson(ex, 200, GSON.toJson(body));
        } catch (IOException | RuntimeException e) {
            LOG.log(Level.WARNING, "Catalog handler error", e);
            Config.Http.sendError(ex, 500, "Internal error: " + e.getClass().getSimpleName());
        }
    }

    private List<Map<String, Object>> attacks() throws IOException {
        List<Map<String, Object>> out = new ArrayList<>();
        Path dir = configRoot.resolve("attacks");
        if (!Files.isDirectory(dir)) return out;
        List<Path> files = new ArrayList<>();
        try (Stream<Path> s = Files.list(dir)) {
            s.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().endsWith(".json")).sorted().forEach(files::add);
        }
        for (Path f : files) {
            JsonElement parsed;
            try {
                parsed = JsonParser.parseString(Files.readString(f));
            } catch (RuntimeException e) {
                LOG.warning(() -> "Skipping unparsable attack template " + f);
                continue;
            }
            if (!parsed.isJsonObject()) continue;
            JsonObject template = parsed.getAsJsonObject();
            if (!template.has("attackType")) continue;
            String type = template.get("attackType").getAsString();
            String uc = AttackCatalog.ucFor(type);
            if (uc == null) continue;
            String id = f.getFileName().toString().replaceFirst("\\.json$", "");
            Map<String, Object> a = new LinkedHashMap<>();
            a.put("id", id);
            a.put("uc", uc);
            a.put("attackType", type);
            a.put("description", template.has("description") ? template.get("description").getAsString() : null);
            a.put("template", template);
            out.add(a);
        }
        return out;
    }

    private List<String> resolutions() throws IOException {
        List<String> out = new ArrayList<>();
        if (!Files.isDirectory(electricalSourcesRoot)) return out;
        try (Stream<Path> s = Files.list(electricalSourcesRoot)) {
            s.filter(Files::isDirectory)
             .map(p -> p.getFileName().toString())
             .filter(n -> n.matches("res\\d+"))
             .sorted((a, b) -> Integer.compare(Integer.parseInt(a.substring(3)), Integer.parseInt(b.substring(3))))
             .forEach(out::add);
        }
        return out;
    }

    private Map<String, Object> defaults() {
        Map<String, Object> d = new LinkedHashMap<>();
        JsonObject benign = readObject(configRoot.resolve("actions/action_create_benign.json"));
        JsonObject attack = readObject(configRoot.resolve("actions/action_create_attack_dataset.json"));
        if (benign != null) {
            d.put("generation", benign.get("generation"));
            d.put("gooseFlow", benign.get("gooseFlow"));
            d.put("setupIED", benign.get("setupIED"));
        }
        if (attack != null) {
            d.put("datasetStructure", attack.get("datasetStructure"));
        }
        return d;
    }

    private static JsonObject readObject(Path p) {
        try {
            JsonElement e = JsonParser.parseString(Files.readString(p));
            return e.isJsonObject() ? e.getAsJsonObject() : null;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }
}
