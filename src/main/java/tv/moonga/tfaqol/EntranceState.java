package tv.moonga.tfaqol;

import java.util.*;
import net.runelite.api.Item;

/** Exact, usable inventory IDs; equipment and bank placeholders are not substitutes. */
public final class EntranceState {
  public static final int DOOR_ID = 62768;
  public static final int SPELLBOOK_VARBIT = 4070;
  private static final Set<Integer> CHINS =
      Collections.unmodifiableSet(new HashSet<>(Arrays.asList(10033, 10034, 11959)));
  public final boolean loaded, chins;
  public final String spellbook;

  private EntranceState(boolean loaded, boolean chins, int book) {
    this.loaded = loaded;
    this.chins = chins;
    this.spellbook = spellbookName(book);
  }

  public static EntranceState read(Item[] inventory, int book) {
    boolean chins = false;
    if (inventory != null)
      for (Item item : inventory)
        if (item != null && item.getQuantity() > 0) chins |= CHINS.contains(item.getId());
    return new EntranceState(inventory != null, chins, book);
  }

  public static String spellbookName(int book) {
    switch (book) {
      case 0:
        return "Standard";
      case 1:
        return "Ancient";
      case 2:
        return "Lunar";
      case 3:
        return "Arceuus";
      default:
        return "Unknown";
    }
  }

  public boolean blocked(boolean requireChins) {
    return requireChins && !chins;
  }

  public List<String> lines() {
    return lines(true, true);
  }

  public List<String> lines(boolean spellbookText, boolean chinsText) {
    List<String> lines = new ArrayList<>();
    if (spellbookText) lines.add("Spellbook: " + spellbook);
    if (chinsText) {
      if (!loaded) lines.add("Inventory loading...");
      else if (!chins) lines.add("Missing Chinchompas");
    }
    return lines;
  }
}
