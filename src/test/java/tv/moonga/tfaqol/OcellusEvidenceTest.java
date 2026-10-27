package tv.moonga.tfaqol;

import static org.junit.Assert.*;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.Test;

public class OcellusEvidenceTest {
  private OcellusEvidence.Signal signal(
      int id, int x, int cycle, String player, String source, boolean object) {
    return new OcellusEvidence.Signal(
        id, x, 20, 1, cycle, object ? Integer.MAX_VALUE : cycle, player, source, object);
  }

  @Test
  public void damageRequiresTheTilePlaneAndTiming() {
    OcellusEvidence e = new OcellusEvidence();
    e.add(signal(4236, 10, 100, null, "floor", false));
    assertEquals(MistakeKind.OC_CRACKS, e.match("A", 10, 20, 1, 110).kind());
    assertNull(e.match("A", 11, 20, 1, 100).kind());
    assertNull(e.match("A", 10, 20, 0, 100).kind());
    assertNull(e.match("A", 10, 20, 1, 111).kind());
  }

  @Test
  public void ambiguousSourcesNeverIncrementCounters() {
    OcellusEvidence e = new OcellusEvidence();
    e.add(signal(4236, 10, 100, null, "floor", false));
    e.add(signal(4297, 10, 100, "A", "projectile", false));
    assertEquals("AMBIGUOUS", e.match("A", 10, 20, 1, 100).status());
    assertNull(e.match("A", 10, 20, 1, 100).kind());
  }

  @Test
  public void severalVariantsOfOneHazardCountAsOneFamily() {
    OcellusEvidence e = new OcellusEvidence();
    e.add(signal(4236, 10, 100, null, "floor", false));
    e.add(signal(4237, 10, 100, null, "floor", false));
    assertEquals(1, e.match("A", 10, 20, 1, 100).families.size());
  }

  @Test
  public void targetedShotsDoNotHitTheWrongPlayer() {
    OcellusEvidence e = new OcellusEvidence();
    e.add(signal(4269, 10, 100, "A", "projectile", false));
    assertEquals(MistakeKind.OC_PORTAL, e.match("A", 12, 20, 1, 100).kind());
    assertNull(e.match("B", 10, 20, 1, 100).kind());
  }

  @Test
  public void projectileRetargetUpdatesReplaceStaleEvidence() {
    OcellusEvidence e = new OcellusEvidence();
    e.projectiles(Arrays.asList(signal(4269, 10, 100, "A", "projectile", false)));
    e.projectiles(Arrays.asList(signal(4269, 10, 100, "B", "projectile", false)));
    assertNull(e.match("A", 10, 20, 1, 100).kind());
    assertEquals(MistakeKind.OC_PORTAL, e.match("B", 10, 20, 1, 100).kind());
  }

  @Test
  public void soulWithdrawalHasOneTickDelayAndIsNotAMistake() {
    OcellusEvidence e = new OcellusEvidence();
    e.add(signal(4313, 10, 100, "A", "spot", false));
    assertEquals("soul-fragments", e.match("A", 10, 20, 1, 130).families.iterator().next());
    assertNull(e.match("A", 10, 20, 1, 130).kind());
    assertTrue(e.match("A", 10, 20, 1, 100).families.isEmpty());
  }

  @Test
  public void residuesStopAtDespawnAndNoOtherTileMatches() {
    OcellusEvidence e = new OcellusEvidence();
    e.add(signal(62701, 10, 100, null, "object", true));
    assertEquals(MistakeKind.OC_BOMB, e.match("A", 10, 20, 1, 160).kind());
    assertNull(e.match("A", 11, 20, 1, 160).kind());
    e.removeObject(62701, 10, 20, 1, 170);
    assertNull(e.match("A", 10, 20, 1, 171).kind());
  }

  @Test
  public void soulChipElementalAndDeflectAreNotAutomaticMistakes() {
    for (String family :
        Arrays.asList(
            "water",
            "fire",
            "magic-impact",
            "soul-fragments",
            "hybrid",
            "entangle",
            "deflect-arena",
            "gfx-4271")) assertNull(family, OcellusEvidence.mistake(family));
  }

  @Test
  public void braziersHealingAndPlayerWeaponReturnsAreNotHazardRules() {
    for (int id : new int[] {2727, 2729, 1965, 62691, 62692})
      assertNull(OcellusEvidence.family(id, true));
    assertNull(OcellusEvidence.mistake(OcellusEvidence.family(4303, false)));
  }

  @Test
  public void oldSignalsArePrunedButActiveObjectsRemain() {
    OcellusEvidence e = new OcellusEvidence();
    e.add(signal(4236, 10, 100, null, "floor", false));
    e.add(signal(62701, 11, 100, null, "object", true));
    e.prune(300);
    assertTrue(e.match("A", 10, 20, 1, 100).families.isEmpty());
    assertEquals(MistakeKind.OC_BOMB, e.match("A", 11, 20, 1, 300).kind());
  }

  @Test
  public void realSegmentsPreserveAtlasSourceFamilies() throws Exception {
    JsonArray runs;
    try (Reader reader =
        new InputStreamReader(
            new java.util.zip.GZIPInputStream(
                getClass().getResourceAsStream("ocellus-replay.json.gz")),
            StandardCharsets.UTF_8)) {
      runs = new JsonParser().parse(reader).getAsJsonArray();
    }
    int tested = 0;
    List<String> failures = new ArrayList<>();
    for (JsonElement re : runs) {
      JsonObject run = re.getAsJsonObject();
      OcellusEvidence e = new OcellusEvidence();
      for (JsonElement se : run.getAsJsonArray("signals")) {
        JsonArray s = se.getAsJsonArray();
        e.add(
            new OcellusEvidence.Signal(
                s.get(0).getAsInt(),
                s.get(1).getAsInt(),
                s.get(2).getAsInt(),
                s.get(3).getAsInt(),
                s.get(4).getAsInt(),
                s.get(5).getAsInt(),
                s.get(6).isJsonNull() ? null : s.get(6).getAsString(),
                s.get(7).getAsString(),
                s.get(8).getAsBoolean()));
      }
      for (JsonElement he : run.getAsJsonArray("hits")) {
        JsonObject h = he.getAsJsonObject();
        Set<String> expected = new TreeSet<>();
        for (JsonElement f : h.getAsJsonArray("families")) expected.add(f.getAsString());
        List<OcellusEvidence.Signal> spots = new ArrayList<>();
        for (JsonElement spot : h.getAsJsonArray("spots")) {
          JsonArray s = spot.getAsJsonArray();
          spots.add(
              new OcellusEvidence.Signal(
                  s.get(0).getAsInt(),
                  h.get("x").getAsInt(),
                  h.get("y").getAsInt(),
                  h.get("plane").getAsInt(),
                  s.get(1).getAsInt(),
                  s.get(1).getAsInt(),
                  h.get("player").getAsString(),
                  "spot",
                  false));
        }
        Set<String> actual =
            new TreeSet<>(
                e.match(
                        h.get("player").getAsString(),
                        h.get("x").getAsInt(),
                        h.get("y").getAsInt(),
                        h.get("plane").getAsInt(),
                        h.get("cycle").getAsInt(),
                        spots)
                    .families);
        if (!actual.equals(expected) && failures.size() < 10)
          failures.add(h.get("id") + " expected=" + expected + " actual=" + actual);
        tested++;
      }
    }
    assertEquals(13, runs.size());
    assertEquals(5121, tested);
    assertTrue(failures.toString(), failures.isEmpty());
  }
}
