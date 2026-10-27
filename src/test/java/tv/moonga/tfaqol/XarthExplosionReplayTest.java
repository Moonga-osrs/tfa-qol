package tv.moonga.tfaqol;

import static org.junit.Assert.*;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import org.junit.Test;

public class XarthExplosionReplayTest {
  @Test
  public void recordedExplosionClassifiesSixImpactsWithoutUsingDamageThresholds() throws Exception {
    JsonObject f;
    try (Reader reader =
        new InputStreamReader(
            getClass().getResourceAsStream("xarth-explosion-20261027.json"),
            StandardCharsets.UTF_8)) {
      f = new Gson().fromJson(reader, JsonObject.class);
    }
    int start = f.get("animationCycle").getAsInt();
    assertEquals(10688, f.get("animationTick").getAsInt());
    XarthRules rules = new XarthRules();
    rules.add(
        new XarthRules.Signal(
            XarthRules.Source.EXPLOSION, 14989, start, start + 90, 13790, 6622, 0, null));
    int count = 0, damage = 0;
    for (JsonElement element : f.getAsJsonArray("hits")) {
      JsonObject j = element.getAsJsonObject();
      XarthRules.Hit h = new XarthRules.Hit();
      h.player = "Player" + (++count);
      h.cycle = j.get("cycle").getAsInt();
      h.tick = j.get("tick").getAsInt();
      h.plane = j.get("plane").getAsInt();
      h.amount = j.get("amount").getAsInt();
      h.type = j.get("type").getAsInt();
      assertEquals(10691, h.tick);
      assertEquals(MistakeKind.XARTH_EXPLOSION, rules.match(h, Collections.emptyList()).mistake);
      damage += h.amount;
    }
    assertEquals(6, count);
    assertEquals(240, damage);
    assertEquals(2, f.getAsJsonArray("charges").size());
  }
}
