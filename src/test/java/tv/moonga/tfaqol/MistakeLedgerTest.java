package tv.moonga.tfaqol;

import static org.junit.Assert.*;

import org.junit.Test;

public class MistakeLedgerTest {
  private MistakeLedger active() {
    MistakeLedger l = new MistakeLedger();
    l.begin(10, 100, false);
    return l;
  }

  @Test
  public void noRaidMeansNoDamageOrDeaths() {
    MistakeLedger l = new MistakeLedger();
    l.hit("A", 10, true, 1, 1, 0, 0, 10, 100);
    l.death("A", 10, 100);
    l.drain(200, true);
    assertNull(l.current());
  }

  @Test
  public void groundGraphicCanArriveAfterItsHitsplat() {
    MistakeLedger l = active();
    l.hit("A", 6, true, 51, 52, 0, 0, 10, 100);
    l.floor(4394, 51, 52, 0, 0, 107);
    l.drain(129, false);
    assertTrue(l.current().events.isEmpty());
    l.drain(130, false);
    assertEquals("Possible Tegelzwaai", l.current().events.get(0).type);
    assertTrue(l.current().events.get(0).evidence.contains("unconfirmed"));
  }

  @Test
  public void swordStagesDoNotCountOneHitTwice() {
    MistakeLedger l = active();
    l.floor(4171, 51, 52, 0, 0, 74);
    l.floor(4172, 51, 52, 0, 0, 104);
    l.hit("A", 24, true, 51, 52, 0, 0, 10, 100);
    l.drain(130, false);
    assertEquals(1, l.current().events.size());
    assertEquals("Possible Zwaardgolf", l.current().events.get(0).type);
    assertEquals(24, l.current().events.get(0).damage);
  }

  @Test
  public void danceGraphicIsPossibleNotConfirmed() {
    MistakeLedger l = active();
    l.floor(4197, 51, 52, 0, 0, 106);
    l.hit("A", 18, true, 51, 52, 0, 0, 10, 100);
    l.drain(130, false);
    assertEquals("Possible Tegeldans", l.current().events.get(0).type);
  }

  @Test
  public void bleedIsNotBlamedOnNearbyFloorEffect() {
    MistakeLedger l = active();
    l.floor(4394, 51, 52, 0, 0, 106);
    l.hit("A", 2, false, 51, 52, 0, 0, 10, 100);
    l.drain(130, false);
    assertTrue(l.current().events.isEmpty());
  }

  @Test
  public void ambiguousMechanicsAreNotAttributed() {
    MistakeLedger l = active();
    l.floor(4394, 51, 52, 0, 0, 106);
    l.floor(4172, 51, 52, 0, 0, 104);
    l.hit("A", 20, true, 51, 52, 0, 0, 10, 100);
    l.drain(130, false);
    assertTrue(l.current().events.isEmpty());
  }

  @Test
  public void differentPlaneViewTileOrTimeDoesNotMatch() {
    MistakeLedger l = active();
    l.floor(4394, 51, 52, 1, 0, 106);
    l.floor(4394, 51, 52, 0, 1, 106);
    l.floor(4394, 50, 52, 0, 0, 106);
    l.floor(4394, 51, 52, 0, 0, 150);
    l.hit("A", 10, true, 51, 52, 0, 0, 10, 100);
    l.drain(130, false);
    assertTrue(l.current().events.isEmpty());
  }

  @Test
  public void ordinaryDamageIsNotAMistake() {
    MistakeLedger l = active();
    l.hit("A", 40, true, 51, 52, 0, 0, 10, 100);
    l.drain(130, false);
    assertTrue(l.current().events.isEmpty());
  }

  @Test
  public void duplicateDeathsAndSameTickDamageMerge() {
    MistakeLedger l = active();
    l.death("A", 10, 100);
    l.death("A", 10, 101);
    l.floor(4394, 1, 1, 0, 0, 100);
    l.hit("A", 3, true, 1, 1, 0, 0, 10, 100);
    l.hit("A", 4, true, 1, 1, 0, 0, 10, 101);
    l.drain(140, false);
    assertEquals(2, l.current().events.size());
    assertEquals(1, l.current().events.stream().filter(e -> e.type.equals("Death")).count());
    assertEquals(
        7,
        l.current().events.stream().filter(e -> !e.type.equals("Death")).findFirst().get().damage);
  }

  @Test
  public void playersHaveSeparateEvents() {
    MistakeLedger l = active();
    l.floor(4394, 1, 1, 0, 0, 100);
    l.hit("A", 3, true, 1, 1, 0, 0, 10, 100);
    l.hit("B", 4, true, 1, 1, 0, 0, 10, 100);
    l.drain(140, false);
    assertEquals(2, l.current().events.size());
  }

  @Test
  public void finishFlushesPendingHitsAndStopsObservation() {
    MistakeLedger l = active();
    l.floor(4394, 1, 1, 0, 0, 100);
    l.hit("A", 10, true, 1, 1, 0, 0, 10, 100);
    l.finish(11, 110, "Boss left view; result unknown");
    assertEquals(1, l.current().events.size());
    assertFalse(l.active());
    l.hit("A", 20, true, 1, 1, 0, 0, 11, 111);
    l.drain(200, true);
    assertEquals(1, l.current().events.size());
  }

  @Test
  public void joiningAndResetAreExplicit() {
    MistakeLedger l = new MistakeLedger();
    l.begin(10, 100, true);
    assertTrue(l.current().joinedInProgress);
    l.reset();
    assertTrue(l.encounters().isEmpty());
    assertNull(l.current());
  }
}
