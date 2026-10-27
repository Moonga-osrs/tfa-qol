package tv.moonga.tfaqol;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.*;
import net.runelite.api.*;
import net.runelite.api.coords.*;
import org.junit.*;

public class XarthObserverTest {
  private Client client;
  private WorldView view;
  private NPC boss;
  private Player self, other;
  private XarthObserver observer;
  private MistakeLedger ledger;

  @Before
  @SuppressWarnings({"rawtypes", "unchecked"})
  public void setup() {
    client = mock(Client.class);
    view = mock(WorldView.class);
    boss = mock(NPC.class);
    when(boss.getId()).thenReturn(16684);
    when(boss.getAnimation()).thenReturn(-1);
    when(boss.getWorldView()).thenReturn(view);
    when(boss.getWorldLocation()).thenReturn(new WorldPoint(100, 100, 0));
    self = player("Self");
    other = player("Other");
    IndexedObjectSet ns = mock(IndexedObjectSet.class), ps = mock(IndexedObjectSet.class);
    when(ns.iterator()).thenAnswer(i -> Arrays.asList(boss).iterator());
    when(ps.iterator()).thenAnswer(i -> Arrays.asList(self, other).iterator());
    when(view.npcs()).thenReturn(ns);
    when(view.players()).thenReturn(ps);
    when(client.getTopLevelWorldView()).thenReturn(view);
    when(client.getLocalPlayer()).thenReturn(self);
    clock(3000);
    ledger = new MistakeLedger();
    ledger.begin(TfaBoss.XARTH, 100, 3000, false);
    observer = new XarthObserver();
    observer.actors(client, ledger, true);
  }

  private Player player(String name) {
    Player p = mock(Player.class);
    when(p.getName()).thenReturn(name);
    when(p.getWorldLocation()).thenReturn(new WorldPoint(104, 100, 0));
    when(p.getWorldView()).thenReturn(view);
    return p;
  }

  private void clock(int c) {
    when(client.getGameCycle()).thenReturn(c);
    when(client.getTickCount()).thenReturn(c / 30);
  }

  private void hit(Player p, int amount, int type) {
    Hitsplat s = mock(Hitsplat.class);
    when(s.getAmount()).thenReturn(amount);
    when(s.getHitsplatType()).thenReturn(type);
    observer.hit(p, s, client);
  }

  private Projectile shot(int id, int end) {
    Projectile p = mock(Projectile.class);
    when(p.getId()).thenReturn(id);
    when(p.getSourceActor()).thenReturn(boss);
    when(p.getStartCycle()).thenReturn(3000);
    when(p.getEndCycle()).thenReturn(end);
    when(p.getTargetActor()).thenReturn(self);
    when(p.getTargetPoint()).thenReturn(new WorldPoint(104, 100, 0));
    observer.projectile(p, client, ledger);
    return p;
  }

  private void drain(int c) {
    clock(c);
    observer.drain(client, ledger, false);
  }

  @Test
  public void explosionHitsThreeTicksAfterTheAnimation() {
    when(boss.getAnimation()).thenReturn(14989);
    observer.animation(boss, client, ledger);
    clock(3091);
    hit(self, 48, HitsplatID.DAMAGE_ME);
    hit(other, 43, HitsplatID.DAMAGE_OTHER);
    drain(3121);
    assertEquals(2, ledger.current().events.size());
    assertTrue(
        ledger.current().events.stream().allMatch(e -> e.type.equals("Possible Xarth explosion")));
  }

  @Test
  public void corpseChargingXarthCountsOnceAsAGroupObservation() {
    NPC corpse = mock(NPC.class);
    when(corpse.getId()).thenReturn(16687);
    when(corpse.getIndex()).thenReturn(42);
    when(corpse.getWorldView()).thenReturn(view);
    when(corpse.getAnimation()).thenReturn(15005);
    when(corpse.getInteracting()).thenReturn(boss);
    observer.animation(corpse, client, ledger);
    observer.animation(corpse, client, ledger);
    assertEquals(1, ledger.current().events.size());
    assertEquals("Possible Xarth charged", ledger.current().events.get(0).type);
    assertEquals("Group", ledger.current().events.get(0).player);
    assertEquals(0, ledger.current().events.get(0).damage);
  }

  @Test
  public void corpseDeathOrAnUnknownTargetDoesNotCountAsCharging() {
    NPC corpse = mock(NPC.class);
    when(corpse.getId()).thenReturn(16687);
    when(corpse.getWorldView()).thenReturn(view);
    when(corpse.getAnimation()).thenReturn(15003);
    when(corpse.getInteracting()).thenReturn(boss);
    observer.animation(corpse, client, ledger);
    clock(3030);
    when(corpse.getAnimation()).thenReturn(15005);
    when(corpse.getInteracting()).thenReturn(null);
    observer.animation(corpse, client, ledger);
    assertTrue(ledger.current().events.isEmpty());
  }

  @Test
  public void correctPrayerMagicAndItsSixBurnTicksDoNotCount() {
    shot(4034, 3030);
    clock(3030);
    when(client.isPrayerActive(Prayer.PROTECT_FROM_MAGIC)).thenReturn(true);
    hit(self, 6, HitsplatID.DAMAGE_ME);
    for (int c : new int[] {3060, 3180, 3300, 3420, 3540, 3660}) {
      clock(c);
      hit(self, 2, HitsplatID.BURN);
      drain(c + 30);
    }
    assertTrue(ledger.current().events.isEmpty());
    assertEquals(
        7, ledger.current().research.stream().filter(e -> e.type.equals("damage")).count());
  }

  @Test
  public void prayerAtImpactIsPreservedAfterASwitch() {
    shot(4374, 3030);
    clock(3030);
    hit(self, 4, HitsplatID.DAMAGE_ME);
    when(client.isPrayerActive(Prayer.PROTECT_FROM_MISSILES)).thenReturn(true);
    drain(3060);
    assertEquals("Possible Ranged hit off prayer", ledger.current().events.get(0).type);
  }

  @Test
  public void anotherPlayersCorrectOverheadPreventsOffPrayer() {
    Projectile p = shot(4034, 3030);
    when(p.getTargetActor()).thenReturn(other);
    observer.projectile(p, client, ledger);
    when(other.getOverheadIcon()).thenReturn(HeadIcon.MAGIC);
    clock(3030);
    hit(other, 4, HitsplatID.DAMAGE_OTHER);
    drain(3060);
    assertTrue(ledger.current().events.isEmpty());
  }

  @Test
  public void aProjectileKeepsItsKnownTargetIfTheActorReferenceDisappears() {
    Projectile p = shot(4374, 3030);
    when(p.getTargetActor()).thenReturn(null);
    observer.projectile(p, client, ledger);
    when(self.getWorldLocation()).thenReturn(new WorldPoint(105, 100, 0));
    clock(3030);
    hit(self, 5, HitsplatID.DAMAGE_ME);
    drain(3060);
    assertEquals(1, ledger.current().events.size());
  }

  @Test
  public void floorSignalMayArriveAfterTheHit() {
    clock(3030);
    hit(other, 16, HitsplatID.DAMAGE_OTHER);
    observer.floor(4379, 104, 100, 0, 3034, client, ledger);
    drain(3060);
    assertEquals("Possible AoE hit", ledger.current().events.get(0).type);
  }

  @Test
  public void corpseAnimationsAndAbsorbGfxDoNotInventTeamMistakes() {
    NPC corpse = mock(NPC.class);
    when(corpse.getId()).thenReturn(16687);
    when(corpse.getWorldView()).thenReturn(view);
    when(corpse.getAnimation()).thenReturn(15003);
    observer.animation(corpse, client, ledger);
    drain(3030);
    assertTrue(ledger.current().events.isEmpty());
    assertTrue(
        ledger.current().research.stream()
            .anyMatch(e -> e.type.equals("animation") && e.evidence.contains("npc=16687")));
  }

  @Test
  public void deathLoadingAndLateBurnKeepEvidenceWithoutCounters() {
    ledger.participant("Self");
    ledger.participant("Other");
    observer.death(self, client, ledger);
    when(self.isDead()).thenReturn(true);
    observer.suspend();
    observer.restore(ledger);
    when(other.getWorldLocation()).thenReturn(new WorldPoint(180, 100, 0));
    observer.actors(client, ledger, true);
    assertTrue(observer.participant(other));
    assertFalse(observer.participant(self));
    hit(other, 1, HitsplatID.BURN);
    drain(3030);
    assertTrue(ledger.current().events.isEmpty());
    assertTrue(
        ledger.current().research.stream().anyMatch(e -> e.evidence.contains("BURN_STATUS")));
  }

  @Test
  public void unrelatedNpcProjectilesAreExcluded() {
    NPC unrelated = mock(NPC.class);
    when(unrelated.getId()).thenReturn(999);
    Projectile p = mock(Projectile.class);
    when(p.getId()).thenReturn(4034);
    when(p.getSourceActor()).thenReturn(unrelated);
    observer.projectile(p, client, ledger);
    hit(self, 10, HitsplatID.DAMAGE_ME);
    drain(3030);
    assertTrue(ledger.current().events.isEmpty());
  }
}
