package tv.moonga.tfaqol;

import net.runelite.client.config.*;

@ConfigGroup("moongaTfaQol")
public interface TfaConfig extends Config {
  @ConfigSection(
      name = "Loot",
      description = "TFA loot-room options",
      position = 15,
      closedByDefault = true)
  String LOOT = "loot";

  @ConfigItem(
      keyName = "bankAllSingleClick",
      name = "Bank-all single click",
      description = "Use Bank-all with one click in the TFA lobby and loot room.",
      position = 0,
      section = LOOT)
  default boolean bankAllSingleClick() {
    return true;
  }

  @ConfigItem(
      keyName = "hiddenLoot",
      name = "Hidden loot",
      description = "Hide the sparkle on your own chests.",
      position = 1,
      section = LOOT)
  default boolean hiddenLoot() {
    return false;
  }

  @ConfigSection(
      name = "Mistake tracker",
      description = "TFA observations for yourself and visible teammates",
      position = 0,
      closedByDefault = false)
  String TRACKER = "tracker";

  @ConfigItem(
      keyName = "sidePanel",
      name = "Side panel",
      description =
          "Show or hide the side panel. Tracking and local reports continue while hidden.",
      position = 0,
      section = TRACKER)
  default boolean sidePanel() {
    return true;
  }

  @ConfigItem(
      keyName = "trackOthers",
      name = "Track others mistakes",
      description =
          "Also observe visible players inside the TFA combat floor. No public chat is sent.",
      position = 1,
      section = TRACKER)
  default boolean trackOthers() {
    return true;
  }

  @ConfigItem(
      keyName = "trackBorkTank",
      name = "Track Bork tank",
      description = "Track identified Bork tank damage. Other Bork mechanics remain enabled.",
      position = 2,
      section = TRACKER)
  default boolean trackBorkTank() {
    return true;
  }

  @ConfigItem(
      keyName = "trackOcellusFloorCracks",
      name = "Track Ocellus floor cracks",
      description =
          "Track P1 floor-crack damage. Other Ocellus mechanics and tile highlights remain"
              + " enabled.",
      position = 3,
      section = TRACKER)
  default boolean trackOcellusFloorCracks() {
    return true;
  }

  @ConfigSection(
      name = "Advanced",
      description = "Optional overall tracker switch",
      position = 40,
      closedByDefault = true)
  String ADVANCED = "advanced";

  @ConfigItem(
      keyName = "mistakeTracker",
      name = "Enable mistake tracker",
      description = "Enable detection and local reports; independent of side-panel visibility",
      position = 0,
      section = ADVANCED)
  default boolean mistakeTracker() {
    return true;
  }

  @ConfigSection(
      name = "QoL settings",
      description = "Entrance reminders and independent inventory requirements",
      position = 10,
      closedByDefault = false)
  String QOL = "qol";

  @ConfigItem(
      keyName = "trackSpellbook",
      name = "Track spellbook",
      description = "Show the Spellbook text above the chosen entrance anchor",
      position = 0,
      section = QOL)
  default boolean trackSpellbook() {
    return true;
  }

  @ConfigItem(
      keyName = "trackChins",
      name = "Track chins",
      description =
          "Show Missing Chinchompas when none are in inventory; does not control entry blocking",
      position = 1,
      section = QOL)
  default boolean trackChins() {
    return true;
  }

  @ConfigItem(
      keyName = "requireChins",
      name = "Right-click without chins",
      description =
          "Require a menu selection for Enter when grey, red or black chinchompas are missing from"
              + " inventory. Right-click Enter remains allowed.",
      position = 2,
      section = QOL)
  default boolean requireChins() {
    return false;
  }
}
