package tv.moonga.tfaqol;

import net.runelite.api.Player;
import net.runelite.client.util.Text;

/** Consistent display-name keys across the tracker and encounter observers. */
final class PlayerNames {
  private PlayerNames() {}

  static String name(Player player) {
    return Text.removeTags(player.getName() == null ? "Unknown player" : player.getName());
  }
}
