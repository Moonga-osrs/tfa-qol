package tv.moonga.tfaqol;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.*;
import net.runelite.api.*;
import net.runelite.api.coords.*;
import org.junit.*;

public class DreadnothObserverTest {
  private Client client;
  private WorldView view;
  private NPC boss, jelly;
  private Player self, other;
  private DreadnothObserver observer;
  private MistakeLedger ledger;

  @Before
  @SuppressWarnings({"rawtypes", "unchecked"})
  public void setup() {
    client = mock(Client.class);
    view = mock(WorldView.class);
    boss = mock(NPC.class);
    jelly = mock(NPC.class);
    when(boss.getId()).thenReturn(16665);
    when(jelly.getId()).thenReturn(16666);
    when(boss.getWorldLocation()).thenReturn(new WorldPoint(100, 100, 0));
    when(boss.getWorldArea()).thenReturn(new WorldArea(100, 100, 5, 5, 0));
    when(jelly.getWorldLocation()).thenReturn(new WorldPoint(109, 102, 0));
    for (NPC n : Arrays.asList(boss, jelly)) {
      when(n.getWorldView()).thenReturn(view);
      when(n.getAnimation()).thenReturn(-1);
    }
    when(boss.getIndex()).thenReturn(1);
    when(jelly.getIndex()).thenReturn(2);
    self = player("Self");
    other = player("Other");
    IndexedObjectSet ns = mock(IndexedObjectSet.class), ps = mock(IndexedObjectSet.class);
    when(ns.iterator()).thenAnswer(i -> Arrays.asList(boss, jelly).iterator());
    when(ps.iterator()).thenAnswer(i -> Arrays.asList(self, other).iterator());
    when(view.npcs()).thenReturn(ns);
    when(view.players()).thenReturn(ps);
    when(client.getTopLevelWorldView()).thenReturn(view);
    when(client.getLocalPlayer()).thenReturn(self);
    clock(3000);
    ledger = new MistakeLedger();
    ledger.begin(TfaBoss.DREADNOTH, 100, 3000, false);
    observer = new DreadnothObserver();
    observer.actors(client, ledger, true);
  }

  private Player player(String name) {
    Player p = mock(Player.class);
    when(p.getName()).thenReturn(name);
    when(p.getWorldView()).thenReturn(view);
    when(p.getWorldLocation()).thenReturn(new WorldPoint(110, 102, 0));
    return p;
  }

  private void clock(int c) {
    when(client.getGameCycle()).thenReturn(c);
    when(client.getTickCount()).thenReturn(c / 30);
  }

  private void animation(NPC n, int a) {
    when(n.getAnimation()).thenReturn(a);
    observer.animation(n, client, ledger);
  }

  private void hit(Player p, int amount) {
    Hitsplat h = mock(Hitsplat.class);
    when(h.getAmount()).thenReturn(amount);
    when(h.getHitsplatType()).thenReturn(HitsplatID.DAMAGE_OTHER);
    observer.hit(p, h, client);
  }

  private void drain(int c) {
    clock(c);
    observer.drain(client, ledger, false);
  }

  private Projectile shot(int id, int end, Player target) {
    int start = client.getGameCycle();
    Projectile p = mock(Projectile.class);
    when(p.getId()).thenReturn(id);
    when(p.getSourceActor()).thenReturn(boss);
    when(p.getStartCycle()).thenReturn(start);
    when(p.getEndCycle()).thenReturn(end);
    when(p.getTargetActor()).thenReturn(target);
    when(p.getTargetPoint()).thenReturn(new WorldPoint(110, 102, 0));
    observer.projectile(p, client, ledger);
    return p;
  }

  @Test
  public void stompHitsOneTickAfterAnimationAndChecksPrayerThen() {
    animation(boss, 14777);
    clock(3030);
    hit(self, 19);
    when(client.isPrayerActive(Prayer.PROTECT_FROM_MELEE)).thenReturn(true);
    drain(3060);
    assertEquals("Possible Stomp off prayer", ledger.current().events.get(0).type);
  }

  @Test
  public void correctPrayerAtImpactSuppressesMistakeEvenIfChangedAfter() {
    animation(boss, 14777);
    clock(3030);
    when(client.isPrayerActive(Prayer.PROTECT_FROM_MELEE)).thenReturn(true);
    hit(self, 2);
    when(client.isPrayerActive(Prayer.PROTECT_FROM_MELEE)).thenReturn(false);
    drain(3060);
    assertTrue(ledger.current().events.isEmpty());
  }

  @Test
  public void threeSeparateRangedProjectilesAreNotCollapsedIntoOne() {
    animation(boss, 14777);
    for (int c : new int[] {3120, 3150, 3180}) shot(4211, c, self);
    for (int c : new int[] {3120, 3150, 3180}) {
      clock(c);
      hit(self, 8);
      drain(c + 30);
    }
    assertEquals(3, ledger.current().events.size());
    assertTrue(
        ledger.current().events.stream()
            .allMatch(e -> e.type.equals("Possible Ranged hit off prayer")));
  }

  @Test
  public void othersUseTheirVisibleOverheadAtImpact() {
    animation(boss, 14777);
    clock(3030);
    when(other.getOverheadIcon()).thenReturn(HeadIcon.MELEE);
    hit(other, 3);
    drain(3060);
    assertTrue(ledger.current().events.isEmpty());
    clock(3200);
    animation(boss, 14777);
    clock(3230);
    when(other.getOverheadIcon()).thenReturn(HeadIcon.RANGED);
    hit(other, 26);
    drain(3260);
    assertEquals(1, ledger.current().events.size());
    assertEquals("Other", ledger.current().events.get(0).player);
  }

  @Test
  public void minionAttackHitsAtItsAnimationStart() {
    when(jelly.getInteracting()).thenReturn(self);
    animation(jelly, 1586);
    hit(self, 6);
    drain(3030);
    assertEquals("Possible Jelly melee off prayer", ledger.current().events.get(0).type);
  }

  @Test
  public void normalJellyUsesMeleePrayerAtImpactRatherThanAtDrain() {
    when(jelly.getInteracting()).thenReturn(self);
    animation(jelly, 1586);
    hit(self, 6);
    when(client.isPrayerActive(Prayer.PROTECT_FROM_MELEE)).thenReturn(true);
    drain(3030);
    assertEquals("Possible Jelly melee off prayer", ledger.current().events.get(0).type);
  }

  @Test
  public void protectedNormalJellyHitDoesNotBecomeAMistakeAfterPrayerChanges() {
    when(jelly.getInteracting()).thenReturn(self);
    animation(jelly, 1586);
    when(client.isPrayerActive(Prayer.PROTECT_FROM_MELEE)).thenReturn(true);
    hit(self, 6);
    when(client.isPrayerActive(Prayer.PROTECT_FROM_MELEE)).thenReturn(false);
    drain(3030);
    assertTrue(ledger.current().events.isEmpty());
  }

  @Test
  public void normalJellyZeroDamageAndAnotherPlayersHitDoNotCount() {
    when(jelly.getInteracting()).thenReturn(self);
    animation(jelly, 1586);
    hit(self, 0);
    hit(other, 6);
    drain(3030);
    assertTrue(ledger.current().events.isEmpty());
  }

  @Test
  public void normalJellyStillCountsAfterTheBossDisappears() {
    when(view.npcs().iterator()).thenAnswer(i -> Collections.singletonList(jelly).iterator());
    observer.actors(client, ledger, true);
    when(jelly.getInteracting()).thenReturn(other);
    when(other.getOverheadIcon()).thenReturn(HeadIcon.RANGED);
    animation(jelly, 1586);
    hit(other, 7);
    drain(3030);
    assertEquals("Possible Jelly melee off prayer", ledger.current().events.get(0).type);
    assertEquals("Other", ledger.current().events.get(0).player);
  }

  @Test
  public void enrageLandingChecksActualFootprintAndPrayer() {
    animation(boss, 14780);
    clock(3090);
    when(self.getWorldLocation()).thenReturn(new WorldPoint(102, 103, 0));
    when(client.isPrayerActive(Prayer.PROTECT_FROM_MELEE)).thenReturn(true);
    hit(self, 35);
    drain(3120);
    assertEquals(1, ledger.current().events.size());
    assertEquals("Possible Under boss on landing", ledger.current().events.get(0).type);
  }

  @Test
  public void thePreviousJumpStillLandsAfterTheBossStartsDying() {
    animation(boss, 14780);
    clock(3090);
    animation(boss, 14782);
    hit(other, 26);
    drain(3120);
    assertEquals("Possible Enrage landing off prayer", ledger.current().events.get(0).type);
  }

  @Test
  public void enrageRangedIsCapturedFromItsOwnProjectile() {
    animation(boss, 14780);
    shot(4211, 3180, self);
    clock(3180);
    hit(self, 7);
    drain(3210);
    assertEquals("Possible Enrage ranged off prayer", ledger.current().events.get(0).type);
  }

  @Test
  public void bloodTargetIsTheOldTileRatherThanThePlayer() {
    animation(boss, 14780);
    shot(4212, 3270, null);
    clock(3270);
    hit(self, 20);
    drain(3300);
    assertEquals("Possible Enrage blood hit", ledger.current().events.get(0).type);
  }

  @Test
  public void unrelatedProjectileSourceNeverCounts() {
    NPC unrelated = mock(NPC.class);
    when(unrelated.getId()).thenReturn(999);
    Projectile p = mock(Projectile.class);
    when(p.getId()).thenReturn(4211);
    when(p.getSourceActor()).thenReturn(unrelated);
    observer.projectile(p, client, ledger);
    hit(self, 15);
    drain(3030);
    assertTrue(ledger.current().events.isEmpty());
  }

  @Test
  public void localDeathAndLoadingKeepKnownDistantTeammates() {
    observer.death(self, client, ledger);
    when(self.isDead()).thenReturn(true);
    observer.suspend();
    observer.restore(ledger);
    when(other.getWorldLocation()).thenReturn(new WorldPoint(180, 102, 0));
    observer.actors(client, ledger, true);
    assertTrue(observer.participant(other));
    assertFalse(observer.participant(self));
    assertTrue(ledger.active());
  }

  @Test
  public void joiningHalfwayThroughAnAnimationDoesNotInventATiming() {
    when(boss.getAnimation()).thenReturn(14777);
    when(boss.getAnimationFrame()).thenReturn(20);
    observer.actors(client, ledger, true);
    clock(3030);
    hit(self, 19);
    drain(3060);
    assertTrue(ledger.current().events.isEmpty());
    assertTrue(
        ledger.current().research.stream()
            .anyMatch(e -> e.type.equals("damage") && e.evidence.contains("UNRESOLVED")));
  }

  @Test
  public void selfOnlyRosterExcludesOtherPlayers() {
    observer.clear();
    ledger.reset();
    ledger.begin(TfaBoss.DREADNOTH, 100, 3000, false);
    observer.actors(client, ledger, false);
    assertFalse(ledger.current().players.contains("Other"));
    assertTrue(ledger.current().players.contains("Self"));
  }

  @Test
  public void projectileTargetSurvivesItsActorReference() {
    Projectile p = shot(4211, 3030, self);
    when(p.getTargetActor()).thenReturn(null);
    when(p.getTargetPoint()).thenReturn(null);
    clock(3030);
    hit(self, 8);
    drain(3060);
    assertEquals("Possible Ranged hit off prayer", ledger.current().events.get(0).type);
  }
}
