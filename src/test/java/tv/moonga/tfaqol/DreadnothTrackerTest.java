package tv.moonga.tfaqol;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.util.*;
import net.runelite.api.*;
import net.runelite.api.coords.*;
import net.runelite.api.events.*;
import org.junit.*;

public class DreadnothTrackerTest {
  private Client client;
  private WorldView view;
  private NPC boss;
  private Player self, other;
  private TfaTracker tracker;
  private MistakeLedger ledger;

  @Before
  @SuppressWarnings({"rawtypes", "unchecked"})
  public void setup() throws Exception {
    tracker = new TfaTracker();
    client = mock(Client.class);
    view = mock(WorldView.class);
    boss = mock(NPC.class);
    when(boss.getId()).thenReturn(16665);
    when(boss.getWorldView()).thenReturn(view);
    when(boss.getWorldLocation()).thenReturn(new WorldPoint(100, 100, 0));
    when(boss.getWorldArea()).thenReturn(new WorldArea(100, 100, 5, 5, 0));
    when(boss.getAnimation()).thenReturn(14777);
    when(boss.getHealthScale()).thenReturn(100);
    when(boss.getHealthRatio()).thenReturn(100);
    self = mock(Player.class);
    other = mock(Player.class);
    when(self.getName()).thenReturn("Self");
    when(other.getName()).thenReturn("Other");
    for (Player p : Arrays.asList(self, other)) {
      when(p.getWorldLocation()).thenReturn(new WorldPoint(110, 101, 0));
      when(p.getWorldView()).thenReturn(view);
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
    when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
    when(client.getLocalPlayer()).thenReturn(self);
    clock(3000);
    PluginGateTest.inject(tracker, "client", client);
    PluginGateTest.inject(tracker, "config", new TfaConfig() {});
    animation();
    Field f = TfaTracker.class.getDeclaredField("ledger");
    f.setAccessible(true);
    ledger = (MistakeLedger) f.get(tracker);
  }

  private void clock(int cycle) {
    when(client.getGameCycle()).thenReturn(cycle);
    when(client.getTickCount()).thenReturn(cycle / 30);
  }

  private void animation() {
    AnimationChanged e = new AnimationChanged();
    e.setActor(boss);
    tracker.onAnimationChanged(e);
  }

  private void hit(Player p, int damage) {
    Hitsplat h = mock(Hitsplat.class);
    when(h.getAmount()).thenReturn(damage);
    when(h.getHitsplatType()).thenReturn(HitsplatID.DAMAGE_OTHER);
    HitsplatApplied e = new HitsplatApplied();
    e.setActor(p);
    e.setHitsplat(h);
    tracker.onHitsplatApplied(e);
  }

  @Test
  public void theOpeningStompStartsTheEncounterAndProducesItsCounter() {
    assertEquals(TfaBoss.DREADNOTH, ledger.current().boss);
    clock(3030);
    hit(other, 19);
    clock(3060);
    tracker.onGameTick(new GameTick());
    assertEquals(1, ledger.current().events.size());
    assertEquals("Possible Stomp off prayer", ledger.current().events.get(0).type);
  }

  @Test
  public void deathAndLoadingPreserveTrackingOfKnownFarAwayPlayers() {
    tracker.onActorDeath(new ActorDeath(self));
    when(self.isDead()).thenReturn(true);
    GameStateChanged loading = new GameStateChanged();
    loading.setGameState(GameState.LOADING);
    tracker.onGameStateChanged(loading);
    when(other.getWorldLocation()).thenReturn(new WorldPoint(180, 101, 0));
    clock(3100);
    animation();
    clock(3130);
    hit(other, 26);
    clock(3160);
    tracker.onGameTick(new GameTick());
    assertEquals(1, ledger.encounters().size());
    assertTrue(ledger.active());
    assertEquals("Other", ledger.current().events.get(0).player);
  }

  @Test
  public void selfOnlyHonorsTheExistingSetting() throws Exception {
    PluginGateTest.inject(
        tracker,
        "config",
        new TfaConfig() {
          public boolean trackOthers() {
            return false;
          }
        });
    clock(3030);
    hit(other, 19);
    hit(self, 19);
    clock(3060);
    tracker.onGameTick(new GameTick());
    assertEquals(1, ledger.current().events.size());
    assertEquals("Self", ledger.current().events.get(0).player);
  }

  @Test
  public void enteringArchiveDoorResetsTheWholeAttempt() throws Exception {
    clock(3030);
    hit(self, 19);
    clock(3060);
    tracker.onGameTick(new GameTick());
    PluginGateTest.inject(tracker, "started", true);
    tracker.archiveEntered();
    assertTrue(ledger.encounters().isEmpty());
    assertFalse(ledger.active());
  }

  @Test
  public void survivingJelliesAndTheirBloodKeepEncounterOpenAfterBossDisappears() throws Exception {
    when(boss.getAnimation()).thenReturn(14782);
    clock(3070);
    animation();
    NPC jelly = mock(NPC.class);
    when(jelly.getId()).thenReturn(16666);
    when(jelly.getAnimation()).thenReturn(-1);
    when(jelly.getWorldView()).thenReturn(view);
    when(jelly.getWorldLocation()).thenReturn(new WorldPoint(110, 101, 0));
    when(view.npcs().iterator()).thenAnswer(i -> Collections.singletonList(jelly).iterator());
    clock(3090);
    tracker.onGameTick(new GameTick());
    assertTrue(ledger.active());
    Field field = TfaTracker.class.getDeclaredField("dreadnoth");
    field.setAccessible(true);
    DreadnothObserver observer = (DreadnothObserver) field.get(tracker);
    GameObject blood = mock(GameObject.class);
    when(blood.getId()).thenReturn(62609);
    when(blood.getWorldView()).thenReturn(view);
    when(blood.getWorldLocation()).thenReturn(new WorldPoint(110, 101, 0));
    observer.object(blood, true, client, ledger);
    when(view.npcs().iterator()).thenAnswer(i -> Collections.emptyIterator());
    clock(3120);
    hit(other, 8);
    clock(3150);
    tracker.onGameTick(new GameTick());
    assertTrue(ledger.active());
    assertTrue(
        ledger.current().research.stream().anyMatch(e -> e.type.equals("damage") && e.damage == 8));
    assertEquals("Possible Blood tile damage", ledger.current().events.get(0).type);
  }

  @Test
  public void sixRecordedCleanupHitsRemainObservableWithoutMainBoss() throws Exception {
    com.google.gson.JsonObject fixture =
        new com.google.gson.Gson()
            .fromJson(
                new java.io.InputStreamReader(
                    getClass().getResourceAsStream("jelly-cleanup-20261027.json"),
                    java.nio.charset.StandardCharsets.UTF_8),
                com.google.gson.JsonObject.class);
    when(boss.getAnimation()).thenReturn(14782);
    clock(3070);
    animation();
    NPC jelly = mock(NPC.class);
    when(jelly.getId()).thenReturn(16666);
    when(jelly.getAnimation()).thenReturn(-1);
    when(jelly.getWorldView()).thenReturn(view);
    when(jelly.getWorldLocation()).thenReturn(new WorldPoint(110, 101, 0));
    when(view.npcs().iterator()).thenAnswer(i -> Collections.singletonList(jelly).iterator());
    when(other.getInteracting()).thenReturn(jelly);
    clock(3090);
    tracker.onGameTick(new GameTick());
    int expected = 0;
    for (com.google.gson.JsonElement element : fixture.getAsJsonArray("hits")) {
      com.google.gson.JsonObject row = element.getAsJsonObject();
      when(other.getName()).thenReturn(row.get("name").getAsString());
      when(other.getWorldLocation())
          .thenReturn(new WorldPoint(row.get("x").getAsInt(), row.get("y").getAsInt(), 0));
      clock(row.get("cycle").getAsInt());
      when(client.getTickCount()).thenReturn(row.get("tick").getAsInt());
      hit(other, row.get("amount").getAsInt());
      clock(row.get("cycle").getAsInt() + 30);
      tracker.onGameTick(new GameTick());
      expected += row.get("amount").getAsInt();
      assertTrue(ledger.active());
    }
    assertEquals(
        6, ledger.current().research.stream().filter(e -> e.type.equals("damage")).count());
    assertEquals(
        expected,
        ledger.current().research.stream()
            .filter(e -> e.type.equals("damage"))
            .mapToInt(e -> e.damage)
            .sum());
    assertEquals(45, expected);
  }
}
