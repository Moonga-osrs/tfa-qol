package tv.moonga.tfaqol;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.*;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import org.junit.*;

public class GuardianObserverTest {
  @Test
  public void survivingVineWithAnUnloadedBossDoesNotCrashOrInventAMistake() {
    NPC vine = mock(NPC.class);
    when(vine.getId()).thenReturn(16664);
    when(vine.getAnimation()).thenReturn(14768);
    observer.animation(client, vine, null);
    observer.drain(client, ledger, true, true);
    assertTrue(ledger.current().events.isEmpty());
  }

  private GuardianObserver observer;
  private Client client;
  private WorldView view;
  private NPC boss;
  private Player self, other, spectator;
  private MistakeLedger ledger;
  private final List<Player> players = new ArrayList<>();

  private Player player(String name, int x, int y, boolean engaged) {
    Player p = mock(Player.class);
    when(p.getName()).thenReturn(name);
    when(p.getWorldView()).thenReturn(view);
    when(p.getWorldLocation()).thenReturn(new WorldPoint(x, y, 0));
    if (engaged) when(p.getInteracting()).thenReturn(boss);
    players.add(p);
    return p;
  }

  @Before
  @SuppressWarnings({"rawtypes", "unchecked"})
  public void setup() {
    observer = new GuardianObserver();
    client = mock(Client.class);
    view = mock(WorldView.class);
    boss = mock(NPC.class);
    when(boss.getId()).thenReturn(16659);
    when(boss.getWorldView()).thenReturn(view);
    when(boss.getWorldLocation()).thenReturn(new WorldPoint(100, 100, 0));
    IndexedObjectSet set = mock(IndexedObjectSet.class);
    when(set.iterator()).thenAnswer(i -> new ArrayList<>(players).iterator());
    when(view.players()).thenReturn(set);
    self = player("Self", 102, 100, true);
    other = player("Other", 102, 99, true);
    spectator = player("Spectator", 115, 115, false);
    when(client.getLocalPlayer()).thenReturn(self);
    ledger = new MistakeLedger();
    ledger.begin(TfaBoss.ARCHIVE_GUARDIAN, 100, 3000, false);
    clock(100);
  }

  private void clock(int tick) {
    when(client.getTickCount()).thenReturn(tick);
    when(client.getGameCycle()).thenReturn(tick * 30);
  }

  private void attack(int anim) {
    when(boss.getAnimation()).thenReturn(anim);
    int gfx = anim == 14754 ? 4204 : anim == 14751 ? 4199 : anim == 14747 ? 4202 : -1;
    if (gfx >= 0) observer.graphic(client, boss, gfx, client.getGameCycle());
    else observer.animation(client, boss, boss);
  }

  private void hit(Player p, int type, int damage) {
    Hitsplat h = mock(Hitsplat.class);
    when(h.getHitsplatType()).thenReturn(type);
    when(h.getAmount()).thenReturn(damage);
    observer.hit(client, p, h);
  }

  private void drain(int tick, boolean others) {
    clock(tick);
    observer.tick(client, boss, ledger, others);
  }

  private boolean has(String player, String type) {
    return ledger.current().events.stream()
        .anyMatch(e -> e.player.equals(player) && e.type.equals("Possible " + type));
  }

  @Test
  public void meleeUsesFootprintAndTimingNotDamage() {
    attack(14752);
    clock(101);
    hit(self, HitsplatID.DAMAGE_ME, 3);
    drain(103, true);
    assertTrue(has("Self", "Melee attack"));
  }

  @Test
  public void crashRequiresOutsideAdjacentTile() {
    when(self.getWorldLocation()).thenReturn(new WorldPoint(102, 99, 0));
    attack(14747);
    hit(self, HitsplatID.DAMAGE_ME, 1);
    drain(102, true);
    assertTrue(has("Self", "Crash damage"));
  }

  @Test
  public void rangedChecksPrayerAtAttackRatherThanHit() {
    attack(14751);
    clock(104);
    when(client.isPrayerActive(Prayer.PROTECT_FROM_MISSILES)).thenReturn(true);
    hit(self, HitsplatID.DAMAGE_ME, 6);
    drain(106, true);
    assertTrue(has("Self", "Taking hit off prayer"));
  }

  @Test
  public void protectedRangeIsNotFlaggedAfterPrayerTurnsOff() {
    when(client.isPrayerActive(Prayer.PROTECT_FROM_MISSILES)).thenReturn(true);
    attack(14751);
    clock(104);
    when(client.isPrayerActive(Prayer.PROTECT_FROM_MISSILES)).thenReturn(false);
    hit(self, HitsplatID.DAMAGE_ME, 6);
    drain(106, true);
    assertFalse(has("Self", "Taking hit off prayer"));
  }

  @Test
  public void mushroomRadiusAndPhaseIdentifyBothSizes() {
    observer.mushroom(62580, 103, 100, 100);
    clock(101);
    hit(self, HitsplatID.POISON, 2);
    hit(other, HitsplatID.POISON, 33);
    drain(103, true);
    assertTrue(has("Self", "Big mushroom explosion"));
    assertTrue(has("Other", "Big mushroom explosion"));
    assertEquals("Small mushroom explosion", GuardianRules.mushroom(1, 2));
    assertNull(GuardianRules.mushroom(2, 2));
  }

  @Test
  public void normalPoisonIsNotAMushroom() {
    clock(101);
    hit(self, HitsplatID.POISON, 10);
    drain(103, true);
    assertFalse(has("Self", "Big mushroom explosion"));
  }

  @Test
  public void fireCountsBlockedHitsAndExcludesSpectators() {
    attack(14754);
    clock(110);
    hit(self, HitsplatID.BLOCK_ME, 0);
    hit(other, HitsplatID.DAMAGE_OTHER, 14);
    drain(112, true);
    drain(113, true);
    assertFalse(has("Self", "Firebreath missed"));
    assertFalse(has("Spectator", "Firebreath missed"));
    assertTrue(has("Other", "Firebreath off prayer"));
  }

  @Test
  public void missingFireNeedsAnotherObservedImpact() {
    attack(14754);
    drain(113, true);
    assertFalse(has("Self", "Firebreath missed"));
  }

  @Test
  public void missingFireIsOneEventPerChannel() {
    attack(14754);
    clock(110);
    hit(other, HitsplatID.DAMAGE_OTHER, 7);
    drain(112, true);
    drain(113, true);
    drain(114, true);
    assertTrue(has("Self", "Firebreath missed"));
    assertEquals(
        1,
        ledger.current().events.stream().filter(e -> e.type.endsWith("Firebreath missed")).count());
  }

  @Test
  public void leavingViewOrDyingDoesNotBecomeMissed() {
    attack(14754);
    observer.death(self);
    players.remove(self);
    clock(110);
    hit(other, HitsplatID.DAMAGE_OTHER, 7);
    drain(112, true);
    drain(113, true);
    assertFalse(has("Self", "Firebreath missed"));
  }

  @Test
  public void selfOnlySettingStillUsesTeammateHitsAsEvidence() {
    attack(14754);
    clock(110);
    hit(other, HitsplatID.DAMAGE_OTHER, 14);
    drain(112, false);
    drain(113, false);
    assertTrue(has("Self", "Firebreath missed"));
    assertFalse(has("Other", "Firebreath off prayer"));
  }

  @Test
  public void vineRequiresAttackNotSpawn() {
    assertEquals("Vine attack", GuardianRules.physical(14768, 0, 1));
    assertNull(GuardianRules.physical(14771, 0, 1));
    assertNull(GuardianRules.physical(14768, 0, 2));
  }

  @Test
  public void fireAnimationAloneDoesNotStartTheGfxDetector() {
    when(boss.getAnimation()).thenReturn(14754);
    observer.animation(client, boss, boss);
    clock(110);
    hit(other, HitsplatID.DAMAGE_OTHER, 14);
    drain(112, true);
    drain(113, true);
    assertFalse(has("Other", "Firebreath off prayer"));
    assertFalse(has("Self", "Firebreath missed"));
  }

  @Test
  public void onePersistentGraphicDoesNotMoveTheStartTick() {
    attack(14754);
    clock(101);
    observer.graphic(client, boss, 4204, 3000);
    clock(110);
    hit(other, HitsplatID.DAMAGE_OTHER, 14);
    drain(112, true);
    drain(113, true);
    assertTrue(has("Self", "Firebreath missed"));
  }

  @Test
  public void unrelatedThrallGraphicDoesNotTriggerFire() {
    observer.graphic(client, boss, 1908, 3000);
    clock(110);
    hit(other, HitsplatID.DAMAGE_OTHER, 14);
    drain(112, true);
    drain(113, true);
    assertFalse(has("Self", "Firebreath missed"));
  }

  @Test
  public void joiningMidGraphicDoesNotGuessItsStart() {
    clock(105);
    observer.graphic(client, boss, 4204, 3000);
    clock(115);
    hit(other, HitsplatID.DAMAGE_OTHER, 14);
    drain(117, true);
    drain(118, true);
    assertFalse(has("Self", "Firebreath missed"));
  }

  @Test
  public void loadedParticipantIsNotRejectedForDistance() {
    observer.restoreParticipants(Collections.singleton("Other"));
    when(other.getInteracting()).thenReturn(null);
    when(other.getWorldLocation()).thenReturn(new WorldPoint(150, 100, 0));
    assertTrue(observer.participant(other, boss));
  }
}
