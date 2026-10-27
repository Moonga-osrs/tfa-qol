package tv.moonga.tfaqol;

import static tv.moonga.tfaqol.PlayerNames.name;

import java.util.*;
import net.runelite.api.*;
import net.runelite.api.coords.*;

/** Full-fight observation, including status after the death animation and corpse/fire context. */
final class XarthObserver {
  private final XarthRules rules = new XarthRules();
  private final Set<String> players = new LinkedHashSet<>(),
      dead = new HashSet<>(),
      seen = new HashSet<>();
  private final Map<String, Integer> deathCycles = new HashMap<>(),
      burnCycles = new HashMap<>(),
      burnEpisodes = new HashMap<>();
  private final Map<String, Boolean> inFire = new HashMap<>();
  private final Map<NPC, Integer> animations = new IdentityHashMap<>();
  private final Map<Projectile, Shot> shots = new IdentityHashMap<>();
  private final List<XarthRules.Hit> pending = new ArrayList<>();

  private static final class Shot {
    int id, start, end;
    String target;
    WorldPoint point;
  }

  private WorldView view;
  private NPC boss;
  private boolean seeded, phaseTwo;
  private int lastSnapshot = -1;

  private static boolean related(int id) {
    return id >= 16684 && id <= 16688;
  }

  void clear() {
    suspend();
    players.clear();
    dead.clear();
    deathCycles.clear();
    burnCycles.clear();
    burnEpisodes.clear();
    pending.clear();
    phaseTwo = false;
    lastSnapshot = -1;
  }

  void suspend() {
    rules.clear();
    animations.clear();
    shots.clear();
    seen.clear();
    inFire.clear();
    view = null;
    boss = null;
    seeded = false;
  }

  void restore(MistakeLedger ledger) {
    if (ledger.current() != null) {
      players.addAll(ledger.current().players);
      dead.addAll(ledger.current().deadPlayers);
    }
  }

  boolean participant(Player p) {
    if (view == null
        || p.getWorldView() != view
        || p.isDead()
        || dead.contains(name(p))
        || p.getWorldLocation().getPlane() != view.getPlane()) return false;
    if (players.contains(name(p))) return true;
    Actor target = p.getInteracting();
    return target instanceof NPC && related(((NPC) target).getId())
        || boss != null && p.getWorldLocation().distanceTo(boss.getWorldLocation()) <= 64;
  }

  boolean acceptsHit(Player p, int cycle) {
    return participant(p)
        || view != null
            && p.getWorldView() == view
            && p.getWorldLocation().getPlane() == view.getPlane()
            && players.contains(name(p))
            && Math.abs(cycle - deathCycles.getOrDefault(name(p), -1000000)) <= 10;
  }

  void actors(Client client, MistakeLedger ledger, boolean others) {
    view = client.getTopLevelWorldView();
    if (view == null) return;
    boss = null;
    for (NPC n : view.npcs())
      if (n.getId() == 16684) {
        boss = n;
        break;
      }
    for (Player p : view.players())
      if ((others || p == client.getLocalPlayer()) && participant(p)) {
        players.add(name(p));
        ledger.participant(name(p));
      }
    for (NPC n : view.npcs())
      if (related(n.getId())) {
        Integer old = animations.get(n);
        int a = n.getAnimation();
        if (old == null || old != a) {
          if (a >= 0 && n.getAnimationFrame() == 0) animation(n, client, ledger);
          else animations.put(n, a);
        }
      }
    if (!seeded && view.getScene() != null) {
      Tile[][][] tiles = view.getScene().getTiles();
      int plane = view.getPlane();
      if (tiles != null && plane >= 0 && plane < tiles.length) {
        seeded = true;
        Set<TileObject> unique = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Tile[] row : tiles[plane])
          if (row != null)
            for (Tile tile : row)
              if (tile != null) {
                if (tile.getGameObjects() != null)
                  for (GameObject o : tile.getGameObjects())
                    if (o != null && o.getId() == 62713 && unique.add(o))
                      object(o, true, client, ledger);
                GroundObject o = tile.getGroundObject();
                if (o != null && o.getId() == 62713) object(o, true, client, ledger);
              }
      }
    }
    if (lastSnapshot != client.getTickCount()) {
      lastSnapshot = client.getTickCount();
      StringBuilder state =
          new StringBuilder("phase=").append(phaseTwo ? "ranged/corpses" : "rubble/flame");
      if (boss != null)
        state
            .append(" boss=")
            .append(boss.getWorldLocation())
            .append(" animation=")
            .append(boss.getAnimation());
      for (Player p : view.players())
        if ((others || p == client.getLocalPlayer()) && participant(p)) {
          WorldPoint w = p.getWorldLocation();
          boolean on = rules.onFire(w.getX(), w.getY(), w.getPlane(), client.getGameCycle());
          Boolean before = inFire.put(name(p), on);
          if (on && !Boolean.TRUE.equals(before))
            log(
                ledger,
                client.getTickCount(),
                client.getGameCycle(),
                name(p),
                "fire-contact",
                "Object 62713 under player; exposure context, not an automatic burn mistake",
                0);
          state
              .append("; player=")
              .append(name(p))
              .append(" at=")
              .append(w)
              .append(" overhead=")
              .append(p.getOverheadIcon())
              .append(" fire=")
              .append(on);
        }
      log(
          ledger,
          client.getTickCount(),
          client.getGameCycle(),
          null,
          "snapshot",
          state.toString(),
          0);
    }
  }

  void animation(NPC npc, Client client, MistakeLedger ledger) {
    int a = npc.getAnimation(), cycle = client.getGameCycle();
    if (view == null || npc.getWorldView() != view || !related(npc.getId())) return;
    animations.put(npc, a);
    if (a < 0 || !seen.add("a:" + npc.getIndex() + ":" + a + ":" + cycle)) return;
    if (npc.getId() == 16687 && a == 15005 && npc.getInteracting() instanceof NPC) {
      NPC target = (NPC) npc.getInteracting();
      if (target.getId() == 16684 && target.getWorldView() == view)
        ledger.observation(
            "Group",
            "Possible " + MistakeKind.XARTH_CHARGED.label,
            0,
            client.getTickCount(),
            cycle,
            "Corpse index="
                + npc.getIndex()
                + " animation=15005 targeting Xarth; individual responsibility unknown");
    }
    if (npc.getId() == 16684) {
      if (a == 14985 && !phaseTwo) {
        phaseTwo = true;
        log(
            ledger,
            client.getTickCount(),
            cycle,
            null,
            "phase",
            "First ranged attack 14985; ranged/corpse block",
            0);
      }
      if (a == 14983) {
        WorldPoint p = npc.getWorldLocation();
        rules.add(
            new XarthRules.Signal(
                XarthRules.Source.MELEE, a, cycle, cycle, p.getX(), p.getY(), p.getPlane(), null));
      }
      if (a == 14989) {
        WorldPoint p = npc.getWorldLocation();
        rules.add(
            new XarthRules.Signal(
                XarthRules.Source.EXPLOSION,
                a,
                cycle,
                cycle + 90,
                p.getX(),
                p.getY(),
                p.getPlane(),
                null));
      }
    }
    log(
        ledger,
        client.getTickCount(),
        cycle,
        null,
        "animation",
        "npc="
            + npc.getId()
            + " index="
            + npc.getIndex()
            + " animation="
            + a
            + " at="
            + npc.getWorldLocation()
            + " target="
            + (npc.getInteracting() instanceof Player
                ? name((Player) npc.getInteracting())
                : "none"),
        0);
  }

  void graphics(Client client, MistakeLedger ledger, boolean others) {
    if (view == null) return;
    for (NPC n : view.npcs()) if (related(n.getId())) spot(n, null, client, ledger);
    for (Player p : view.players())
      if ((others || p == client.getLocalPlayer()) && participant(p))
        spot(p, name(p), client, ledger);
  }

  private void spot(Actor a, String player, Client client, MistakeLedger ledger) {
    if (a.getSpotAnims() == null) return;
    for (ActorSpotAnim g : a.getSpotAnims()) {
      int id = g.getId();
      if (id < 4373
          || id > 4387
          || !seen.add(
              "g:"
                  + (a instanceof NPC ? "npc" + ((NPC) a).getIndex() : player)
                  + ":"
                  + id
                  + ":"
                  + g.getStartCycle())) continue;
      log(
          ledger,
          client.getTickCount(),
          client.getGameCycle(),
          player,
          "actor-gfx",
          "npc="
              + (a instanceof NPC ? ((NPC) a).getId() : 0)
              + " gfx="
              + id
              + " start="
              + g.getStartCycle(),
          0);
    }
  }

  void floor(int id, int x, int y, int plane, int start, Client client, MistakeLedger ledger) {
    if (id < 4373
        || id > 4387
        || !seen.add("f:" + id + ":" + x + ":" + y + ":" + plane + ":" + start)) return;
    if (id == 4379)
      rules.add(new XarthRules.Signal(XarthRules.Source.AOE, id, start, start, x, y, plane, null));
    log(
        ledger,
        client.getTickCount(),
        client.getGameCycle(),
        null,
        "floor",
        "gfx=" + id + " start=" + start + " at=" + x + "," + y + "," + plane,
        0);
  }

  void projectile(Projectile p, Client client, MistakeLedger ledger) {
    int id = p.getId();
    if (view == null || id != 4034 && id != 4374 && id != 4378) return;
    Actor source = p.getSourceActor();
    if (source != null
        && (!(source instanceof NPC)
            || ((NPC) source).getId() != 16684
            || source.getWorldView() != view)) return;
    if (p.getEndCycle() < client.getGameCycle() - 10) return;
    Shot s = shots.get(p);
    if (s == null) {
      s = new Shot();
      s.id = id;
      s.start = p.getStartCycle();
      shots.put(p, s);
      log(
          ledger,
          client.getTickCount(),
          client.getGameCycle(),
          null,
          "projectile",
          "id=" + id + " start=" + p.getStartCycle() + " end=" + p.getEndCycle(),
          0);
    }
    s.end = p.getEndCycle();
    if (p.getTargetPoint() != null) s.point = p.getTargetPoint();
    if (p.getTargetActor() instanceof Player) s.target = name((Player) p.getTargetActor());
  }

  void object(TileObject o, boolean spawn, Client client, MistakeLedger ledger) {
    if (view == null || o.getWorldView() != view || o.getId() < 62710 || o.getId() > 62713) return;
    WorldPoint p = o.getWorldLocation();
    if (o.getId() == 62713)
      rules.fire(p.getX(), p.getY(), p.getPlane(), client.getGameCycle(), spawn);
    log(
        ledger,
        client.getTickCount(),
        client.getGameCycle(),
        null,
        spawn ? "object-spawn" : "object-despawn",
        "id=" + o.getId() + " at=" + p,
        0);
  }

  void hit(Player p, Hitsplat splat, Client client) {
    XarthRules.Hit h = new XarthRules.Hit();
    WorldPoint w = p.getWorldLocation();
    h.player = name(p);
    h.x = w.getX();
    h.y = w.getY();
    h.plane = w.getPlane();
    h.tick = client.getTickCount();
    h.cycle = client.getGameCycle();
    h.amount = splat.getAmount();
    h.type = splat.getHitsplatType();
    if (p == client.getLocalPlayer()) {
      h.rangedPrayer = client.isPrayerActive(Prayer.PROTECT_FROM_MISSILES);
      h.magicPrayer = client.isPrayerActive(Prayer.PROTECT_FROM_MAGIC);
      h.prayerEvidence = "local state at hitsplat";
    } else {
      h.rangedPrayer = p.getOverheadIcon() == HeadIcon.RANGED;
      h.magicPrayer = p.getOverheadIcon() == HeadIcon.MAGIC;
      h.prayerEvidence = "visible overhead at hitsplat: " + p.getOverheadIcon();
    }
    pending.add(h);
  }

  void drain(Client client, MistakeLedger ledger, boolean all) {
    int cycle = client.getGameCycle();
    List<XarthRules.Signal> projectiles = new ArrayList<>();
    for (Shot s : shots.values())
      if (s.point != null)
        projectiles.add(
            new XarthRules.Signal(
                s.id == 4034
                    ? XarthRules.Source.BASIC_MAGIC
                    : s.id == 4374 ? XarthRules.Source.RANGED : XarthRules.Source.AOE,
                s.id,
                s.start,
                s.end,
                s.point.getX(),
                s.point.getY(),
                s.point.getPlane(),
                s.target));
    Iterator<XarthRules.Hit> i = pending.iterator();
    while (i.hasNext()) {
      XarthRules.Hit h = i.next();
      if (!all && cycle - h.cycle < 30) continue;
      i.remove();
      if (h.amount <= 0) continue;
      XarthRules.Result result = rules.match(h, projectiles);
      String extra = "";
      if (h.type == HitsplatID.BURN) {
        int last = burnCycles.getOrDefault(h.player, -1000000),
            episode = burnEpisodes.getOrDefault(h.player, 0);
        if (h.cycle - last > 150) episode++;
        burnCycles.put(h.player, h.cycle);
        burnEpisodes.put(h.player, episode);
        extra = " burnEpisode=" + episode + " episodeGrouping=heuristic; no automatic burn mistake";
      }
      String detail =
          "status="
              + result.status
              + " at="
              + h.x
              + ","
              + h.y
              + ","
              + h.plane
              + " hitsplat="
              + h.type
              + " magicPrayer="
              + h.magicPrayer
              + " rangedPrayer="
              + h.rangedPrayer
              + " prayerEvidence="
              + h.prayerEvidence
              + " sources="
              + result.sources
              + " evidence="
              + result.evidence
              + extra;
      log(ledger, h.tick, h.cycle, h.player, "damage", detail, h.amount);
      if (result.mistake != null)
        ledger.observation(
            h.player, "Possible " + result.mistake.label, h.amount, h.tick, h.cycle, detail);
    }
    shots.values().removeIf(s -> cycle - s.end > 120);
    rules.prune(cycle);
  }

  void death(Player p, Client client, MistakeLedger ledger) {
    dead.add(name(p));
    deathCycles.put(name(p), client.getGameCycle());
    log(
        ledger,
        client.getTickCount(),
        client.getGameCycle(),
        name(p),
        "death",
        "Observed death; source not inferred",
        0);
  }

  private void log(
      MistakeLedger ledger,
      int tick,
      int cycle,
      String player,
      String type,
      String detail,
      int amount) {
    ledger.research(tick, cycle, player, type, "Xarth " + detail, amount);
  }
}
