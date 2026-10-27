package tv.moonga.tfaqol;

import static org.junit.Assert.*;

import java.util.*;
import org.junit.Test;

public class PortalGapsTest {
  static List<PortalGaps.Portal> portals(int h, int v) {
    List<PortalGaps.Portal> result = new ArrayList<>();
    for (int sign : new int[] {-1, 1})
      for (int slot = 0; slot < 6; slot++) {
        int at = 50 + (sign < 0 ? -2 * (slot + 1) : 1 + 2 * slot);
        result.add(new PortalGaps.Portal(at, 50, slot == h ? 62684 : 62685));
        result.add(new PortalGaps.Portal(50, at, slot == v ? 62684 : 62685));
      }
    return result;
  }

  @Test
  public void allCurrentGapCombinationsWorkWithoutAnyHistory() {
    for (int h = 0; h < 6; h++)
      for (int v = 0; v < 6; v++) {
        List<int[]> gaps = PortalGaps.current(portals(h, v));
        assertEquals(4, gaps.size());
        for (int[] gap : gaps) {
          assertTrue(gap[0] == 50 - 2 * (h + 1) || gap[0] == 51 + 2 * h);
          assertTrue(gap[1] == 50 - 2 * (v + 1) || gap[1] == 51 + 2 * v);
        }
      }
  }

  @Test
  public void incompleteDuplicateOrAllInactiveSnapshotsDoNotInventSafeTiles() {
    List<PortalGaps.Portal> p = portals(1, 2);
    p.remove(0);
    assertTrue(PortalGaps.current(p).isEmpty());
    p = portals(1, 2);
    p.set(0, p.get(1));
    assertTrue(PortalGaps.current(p).isEmpty());
    p = portals(1, 2);
    List<PortalGaps.Portal> idle = new ArrayList<>();
    for (PortalGaps.Portal at : p) idle.add(new PortalGaps.Portal(at.x, at.y, 62684));
    assertTrue(PortalGaps.current(idle).isEmpty());
  }
}
