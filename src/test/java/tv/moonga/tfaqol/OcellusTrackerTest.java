package tv.moonga.tfaqol;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.util.*;
import net.runelite.api.*;
import net.runelite.api.coords.*;
import net.runelite.api.events.*;
import org.junit.*;

public class OcellusTrackerTest {
  private TfaTracker tracker;
  private Client client;
  private WorldView view;
  private NPC boss;
  private Player self, other;
  private MistakeLedger ledger;
  private OcellusObserver observer;

  @Before
  @SuppressWarnings({"rawtypes", "unchecked"})
  public void setup() throws Exception {
    tracker = new TfaTracker();
    client = mock(Client.class);
    view = mock(WorldView.class);
    boss = mock(NPC.class);
    self = mock(Player.class);
    other = mock(Player.class);
    when(boss.getId()).thenReturn(16674);
    when(boss.getAnimation()).thenReturn(14806);
    when(boss.getWorldView()).thenReturn(view);
    when(boss.getWorldLocation()).thenReturn(new WorldPoint(100, 100, 1));
    when(boss.getHealthScale()).thenReturn(100);
    when(boss.getHealthRatio()).thenReturn(100);
    when(view.getPlane()).thenReturn(1);
    when(self.getName()).thenReturn("Self");
    when(other.getName()).thenReturn("Other");
    for (Player p : Arrays.asList(self, other)) {
      when(p.getWorldView()).thenReturn(view);
      when(p.getWorldLocation()).thenReturn(new WorldPoint(104, 101, 1));
    }
    IndexedObjectSet ns = mock(IndexedObjectSet.class), ps = mock(IndexedObjectSet.class);
    when(ns.iterator()).thenAnswer(i -> Arrays.asList(boss).iterator());
    when(ps.iterator()).thenAnswer(i -> Arrays.asList(self, other).iterator());
    when(view.npcs()).thenReturn(ns);
    when(view.players()).thenReturn(ps);
    net.runelite.api.Deque graphics = mock(net.runelite.api.Deque.class);
    when(graphics.iterator()).thenAnswer(i -> Collections.emptyIterator());
    when(view.getGraphicsObjects()).thenReturn(graphics);
    when(client.getTopLevelWorldView()).thenReturn(view);
    when(client.getLocalPlayer()).thenReturn(self);
    when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
    clock(3000);
    PluginGateTest.inject(tracker, "client", client);
    PluginGateTest.inject(tracker, "config", new TfaConfig() {});
    animate();
    tracker.onGameTick(new GameTick());
    ledger = (MistakeLedger) field("ledger");
    observer = (OcellusObserver) field("ocellus");
  }

  private Object field(String name) throws Exception {
    Field f = TfaTracker.class.getDeclaredField(name);
    f.setAccessible(true);
    return f.get(tracker);
  }

  private void clock(int c) {
    when(client.getGameCycle()).thenReturn(c);
    when(client.getTickCount()).thenReturn(c / 30);
  }

  private void animate() {
    AnimationChanged e = new AnimationChanged();
    e.setActor(boss);
    tracker.onAnimationChanged(e);
  }

  private void hit(Player p, int amount, int type) {
    Hitsplat h = mock(Hitsplat.class);
    when(h.getAmount()).thenReturn(amount);
    when(h.getHitsplatType()).thenReturn(type);
    HitsplatApplied e = new HitsplatApplied();
    e.setActor(p);
    e.setHitsplat(h);
    tracker.onHitsplatApplied(e);
  }

  @Test
  public void unnamedPortalFormAndP3StayInSameRoom() {
    assertEquals(TfaBoss.OCELLUS, ledger.current().boss);
    when(boss.getId()).thenReturn(16675);
    when(boss.getAnimation()).thenReturn(14837);
    clock(3030);
    animate();
    when(boss.getId()).thenReturn(16677);
    when(boss.getAnimation()).thenReturn(14881);
    clock(3060);
    animate();
    assertEquals(1, ledger.encounters().size());
    assertTrue(ledger.active());
  }

  @Test
  public void sceneLoadAfterLocalDeathPreservesOtherPlayersAndCounts() {
    tracker.onActorDeath(new ActorDeath(self));
    when(self.isDead()).thenReturn(true);
    GameStateChanged loading = new GameStateChanged();
    loading.setGameState(GameState.LOADING);
    tracker.onGameStateChanged(loading);
    when(other.getWorldLocation()).thenReturn(new WorldPoint(180, 101, 1));
    clock(3030);
    tracker.onGameTick(new GameTick());
    observer.floor(4236, 180, 101, 1, 3030, 101, 3030, ledger);
    hit(other, 12, HitsplatID.DAMAGE_OTHER);
    clock(3060);
    tracker.onGameTick(new GameTick());
    assertTrue(ledger.active());
    assertEquals(1, ledger.encounters().size());
    assertEquals(1, ledger.current().events.size());
    assertEquals("Other", ledger.current().events.get(0).player);
  }

  @Test
  public void selfOnlySkipsOtherDamageAndKeepsOwnUnknownDamage() throws Exception {
    PluginGateTest.inject(
        tracker,
        "config",
        new TfaConfig() {
          public boolean trackOthers() {
            return false;
          }
        });
    hit(other, 8, HitsplatID.DAMAGE_OTHER);
    hit(self, 3, HitsplatID.DAMAGE_ME);
    clock(3030);
    tracker.onGameTick(new GameTick());
    assertEquals(
        1, ledger.current().research.stream().filter(e -> e.type.equals("damage")).count());
    assertTrue(ledger.current().events.isEmpty());
  }

  @Test
  public void lethalDamageArrivingAfterDeathEventIsRetained() {
    when(self.isDead()).thenReturn(true);
    tracker.onActorDeath(new ActorDeath(self));
    hit(self, 22, HitsplatID.DAMAGE_ME);
    clock(3030);
    tracker.onGameTick(new GameTick());
    assertTrue(
        ledger.current().research.stream()
            .anyMatch(e -> e.type.equals("damage") && e.damage == 22));
  }
}
