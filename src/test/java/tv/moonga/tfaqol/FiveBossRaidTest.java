package tv.moonga.tfaqol;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import com.google.gson.Gson;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import net.runelite.api.*;
import net.runelite.api.coords.*;
import net.runelite.api.events.*;
import org.junit.*;

public class FiveBossRaidTest {
  private TfaTracker tracker;
  private Client client;
  private WorldView view;
  private Player self, other;
  private MistakeLedger ledger;
  private final List<NPC> npcs = new ArrayList<>();

  @Before
  @SuppressWarnings({"rawtypes", "unchecked"})
  public void setup() throws Exception {
    tracker = new TfaTracker();
    client = mock(Client.class);
    view = mock(WorldView.class);
    self = player("Self");
    other = player("Other");
    when(client.getTopLevelWorldView()).thenReturn(view);
    when(client.getLocalPlayer()).thenReturn(self);
    when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
    IndexedObjectSet ns = mock(IndexedObjectSet.class), ps = mock(IndexedObjectSet.class);
    when(ns.iterator()).thenAnswer(i -> new ArrayList<>(npcs).iterator());
    when(ps.iterator()).thenAnswer(i -> Arrays.asList(self, other).iterator());
    when(view.npcs()).thenReturn(ns);
    when(view.players()).thenReturn(ps);
    net.runelite.api.Deque graphics = mock(net.runelite.api.Deque.class);
    when(graphics.iterator()).thenAnswer(i -> Collections.emptyIterator());
    when(view.getGraphicsObjects()).thenReturn(graphics);
    PluginGateTest.inject(tracker, "client", client);
    PluginGateTest.inject(tracker, "config", new TfaConfig() {});
    clock(3000);
    Field f = TfaTracker.class.getDeclaredField("ledger");
    f.setAccessible(true);
    ledger = (MistakeLedger) f.get(tracker);
  }

  private Player player(String name) {
    Player p = mock(Player.class);
    when(p.getName()).thenReturn(name);
    when(p.getWorldView()).thenReturn(view);
    when(p.getWorldLocation()).thenReturn(new WorldPoint(102, 100, 0));
    return p;
  }

  private void clock(int c) {
    when(client.getGameCycle()).thenReturn(c);
    when(client.getTickCount()).thenReturn(c / 30);
  }

  private NPC enter(int id, int animation, int cycle) {
    clock(cycle);
    npcs.clear();
    NPC n = mock(NPC.class);
    when(n.getId()).thenReturn(id);
    when(n.getWorldView()).thenReturn(view);
    when(n.getWorldLocation()).thenReturn(new WorldPoint(100, 100, 0));
    when(n.getWorldArea())
        .thenReturn(
            new WorldArea(
                100,
                100,
                id == 16684 || id == 16674 ? 3 : 5,
                id == 16684 || id == 16674 ? 3 : 5,
                0));
    when(n.getAnimation()).thenReturn(animation);
    when(n.getInteracting()).thenReturn(self);
    when(n.getHealthScale()).thenReturn(100);
    when(n.getHealthRatio()).thenReturn(90);
    for (Player p : Arrays.asList(self, other)) when(p.getInteracting()).thenReturn(n);
    npcs.add(n);
    AnimationChanged e = new AnimationChanged();
    e.setActor(n);
    tracker.onAnimationChanged(e);
    tracker.onGameTick(new GameTick());
    return n;
  }

  private void hit(Player p, int amount) {
    Hitsplat h = mock(Hitsplat.class);
    when(h.getAmount()).thenReturn(amount);
    when(h.getHitsplatType()).thenReturn(HitsplatID.DAMAGE_OTHER);
    HitsplatApplied e = new HitsplatApplied();
    e.setActor(p);
    e.setHitsplat(h);
    tracker.onHitsplatApplied(e);
  }

  @Test
  public void allFiveRoomsRemainInOrderWithTheirDamageLogs() throws Exception {
    int c = 3000;
    for (int[] data :
        new int[][] {
          {16658, 14752}, {16640, 14542}, {16665, 14777}, {16684, 14984}, {16674, 14806}
        }) {
      enter(data[0], data[1], c);
      clock(c + 30);
      hit(other, 3);
      clock(c + 90);
      tracker.onGameTick(new GameTick());
      c += 300;
    }
    assertEquals(
        Arrays.asList(
            TfaBoss.ARCHIVE_GUARDIAN,
            TfaBoss.BORK_AND_SMORKS,
            TfaBoss.DREADNOTH,
            TfaBoss.XARTH,
            TfaBoss.OCELLUS),
        Arrays.asList(ledger.encounters().stream().map(e -> e.boss).toArray(TfaBoss[]::new)));
    for (MistakeLedger.Encounter e : ledger.encounters())
      assertTrue(
          e.boss.toString(),
          e.research.stream().anyMatch(r -> r.type.equals("damage") && r.damage == 3));
    tracker.onActorDeath(new ActorDeath(self));
    assertEquals(5, ledger.encounters().size());
    assertTrue(ledger.active());
    PluginGateTest.inject(tracker, "started", true);
    tracker.archiveEntered();
    assertTrue(ledger.encounters().isEmpty());
  }

  @Test
  public void guardianDoesNotReaddADeadPlayerAfterLoading() {
    enter(16658, 14752, 3000);
    tracker.onActorDeath(new ActorDeath(self));
    when(self.isDead()).thenReturn(true);
    GameStateChanged loading = new GameStateChanged();
    loading.setGameState(GameState.LOADING);
    tracker.onGameStateChanged(loading);
    when(self.isDead()).thenReturn(false);
    clock(3060);
    tracker.onGameTick(new GameTick());
    clock(3090);
    hit(self, 40);
    clock(3180);
    tracker.onGameTick(new GameTick());
    assertTrue(
        ledger.current().research.stream()
            .noneMatch(e -> e.type.equals("damage") && e.damage == 40));
    assertTrue(ledger.current().players.contains("Other"));
  }

  @Test
  public void unfinishedGuardianHitsAreRetainedWhenChangingRooms() {
    enter(16658, 14752, 3000);
    clock(3030);
    hit(other, 44);
    enter(16640, 14542, 3031);
    assertTrue(
        ledger.encounters().get(0).research.stream()
            .anyMatch(e -> e.type.equals("damage") && e.damage == 44));
    assertEquals("Possible Melee attack", ledger.encounters().get(0).events.get(0).type);
  }

  @Test
  public void automaticCheckpointDoesNotResetCounters() throws Exception {
    enter(16684, 14984, 3000);
    ExecutorService io = mock(ExecutorService.class);
    PluginGateTest.inject(tracker, "io", io);
    PluginGateTest.inject(tracker, "gson", new Gson());
    clock(9000);
    tracker.onGameTick(new GameTick());
    verify(io, times(1)).submit(any(Runnable.class));
    clock(9030);
    tracker.onGameTick(new GameTick());
    verify(io, times(1)).submit(any(Runnable.class));
    assertEquals(1, ledger.encounters().size());
    assertTrue(ledger.active());
  }

  @Test
  public void pendingGuardianDamageResolvesEvenWhenBossLeavesTheLoadedScene() {
    enter(16658, 14752, 3000);
    clock(3030);
    hit(other, 44);
    npcs.clear();
    clock(3090);
    tracker.onGameTick(new GameTick());
    assertTrue(ledger.active());
    assertEquals("Possible Melee attack", ledger.current().events.get(0).type);
    assertEquals(1, ledger.encounters().size());
  }
}
