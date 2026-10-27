package tv.moonga.tfaqol;

import static tv.moonga.tfaqol.PlayerNames.name;

import java.util.*;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;

/**
 * Only observed, uniquely matched damage is counted. The projectile alone is not damage evidence.
 */
final class BorkObserver {
  private static final class Floor {
    int x, y, cycle;

    Floor(int x, int y, int cycle) {
      this.x = x;
      this.y = y;
      this.cycle = cycle;
    }
  }

  private static final class Stomp {
    String target;
    int cycle;

    Stomp(String target, int cycle) {
      this.target = target;
      this.cycle = cycle;
    }
  }

  private static final class Ground {
    int start, end = Integer.MAX_VALUE;

    Ground(int start) {
      this.start = start;
    }
  }

  private static final class Hit {
    String who;
    int x, y, tick, cycle, amount;
    boolean direct, burn, ranged, magic;
  }

  private static final class Shot {
    int id, end, impactTick, impactCycle;
    String target;
    WorldPoint point;
    boolean valid = true, impact, borkInside, aliveAtImpact, done;
    Set<String> expected = new LinkedHashSet<>(), eligible = new HashSet<>();
  }

  private final List<Floor> floors = new ArrayList<>();
  private final List<Stomp> stomps = new ArrayList<>();
  private final List<Integer> stuns = new ArrayList<>();
  private final Map<Long, List<Ground>> grounds = new HashMap<>();
  private final Map<Projectile, Shot> shots = new IdentityHashMap<>();
  private final List<Hit> pending = new ArrayList<>();
  private final Set<String> engaged = new LinkedHashSet<>();
  private final Set<String> eliminated = new HashSet<>();
  private NPC bork, smorks;
  private boolean roomAttempted;
  private WorldView view;
  private Set<Long> room = Collections.emptySet();

  private static long key(int x, int y) {
    return ((long) x << 32) ^ (y & 0xffffffffL);
  }

  void clear() {
    floors.clear();
    stomps.clear();
    stuns.clear();
    grounds.clear();
    shots.clear();
    pending.clear();
    engaged.clear();
    eliminated.clear();
    bork = null;
    smorks = null;
    view = null;
    roomAttempted = false;
    room = Collections.emptySet();
  }

  void restoreParticipants(Collection<String> names) {
    engaged.addAll(names);
  }

  void excludePlayers(Collection<String> names) {
    eliminated.addAll(names);
    engaged.removeAll(names);
  }

  void actors(WorldView w) {
    view = w;
    bork = null;
    smorks = null;
    for (NPC n : w.npcs()) {
      if (n.getId() == 16640) bork = n;
      else if (n.getId() == 16641) smorks = n;
    }
    if (!roomAttempted && bork != null && w.getCollisionMaps() != null) {
      roomAttempted = true;
      room = findRoom(w, bork);
    }
  }

  boolean participant(Player p) {
    if (view == null
        || p.isDead()
        || eliminated.contains(name(p))
        || p.getWorldView() != view
        || p.getWorldLocation().getPlane() != view.getPlane()) return false;
    Actor target = p.getInteracting();
    if (target instanceof NPC
        && (((NPC) target).getId() == 16640 || ((NPC) target).getId() == 16641))
      engaged.add(name(p));
    if (bork != null && bork.getInteracting() == p
        || smorks != null && smorks.getInteracting() == p) engaged.add(name(p));
    WorldPoint at = p.getWorldLocation();
    if (room.contains(key(at.getX() - view.getBaseX(), at.getY() - view.getBaseY())))
      engaged.add(name(p));
    return engaged.contains(name(p))
        && (room.isEmpty()
            || room.contains(key(at.getX() - view.getBaseX(), at.getY() - view.getBaseY())));
  }

  void animation(NPC npc, int cycle) {
    if (npc.getId() != 16640) return;
    if (npc.getAnimation() == 14545) stun(cycle);
    if (npc.getAnimation() == 14542) {
      Actor target = npc.getInteracting();
      stomps.add(new Stomp(target instanceof Player ? name((Player) target) : null, cycle));
    }
  }

  void stun(int cycle) {
    if (!stuns.contains(cycle)) stuns.add(cycle);
  }

  void floor(int x, int y, int start) {
    floors.add(new Floor(x, y, start));
  }

  void ground(int x, int y, int cycle, boolean spawn) {
    List<Ground> list = grounds.computeIfAbsent(key(x, y), k -> new ArrayList<>());
    if (spawn) {
      if (list.isEmpty() || list.get(list.size() - 1).end != Integer.MAX_VALUE)
        list.add(new Ground(cycle));
    } else if (!list.isEmpty()) list.get(list.size() - 1).end = cycle;
  }

  void projectile(Projectile projectile, Client client) {
    int id = projectile.getId();
    if (id != 4056 && id != 4057 && id != 4045 && id != 4440 || view == null) return;
    Shot shot = shots.get(projectile);
    if (shot == null) {
      Actor source = projectile.getSourceActor();
      WorldPoint sourcePoint = projectile.getSourcePoint();
      if (source instanceof NPC) {
        if (((NPC) source).getId() != 16641 || source.getWorldView() != view) return;
      } else if (smorks == null
          || sourcePoint == null
          || sourcePoint.getPlane() != view.getPlane()
          || sourcePoint.distanceTo(smorks.getWorldLocation()) > 4) return;
      if (projectile.getEndCycle() < client.getGameCycle()) return;
      shot = new Shot();
      shot.id = id;
      shot.end = projectile.getEndCycle();
      if (id == 4440)
        for (Player p : view.players()) if (participant(p)) shot.expected.add(name(p));
      shots.put(projectile, shot);
    }
    Actor target = projectile.getTargetActor();
    if (target instanceof Player) shot.target = name((Player) target);
    if (projectile.getTargetPoint() != null) shot.point = projectile.getTargetPoint();
  }

  void hit(Player player, Hitsplat hitsplat, boolean direct, Client client) {
    Hit h = new Hit();
    h.who = name(player);
    WorldPoint at = player.getWorldLocation();
    h.x = at.getX();
    h.y = at.getY();
    h.tick = client.getTickCount();
    h.cycle = client.getGameCycle();
    h.amount = hitsplat.getAmount();
    h.direct = direct;
    h.burn = hitsplat.getHitsplatType() == HitsplatID.BURN;
    h.ranged =
        player == client.getLocalPlayer()
            ? client.isPrayerActive(Prayer.PROTECT_FROM_MISSILES)
            : player.getOverheadIcon() == HeadIcon.RANGED;
    h.magic =
        player == client.getLocalPlayer()
            ? client.isPrayerActive(Prayer.PROTECT_FROM_MAGIC)
            : player.getOverheadIcon() == HeadIcon.MAGIC;
    pending.add(h);
  }

  void frame(Client client) {
    if (view == null) return;
    int cycle = client.getGameCycle();
    if (bork != null && bork.getSpotAnims() != null)
      for (ActorSpotAnim spot : bork.getSpotAnims())
        if (spot.getId() == 1542 && spot.getStartCycle() <= cycle) stun(spot.getStartCycle());
    for (Shot shot : shots.values())
      if (shot.id == 4440 && !shot.done && !shot.impact) {
        if (bork == null || bork.isDead()) shot.valid = false;
        if (cycle >= shot.end) {
          shot.impact = true;
          shot.impactCycle = cycle;
          shot.impactTick = client.getTickCount();
          shot.aliveAtImpact = bork != null && !bork.isDead();
          if (shot.aliveAtImpact && shot.point != null)
            shot.borkInside = bork.getWorldArea().contains(shot.point);
          for (Player p : view.players()) if (participant(p)) shot.eligible.add(name(p));
        }
      }
  }

  void drain(Client client, MistakeLedger ledger, boolean others) {
    drain(client, ledger, others, false);
  }

  void drain(Client client, MistakeLedger ledger, boolean others, boolean all) {
    drain(client, ledger, others, all, true);
  }

  void drain(Client client, MistakeLedger ledger, boolean others, boolean all, boolean trackTank) {
    int cycle = client.getGameCycle();
    String self = client.getLocalPlayer() == null ? null : name(client.getLocalPlayer());
    Iterator<Hit> iterator = pending.iterator();
    while (iterator.hasNext()) {
      Hit h = iterator.next();
      if (!all && cycle - h.cycle < 30) continue;
      iterator.remove();
      if (h.amount <= 0 || !others && !h.who.equals(self)) continue;
      Set<String> projectileTypes = new HashSet<>();
      boolean projectileHit = false;
      if (h.direct)
        for (Shot shot : shots.values())
          if (Math.abs(shot.end - h.cycle) <= 10
              && (h.who.equals(shot.target)
                  || shot.target == null
                      && shot.point != null
                      && shot.point.getX() == h.x
                      && shot.point.getY() == h.y)) {
            projectileHit = true;
            if (shot.id == 4056 && !h.ranged) projectileTypes.add("Ranged hit off prayer");
            if (shot.id == 4057 && !h.magic) projectileTypes.add("Magic hit off prayer");
          }
      boolean onFloor = false;
      if (h.direct)
        for (Floor f : floors)
          if (f.x == h.x && f.y == h.y && Math.abs(f.cycle - h.cycle) <= 10) onFloor = true;
      boolean onGround = false;
      if (h.direct || h.burn)
        for (Ground ground : grounds.getOrDefault(key(h.x, h.y), Collections.emptyList()))
          if (ground.start <= h.cycle + 10 && ground.end >= h.cycle) onGround = true;
      // A coincident projectile and hazard cannot be uniquely attributed from one hitsplat.
      boolean ambiguous =
          projectileHit && (onFloor || onGround)
              || onFloor && onGround
              || projectileTypes.size() > 1;
      boolean tank = false;
      if (onFloor)
        for (Stomp s : stomps)
          if (h.who.equals(s.target)
              && (Math.abs(h.cycle - s.cycle) <= 10 || Math.abs(h.cycle - s.cycle - 60) <= 10))
            tank = true;
      if (!ambiguous && tank && !trackTank) continue;
      ledger.research(
          h.tick,
          h.cycle,
          h.who,
          "damage",
          "status="
              + (ambiguous
                  ? "AMBIGUOUS"
                  : projectileHit || onFloor || onGround ? "CORRELATED" : "UNRESOLVED")
              + " at="
              + h.x
              + ","
              + h.y
              + " rangedPrayer="
              + h.ranged
              + " magicPrayer="
              + h.magic
              + " direct="
              + h.direct
              + " burn="
              + h.burn
              + " projectile="
              + projectileHit
              + " floor4395="
              + onFloor
              + " activeFire62544="
              + onGround,
          h.amount);
      if (ambiguous) continue;
      String type = null, evidence = null;
      if (projectileHit) {
        if (projectileTypes.size() == 1) {
          type = projectileTypes.iterator().next();
          evidence =
              "Smorks projectile ends within 10 client cycles of this hit; protection prayer absent"
                  + " at impact.";
        }
      } else if (onFloor) {
        type = tank ? "Tank damage" : "Bork attack";
        evidence =
            "Positive damage on GFX 4395 tile within 10 client cycles; tank hits additionally match"
                + " Bork's 14542 target and first/second impact (2 ticks apart).";
      } else if (onGround) {
        type = "Flame ground damage";
        evidence =
            "Positive damage on active object 62544; no matching Smorks projectile or Bork floor"
                + " impact.";
      }
      if (type != null)
        ledger.observation(h.who, "Possible " + type, h.amount, h.tick, h.cycle, evidence);
    }
    for (Shot shot : shots.values())
      if (shot.id == 4440 && !shot.done && shot.impact && cycle - shot.end >= 30) {
        shot.done = true;
        boolean stunned = false;
        for (int start : stuns) if (Math.abs(start - shot.end) <= 15) stunned = true;
        if (shot.valid && shot.aliveAtImpact && shot.point != null && !shot.borkInside && !stunned)
          for (String who : shot.expected)
            if (shot.eligible.contains(who) && (others || who.equals(self)))
              ledger.observation(
                  who,
                  "Possible Bomb missed",
                  0,
                  shot.impactTick,
                  shot.impactCycle,
                  "Observed bomb 4440 landed outside Bork's footprint; no stun 1542/14545 at"
                      + " impact. Counted once for each observed participant still in the room.");
      }
    shots.values().removeIf(s -> cycle - s.end > 120);
    floors.removeIf(f -> cycle - f.cycle > 120);
    stomps.removeIf(s -> cycle - s.cycle > 180);
    stuns.removeIf(s -> cycle - s > 180);
    grounds
        .values()
        .forEach(list -> list.removeIf(g -> g.end != Integer.MAX_VALUE && cycle - g.end > 120));
    grounds.values().removeIf(List::isEmpty);
  }

  private static Set<Long> findRoom(WorldView w, NPC bork) {
    CollisionData[] maps = w.getCollisionMaps();
    if (maps == null
        || w.getPlane() < 0
        || w.getPlane() >= maps.length
        || maps[w.getPlane()] == null) return Collections.emptySet();
    int[][] flags = maps[w.getPlane()].getFlags();
    WorldPoint p = bork.getWorldLocation();
    int x = p.getX() - w.getBaseX() + 2, y = p.getY() - w.getBaseY() + 2;
    if (!open(flags, x, y)) return Collections.emptySet();
    Set<Long> seen = new HashSet<>();
    ArrayDeque<int[]> queue = new ArrayDeque<>();
    seen.add(key(x, y));
    queue.add(new int[] {x, y});
    int[][] directions = {{1, 0, 8, 128}, {-1, 0, 128, 8}, {0, 1, 2, 32}, {0, -1, 32, 2}};
    while (!queue.isEmpty()) {
      int[] at = queue.remove();
      for (int[] d : directions) {
        int nx = at[0] + d[0], ny = at[1] + d[1];
        if (open(flags, nx, ny)
            && (flags[at[0]][at[1]] & d[2]) == 0
            && (flags[nx][ny] & d[3]) == 0
            && seen.add(key(nx, ny))) {
          if (seen.size() > 2500) return Collections.emptySet();
          queue.add(new int[] {nx, ny});
        }
      }
    }
    return seen;
  }

  private static boolean open(int[][] flags, int x, int y) {
    return flags != null
        && x >= 0
        && x < flags.length
        && flags[x] != null
        && y >= 0
        && y < flags[x].length
        && (flags[x][y] & 0x1240100) == 0;
  }
}
