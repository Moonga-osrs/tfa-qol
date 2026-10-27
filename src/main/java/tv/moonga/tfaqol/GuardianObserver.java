package tv.moonga.tfaqol;

import static tv.moonga.tfaqol.PlayerNames.name;

import java.util.*;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;

/**
 * Conservative, delayed evidence correlation. Ambiguous sources are never assigned a unique
 * mistake.
 */
final class GuardianObserver {
  static final class Attack {
    final int animation, tick, x, y, size;
    final Map<String, Boolean> prayers = new HashMap<>();
    final Set<String> expected = new HashSet<>(), seen = new HashSet<>(), invalid = new HashSet<>();
    boolean completed;

    Attack(int animation, int tick, int x, int y, int size) {
      this.animation = animation;
      this.tick = tick;
      this.x = x;
      this.y = y;
      this.size = size;
    }
  }

  static final class Hit {
    final String player;
    final int tick, cycle, x, y, amount, type;
    final boolean magic;

    Hit(String player, int tick, int cycle, int x, int y, int amount, int type, boolean magic) {
      this.player = player;
      this.tick = tick;
      this.cycle = cycle;
      this.x = x;
      this.y = y;
      this.amount = amount;
      this.type = type;
      this.magic = magic;
    }
  }

  private final List<Attack> attacks = new ArrayList<>();
  private final List<Hit> hits = new ArrayList<>();
  private final Map<Long, Integer> mushrooms = new HashMap<>();
  private final Set<Long> objects = new HashSet<>();
  private final Set<String> engaged = new HashSet<>(), eliminated = new HashSet<>();
  private final Map<Long, Integer> seenGraphics = new HashMap<>();

  void clear() {
    attacks.clear();
    hits.clear();
    mushrooms.clear();
    objects.clear();
    engaged.clear();
    eliminated.clear();
    seenGraphics.clear();
  }

  void restoreParticipants(Collection<String> names) {
    for (String n : names) if (!eliminated.contains(n)) engaged.add(n);
  }

  void excludePlayers(Collection<String> names) {
    eliminated.addAll(names);
    engaged.removeAll(names);
  }

  boolean participant(Player p, NPC boss) {
    if (p.isDead()
        || eliminated.contains(name(p))
        || p.getWorldView() != boss.getWorldView()
        || p.getWorldLocation().getPlane() != boss.getWorldLocation().getPlane()) return false;
    Actor target = p.getInteracting();
    if (target instanceof NPC) {
      int id = ((NPC) target).getId();
      if (id >= 16658 && id <= 16660 || id == 16664) engaged.add(name(p));
    }
    if (boss.getInteracting() == p) engaged.add(name(p));
    return engaged.contains(name(p));
  }

  private static boolean prayer(Client client, Player p, boolean magic) {
    return p == client.getLocalPlayer()
        ? client.isPrayerActive(magic ? Prayer.PROTECT_FROM_MAGIC : Prayer.PROTECT_FROM_MISSILES)
        : p.getOverheadIcon() == (magic ? HeadIcon.MAGIC : HeadIcon.RANGED);
  }

  void animation(Client client, NPC n, NPC boss) {
    // A surviving vine can still animate after the boss has left the loaded view.
    if (boss == null) return;
    int a = n.getAnimation();
    if (!(a == 14752 || a == 14768)) return;
    if (n != boss && n.getId() != 16664) return;
    start(client, n, boss, a);
  }

  void graphic(Client client, NPC boss, int id, int startCycle) {
    int a = id == 4204 ? 14754 : id == 4199 ? 14751 : id == 4202 ? 14747 : -1;
    if (a < 0 || startCycle > client.getGameCycle() || client.getGameCycle() - startCycle > 30)
      return;
    long key = ((long) id << 32) ^ (startCycle & 0xffffffffL);
    if (seenGraphics.putIfAbsent(key, client.getTickCount()) != null) return;
    start(client, boss, boss, a);
  }

  private void start(Client client, NPC n, NPC boss, int a) {
    int tick = client.getTickCount();
    for (Attack old : attacks)
      if (old.animation == a
          && old.tick == tick
          && old.x == n.getWorldLocation().getX()
          && old.y == n.getWorldLocation().getY()) return;
    WorldPoint p = n.getWorldLocation();
    Attack attack = new Attack(a, tick, p.getX(), p.getY(), n.getId() == 16664 ? 1 : 5);
    for (Player player : boss.getWorldView().players())
      if (participant(player, boss)) {
        attack.expected.add(name(player));
        attack.prayers.put(name(player), prayer(client, player, a == 14754));
      }
    attacks.add(attack);
  }

  void mushroom(int id, int x, int y, int tick) {
    long key = ((long) x << 32) ^ (y & 0xffffffffL);
    if (id == 62580 && objects.add(key)) mushrooms.put(key, tick);
  }

  void mushroomRemoved(int x, int y) {
    objects.remove(((long) x << 32) ^ (y & 0xffffffffL));
  }

  void hit(Client client, Player p, Hitsplat h) {
    WorldPoint at = p.getWorldLocation();
    hits.add(
        new Hit(
            name(p),
            client.getTickCount(),
            client.getGameCycle(),
            at.getX(),
            at.getY(),
            h.getAmount(),
            h.getHitsplatType(),
            prayer(client, p, true)));
  }

  void death(Player p) {
    eliminated.add(name(p));
    for (Attack a : attacks) a.invalid.add(name(p));
    engaged.remove(name(p));
  }

  void tick(Client client, NPC boss, MistakeLedger ledger, boolean others) {
    Map<String, Player> visible = new HashMap<>();
    for (Player p : boss.getWorldView().players())
      if (participant(p, boss)) visible.put(name(p), p);
    for (Attack a : attacks)
      if (a.animation == 14754 && !a.completed) {
        for (String who : a.expected) if (!visible.containsKey(who)) a.invalid.add(who);
      }
    drain(client, ledger, others, false);
    for (Attack a : attacks)
      if (a.animation == 14754 && !a.completed && client.getTickCount() - a.tick >= 13) {
        a.completed = true;
        if (!a.seen.isEmpty())
          for (String who : a.expected)
            if (!a.invalid.contains(who)
                && !a.seen.contains(who)
                && (others
                    || client.getLocalPlayer() != null
                        && who.equals(name(client.getLocalPlayer()))))
              ledger.observation(
                  who,
                  "Possible Firebreath missed",
                  0,
                  a.tick + 10,
                  client.getGameCycle(),
                  "Engaged participant continuously visible; no damage or block hitsplat on"
                      + " observed firebreath impact tick, while another participant was hit."
                      + " Timing requires live validation.");
      }
    attacks.removeIf(a -> client.getTickCount() - a.tick > 18);
    mushrooms.values().removeIf(t -> client.getTickCount() - t > 5);
    seenGraphics.values().removeIf(t -> client.getTickCount() - t > 30);
  }

  void drain(Client client, MistakeLedger ledger, boolean others, boolean all) {
    Iterator<Hit> it = hits.iterator();
    while (it.hasNext()) {
      Hit h = it.next();
      if (!all && client.getTickCount() - h.tick < 2) continue;
      it.remove();
      Set<String> matches = new HashSet<>();
      boolean poison = h.type == HitsplatID.POISON;
      boolean direct =
          !poison
              && h.type != HitsplatID.VENOM
              && h.type != HitsplatID.BLEED
              && h.type != HitsplatID.BURN;
      for (Attack a : attacks) {
        int age = h.tick - a.tick;
        if (direct) {
          String physical =
              GuardianRules.physical(
                  a.animation, age, GuardianRules.distance(h.x, h.y, a.x, a.y, a.size));
          if (physical != null) matches.add(physical);
          if (a.animation == 14751 && age == 4 && Boolean.FALSE.equals(a.prayers.get(h.player)))
            matches.add("Taking hit off prayer");
          if (a.animation == 14754 && GuardianRules.fireImpact(age)) {
            a.seen.add(h.player);
            if (!h.magic && Boolean.FALSE.equals(a.prayers.get(h.player)))
              matches.add("Firebreath off prayer");
          }
        }
      }
      if (poison)
        for (Map.Entry<Long, Integer> m : mushrooms.entrySet()) {
          int x = (int) (m.getKey() >> 32), y = (int) (long) m.getKey();
          String type =
              GuardianRules.mushroom(
                  h.tick - m.getValue(), Math.max(Math.abs(h.x - x), Math.abs(h.y - y)));
          if (type != null) matches.add(type);
        }
      if (h.amount > 0
          && (others
              || client.getLocalPlayer() != null && h.player.equals(name(client.getLocalPlayer()))))
        ledger.research(
            h.tick,
            h.cycle,
            h.player,
            "damage",
            "status="
                + (matches.size() > 1
                    ? "AMBIGUOUS"
                    : matches.isEmpty() ? "UNRESOLVED" : "POSSIBLE_MISTAKE")
                + " hitsplat="
                + h.type
                + " at="
                + h.x
                + ","
                + h.y
                + " magicPrayerAtImpact="
                + h.magic
                + " candidates="
                + matches,
            h.amount);
      if (matches.size() == 1
          && h.amount > 0
          && (others
              || client.getLocalPlayer() != null && h.player.equals(name(client.getLocalPlayer()))))
        ledger.observation(
            h.player,
            "Possible " + matches.iterator().next(),
            h.amount,
            h.tick,
            h.cycle,
            "Guardian animation/object timing, position and observed prayer; damage amount is not"
                + " an identification rule");
    }
  }
}
