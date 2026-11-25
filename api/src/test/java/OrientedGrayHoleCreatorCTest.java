import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import br.ufu.facom.ereno.attacks.uc09.creator.OrientedGrayHoleCreatorC;
import br.ufu.facom.ereno.config.AttackConfig;
import br.ufu.facom.ereno.config.ConfigLoader;
import br.ufu.facom.ereno.general.ProtectionIED;
import br.ufu.facom.ereno.messages.Goose;

public class OrientedGrayHoleCreatorCTest {

    /** 30 messages; stNum goes 1 -> 2 at index 10 and 2 -> 3 at index 20. sqNum = index. */
    private static ArrayList<Goose> stream() {
        ArrayList<Goose> list = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            int stNum = i < 10 ? 1 : i < 20 ? 2 : 3;
            list.add(new Goose(0, stNum, i, i * 0.1, i * 0.1, "normal"));
        }
        return list;
    }

    private static List<Integer> run(String json, int target) {
        AttackConfig cfg = new AttackConfig(JsonParser.parseString(json).getAsJsonObject());
        ProtectionIED ied = new ProtectionIED("oriented_grayhole") {
            @Override
            public void run(int n) { }
        };
        new OrientedGrayHoleCreatorC(stream(), cfg).generate(ied, target);
        for (Goose g : ied.getMessages()) assertEquals("oriented_grayhole", g.getLabel());
        return ied.getMessages().stream().map(Goose::getSqNum).collect(Collectors.toList());
    }

    @Test
    void deterministicBurstDropsTheWindowAfterEveryChange() {
        ConfigLoader.setSeed(1L);
        List<Integer> kept = run("{\"attackType\":\"oriented_grayhole\",\"approach\":\"DETERMINISTIC_BURST\","
                + "\"burstLen\":{\"min\":3,\"max\":3}}", 100);
        for (int dropped : new int[]{10, 11, 12, 20, 21, 22}) assertTrue(!kept.contains(dropped), "kept " + dropped);
        assertEquals(24, kept.size());
        assertTrue(kept.contains(9) && kept.contains(13) && kept.contains(23));
    }

    @Test
    void randomicBurstWithCertainProbabilityMatchesDeterministic() {
        ConfigLoader.setSeed(1L);
        List<Integer> kept = run("{\"attackType\":\"oriented_grayhole\",\"approach\":\"RANDOMIC_BURST\","
                + "\"discardProb\":1.0,\"burstLen\":{\"min\":2,\"max\":2}}", 100);
        assertEquals(26, kept.size());
        ConfigLoader.setSeed(1L);
        List<Integer> none = run("{\"attackType\":\"oriented_grayhole\",\"approach\":\"RANDOMIC_BURST\","
                + "\"discardProb\":0.0,\"burstLen\":{\"min\":2,\"max\":2}}", 100);
        assertEquals(30, none.size());
    }

    @Test
    void randomicMessageOnlyDropsInsideTheWindowAndIsSeeded() {
        String cfg = "{\"attackType\":\"oriented_grayhole\",\"approach\":\"RANDOMIC_MESSAGE\","
                + "\"discardProb\":0.5,\"burstLen\":{\"min\":5,\"max\":5}}";
        ConfigLoader.setSeed(7L);
        List<Integer> first = run(cfg, 100);
        ConfigLoader.setSeed(7L);
        assertEquals(first, run(cfg, 100), "same seed, same segment");
        for (int i = 0; i < 30; i++) {
            boolean inWindow = (i >= 10 && i < 15) || (i >= 20 && i < 25);
            if (!inWindow) assertTrue(first.contains(i), "message " + i + " is outside any window");
        }
    }

    @Test
    void stopsAtTheTargetSizeAndRejectsUnknownApproach() {
        ConfigLoader.setSeed(1L);
        assertEquals(8, run("{\"attackType\":\"oriented_grayhole\"}", 8).size());
        assertThrows(IllegalArgumentException.class,
                () -> run("{\"attackType\":\"oriented_grayhole\",\"approach\":\"SOMETIMES\"}", 10));
    }

    @Test
    void percentageProbabilityIsAccepted() {
        JsonObject o = JsonParser.parseString("{\"discardProb\":15}").getAsJsonObject();
        assertEquals(0.15, new AttackConfig(o).getProbability("discardProb", 0), 1e-9);
    }
}
