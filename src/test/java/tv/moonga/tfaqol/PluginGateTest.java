package tv.moonga.tfaqol;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import net.runelite.api.*;
import net.runelite.api.events.*;
import org.junit.*;

public class PluginGateTest {
  private TfaQolPlugin plugin;
  private Client client;
  private ItemContainer inventory;
  private Settings settings;

  static class Settings implements TfaConfig {
    boolean chins;

    public boolean requireChins() {
      return chins;
    }
  }

  static void inject(Object target, String field, Object value) throws Exception {
    Field f = target.getClass().getDeclaredField(field);
    f.setAccessible(true);
    f.set(target, value);
  }

  @Before
  public void setup() throws Exception {
    plugin = new TfaQolPlugin();
    client = mock(Client.class);
    inventory = mock(ItemContainer.class);
    settings = new Settings();
    inject(plugin, "client", client);
    inject(plugin, "config", settings);
    when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
    when(client.getItemContainer(InventoryID.INVENTORY)).thenReturn(inventory);
    when(inventory.getItems()).thenReturn(new Item[0]);
  }

  private MenuEntry enter() {
    return EntranceMenuTest.entry(62768, "Enter", MenuAction.GAME_OBJECT_FIRST_OPTION);
  }

  private MenuShouldLeftClick left(MenuEntry... entries) {
    when(client.getMenuEntries()).thenReturn(entries);
    MenuShouldLeftClick e = new MenuShouldLeftClick();
    plugin.onMenuShouldLeftClick(e);
    return e;
  }

  @Test
  public void defaultsKeepNormalLeftClick() {
    assertFalse(left(enter()).isForceRightClick());
  }

  @Test
  public void chinsAloneRestoreEntryWithoutAnySalve() {
    settings.chins = true;
    when(inventory.getItems()).thenReturn(new Item[] {new Item(11959, 5)});
    assertFalse(left(enter()).isForceRightClick());
  }

  @Test
  public void missingChinsForcesMenu() {
    settings.chins = true;
    assertTrue(left(enter()).isForceRightClick());
  }

  @Test
  public void itemsRestoreNormalLeftClick() {
    settings.chins = true;
    settings.chins = true;
    when(inventory.getItems()).thenReturn(new Item[] {new Item(12018, 1), new Item(11959, 5)});
    assertFalse(left(enter()).isForceRightClick());
  }

  @Test
  public void rightClickEnterIsNeverConsumed() {
    settings.chins = true;
    settings.chins = true;
    MenuOptionClicked e = new MenuOptionClicked(enter());
    plugin.onMenuOptionClicked(e);
    assertFalse(e.isConsumed());
    verify(client, never()).addChatMessage(any(), any(), any(), any());
  }

  @Test
  public void entriesAreKeptAndDeprioritized() {
    settings.chins = true;
    MenuEntry door = enter(),
        other = EntranceMenuTest.entry(77, "Enter", MenuAction.GAME_OBJECT_FIRST_OPTION);
    when(client.getMenuEntries()).thenReturn(new MenuEntry[] {door, other});
    plugin.onPostMenuSort(new PostMenuSort());
    verify(door).setDeprioritized(true);
    verify(door).setForceLeftClick(false);
    verify(other, never()).setDeprioritized(anyBoolean());
    verify(client, never()).setMenuEntries(any());
  }

  @Test
  public void alreadyOpenMenuKeepsEnter() {
    settings.chins = true;
    MenuEntry door = enter();
    MenuOpened e = new MenuOpened();
    e.setMenuEntries(new MenuEntry[] {door});
    plugin.onMenuOpened(e);
    assertSame(door, e.getMenuEntries()[0]);
    verify(door).setDeprioritized(true);
  }

  @Test
  public void freshEntriesAreDeprioritized() {
    settings.chins = true;
    MenuEntry door = enter();
    plugin.onMenuEntryAdded(new MenuEntryAdded(door));
    verify(door).setDeprioritized(true);
  }

  @Test
  public void inventoryLoadingOnlyForcesMenuWhenEnabled() {
    when(client.getItemContainer(InventoryID.INVENTORY)).thenReturn(null);
    settings.chins = true;
    assertTrue(left(enter()).isForceRightClick());
    settings.chins = false;
    assertFalse(left(enter()).isForceRightClick());
  }

  @Test
  public void unrelatedTopOptionDoesNotForceMenu() {
    settings.chins = true;
    assertFalse(
        left(enter(), EntranceMenuTest.entry(77, "Enter", MenuAction.GAME_OBJECT_FIRST_OPTION))
            .isForceRightClick());
  }

  @Test
  public void loggedOutDoesNotForceMenu() {
    settings.chins = true;
    when(client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
    assertFalse(left(enter()).isForceRightClick());
  }

  @Test
  public void removingRequirementRestoresPriority() {
    MenuEntry door = enter();
    settings.chins = false;
    when(client.getMenuEntries()).thenReturn(new MenuEntry[] {door});
    plugin.onPostMenuSort(new PostMenuSort());
    verify(door).setDeprioritized(false);
  }
}
