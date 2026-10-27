package tv.moonga.tfaqol;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.*;
import net.runelite.api.*;
import net.runelite.api.coords.*;
import org.junit.*;

public class OcellusObserverTest {
  private Client client;
  private WorldView view;
  private NPC boss;
  private Player self, other;
  private OcellusObserver observer;
  private MistakeLedger ledger;

  @Before
  @SuppressWarnings({"rawtypes", "unchecked"})
  public void setup() {
    client = mock(Client.class);
    view = mock(WorldView.class);
    boss = mock(NPC.class);
    when(boss.getId()).thenReturn(16674);
    when(boss.getWorldLocation()).thenReturn(new WorldPoint(100, 100, 1));
    when(boss.getWorldView()).thenReturn(view);
    when(boss.getHealthRatio()).thenReturn(100);
    when(boss.getHealthScale()).thenReturn(100);
    when(view.getPlane()).thenReturn(1);
    self = player("Self");
    other = player("Other");
    IndexedObjectSet ns = mock(IndexedObjectSet.class), ps = mock(IndexedObjectSet.class);
    when(ns.iterator()).thenAnswer(i -> Arrays.asList(boss).iterator());
    when(ps.iterator()).thenAnswer(i -> Arrays.asList(self, other).iterator());
    when(view.npcs()).thenReturn(ns);
    when(view.players()).thenReturn(ps);
    when(client.getLocalPlayer()).thenReturn(self);
    observer = new OcellusObserver();
    ledger = new MistakeLedger();
    ledger.begin(TfaBoss.OCELLUS, 100, 3000, false);
    clock(3000);
    observer.actors(view, ledger, 100, 3000);
  }

  private Player player(String name) {
    Player p = mock(Player.class);
    when(p.getName()).thenReturn(name);
    when(p.getWorldView()).thenReturn(view);
    when(p.getWorldLocation()).thenReturn(new WorldPoint(104, 101, 1));
    return p;
  }

  private void clock(int cycle) {
    when(client.getGameCycle()).thenReturn(cycle);
    when(client.getTickCount()).thenReturn(cycle / 30);
  }

  private void hit(Player player, int amount, int type) {
    Hitsplat h = mock(Hitsplat.class);
    when(h.getAmount()).thenReturn(amount);
    when(h.getHitsplatType()).thenReturn(type);
    observer.hit(player, h, client, ledger);
  }

  private void drain(int cycle) {
    clock(cycle);
    observer.drain(client, ledger, false);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private void spot(Player p, int id, int start) {
    ActorSpotAnim s = mock(ActorSpotAnim.class);
    when(s.getId()).thenReturn(id);
    when(s.getStartCycle()).thenReturn(start);
    IterableHashTable table = mock(IterableHashTable.class);
    when(table.iterator()).thenAnswer(i -> Collections.singletonList(s).iterator());
    when(p.getSpotAnims()).thenReturn(table);
  }

  @Test
  public void unknownChipRemainsInResearchWithoutMistake() {
    hit(self, 3, HitsplatID.DAMAGE_ME);
    drain(3030);
    assertTrue(ledger.current().events.isEmpty());
    assertTrue(
        ledger.current().research.stream()
            .anyMatch(e -> e.type.equals("damage") && e.evidence.contains("UNRESOLVED")));
  }

  @Test
  public void lateFloorSignalAndPositiveDamageCountProvisionally() {
    hit(other, 11, HitsplatID.DAMAGE_OTHER);
    observer.floor(4236, 104, 101, 1, 3004, 100, 3004, ledger);
    drain(3030);
    assertEquals(1, ledger.current().events.size());
    assertEquals("Possible Floor cracks", ledger.current().events.get(0).type);
  }

  @Test
  public void standingOnTelegraphWithoutDamageDoesNotCount() {
    observer.floor(4236, 104, 101, 1, 3000, 100, 3000, ledger);
    drain(3030);
    assertTrue(ledger.current().events.isEmpty());
  }

  @Test
  public void healingCannotBecomeFloorMistake() {
    observer.floor(4236, 104, 101, 1, 3000, 100, 3000, ledger);
    hit(self, 40, HitsplatID.HEAL);
    drain(3030);
    assertTrue(ledger.current().events.isEmpty());
    assertTrue(
        ledger.current().research.stream().anyMatch(e -> e.type.equals("heal") && e.damage == 40));
  }

  @Test
  public void soulWithdrawalAndReturnPairWithoutMistake() {
    spot(self, 4313, 2970);
    hit(self, 46, HitsplatID.DAMAGE_ME);
    drain(3030);
    clock(3420);
    spot(self, 4314, 3420);
    hit(self, 46, HitsplatID.HEAL);
    drain(3450);
    assertTrue(ledger.current().events.isEmpty());
    assertTrue(
        ledger.current().research.stream()
            .anyMatch(e -> e.type.equals("soul-return") && e.evidence.contains("delayCycles=420")));
  }

  @Test
  public void replacedScheduledSpotDoesNotBecomeStaleDamageSource() {
    spot(self, 4293, 3000);
    observer.frame(client, ledger, true);
    spot(self, 4293, 3120);
    observer.floor(4270, 104, 101, 1, 3000, 100, 3000, ledger);
    hit(self, 19, HitsplatID.DAMAGE_ME);
    drain(3030);
    assertEquals(1, ledger.current().events.size());
    assertEquals("Possible AoE dodge", ledger.current().events.get(0).type);
  }

  @Test
  public void allFourPartsAndFinalCycleRemainOneEncounter() {
    when(boss.getId()).thenReturn(16677);
    for (int a : new int[] {14881, 14862, 14862, 14862, 14863}) {
      when(boss.getAnimation()).thenReturn(a);
      observer.animation(boss, client.getTickCount(), client.getGameCycle(), ledger);
      clock(client.getGameCycle() + 60);
    }
    hit(other, 3, HitsplatID.DAMAGE_OTHER);
    drain(client.getGameCycle() + 30);
    assertEquals(1, ledger.encounters().size());
    assertTrue(
        ledger.current().research.stream()
            .map(e -> e.evidence)
            .collect(java.util.stream.Collectors.joining("\n")),
        ledger.current().research.stream()
            .anyMatch(e -> e.type.equals("damage") && e.evidence.contains("phase=P3-4")));
  }

  @Test
  public void localDeathDoesNotResetOrExcludeDistantTeammates() {
    ledger.participant("Self");
    ledger.participant("Other");
    observer.death(self, 100, 3000, ledger);
    when(self.isDead()).thenReturn(true);
    when(other.getWorldLocation()).thenReturn(new WorldPoint(170, 101, 1));
    observer.suspend();
    observer.restore(ledger);
    observer.actors(view, ledger, 101, 3030);
    assertTrue(observer.participant(other));
    assertFalse(observer.participant(self));
    hit(other, 6, HitsplatID.DAMAGE_OTHER);
    drain(3060);
    assertTrue(ledger.active());
    assertTrue(
        ledger.current().research.stream()
            .anyMatch(e -> "Other".equals(e.player) && e.type.equals("damage")));
  }

  @Test
  public void reportRetainsSnapshotsAndClearDiscardsOldHazards() {
    observer.snapshot(client, ledger, true);
    assertTrue(
        ledger.current().research.stream()
            .anyMatch(e -> e.type.equals("snapshot") && e.evidence.contains("player=Other")));
    observer.floor(4236, 104, 101, 1, 3000, 100, 3000, ledger);
    observer.clear();
    observer.actors(view, ledger, 100, 3000);
    hit(self, 8, HitsplatID.DAMAGE_ME);
    drain(3030);
    assertTrue(ledger.current().events.isEmpty());
  }

  @Test
  public void expiredActorReferenceDoesNotLosePortalTarget() {
    Projectile shot = mock(Projectile.class);
    when(shot.getId()).thenReturn(4269);
    when(shot.getSourceActor()).thenReturn(boss);
    when(shot.getStartCycle()).thenReturn(2990);
    when(shot.getEndCycle()).thenReturn(3000);
    when(shot.getTargetActor()).thenReturn(self);
    when(shot.getTargetPoint()).thenReturn(new WorldPoint(104, 101, 1));
    observer.projectile(shot, client, ledger);
    when(shot.getTargetActor()).thenReturn(null);
    when(shot.getTargetPoint()).thenReturn(null);
    observer.projectile(shot, client, ledger);
    hit(self, 8, HitsplatID.DAMAGE_ME);
    drain(3030);
    assertEquals("Possible Portal projectile", ledger.current().events.get(0).type);
  }

  @Test
  public void carriedBurnOrBleedNeverTurnsIntoAnOcellusFloorMistake() {
    observer.floor(4236, 104, 101, 1, 3000, 100, 3000, ledger);
    hit(self, 2, HitsplatID.BURN);
    hit(self, 2, HitsplatID.BLEED);
    drain(3030);
    assertTrue(ledger.current().events.isEmpty());
    assertEquals(
        2, ledger.current().research.stream().filter(e -> e.type.equals("damage")).count());
  }

  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  public void rubyEvidenceMakesCoincidentAoeUncertainInsteadOfAMistake() {
    when(self.getAnimation()).thenReturn(9168);
    when(self.getInteracting()).thenReturn(boss);
    ActorSpotAnim s = mock(ActorSpotAnim.class);
    when(s.getId()).thenReturn(754);
    when(s.getStartCycle()).thenReturn(2972);
    IterableHashTable table = mock(IterableHashTable.class);
    when(table.iterator()).thenAnswer(i -> Collections.singletonList(s).iterator());
    when(boss.getSpotAnims()).thenReturn(table);
    observer.floor(4270, 104, 101, 1, 3000, 100, 3000, ledger);
    hit(self, 9, HitsplatID.DAMAGE_ME);
    drain(3030);
    assertTrue(ledger.current().events.isEmpty());
    assertTrue(
        ledger.current().research.stream()
            .anyMatch(
                e ->
                    e.type.equals("damage")
                        && e.evidence.contains("WEAPON_SELF_DAMAGE_POSSIBLE")
                        && e.evidence.contains("aoe-dodge")));
  }

  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  public void recordedRubyCasesAndRealBombWithoutRubyStayDistinct() {
    com.google.gson.JsonArray rows =
        new com.google.gson.Gson()
            .fromJson(
                new java.io.InputStreamReader(
                    getClass().getResourceAsStream("weapon-competing-evidence-20261027.json"),
                    java.nio.charset.StandardCharsets.UTF_8),
                com.google.gson.JsonArray.class);
    for (com.google.gson.JsonElement element : rows) {
      setup();
      com.google.gson.JsonObject row = element.getAsJsonObject();
      int cycle = row.get("cycle").getAsInt(),
          x = row.get("x").getAsInt(),
          y = row.get("y").getAsInt();
      boolean ruby = row.get("ruby").getAsBoolean();
      clock(cycle);
      when(self.getWorldLocation()).thenReturn(new WorldPoint(x, y, 1));
      when(self.getAnimation()).thenReturn(9168);
      when(self.getInteracting()).thenReturn(boss);
      when(boss.getId()).thenReturn(16677);
      if (ruby) {
        ActorSpotAnim spot = mock(ActorSpotAnim.class);
        when(spot.getId()).thenReturn(754);
        when(spot.getStartCycle()).thenReturn(row.get("start").getAsInt());
        IterableHashTable table = mock(IterableHashTable.class);
        when(table.iterator()).thenAnswer(i -> Collections.singletonList(spot).iterator());
        when(boss.getSpotAnims()).thenReturn(table);
      }
      observer.floor(
          row.get("floor").getAsInt(), x, y, 1, cycle, row.get("tick").getAsInt(), cycle, ledger);
      hit(self, row.get("amount").getAsInt(), HitsplatID.DAMAGE_ME);
      drain(cycle + 30);
      if (ruby) {
        assertTrue(ledger.current().events.isEmpty());
        assertTrue(
            ledger.current().research.stream()
                .anyMatch(e -> e.evidence.contains("WEAPON_SELF_DAMAGE_POSSIBLE")));
      } else {
        assertEquals(1, ledger.current().events.size());
        assertEquals("Possible Quadrant bomb", ledger.current().events.get(0).type);
      }
    }
  }

  @Test
  public void floorCracksCanBeDisabledWithoutDisablingOtherMechanics() {
    hit(other, 11, HitsplatID.DAMAGE_OTHER);
    observer.floor(4236, 104, 101, 1, 3004, 100, 3004, ledger);
    clock(3030);
    observer.drain(client, ledger, false, false);
    assertTrue(ledger.current().events.isEmpty());
    assertFalse(ledger.current().research.stream().anyMatch(e -> e.type.equals("damage")));
    clock(3120);
    observer.floor(4270, 104, 101, 1, 3120, 104, 3120, ledger);
    hit(other, 9, HitsplatID.DAMAGE_OTHER);
    clock(3150);
    observer.drain(client, ledger, false, false);
    assertEquals("Possible AoE dodge", ledger.current().events.get(0).type);
    clock(3300);
    observer.floor(4236, 104, 101, 1, 3300, 110, 3300, ledger);
    hit(other, 6, HitsplatID.DAMAGE_OTHER);
    clock(3330);
    observer.drain(client, ledger, true, true);
    assertEquals("Possible Floor cracks", ledger.current().events.get(1).type);
  }
}
