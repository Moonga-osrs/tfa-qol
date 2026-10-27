package tv.moonga.tfaqol;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.*;
import net.runelite.api.*;
import net.runelite.api.coords.*;
import org.junit.*;

public class BorkObserverTest {
  private Client client;
  private WorldView view;
  private NPC bork, smorks;
  private Player self, other, spectator;
  private BorkObserver observer;
  private MistakeLedger ledger;
  private final List<NPC> npcs = new ArrayList<>();
  private final List<Player> players = new ArrayList<>();

  private Player player(String name, int x, int y, boolean engaged) {
    Player p = mock(Player.class);
    when(p.getName()).thenReturn(name);
    when(p.getWorldLocation()).thenReturn(new WorldPoint(x, y, 0));
    when(p.getWorldView()).thenReturn(view);
    if (engaged) when(p.getInteracting()).thenReturn(bork);
    players.add(p);
    return p;
  }

  @Before
  @SuppressWarnings({"rawtypes", "unchecked"})
  public void setup() {
    client = mock(Client.class);
    view = mock(WorldView.class);
    bork = mock(NPC.class);
    smorks = mock(NPC.class);
    when(bork.getId()).thenReturn(16640);
    when(smorks.getId()).thenReturn(16641);
    when(bork.getWorldView()).thenReturn(view);
    when(smorks.getWorldView()).thenReturn(view);
    when(bork.getWorldLocation()).thenReturn(new WorldPoint(100, 100, 0));
    when(bork.getWorldArea()).thenReturn(new WorldArea(100, 100, 4, 4, 0));
    when(smorks.getWorldLocation()).thenReturn(new WorldPoint(105, 100, 0));
    npcs.add(bork);
    npcs.add(smorks);
    IndexedObjectSet ns = mock(IndexedObjectSet.class), ps = mock(IndexedObjectSet.class);
    when(ns.iterator()).thenAnswer(i -> new ArrayList<>(npcs).iterator());
    when(ps.iterator()).thenAnswer(i -> new ArrayList<>(players).iterator());
    when(view.npcs()).thenReturn(ns);
    when(view.players()).thenReturn(ps);
    self = player("Self", 104, 101, true);
    other = player("Other", 105, 101, true);
    spectator = player("Spectator", 130, 130, false);
    when(client.getLocalPlayer()).thenReturn(self);
    when(bork.getInteracting()).thenReturn(self);
    observer = new BorkObserver();
    observer.actors(view);
    ledger = new MistakeLedger();
    ledger.begin(TfaBoss.BORK_AND_SMORKS, 100, 3000, false);
    clock(3000);
  }

  private void clock(int cycle) {
    when(client.getGameCycle()).thenReturn(cycle);
    when(client.getTickCount()).thenReturn(cycle / 30);
  }

  private Projectile shot(int id, int end, Player target, WorldPoint point) {
    Projectile p = mock(Projectile.class);
    when(p.getId()).thenReturn(id);
    when(p.getSourceActor()).thenReturn(smorks);
    when(p.getTargetActor()).thenReturn(target);
    when(p.getTargetPoint()).thenReturn(point);
    when(p.getEndCycle()).thenReturn(end);
    observer.projectile(p, client);
    return p;
  }

  private void hit(Player p, int amount, int type) {
    Hitsplat h = mock(Hitsplat.class);
    when(h.getAmount()).thenReturn(amount);
    when(h.getHitsplatType()).thenReturn(type);
    observer.hit(p, h, type == HitsplatID.DAMAGE_ME || type == HitsplatID.DAMAGE_OTHER, client);
  }

  private void drain(int cycle) {
    clock(cycle);
    observer.drain(client, ledger, true);
  }

  private long count(String type) {
    return ledger.current().events.stream().filter(e -> e.type.equals("Possible " + type)).count();
  }

  @Test
  public void rangeUsesImpactPrayerAndNotLaunchPrayer() {
    when(client.isPrayerActive(Prayer.PROTECT_FROM_MISSILES)).thenReturn(true);
    shot(4056, 3120, self, self.getWorldLocation());
    clock(3120);
    when(client.isPrayerActive(Prayer.PROTECT_FROM_MISSILES)).thenReturn(false);
    hit(self, 1, HitsplatID.DAMAGE_ME);
    drain(3150);
    assertEquals(1, count("Ranged hit off prayer"));
  }

  @Test
  public void prayerActivatedBeforeImpactPreventsMistake() {
    shot(4056, 3120, self, self.getWorldLocation());
    clock(3120);
    when(client.isPrayerActive(Prayer.PROTECT_FROM_MISSILES)).thenReturn(true);
    hit(self, 27, HitsplatID.DAMAGE_ME);
    drain(3150);
    assertEquals(0, count("Ranged hit off prayer"));
  }

  @Test
  public void magicChecksTeammatesVisibleMagicPrayer() {
    shot(4057, 3120, other, other.getWorldLocation());
    clock(3120);
    when(other.getOverheadIcon()).thenReturn(HeadIcon.MAGIC);
    hit(other, 12, HitsplatID.DAMAGE_OTHER);
    drain(3150);
    assertTrue(ledger.current().events.isEmpty());
    clock(3200);
    shot(4057, 3320, other, other.getWorldLocation());
    clock(3320);
    when(other.getOverheadIcon()).thenReturn(HeadIcon.RANGED);
    hit(other, 2, HitsplatID.DAMAGE_OTHER);
    drain(3350);
    assertEquals(1, count("Magic hit off prayer"));
  }

  @Test
  public void projectilesAloneDoNotCount() {
    shot(4056, 3120, self, self.getWorldLocation());
    drain(3150);
    assertTrue(ledger.current().events.isEmpty());
  }

  @Test
  public void tankHasTwoSeparateDamageImpactsAndOthersGetFloorMistake() {
    when(bork.getAnimation()).thenReturn(14542);
    observer.animation(bork, 3000);
    observer.floor(104, 101, 3003);
    observer.floor(105, 101, 3003);
    hit(self, 5, HitsplatID.DAMAGE_ME);
    hit(other, 39, HitsplatID.DAMAGE_OTHER);
    clock(3060);
    observer.floor(104, 101, 3063);
    hit(self, 7, HitsplatID.DAMAGE_ME);
    drain(3100);
    assertEquals(2, count("Tank damage"));
    assertEquals(1, count("Bork attack"));
  }

  @Test
  public void floorEffectMayArriveAfterTheHitAndRequiresDamage() {
    hit(other, 26, HitsplatID.DAMAGE_OTHER);
    observer.floor(105, 101, 3004);
    drain(3030);
    assertEquals(1, count("Bork attack"));
    observer.floor(104, 101, 3040);
    clock(3040);
    hit(self, 0, HitsplatID.DAMAGE_ME);
    drain(3070);
    assertEquals(1, ledger.current().events.size());
  }

  @Test
  public void simultaneousProjectileAndFloorDamageAreNotGuessed() {
    shot(4056, 3000, self, self.getWorldLocation());
    observer.floor(104, 101, 3002);
    hit(self, 27, HitsplatID.DAMAGE_ME);
    drain(3030);
    assertTrue(ledger.current().events.isEmpty());
  }

  @Test
  public void standingOnResidueWithoutDamageDoesNotCount() {
    observer.ground(104, 101, 3000, true);
    drain(3100);
    assertEquals(0, count("Flame ground damage"));
  }

  @Test
  public void residueCountsDamageIncludingBurnButNotAfterDespawn() {
    observer.ground(104, 101, 2990, true);
    hit(self, 6, HitsplatID.DAMAGE_ME);
    clock(3060);
    hit(self, 1, HitsplatID.BURN);
    observer.ground(104, 101, 3070, false);
    clock(3080);
    hit(self, 1, HitsplatID.BURN);
    drain(3120);
    assertEquals(2, count("Flame ground damage"));
  }

  @Test
  public void bombMissIsSharedOnceAndSpectatorIsExcluded() {
    Projectile p = shot(4440, 3135, null, new WorldPoint(110, 110, 0));
    observer.projectile(p, client);
    clock(3135);
    observer.frame(client);
    drain(3165);
    drain(3200);
    assertEquals(2, count("Bomb missed"));
    assertFalse(ledger.current().events.stream().anyMatch(e -> e.player.equals("Spectator")));
  }

  @Test
  public void aSuccessfulStunPreventsBombMiss() {
    shot(4440, 3135, null, new WorldPoint(110, 110, 0));
    clock(3135);
    observer.frame(client);
    observer.stun(3135);
    drain(3165);
    assertEquals(0, count("Bomb missed"));
  }

  @Test
  public void bombInsideFootprintWithoutObservedStunIsInconclusive() {
    shot(4440, 3135, null, new WorldPoint(102, 101, 0));
    clock(3135);
    observer.frame(client);
    drain(3165);
    assertEquals(0, count("Bomb missed"));
  }

  @Test
  public void unloadedOrDeadBorkIsNotAMissedBomb() {
    shot(4440, 3135, null, new WorldPoint(110, 110, 0));
    npcs.remove(bork);
    observer.actors(view);
    clock(3135);
    observer.frame(client);
    drain(3165);
    assertEquals(0, count("Bomb missed"));
  }

  @Test
  public void afterLocalDeathOnlyAliveTeammatesGetTheSharedMistake() {
    shot(4440, 3135, null, new WorldPoint(110, 110, 0));
    when(self.isDead()).thenReturn(true);
    clock(3135);
    observer.frame(client);
    drain(3165);
    assertEquals(1, count("Bomb missed"));
    assertEquals("Other", ledger.current().events.get(0).player);
  }

  @Test
  public void selfOnlyDoesNotCountOthers() {
    shot(4440, 3135, null, new WorldPoint(110, 110, 0));
    clock(3135);
    observer.frame(client);
    clock(3165);
    observer.drain(client, ledger, false);
    assertEquals(1, count("Bomb missed"));
    assertEquals("Self", ledger.current().events.get(0).player);
  }

  @Test
  public void projectilesFromUnrelatedNpcsAreIgnored() {
    Projectile p = mock(Projectile.class);
    NPC unrelated = mock(NPC.class);
    when(unrelated.getId()).thenReturn(999);
    when(p.getId()).thenReturn(4056);
    when(p.getSourceActor()).thenReturn(unrelated);
    when(p.getEndCycle()).thenReturn(3000);
    when(p.getTargetActor()).thenReturn(self);
    observer.projectile(p, client);
    hit(self, 27, HitsplatID.DAMAGE_ME);
    drain(3030);
    assertTrue(ledger.current().events.isEmpty());
  }

  @Test
  public void respawnedSpectatorDoesNotRejoinBombRosterUntilNewAttempt() {
    observer.excludePlayers(Collections.singleton("Self"));
    observer.restoreParticipants(Collections.singleton("Self"));
    assertFalse(observer.participant(self));
    shot(4440, 3135, null, new WorldPoint(110, 110, 0));
    clock(3135);
    observer.frame(client);
    drain(3165);
    assertEquals(1, count("Bomb missed"));
    assertEquals("Other", ledger.current().events.get(0).player);
  }

  @Test
  public void aClearedProjectileTargetReferenceKeepsItsKnownVictim() {
    Projectile p = shot(4057, 3000, self, new WorldPoint(104, 101, 0));
    when(p.getTargetActor()).thenReturn(null);
    observer.projectile(p, client);
    when(self.getWorldLocation()).thenReturn(new WorldPoint(106, 101, 0));
    hit(self, 27, HitsplatID.DAMAGE_ME);
    drain(3030);
    assertEquals(1, count("Magic hit off prayer"));
  }

  @Test
  public void disablingTankTrackingKeepsOtherFloorHitsAndCanBeReenabled() {
    when(bork.getAnimation()).thenReturn(14542);
    observer.animation(bork, 3000);
    observer.floor(104, 101, 3003);
    observer.floor(105, 101, 3003);
    hit(self, 5, HitsplatID.DAMAGE_ME);
    hit(other, 39, HitsplatID.DAMAGE_OTHER);
    clock(3030);
    observer.drain(client, ledger, true, false, false);
    assertEquals(0, count("Tank damage"));
    assertEquals(1, count("Bork attack"));
    assertFalse(
        ledger.current().research.stream()
            .anyMatch(e -> e.type.equals("damage") && e.player.equals("Self")));
    clock(3060);
    observer.floor(104, 101, 3063);
    hit(self, 7, HitsplatID.DAMAGE_ME);
    clock(3100);
    observer.drain(client, ledger, true, true, true);
    assertEquals(1, count("Tank damage"));
  }
}
