package tv.moonga.tfaqol;

import static org.junit.Assert.*;

import java.util.*;
import net.runelite.client.config.*;
import org.junit.Test;

public class QolSettingsTest {
  private EntranceState missing() {
    return EntranceState.read(new net.runelite.api.Item[0], 1);
  }

  @Test
  public void spellbookCanBeShownOnItsOwn() {
    assertEquals(Collections.singletonList("Spellbook: Ancient"), missing().lines(true, false));
  }

  @Test
  public void warningsCanBeShownWithoutSpellbook() {
    assertEquals(Collections.singletonList("Missing Chinchompas"), missing().lines(false, true));
  }

  @Test
  public void allTextOffLeavesNothingToRender() {
    assertTrue(missing().lines(false, false).isEmpty());
    assertTrue(EntranceState.read(null, 1).lines(false, false).isEmpty());
  }

  @Test
  public void hidingTextDoesNotChangeEntryRequirements() {
    EntranceState s = missing();
    s.lines(false, false);
    assertTrue(s.blocked(true));
    assertFalse(s.blocked(false));
  }

  @Test
  public void settingsSectionsAndItemsFollowRequestedOrder() throws Exception {
    ConfigSection tracker = TfaConfig.class.getField("TRACKER").getAnnotation(ConfigSection.class),
        qol = TfaConfig.class.getField("QOL").getAnnotation(ConfigSection.class);
    assertTrue(tracker.position() < qol.position());
    assertTrue(item("sidePanel").position() < item("trackOthers").position());
    String[] names = {"trackSpellbook", "trackChins", "requireChins"};
    for (int i = 0; i < names.length; i++) {
      assertEquals(TfaConfig.QOL, item(names[i]).section());
      if (i > 0) assertTrue(item(names[i - 1]).position() < item(names[i]).position());
    }
  }

  private ConfigItem item(String method) throws Exception {
    return TfaConfig.class.getMethod(method).getAnnotation(ConfigItem.class);
  }
}
