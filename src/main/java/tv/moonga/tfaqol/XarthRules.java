package tv.moonga.tfaqol;

import java.util.*;

/** Basic fireballs, BURN and BLEED are context, never automatic prayer mistakes. */
final class XarthRules {
  enum Source {
    BASIC_MAGIC,
    RANGED,
    AOE,
    MELEE,
    FIRE,
    EXPLOSION
  }

  static final class Signal {
    final Source source;
    final int id, start, x, y, plane;
    int end;
    final String target;

    Signal(Source source, int id, int start, int end, int x, int y, int plane, String target) {
      this.source = source;
      this.id = id;
      this.start = start;
      this.end = end;
      this.x = x;
      this.y = y;
      this.plane = plane;
      this.target = target;
    }

    String detail() {
      return source
          + " id="
          + id
          + " start="
          + start
          + " end="
          + end
          + " at="
          + x
          + ","
          + y
          + ","
          + plane
          + " target="
          + target;
    }
  }

  static final class Hit {
    String player, prayerEvidence;
    int tick, cycle, amount, type, x, y, plane;
    boolean rangedPrayer, magicPrayer;
  }

  static final class Result {
    final Set<Source> sources = new LinkedHashSet<>();
    final List<String> evidence = new ArrayList<>();
    MistakeKind mistake;
    String status;
  }

  private final List<Signal> history = new ArrayList<>();

  void add(Signal s) {
    history.add(s);
  }

  void fire(int x, int y, int plane, int cycle, boolean spawn) {
    if (spawn) {
      if (history.stream()
          .noneMatch(
              s ->
                  s.source == Source.FIRE
                      && s.x == x
                      && s.y == y
                      && s.plane == plane
                      && s.end == Integer.MAX_VALUE))
        add(new Signal(Source.FIRE, 62713, cycle, Integer.MAX_VALUE, x, y, plane, null));
    } else
      for (Signal s : history)
        if (s.source == Source.FIRE
            && s.x == x
            && s.y == y
            && s.plane == plane
            && s.end == Integer.MAX_VALUE) s.end = cycle;
  }

  boolean onFire(int x, int y, int plane, int cycle) {
    return history.stream()
        .anyMatch(
            s ->
                s.source == Source.FIRE
                    && s.x == x
                    && s.y == y
                    && s.plane == plane
                    && s.start <= cycle
                    && s.end >= cycle);
  }

  Result match(Hit h, Collection<Signal> projectiles) {
    Result r = new Result();
    List<Signal> all = new ArrayList<>(history);
    all.addAll(projectiles);
    for (Signal s : all) {
      if (s.plane != h.plane) continue;
      boolean match = false;
      switch (s.source) {
        case FIRE:
          match = s.x == h.x && s.y == h.y && s.start <= h.cycle && s.end >= h.cycle;
          break;
        case MELEE:
          match =
              Math.abs(s.start - h.cycle) <= 10
                  && Math.max(
                          0,
                          Math.max(
                              Math.max(s.x - h.x, h.x - s.x - 2),
                              Math.max(s.y - h.y, h.y - s.y - 2)))
                      <= 1;
          break;
        case BASIC_MAGIC:
        case RANGED:
          match =
              Math.abs(s.end - h.cycle) <= 10
                  && (h.player.equals(s.target) || s.target == null && s.x == h.x && s.y == h.y);
          break;
        case AOE:
          match = Math.abs(s.end - h.cycle) <= 10 && s.x == h.x && s.y == h.y;
          break;
        case EXPLOSION:
          match = Math.abs(s.end - h.cycle) <= 10;
          break;
      }
      if (match) {
        r.sources.add(s.source);
        if (r.evidence.size() < 30) r.evidence.add(s.detail());
      }
    }
    if (h.amount <= 0) {
      r.status = "NO_DAMAGE";
      return r;
    }
    if (h.type == 74) {
      r.status = "BURN_STATUS";
      return r;
    }
    if (h.type == 67) {
      r.status = "BLEED_STATUS";
      return r;
    }
    if (r.sources.size() != 1) {
      r.status = r.sources.isEmpty() ? "UNRESOLVED" : "AMBIGUOUS";
      return r;
    }
    switch (r.sources.iterator().next()) {
      case BASIC_MAGIC:
        if (!h.magicPrayer
            && all.stream()
                .anyMatch(
                    s ->
                        s.source == Source.BASIC_MAGIC
                            && h.player.equals(s.target)
                            && Math.abs(s.end - h.cycle) <= 10
                            && s.plane == h.plane)) r.mistake = MistakeKind.XARTH_MAGIC;
        else {
          r.status = "BASIC_MAGIC_CONTEXT";
          return r;
        }
        break;
      case RANGED:
        if (!h.rangedPrayer) r.mistake = MistakeKind.XARTH_RANGE;
        break;
      case MELEE:
        r.mistake = MistakeKind.XARTH_MELEE;
        break;
      case AOE:
        r.mistake = MistakeKind.XARTH_AOE;
        break;
      case FIRE:
        r.mistake = MistakeKind.XARTH_FIRE;
        break;
      case EXPLOSION:
        r.mistake = MistakeKind.XARTH_EXPLOSION;
        break;
    }
    r.status = r.mistake == null ? "PROTECTED" : "POSSIBLE_MISTAKE";
    return r;
  }

  void prune(int cycle) {
    history.removeIf(s -> s.end != Integer.MAX_VALUE && cycle - s.end > 120);
  }

  void clear() {
    history.clear();
  }
}
