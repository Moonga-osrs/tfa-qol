package tv.moonga.tfaqol;

import java.util.*;
import net.runelite.api.*;
import net.runelite.client.util.Text;

public final class EntranceMenu {
  private EntranceMenu() {}

  public static boolean isEnter(MenuEntry entry) {
    if (entry == null
        || entry.getIdentifier() != EntranceState.DOOR_ID
        || entry.getOption() == null
        || !"Enter".equalsIgnoreCase(Text.removeTags(entry.getOption()).trim())) return false;
    MenuAction type = entry.getType();
    return type == MenuAction.GAME_OBJECT_FIRST_OPTION
        || type == MenuAction.GAME_OBJECT_SECOND_OPTION
        || type == MenuAction.GAME_OBJECT_THIRD_OPTION
        || type == MenuAction.GAME_OBJECT_FOURTH_OPTION
        || type == MenuAction.GAME_OBJECT_FIFTH_OPTION;
  }
}
