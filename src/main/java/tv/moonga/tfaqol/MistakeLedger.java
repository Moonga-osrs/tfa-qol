package tv.moonga.tfaqol;

import java.time.Instant;
import java.util.*;

/** Local evidence log. A matching floor graphic is deliberately labelled POSSIBLE, never proven. */
public final class MistakeLedger {
  public static final class Event {
    public final int tick, cycle;
    public final String player, type, evidence;
    public int damage;

    Event(int tick, int cycle, String player, String type, String evidence, int damage) {
      this.tick = tick;
      this.cycle = cycle;
      this.player = player;
      this.type = type;
      this.evidence = evidence;
      this.damage = damage;
    }
  }

  public static final class Encounter {
    public final int number, startTick, startCycle;
    public final String room, started;
    public final boolean joinedInProgress;
    public final TfaBoss boss;
    public String outcome = "Active";
    public int endTick = -1;
    public final List<Event> events = new ArrayList<>();
    public int omittedEvents;
    public final List<Event> research = new ArrayList<>();
    public int omittedResearch;
    public final Set<String> players = new LinkedHashSet<>();
    public final Set<String> deadPlayers = new HashSet<>();

    Encounter(int number, String room, int tick, int cycle, boolean joined, TfaBoss boss) {
      this.number = number;
      this.room = room;
      startTick = tick;
      startCycle = cycle;
      joinedInProgress = joined;
      this.boss = boss;
      started = Instant.now().toString();
    }
  }

  private static final class Floor {
    final int x, y, plane, view, start;
    final String type;

    Floor(int x, int y, int plane, int view, int start, String type) {
      this.x = x;
      this.y = y;
      this.plane = plane;
      this.view = view;
      this.start = start;
      this.type = type;
    }
  }

  private static final class Hit {
    final String player;
    final int tick, cycle, x, y, plane, view, damage;
    final boolean direct;

    Hit(
        String player,
        int damage,
        boolean direct,
        int x,
        int y,
        int plane,
        int view,
        int tick,
        int cycle) {
      this.player = player;
      this.damage = damage;
      this.direct = direct;
      this.x = x;
      this.y = y;
      this.plane = plane;
      this.view = view;
      this.tick = tick;
      this.cycle = cycle;
    }
  }

  private final List<Encounter> encounters = new ArrayList<>();
  private Encounter current;
  private final List<Floor> floors = new ArrayList<>();
  private final List<Hit> pending = new ArrayList<>();
  private int next = 1;

  public List<Encounter> encounters() {
    return Collections.unmodifiableList(encounters);
  }

  public Encounter current() {
    return current;
  }

  public boolean active() {
    return current != null && current.endTick < 0 && current.boss != null;
  }

  public void begin(int tick, int cycle, boolean joined) {
    begin(TfaBoss.EZEKIEL, tick, cycle, joined);
  }

  public void begin(TfaBoss boss, int tick, int cycle, boolean joined) {
    if (boss == null || !BossDetectors.forBoss(boss).available()) return;
    if (active() && current.boss == boss) return;
    if (current != null && current.endTick < 0) finish(tick, cycle, "Observation ended");
    current = new Encounter(next++, boss.label, tick, cycle, joined, boss);
    encounters.add(current);
    floors.clear();
    pending.clear();
  }

  public void floor(int id, int x, int y, int plane, int view, int start) {
    if (!active()) return;
    String type = BossDetectors.forBoss(current.boss).floorMechanic(id);
    if (type != null) floors.add(new Floor(x, y, plane, view, start, type));
  }

  public void hit(
      String player,
      int damage,
      boolean direct,
      int x,
      int y,
      int plane,
      int view,
      int tick,
      int cycle) {
    if (active() && damage > 0)
      pending.add(new Hit(player, damage, direct, x, y, plane, view, tick, cycle));
  }

  public void participant(String player) {
    if (active()) current.players.add(player);
  }

  public void observation(
      String player, String type, int damage, int tick, int cycle, String evidence) {
    if (active()) add(new Event(tick, cycle, player, type, evidence, damage));
  }

  public void research(int tick, int cycle, String player, String type, String detail, int amount) {
    if (!active()) return;
    if (current.research.size() < 50000)
      current.research.add(new Event(tick, cycle, player, type, detail, amount));
    else current.omittedResearch++;
  }

  public void death(String player, int tick, int cycle) {
    if (active())
      add(new Event(tick, cycle, player, "Death", "Observed death event; cause not attributed", 0));
  }

  public void drain(int cycle, boolean all) {
    Iterator<Hit> iterator = pending.iterator();
    while (iterator.hasNext()) {
      Hit hit = iterator.next();
      if (!all && cycle - hit.cycle < 30) continue;
      iterator.remove();
      Set<String> matches = new HashSet<>();
      if (hit.direct)
        for (Floor f : floors)
          if (f.x == hit.x
              && f.y == hit.y
              && f.plane == hit.plane
              && f.view == hit.view
              && Math.abs(f.start - hit.cycle) <= 10) matches.add(f.type);
      if (matches.size() == 1) {
        add(
            new Event(
                hit.tick,
                hit.cycle,
                hit.player,
                "Possible " + matches.iterator().next(),
                "Positive damage on a matching scheduled floor GFX tile (within 10 client cycles);"
                    + " source unconfirmed",
                hit.damage));
      }
    }
    floors.removeIf(f -> cycle - f.start > 120);
  }

  public void finish(int tick, int cycle, String outcome) {
    if (current == null || current.endTick >= 0) return;
    drain(cycle, true);
    current.endTick = tick;
    current.outcome = outcome;
    floors.clear();
    pending.clear();
  }

  private void add(Event event) {
    if (current == null || MistakeKind.find(current.boss, event.type) == null) return;
    current.players.add(event.player);
    for (int i = current.events.size() - 1; i >= 0; i--) {
      Event old = current.events.get(i);
      if (old.tick == event.tick
          && old.player.equals(event.player)
          && old.type.equals(event.type)) {
        old.damage += event.damage;
        return;
      }
    }
    if (current.events.size() < 2000) current.events.add(event);
    else current.omittedEvents++;
  }

  public void reset() {
    encounters.clear();
    current = null;
    floors.clear();
    pending.clear();
    next = 1;
  }
}
