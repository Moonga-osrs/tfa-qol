package tv.moonga.tfaqol;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.SpotanimID;
import net.runelite.api.gameval.VarbitID;
import org.junit.*;

public class LootOptionsTest {
  static class Settings implements TfaConfig {
    boolean hide = true, bank = true;

    public boolean hiddenLoot() {
      return hide;
    }

    public boolean bankAllSingleClick() {
      return bank;
    }
  }

  private Client client;
  private WorldView view;
  private NPC chest;
  private Settings config;
  private LootOptions loot;
  private final Map<Long, ActorSpotAnim> effects = new LinkedHashMap<>();
  private final AtomicInteger ownChest = new AtomicInteger(7);

  @Before
  @SuppressWarnings({"rawtypes", "unchecked"})
  public void setup() throws Exception {
    client = mock(Client.class);
    view = mock(WorldView.class);
    chest = mock(NPC.class);
    config = new Settings();
    loot = new LootOptions();
    PluginGateTest.inject(loot, "client", client);
    PluginGateTest.inject(loot, "config", config);
    when(client.getTopLevelWorldView()).thenReturn(view);
    when(chest.getWorldView()).thenReturn(view);
    when(client.getVarbitValue(VarbitID.TFA_VAULT_OWN_CHEST)).thenAnswer(i -> ownChest.get());
    doAnswer(
            i -> {
              ownChest.set(i.getArgument(1));
              return null;
            })
        .when(client)
        .setVarbit(eq(VarbitID.TFA_VAULT_OWN_CHEST), anyInt());
    IndexedObjectSet npcs = mock(IndexedObjectSet.class);
    when(npcs.iterator()).thenAnswer(i -> Collections.singletonList(chest).iterator());
    when(view.npcs()).thenReturn(npcs);
    IterableHashTable table = mock(IterableHashTable.class);
    when(table.iterator()).thenAnswer(i -> new ArrayList<>(effects.values()).iterator());
    when(table.get(anyLong())).thenAnswer(i -> effects.get(i.getArgument(0)));
    when(chest.getSpotAnims()).thenReturn(table);
    doAnswer(
            i -> {
              effects.remove(((Integer) i.getArgument(0)).longValue());
              return null;
            })
        .when(chest)
        .removeSpotAnim(anyInt());
  }

  private WorldPoint lootRoom(int y) {
    return new WorldPoint(4100, y, 0);
  }

  private void effect(int slot, int id) {
    ActorSpotAnim s = mock(ActorSpotAnim.class);
    when(s.getHash()).thenReturn((long) slot);
    when(s.getId()).thenReturn(id);
    when(s.getHeight()).thenReturn(12);
    effects.put((long) slot, s);
  }

  @Test
  public void bankAllIsLimitedToLobbyAndLootAndCanBeDisabled() {
    MenuEntry menu = mock(MenuEntry.class);
    when(menu.getOption()).thenReturn("Bank-all");
    loot.update(new WorldPoint(100, 100, 0));
    loot.bankAll(menu);
    verify(menu, never()).setForceLeftClick(anyBoolean());
    loot.update(new WorldPoint(4100, 5505, 0));
    loot.bankAll(menu);
    verify(menu).setForceLeftClick(true);
    clearInvocations(menu);
    loot.update(lootRoom(5780));
    loot.bankAll(menu);
    verify(menu).setForceLeftClick(true);
    config.bank = false;
    clearInvocations(menu);
    loot.bankAll(menu);
    verify(menu, never()).setForceLeftClick(anyBoolean());
  }

  @Test
  public void sparkleHidesOnlyItsOwnSlotAndRestoresAtRevealThreshold() {
    effect(0, SpotanimID.VFX_TFA_SMALL_CHEST_SPARKLES);
    effect(1, 999);
    loot.update(lootRoom(5780));
    assertEquals(0, ownChest.get());
    verify(chest).removeSpotAnim(0);
    assertTrue(effects.containsKey(1L));
    verify(chest, never()).clearSpotAnims();
    loot.update(lootRoom(5791));
    assertEquals(7, ownChest.get());
    verify(chest).createSpotAnim(0, SpotanimID.VFX_TFA_SMALL_CHEST_SPARKLES, 12, 0);
    loot.update(lootRoom(5780));
    verify(chest, times(1)).removeSpotAnim(0);
  }

  @Test
  public void disablingHiddenLootRestoresTheChest() {
    effect(0, SpotanimID.VFX_TFA_SMALL_CHEST_SPARKLES);
    loot.update(lootRoom(5780));
    config.hide = false;
    loot.update(lootRoom(5780));
    assertEquals(7, ownChest.get());
    verify(chest).createSpotAnim(0, SpotanimID.VFX_TFA_SMALL_CHEST_SPARKLES, 12, 0);
  }

  @Test
  public void disabledSettingNeverTouchesChestState() {
    config.hide = false;
    effect(0, SpotanimID.VFX_TFA_SMALL_CHEST_SPARKLES);
    loot.update(lootRoom(5780));
    verify(chest, never()).removeSpotAnim(anyInt());
    verify(client, never()).setVarbit(anyInt(), anyInt());
  }

  @Test
  public void latestServerChestValueIsRestored() {
    loot.update(lootRoom(5780));
    ownChest.set(9);
    loot.ownChestChanged();
    assertEquals(0, ownChest.get());
    loot.update(lootRoom(5791));
    assertEquals(9, ownChest.get());
  }

  @Test
  public void logoutAndDifferentViewsNeverRestoreStaleState() {
    effect(0, SpotanimID.VFX_TFA_SMALL_CHEST_SPARKLES);
    loot.update(lootRoom(5780));
    loot.discard();
    ownChest.set(0);
    loot.shutdown();
    assertEquals(0, ownChest.get());
    verify(chest, never()).createSpotAnim(anyInt(), anyInt(), anyInt(), anyInt());
  }

  @Test
  public void replacementEffectsAreNeverOverwritten() {
    effect(0, SpotanimID.VFX_TFA_SMALL_CHEST_SPARKLES);
    loot.update(lootRoom(5780));
    effect(0, 888);
    loot.shutdown();
    verify(chest, never()).createSpotAnim(anyInt(), anyInt(), anyInt(), anyInt());
    assertEquals(7, ownChest.get());
  }
}
