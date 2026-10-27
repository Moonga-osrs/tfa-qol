package tv.moonga.tfaqol;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import com.google.gson.Gson;
import java.lang.reflect.Field;
import java.nio.file.Files;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.ui.ClientToolbar;
import org.junit.Test;

public class SidePanelTest {
  static class Settings implements TfaConfig {
    boolean visible;

    public boolean sidePanel() {
      return visible;
    }
  }

  private TfaTracker setup(ClientToolbar toolbar, Settings settings) throws Exception {
    TfaTracker t = new TfaTracker();
    PluginGateTest.inject(t, "client", mock(Client.class));
    PluginGateTest.inject(t, "clientThread", mock(ClientThread.class));
    PluginGateTest.inject(t, "toolbar", toolbar);
    PluginGateTest.inject(t, "bus", mock(EventBus.class));
    PluginGateTest.inject(t, "config", settings);
    PluginGateTest.inject(t, "gson", new Gson());
    java.nio.file.Path temporary = Files.createTempDirectory("tfa-panel-test-");
    SwingUtilities.invokeAndWait(
        () -> t.startup(net.runelite.client.util.Filepath.Unchecked.getRooted(temporary)));
    return t;
  }

  private MistakeLedger ledger(TfaTracker t) throws Exception {
    Field f = TfaTracker.class.getDeclaredField("ledger");
    f.setAccessible(true);
    return (MistakeLedger) f.get(t);
  }

  private void change(TfaTracker t) throws Exception {
    ConfigChanged e = new ConfigChanged();
    e.setGroup("moongaTfaQol");
    e.setKey("sidePanel");
    t.onConfigChanged(e);
    SwingUtilities.invokeAndWait(() -> {});
  }

  @Test
  public void hiddenPanelStillTracksAndCanBeShownLive() throws Exception {
    Settings settings = new Settings();
    ClientToolbar toolbar = mock(ClientToolbar.class);
    TfaTracker t = setup(toolbar, settings);
    try {
      verify(toolbar, never()).addNavigation(any());
      ledger(t).begin(10, 100, false);
      ledger(t).observation("Teammate", "Possible Tegeldans", 10, 11, 130, "Test agreed category");
      settings.visible = true;
      change(t);
      verify(toolbar).addNavigation(any());
      assertTrue(ledger(t).active());
      assertEquals(1, ledger(t).current().events.size());
    } finally {
      SwingUtilities.invokeAndWait(t::shutdown);
    }
  }

  @Test
  public void hidingAnOpenPanelDoesNotStopOrResetTheEncounter() throws Exception {
    Settings settings = new Settings();
    settings.visible = true;
    ClientToolbar toolbar = mock(ClientToolbar.class);
    TfaTracker t = setup(toolbar, settings);
    try {
      ledger(t).begin(10, 100, false);
      settings.visible = false;
      change(t);
      verify(toolbar).removeNavigation(any());
      assertTrue(ledger(t).active());
      ledger(t).observation("Teammate", "Possible Tegeldans", 10, 11, 130, "Test agreed category");
      assertEquals(1, ledger(t).current().events.size());
    } finally {
      SwingUtilities.invokeAndWait(t::shutdown);
    }
  }
}
