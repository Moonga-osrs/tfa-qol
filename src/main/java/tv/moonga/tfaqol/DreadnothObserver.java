package tv.moonga.tfaqol;

import static tv.moonga.tfaqol.PlayerNames.name;

import java.util.*;
import net.runelite.api.*;
import net.runelite.api.coords.*;

/**
 * NPC 16665 + Jelly 16666. Repeating chains are observed, never predicted from a fight-wide timer.
 */
final class DreadnothObserver {
  private final DreadnothRules rules = new DreadnothRules();
  private final Set<String> participants = new LinkedHashSet<>(),
      dead = new HashSet<>(),
      seen = new HashSet<>();
  private final Map<String, Integer> deaths = new HashMap<>();
  private final Map<NPC, Integer> animations = new IdentityHashMap<>();

  private static final class Shot {
    Projectile projectile;
    boolean enrage;
    String target;
    WorldPoint point;
  }

  private final Map<Projectile, Shot> shots = new IdentityHashMap<>();
  private final List<DreadnothRules.Hit> pending = new ArrayList<>();
  private WorldView view;
  private NPC boss;
  private boolean enrage, seeded;
  private int lastSnapshot = -1;

  void clear() {
    suspend();
    participants.clear();
    dead.clear();
    deaths.clear();
    pending.clear();
    enrage = false;
    lastSnapshot = -1;
  }

  void suspend() {
    rules.clear();
    animations.clear();
    shots.clear();
    seen.clear();
    view = null;
    boss = null;
    seeded = false;
  }

  void restore(MistakeLedger ledger) {
    if (ledger.current() != null) {
      participants.addAll(ledger.current().players);
      dead.addAll(ledger.current().deadPlayers);
    }
  }

  void actors(Client client, MistakeLedger ledger, boolean others) {
    view = client.getTopLevelWorldView();
    if (view == null) return;
    boss = null;
    for (NPC n : view.npcs())
      if (n.getId() == 16665) {
        boss = n;
        break;
      }
    for (Player p : view.players())
      if (participant(p) && (others || p == client.getLocalPlayer())) {
        participants.add(name(p));
        ledger.participant(name(p));
      }
    for (NPC n : view.npcs())
      if (n.getId() == 16665 || n.getId() == 16666) {
        int a = n.getAnimation();
        Integer previous = animations.get(n);
        if (n.getId() == 16665 && a == 14780) enrage = true;
        if (previous == null || previous != a) {
          if (a >= 0 && n.getAnimationFrame() == 0) animation(n, client, ledger);
          else animations.put(n, a);
        }
      }
    if (!seeded && view.getScene() != null) {
      Tile[][][] tiles = view.getScene().getTiles();
      int p = view.getPlane();
      if (tiles != null && p >= 0 && p < tiles.length) {
        seeded = true;
        Set<TileObject> unique = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Tile[] row : tiles[p])
          if (row != null)
            for (Tile tile : row)
              if (tile != null) {
                if (tile.getGameObjects() != null)
                  for (GameObject o : tile.getGameObjects())
                    if (o != null && o.getId() == 62609 && unique.add(o))
                      object(o, true, client, ledger);
                GroundObject o = tile.getGroundObject();
                if (o != null && o.getId() == 62609) object(o, true, client, ledger);
              }
      }
    }
    if (lastSnapshot != client.getTickCount()) {
      lastSnapshot = client.getTickCount();
      StringBuilder state =
          new StringBuilder("boss=")
              .append(boss == null ? "absent" : boss.getWorldLocation())
              .append(" animation=")
              .append(boss == null ? -1 : boss.getAnimation())
              .append(" enrage=")
              .append(enrage);
      for (Player p : view.players())
        if ((others || p == client.getLocalPlayer()) && participant(p))
          state
              .append("; player=")
              .append(name(p))
              .append(" at=")
              .append(p.getWorldLocation())
              .append(" overhead=")
              .append(p.getOverheadIcon());
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

  boolean cleanupActive() {
    if (view == null) return false;
    if (rules.hasActiveBlood()) return true;
    for (NPC n : view.npcs()) if (n.getId() == 16666 && !n.isDead()) return true;
    return false;
  }

  boolean participant(Player p) {
    if (view == null
        || p.getWorldView() != view
        || p.getWorldLocation().getPlane() != view.getPlane()
        || p.isDead()
        || dead.contains(name(p))) return false;
    if (participants.contains(name(p))) return true;
    Actor target = p.getInteracting();
    return target instanceof NPC
            && (((NPC) target).getId() == 16665 || ((NPC) target).getId() == 16666)
        || boss != null && p.getWorldLocation().distanceTo(boss.getWorldLocation()) <= 64;
  }

  boolean acceptsHit(Player p, int cycle) {
    return participant(p)
        || view != null
            && p.getWorldView() == view
            && p.getWorldLocation().getPlane() == view.getPlane()
            && participants.contains(name(p))
            && Math.abs(cycle - deaths.getOrDefault(name(p), -1000000)) <= 10;
  }

  void animation(NPC npc, Client client, MistakeLedger ledger) {
    int id = npc.getId(), a = npc.getAnimation(), cycle = client.getGameCycle();
    if (id != 16665 && id != 16666 || npc.getWorldView() != view) return;
    animations.put(npc, a);
    if (a < 0 || !seen.add("a:" + npc.getIndex() + ":" + a + ":" + cycle)) return;
    WorldPoint p = npc.getWorldLocation();
    DreadnothRules.Source source = null;
    int impact = cycle;
    String target = null;
    if (id == 16665) {
      if (a == 14777) {
        source = DreadnothRules.Source.STOMP;
        impact += 30;
      } else if (a == 14779) source = DreadnothRules.Source.MELEE;
      else if (a == 14780) {
        enrage = true;
        source = DreadnothRules.Source.LANDING;
        impact += 90;
      }
    } else if (a == 1586 && npc.getInteracting() instanceof Player) {
      source = DreadnothRules.Source.JELLY;
      target = name((Player) npc.getInteracting());
    }
    if (source != null)
      rules.add(
          new DreadnothRules.Signal(
              source,
              impact,
              p.getX(),
              p.getY(),
              p.getPlane(),
              id == 16665 ? 5 : 1,
              a,
              target,
              cycle,
              impact));
    log(
        ledger,
        client.getTickCount(),
        cycle,
        target,
        "animation",
        "npc="
            + id
            + " index="
            + npc.getIndex()
            + " animation="
            + a
            + " at="
            + p
            + " expectedImpact="
            + impact
            + " source="
            + source,
        0);
  }

  void projectile(Projectile p, Client client, MistakeLedger ledger) {
    int id = p.getId();
    if (view == null || id != 4211 && id != 4212) return;
    Actor source = p.getSourceActor();
    if (source != null
        && (!(source instanceof NPC)
            || ((NPC) source).getId() != 16665
            || source.getWorldView() != view)) return;
    if (p.getEndCycle() < client.getGameCycle() - 10) return;
    if (!shots.containsKey(p)) {
      Shot s = new Shot();
      s.projectile = p;
      s.enrage = enrage || id == 4212;
      shots.put(p, s);
      if (id == 4212) enrage = true;
      log(
          ledger,
          client.getTickCount(),
          client.getGameCycle(),
          null,
          "projectile",
          "id="
              + id
              + " start="
              + p.getStartCycle()
              + " end="
              + p.getEndCycle()
              + " target="
              + p.getTargetPoint()
              + " enrage="
              + s.enrage,
          0);
    }
    Shot captured = shots.get(p);
    if (captured != null) {
      if (p.getTargetActor() instanceof Player) captured.target = name((Player) p.getTargetActor());
      if (p.getTargetPoint() != null) captured.point = p.getTargetPoint();
    }
  }

  void floor(int id, int x, int y, int plane, int start, Client client, MistakeLedger ledger) {
    if (view == null || id != 4209 && id != 1576) return;
    if (!seen.add("f:" + id + ":" + x + ":" + y + ":" + plane + ":" + start)) return;
    log(
        ledger,
        client.getTickCount(),
        client.getGameCycle(),
        null,
        "floor",
        "gfx=" + id + " at=" + x + "," + y + "," + plane + " start=" + start,
        0);
    if (id == 4209) {
      enrage = true;
      rules.add(
          new DreadnothRules.Signal(
              DreadnothRules.Source.LANDING,
              start + 90,
              x,
              y,
              plane,
              5,
              4209,
              null,
              start,
              start + 90));
    }
  }

  void object(TileObject o, boolean spawn, Client client, MistakeLedger ledger) {
    if (view == null || o.getWorldView() != view || o.getId() != 62609) return;
    WorldPoint p = o.getWorldLocation();
    rules.ground(p.getX(), p.getY(), p.getPlane(), client.getGameCycle(), spawn);
    log(
        ledger,
        client.getTickCount(),
        client.getGameCycle(),
        null,
        spawn ? "blood-spawn" : "blood-despawn",
        "object=62609 at=" + p,
        0);
  }

  void hit(Player p, Hitsplat splat, Client client) {
    DreadnothRules.Hit h = new DreadnothRules.Hit();
    WorldPoint at = p.getWorldLocation();
    h.player = name(p);
    h.x = at.getX();
    h.y = at.getY();
    h.plane = at.getPlane();
    h.cycle = client.getGameCycle();
    h.tick = client.getTickCount();
    h.amount = splat.getAmount();
    h.type = splat.getHitsplatType();
    h.underBoss = boss != null && boss.getWorldArea() != null && boss.getWorldArea().contains(at);
    if (p == client.getLocalPlayer()) {
      h.meleePrayer = client.isPrayerActive(Prayer.PROTECT_FROM_MELEE);
      h.rangedPrayer = client.isPrayerActive(Prayer.PROTECT_FROM_MISSILES);
      h.prayerEvidence = "local prayer state at hitsplat";
    } else {
      h.meleePrayer = p.getOverheadIcon() == HeadIcon.MELEE;
      h.rangedPrayer = p.getOverheadIcon() == HeadIcon.RANGED;
      h.prayerEvidence = "visible overhead at hitsplat: " + p.getOverheadIcon();
    }
    pending.add(h);
  }

  void drain(Client client, MistakeLedger ledger, boolean all) {
    int cycle = client.getGameCycle();
    List<DreadnothRules.Signal> projectiles = new ArrayList<>();
    for (Shot shot : shots.values()) {
      Projectile p = shot.projectile;
      if (p.getTargetActor() instanceof Player) shot.target = name((Player) p.getTargetActor());
      if (p.getTargetPoint() != null) shot.point = p.getTargetPoint();
      WorldPoint at = shot.point;
      if (at == null) continue;
      DreadnothRules.Source source =
          p.getId() == 4212
              ? DreadnothRules.Source.BLOOD_SHOT
              : shot.enrage ? DreadnothRules.Source.ENRAGE_RANGE : DreadnothRules.Source.RANGE;
      projectiles.add(
          new DreadnothRules.Signal(
              source,
              p.getEndCycle(),
              at.getX(),
              at.getY(),
              at.getPlane(),
              1,
              p.getId(),
              shot.target,
              p.getStartCycle(),
              p.getEndCycle()));
    }
    Iterator<DreadnothRules.Hit> iterator = pending.iterator();
    while (iterator.hasNext()) {
      DreadnothRules.Hit h = iterator.next();
      if (!all && cycle - h.cycle < 30) continue;
      iterator.remove();
      if (h.amount <= 0) continue;
      DreadnothRules.Result r = rules.match(h, projectiles);
      String detail =
          "at="
              + h.x
              + ","
              + h.y
              + ","
              + h.plane
              + " hitsplat="
              + h.type
              + " status="
              + r.status()
              + " meleePrayer="
              + h.meleePrayer
              + " rangedPrayer="
              + h.rangedPrayer
              + " underBoss="
              + h.underBoss
              + " prayerEvidence="
              + h.prayerEvidence
              + " sources="
              + r.sources
              + " evidence="
              + r.evidence;
      log(ledger, h.tick, h.cycle, h.player, "damage", detail, h.amount);
      for (MistakeKind mistake : r.mistakes)
        ledger.observation(
            h.player, "Possible " + mistake.label, h.amount, h.tick, h.cycle, detail);
    }
    shots.values().removeIf(s -> cycle - s.projectile.getEndCycle() > 120);
    rules.prune(cycle);
  }

  void death(Player p, Client client, MistakeLedger ledger) {
    dead.add(name(p));
    deaths.put(name(p), client.getGameCycle());
    log(
        ledger,
        client.getTickCount(),
        client.getGameCycle(),
        name(p),
        "death",
        "Observed death; cause unassigned",
        0);
  }

  private void log(
      MistakeLedger ledger,
      int tick,
      int cycle,
      String player,
      String type,
      String detail,
      int damage) {
    ledger.research(tick, cycle, player, type, "Dreadnoth " + detail, damage);
  }
}
