package tv.moonga.tfaqol;

import static org.junit.Assert.*;

import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

public class EntranceResetTest {
  private WorldPoint p(int x, int y) {
    return new WorldPoint(x, y, 0);
  }

  @Test
  public void aClickWithoutEntryDoesNotReset() {
    EntranceReset r = new EntranceReset();
    r.arm(p(100, 99), p(100, 100), 10);
    assertFalse(r.observe(p(100, 99), 11));
    assertFalse(r.observe(p(100, 100), 12));
  }

  @Test
  public void crossingTheDoorResetsExactlyOnce() {
    EntranceReset r = new EntranceReset();
    r.arm(p(100, 99), p(100, 100), 10);
    assertTrue(r.observe(p(100, 101), 11));
    assertFalse(r.observe(p(100, 102), 12));
  }

  @Test
  public void farAwayWalkingIsNotEntry() {
    EntranceReset r = new EntranceReset();
    r.arm(p(100, 50), p(100, 100), 10);
    assertFalse(r.observe(p(100, 90), 11));
    assertTrue(r.observe(p(100, 101), 12));
  }

  @Test
  public void entryTeleportConfirmsWhenNearDoor() {
    EntranceReset r = new EntranceReset();
    r.arm(p(100, 99), p(100, 100), 10);
    assertTrue(r.observe(p(4000, 5000), 11));
  }

  @Test
  public void cancelledOrExpiredAttemptCannotResetLater() {
    EntranceReset r = new EntranceReset();
    r.arm(p(100, 99), p(100, 100), 10);
    r.cancel();
    assertFalse(r.observe(p(100, 101), 11));
    r.arm(p(100, 99), p(100, 100), 10);
    assertFalse(r.observe(p(100, 101), 71));
  }

  @Test
  public void crossingAnUnrelatedPartOfTheMapIsNotEntry() {
    EntranceReset r = new EntranceReset();
    r.arm(p(20, 99), p(100, 100), 10);
    assertFalse(r.observe(p(20, 101), 11));
  }
}
