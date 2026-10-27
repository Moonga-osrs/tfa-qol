package tv.moonga.tfaqol;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.util.Collections;
import javax.imageio.ImageIO;
import net.runelite.api.*;
import net.runelite.client.ui.overlay.Overlay;
import org.junit.Test;

public class OverlayPreviewTest {
  @Test
  public void renderActualOverlayInThreeInventoryStates() throws Exception {
    BufferedImage image = new BufferedImage(960, 340, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = image.createGraphics();
    g.setColor(new Color(24, 32, 42));
    g.fillRect(0, 0, 960, 340);
    g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
    g.setColor(Color.LIGHT_GRAY);
    g.drawString("TFA Qol - overlay preview (not a live client screenshot)", 20, 25);
    for (int i = 0; i < 3; i++) {
      TfaQolPlugin plugin = new TfaQolPlugin();
      Client client = mock(Client.class);
      WorldView w = mock(WorldView.class);
      ItemContainer inventory = mock(ItemContainer.class);
      GameObject door = mock(GameObject.class);
      PluginGateTest.inject(plugin, "client", client);
      PluginGateTest.inject(plugin, "config", new TfaConfig() {});
      PluginGateTest.inject(plugin, "doors", Collections.singletonList(door));
      when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
      when(client.getTopLevelWorldView()).thenReturn(w);
      when(client.getItemContainer(InventoryID.INVENTORY)).thenReturn(inventory);
      when(client.getVarbitValue(4070)).thenReturn(i);
      Item[] items =
          i == 0
              ? new Item[0]
              : i == 1
                  ? new Item[] {new Item(12018, 1)}
                  : new Item[] {new Item(26782, 1), new Item(11959, 100)};
      when(inventory.getItems()).thenReturn(items);
      when(door.getWorldView()).thenReturn(w);
      net.runelite.api.Point at = new net.runelite.api.Point(160 + i * 320, 190);
      when(door.getCanvasTextLocation(any(Graphics2D.class), eq(""), anyInt())).thenReturn(at);
      g.setColor(new Color(94, 77, 65));
      g.fillRoundRect(35 + i * 320, 215, 250, 60, 5, 5);
      g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
      g.setColor(new Color(230, 220, 205));
      g.drawString("Archive Door", 110 + i * 320, 250);
      Field f = TfaQolPlugin.class.getDeclaredField("overlay");
      f.setAccessible(true);
      ((Overlay) f.get(plugin)).render(g);
      verify(door).getCanvasTextLocation(any(Graphics2D.class), eq(""), anyInt());
    }
    g.dispose();
    Files.createDirectories(Path.of("preview"));
    assertTrue(ImageIO.write(image, "png", Path.of("preview/entrance.png").toFile()));
  }
}
