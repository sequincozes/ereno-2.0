import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import br.ufu.facom.ereno.api.RunManager;
import br.ufu.facom.ereno.api.RunPlanner;
import br.ufu.facom.ereno.api.RunSpec;
import br.ufu.facom.ereno.api.RunSpecValidator;

/**
 * Real generation through the run pipeline: create_benign + create_attack_dataset
 * in child JVMs running ActionRunner, small enough to finish in seconds.
 * Checks that every requested attack shows up with its label in the CSV and
 * that the same seed reproduces the same bytes.
 */
public class RunEndToEndTest {

    @Test
    void generatesLabelledCsvReproducibly() throws Exception {
        Path tmp = Files.createTempDirectory("runs-e2e");
        RunManager runs = new RunManager(tmp, Paths.get("").toAbsolutePath(),
                new RunPlanner(RunsApiTest.ELECTRICAL), RunManager.defaultChildCommand(), 1);
        try {
            JsonObject specJson = RunsApiTest.validSpec("e2e");
            specJson.getAsJsonArray("attacks").add(RunsApiTest.attack(RunsApiTest.readTemplate("uc08_grayhole")));
            specJson.getAsJsonArray("attacks").add(RunsApiTest.attack(RunsApiTest.readTemplate("uc11_programmable")));
            RunSpec spec = new Gson().fromJson(specJson, RunSpec.class);
            assertTrue(new RunSpecValidator(RunsApiTest.ELECTRICAL).validate(spec).isEmpty());

            String first = runAndRead(runs, spec);
            Map<String, Integer> labels = countLabels(first);
            assertTrue(labels.getOrDefault("normal", 0) > 0, labels.toString());
            assertTrue(labels.getOrDefault("random_replay", 0) > 0, labels.toString());
            assertTrue(labels.getOrDefault("grayhole", 0) > 0, labels.toString());
            assertTrue(labels.getOrDefault("programmable", 0) > 0, labels.toString());

            String second = runAndRead(runs, spec);
            assertEquals(first, second, "same spec and seed must give the same dataset");
        } finally {
            runs.shutdown();
            try (Stream<Path> w = Files.walk(tmp)) {
                w.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }
    }

    private static String runAndRead(RunManager runs, RunSpec spec) throws Exception {
        RunManager.RunStatus st = runs.submit(spec);
        long deadline = System.currentTimeMillis() + 180_000;
        while (runs.get(st.id).state == RunManager.State.QUEUED || runs.get(st.id).state == RunManager.State.RUNNING) {
            if (System.currentTimeMillis() > deadline) throw new AssertionError("e2e run timed out");
            Thread.sleep(200);
        }
        RunManager.RunStatus done = runs.get(st.id);
        assertEquals(RunManager.State.SUCCEEDED, done.state,
                done.error + "\n" + runs.logTail(st.id, 4000));
        return Files.readString(runs.runDir(st.id).resolve(done.outputFile));
    }

    private static Map<String, Integer> countLabels(String csv) throws IOException {
        List<String> lines = csv.lines().filter(l -> !l.isBlank()).collect(java.util.stream.Collectors.toList());
        Map<String, Integer> counts = new HashMap<>();
        for (String line : lines.subList(1, lines.size())) {
            String label = line.substring(line.lastIndexOf(',') + 1).trim();
            counts.merge(label, 1, Integer::sum);
        }
        return counts;
    }
}
