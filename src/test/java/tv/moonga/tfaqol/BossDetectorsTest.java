package tv.moonga.tfaqol;

import static org.junit.Assert.*;

import java.util.*;
import org.junit.Test;

public class BossDetectorsTest {
  @Test
  public void allEightBossesHaveAnOrderedScaffold() {
    assertEquals(8, BossDetectors.all().size());
    assertEquals(
        Arrays.asList(
            "Archive Guardian",
            "Ezekiel",
            "Bork and the Smorks",
            "Moia",
            "Dreadnoth the Jellied",
            "Dagon'hai Elder and monks",
            "Xarth",
            "Ocellus"),
        Arrays.asList(Arrays.stream(TfaBoss.values()).map(b -> b.label).toArray(String[]::new)));
  }

  @Test
  public void onlyVerifiedEzekielRulesAreActive() {
    for (TfaBoss b : TfaBoss.values()) {
      BossMistakeDetector d = BossDetectors.forBoss(b);
      assertEquals(b, d.boss());
      assertEquals(
          (b == TfaBoss.EZEKIEL
              || b == TfaBoss.ARCHIVE_GUARDIAN
              || b == TfaBoss.BORK_AND_SMORKS
              || b == TfaBoss.OCELLUS
              || b == TfaBoss.DREADNOTH
              || b == TfaBoss.XARTH),
          d.available());
      if (!d.available()) {
        assertFalse(d.matchesNpc(16652));
        assertFalse(d.startsEncounter(14685));
        assertFalse(d.endsEncounter(14697));
        assertNull(d.floorMechanic(4394));
      }
    }
  }

  @Test
  public void unknownNpcsNeverBecomeRaidEncounters() {
    assertNull(BossDetectors.forNpc(-1));
    assertNull(BossDetectors.forNpc(999));
    assertEquals(TfaBoss.EZEKIEL, BossDetectors.forNpc(16652).boss());
  }

  @Test
  public void ezekielRulesArePreserved() {
    BossMistakeDetector d = BossDetectors.forBoss(TfaBoss.EZEKIEL);
    assertTrue(d.startsEncounter(14687));
    assertTrue(d.endsEncounter(14697));
    assertEquals("Tegelzwaai", d.floorMechanic(4394));
    assertEquals("Zwaardgolf", d.floorMechanic(4172));
    assertEquals("Tegeldans", d.floorMechanic(4198));
  }

  @Test
  public void pendingBossDoesNotPretendToBeTracked() {
    MistakeLedger l = new MistakeLedger();
    l.begin(TfaBoss.MOIA, 1, 30, false);
    assertFalse(l.active());
    assertNull(l.current());
  }
}
