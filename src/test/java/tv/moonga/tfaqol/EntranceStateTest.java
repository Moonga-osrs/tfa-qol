package tv.moonga.tfaqol;

import static org.junit.Assert.*;

import java.util.*;
import net.runelite.api.Item;
import org.junit.Test;

public class EntranceStateTest {
  @Test
  public void allThreeChinTypesAreAcceptedWithoutAnySalve() {
    for (int id : new int[] {10033, 10034, 11959}) {
      EntranceState s = EntranceState.read(new Item[] {new Item(id, 50)}, 0);
      assertTrue(s.chins);
      assertFalse(s.blocked(true));
      assertEquals(Collections.singletonList("Spellbook: Standard"), s.lines());
    }
  }

  @Test
  public void placeholdersAndNonweaponChinsAreRejected() {
    for (int id : new int[] {12018, 25278, 26782, 9976, 9977, 15365, -1})
      assertFalse(EntranceState.read(new Item[] {new Item(id, 1)}, 0).chins);
  }

  @Test
  public void zeroOrNegativeQuantityDoesNotCount() {
    assertFalse(
        EntranceState.read(new Item[] {new Item(10033, 0), new Item(11959, -1), null}, 0).chins);
  }

  @Test
  public void emptyInventoryOnlyWarnsAboutChins() {
    EntranceState s = EntranceState.read(new Item[0], 1);
    assertEquals(Arrays.asList("Spellbook: Ancient", "Missing Chinchompas"), s.lines());
    assertFalse(s.blocked(false));
    assertTrue(s.blocked(true));
  }

  @Test
  public void loadingDoesNotPretendTheInventoryIsEmpty() {
    EntranceState s = EntranceState.read(null, 3);
    assertFalse(s.loaded);
    assertEquals(Arrays.asList("Spellbook: Arceuus", "Inventory loading..."), s.lines());
    assertFalse(s.blocked(false));
    assertTrue(s.blocked(true));
  }

  @Test
  public void spellbookNamesAndUnknownAreExplicit() {
    assertEquals("Standard", EntranceState.spellbookName(0));
    assertEquals("Ancient", EntranceState.spellbookName(1));
    assertEquals("Lunar", EntranceState.spellbookName(2));
    assertEquals("Arceuus", EntranceState.spellbookName(3));
    assertEquals("Unknown", EntranceState.spellbookName(99));
  }
}
