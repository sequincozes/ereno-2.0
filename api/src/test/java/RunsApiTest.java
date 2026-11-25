import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;

import br.ufu.facom.ereno.api.RunManager;
import br.ufu.facom.ereno.api.RunPlanner;
import br.ufu.facom.ereno.api.Server;

/** /api/runs and /api/catalog with a fake child runner (fast lifecycle tests). */
public class RunsApiTest {

    private HttpServer server;
    private RunManager runs;
    private Path tmp;
    private int port;

    static final Path ELECTRICAL = Paths.get("electrical-sources").toAbsolutePath();

    @BeforeEach
    void start() throws IOException {
        tmp = Files.createTempDirectory("runs-api");
        List<String> fake = List.of(
                Paths.get(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", System.getProperty("java.class.path"), "FakeActionRunner");
        runs = new RunManager(tmp.resolve("runs"), Paths.get("").toAbsolutePath(), new RunPlanner(ELECTRICAL), fake, 1);
        server = Server.start("127.0.0.1", 0, Paths.get("config").toAbsolutePath(), tmp.resolve("target"),
                null, ELECTRICAL, runs);
        port = server.getAddress().getPort();
    }

    @AfterEach
    void stop() throws IOException {
        runs.shutdown();
        server.stop(0);
        try (Stream<Path> w = Files.walk(tmp)) {
            w.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        }
    }

    // --- spec helpers ------------------------------------------------------------

    static JsonObject validSpec(String name) {
        JsonObject spec = JsonParser.parseString("{"
                + "\"name\":\"" + name + "\",\"randomSeed\":42,"
                + "\"output\":{\"format\":\"csv\"},"
                + "\"benign\":{\"numberOfMessages\":1000,\"faultProbability\":5,\"svStrategy\":\"DECIMATED\","
                + "  \"svSamplesPerGoose\":80,\"electricalSources\":\"res10\"},"
                + "\"setupIED\":{\"iedName\":\"Protection IED\",\"gocbRef\":\"LD/LLN0$GO$gcblA\",\"datSet\":\"LD/LLN0$IntLockA\","
                + "  \"minTime\":\"4\",\"maxTime\":\"1000\",\"timestamp\":\"1\",\"stNum\":\"1\",\"sqNum\":\"1\"},"
                + "\"gooseFlow\":{\"goID\":\"IntLockA\",\"ethSrc\":\"01:0c:cd:01:2f:80\",\"ethDst\":\"01:0c:cd:01:2f:81\","
                + "  \"ethType\":\"0x88B8\",\"gooseAppid\":\"0x00003001\",\"TPID\":\"0x8100\","
                + "  \"ndsCom\":false,\"test\":false,\"cbstatus\":false},"
                + "\"datasetStructure\":{\"messagesPerSegment\":200,\"includeBenignSegment\":true},"
                + "\"attacks\":[]}").getAsJsonObject();
        spec.getAsJsonArray("attacks").add(attack(readTemplate("uc01_random_replay")));
        return spec;
    }

    static JsonObject attack(JsonObject config) {
        JsonObject a = new JsonObject();
        a.add("config", config);
        return a;
    }

    static JsonObject readTemplate(String id) {
        try {
            return JsonParser.parseString(Files.readString(Paths.get("config/attacks/" + id + ".json"))).getAsJsonObject();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    // --- tests -------------------------------------------------------------------

    @Test
    void catalogListsAttackTemplatesResolutionsAndDefaults() throws IOException {
        Response r = http("GET", "/api/catalog", null);
        assertEquals(200, r.status);
        JsonObject body = r.json();
        JsonArray attacks = body.getAsJsonArray("attacks");
        assertTrue(attacks.size() >= 9, r.body);
        boolean hasUc11 = false;
        for (int i = 0; i < attacks.size(); i++) {
            JsonObject a = attacks.get(i).getAsJsonObject();
            assertNotNull(a.get("template"));
            if (a.get("uc").getAsString().equals("uc11")) hasUc11 = true;
        }
        assertTrue(hasUc11, "uc11 template should be listed");
        assertTrue(body.getAsJsonArray("electricalSources").toString().contains("res10"));
        assertTrue(body.getAsJsonObject("defaults").has("setupIED"));
    }

    @Test
    void invalidSpecReturnsFieldErrors() throws IOException {
        JsonObject spec = validSpec("bad");
        spec.remove("randomSeed");
        spec.getAsJsonObject("gooseFlow").addProperty("ethSrc", "not-a-mac");
        spec.getAsJsonObject("benign").addProperty("electricalSources", "res999");
        JsonObject uc01 = readTemplate("uc01_random_replay");
        uc01.getAsJsonObject("delayMs").addProperty("min", 900.0);
        spec.getAsJsonArray("attacks").set(0, attack(uc01));
        JsonObject prog = new JsonObject();
        prog.addProperty("attackType", "programmable");
        prog.add("rules", JsonParser.parseString("{\"r0\":{\"op\":\"set\",\"field\":\"timestamp\",\"value\":1}}"));
        spec.getAsJsonArray("attacks").add(attack(prog));
        JsonObject unknown = new JsonObject();
        unknown.addProperty("attackType", "teleport");
        spec.getAsJsonArray("attacks").add(attack(unknown));

        Response r = http("POST", "/api/runs", spec.toString());
        assertEquals(400, r.status, r.body);
        String b = r.body;
        assertTrue(b.contains("\"randomSeed\""), b);
        assertTrue(b.contains("gooseFlow.ethSrc"), b);
        assertTrue(b.contains("benign.electricalSources"), b);
        assertTrue(b.contains("attacks[0].config.delayMs.max"), b);
        assertTrue(b.contains("attacks[1].config.rules.r0.op"), b);
        assertTrue(b.contains("attacks[2].config.attackType"), b);
        assertTrue(http("GET", "/api/runs", null).json().getAsJsonArray("runs").size() == 0);
    }

    @Test
    void runLifecycleWritesStepsAndOutputAndCanBeDownloaded() throws Exception {
        JsonObject spec = validSpec("Meu Dataset");
        spec.getAsJsonArray("attacks").add(attack(readTemplate("uc01_random_replay")));
        spec.getAsJsonArray("attacks").add(attack(readTemplate("uc11_programmable")));

        Response created = http("POST", "/api/runs", spec.toString());
        assertEquals(201, created.status, created.body);
        String id = created.json().get("id").getAsString();

        JsonObject run = waitForEnd(id);
        assertEquals("SUCCEEDED", run.get("state").getAsString(), run.toString());
        assertEquals("output/meu_dataset.csv", run.get("outputFile").getAsString());

        Path runDir = runs.runDir(id);
        JsonObject attackStep = JsonParser.parseString(
                Files.readString(runDir.resolve("steps/create_attack_dataset.json"))).getAsJsonObject();
        JsonArray segs = attackStep.getAsJsonArray("attackSegments");
        assertEquals("uc01_random_replay", segs.get(0).getAsJsonObject().get("name").getAsString());
        assertEquals("uc01_random_replay_2", segs.get(1).getAsJsonObject().get("name").getAsString());
        assertEquals("uc11_programmable", segs.get(2).getAsJsonObject().get("name").getAsString());
        assertEquals(42, attackStep.get("randomSeed").getAsInt());
        assertTrue(attackStep.getAsJsonObject("input").get("benignDataPath").getAsString()
                .endsWith("42_5%fault_benign_data.arff"));
        assertTrue(Files.isRegularFile(runDir.resolve("manifest.json")));
        assertTrue(Files.isRegularFile(runDir.resolve("spec.json")));

        Response detail = http("GET", "/api/runs/" + id, null);
        assertEquals(200, detail.status);
        assertTrue(detail.body.contains("fake create_attack_dataset"), detail.body);

        Response download = http("GET", "/api/runs/" + id + "/files/output/meu_dataset.csv", null);
        assertEquals(200, download.status);
        assertTrue(download.body.contains("random_replay"));
        assertEquals("attachment; filename=\"meu_dataset.csv\"", download.disposition);

        assertEquals(400, http("GET", "/api/runs/" + id + "/files/../../secret.json", null).status);

        assertEquals(204, http("DELETE", "/api/runs/" + id, null).status);
        assertFalse(Files.exists(runDir));
        assertEquals(404, http("GET", "/api/runs/" + id, null).status);
    }

    @Test
    void failingStepMarksRunFailed() throws Exception {
        Response created = http("POST", "/api/runs", validSpec("boom").toString());
        String id = created.json().get("id").getAsString();
        JsonObject run = waitForEnd(id);
        assertEquals("FAILED", run.get("state").getAsString());
        assertEquals(3, run.get("exitCode").getAsInt());
    }

    @Test
    void cancelStopsARunningRun() throws Exception {
        Response created = http("POST", "/api/runs", validSpec("slow").toString());
        String id = created.json().get("id").getAsString();
        long deadline = System.currentTimeMillis() + 20_000;
        while (!"create_attack_dataset".equals(stepOf(id)) && System.currentTimeMillis() < deadline) {
            Thread.sleep(100);
        }
        assertEquals(204, http("DELETE", "/api/runs/" + id, null).status);
        JsonObject run = waitForEnd(id);
        assertEquals("CANCELLED", run.get("state").getAsString());
    }

    // --- plumbing ----------------------------------------------------------------

    private String stepOf(String id) throws IOException {
        JsonObject run = http("GET", "/api/runs/" + id, null).json().getAsJsonObject("run");
        return run.has("step") && !run.get("step").isJsonNull() ? run.get("step").getAsString() : null;
    }

    private JsonObject waitForEnd(String id) throws Exception {
        long deadline = System.currentTimeMillis() + 60_000;
        while (System.currentTimeMillis() < deadline) {
            JsonObject run = http("GET", "/api/runs/" + id, null).json().getAsJsonObject("run");
            String state = run.get("state").getAsString();
            if (!state.equals("QUEUED") && !state.equals("RUNNING")) return run;
            Thread.sleep(100);
        }
        throw new AssertionError("run " + id + " did not finish");
    }

    static final class Response {
        int status;
        String body;
        String disposition;

        JsonObject json() {
            return JsonParser.parseString(body).getAsJsonObject();
        }
    }

    private Response http(String method, String path, String body) throws IOException {
        HttpURLConnection c = (HttpURLConnection) URI.create("http://127.0.0.1:" + port + path).toURL().openConnection();
        c.setRequestMethod(method);
        if (body != null) {
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            try (OutputStream out = c.getOutputStream()) {
                out.write(body.getBytes(StandardCharsets.UTF_8));
            }
        }
        Response r = new Response();
        r.status = c.getResponseCode();
        r.disposition = c.getHeaderField("Content-Disposition");
        InputStream in = r.status >= 400 ? c.getErrorStream() : c.getInputStream();
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        if (in != null) {
            try (InputStream i = in) {
                i.transferTo(buf);
            }
        }
        r.body = buf.toString(StandardCharsets.UTF_8);
        return r;
    }
}
