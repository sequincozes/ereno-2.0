package br.ufu.facom.ereno.api;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Queues and runs dataset generations. Each run gets its own folder
 * ({runsRoot}/{id}/) and each step runs as a child JVM executing
 * ActionRunner on the step's config.
 *
 * Child processes instead of in-process calls because the core keeps
 * mutable static state (ConfigLoader.gooseFlow/setupIED, ARFFWritter's
 * writer) and ActionRunner calls System.exit; a child keeps every run
 * isolated, lets cancel kill it, and captures its log in the run folder.
 */
public final class RunManager {

    public enum State { QUEUED, RUNNING, SUCCEEDED, FAILED, CANCELLED }

    /** Persisted as {runDir}/status.json; also the API's run summary. */
    public static final class RunStatus {
        public String id;
        public String name;
        public State state;
        public String step;
        public int stepIndex;
        public int stepCount;
        public String createdAt;
        public String startedAt;
        public String finishedAt;
        public Integer exitCode;
        public String error;
        public Long randomSeed;
        public String outputFile;

        boolean isActive() {
            return state == State.QUEUED || state == State.RUNNING;
        }
    }

    private static final Logger LOG = Logger.getLogger(RunManager.class.getName());
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final DateTimeFormatter ID_TIME =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

    private final Path runsRoot;
    private final Path workDir;
    private final RunPlanner planner;
    private final List<String> childCommandPrefix;
    private final String gitCommit;
    private final ExecutorService workers;
    private final Map<String, RunStatus> runs = new ConcurrentHashMap<>();
    private final Map<String, Process> processes = new ConcurrentHashMap<>();

    /**
     * @param runsRoot           where run folders live
     * @param workDir            working directory of the child JVMs (the core root, api/)
     * @param planner            builds the step configs
     * @param childCommandPrefix command that runs ActionRunner; the step config path is appended
     * @param workerCount        how many runs may execute at once
     */
    public RunManager(Path runsRoot, Path workDir, RunPlanner planner,
                      List<String> childCommandPrefix, int workerCount) throws IOException {
        this.runsRoot = runsRoot.toAbsolutePath().normalize();
        this.workDir = workDir.toAbsolutePath().normalize();
        this.planner = planner;
        this.childCommandPrefix = new ArrayList<>(childCommandPrefix);
        this.gitCommit = readGitCommit(this.workDir);
        this.workers = Executors.newFixedThreadPool(Math.max(1, workerCount), r -> {
            Thread t = new Thread(r, "run-worker");
            t.setDaemon(true);
            return t;
        });
        Files.createDirectories(this.runsRoot);
        loadExisting();
    }

    /** The default child command: this same JVM binary and classpath running ActionRunner. */
    public static List<String> defaultChildCommand() {
        List<String> cmd = new ArrayList<>();
        cmd.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        String jvmArgs = System.getProperty("ereno.runner.jvmArgs", "").trim();
        if (!jvmArgs.isEmpty()) {
            for (String a : jvmArgs.split("\\s+")) cmd.add(a);
        }
        cmd.add("-Dfile.encoding=UTF-8");
        cmd.add("-cp");
        cmd.add(System.getProperty("ereno.runner.classpath", System.getProperty("java.class.path")));
        cmd.add("br.ufu.facom.ereno.ActionRunner");
        return cmd;
    }

    public Path runsRoot() {
        return runsRoot;
    }

    public RunStatus submit(RunSpec spec) throws IOException {
        String id = ID_TIME.format(Instant.now()) + "-" + UUID.randomUUID().toString().substring(0, 6);
        Path runDir = runsRoot.resolve(id);
        Files.createDirectories(runDir);
        Files.write(runDir.resolve("spec.json"), GSON.toJson(spec).getBytes(StandardCharsets.UTF_8));

        List<RunPlanner.Step> steps = planner.plan(spec, runDir);

        RunStatus st = new RunStatus();
        st.id = id;
        st.name = spec.name;
        st.state = State.QUEUED;
        st.stepCount = steps.size();
        st.createdAt = Instant.now().toString();
        st.randomSeed = spec.randomSeed;
        st.outputFile = "output/" + RunPlanner.outputFileName(spec);
        runs.put(id, st);
        persist(st);

        workers.submit(() -> execute(st, steps, runDir));
        return st;
    }

    public List<RunStatus> list() {
        List<RunStatus> out = new ArrayList<>(runs.values());
        out.sort(Comparator.comparing((RunStatus r) -> r.createdAt == null ? "" : r.createdAt).reversed());
        return out;
    }

    public RunStatus get(String id) {
        return id == null ? null : runs.get(id);
    }

    public Path runDir(String id) {
        return runsRoot.resolve(id);
    }

    /** Cancels a queued or running run. @return false when the run is not active. */
    public synchronized boolean cancel(String id) throws IOException {
        RunStatus st = runs.get(id);
        if (st == null || !st.isActive()) return false;
        st.state = State.CANCELLED;
        st.finishedAt = Instant.now().toString();
        st.error = "Cancelled by user";
        persist(st);
        Process p = processes.remove(id);
        if (p != null) {
            p.descendants().forEach(ProcessHandle::destroyForcibly);
            p.destroyForcibly();
        }
        appendLog(runDir(id), "\n=== Cancelled by user ===\n");
        return true;
    }

    /** Deletes a finished run's folder. @return false when the run is active or unknown. */
    public synchronized boolean delete(String id) throws IOException {
        RunStatus st = runs.get(id);
        if (st == null || st.isActive()) return false;
        runs.remove(id);
        deleteRecursive(runDir(id));
        return true;
    }

    /** Last {@code maxBytes} of the run log. */
    public String logTail(String id, int maxBytes) throws IOException {
        Path log = runDir(id).resolve("run.log");
        if (!Files.isRegularFile(log)) return "";
        try (RandomAccessFile raf = new RandomAccessFile(log.toFile(), "r")) {
            long len = raf.length();
            long start = Math.max(0, len - maxBytes);
            raf.seek(start);
            byte[] buf = new byte[(int) (len - start)];
            raf.readFully(buf);
            return new String(buf, StandardCharsets.UTF_8);
        }
    }

    /** Files of a run, relative to its folder, excluding the step scaffolding. */
    public List<Map<String, Object>> files(String id) throws IOException {
        Path dir = runDir(id);
        List<Map<String, Object>> out = new ArrayList<>();
        if (!Files.isDirectory(dir)) return out;
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.filter(Files::isRegularFile).sorted().forEach(p -> {
                Map<String, Object> f = new LinkedHashMap<>();
                f.put("path", dir.relativize(p).toString().replace('\\', '/'));
                try {
                    f.put("bytes", Files.size(p));
                } catch (IOException e) {
                    f.put("bytes", -1);
                }
                out.add(f);
            });
        }
        return out;
    }

    public void shutdown() {
        workers.shutdownNow();
        for (Process p : processes.values()) {
            p.descendants().forEach(ProcessHandle::destroyForcibly);
            p.destroyForcibly();
        }
    }

    // --- execution -----------------------------------------------------------

    private void execute(RunStatus st, List<RunPlanner.Step> steps, Path runDir) {
        synchronized (this) {
            if (st.state != State.QUEUED) return; // cancelled while queued
            st.state = State.RUNNING;
            st.startedAt = Instant.now().toString();
            persistQuietly(st);
        }
        Path log = runDir.resolve("run.log");
        try {
            for (int i = 0; i < steps.size(); i++) {
                RunPlanner.Step step = steps.get(i);
                synchronized (this) {
                    if (st.state != State.RUNNING) return;
                    st.step = step.name;
                    st.stepIndex = i + 1;
                    persist(st);
                }
                appendLog(runDir, "\n=== Step " + (i + 1) + "/" + steps.size() + ": " + step.name + " ===\n");

                List<String> cmd = new ArrayList<>(childCommandPrefix);
                cmd.add(step.config.toString());
                ProcessBuilder pb = new ProcessBuilder(cmd)
                        .directory(workDir.toFile())
                        .redirectErrorStream(true)
                        .redirectOutput(ProcessBuilder.Redirect.appendTo(log.toFile()));
                Process p = pb.start();
                processes.put(st.id, p);
                synchronized (this) {
                    if (st.state != State.RUNNING) { // cancelled between start() and put()
                        processes.remove(st.id);
                        p.descendants().forEach(ProcessHandle::destroyForcibly);
                        p.destroyForcibly();
                        return;
                    }
                }
                int exit = p.waitFor();
                processes.remove(st.id);

                synchronized (this) {
                    if (st.state != State.RUNNING) return; // cancelled meanwhile
                    if (exit != 0) {
                        st.state = State.FAILED;
                        st.exitCode = exit;
                        st.error = "Step " + step.name + " exited with code " + exit + " (see run.log)";
                        st.finishedAt = Instant.now().toString();
                        persist(st);
                        return;
                    }
                }
            }
            synchronized (this) {
                if (st.state != State.RUNNING) return;
                if (!Files.isRegularFile(runDir.resolve(st.outputFile))) {
                    st.state = State.FAILED;
                    st.error = "Generation finished but " + st.outputFile + " was not written (see run.log)";
                } else {
                    st.state = State.SUCCEEDED;
                    st.exitCode = 0;
                }
                st.finishedAt = Instant.now().toString();
                persist(st);
                writeManifest(st, runDir);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            failQuietly(st, "Interrupted");
        } catch (IOException | RuntimeException e) {
            LOG.log(Level.WARNING, "Run " + st.id + " failed", e);
            failQuietly(st, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private void writeManifest(RunStatus st, Path runDir) throws IOException {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("runId", st.id);
        m.put("name", st.name);
        m.put("randomSeed", st.randomSeed);
        m.put("createdAt", st.createdAt);
        m.put("finishedAt", st.finishedAt);
        m.put("gitCommit", gitCommit);
        m.put("javaVersion", System.getProperty("java.version"));
        Package pkg = RunManager.class.getPackage();
        m.put("ereno.version", pkg == null ? null : pkg.getImplementationVersion());
        m.put("spec", "spec.json");
        m.put("steps", "steps/");
        List<Map<String, Object>> files = new ArrayList<>();
        for (Map<String, Object> f : files(st.id)) {
            String rel = (String) f.get("path");
            if (rel.equals("status.json") || rel.equals("manifest.json") || rel.equals("run.log")) continue;
            f.put("sha256", sha256(runDir.resolve(rel)));
            files.add(f);
        }
        m.put("files", files);
        Files.write(runDir.resolve("manifest.json"), GSON.toJson(m).getBytes(StandardCharsets.UTF_8));
    }

    private synchronized void failQuietly(RunStatus st, String message) {
        if (!st.isActive()) return;
        st.state = State.FAILED;
        st.error = message;
        st.finishedAt = Instant.now().toString();
        persistQuietly(st);
    }

    // --- persistence ---------------------------------------------------------

    private void persist(RunStatus st) throws IOException {
        Config.PathSafety.atomicWrite(runDir(st.id).resolve("status.json"),
                GSON.toJson(st).getBytes(StandardCharsets.UTF_8));
    }

    private void persistQuietly(RunStatus st) {
        try {
            persist(st);
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Could not persist status of run " + st.id, e);
        }
    }

    /** Reloads runs from disk; anything that was active when the server stopped is marked failed. */
    private void loadExisting() throws IOException {
        try (Stream<Path> dirs = Files.list(runsRoot)) {
            for (Path dir : (Iterable<Path>) dirs::iterator) {
                Path status = dir.resolve("status.json");
                if (!Files.isRegularFile(status)) continue;
                try {
                    RunStatus st = GSON.fromJson(Files.readString(status), RunStatus.class);
                    if (st == null || st.id == null) continue;
                    if (st.isActive()) {
                        st.state = State.FAILED;
                        st.error = "Server stopped before the run finished";
                        st.finishedAt = Instant.now().toString();
                        persistQuietly(st);
                    }
                    runs.put(st.id, st);
                } catch (RuntimeException e) {
                    LOG.log(Level.WARNING, "Skipping unreadable run status " + status, e);
                }
            }
        }
    }

    private static void appendLog(Path runDir, String text) throws IOException {
        Files.write(runDir.resolve("run.log"), text.getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    private static String readGitCommit(Path dir) {
        try {
            Process p = new ProcessBuilder("git", "rev-parse", "HEAD")
                    .directory(dir.toFile()).redirectErrorStream(true).start();
            String out;
            try (InputStream in = p.getInputStream()) {
                out = new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
            }
            if (!p.waitFor(5, TimeUnit.SECONDS) || p.exitValue() != 0) return null;
            String dirty = null;
            Process d = new ProcessBuilder("git", "status", "--porcelain", "--untracked-files=no")
                    .directory(dir.toFile()).redirectErrorStream(true).start();
            try (InputStream in = d.getInputStream()) {
                dirty = new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
            }
            d.waitFor(5, TimeUnit.SECONDS);
            return dirty.isEmpty() ? out : out + "-dirty";
        } catch (IOException | InterruptedException | RuntimeException e) {
            return null;
        }
    }

    private static String sha256(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[1 << 16];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
            StringBuilder sb = new StringBuilder();
            for (byte b : md.digest()) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void deleteRecursive(Path p) throws IOException {
        if (!Files.exists(p)) return;
        try (Stream<Path> walk = Files.walk(p)) {
            walk.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
        }
    }
}
