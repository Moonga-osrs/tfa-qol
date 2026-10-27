package tv.moonga.tfaqol;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.util.*;
import net.runelite.api.*;
import net.runelite.api.coords.*;
import net.runelite.api.events.*;
import org.junit.Test;

public class BorkTrackerTest {
  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  public void smorksStartTrackingAndContinueAfterBorkAndLocalPlayerDie() throws Exception {
    TfaTracker tracker = new TfaTracker();
    Client client = mock(Client.class);
    WorldView view = mock(WorldView.class);
    NPC bork = mock(NPC.class), smorks = mock(NPC.class);
    Player self = mock(Player.class), other = mock(Player.class);
    when(bork.getId()).thenReturn(16640);
    when(smorks.getId()).thenReturn(16641);
    when(smorks.getAnimation()).thenReturn(14554);
    when(smorks.getWorldView()).thenReturn(view);
    when(smorks.getWorldLocation()).thenReturn(new WorldPoint(105, 100, 0));
    when(bork.getWorldLocation()).thenReturn(new WorldPoint(100, 100, 0));
    when(bork.getWorldArea()).thenReturn(new WorldArea(100, 100, 4, 4, 0));
    when(bork.getWorldView()).thenReturn(view);
    when(self.getName()).thenReturn("Self");
    when(other.getName()).thenReturn("Other");
    for (Player p : Arrays.asList(self, other)) {
      when(p.getWorldView()).thenReturn(view);
      when(p.getWorldLocation()).thenReturn(new WorldPoint(104, 101, 0));
      when(p.getInteracting()).thenReturn(smorks);
    }
    IndexedObjectSet ns = mock(IndexedObjectSet.class), ps = mock(IndexedObjectSet.class);
    when(ns.iterator()).thenAnswer(i -> Arrays.asList(bork, smorks).iterator());
    when(ps.iterator()).thenAnswer(i -> Arrays.asList(self, other).iterator());
    when(view.npcs()).thenReturn(ns);
    when(view.players()).thenReturn(ps);
    net.runelite.api.Deque graphics = mock(net.runelite.api.Deque.class);
    when(graphics.iterator()).thenAnswer(i -> Collections.emptyIterator());
    when(view.getGraphicsObjects()).thenReturn(graphics);
    when(client.getTopLevelWorldView()).thenReturn(view);
    when(client.getLocalPlayer()).thenReturn(self);
    when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
    when(client.getGameCycle()).thenReturn(3000);
    when(client.getTickCount()).thenReturn(100);
    PluginGateTest.inject(tracker, "client", client);
    PluginGateTest.inject(tracker, "config", new TfaConfig() {});
    AnimationChanged animation = new AnimationChanged();
    animation.setActor(smorks);
    tracker.onAnimationChanged(animation);
    Field f = TfaTracker.class.getDeclaredField("ledger");
    f.setAccessible(true);
    MistakeLedger ledger = (MistakeLedger) f.get(tracker);
    assertEquals(TfaBoss.BORK_AND_SMORKS, ledger.current().boss);
    tracker.onActorDeath(new ActorDeath(self));
    when(self.isDead()).thenReturn(true);
    when(bork.isDead()).thenReturn(true);
    Projectile projectile = mock(Projectile.class);
    when(projectile.getId()).thenReturn(4057);
    when(projectile.getSourceActor()).thenReturn(smorks);
    when(projectile.getTargetActor()).thenReturn(other);
    when(projectile.getTargetPoint()).thenReturn(new WorldPoint(104, 101, 0));
    when(projectile.getEndCycle()).thenReturn(3120);
    ProjectileMoved moved = new ProjectileMoved();
    moved.setProjectile(projectile);
    tracker.onProjectileMoved(moved);
    when(client.getGameCycle()).thenReturn(3120);
    when(client.getTickCount()).thenReturn(104);
    Hitsplat hit = mock(Hitsplat.class);
    when(hit.getAmount()).thenReturn(12);
    when(hit.getHitsplatType()).thenReturn(HitsplatID.DAMAGE_OTHER);
    HitsplatApplied applied = new HitsplatApplied();
    applied.setActor(other);
    applied.setHitsplat(hit);
    tracker.onHitsplatApplied(applied);
    when(client.getGameCycle()).thenReturn(3150);
    when(client.getTickCount()).thenReturn(105);
    tracker.onGameTick(new GameTick());
    assertTrue(ledger.active());
    assertEquals(2, ledger.current().events.size());
    assertEquals(
        "Other",
        ledger.current().events.stream()
            .filter(e -> !e.type.equals("Death"))
            .findFirst()
            .get()
            .player);
    assertEquals(
        "Possible Magic hit off prayer",
        ledger.current().events.stream()
            .filter(e -> !e.type.equals("Death"))
            .findFirst()
            .get()
            .type);
  }

  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  public void engagedSmorksStartBeforeBorksFirstAnimation() throws Exception {
    TfaTracker tracker = new TfaTracker();
    Client client = mock(Client.class);
    WorldView view = mock(WorldView.class);
    NPC bork = mock(NPC.class), smorks = mock(NPC.class);
    Player self = mock(Player.class), other = mock(Player.class);
    when(bork.getId()).thenReturn(16640);
    when(smorks.getId()).thenReturn(16641);
    when(smorks.getAnimation()).thenReturn(14554);
    when(smorks.getWorldView()).thenReturn(view);
    when(smorks.getWorldLocation()).thenReturn(new WorldPoint(105, 100, 0));
    when(bork.getWorldLocation()).thenReturn(new WorldPoint(100, 100, 0));
    when(bork.getWorldArea()).thenReturn(new WorldArea(100, 100, 4, 4, 0));
    when(bork.getWorldView()).thenReturn(view);
    when(self.getName()).thenReturn("Self");
    when(other.getName()).thenReturn("Other");
    for (Player p : Arrays.asList(self, other)) {
      when(p.getWorldView()).thenReturn(view);
      when(p.getWorldLocation()).thenReturn(new WorldPoint(104, 101, 0));
      when(p.getInteracting()).thenReturn(smorks);
    }
    IndexedObjectSet ns = mock(IndexedObjectSet.class), ps = mock(IndexedObjectSet.class);
    when(ns.iterator()).thenAnswer(i -> Arrays.asList(bork, smorks).iterator());
    when(ps.iterator()).thenAnswer(i -> Arrays.asList(self, other).iterator());
    when(view.npcs()).thenReturn(ns);
    when(view.players()).thenReturn(ps);
    net.runelite.api.Deque graphics = mock(net.runelite.api.Deque.class);
    when(graphics.iterator()).thenAnswer(i -> Collections.emptyIterator());
    when(view.getGraphicsObjects()).thenReturn(graphics);
    when(client.getTopLevelWorldView()).thenReturn(view);
    when(client.getLocalPlayer()).thenReturn(self);
    when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
    when(client.getGameCycle()).thenReturn(3000);
    when(client.getTickCount()).thenReturn(100);
    PluginGateTest.inject(tracker, "client", client);
    PluginGateTest.inject(tracker, "config", new TfaConfig() {});
    when(bork.getAnimation()).thenReturn(-1);
    when(smorks.getAnimation()).thenReturn(-1);
    when(bork.getHealthRatio()).thenReturn(-1);
    when(bork.getHealthScale()).thenReturn(-1);
    when(smorks.getHealthScale()).thenReturn(40);
    when(smorks.getHealthRatio()).thenReturn(38);
    tracker.onGameTick(new GameTick());
    Field f = TfaTracker.class.getDeclaredField("ledger");
    f.setAccessible(true);
    MistakeLedger ledger = (MistakeLedger) f.get(tracker);
    assertEquals(TfaBoss.BORK_AND_SMORKS, ledger.current().boss);
    tracker.onActorDeath(new ActorDeath(self));
    when(self.isDead()).thenReturn(true);
    when(bork.isDead()).thenReturn(true);
    Projectile projectile = mock(Projectile.class);
    when(projectile.getId()).thenReturn(4057);
    when(projectile.getSourceActor()).thenReturn(smorks);
    when(projectile.getTargetActor()).thenReturn(other);
    when(projectile.getTargetPoint()).thenReturn(new WorldPoint(104, 101, 0));
    when(projectile.getEndCycle()).thenReturn(3120);
    ProjectileMoved moved = new ProjectileMoved();
    moved.setProjectile(projectile);
    tracker.onProjectileMoved(moved);
    when(client.getGameCycle()).thenReturn(3120);
    when(client.getTickCount()).thenReturn(104);
    Hitsplat hit = mock(Hitsplat.class);
    when(hit.getAmount()).thenReturn(12);
    when(hit.getHitsplatType()).thenReturn(HitsplatID.DAMAGE_OTHER);
    HitsplatApplied applied = new HitsplatApplied();
    applied.setActor(other);
    applied.setHitsplat(hit);
    tracker.onHitsplatApplied(applied);
    when(client.getGameCycle()).thenReturn(3150);
    when(client.getTickCount()).thenReturn(105);
    tracker.onGameTick(new GameTick());
    assertTrue(ledger.active());
    assertEquals(2, ledger.current().events.size());
    assertEquals(
        "Other",
        ledger.current().events.stream()
            .filter(e -> !e.type.equals("Death"))
            .findFirst()
            .get()
            .player);
    assertEquals(
        "Possible Magic hit off prayer",
        ledger.current().events.stream()
            .filter(e -> !e.type.equals("Death"))
            .findFirst()
            .get()
            .type);
  }
}
