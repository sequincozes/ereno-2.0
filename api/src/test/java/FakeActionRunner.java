import java.io.FileReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Stand-in for ActionRunner in RunsApiTest: reads a step config and writes the
 * file the real action would, so the run lifecycle can be tested in
 * milliseconds. An output filename starting with "slow" makes it sleep
 * (to test cancel); one starting with "boom" makes it exit 3.
 */
public class FakeActionRunner {
    public static void main(String[] args) throws Exception {
        JsonObject cfg;
        try (FileReader r = new FileReader(args[0], StandardCharsets.UTF_8)) {
            cfg = JsonParser.parseReader(r).getAsJsonObject();
        }
        String action = cfg.get("action").getAsString();
        System.out.println("fake " + action);
        if (action.equals("create_attack_dataset")) {
            JsonObject out = cfg.getAsJsonObject("output");
            String filename = out.get("filename").getAsString();
            if (filename.startsWith("slow")) Thread.sleep(60_000);
            if (filename.startsWith("boom")) System.exit(3);
            Path dir = Path.of(out.get("directory").getAsString());
            Files.createDirectories(dir);
            Files.writeString(dir.resolve(filename), "label\nnormal\nrandom_replay\n");
        }
    }
}
