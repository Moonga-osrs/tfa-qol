package tv.moonga.tfaqol;

import static tv.moonga.tfaqol.PlayerNames.name;

import java.util.*;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;

/** Ocellus atlas adapter. Preserves observations separately from provisional mistake counters. */
final class OcellusObserver {
  private final OcellusEvidence evidence = new OcellusEvidence();
  private final Set<String> seen = new HashSet<>(),
      participants = new LinkedHashSet<>(),
      dead = new HashSet<>();
  private final Map<String, Integer> deathCycles = new HashMap<>();
  private final Map<String, Hit> souls = new HashMap<>();
  private final Map<Projectile, Integer> shots = new IdentityHashMap<>();
  private final Map<Projectile, String> shotTargets = new IdentityHashMap<>();
  private final Map<Projectile, WorldPoint> shotPoints = new IdentityHashMap<>();
  private final Map<Projectile, Integer> shotLogTicks = new IdentityHashMap<>();
  private final Map<TileObject, Integer> animatedObjects = new IdentityHashMap<>();
  private final List<Hit> pending = new ArrayList<>();
  private WorldView view;
  private NPC boss;
  private String phase = "Unknown";
  private int part,
      lastAnimation = -1,
      lastAnimationCycle = -1,
      cycleStart = -1,
      lastCollapse = -10000,
      lastSnapshot = -1;
  private boolean seeded;

  private static final class Hit {
    String player;
    int x, y, plane, cycle, tick, amount, type, animation, animationCycle, cycleOffset;
    String phase, weaponEvidence;
    List<String> prayers;
    final List<Integer> activeGfx = new ArrayList<>();
    final List<OcellusEvidence.Signal> spots = new ArrayList<>();
  }

  static boolean main(int id) {
    return id == 16673 || id == 16674 || id == 16675 || id == 16677 || id == 16682;
  }

  static boolean related(int id) {
    return id >= 16668 && id <= 16690;
  }

  void clear() {
    evidence.clear();
    seen.clear();
    shots.clear();
    shotTargets.clear();
    shotPoints.clear();
    shotLogTicks.clear();
    animatedObjects.clear();
    pending.clear();
    participants.clear();
    dead.clear();
    deathCycles.clear();
    souls.clear();
    view = null;
    boss = null;
    phase = "Unknown";
    part = 0;
    lastAnimation = -1;
    lastAnimationCycle = -1;
    cycleStart = -1;
    lastCollapse = -10000;
    lastSnapshot = -1;
    seeded = false;
  }

  void suspend() {
    evidence.clear();
    seen.clear();
    shots.clear();
    shotTargets.clear();
    shotPoints.clear();
    shotLogTicks.clear();
    animatedObjects.clear();
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

  void actors(WorldView w, MistakeLedger ledger, int tick, int cycle) {
    view = w;
    boss = null;
    for (NPC n : w.npcs())
      if (main(n.getId()) && (boss == null || boss.isDead() && !n.isDead())) boss = n;
    if (boss == null) return;
    int id = boss.getId();
    String next;
    if (id == 16673 || id == 16674) next = "P1";
    else if (id == 16675) next = "P2";
    else {
      if (part == 0 && boss.getHealthScale() > 0 && boss.getHealthRatio() >= 0) {
        double ratio = (double) boss.getHealthRatio() / boss.getHealthScale();
        part = ratio > .76 ? 1 : ratio > .51 ? 2 : ratio > .26 ? 3 : 4;
      }
      next = "P3-" + (part == 0 ? "?" : part);
    }
    if (!next.equals(phase)) {
      phase = next;
      log(
          ledger,
          tick,
          cycle,
          "phase",
          null,
          "phase=" + phase + " npc=" + id + "; HP inference when joining mid-fight",
          0);
    }
    for (Player p : w.players()) if (participant(p)) participants.add(name(p));
    if (!seeded && w.getScene() != null) {
      seeded = true;
      Tile[][][] tiles = w.getScene().getTiles();
      int plane = w.getPlane();
      if (tiles != null && plane >= 0 && plane < tiles.length) {
        Set<TileObject> objects = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Tile[] row : tiles[plane])
          if (row != null)
            for (Tile tile : row)
              if (tile != null) {
                if (tile.getGameObjects() != null)
                  for (GameObject o : tile.getGameObjects())
                    if (o != null && objects.add(o)) object(o, true, tick, cycle, ledger);
                if (tile.getGroundObject() != null)
                  object(tile.getGroundObject(), true, tick, cycle, ledger);
              }
      }
    }
  }

  boolean participant(Player p) {
    if (view == null
        || p.getWorldView() != view
        || p.isDead()
        || dead.contains(name(p))
        || p.getWorldLocation().getPlane() != view.getPlane()) return false;
    if (participants.contains(name(p))) return true;
    Actor target = p.getInteracting();
    if (target instanceof NPC && related(((NPC) target).getId())) return true;
    // Observation roster, not bomb/shared-mistake eligibility. Spectators are retained but never
    // faulted for absence.
    return boss != null && p.getWorldLocation().distanceTo(boss.getWorldLocation()) <= 64;
  }

  boolean acceptsHit(Player p, int cycle) {
    return participant(p)
        || view != null
            && p.getWorldView() == view
            && p.getWorldLocation().getPlane() == view.getPlane()
            && participants.contains(name(p))
            && Math.abs(cycle - deathCycles.getOrDefault(name(p), -1000000)) <= 10;
  }

  void animation(NPC npc, int tick, int cycle, MistakeLedger ledger) {
    if (!related(npc.getId())) return;
    int a = npc.getAnimation();
    if (a < 0) return;
    if (!seen.add("animation:" + npc.getIndex() + ":" + a + ":" + cycle)) return;
    if (main(npc.getId())) {
      lastAnimation = a;
      lastAnimationCycle = cycle;
      if (a == 14881) part = 1;
      if (a == 14862 && cycle - lastCollapse > 30) {
        part = part == 0 ? 0 : Math.min(4, part + 1);
        lastCollapse = cycle;
      }
      if (a == 14863) {
        part = 4;
        cycleStart = cycle;
      }
      if (part > 0 && (npc.getId() == 16677 || npc.getId() == 16682)) phase = "P3-" + part;
    }
    log(
        ledger,
        tick,
        cycle,
        "animation",
        null,
        "npc="
            + npc.getId()
            + " index="
            + npc.getIndex()
            + " animation="
            + a
            + " p4OffsetCycles="
            + (cycleStart < 0 ? -1 : cycle - cycleStart)
            + " orientation="
            + npc.getOrientation()
            + " target="
            + (npc.getInteracting() instanceof Player
                ? name((Player) npc.getInteracting())
                : "none"),
        0);
  }

  void floor(
      int id, int x, int y, int plane, int start, int tick, int cycle, MistakeLedger ledger) {
    if (id < 4228
        || id > 4372
        || !seen.add("floor:" + id + ":" + x + ":" + y + ":" + plane + ":" + start)) return;
    if (id >= 4236 && id <= 4348)
      evidence.add(new OcellusEvidence.Signal(id, x, y, plane, start, start, null, "floor", false));
    log(
        ledger,
        tick,
        cycle,
        "floor",
        null,
        "gfx="
            + id
            + " at="
            + x
            + ","
            + y
            + ","
            + plane
            + " start="
            + start
            + " family="
            + OcellusEvidence.family(id, false),
        0);
  }

  void object(TileObject o, boolean spawn, int tick, int cycle, MistakeLedger ledger) {
    if (o.getWorldView() != view || o.getId() < 62684 || o.getId() > 63008) return;
    WorldPoint p = o.getWorldLocation();
    String k = "object:" + o.getId() + ":" + p;
    if (spawn) animatedObjects.putIfAbsent(o, Integer.MIN_VALUE);
    else animatedObjects.remove(o);
    if (spawn && !seen.add(k)) return;
    if (!spawn) seen.remove(k);
    if (spawn)
      evidence.add(
          new OcellusEvidence.Signal(
              o.getId(),
              p.getX(),
              p.getY(),
              p.getPlane(),
              cycle,
              Integer.MAX_VALUE,
              null,
              "object",
              true));
    else evidence.removeObject(o.getId(), p.getX(), p.getY(), p.getPlane(), cycle);
    log(
        ledger,
        tick,
        cycle,
        spawn ? "object-spawn" : "object-despawn",
        null,
        "id=" + o.getId() + " at=" + p + "; braziers/context never mistakes",
        0);
  }

  void projectile(Projectile p, Client client, MistakeLedger ledger) {
    int id = p.getId();
    if (id < 4228
        || id > 4338
        || id == 4303
        || OcellusEvidence.family(id, false) == null
        || view == null) return;
    Actor source = p.getSourceActor();
    if (source != null
        && (!(source instanceof NPC)
            || !related(((NPC) source).getId())
            || source.getWorldView() != view)) return;
    Actor target = p.getTargetActor();
    if (target instanceof Player) shotTargets.put(p, name((Player) target));
    if (p.getTargetPoint() != null) shotPoints.put(p, p.getTargetPoint());
    String who = shotTargets.get(p);
    WorldPoint at = shotPoints.get(p);
    if (at == null) return;
    int cycle = client.getGameCycle();
    Integer old = shots.put(p, cycle);
    if (old == null) {
      log(
          ledger,
          client.getTickCount(),
          cycle,
          "projectile",
          who,
          "id=" + id + " start=" + p.getStartCycle() + " end=" + p.getEndCycle() + " target=" + at,
          0);
    }
    int tick = client.getTickCount();
    if (!Objects.equals(shotLogTicks.get(p), tick)) {
      shotLogTicks.put(p, tick);
      log(
          ledger,
          tick,
          cycle,
          "projectile-position",
          who,
          "id="
              + id
              + " start="
              + p.getStartCycle()
              + " end="
              + p.getEndCycle()
              + " x="
              + p.getX()
              + " y="
              + p.getY()
              + " z="
              + p.getZ()
              + " baseX="
              + view.getBaseX()
              + " baseY="
              + view.getBaseY()
              + " target="
              + at,
          0);
    }
    // Replace updates at a single cycle; end/target may change, so matching reads the latest
    // projectile state at drain time.
    if (id == 4269 && (old == null || old != cycle) && p.getX() != 0 && p.getY() != 0) {
      double x = p.getX() / 128d + view.getBaseX(), y = p.getY() / 128d + view.getBaseY();
      for (int tx = (int) Math.floor(x - 1.3); tx <= x + .3; tx++)
        for (int ty = (int) Math.floor(y - 1.3); ty <= y + .3; ty++)
          if (Math.max(Math.abs(x - tx - .5), Math.abs(y - ty - .5)) <= .8)
            evidence.add(
                new OcellusEvidence.Signal(
                    id, tx, ty, at.getPlane(), cycle, cycle, null, "path", false));
    }
  }

  void frame(Client client, MistakeLedger ledger, boolean others) {
    if (view == null) return;
    int cycle = client.getGameCycle(), tick = client.getTickCount();
    for (NPC n : view.npcs()) if (related(n.getId())) spots(n, null, tick, cycle, ledger);
    for (Player p : view.players())
      if ((others || p == client.getLocalPlayer()) && participant(p))
        spots(p, name(p), tick, cycle, ledger);
  }

  void snapshot(Client client, MistakeLedger ledger, boolean others) {
    if (view == null || lastSnapshot == client.getTickCount()) return;
    lastSnapshot = client.getTickCount();
    StringBuilder data = new StringBuilder();
    for (Player p : view.players())
      if ((others || p == client.getLocalPlayer()) && participant(p)) {
        data.append("player=")
            .append(name(p))
            .append(" at=")
            .append(p.getWorldLocation())
            .append(" anim=")
            .append(p.getAnimation())
            .append(" orientation=")
            .append(p.getOrientation())
            .append(" dead=")
            .append(p.isDead())
            .append(';');
      }
    for (NPC n : view.npcs())
      if (related(n.getId()))
        data.append("npc=")
            .append(n.getId())
            .append(" index=")
            .append(n.getIndex())
            .append(" at=")
            .append(n.getWorldLocation())
            .append(" anim=")
            .append(n.getAnimation())
            .append(" hp=")
            .append(n.getHealthRatio())
            .append('/')
            .append(n.getHealthScale())
            .append(" orientation=")
            .append(n.getOrientation())
            .append(';');
    log(ledger, client.getTickCount(), client.getGameCycle(), "snapshot", null, data.toString(), 0);
    for (Map.Entry<TileObject, Integer> entry : animatedObjects.entrySet()) {
      TileObject o = entry.getKey();
      Renderable render =
          o instanceof GameObject
              ? ((GameObject) o).getRenderable()
              : o instanceof GroundObject ? ((GroundObject) o).getRenderable() : null;
      if (render instanceof DynamicObject) {
        DynamicObject dynamic = (DynamicObject) render;
        Animation anim = dynamic.getAnimation();
        int id = anim == null ? -1 : anim.getId();
        if (id != entry.getValue()) {
          entry.setValue(id);
          log(
              ledger,
              client.getTickCount(),
              client.getGameCycle(),
              "object-animation",
              null,
              "object="
                  + o.getId()
                  + " at="
                  + o.getWorldLocation()
                  + " animation="
                  + id
                  + " frame="
                  + dynamic.getAnimFrame(),
              0);
        }
      }
    }
  }

  private void spots(Actor actor, String player, int tick, int cycle, MistakeLedger ledger) {
    if (actor.getSpotAnims() == null) return;
    for (ActorSpotAnim s : actor.getSpotAnims()) {
      int id = s.getId();
      if (id < 4228
          || id > 4372
          || !seen.add(
              "spot:"
                  + (player == null ? "npc" + ((NPC) actor).getIndex() : player)
                  + ":"
                  + id
                  + ":"
                  + s.getStartCycle())) continue;
      log(
          ledger,
          tick,
          cycle,
          "actor-gfx",
          player,
          "id="
              + id
              + " start="
              + s.getStartCycle()
              + " npc="
              + (actor instanceof NPC ? ((NPC) actor).getId() : 0),
          0);
    }
  }

  void hit(Player p, Hitsplat splat, Client client, MistakeLedger ledger) {
    Hit h = new Hit();
    WorldPoint at = p.getWorldLocation();
    h.player = name(p);
    h.x = at.getX();
    h.y = at.getY();
    h.plane = at.getPlane();
    h.tick = client.getTickCount();
    h.cycle = client.getGameCycle();
    h.amount = splat.getAmount();
    h.type = splat.getHitsplatType();
    h.weaponEvidence = WeaponSelfDamage.evidence(client, p, splat);
    h.phase = phase;
    h.animation = lastAnimation;
    h.animationCycle = lastAnimationCycle;
    h.cycleOffset = cycleStart < 0 ? -1 : h.cycle - cycleStart;
    if (p == client.getLocalPlayer()) {
      h.prayers = new ArrayList<>();
      for (Prayer prayer : Prayer.values())
        if (client.isPrayerActive(prayer)) h.prayers.add(prayer.name());
    }
    if (p.getSpotAnims() != null)
      for (ActorSpotAnim s : p.getSpotAnims()) {
        h.activeGfx.add(s.getId());
        if (s.getId() == 4313
            || s.getId() == 4293
            || s.getId() == 4230
            || s.getId() == 4233
            || s.getId() == 4339)
          h.spots.add(
              new OcellusEvidence.Signal(
                  s.getId(),
                  h.x,
                  h.y,
                  h.plane,
                  s.getStartCycle(),
                  s.getStartCycle(),
                  h.player,
                  "spot",
                  false));
      }
    pending.add(h);
    spots(p, h.player, h.tick, h.cycle, ledger);
  }

  void drain(Client client, MistakeLedger ledger, boolean all) {
    drain(client, ledger, all, true);
  }

  void drain(Client client, MistakeLedger ledger, boolean all, boolean trackFloorCracks) {
    int cycle = client.getGameCycle();
    List<OcellusEvidence.Signal> latest = new ArrayList<>();
    for (Projectile p : shots.keySet()) {
      if (p.getTargetActor() instanceof Player)
        shotTargets.put(p, name((Player) p.getTargetActor()));
      if (p.getTargetPoint() != null) shotPoints.put(p, p.getTargetPoint());
      WorldPoint at = shotPoints.get(p);
      if (at != null) {
        latest.add(
            new OcellusEvidence.Signal(
                p.getId(),
                at.getX(),
                at.getY(),
                at.getPlane(),
                p.getStartCycle(),
                p.getEndCycle(),
                shotTargets.get(p),
                "projectile",
                false));
      }
    }
    evidence.projectiles(latest);
    Iterator<Hit> i = pending.iterator();
    while (i.hasNext()) {
      Hit h = i.next();
      if (!all && cycle - h.cycle < 30) continue;
      i.remove();
      if (h.amount <= 0) continue;
      OcellusEvidence.Result result = evidence.match(h.player, h.x, h.y, h.plane, h.cycle, h.spots);
      boolean heal = h.type == HitsplatID.HEAL;
      if (h.weaponEvidence != null) {
        result.families.add("weapon-self-damage");
        result.evidence.add(h.weaponEvidence);
      }
      String detail =
          "phase="
              + h.phase
              + " at="
              + h.x
              + ","
              + h.y
              + ","
              + h.plane
              + " hitsplat="
              + h.type
              + " animation="
              + h.animation
              + " animationCycle="
              + h.animationCycle
              + " p4OffsetCycles="
              + h.cycleOffset
              + " activeGfx="
              + h.activeGfx
              + " localPrayers="
              + h.prayers
              + " status="
              + (heal
                  ? "HEAL"
                  : h.type == HitsplatID.BURN
                      ? "BURN_STATUS"
                      : h.type == HitsplatID.BLEED
                          ? "BLEED_STATUS"
                          : h.weaponEvidence != null
                              ? "WEAPON_SELF_DAMAGE_POSSIBLE"
                              : result.status())
              + " families="
              + result.families
              + " evidence="
              + result.evidence;
      if (!trackFloorCracks
          && !heal
          && h.type != HitsplatID.BURN
          && h.type != HitsplatID.BLEED
          && result.kind() == MistakeKind.OC_CRACKS) continue;
      log(ledger, h.tick, h.cycle, heal ? "heal" : "damage", h.player, detail, h.amount);
      boolean withdrawal =
          !heal
              && h.spots.stream()
                  .anyMatch(s -> s.id == 4313 && Math.abs(h.cycle - s.start - 30) <= 10);
      if (withdrawal) {
        Hit old = souls.put(h.player, h);
        if (old != null)
          log(
              ledger,
              h.tick,
              h.cycle,
              "soul-unpaired",
              h.player,
              "Previous withdrawal tick="
                  + old.tick
                  + " amount="
                  + old.amount
                  + " has no observed return before next withdrawal; not a mistake",
              0);
        log(
            ledger,
            h.tick,
            h.cycle,
            "soul-withdrawal",
            h.player,
            "gfx=4313 start-to-hit=30 +/-10 cycles; not a mistake",
            h.amount);
      }
      Hit soul = souls.get(h.player);
      if (heal
          && h.activeGfx.contains(4314)
          && soul != null
          && h.cycle >= soul.cycle
          && h.cycle - soul.cycle <= 900) {
        log(
            ledger,
            h.tick,
            h.cycle,
            "soul-return",
            h.player,
            "withdrawalTick="
                + soul.tick
                + " withdrawn="
                + soul.amount
                + " returned="
                + h.amount
                + " delayCycles="
                + (h.cycle - soul.cycle)
                + " gfx=4314",
            h.amount);
        souls.remove(h.player);
      }
      if (!heal && h.type != HitsplatID.BURN && h.type != HitsplatID.BLEED) {
        MistakeKind kind = result.kind();
        if (kind != null)
          ledger.observation(
              h.player,
              "Possible " + kind.label,
              h.amount,
              h.tick,
              h.cycle,
              detail + "; correlation only, source and avoidability unconfirmed");
      }
    }
    shots.entrySet().removeIf(e -> cycle - e.getKey().getEndCycle() > 120);
    shotLogTicks.keySet().retainAll(shots.keySet());
    shotTargets.keySet().retainAll(shots.keySet());
    shotPoints.keySet().retainAll(shots.keySet());
    evidence.prune(cycle);
  }

  void death(Player p, int tick, int cycle, MistakeLedger ledger) {
    dead.add(name(p));
    deathCycles.put(name(p), cycle);
    log(ledger, tick, cycle, "death", name(p), "Observed death; no inferred cause", 0);
  }

  private void log(
      MistakeLedger ledger,
      int tick,
      int cycle,
      String type,
      String player,
      String detail,
      int amount) {
    ledger.research(tick, cycle, player, type, "phase=" + phase + " " + detail, amount);
  }
}
