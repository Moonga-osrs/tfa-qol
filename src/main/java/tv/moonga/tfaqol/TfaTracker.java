package tv.moonga.tfaqol;

import static tv.moonga.tfaqol.PlayerNames.name;

import com.google.gson.Gson;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import javax.inject.Inject;
import javax.swing.*;
import net.runelite.api.*;
import net.runelite.api.coords.*;
import net.runelite.api.events.*;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.*;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.ui.*;
import net.runelite.client.util.Filepath;

public final class TfaTracker {
  @Inject private Client client;
  @Inject private ClientThread clientThread;
  @Inject private ClientToolbar toolbar;
  @Inject private EventBus bus;
  @Inject private TfaConfig config;
  @Inject private Gson gson;
  private final XarthObserver xarth = new XarthObserver();

  private boolean xarthActive() {
    return ledger.active() && ledger.current().boss == TfaBoss.XARTH;
  }

  private final DreadnothObserver dreadnoth = new DreadnothObserver();

  private boolean dreadnothActive() {
    return ledger.active() && ledger.current().boss == TfaBoss.DREADNOTH;
  }

  private final OcellusObserver ocellus = new OcellusObserver();

  private boolean ocellusActive() {
    return ledger.active() && ledger.current().boss == TfaBoss.OCELLUS;
  }

  private final GuardianObserver guardian = new GuardianObserver();
  private final BorkObserver borkObserver = new BorkObserver();

  private boolean borkActive() {
    return ledger.active() && ledger.current().boss == TfaBoss.BORK_AND_SMORKS;
  }

  private boolean guardianActive() {
    return ledger.active() && ledger.current().boss == TfaBoss.ARCHIVE_GUARDIAN;
  }

  private final MistakeLedger ledger = new MistakeLedger();
  private final Set<GraphicsObject> graphics = Collections.newSetFromMap(new IdentityHashMap<>());
  private final Set<Hitsplat> hits = Collections.newSetFromMap(new IdentityHashMap<>());
  private int lastCheckpoint;
  private final Map<String, Integer> deathCycles = new HashMap<>();
  private NavigationButton navigation;
  private MistakePanel panel;
  private ExecutorService io;
  private Filepath reports;
  private boolean panelVisible;
  private NPC boss;
  private WorldView view;
  private EncounterArea area;
  private int baseX, baseY, plane;
  private boolean victory, started, borkSeeded;
  private static final Set<Integer> DIRECT =
      new HashSet<>(
          Arrays.asList(
              HitsplatID.DAMAGE_ME,
              HitsplatID.DAMAGE_OTHER,
              HitsplatID.DAMAGE_MAX_ME,
              HitsplatID.DAMAGE_ME_CYAN,
              HitsplatID.DAMAGE_OTHER_CYAN,
              HitsplatID.DAMAGE_ME_ORANGE,
              HitsplatID.DAMAGE_OTHER_ORANGE,
              HitsplatID.DAMAGE_ME_YELLOW,
              HitsplatID.DAMAGE_OTHER_YELLOW,
              HitsplatID.DAMAGE_ME_WHITE,
              HitsplatID.DAMAGE_OTHER_WHITE));

  private static boolean damageType(int id) {
    return DIRECT.contains(id)
        || id == HitsplatID.BLEED
        || id == HitsplatID.POISON
        || id == HitsplatID.VENOM
        || id == HitsplatID.BURN;
  }

  public void startup(Filepath pluginDirectory) {
    started = true;
    reports = pluginDirectory.join("reports");
    io =
        Executors.newSingleThreadExecutor(
            r -> {
              Thread t = new Thread(r, "tfa-local-reports");
              t.setDaemon(true);
              return t;
            });
    panel =
        new MistakePanel(
            () -> clientThread.invokeLater(() -> save("Manual report")),
            () ->
                clientThread.invokeLater(
                    () -> {
                      finish("Manual reset");
                      save("Before reset");
                      ledger.reset();
                      deathCycles.clear();
                      clearObservers();
                      refreshPanel("Session reset");
                    }),
            () -> setStatus("Report folder: " + reports));

    navigation =
        NavigationButton.builder()
            .tooltip("TFA Qol & mistake tracker")
            .icon(imageForNavigation())
            .panel(panel)
            .priority(5)
            .build();
    syncPanelVisibility();
    bus.register(this);
    refreshPanel("Waiting for TFA");
  }

  public void shutdown() {
    started = false;
    bus.unregister(this);
    finish("Plugin disabled");
    save("Plugin disabled");
    if (navigation != null && panelVisible) toolbar.removeNavigation(navigation);
    panelVisible = false;
    navigation = null;
    if (io != null) io.shutdown();
    clearWorld();
  }

  private void syncPanelVisibility() {
    Runnable change =
        () -> {
          if (!started || navigation == null) return;
          if (config.sidePanel() && !panelVisible) {
            toolbar.addNavigation(navigation);
            panelVisible = true;
          } else if (!config.sidePanel() && panelVisible) {
            toolbar.removeNavigation(navigation);
            panelVisible = false;
          }
        };
    if (SwingUtilities.isEventDispatchThread()) change.run();
    else SwingUtilities.invokeLater(change);
  }

  @Subscribe
  public void onConfigChanged(ConfigChanged e) {
    if ("moongaTfaQol".equals(e.getGroup()) && "sidePanel".equals(e.getKey()))
      syncPanelVisibility();
  }

  private void clearWorld() {
    if (ledger.active()) {
      if (guardianActive()) guardian.drain(client, ledger, config.trackOthers(), true);
      if (borkActive())
        borkObserver.drain(client, ledger, config.trackOthers(), true, config.trackBorkTank());
    }
    xarth.suspend();
    dreadnoth.suspend();
    ocellus.suspend();
    borkSeeded = false;
    guardian.clear();
    borkObserver.clear();
    boss = null;
    view = null;
    area = null;
    graphics.clear();
    hits.clear();
    victory = false;
  }

  private void clearObservers() {
    xarth.clear();
    dreadnoth.clear();
    ocellus.clear();
    guardian.clear();
    borkObserver.clear();
  }

  private void restoreParticipants() {
    guardian.restoreParticipants(ledger.current().players);
    guardian.excludePlayers(ledger.current().deadPlayers);
    borkObserver.restoreParticipants(ledger.current().players);
    borkObserver.excludePlayers(ledger.current().deadPlayers);
    ocellus.restore(ledger);
    dreadnoth.restore(ledger);
    xarth.restore(ledger);
  }

  private void prepare(WorldView w) {
    if (view != w || baseX != w.getBaseX() || baseY != w.getBaseY() || plane != w.getPlane()) {
      clearWorld();
      if (ledger.active()) {
        restoreParticipants();
      }
      view = w;
      baseX = w.getBaseX();
      baseY = w.getBaseY();
      plane = w.getPlane();
    }
  }

  private void begin(boolean joined) {
    if (boss == null) return;
    if (ledger.active()) {
      BossMistakeDetector next = BossDetectors.forNpc(boss.getId());
      if (next != null && next.boss() != ledger.current().boss) finish("Next boss observed");
      else return;
    }
    BossMistakeDetector detector = BossDetectors.forNpc(boss.getId());
    if (detector == null) return;
    ledger.begin(detector.boss(), client.getTickCount(), client.getGameCycle(), joined);
    victory = false;
    lastCheckpoint = client.getTickCount();
    deathCycles.clear();
    clearObservers();
    graphics.clear();
    hits.clear();
    if (borkActive()) {
      borkObserver.actors(view);
      seedBorkGround();
    }
    updateArea();
    refreshPanel(detector.boss().label + " observed");
  }

  private void updateArea() {
    if (borkActive() || ocellusActive() || dreadnothActive() || xarthActive()) return;
    if (view != null
        && boss != null
        && view.getCollisionMaps() != null
        && plane < view.getCollisionMaps().length
        && view.getCollisionMaps()[plane] != null) {
      WorldArea b = boss.getWorldArea();
      int[][] f = view.getCollisionMaps()[plane].getFlags();
      EncounterArea next =
          EncounterArea.find(
              f, b.getX() - baseX + b.getWidth() / 2, b.getY() - baseY + b.getHeight() / 2);
      if (next == null && area != null)
        next = EncounterArea.find(f, area.x + area.size / 2, area.y + area.size / 2);
      if (next != null && (area == null || next.size >= area.size)) area = next;
    }
  }

  private void finish(String reason) {
    if (ledger.current() == null || ledger.current().endTick >= 0) return;
    if (guardianActive()) guardian.drain(client, ledger, config.trackOthers(), true);
    if (borkActive())
      borkObserver.drain(client, ledger, config.trackOthers(), true, config.trackBorkTank());
    if (xarthActive()) xarth.drain(client, ledger, true);
    if (dreadnothActive()) dreadnoth.drain(client, ledger, true);
    if (ocellusActive()) ocellus.drain(client, ledger, true, config.trackOcellusFloorCracks());
    ledger.finish(client.getTickCount(), client.getGameCycle(), reason);
    save(reason);
    refreshPanel(reason);
  }

  public void archiveEntered() {
    if (!started) return;
    finish("Archive Door entered; new attempt");
    save("Before Archive Door reset");
    ledger.reset();
    deathCycles.clear();
    xarth.clear();
    dreadnoth.clear();
    ocellus.clear();
    clearWorld();
    refreshPanel("New attempt - counters reset");
  }

  @Subscribe
  public void onAnimationChanged(AnimationChanged e) {
    if (!config.mistakeTracker() || !(e.getActor() instanceof NPC)) return;
    NPC n = (NPC) e.getActor();
    if (xarthActive() && n.getId() >= 16685 && n.getId() <= 16688 && n.getWorldView() == view) {
      xarth.animation(n, client, ledger);
      return;
    }
    if (dreadnothActive() && n.getId() == 16666 && n.getWorldView() == view) {
      dreadnoth.animation(n, client, ledger);
      return;
    }
    if (ocellusActive()
        && OcellusObserver.related(n.getId())
        && !OcellusObserver.main(n.getId())
        && n.getWorldView() == view) {
      ocellus.animation(n, client.getTickCount(), client.getGameCycle(), ledger);
      return;
    }
    if (guardianActive() && n.getId() == 16664 && n.getWorldView() == view) {
      auditAnimation(n);
      guardian.animation(client, n, boss);
      return;
    }
    BossMistakeDetector detector = BossDetectors.forNpc(n.getId());
    if (detector == null || n.getWorldView() != client.getTopLevelWorldView()) return;
    prepare(n.getWorldView());
    boss = n;
    int a = n.getAnimation();
    if (detector.startsEncounter(a)) begin(false);
    if (detector.endsEncounter(a) && ledger.active()) victory = true;
    if (xarthActive()) {
      xarth.actors(client, ledger, config.trackOthers());
      xarth.animation(n, client, ledger);
    }
    if (dreadnothActive()) {
      dreadnoth.actors(client, ledger, config.trackOthers());
      dreadnoth.animation(n, client, ledger);
    }
    if (ocellusActive()) {
      ocellus.actors(view, ledger, client.getTickCount(), client.getGameCycle());
      ocellus.animation(n, client.getTickCount(), client.getGameCycle(), ledger);
    }
    auditAnimation(n);
    if (guardianActive()) guardian.animation(client, n, boss);
    if (borkActive()) {
      borkObserver.actors(view);
      borkObserver.animation(n, client.getGameCycle());
    }
  }

  @Subscribe
  public void onGameTick(GameTick e) {
    if (!config.mistakeTracker()) {
      finish("Tracking disabled");
      clearWorld();
      return;
    }
    WorldView w = client.getTopLevelWorldView();
    if (w == null || client.getGameState() != GameState.LOGGED_IN) return;
    prepare(w);
    NPC found = null;
    for (NPC n : w.npcs())
      if (BossDetectors.forNpc(n.getId()) != null) {
        if (found == null || found.isDead() && !n.isDead() || n.getId() == 16640 && !n.isDead())
          found = n;
      }
    if (found == null) {
      if (guardianActive()) guardian.drain(client, ledger, config.trackOthers(), false);
      if (borkActive())
        borkObserver.drain(client, ledger, config.trackOthers(), false, config.trackBorkTank());
      if (xarthActive()) {
        xarth.actors(client, ledger, config.trackOthers());
        xarth.drain(client, ledger, false);
      }
      if (dreadnothActive()) {
        dreadnoth.actors(client, ledger, config.trackOthers());
        dreadnoth.drain(client, ledger, false);
      }
      if (ocellusActive()) ocellus.drain(client, ledger, false, config.trackOcellusFloorCracks());
      if (w.getGraphicsObjects() != null)
        for (GraphicsObject g : w.getGraphicsObjects()) capture(g);
      hits.clear();
      boolean cleanup = dreadnothActive() && dreadnoth.cleanupActive();
      if (victory && !cleanup) finish("Boss end animation observed");
      boss = null;
      if (ledger.active() && client.getTickCount() - lastCheckpoint >= 200) {
        lastCheckpoint = client.getTickCount();
        save("Automatic cleanup checkpoint");
      }
      if (ledger.active())
        refreshPanel(
            cleanup
                ? "Dreadnoth cleanup - jellies/blood still tracked"
                : "Combat out of view - counters kept");
      return;
    }
    boss = found;
    if (ledger.active() && BossDetectors.forNpc(boss.getId()).boss() != ledger.current().boss)
      finish("Next boss observed");
    if (!ledger.active()
        && (boss.getHealthScale() > 0
                && boss.getHealthRatio() > 0
                && boss.getHealthRatio() < boss.getHealthScale()
            || BossDetectors.forNpc(boss.getId()).boss() == TfaBoss.BORK_AND_SMORKS
                && borkCombatObserved(w))) begin(true);
    if (!ledger.active()) return;
    updateArea();
    if (xarthActive()) {
      xarth.actors(client, ledger, config.trackOthers());
      xarth.graphics(client, ledger, config.trackOthers());
      if (client.getProjectiles() != null)
        for (Projectile shot : client.getProjectiles()) xarth.projectile(shot, client, ledger);
      xarth.drain(client, ledger, false);
    }
    if (dreadnothActive()) {
      dreadnoth.actors(client, ledger, config.trackOthers());
      if (client.getProjectiles() != null)
        for (Projectile shot : client.getProjectiles()) dreadnoth.projectile(shot, client, ledger);
      dreadnoth.drain(client, ledger, false);
    }
    if (ocellusActive()) {
      ocellus.actors(w, ledger, client.getTickCount(), client.getGameCycle());
      ocellus.frame(client, ledger, config.trackOthers());
      ocellus.snapshot(client, ledger, config.trackOthers());
      if (client.getProjectiles() != null)
        for (Projectile shot : client.getProjectiles()) ocellus.projectile(shot, client, ledger);
      ocellus.drain(client, ledger, false, config.trackOcellusFloorCracks());
    }
    if (guardianActive()) guardian.tick(client, boss, ledger, config.trackOthers());
    if (borkActive()) {
      borkObserver.actors(w);
      if (!borkSeeded) seedBorkGround();
      if (client.getProjectiles() != null)
        for (Projectile shot : client.getProjectiles()) borkObserver.projectile(shot, client);
      borkObserver.frame(client);
      borkObserver.drain(client, ledger, config.trackOthers(), false, config.trackBorkTank());
    }
    for (Player p : w.players())
      if (inArea(p) && (config.trackOthers() || p == client.getLocalPlayer())) {
        ledger.participant(name(p));
      }
    auditSnapshot(w);
    for (GraphicsObject object : w.getGraphicsObjects()) capture(object);
    ledger.drain(client.getGameCycle(), false);
    hits.clear();
    if (client.getTickCount() - lastCheckpoint >= 200) {
      lastCheckpoint = client.getTickCount();
      save("Automatic combat checkpoint");
    }
    refreshPanel(ledger.current().room + " - tracking");
  }

  @Subscribe
  public void onClientTick(ClientTick e) {
    if (!config.mistakeTracker() || client.getGameState() != GameState.LOGGED_IN) return;
    WorldView w = client.getTopLevelWorldView();
    if (w == null) return;
    if (xarthActive() && view == w) xarth.graphics(client, ledger, config.trackOthers());
    if (ocellusActive() && view == w) {
      ocellus.frame(client, ledger, config.trackOthers());
      if (client.getProjectiles() != null)
        for (Projectile shot : client.getProjectiles()) ocellus.projectile(shot, client, ledger);
    }
    if (borkActive() && view == w) {
      borkObserver.actors(w);
      borkObserver.frame(client);
    }
    for (NPC n : w.npcs())
      if (n.getId() >= 16658 && n.getId() <= 16660 && n.getSpotAnims() != null) {
        for (ActorSpotAnim spot : n.getSpotAnims())
          if (spot.getId() == 4204 || spot.getId() == 4199 || spot.getId() == 4202) {
            if (spot.getStartCycle() > client.getGameCycle()
                || client.getGameCycle() - spot.getStartCycle() > 30) continue;
            prepare(w);
            boss = n;
            if (!ledger.active()) begin(false);
            if (guardianActive()) guardian.graphic(client, n, spot.getId(), spot.getStartCycle());
          }
      }
  }

  private boolean borkCombatObserved(WorldView w) {
    for (NPC n : w.npcs())
      if (n.getId() == 16640 || n.getId() == 16641) {
        if (n.getHealthScale() > 0
            && n.getHealthRatio() > 0
            && n.getHealthRatio() < n.getHealthScale()) return true;
        if (n.getInteracting() instanceof Player) {
          Actor target = ((Player) n.getInteracting()).getInteracting();
          if (target instanceof NPC
              && (((NPC) target).getId() == 16640 || ((NPC) target).getId() == 16641)) return true;
        }
      }
    return false;
  }

  private boolean inArea(Player p) {
    if (xarthActive()) return xarth.participant(p);
    if (dreadnothActive()) return dreadnoth.participant(p);
    if (ocellusActive()) return ocellus.participant(p);
    if (borkActive()) return borkObserver.participant(p);
    if (ledger.active()
        && view != null
        && p.getWorldView() == view
        && p.getWorldLocation().getPlane() == plane
        && ledger.current().players.contains(name(p))
        && !ledger.current().deadPlayers.contains(name(p))
        && !p.isDead()) return true;
    if (guardianActive() && boss != null) return guardian.participant(p, boss);
    if (view == null
        || p.getWorldView() != view
        || p.getWorldLocation().getPlane() != plane
        || area == null) return false;
    WorldPoint point = p.getWorldLocation();
    return area.contains(point.getX() - baseX, point.getY() - baseY);
  }

  private void auditAnimation(NPC n) {
    if ((guardianActive() || borkActive()) && n.getWorldView() == view)
      ledger.research(
          client.getTickCount(),
          client.getGameCycle(),
          null,
          "animation",
          "npc="
              + n.getId()
              + " index="
              + n.getIndex()
              + " animation="
              + n.getAnimation()
              + " at="
              + n.getWorldLocation()
              + " target="
              + (n.getInteracting() instanceof Player ? name((Player) n.getInteracting()) : "none"),
          0);
  }

  private void auditHit(Player p, Hitsplat h) {
    if (!(guardianActive() || borkActive())
        || h.getAmount() <= 0
        || !damageType(h.getHitsplatType())
        || !config.trackOthers() && p != client.getLocalPlayer()) return;
    ledger.research(
        client.getTickCount(),
        client.getGameCycle(),
        name(p),
        "hit-context",
        "at="
            + p.getWorldLocation()
            + " hitsplat="
            + h.getHitsplatType()
            + " overhead="
            + p.getOverheadIcon()
            + " boss="
            + (boss == null ? "none" : boss.getId() + " animation=" + boss.getAnimation())
            + " localMagic="
            + (p == client.getLocalPlayer()
                ? client.isPrayerActive(Prayer.PROTECT_FROM_MAGIC)
                : "not recorded")
            + " localRanged="
            + (p == client.getLocalPlayer()
                ? client.isPrayerActive(Prayer.PROTECT_FROM_MISSILES)
                : "not recorded"),
        h.getAmount());
  }

  private void auditSnapshot(WorldView w) {
    if (!(guardianActive() || borkActive())) return;
    StringBuilder state = new StringBuilder();
    for (NPC n : w.npcs())
      if (BossDetectors.forNpc(n.getId()) != null || n.getId() == 16664)
        state
            .append("npc=")
            .append(n.getId())
            .append(" at=")
            .append(n.getWorldLocation())
            .append(" animation=")
            .append(n.getAnimation())
            .append(';');
    for (Player p : w.players())
      if ((config.trackOthers() || p == client.getLocalPlayer()) && inArea(p))
        state
            .append("player=")
            .append(name(p))
            .append(" at=")
            .append(p.getWorldLocation())
            .append(" overhead=")
            .append(p.getOverheadIcon())
            .append(';');
    ledger.research(
        client.getTickCount(), client.getGameCycle(), null, "snapshot", state.toString(), 0);
  }

  private boolean recentDeath(Player p) {
    return ledger.active()
        && view != null
        && p.getWorldView() == view
        && p.getWorldLocation().getPlane() == plane
        && ledger.current().players.contains(name(p))
        && Math.abs(client.getGameCycle() - deathCycles.getOrDefault(name(p), -1000000)) <= 10;
  }

  private boolean track(Player p) {
    return ledger.active() && (config.trackOthers() || p == client.getLocalPlayer()) && inArea(p);
  }

  private void capture(GraphicsObject g) {
    if (!ledger.active()
        || view == null
        || g.getWorldView() != view
        || g.getLevel() != plane
        || !graphics.add(g)) return;
    int id = g.getId();
    LocalPoint p = g.getLocation();
    if (xarthActive())
      xarth.floor(
          id,
          p.getSceneX() + baseX,
          p.getSceneY() + baseY,
          plane,
          g.getStartCycle(),
          client,
          ledger);
    if (dreadnothActive())
      dreadnoth.floor(
          id,
          p.getSceneX() + baseX,
          p.getSceneY() + baseY,
          plane,
          g.getStartCycle(),
          client,
          ledger);
    if (ocellusActive())
      ocellus.floor(
          id,
          p.getSceneX() + baseX,
          p.getSceneY() + baseY,
          plane,
          g.getStartCycle(),
          client.getTickCount(),
          client.getGameCycle(),
          ledger);
    if (borkActive() && id == 4395)
      borkObserver.floor(p.getSceneX() + baseX, p.getSceneY() + baseY, g.getStartCycle());
    ledger.floor(id, p.getSceneX(), p.getSceneY(), plane, view.getId(), g.getStartCycle());
    // Joining during phase two: locate the shrunken floor from its actual graphics, not the boss.
    if (area == null
        && (id == 4197 || id == 4198)
        && view.getCollisionMaps() != null
        && view.getCollisionMaps()[plane] != null)
      area =
          EncounterArea.find(
              view.getCollisionMaps()[plane].getFlags(), p.getSceneX(), p.getSceneY());
  }

  @Subscribe
  public void onGraphicsObjectCreated(GraphicsObjectCreated e) {
    if (config.mistakeTracker()) capture(e.getGraphicsObject());
  }

  private void seedBorkGround() {
    if (view == null || view.getScene() == null) return;
    Tile[][][] tiles = view.getScene().getTiles();
    if (tiles == null || plane < 0 || plane >= tiles.length) return;
    borkSeeded = true;
    Set<TileObject> seen = Collections.newSetFromMap(new IdentityHashMap<>());
    for (Tile[] row : tiles[plane])
      if (row != null)
        for (Tile tile : row)
          if (tile != null) {
            if (tile.getGameObjects() != null)
              for (GameObject object : tile.getGameObjects())
                if (object != null && object.getId() == 62544 && seen.add(object))
                  objectSpawn(object);
            GroundObject ground = tile.getGroundObject();
            if (ground != null && ground.getId() == 62544) objectSpawn(ground);
          }
  }

  @Subscribe
  public void onProjectileMoved(ProjectileMoved e) {
    if (!config.mistakeTracker() || view == null) return;
    if (xarthActive()) xarth.projectile(e.getProjectile(), client, ledger);
    if (dreadnothActive()) dreadnoth.projectile(e.getProjectile(), client, ledger);
    if (ocellusActive()) ocellus.projectile(e.getProjectile(), client, ledger);
    if (borkActive()) borkObserver.projectile(e.getProjectile(), client);
  }

  private void objectSpawn(TileObject o) {
    if (xarthActive()) xarth.object(o, true, client, ledger);
    if (dreadnothActive()) dreadnoth.object(o, true, client, ledger);
    if (ocellusActive())
      ocellus.object(o, true, client.getTickCount(), client.getGameCycle(), ledger);
    if (borkActive() && o.getWorldView() == view && o.getPlane() == plane && o.getId() == 62544) {
      WorldPoint at = o.getWorldLocation();
      borkObserver.ground(at.getX(), at.getY(), client.getGameCycle(), true);
    }
    if (guardianActive() && o.getWorldView() == view && o.getPlane() == plane) {
      WorldPoint p = o.getWorldLocation();
      guardian.mushroom(o.getId(), p.getX(), p.getY(), client.getTickCount());
    }
  }

  private void objectRemoved(TileObject o) {
    if (xarthActive()) xarth.object(o, false, client, ledger);
    if (dreadnothActive()) dreadnoth.object(o, false, client, ledger);
    if (ocellusActive())
      ocellus.object(o, false, client.getTickCount(), client.getGameCycle(), ledger);
    if (borkActive() && o.getWorldView() == view && o.getPlane() == plane && o.getId() == 62544) {
      WorldPoint at = o.getWorldLocation();
      borkObserver.ground(at.getX(), at.getY(), client.getGameCycle(), false);
    }
    if (guardianActive() && o.getId() == 62580 && o.getWorldView() == view) {
      WorldPoint p = o.getWorldLocation();
      guardian.mushroomRemoved(p.getX(), p.getY());
    }
  }

  @Subscribe
  public void onGameObjectSpawned(GameObjectSpawned e) {
    objectSpawn(e.getGameObject());
  }

  @Subscribe
  public void onGroundObjectSpawned(GroundObjectSpawned e) {
    objectSpawn(e.getGroundObject());
  }

  @Subscribe
  public void onGameObjectDespawned(GameObjectDespawned e) {
    objectRemoved(e.getGameObject());
  }

  @Subscribe
  public void onGroundObjectDespawned(GroundObjectDespawned e) {
    objectRemoved(e.getGroundObject());
  }

  @Subscribe
  public void onHitsplatApplied(HitsplatApplied e) {
    if (!config.mistakeTracker() || !(e.getActor() instanceof Player)) return;
    Player p = (Player) e.getActor();
    Hitsplat h = e.getHitsplat();
    int type = h.getHitsplatType();
    if (ledger.active()
        && damageType(type)
        && h.getAmount() > 0
        && (config.trackOthers() || p == client.getLocalPlayer())
        && (inArea(p) || recentDeath(p))
        && !ocellusActive()) {
      String weapon = WeaponSelfDamage.evidence(client, p, h);
      if (weapon != null)
        ledger.research(
            client.getTickCount(),
            client.getGameCycle(),
            name(p),
            "self-damage-context",
            weapon,
            h.getAmount());
    }
    if (xarthActive()) {
      if (damageType(type)
          && (config.trackOthers() || p == client.getLocalPlayer())
          && xarth.acceptsHit(p, client.getGameCycle())
          && hits.add(h)) xarth.hit(p, h, client);
      return;
    }
    if (dreadnothActive()) {
      if (damageType(type)
          && (config.trackOthers() || p == client.getLocalPlayer())
          && dreadnoth.acceptsHit(p, client.getGameCycle())
          && hits.add(h)) dreadnoth.hit(p, h, client);
      return;
    }
    if (ocellusActive()) {
      if ((damageType(type) || type == HitsplatID.HEAL)
          && (config.trackOthers() || p == client.getLocalPlayer())
          && ocellus.acceptsHit(p, client.getGameCycle())
          && hits.add(h)) ocellus.hit(p, h, client, ledger);
      return;
    }
    boolean block = type == HitsplatID.BLOCK_ME || type == HitsplatID.BLOCK_OTHER;
    if (!(damageType(type) || block)
        || !(inArea(p) || recentDeath(p))
        || !ledger.active()
        || !hits.add(h)) return;
    auditHit(p, h);
    if (guardianActive()) {
      guardian.hit(client, p, h);
      return;
    }
    if (borkActive()) {
      if (h.getAmount() > 0) borkObserver.hit(p, h, DIRECT.contains(type), client);
      return;
    }
    if (!track(p) || h.getAmount() <= 0) return;
    WorldPoint at = p.getWorldLocation();
    ledger.hit(
        name(p),
        h.getAmount(),
        DIRECT.contains(type),
        at.getX() - baseX,
        at.getY() - baseY,
        plane,
        view.getId(),
        client.getTickCount(),
        client.getGameCycle());
  }

  @Subscribe
  public void onActorDeath(ActorDeath e) {
    if (!config.mistakeTracker() || !ledger.active()) return;
    if (xarthActive() && e.getActor() instanceof NPC && ((NPC) e.getActor()).getId() == 16684) {
      victory = true;
      return;
    }
    if (dreadnothActive() && e.getActor() instanceof NPC && ((NPC) e.getActor()).getId() == 16665) {
      victory = true;
      return;
    }
    if (ocellusActive()
        && e.getActor() instanceof NPC
        && OcellusObserver.main(((NPC) e.getActor()).getId())) {
      victory = true;
      return;
    }
    if (!(e.getActor() instanceof Player)) return;
    Player p = (Player) e.getActor();
    if (inArea(p) || ledger.current().players.contains(name(p))) {
      deathCycles.put(name(p), client.getGameCycle());
      ledger.current().deadPlayers.add(name(p));
      if (borkActive()) borkObserver.excludePlayers(Collections.singleton(name(p)));
    }
    if (xarthActive()
        && (inArea(p) || ledger.current().players.contains(name(p)))
        && (config.trackOthers() || p == client.getLocalPlayer())) xarth.death(p, client, ledger);
    if (dreadnothActive()
        && (inArea(p) || ledger.current().players.contains(name(p)))
        && (config.trackOthers() || p == client.getLocalPlayer()))
      dreadnoth.death(p, client, ledger);
    if (ocellusActive()
        && (inArea(p) || ledger.current().players.contains(name(p)))
        && (config.trackOthers() || p == client.getLocalPlayer()))
      ocellus.death(p, client.getTickCount(), client.getGameCycle(), ledger);
    if ((guardianActive() || borkActive())
        && ledger.current().players.contains(name(p))
        && (config.trackOthers() || p == client.getLocalPlayer()))
      ledger.research(
          client.getTickCount(),
          client.getGameCycle(),
          name(p),
          "death",
          "Observed death; counters preserved",
          0);
    if (guardianActive()) guardian.death(p);
  }

  @Subscribe
  public void onGameStateChanged(GameStateChanged e) {
    if (e.getGameState() == GameState.LOADING) {
      clearWorld();
      if (ledger.active()) {
        restoreParticipants();
      }
      refreshPanel("Loading view - counters kept");
    } else if (e.getGameState() == GameState.LOGIN_SCREEN
        || e.getGameState() == GameState.HOPPING) {
      finish("Logout/hop; observations saved");
      clearWorld();
    }
  }

  private void save(String reason) {
    if (io == null || ledger.encounters().isEmpty()) return;
    String payload = gson.toJson(report(reason));
    String file =
        "tfa-"
            + DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")
                .withZone(ZoneOffset.UTC)
                .format(Instant.now())
            + "-"
            + UUID.randomUUID().toString().substring(0, 8)
            + ".json";
    io.submit(
        () -> {
          try {
            reports.createDirectories();
            reports.joinSegment(file).write(payload);
            setStatus("Local report saved");
          } catch (Exception failure) {
            setStatus("Could not save local report");
          }
        });
  }

  private Map<String, Object> report(String reason) {
    Map<String, Object> report = new LinkedHashMap<>();
    report.put("schemaVersion", 2);
    report.put("pluginVersion", "1.0.0");
    report.put(
        "scope",
        "Archive Guardian, Ezekiel, Bork and the Smorks, Dreadnoth, Xarth, Ocellus; possible"
            + " mistakes and separate damage research for testing");
    report.put("savedAt", Instant.now().toString());
    report.put("reason", reason);
    report.put(
        "limitations",
        "Possible mechanics are correlations, not proven damage sources. Only visible players are"
            + " observed; Prayer checks for others use visible overheads at impact. Xarth"
            + " correct-prayer magic chip and all BURN/BLEED status ticks are excluded from mistake"
            + " counters. Research includes unresolved damage, healing and soul mechanics which are"
            + " not automatic mistakes. Unloaded actors remain unobservable.");
    report.put("encounters", ledger.encounters());
    return report;
  }

  private BufferedImage imageForNavigation() {
    BufferedImage image = new BufferedImage(24, 24, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = image.createGraphics();
    MistakePanel.icon("cabbage", 24).paintIcon(null, g, 0, 0);
    g.dispose();
    return image;
  }

  private void setStatus(String text) {
    SwingUtilities.invokeLater(
        () -> {
          if (panel != null) panel.status(text);
        });
  }

  private void refreshPanel(String text) {
    List<MistakePanel.Fight> fights = new ArrayList<>();
    for (MistakeLedger.Encounter encounter : ledger.encounters())
      if (encounter.boss != null) {
        List<MistakePanel.Row> rows = new ArrayList<>();
        for (MistakeLedger.Event event : encounter.events) {
          MistakeKind kind = MistakeKind.find(encounter.boss, event.type);
          if (kind != null)
            rows.add(new MistakePanel.Row(kind, event.player, event.damage, event.tick));
        }
        int review = 0, unresolved = 0;
        for (MistakeLedger.Event event : encounter.research)
          if (event.type.equals("damage")) {
            review++;
            if (event.evidence.contains("status=UNRESOLVED")
                || event.evidence.contains("status=AMBIGUOUS")) unresolved++;
          }
        fights.add(
            new MistakePanel.Fight(
                encounter.number, encounter.boss, rows, encounter.players, review, unresolved));
      }
    SwingUtilities.invokeLater(
        () -> {
          if (panel != null) panel.updateFights(fights, text);
        });
  }
}
