package tv.moonga.tfaqol;

import static org.junit.Assert.*;

import java.util.*;
import org.junit.*;

public class DreadnothRulesTest {
  private DreadnothRules rules;
  private DreadnothRules.Hit hit;

  private DreadnothRules.Signal signal(DreadnothRules.Source source, int cycle, String target) {
    return new DreadnothRules.Signal(
        source, cycle, 100, 100, 0, 5, 4211, target, cycle - 100, cycle);
  }

  @Before
  public void setup() {
    rules = new DreadnothRules();
    hit = new DreadnothRules.Hit();
    hit.player = "A";
    hit.x = 105;
    hit.y = 102;
    hit.plane = 0;
    hit.cycle = 3000;
    hit.amount = 4;
  }

  private DreadnothRules.Result result() {
    return rules.match(hit, Collections.emptyList());
  }

  @Test
  public void standingNextToBossWithMeleeDamageCountsEvenOnPrayer() {
    rules.add(signal(DreadnothRules.Source.MELEE, 3000, null));
    hit.meleePrayer = true;
    assertTrue(result().mistakes.contains(MistakeKind.DREAD_MELEE));
    hit.x = 106;
    assertTrue(result().sources.isEmpty());
  }

  @Test
  public void stompRequiresMissingMeleePrayerAtImpact() {
    rules.add(signal(DreadnothRules.Source.STOMP, 3000, null));
    assertTrue(result().mistakes.contains(MistakeKind.DREAD_STOMP));
    hit.meleePrayer = true;
    assertTrue(result().mistakes.isEmpty());
    assertEquals("PROTECTED", result().status());
  }

  @Test
  public void aStompIsNotInferredOneTickAfterItsActualImpact() {
    rules.add(signal(DreadnothRules.Source.STOMP, 2970, null));
    assertTrue(result().sources.isEmpty());
  }

  @Test
  public void rangedHitsRequireMissingMissilesAtImpact() {
    DreadnothRules.Signal s = signal(DreadnothRules.Source.RANGE, 3000, "A");
    assertTrue(rules.match(hit, Arrays.asList(s)).mistakes.contains(MistakeKind.DREAD_RANGE));
    hit.rangedPrayer = true;
    assertTrue(rules.match(hit, Arrays.asList(s)).mistakes.isEmpty());
  }

  @Test
  public void enrageRangeHasItsOwnCounter() {
    assertTrue(
        rules
            .match(hit, Arrays.asList(signal(DreadnothRules.Source.ENRAGE_RANGE, 3000, "A")))
            .mistakes
            .contains(MistakeKind.DREAD_ENRAGE_RANGE));
  }

  @Test
  public void jellyAttacksOnlyMatchTheirActualTarget() {
    rules.add(signal(DreadnothRules.Source.JELLY, 3000, "B"));
    assertTrue(result().sources.isEmpty());
    rules.add(signal(DreadnothRules.Source.JELLY, 3000, "A"));
    assertTrue(result().mistakes.contains(MistakeKind.DREAD_JELLY));
    hit.meleePrayer = true;
    assertTrue(result().mistakes.isEmpty());
  }

  @Test
  public void bloodRequiresActiveTileAndPositiveDamage() {
    rules.ground(105, 102, 0, 2990, true);
    assertTrue(result().mistakes.contains(MistakeKind.DREAD_BLOOD));
    hit.amount = 0;
    assertTrue(result().mistakes.isEmpty());
    hit.amount = 7;
    rules.ground(105, 102, 0, 2999, false);
    assertTrue(result().sources.isEmpty());
  }

  @Test
  public void landingUnderBossCountsWithCorrectMeleePrayer() {
    rules.add(signal(DreadnothRules.Source.LANDING, 3000, null));
    hit.underBoss = true;
    hit.meleePrayer = true;
    assertEquals(Collections.singleton(MistakeKind.DREAD_UNDER), result().mistakes);
  }

  @Test
  public void landingUnderBossOffPrayerPreservesBothIndependentErrors() {
    rules.add(signal(DreadnothRules.Source.LANDING, 3000, null));
    hit.underBoss = true;
    assertEquals(
        new HashSet<>(Arrays.asList(MistakeKind.DREAD_LANDING, MistakeKind.DREAD_UNDER)),
        result().mistakes);
  }

  @Test
  public void protectedLandingOutsideFootprintIsNotAFault() {
    rules.add(signal(DreadnothRules.Source.LANDING, 3000, null));
    hit.meleePrayer = true;
    assertTrue(result().mistakes.isEmpty());
  }

  @Test
  public void sidesteppingBloodProjectileAvoidsItsMistake() {
    DreadnothRules.Signal s = signal(DreadnothRules.Source.BLOOD_SHOT, 3000, null);
    hit.x = 100;
    hit.y = 100;
    assertTrue(
        rules.match(hit, Arrays.asList(s)).mistakes.contains(MistakeKind.DREAD_ENRAGE_BLOOD));
    hit.x = 101;
    assertTrue(rules.match(hit, Arrays.asList(s)).sources.isEmpty());
  }

  @Test
  public void projectileAndItsNewPuddleAreOneBloodImpact() {
    hit.x = 100;
    hit.y = 100;
    rules.ground(100, 100, 0, 3000, true);
    DreadnothRules.Result r =
        rules.match(hit, Arrays.asList(signal(DreadnothRules.Source.BLOOD_SHOT, 3000, null)));
    assertEquals(1, r.sources.size());
    assertEquals(Collections.singleton(MistakeKind.DREAD_ENRAGE_BLOOD), r.mistakes);
  }

  @Test
  public void anOldPuddleAndNewProjectileRemainAmbiguous() {
    hit.x = 100;
    hit.y = 100;
    rules.ground(100, 100, 0, 2900, true);
    assertEquals(
        "AMBIGUOUS",
        rules
            .match(hit, Arrays.asList(signal(DreadnothRules.Source.BLOOD_SHOT, 3000, null)))
            .status());
  }

  @Test
  public void overlappingMeleeAndBloodAreNotAssignedByDamageAmount() {
    rules.add(signal(DreadnothRules.Source.MELEE, 3000, null));
    rules.ground(105, 102, 0, 2990, true);
    for (int amount : new int[] {3, 8, 20, 43}) {
      hit.amount = amount;
      assertEquals("AMBIGUOUS", result().status());
      assertTrue(result().mistakes.isEmpty());
    }
  }

  @Test
  public void wrongPlaneAndWrongTargetDoNotMatch() {
    hit.plane = 1;
    rules.add(signal(DreadnothRules.Source.STOMP, 3000, null));
    assertTrue(result().sources.isEmpty());
    hit.plane = 0;
    assertTrue(
        rules
            .match(hit, Arrays.asList(signal(DreadnothRules.Source.RANGE, 3000, "B")))
            .mistakes
            .contains(MistakeKind.DREAD_STOMP));
  }

  @Test
  public void expiredAttacksArePrunedAndActiveBloodIsKept() {
    rules.add(signal(DreadnothRules.Source.STOMP, 2800, null));
    rules.ground(105, 102, 0, 2800, true);
    rules.prune(3000);
    assertEquals(Collections.singleton(DreadnothRules.Source.BLOOD_TILE), result().sources);
    rules.clear();
    assertTrue(result().sources.isEmpty());
  }

  @Test
  public void carriedStatusDamageIsNotANewJellyRoomMistake() {
    rules.ground(105, 102, 0, 2990, true);
    for (int type : new int[] {74, 67}) {
      hit.type = type;
      assertTrue(result().mistakes.isEmpty());
      assertTrue(result().status().endsWith("STATUS"));
    }
  }
}
