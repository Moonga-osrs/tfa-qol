package tv.moonga.tfaqol;

import java.util.*;

/** Replay-derived correlations. No damage amount or animation alone is a mistake rule. */
final class OcellusEvidence {
  static final class Signal {
    final int id, x, y, plane, start, end;
    final String player, source, family;
    final boolean object;

    Signal(
        int id,
        int x,
        int y,
        int plane,
        int start,
        int end,
        String player,
        String source,
        boolean object) {
      this.id = id;
      this.x = x;
      this.y = y;
      this.plane = plane;
      this.start = start;
      this.end = end;
      this.player = player;
      this.source = source;
      this.object = object;
      family = family(id, object);
    }

    String evidence() {
      return source + " id=" + id + " start=" + start + " end=" + end + " at=" + x + "," + y + ","
          + plane;
    }
  }

  static final class Result {
    final Set<String> families = new LinkedHashSet<>();
    final List<String> evidence = new ArrayList<>();

    String status() {
      return families.size() > 1 ? "AMBIGUOUS" : families.isEmpty() ? "UNRESOLVED" : "CORRELATED";
    }

    MistakeKind kind() {
      return families.size() == 1 ? mistake(families.iterator().next()) : null;
    }
  }

  private final List<Signal> signals = new ArrayList<>();

  void add(Signal s) {
    if (s.family != null) signals.add(s);
  }

  void projectiles(Collection<Signal> latest) {
    signals.removeIf(s -> "projectile".equals(s.source));
    for (Signal s : latest) add(s);
  }

  void removeObject(int id, int x, int y, int plane, int cycle) {
    for (int i = 0; i < signals.size(); i++) {
      Signal s = signals.get(i);
      if (s.object
          && s.id == id
          && s.x == x
          && s.y == y
          && s.plane == plane
          && s.end == Integer.MAX_VALUE)
        signals.set(i, new Signal(id, x, y, plane, s.start, cycle, null, s.source, true));
    }
  }

  Result match(String player, int x, int y, int plane, int cycle) {
    return match(player, x, y, plane, cycle, Collections.emptyList());
  }

  Result match(String player, int x, int y, int plane, int cycle, Collection<Signal> attached) {
    Result result = new Result();
    List<Signal> candidates = new ArrayList<>(signals);
    candidates.addAll(attached);
    for (Signal s : candidates) {
      if (s.plane != plane) continue;
      boolean match = false;
      if (s.object) match = s.x == x && s.y == y && s.start <= cycle && s.end >= cycle;
      else if ("projectile".equals(s.source))
        match =
            Math.abs(s.end - cycle) <= 10
                && (player.equals(s.player) || s.player == null && s.x == x && s.y == y);
      else if ("path".equals(s.source))
        match = s.x == x && s.y == y && Math.abs(s.start - cycle) <= 3;
      else if ("spot".equals(s.source))
        match =
            player.equals(s.player)
                && (s.id == 4313
                    ? Math.abs(cycle - s.start - 30) <= 10
                    : Math.abs(s.start - cycle) <= 10);
      else match = s.x == x && s.y == y && Math.abs(s.start - cycle) <= 10;
      if (match) {
        result.families.add(s.family);
        if (result.evidence.size() < 40) result.evidence.add(s.evidence());
      }
    }
    return result;
  }

  void prune(int cycle) {
    signals.removeIf(s -> s.end != Integer.MAX_VALUE && cycle - s.end > 120);
  }

  void clear() {
    signals.clear();
  }

  static String family(int id, boolean object) {
    if (object)
      return id == 62701
          ? "quadrant-bomb"
          : id == 62702 ? "debris" : id >= 62703 && id <= 62705 ? "shadow-residue" : null;
    if (id >= 4228 && id <= 4230) return "water";
    if (id >= 4231 && id <= 4233) return "fire";
    if (id >= 4236 && id <= 4259) return "floor-cracks";
    if (id >= 4260 && id <= 4265) return "charge-explosion";
    if (id == 4269) return "portal-projectiles";
    if (id == 4270) return "aoe-dodge";
    if (id >= 4290 && id <= 4292) return "entangle";
    if (id >= 4293 && id <= 4298) return "magic-impact";
    if (id >= 4299 && id <= 4302) return "quadrant-bomb";
    if (id == 4303) return "voidflare-healing";
    if (id >= 4309 && id <= 4316) return "soul-fragments";
    if (id >= 4317 && id <= 4321) return "unleash";
    if (id >= 4328 && id <= 4330) return "hybrid";
    if (id >= 4335 && id <= 4338) return id % 2 == 1 ? "shadow-slash" : "superior-shadow-slash";
    if (id == 4339 || id == 4340) return "void-chain";
    if (id >= 4346 && id <= 4348) return "debris";
    if (id >= 4349 && id <= 4372) return "deflect-arena";
    return id >= 4236 && id <= 4348 ? "gfx-" + id : null;
  }

  static MistakeKind mistake(String family) {
    switch (family) {
      case "floor-cracks":
        return MistakeKind.OC_CRACKS;
      case "charge-explosion":
        return MistakeKind.OC_EXPLOSION;
      case "portal-projectiles":
        return MistakeKind.OC_PORTAL;
      case "aoe-dodge":
        return MistakeKind.OC_DODGE;
      case "quadrant-bomb":
        return MistakeKind.OC_BOMB;
      case "shadow-residue":
        return MistakeKind.OC_RESIDUE;
      case "shadow-slash":
        return MistakeKind.OC_SLASH;
      case "superior-shadow-slash":
        return MistakeKind.OC_SUPERIOR;
      case "debris":
        return MistakeKind.OC_DEBRIS;
      case "void-chain":
        return MistakeKind.OC_CHAIN;
      case "unleash":
        return MistakeKind.OC_UNLEASH;
      default:
        return null; // Water/fire, basic/hybrid chip, soul and reflect require further validation.
    }
  }
}
