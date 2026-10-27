package tv.moonga.tfaqol;

import com.google.inject.Provides;
import java.awt.*;
import java.util.*;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.*;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.*;

@PluginDescriptor(
    name = "TFA Qol & mistake tracker",
    description = "Archive Door reminders, entry requirements and a local TFA mistake tracker",
    enabledByDefault = true)
public class TfaQolPlugin extends Plugin {
  @Inject private Client client;
  @Inject private ClientThread clientThread;
  @Inject private OverlayManager overlays;
  @Inject private TfaConfig config;
  @Inject private TfaTracker tracker;
  @Inject private SafeTilesOverlay safeTiles;
  @Inject private LootOptions loot;
  private final Overlay overlay = new EntranceOverlay();
  private final EntranceReset entranceReset = new EntranceReset();
  private List<GameObject> doors = Collections.emptyList();

  @Provides
  TfaConfig provideConfig(ConfigManager manager) {
    return manager.getConfig(TfaConfig.class);
  }

  @Override
  protected void startUp() throws Exception {
    overlays.add(overlay);
    overlays.add(safeTiles);
    tracker.startup(getPluginDirectory());
    clientThread.invokeLater(
        () -> {
          refreshDoors();
          safeTiles.refresh();
          loot.tick();
        });
  }

  @Override
  protected void shutDown() {
    entranceReset.cancel();
    loot.shutdown();
    tracker.shutdown();
    overlays.remove(overlay);
    overlays.remove(safeTiles);
    safeTiles.reset();
    doors = Collections.emptyList();
  }

  @Subscribe
  public void onGraphicChanged(GraphicChanged e) {
    loot.graphic(e.getActor());
  }

  @Subscribe
  public void onVarbitChanged(VarbitChanged e) {
    if (e.getVarbitId() == net.runelite.api.gameval.VarbitID.TFA_VAULT_OWN_CHEST)
      loot.ownChestChanged();
  }

  @Subscribe
  public void onLootConfigChanged(net.runelite.client.events.ConfigChanged e) {
    if ("moongaTfaQol".equals(e.getGroup()) && "hiddenLoot".equals(e.getKey()))
      clientThread.invokeLater(loot::tick);
  }

  @Subscribe
  public void onCommandExecuted(CommandExecuted e) {
    safeTiles.command(e);
  }

  @Subscribe
  public void onGroundObjectSpawned(GroundObjectSpawned e) {
    safeTiles.invalidate();
  }

  @Subscribe
  public void onGroundObjectDespawned(GroundObjectDespawned e) {
    safeTiles.invalidate();
  }

  EntranceState state() {
    ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
    return EntranceState.read(
        inventory == null ? null : inventory.getItems(),
        client.getVarbitValue(EntranceState.SPELLBOOK_VARBIT));
  }

  private boolean blocked() {
    return client.getGameState() == GameState.LOGGED_IN && state().blocked(config.requireChins());
  }

  private void updateMenu(MenuEntry[] entries) {
    if (entries == null) return;
    boolean restricted = blocked();
    for (MenuEntry entry : entries)
      if (EntranceMenu.isEnter(entry)) {
        entry.setDeprioritized(restricted);
        if (restricted) entry.setForceLeftClick(false);
      }
  }

  @Subscribe(priority = -1000)
  public void onMenuEntryAdded(MenuEntryAdded e) {
    updateMenu(new MenuEntry[] {e.getMenuEntry()});
    loot.bankAll(e.getMenuEntry());
  }

  @Subscribe(priority = -1000)
  public void onPostMenuSort(PostMenuSort e) {
    updateMenu(client.getMenuEntries());
  }

  @Subscribe(priority = -1000)
  public void onMenuOpened(MenuOpened e) {
    updateMenu(e.getMenuEntries());
  }

  @Subscribe(priority = -1000)
  public void onMenuShouldLeftClick(MenuShouldLeftClick e) {
    if (!blocked()) return;
    MenuEntry[] entries = client.getMenuEntries();
    if (entries != null && entries.length > 0 && EntranceMenu.isEnter(entries[entries.length - 1]))
      e.setForceRightClick(true);
  }

  @Subscribe(priority = -10000)
  public void onMenuOptionClicked(MenuOptionClicked e) {
    if (e.isConsumed()
        || client.getGameState() != GameState.LOGGED_IN
        || !EntranceMenu.isEnter(e.getMenuEntry())) return;
    // Explicit menu selection remains allowed, even when required items are missing.
    WorldView w = client.getTopLevelWorldView();
    Player p = client.getLocalPlayer();
    MenuEntry entry = e.getMenuEntry();
    if (w != null
        && p != null
        && (entry.getWorldViewId() < 0 || entry.getWorldViewId() == w.getId()))
      entranceReset.arm(
          p.getWorldLocation(),
          new net.runelite.api.coords.WorldPoint(
              w.getBaseX() + entry.getParam0(), w.getBaseY() + entry.getParam1(), w.getPlane()),
          client.getTickCount());
  }

  @Subscribe
  public void onClientTick(ClientTick e) {
    safeTiles.frame();
    if (client.isMenuOpen()) updateMenu(client.getMenuEntries());
  }

  @Subscribe(priority = 1000)
  public void onGameTick(GameTick e) {
    loot.tick();
    safeTiles.refresh();
    if (client.getGameState() == GameState.LOGGED_IN) {
      Player p = client.getLocalPlayer();
      if (p != null && p.isDead()) entranceReset.cancel();
      if (p != null
          && !p.isDead()
          && entranceReset.observe(p.getWorldLocation(), client.getTickCount())
          && tracker != null) tracker.archiveEntered();
    }
    refreshDoors();
  }

  @Subscribe(priority = 1000)
  public void onActorDeath(ActorDeath e) {
    if (e.getActor() == client.getLocalPlayer()) entranceReset.cancel();
  }

  @Subscribe
  public void onGameStateChanged(GameStateChanged e) {
    if (e.getGameState() == GameState.LOGIN_SCREEN
        || e.getGameState() == GameState.HOPPING
        || e.getGameState() == GameState.CONNECTION_LOST) loot.discard();
    if (e.getGameState() != GameState.LOGGED_IN) safeTiles.clear();
    if (e.getGameState() != GameState.LOGGED_IN) doors = Collections.emptyList();
    if (e.getGameState() == GameState.LOGIN_SCREEN
        || e.getGameState() == GameState.HOPPING
        || e.getGameState() == GameState.CONNECTION_LOST) entranceReset.cancel();
  }

  @Subscribe
  public void onGameObjectSpawned(GameObjectSpawned e) {
    safeTiles.invalidate();
    GameObject object = e.getGameObject();
    if (object.getId() != EntranceState.DOOR_ID) return;
    List<GameObject> updated = new ArrayList<>(doors);
    if (!updated.contains(object)) {
      updated.add(object);
      doors = updated;
    }
  }

  @Subscribe
  public void onGameObjectDespawned(GameObjectDespawned e) {
    safeTiles.invalidate();
    if (doors.contains(e.getGameObject())) {
      List<GameObject> updated = new ArrayList<>(doors);
      updated.remove(e.getGameObject());
      doors = updated;
    }
  }

  private void refreshDoors() {
    WorldView w = client.getTopLevelWorldView();
    if (w == null || client.getGameState() != GameState.LOGGED_IN || w.getScene() == null) {
      doors = Collections.emptyList();
      return;
    }
    Tile[][][] tiles = w.getScene().getTiles();
    int plane = w.getPlane();
    if (tiles == null || plane < 0 || plane >= tiles.length) {
      doors = Collections.emptyList();
      return;
    }
    Set<GameObject> seen = Collections.newSetFromMap(new IdentityHashMap<>());
    List<GameObject> found = new ArrayList<>();
    for (Tile[] row : tiles[plane])
      if (row != null)
        for (Tile tile : row)
          if (tile != null && tile.getGameObjects() != null)
            for (GameObject object : tile.getGameObjects())
              if (object != null && object.getId() == EntranceState.DOOR_ID && seen.add(object))
                found.add(object);
    doors = found;
  }

  private final class EntranceOverlay extends Overlay {
    EntranceOverlay() {
      setPosition(OverlayPosition.DYNAMIC);
      setLayer(OverlayLayer.ABOVE_SCENE);
      setPriority(OverlayPriority.HIGH);
    }

    @Override
    public Dimension render(Graphics2D g) {
      if (client.getGameState() != GameState.LOGGED_IN) return null;
      WorldView w = client.getTopLevelWorldView();
      if (w == null) return null;
      EntranceState current = state();
      List<String> lines = current.lines(config.trackSpellbook(), config.trackChins());
      if (lines.isEmpty()) return null;
      Font old = g.getFont();
      g.setFont(FontManager.getRunescapeBoldFont().deriveFont(16f));
      try {
        int spacing = g.getFontMetrics().getHeight() + 3, width = 0;
        for (String line : lines) width = Math.max(width, g.getFontMetrics().stringWidth(line));
        List<net.runelite.api.Point> anchors = new ArrayList<>();
        for (GameObject door : doors)
          if (door.getWorldView() == w && door.getPlane() == w.getPlane()) {
            net.runelite.api.Point p = door.getCanvasTextLocation(g, "", 180);
            if (p != null) anchors.add(p);
          }
        for (net.runelite.api.Point at : anchors) {
          int top = at.getY() - (lines.size() - 1) * spacing - g.getFontMetrics().getAscent();
          g.setColor(new Color(12, 16, 22, 170));
          g.fillRoundRect(
              at.getX() - width / 2 - 7, top - 4, width + 14, lines.size() * spacing + 5, 8, 8);
          for (int i = 0; i < lines.size(); i++) {
            String text = lines.get(i);
            int x = at.getX() - g.getFontMetrics().stringWidth(text) / 2,
                y = at.getY() - (lines.size() - 1 - i) * spacing;
            g.setColor(Color.BLACK);
            g.drawString(text, x + 1, y + 1);
            g.setColor(
                text.startsWith("Spellbook:")
                    ? new Color(155, 230, 255)
                    : current.loaded ? new Color(255, 105, 100) : new Color(255, 190, 80));
            g.drawString(text, x, y);
          }
        }
      } finally {
        g.setFont(old);
      }
      return null;
    }
  }
}
