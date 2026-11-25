package br.ufu.facom.ereno.api;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

/**
 * Dataset generation runs.
 *
 *   POST   /api/runs                    validate a RunSpec and queue it (201, or 400 with field errors)
 *   GET    /api/runs                    list runs, newest first
 *   GET    /api/runs/{id}               status + spec + files + log tail
 *   DELETE /api/runs/{id}               cancel an active run, or delete a finished one
 *   GET    /api/runs/{id}/log           full log (text/plain)
 *   GET    /api/runs/{id}/files/{rel}   download one file of the run (streamed, as attachment)
 */
final class RunsHandler implements HttpHandler {

    private static final Logger LOG = Logger.getLogger(RunsHandler.class.getName());
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().serializeNulls().create();
    private static final int MAX_BODY = 2 * 1024 * 1024;
    private static final int LOG_TAIL_BYTES = 16 * 1024;
    private static final String PREFIX = "/api/runs";

    private final RunManager runs;
    private final RunSpecValidator validator;

    RunsHandler(RunManager runs, RunSpecValidator validator) {
        this.runs = runs;
        this.validator = validator;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        try {
            if (Config.Http.handlePreflight(ex)) return;
            if (!Config.Http.checkAuth(ex)) return;

            String path = ex.getRequestURI().getPath();
            String rest = path.equals(PREFIX) ? "" : path.startsWith(PREFIX + "/") ? path.substring(PREFIX.length() + 1) : null;
            String method = ex.getRequestMethod().toUpperCase(Locale.ROOT);
            if (rest == null) {
                Config.Http.sendError(ex, 404, "Not found");
                return;
            }

            if (rest.isEmpty()) {
                if (method.equals("GET")) {
                    Config.Http.sendJson(ex, 200, GSON.toJson(Map.of("runs", runs.list())));
                } else if (method.equals("POST")) {
                    create(ex);
                } else {
                    Config.Http.sendError(ex, 405, "Method not allowed");
                }
                return;
            }

            int slash = rest.indexOf('/');
            String id = slash < 0 ? rest : rest.substring(0, slash);
            String sub = slash < 0 ? "" : rest.substring(slash + 1);
            RunManager.RunStatus st = runs.get(id);
            if (st == null) {
                Config.Http.sendError(ex, 404, "Run not found: " + id);
                return;
            }

            if (sub.isEmpty()) {
                if (method.equals("GET")) {
                    detail(ex, st);
                } else if (method.equals("DELETE")) {
                    if (st.isActive()) {
                        runs.cancel(id);
                    } else {
                        runs.delete(id);
                    }
                    Config.Http.sendBytes(ex, 204, "application/json", new byte[0]);
                } else {
                    Config.Http.sendError(ex, 405, "Method not allowed");
                }
                return;
            }

            if (!method.equals("GET")) {
                Config.Http.sendError(ex, 405, "Method not allowed");
                return;
            }
            Path runDir = runs.runDir(id);
            if (sub.equals("log")) {
                Path log = runDir.resolve("run.log");
                if (!Files.isRegularFile(log)) {
                    Config.Http.sendBytes(ex, 200, "text/plain; charset=utf-8", new byte[0]);
                } else {
                    Config.Http.sendFile(ex, log, "text/plain; charset=utf-8", null);
                }
                return;
            }
            if (sub.startsWith("files/")) {
                String rel = sub.substring("files/".length());
                Path file = Config.PathSafety.resolveSafe(runDir, rel);
                if (file == null) {
                    Config.Http.sendError(ex, 400, "Invalid path");
                    return;
                }
                if (!Files.isRegularFile(file)) {
                    Config.Http.sendError(ex, 404, "File not found: " + rel);
                    return;
                }
                Config.Http.sendFile(ex, file, contentType(rel), file.getFileName().toString());
                return;
            }
            Config.Http.sendError(ex, 404, "Not found");
        } catch (IOException | RuntimeException e) {
            LOG.log(Level.WARNING, "Runs handler error", e);
            Config.Http.sendError(ex, 500, "Internal error: " + e.getClass().getSimpleName());
        }
    }

    private void create(HttpExchange ex) throws IOException {
        byte[] body = Config.Http.readBody(ex);
        if (body.length == 0) {
            Config.Http.sendError(ex, 400, "Empty body");
            return;
        }
        if (body.length > MAX_BODY) {
            Config.Http.sendError(ex, 413, "Run spec too large");
            return;
        }
        RunSpec spec;
        try {
            spec = GSON.fromJson(JsonParser.parseString(new String(body, StandardCharsets.UTF_8)), RunSpec.class);
        } catch (JsonParseException | IllegalStateException | NumberFormatException e) {
            Config.Http.sendError(ex, 400, "Invalid JSON: " + e.getMessage());
            return;
        }
        List<RunSpecValidator.FieldError> errors = validator.validate(spec);
        if (!errors.isEmpty()) {
            Config.Http.sendJson(ex, 400, GSON.toJson(RunSpecValidator.toBody(errors)));
            return;
        }
        RunManager.RunStatus st = runs.submit(spec);
        Config.Http.sendJson(ex, 201, GSON.toJson(st));
    }

    private void detail(HttpExchange ex, RunManager.RunStatus st) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("run", st);
        Path specFile = runs.runDir(st.id).resolve("spec.json");
        if (Files.isRegularFile(specFile)) {
            body.put("spec", JsonParser.parseString(Files.readString(specFile)));
        }
        body.put("files", runs.files(st.id));
        body.put("logTail", runs.logTail(st.id, LOG_TAIL_BYTES));
        Config.Http.sendJson(ex, 200, GSON.toJson(body));
    }

    private static String contentType(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".json")) return "application/json; charset=utf-8";
        if (lower.endsWith(".csv")) return "text/csv; charset=utf-8";
        if (lower.endsWith(".arff") || lower.endsWith(".log") || lower.endsWith(".txt")) return "text/plain; charset=utf-8";
        return "application/octet-stream";
    }
}
