package tv.moonga.tfaqol;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import com.google.gson.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import javax.swing.*;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.ui.*;
import org.junit.Test;

public class TrackerPanelTest {
  private static Object field(Object o, String name) throws Exception {
    Field f = o.getClass().getDeclaredField(name);
    f.setAccessible(true);
    return f.get(o);
  }

  private static void layout(Component c) {
    if (c instanceof Container) {
      ((Container) c).doLayout();
      for (Component child : ((Container) c).getComponents()) layout(child);
    }
  }

  @Test
  public void panelRendersAndShutdownSavesAValidLocalReport() throws Exception {
    TfaTracker t = new TfaTracker();
    Client client = mock(Client.class);
    when(client.getTickCount()).thenReturn(120);
    when(client.getGameCycle()).thenReturn(3700);
    ClientToolbar toolbar = mock(ClientToolbar.class);
    PluginGateTest.inject(t, "client", client);
    PluginGateTest.inject(t, "clientThread", mock(ClientThread.class));
    PluginGateTest.inject(t, "toolbar", toolbar);
    PluginGateTest.inject(t, "bus", mock(EventBus.class));
    PluginGateTest.inject(t, "config", new TfaConfig() {});
    PluginGateTest.inject(t, "gson", new Gson());
    Path temporary = Files.createTempDirectory("tfa-report-test-");
    SwingUtilities.invokeAndWait(
        () -> t.startup(net.runelite.client.util.Filepath.Unchecked.getRooted(temporary)));
    MistakeLedger ledger = (MistakeLedger) field(t, "ledger");
    ledger.begin(TfaBoss.ARCHIVE_GUARDIAN, 90, 2700, false);
    ledger.observation(
        "Example player", "Possible Firebreath off prayer", 14, 95, 2850, "Test fixture");
    ledger.begin(100, 3000, false);
    ledger.floor(4394, 50, 50, 0, 0, 3107);
    ledger.hit("Example player", 12, true, 50, 50, 0, 0, 103, 3100);
    ledger.drain(3130, false);
    ledger.death("Example teammate", 110, 3300);
    java.lang.reflect.Method refresh =
        TfaTracker.class.getDeclaredMethod("refreshPanel", String.class);
    refresh.setAccessible(true);
    refresh.invoke(t, "Ezekiel - tracking");
    SwingUtilities.invokeAndWait(() -> {});
    NavigationButton button = (NavigationButton) field(t, "navigation");
    PluginPanel panel = button.getPanel();
    BufferedImage image = new BufferedImage(225, 680, BufferedImage.TYPE_INT_ARGB);
    SwingUtilities.invokeAndWait(
        () -> {
          panel.setSize(225, 680);
          layout(panel);
          Graphics2D g = image.createGraphics();
          panel.printAll(g);
          g.dispose();
        });
    Files.createDirectories(Path.of("preview"));
    ImageIO.write(image, "png", Path.of("preview/mistake-tracker.png").toFile());
    SwingUtilities.invokeAndWait(t::shutdown);
    ExecutorService io = (ExecutorService) field(t, "io");
    assertTrue(io.awaitTermination(5, TimeUnit.SECONDS));
    try (Stream<Path> files = Files.list(temporary.resolve("reports"))) {
      Path report =
          files
              .filter(p -> p.toString().endsWith(".json"))
              .findFirst()
              .orElseThrow(AssertionError::new);
      JsonObject json = new JsonParser().parse(Files.readString(report)).getAsJsonObject();
      assertEquals(
          "Archive Guardian, Ezekiel, Bork and the Smorks, Dreadnoth, Xarth, Ocellus; possible"
              + " mistakes and separate damage research for testing",
          json.get("scope").getAsString());
      assertEquals(2, json.getAsJsonArray("encounters").size());
    }
    verify(toolbar).addNavigation(button);
    verify(toolbar).removeNavigation(button);
  }
}
