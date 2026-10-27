package tv.moonga.tfaqol;

import java.util.*;

/** Derives only the currently observed gaps; retains no sequence or forecasts. */
final class PortalGaps {
  static final class Portal {
    final int x, y, id;

    Portal(int x, int y, int id) {
      this.x = x;
      this.y = y;
      this.id = id;
    }
  }

  static List<int[]> current(List<Portal> portals) {
    if (portals.size() != 24) return Collections.emptyList();
    Set<String> positions = new HashSet<>();
    Map<Integer, Integer> xs = new HashMap<>(), ys = new HashMap<>();
    for (Portal p : portals) {
      if (!positions.add(p.x + ":" + p.y)) return Collections.emptyList();
      xs.merge(p.x, 1, Integer::sum);
      ys.merge(p.y, 1, Integer::sum);
    }
    int cx = xs.entrySet().stream().max(Map.Entry.comparingByValue()).get().getKey();
    int cy = ys.entrySet().stream().max(Map.Entry.comparingByValue()).get().getKey();
    if (xs.get(cx) != 12 || ys.get(cy) != 12) return Collections.emptyList();
    List<int[]> result = new ArrayList<>();
    for (int sx : new int[] {-1, 1})
      for (int sy : new int[] {-1, 1}) {
        Integer gapX = gap(portals, cx, cy, sx, true), gapY = gap(portals, cx, cy, sy, false);
        if (gapX != null && gapY != null) result.add(new int[] {gapX, gapY, cx, cy});
      }
    return result;
  }

  private static Integer gap(List<Portal> portals, int cx, int cy, int sign, boolean horizontal) {
    Integer gap = null;
    int total = 0, active = 0;
    Set<Integer> slots = new HashSet<>();
    for (Portal p : portals) {
      int d = (horizontal ? p.x - cx : p.y - cy);
      if ((horizontal ? p.y != cy : p.x != cx) || Integer.signum(d) != sign) continue;
      int slot = sign < 0 ? -d / 2 - 1 : (d - 1) / 2;
      int expected = sign < 0 ? -2 * (slot + 1) : 1 + 2 * slot;
      if (slot < 0 || slot > 5 || d != expected || !slots.add(slot)) return null;
      total++;
      if (p.id == 62685) active++;
      else if (p.id == 62684 && gap == null) gap = horizontal ? p.x : p.y;
      else return null;
    }
    return total == 6 && active == 5 ? gap : null;
  }
}
