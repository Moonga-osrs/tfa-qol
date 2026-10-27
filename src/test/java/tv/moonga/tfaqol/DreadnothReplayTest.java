package tv.moonga.tfaqol;

import static org.junit.Assert.*;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import org.junit.Test;

public class DreadnothReplayTest {
  private DreadnothRules.Signal signal(JsonArray s) {
    return new DreadnothRules.Signal(
        DreadnothRules.Source.valueOf(s.get(0).getAsString()),
        s.get(1).getAsInt(),
        s.get(2).getAsInt(),
        s.get(3).getAsInt(),
        s.get(4).getAsInt(),
        s.get(5).getAsInt(),
        s.get(6).getAsInt(),
        s.get(7).isJsonNull() ? null : s.get(7).getAsString(),
        s.get(8).getAsInt(),
        s.get(9).getAsInt());
  }

  @Test
  public void suppliedRecordingsMatchAttackAndImpactEvidence() throws Exception {
    JsonObject fixture;
    try (Reader input =
        new InputStreamReader(
            new GZIPInputStream(getClass().getResourceAsStream("dreadnoth-replay.json.gz")),
            StandardCharsets.UTF_8)) {
      fixture = new JsonParser().parse(input).getAsJsonObject();
    }
    Map<String, String> actual = new HashMap<>();
    List<Map<String, Object>> audit = new ArrayList<>();
    int count = 0;
    for (JsonElement re : fixture.getAsJsonArray("runs")) {
      JsonObject run = re.getAsJsonObject();
      int number = run.get("run").getAsInt();
      DreadnothRules rules = new DreadnothRules();
      for (JsonElement se : run.getAsJsonArray("signals")) rules.add(signal(se.getAsJsonArray()));
      List<DreadnothRules.Signal> shots = new ArrayList<>();
      for (JsonElement se : run.getAsJsonArray("projectiles"))
        shots.add(signal(se.getAsJsonArray()));
      List<Integer> chainSizes = new ArrayList<>();
      int previous = -10000;
      for (JsonElement st : run.getAsJsonArray("stomps")) {
        int tick = st.getAsJsonObject().get("tick").getAsInt();
        if (tick - previous > 12) chainSizes.add(1);
        else chainSizes.set(chainSizes.size() - 1, chainSizes.get(chainSizes.size() - 1) + 1);
        previous = tick;
      }
      assertEquals(Arrays.asList(1, 2, 3), chainSizes);
      for (JsonElement he : run.getAsJsonArray("hits")) {
        JsonObject h = he.getAsJsonObject();
        DreadnothRules.Hit hit = new DreadnothRules.Hit();
        hit.player = h.get("player").getAsString();
        hit.cycle = h.get("cycle").getAsInt();
        hit.tick = h.get("tick").getAsInt();
        hit.x = h.get("x").getAsInt();
        hit.y = h.get("y").getAsInt();
        hit.plane = h.get("plane").getAsInt();
        hit.amount = h.get("amount").getAsInt();
        hit.underBoss = h.get("underBoss").getAsBoolean();
        DreadnothRules.Result result = rules.match(hit, shots);
        String source = new TreeSet<>(result.sources).toString();
        String key = number + ":" + hit.tick + ":" + h.get("name").getAsString() + ":" + hit.amount;
        actual.put(key, source);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("example", key);
        row.put("sources", result.sources);
        row.put("underBoss", hit.underBoss);
        row.put("amount", hit.amount);
        row.put("note", "Prayer state not present in recording; not a historical mistake count");
        audit.add(row);
        count++;
      }
    }
    assertEquals(66, count);
    assertEquals("[BLOOD_TILE]", actual.get("1:854:Player010:7"));
    assertEquals("[RANGE]", actual.get("1:875:Player002:11"));
    assertEquals("[JELLY]", actual.get("1:917:Player002:6"));
    assertEquals("[MELEE]", actual.get("1:938:Player010:18"));
    assertEquals("[MELEE, BLOOD_TILE]", actual.get("1:973:Player002:21"));
    assertEquals("[STOMP]", actual.get("2:399:Player008:19"));
    assertEquals("[STOMP]", actual.get("2:448:Player014:26"));
    assertEquals("[LANDING]", actual.get("2:532:Player008:28"));
    assertEquals("[MELEE]", actual.get("3:1262:Player017:3"));
    assertEquals("[]", actual.get("3:1408:Player011:4"));
    assertEquals("[LANDING]", actual.get("3:1495:Player003:35"));
    assertEquals("[MELEE, BLOOD_TILE]", actual.get("3:1382:Player011:20"));
    Files.createDirectories(Path.of("validation"));
    Files.writeString(
        Path.of("validation/dreadnoth-replay.json"),
        new GsonBuilder().setPrettyPrinting().create().toJson(audit));
  }
}
