package tv.moonga.tfaqol;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.awt.Color;
import java.lang.reflect.Field;
import java.util.*;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.CommandExecuted;
import org.junit.*;

public class SafeTilesOverlayTest {
  private Client client;
  private WorldView view;
  private SafeTilesOverlay overlay;
  private Tile[][][] scene;

  @Before
  @SuppressWarnings({"rawtypes", "unchecked"})
  public void setup() throws Exception {
    client = mock(Client.class);
    view = mock(WorldView.class);
    Scene s = mock(Scene.class);
    scene = new Tile[1][104][104];
    when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
    when(client.getTopLevelWorldView()).thenReturn(view);
    when(view.getScene()).thenReturn(s);
    when(s.getTiles()).thenReturn(scene);
    IndexedObjectSet npcs = mock(IndexedObjectSet.class);
    when(npcs.iterator()).thenAnswer(i -> Collections.emptyIterator());
    when(view.npcs()).thenReturn(npcs);
    overlay = new SafeTilesOverlay();
    PluginGateTest.inject(overlay, "client", client);
  }

  @SuppressWarnings("unchecked")
  private Map<Integer, Color> tiles() throws Exception {
    Field f = SafeTilesOverlay.class.getDeclaredField("tiles");
    f.setAccessible(true);
    return (Map<Integer, Color>) f.get(overlay);
  }

  private GameObject object(int id, int x, int y) {
    GameObject o = mock(GameObject.class);
    when(o.getId()).thenReturn(id);
    when(o.getWorldView()).thenReturn(view);
    when(o.getWorldLocation()).thenReturn(new WorldPoint(x, y, 0));
    when(o.getSceneMinLocation()).thenReturn(new net.runelite.api.Point(x, y));
    when(o.getSceneMaxLocation()).thenReturn(new net.runelite.api.Point(x, y));
    Tile t = mock(Tile.class);
    when(t.getGameObjects()).thenReturn(new GameObject[] {o});
    scene[0][x][y] = t;
    return o;
  }

  @Test
  public void vineIsExactlyNineTilesAndMushroomIsOrangeFiveByFive() throws Exception {
    overlay.command(new CommandExecuted("safetiles", new String[] {SafeTileMode.GUARDIAN.command}));
    NPC vine = mock(NPC.class);
    when(vine.getId()).thenReturn(16664);
    when(vine.getWorldLocation()).thenReturn(new WorldPoint(20, 20, 0));
    when(view.npcs().iterator()).thenAnswer(i -> Collections.singletonList(vine).iterator());
    object(62580, 40, 40);
    overlay.refresh();
    assertEquals(34, tiles().size());
    assertEquals(9, tiles().values().stream().filter(c -> c.getGreen() == 35).count());
    assertEquals(25, tiles().values().stream().filter(c -> c.getGreen() == 150).count());
  }

  @Test
  public void portalModeMarksOnlyMyQuadrantAndLeavesFourSafeTilesClear() throws Exception {
    overlay.command(
        new CommandExecuted("safetiles", new String[] {SafeTileMode.OCELLUS_P2.command}));
    Player self = mock(Player.class);
    when(self.getWorldLocation()).thenReturn(new WorldPoint(55, 55, 0));
    when(client.getLocalPlayer()).thenReturn(self);
    for (PortalGaps.Portal p : PortalGapsTest.portals(1, 2)) object(p.id, p.x, p.y);
    overlay.refresh();
    Map<Integer, Color> marked = tiles();
    assertEquals(140, marked.size());
    for (int x = 53; x <= 54; x++)
      for (int y = 55; y <= 56; y++) assertFalse(marked.containsKey(x * 104 + y));
    for (int key : marked.keySet()) {
      assertTrue(key / 104 > 50);
      assertTrue(key % 104 > 50);
    }
    scene[0][39][50] =
        null; // The observed active portal layout can be replaced with an incomplete snapshot.
    scene[0][38][50] = null;
    overlay.refresh();
    assertTrue(tiles().isEmpty());
  }

  @Test
  public void persistentHazardsDisappearWhenTheirObjectDoes() throws Exception {
    overlay.command(new CommandExecuted("safetiles", new String[] {SafeTileMode.JELLY.command}));
    object(62609, 20, 20);
    overlay.refresh();
    assertEquals(1, tiles().size());
    scene[0][20][20] = null;
    overlay.refresh();
    assertTrue(tiles().isEmpty());
  }

  @Test
  public void onlyTheRequestedMechanicIdsAreHighlighted() {
    assertTrue(SafeTilesOverlay.graphicHazard(SafeTileMode.BORK, 4395));
    assertFalse(SafeTilesOverlay.graphicHazard(SafeTileMode.XARTH, 4395));
    assertTrue(SafeTilesOverlay.graphicHazard(SafeTileMode.OCELLUS_P1, 4236));
    assertFalse(SafeTilesOverlay.graphicHazard(SafeTileMode.OCELLUS_P1, 4260));
    assertTrue(SafeTilesOverlay.objectHazard(SafeTileMode.OCELLUS_P3, 62701));
    assertTrue(SafeTilesOverlay.objectHazard(SafeTileMode.OCELLUS_P3, 62703));
    assertTrue(SafeTilesOverlay.objectHazard(SafeTileMode.OCELLUS_P3, 62704));
    assertTrue(SafeTilesOverlay.objectHazard(SafeTileMode.OCELLUS_P3, 62705));
    assertFalse(SafeTilesOverlay.objectHazard(SafeTileMode.OCELLUS_P3, 62706));
  }

  @Test
  public void commandsAreSessionOnlyAndInvalidCommandsPreserveTheSelection() throws Exception {
    object(62609, 20, 20);
    overlay.command(new CommandExecuted("safetiles", new String[] {"3"}));
    assertEquals(1, tiles().size());
    overlay.command(new CommandExecuted("safetiles", new String[] {"bad"}));
    assertEquals(1, tiles().size());
    overlay.command(new CommandExecuted("safetiles", new String[] {"off"}));
    assertTrue(tiles().isEmpty());
    overlay.command(new CommandExecuted("safetiles", new String[] {"3"}));
    overlay.reset();
    overlay.refresh();
    assertTrue(tiles().isEmpty());
    assertNull(SafeTileMode.parse("bad"));
  }

  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  public void graphicsFollowVisibleLifetimeAndLoadingClearsHighlights() throws Exception {
    overlay.command(new CommandExecuted("safetiles", new String[] {SafeTileMode.BORK.command}));
    GraphicsObject gfx = mock(GraphicsObject.class);
    when(gfx.getWorldView()).thenReturn(view);
    when(gfx.getId()).thenReturn(4395);
    net.runelite.api.coords.LocalPoint location =
        net.runelite.api.coords.LocalPoint.fromScene(20, 20, view);
    when(gfx.getLocation()).thenReturn(location);
    net.runelite.api.Deque graphics = mock(net.runelite.api.Deque.class);
    when(graphics.iterator()).thenAnswer(i -> Collections.singletonList(gfx).iterator());
    when(view.getGraphicsObjects()).thenReturn(graphics);
    overlay.refresh();
    assertEquals(1, tiles().size());
    when(gfx.finished()).thenReturn(true);
    overlay.frame();
    assertTrue(tiles().isEmpty());
    when(gfx.finished()).thenReturn(false);
    overlay.frame();
    assertEquals(1, tiles().size());
    when(client.getGameState()).thenReturn(GameState.LOADING);
    overlay.frame();
    assertTrue(tiles().isEmpty());
  }

  @Test
  public void quadrantChangesDiscardThePreviousQuadrantsTiles() throws Exception {
    overlay.command(
        new CommandExecuted("safetiles", new String[] {SafeTileMode.OCELLUS_P2.command}));
    Player self = mock(Player.class);
    when(self.getWorldLocation()).thenReturn(new WorldPoint(55, 55, 0));
    when(client.getLocalPlayer()).thenReturn(self);
    for (PortalGaps.Portal p : PortalGapsTest.portals(1, 2)) object(p.id, p.x, p.y);
    overlay.refresh();
    assertEquals(140, tiles().size());
    when(self.getWorldLocation()).thenReturn(new WorldPoint(45, 45, 0));
    overlay.frame();
    assertEquals(140, tiles().size());
    for (int key : tiles().keySet()) {
      assertTrue(key / 104 < 50);
      assertTrue(key % 104 < 50);
    }
  }

  @Test
  public void allInsolenceVariantsDisappearOnDespawnAndStayOutOfOtherModes() throws Exception {
    for (int id = 62703; id <= 62705; id++) object(id, 20 + id - 62703, 20);
    overlay.command(new CommandExecuted("safetiles", new String[] {"p3"}));
    assertEquals(3, tiles().size());
    scene[0][21][20] = null;
    overlay.invalidate();
    overlay.frame();
    assertEquals(2, tiles().size());
    overlay.command(new CommandExecuted("safetiles", new String[] {"p1"}));
    assertTrue(tiles().isEmpty());
    overlay.command(new CommandExecuted("safetiles", new String[] {"p3"}));
    assertEquals(2, tiles().size());
    overlay.command(new CommandExecuted("safetiles", new String[] {"off"}));
    assertTrue(tiles().isEmpty());
  }
}
