package tv.moonga.tfaqol;

import java.util.*;

/** Dreadnoth source evidence and impact-time prayer checks, kept separate from damage amounts. */
final class DreadnothRules {
  enum Source {
    MELEE,
    STOMP,
    RANGE,
    JELLY,
    BLOOD_TILE,
    LANDING,
    ENRAGE_RANGE,
    BLOOD_SHOT
  }

  static final class Signal {
    final Source source;
    final int cycle, x, y, plane, size, id, start;
    int end;
    final String target;

    Signal(
        Source source,
        int cycle,
        int x,
        int y,
        int plane,
        int size,
        int id,
        String target,
        int start,
        int end) {
      this.source = source;
      this.cycle = cycle;
      this.x = x;
      this.y = y;
      this.plane = plane;
      this.size = size;
      this.id = id;
      this.target = target;
      this.start = start;
      this.end = end;
    }

    String detail() {
      return source
          + " id="
          + id
          + " impact="
          + cycle
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
    String player;
    int x, y, plane, cycle, tick, amount, type;
    boolean meleePrayer, rangedPrayer, underBoss;
    String prayerEvidence;
  }

  static final class Result {
    final Set<Source> sources = new LinkedHashSet<>();
    final Set<MistakeKind> mistakes = new LinkedHashSet<>();
    final List<String> evidence = new ArrayList<>();
    String override;

    String status() {
      return override != null
          ? override
          : sources.size() > 1
              ? "AMBIGUOUS"
              : sources.isEmpty()
                  ? "UNRESOLVED"
                  : mistakes.isEmpty() ? "PROTECTED" : "POSSIBLE_MISTAKE";
    }
  }

  private final List<Signal> history = new ArrayList<>();

  void add(Signal s) {
    history.add(s);
  }

  void ground(int x, int y, int plane, int cycle, boolean spawn) {
    if (spawn) {
      if (history.stream()
          .noneMatch(
              s ->
                  s.source == Source.BLOOD_TILE
                      && s.x == x
                      && s.y == y
                      && s.plane == plane
                      && s.end == Integer.MAX_VALUE))
        add(
            new Signal(
                Source.BLOOD_TILE, cycle, x, y, plane, 1, 62609, null, cycle, Integer.MAX_VALUE));
    } else
      for (Signal s : history)
        if (s.source == Source.BLOOD_TILE
            && s.x == x
            && s.y == y
            && s.plane == plane
            && s.end == Integer.MAX_VALUE) s.end = cycle;
  }

  Result match(Hit h, Collection<Signal> projectiles) {
    Result r = new Result();
    if (h.amount <= 0) return r;
    List<Signal> all = new ArrayList<>(history);
    all.addAll(projectiles);
    List<Signal> matches = new ArrayList<>();
    for (Signal s : all) {
      if (s.plane != h.plane) continue;
      boolean match = false;
      switch (s.source) {
        case BLOOD_TILE:
          match = s.x == h.x && s.y == h.y && s.start <= h.cycle && h.cycle <= s.end;
          break;
        case MELEE:
          match = Math.abs(s.cycle - h.cycle) <= 10 && distance(s, h) <= 1;
          break;
        case STOMP:
        case LANDING:
          match = Math.abs(s.cycle - h.cycle) <= 10;
          break;
        case JELLY:
          match = Math.abs(s.cycle - h.cycle) <= 10 && h.player.equals(s.target);
          break;
        case RANGE:
        case ENRAGE_RANGE:
          match =
              Math.abs(s.cycle - h.cycle) <= 10
                  && (h.player.equals(s.target) || s.target == null && s.x == h.x && s.y == h.y);
          break;
        case BLOOD_SHOT:
          match = Math.abs(s.cycle - h.cycle) <= 10 && s.x == h.x && s.y == h.y;
          break;
      }
      if (match) matches.add(s);
    }
    // The blood projectile creates a puddle at the same instant: this is one impact, not two
    // competing causes.
    matches.removeIf(
        s ->
            s.source == Source.BLOOD_TILE
                && matches.stream()
                    .anyMatch(
                        p ->
                            p.source == Source.BLOOD_SHOT
                                && p.x == s.x
                                && p.y == s.y
                                && Math.abs(p.cycle - s.start) <= 10));
    for (Signal s : matches) {
      r.sources.add(s.source);
      if (r.evidence.size() < 30) r.evidence.add(s.detail());
    }
    if (h.type == 74 || h.type == 67) {
      r.override = h.type == 74 ? "BURN_STATUS" : "BLEED_STATUS";
      return r;
    }
    if (r.sources.size() != 1) return r;
    switch (r.sources.iterator().next()) {
      case MELEE:
        r.mistakes.add(MistakeKind.DREAD_MELEE);
        break;
      case STOMP:
        if (!h.meleePrayer) r.mistakes.add(MistakeKind.DREAD_STOMP);
        break;
      case RANGE:
        if (!h.rangedPrayer) r.mistakes.add(MistakeKind.DREAD_RANGE);
        break;
      case JELLY:
        if (!h.meleePrayer) r.mistakes.add(MistakeKind.DREAD_JELLY);
        break;
      case BLOOD_TILE:
        r.mistakes.add(MistakeKind.DREAD_BLOOD);
        break;
      case LANDING:
        if (!h.meleePrayer) r.mistakes.add(MistakeKind.DREAD_LANDING);
        if (h.underBoss) r.mistakes.add(MistakeKind.DREAD_UNDER);
        break;
      case ENRAGE_RANGE:
        if (!h.rangedPrayer) r.mistakes.add(MistakeKind.DREAD_ENRAGE_RANGE);
        break;
      case BLOOD_SHOT:
        r.mistakes.add(MistakeKind.DREAD_ENRAGE_BLOOD);
        break;
    }
    return r;
  }

  private static int distance(Signal s, Hit h) {
    return Math.max(
        0,
        Math.max(
            Math.max(s.x - h.x, h.x - (s.x + s.size - 1)),
            Math.max(s.y - h.y, h.y - (s.y + s.size - 1))));
  }

  void prune(int cycle) {
    history.removeIf(
        s ->
            s.source == Source.BLOOD_TILE
                ? s.end != Integer.MAX_VALUE && cycle - s.end > 120
                : cycle - s.cycle > 120);
  }

  boolean hasActiveBlood() {
    return history.stream()
        .anyMatch(s -> s.source == Source.BLOOD_TILE && s.end == Integer.MAX_VALUE);
  }

  void clear() {
    history.clear();
  }
}
