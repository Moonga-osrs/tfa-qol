package tv.moonga.tfaqol;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import net.runelite.api.*;
import org.junit.Test;

public class EntranceMenuTest {
  static MenuEntry entry(int id, String option, MenuAction action) {
    MenuEntry e = mock(MenuEntry.class);
    when(e.getIdentifier()).thenReturn(id);
    when(e.getOption()).thenReturn(option);
    when(e.getType()).thenReturn(action);
    return e;
  }

  @Test
  public void targetsOnlyEnterOnTheArchiveDoor() {
    assertTrue(EntranceMenu.isEnter(entry(62768, "Enter", MenuAction.GAME_OBJECT_FIRST_OPTION)));
    assertFalse(EntranceMenu.isEnter(entry(62769, "Enter", MenuAction.GAME_OBJECT_FIRST_OPTION)));
    assertFalse(EntranceMenu.isEnter(entry(62768, "Examine", MenuAction.EXAMINE_OBJECT)));
    assertFalse(EntranceMenu.isEnter(entry(62768, "Enter", MenuAction.NPC_FIRST_OPTION)));
  }

  @Test
  public void decoratedEnterAndSwappedObjectOptionsAreRecognized() {
    assertTrue(
        EntranceMenu.isEnter(
            entry(62768, "<col=ff0000>Enter</col>", MenuAction.GAME_OBJECT_SECOND_OPTION)));
    assertTrue(EntranceMenu.isEnter(entry(62768, " enter ", MenuAction.GAME_OBJECT_FIFTH_OPTION)));
  }

  @Test
  public void itemUseAndNullsAreNotEntranceActions() {
    assertFalse(EntranceMenu.isEnter(null));
    assertFalse(EntranceMenu.isEnter(entry(62768, null, MenuAction.GAME_OBJECT_FIRST_OPTION)));
    assertFalse(EntranceMenu.isEnter(entry(62768, "Use", MenuAction.ITEM_USE_ON_GAME_OBJECT)));
  }
}
