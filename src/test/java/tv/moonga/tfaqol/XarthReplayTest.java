package tv.moonga.tfaqol;

import static org.junit.Assert.*;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import org.junit.Test;

public class XarthReplayTest {
  private XarthRules.Signal signal(JsonArray s) {
    return new XarthRules.Signal(
        XarthRules.Source.valueOf(s.get(0).getAsString()),
        s.get(1).getAsInt(),
        s.get(2).getAsInt(),
        s.get(3).getAsInt(),
        s.get(4).getAsInt(),
        s.get(5).getAsInt(),
        s.get(6).getAsInt(),
        s.get(7).isJsonNull() ? null : s.get(7).getAsString());
  }

  @Test
  public void allThreeFightsKeepStatusSeparateAndPreserveProtectedMagicChip() throws Exception {
    JsonObject fixture;
    try (Reader reader =
        new InputStreamReader(
            new GZIPInputStream(getClass().getResourceAsStream("xarth-replay.json.gz")),
            StandardCharsets.UTF_8)) {
      fixture = new JsonParser().parse(reader).getAsJsonObject();
    }
    int hits = 0, burn = 0, bleed = 0;
    Map<String, XarthRules.Result> examples = new HashMap<>();
    List<Map<String, Object>> audit = new ArrayList<>();
    for (JsonElement re : fixture.getAsJsonArray("runs")) {
      JsonObject run = re.getAsJsonObject();
      XarthRules rules = new XarthRules();
      for (JsonElement s : run.getAsJsonArray("signals")) rules.add(signal(s.getAsJsonArray()));
      List<XarthRules.Signal> projectiles = new ArrayList<>();
      for (JsonElement s : run.getAsJsonArray("projectiles"))
        projectiles.add(signal(s.getAsJsonArray()));
      for (JsonElement he : run.getAsJsonArray("hits")) {
        JsonObject j = he.getAsJsonObject();
        XarthRules.Hit h = new XarthRules.Hit();
        h.player = j.get("player").getAsString();
        h.tick = j.get("tick").getAsInt();
        h.cycle = j.get("cycle").getAsInt();
        h.amount = j.get("amount").getAsInt();
        h.type = j.get("type").getAsInt();
        h.x = j.get("x").getAsInt();
        h.y = j.get("y").getAsInt();
        h.plane = j.get("plane").getAsInt();
        h.magicPrayer = true;
        h.rangedPrayer = true;
        JsonElement prayer = j.get("localPrayer");
        if (prayer != null && !prayer.isJsonNull()) {
          String before = prayer.getAsJsonObject().get("before").toString();
          h.magicPrayer = before.contains("PROTECT_FROM_MAGIC");
          h.rangedPrayer = before.contains("PROTECT_FROM_MISSILES");
        }
        XarthRules.Result result = rules.match(h, projectiles);
        String key =
            run.get("id").getAsString()
                + ":"
                + h.tick
                + ":"
                + j.get("name").getAsString()
                + ":"
                + h.amount;
        examples.put(key, result);
        if (h.type == 74) {
          burn++;
          assertEquals("BURN_STATUS", result.status);
          assertNull(result.mistake);
        }
        if (h.type == 67) {
          bleed++;
          assertEquals("BLEED_STATUS", result.status);
          assertNull(result.mistake);
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("example", key);
        row.put("status", result.status);
        row.put("sources", result.sources);
        row.put("mistake", result.mistake);
        row.put(
            "note",
            prayer == null || prayer.isJsonNull()
                ? "Other-player prayers unavailable; protected state is synthetic for replay"
                    + " validation"
                : "Recorded local prayer snapshots");
        audit.add(row);
        hits++;
      }
    }
    assertEquals(260, hits);
    assertEquals(193, burn);
    assertEquals(10, bleed);
    assertEquals("BASIC_MAGIC_CONTEXT", examples.get("x1:1826:Player013:6").status);
    assertNull(examples.get("x1:1826:Player013:6").mistake);
    assertEquals("BASIC_MAGIC_CONTEXT", examples.get("x2:5470:Player013:4").status);
    assertEquals(MistakeKind.XARTH_MELEE, examples.get("x3:1831:Player003:23").mistake);
    assertEquals(MistakeKind.XARTH_MELEE, examples.get("x3:1852:Player003:10").mistake);
    assertEquals(MistakeKind.XARTH_AOE, examples.get("x2:5413:Player016:16").mistake);
    assertEquals(MistakeKind.XARTH_AOE, examples.get("x2:5475:Player009:15").mistake);
    assertEquals("BURN_STATUS", examples.get("x3:1964:Player011:1").status);
    assertTrue(examples.get("x3:1964:Player011:1").sources.contains(XarthRules.Source.FIRE));
    Files.createDirectories(Path.of("validation"));
    Files.writeString(
        Path.of("validation/xarth-replay.json"),
        new GsonBuilder().setPrettyPrinting().create().toJson(audit));
  }
}
