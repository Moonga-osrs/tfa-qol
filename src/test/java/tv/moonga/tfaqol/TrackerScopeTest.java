package tv.moonga.tfaqol;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.util.*;
import net.runelite.api.*;
import net.runelite.api.coords.*;
import net.runelite.api.events.*;
import org.junit.Before;
import org.junit.Test;

public class TrackerScopeTest {
  private TfaTracker tracker;
  private Client client;
  private WorldView view;
  private NPC boss;
  private Player self, teammate, spectator;
  private Settings settings;

  static class Settings implements TfaConfig {
    boolean others = true;

    public boolean trackOthers() {
      return others;
    }
  }

  private Player player(String name, int x, int y) {
    Player p = mock(Player.class);
    when(p.getName()).thenReturn(name);
    when(p.getWorldView()).thenReturn(view);
    when(p.getWorldLocation()).thenReturn(new WorldPoint(x, y, 0));
    return p;
  }

  private MistakeLedger ledger() throws Exception {
    Field f = TfaTracker.class.getDeclaredField("ledger");
    f.setAccessible(true);
    return (MistakeLedger) f.get(tracker);
  }

  @Before
  @SuppressWarnings({"rawtypes", "unchecked"})
  public void setup() throws Exception {
    tracker = new TfaTracker();
    client = mock(Client.class);
    view = mock(WorldView.class);
    boss = mock(NPC.class);
    settings = new Settings();
    PluginGateTest.inject(tracker, "client", client);
    PluginGateTest.inject(tracker, "config", settings);
    when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
    when(client.getTopLevelWorldView()).thenReturn(view);
    when(client.getGameCycle()).thenReturn(100);
    when(client.getTickCount()).thenReturn(10);
    when(view.getBaseX()).thenReturn(1000);
    when(view.getBaseY()).thenReturn(2000);
    when(boss.getId()).thenReturn(16652);
    when(boss.getWorldView()).thenReturn(view);
    when(boss.getAnimation()).thenReturn(14685);
    when(boss.getWorldArea()).thenReturn(new WorldArea(1053, 2053, 3, 3, 0));
    int[][] flags = new int[104][104];
    for (int[] row : flags) Arrays.fill(row, 0x1000000);
    for (int x = 50; x < 59; x++) for (int y = 50; y < 59; y++) flags[x][y] = 0;
    CollisionData collision = mock(CollisionData.class);
    when(collision.getFlags()).thenReturn(flags);
    when(view.getCollisionMaps()).thenReturn(new CollisionData[] {collision});
    self = player("Self", 1051, 2051);
    teammate = player("Teammate", 1052, 2052);
    spectator = player("Spectator", 1080, 2080);
    when(client.getLocalPlayer()).thenReturn(self);
  }

  private void start() {
    AnimationChanged e = new AnimationChanged();
    e.setActor(boss);
    tracker.onAnimationChanged(e);
    try {
      ledger().floor(4394, 51, 51, 0, 0, 100);
      ledger().floor(4394, 52, 52, 0, 0, 100);
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  private void hit(Player p, int type, int damage) {
    Hitsplat h = mock(Hitsplat.class);
    when(h.getHitsplatType()).thenReturn(type);
    when(h.getAmount()).thenReturn(damage);
    HitsplatApplied e = new HitsplatApplied();
    e.setActor(p);
    e.setHitsplat(h);
    tracker.onHitsplatApplied(e);
  }

  @Test
  public void teammatesInsideTfaAreIncludedButSpectatorsOutsideAreNot() throws Exception {
    start();
    hit(self, HitsplatID.DAMAGE_ME, 10);
    hit(teammate, HitsplatID.DAMAGE_OTHER, 20);
    hit(spectator, HitsplatID.DAMAGE_OTHER, 30);
    ledger().drain(140, false);
    assertEquals(2, ledger().current().events.size());
  }

  @Test
  public void selfOnlySettingWorks() throws Exception {
    settings.others = false;
    start();
    hit(self, HitsplatID.DAMAGE_ME, 10);
    hit(teammate, HitsplatID.DAMAGE_OTHER, 20);
    ledger().drain(140, false);
    assertEquals(1, ledger().current().events.size());
    assertEquals("Self", ledger().current().events.get(0).player);
  }

  @Test
  public void nothingIsRecordedOutsideTfa() throws Exception {
    hit(self, HitsplatID.DAMAGE_ME, 10);
    tracker.onActorDeath(new ActorDeath(self));
    assertNull(ledger().current());
  }

  @Test
  public void unrelatedNpcAnimationsDoNotStartAnEncounter() throws Exception {
    when(boss.getId()).thenReturn(999);
    start();
    hit(self, HitsplatID.DAMAGE_ME, 10);
    assertNull(ledger().current());
  }

  @Test
  public void healingAndPrayerDrainAreNotDamage() throws Exception {
    start();
    hit(self, HitsplatID.HEAL, 40);
    hit(self, HitsplatID.PRAYER_DRAIN, 20);
    ledger().drain(140, false);
    assertTrue(ledger().current().events.isEmpty());
  }

  @Test
  public void deathIsObservedWithoutGuessingCause() throws Exception {
    start();
    tracker.onActorDeath(new ActorDeath(teammate));
    assertTrue(ledger().current().events.isEmpty());
  }

  @Test
  public void onlyDefaultLocalUiNoPublicChatIsSent() throws Exception {
    start();
    hit(teammate, HitsplatID.DAMAGE_OTHER, 20);
    ledger().drain(140, false);
    verify(client, never()).addChatMessage(any(), anyString(), anyString(), any());
  }

  @Test
  public void deathAndLoadingKeepTheSameEncounterAndCounters() throws Exception {
    start();
    hit(self, HitsplatID.DAMAGE_ME, 10);
    ledger().drain(140, false);
    MistakeLedger.Encounter original = ledger().current();
    tracker.onActorDeath(new ActorDeath(self));
    GameStateChanged loading = new GameStateChanged();
    loading.setGameState(GameState.LOADING);
    tracker.onGameStateChanged(loading);
    assertSame(original, ledger().current());
    assertTrue(ledger().active());
    assertEquals(1, ledger().current().events.size());
  }

  @Test
  public void teammateDamageStillCountsAfterLocalDeath() throws Exception {
    start();
    tracker.onActorDeath(new ActorDeath(self));
    hit(teammate, HitsplatID.DAMAGE_OTHER, 20);
    ledger().drain(140, false);
    assertEquals(1, ledger().current().events.size());
    assertEquals("Teammate", ledger().current().events.get(0).player);
  }
}
