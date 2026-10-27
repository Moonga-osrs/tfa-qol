package tv.moonga.tfaqol;

import java.util.*;

/** Only closed, fully walkable 9x9 or 3x3 floors are accepted as the observed combat area. */
final class EncounterArea {
  final int x, y, size;

  EncounterArea(int x, int y, int size) {
    this.x = x;
    this.y = y;
    this.size = size;
  }

  boolean contains(int x, int y) {
    return x >= this.x && x < this.x + size && y >= this.y && y < this.y + size;
  }

  static EncounterArea find(int[][] flags, int x, int y) {
    if (!open(flags, x, y)) return null;
    Set<Long> seen = new HashSet<>();
    ArrayDeque<int[]> q = new ArrayDeque<>();
    q.add(new int[] {x, y});
    seen.add(key(x, y));
    int minX = x, maxX = x, minY = y, maxY = y;
    while (!q.isEmpty()) {
      int[] p = q.remove();
      minX = Math.min(minX, p[0]);
      maxX = Math.max(maxX, p[0]);
      minY = Math.min(minY, p[1]);
      maxY = Math.max(maxY, p[1]);
      for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
        int a = p[0] + d[0], b = p[1] + d[1];
        if (open(flags, a, b) && seen.add(key(a, b))) {
          if (seen.size() > 81) return null;
          q.add(new int[] {a, b});
        }
      }
    }
    int n = maxX - minX + 1;
    return (n == 3 || n == 9) && maxY - minY + 1 == n && seen.size() == n * n
        ? new EncounterArea(minX, minY, n)
        : null;
  }

  private static long key(int x, int y) {
    return ((long) x << 32) ^ (y & 0xffffffffL);
  }

  private static boolean open(int[][] f, int x, int y) {
    return f != null
        && x >= 0
        && x < f.length
        && f[x] != null
        && y >= 0
        && y < f[x].length
        && (f[x][y] & 0x1240100) == 0;
  }
}
