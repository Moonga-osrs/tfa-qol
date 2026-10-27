package tv.moonga.tfaqol;

import java.awt.*;
import java.util.*;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.events.CommandExecuted;
import net.runelite.client.ui.overlay.*;

/** Highlights observed scene hazards. Portal mode reads only the current portal objects. */
final class SafeTilesOverlay extends Overlay {
  private static final Color RED = new Color(255, 35, 35), ORANGE = new Color(255, 150, 25);
  @Inject private Client client;
  private Map<Integer, Color> objects = Collections.emptyMap(), tiles = Collections.emptyMap();
  private WorldView view;
  private int plane = -1;
  private boolean dirty;
  private int playerX = -1, playerY = -1;
  private SafeTileMode mode = SafeTileMode.OFF;

  SafeTilesOverlay() {
    setPosition(OverlayPosition.DYNAMIC);
    setLayer(OverlayLayer.ABOVE_SCENE);
  }

  void clear() {
    objects = tiles = Collections.emptyMap();
    view = null;
    plane = -1;
  }

  void reset() {
    mode = SafeTileMode.OFF;
    clear();
  }

  void command(CommandExecuted e) {
    if (!"safetiles".equalsIgnoreCase(e.getCommand())) return;
    String[] args = e.getArguments();
    SafeTileMode selected = args.length == 1 ? SafeTileMode.parse(args[0]) : null;
    if (selected == null) {
      client.addChatMessage(
          ChatMessageType.GAMEMESSAGE, "", "Usage: ::safetiles 1|2|3|4|p1|p2|p3|off", null);
      return;
    }
    mode = selected;
    refresh();
    client.addChatMessage(
        ChatMessageType.GAMEMESSAGE, "", "Safe tiles: " + selected.command + ".", null);
  }

  void invalidate() {
    dirty = true;
  }

  void refresh() {
    dirty = false;
    clear();
    if (mode == null || mode == SafeTileMode.OFF || client.getGameState() != GameState.LOGGED_IN)
      return;
    WorldView w = client.getTopLevelWorldView();
    if (w == null || w.getScene() == null) return;
    view = w;
    plane = w.getPlane();
    Map<Integer, Color> result = new HashMap<>();
    List<PortalGaps.Portal> portals = new ArrayList<>();
    Tile[][][] scene = w.getScene().getTiles();
    if (scene == null || plane < 0 || plane >= scene.length) return;
    Set<TileObject> seen = Collections.newSetFromMap(new IdentityHashMap<>());
    for (Tile[] row : scene[plane])
      if (row != null)
        for (Tile tile : row)
          if (tile != null) {
            if (tile.getGameObjects() != null)
              for (GameObject o : tile.getGameObjects())
                if (o != null && seen.add(o)) {
                  if (mode == SafeTileMode.OCELLUS_P2 && (o.getId() == 62684 || o.getId() == 62685))
                    portals.add(
                        new PortalGaps.Portal(
                            o.getWorldLocation().getX() - w.getBaseX(),
                            o.getWorldLocation().getY() - w.getBaseY(),
                            o.getId()));
                  object(result, o);
                }
            if (tile.getGroundObject() != null && seen.add(tile.getGroundObject()))
              object(result, tile.getGroundObject());
          }
    if (mode == SafeTileMode.GUARDIAN)
      for (NPC n : w.npcs())
        if (n.getId() == 16664 && !n.isDead())
          square(
              result,
              n.getWorldLocation().getX() - w.getBaseX(),
              n.getWorldLocation().getY() - w.getBaseY(),
              1,
              RED);
    if (mode == SafeTileMode.OCELLUS_P2 && client.getLocalPlayer() != null) {
      int px = client.getLocalPlayer().getWorldLocation().getX() - w.getBaseX(),
          py = client.getLocalPlayer().getWorldLocation().getY() - w.getBaseY();
      playerX = px;
      playerY = py;
      for (int[] gap : PortalGaps.current(portals)) {
        int sx = Integer.signum(gap[0] - gap[2]), sy = Integer.signum(gap[1] - gap[3]);
        if (Integer.signum(px - gap[2]) != sx || Integer.signum(py - gap[3]) != sy) continue;
        int x0 = gap[2] + (sx < 0 ? -12 : 1), y0 = gap[3] + (sy < 0 ? -12 : 1);
        for (int x = x0; x < x0 + 12; x++)
          for (int y = y0; y < y0 + 12; y++)
            if (x < gap[0] || x > gap[0] + 1 || y < gap[1] || y > gap[1] + 1)
              put(result, x, y, RED);
      }
    }
    objects = result;
    frame();
  }

  private void object(Map<Integer, Color> result, TileObject o) {
    if (o.getWorldView() != view || o.getPlane() != plane) return;
    int id = o.getId(),
        x = o.getWorldLocation().getX() - view.getBaseX(),
        y = o.getWorldLocation().getY() - view.getBaseY();
    if (mode == SafeTileMode.GUARDIAN && id >= 62578 && id <= 62580) {
      square(result, x, y, 2, ORANGE);
      return;
    }
    if (!objectHazard(mode, id)) return;
    if (id == 62701) {
      put(result, x, y, RED);
      return;
    }
    // Persistent residue objects use their actual occupied tiles, not a guessed radius.
    if (o instanceof GameObject) {
      net.runelite.api.Point min = ((GameObject) o).getSceneMinLocation(),
          max = ((GameObject) o).getSceneMaxLocation();
      if (min != null
          && max != null
          && max.getX() - min.getX() < 16
          && max.getY() - min.getY() < 16) {
        for (int a = min.getX(); a <= max.getX(); a++)
          for (int b = min.getY(); b <= max.getY(); b++) put(result, a, b, RED);
        return;
      }
    }
    put(result, x, y, RED);
  }

  static boolean objectHazard(SafeTileMode mode, int id) {
    return mode == SafeTileMode.BORK && id == 62544
        || mode == SafeTileMode.JELLY && id == 62609
        || mode == SafeTileMode.XARTH && id == 62713
        || mode == SafeTileMode.OCELLUS_P3 && (id >= 62701 && id <= 62705);
  }

  static boolean graphicHazard(SafeTileMode mode, int id) {
    return mode == SafeTileMode.BORK && id == 4395
        || mode == SafeTileMode.OCELLUS_P1 && id >= 4236 && id <= 4259
        || mode == SafeTileMode.OCELLUS_P3
            && (id >= 4299 && id <= 4302 || id >= 4346 && id <= 4348);
  }

  void frame() {
    if (dirty) {
      refresh();
      return;
    }
    if (view == null) return;
    if (mode == SafeTileMode.OCELLUS_P2 && client.getLocalPlayer() != null) {
      int x = client.getLocalPlayer().getWorldLocation().getX() - view.getBaseX(),
          y = client.getLocalPlayer().getWorldLocation().getY() - view.getBaseY();
      if (x != playerX || y != playerY) {
        refresh();
        return;
      }
    }
    if (client.getGameState() != GameState.LOGGED_IN
        || view != client.getTopLevelWorldView()
        || view.getPlane() != plane) {
      clear();
      return;
    }
    Map<Integer, Color> result = new HashMap<>(objects);
    if (view.getGraphicsObjects() != null)
      for (GraphicsObject gfx : view.getGraphicsObjects())
        if (gfx.getWorldView() == view
            && gfx.getLevel() == plane
            && !gfx.finished()
            && graphicHazard(mode, gfx.getId())
            && gfx.getLocation() != null)
          put(result, gfx.getLocation().getSceneX(), gfx.getLocation().getSceneY(), RED);
    tiles = result;
  }

  private static void square(Map<Integer, Color> result, int x, int y, int radius, Color color) {
    for (int a = x - radius; a <= x + radius; a++)
      for (int b = y - radius; b <= y + radius; b++) put(result, a, b, color);
  }

  private static void put(Map<Integer, Color> result, int x, int y, Color color) {
    if (x >= 0 && x < 104 && y >= 0 && y < 104) {
      int key = x * 104 + y;
      if (!RED.equals(result.get(key))) result.put(key, color);
    }
  }

  @Override
  public Dimension render(Graphics2D g) {
    if (view == null
        || view != client.getTopLevelWorldView()
        || view.getPlane() != plane
        || client.getGameState() != GameState.LOGGED_IN) return null;
    Color before = g.getColor();
    try {
      for (Map.Entry<Integer, Color> tile : tiles.entrySet()) {
        LocalPoint local = LocalPoint.fromScene(tile.getKey() / 104, tile.getKey() % 104, view);
        Polygon poly = Perspective.getCanvasTilePoly(client, local);
        if (poly != null) {
          Color c = tile.getValue();
          g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 65));
          g.fillPolygon(poly);
          g.setColor(c);
          g.drawPolygon(poly);
        }
      }
    } finally {
      g.setColor(before);
    }
    return null;
  }
}
