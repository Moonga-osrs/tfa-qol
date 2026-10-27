package tv.moonga.tfaqol;

import static org.junit.Assert.*;

import java.util.*;
import org.junit.*;

public class XarthRulesTest {
  private XarthRules rules;
  private XarthRules.Hit h;

  private XarthRules.Signal signal(XarthRules.Source s, int id, String target) {
    return new XarthRules.Signal(s, id, 2900, 3000, 100, 100, 0, target);
  }

  @Before
  public void setup() {
    rules = new XarthRules();
    h = new XarthRules.Hit();
    h.player = "A";
    h.x = 100;
    h.y = 100;
    h.plane = 0;
    h.cycle = 3000;
    h.tick = 100;
    h.amount = 6;
    h.type = 17;
  }

  private XarthRules.Result result(XarthRules.Signal... signals) {
    return rules.match(h, Arrays.asList(signals));
  }

  @Test
  public void explosionHitsTheWholePlaneRegardlessOfPrayer() {
    h.x = 180;
    h.y = 200;
    h.magicPrayer = true;
    h.rangedPrayer = true;
    assertEquals(
        MistakeKind.XARTH_EXPLOSION,
        result(signal(XarthRules.Source.EXPLOSION, 14989, null)).mistake);
    h.plane = 1;
    assertNull(result(signal(XarthRules.Source.EXPLOSION, 14989, null)).mistake);
  }

  @Test
  public void explosionDoesNotCountStatusZeroLateOrAmbiguousDamage() {
    XarthRules.Signal s = signal(XarthRules.Source.EXPLOSION, 14989, null);
    for (int type : new int[] {74, 67}) {
      h.type = type;
      assertNull(result(s).mistake);
    }
    h.type = 17;
    h.amount = 0;
    assertNull(result(s).mistake);
    h.amount = 6;
    h.cycle = 3011;
    assertNull(result(s).mistake);
    h.cycle = 3000;
    rules.fire(100, 100, 0, 2990, true);
    assertEquals("AMBIGUOUS", result(s).status);
    assertNull(result(s).mistake);
  }

  @Test
  public void correctMagicPrayerChipIsNotAMistakeAtAnyAmount() {
    h.magicPrayer = true;
    for (int n : new int[] {1, 4, 6, 20}) {
      h.amount = n;
      assertNull(result(signal(XarthRules.Source.BASIC_MAGIC, 4034, "A")).mistake);
      assertEquals(
          "BASIC_MAGIC_CONTEXT", result(signal(XarthRules.Source.BASIC_MAGIC, 4034, "A")).status);
    }
  }

  @Test
  public void aTargetedFireballOffMagicUsesObservedPrayerRatherThanDamageAmount() {
    assertEquals(
        MistakeKind.XARTH_MAGIC, result(signal(XarthRules.Source.BASIC_MAGIC, 4034, "A")).mistake);
  }

  @Test
  public void groundTargetFireballIsNotAssumedToBeOffPrayer() {
    assertNull(result(signal(XarthRules.Source.BASIC_MAGIC, 4034, null)).mistake);
  }

  @Test
  public void burnNeverCountsEvenWithCoincidentRangedAndFire() {
    h.type = 74;
    rules.fire(100, 100, 0, 2990, true);
    assertNull(result(signal(XarthRules.Source.RANGED, 4374, "A")).mistake);
    assertEquals("BURN_STATUS", result(signal(XarthRules.Source.RANGED, 4374, "A")).status);
  }

  @Test
  public void bleedNeverCountsAsFreshMeleeOrRanged() {
    h.type = 67;
    assertNull(result(signal(XarthRules.Source.MELEE, 14983, null)).mistake);
    assertEquals("BLEED_STATUS", result().status);
  }

  @Test
  public void rangedRequiresMissingMissilesAtImpact() {
    assertEquals(
        MistakeKind.XARTH_RANGE, result(signal(XarthRules.Source.RANGED, 4374, "A")).mistake);
    h.rangedPrayer = true;
    assertNull(result(signal(XarthRules.Source.RANGED, 4374, "A")).mistake);
  }

  @Test
  public void rangedTargetMustBeTheDamagedPlayer() {
    assertNull(result(signal(XarthRules.Source.RANGED, 4374, "B")).mistake);
  }

  @Test
  public void aoeMatchesExactTileAndTime() {
    assertEquals(MistakeKind.XARTH_AOE, result(signal(XarthRules.Source.AOE, 4378, null)).mistake);
    h.x = 101;
    assertNull(result(signal(XarthRules.Source.AOE, 4378, null)).mistake);
    h.x = 100;
    h.cycle = 3011;
    assertNull(result(signal(XarthRules.Source.AOE, 4378, null)).mistake);
  }

  @Test
  public void projectileAndFloorOfSameAoeAreOneFamily() {
    rules.add(signal(XarthRules.Source.AOE, 4379, null));
    assertEquals(1, result(signal(XarthRules.Source.AOE, 4378, null)).sources.size());
  }

  @Test
  public void meleeUsesThreeByThreeFootprint() {
    XarthRules.Signal s =
        new XarthRules.Signal(XarthRules.Source.MELEE, 14983, 3000, 3000, 100, 100, 0, null);
    h.x = 103;
    assertEquals(MistakeKind.XARTH_MELEE, result(s).mistake);
    h.x = 104;
    assertNull(result(s).mistake);
  }

  @Test
  public void ordinaryDamageOnActiveFireCanCountButNotAfterDespawn() {
    rules.fire(100, 100, 0, 2990, true);
    assertEquals(MistakeKind.XARTH_FIRE, result().mistake);
    rules.fire(100, 100, 0, 2999, false);
    assertNull(result().mistake);
  }

  @Test
  public void overlappingFireAndMagicAreUncertain() {
    rules.fire(100, 100, 0, 2990, true);
    assertNull(result(signal(XarthRules.Source.BASIC_MAGIC, 4034, "A")).mistake);
    assertEquals("AMBIGUOUS", result(signal(XarthRules.Source.BASIC_MAGIC, 4034, "A")).status);
  }

  @Test
  public void zeroHitsAndWrongPlaneNeverCount() {
    h.amount = 0;
    assertNull(result(signal(XarthRules.Source.AOE, 4378, null)).mistake);
    h.amount = 5;
    h.plane = 1;
    assertNull(result(signal(XarthRules.Source.AOE, 4378, null)).mistake);
  }

  @Test
  public void activeFirePersistsAndExpiredSignalsDoNot() {
    rules.fire(100, 100, 0, 2800, true);
    rules.add(new XarthRules.Signal(XarthRules.Source.MELEE, 14983, 2800, 2800, 100, 100, 0, null));
    rules.prune(3000);
    assertEquals(Collections.singleton(XarthRules.Source.FIRE), result().sources);
    rules.clear();
    assertTrue(result().sources.isEmpty());
  }
}
