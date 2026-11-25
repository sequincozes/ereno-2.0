package br.ufu.facom.ereno.api;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Logger;

import com.sun.net.httpserver.HttpServer;

/**
 * Full ERENO backend for the ERENO UI: the config/artifact routes of
 * {@link Config} plus /api/catalog and /api/runs (dataset generation).
 *
 * Run from the core root (api/), since the core resolves paths against the
 * working directory:
 *   java -jar target/ERENO-1.0-SNAPSHOT-uber.jar serve
 *
 * Settings (system properties, env where noted):
 *   ereno.api.port (8080), ereno.api.bind / ERENO_API_BIND (127.0.0.1),
 *   ereno.api.config (config), ereno.api.artifacts (target),
 *   ereno.api.runs (runs), ereno.api.workers (1),
 *   ereno.runner.jvmArgs (extra JVM args for each run, e.g. -Xmx4g),
 *   ERENO_API_TOKEN (bearer token), ERENO_API_CORS_ORIGIN (http://localhost:5173)
 */
public final class Server {

    private static final Logger LOGGER = Logger.getLogger(Server.class.getName());

    private Server() {}

    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(System.getProperty("ereno.api.port", "8080"));
        String bind = firstNonEmpty(System.getenv("ERENO_API_BIND"), System.getProperty("ereno.api.bind"), "127.0.0.1");
        Path workDir = Paths.get("").toAbsolutePath().normalize();
        Path configRoot = Paths.get(System.getProperty("ereno.api.config", "config")).toAbsolutePath().normalize();
        Path artifactsRoot = Paths.get(System.getProperty("ereno.api.artifacts", "target")).toAbsolutePath().normalize();
        Path runsRoot = Paths.get(System.getProperty("ereno.api.runs", "runs")).toAbsolutePath().normalize();
        int workers = Integer.parseInt(System.getProperty("ereno.api.workers", "1"));
        boolean backupsEnabled = !"false".equalsIgnoreCase(System.getProperty("ereno.api.backups", "true"));
        Path backupRoot = backupsEnabled ? artifactsRoot.resolve("config_backups") : null;

        String token = System.getenv("ERENO_API_TOKEN");
        boolean loopback = "127.0.0.1".equals(bind) || "localhost".equalsIgnoreCase(bind) || "::1".equals(bind);
        if (!loopback && (token == null || token.isEmpty())) {
            System.err.println("Refusing to bind " + bind + " without ERENO_API_TOKEN set.");
            System.exit(2);
        }

        RunManager runs = new RunManager(runsRoot, workDir, new RunPlanner(workDir.resolve("electrical-sources")),
                RunManager.defaultChildCommand(), workers);
        HttpServer server = start(bind, port, configRoot, artifactsRoot, backupRoot,
                workDir.resolve("electrical-sources"), runs);

        LOGGER.info(() -> "ERENO API listening on http://" + bind + ":" + server.getAddress().getPort());
        LOGGER.info(() -> "  working dir:    " + workDir);
        LOGGER.info(() -> "  config root:    " + configRoot);
        LOGGER.info(() -> "  runs root:      " + runsRoot + " (" + workers + " worker(s))");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOGGER.info("Shutting down API server");
            runs.shutdown();
            server.stop(1);
        }, "api-shutdown"));
    }

    /** Testable entry point: builds and starts the server with all routes. */
    public static HttpServer start(String bind, int port, Path configRoot, Path artifactsRoot, Path backupRoot,
                                   Path electricalSourcesRoot, RunManager runs) throws IOException {
        HttpServer server = Config.create(bind, port, configRoot, artifactsRoot, backupRoot);
        server.createContext("/api/catalog", new CatalogHandler(configRoot, electricalSourcesRoot));
        server.createContext("/api/runs", new RunsHandler(runs, new RunSpecValidator(electricalSourcesRoot)));
        server.start();
        return server;
    }

    private static String firstNonEmpty(String... values) {
        for (String v : values) {
            if (v != null && !v.isEmpty()) return v;
        }
        return null;
    }
}
