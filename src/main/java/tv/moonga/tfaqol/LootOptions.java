package tv.moonga.tfaqol;

import java.util.*;
import javax.inject.Inject;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.SpotanimID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.util.Text;

/**
 * TFA-scoped loot QoL. Adapted from LlemonDuck's The Fractured Archive loot features. Copyright (c)
 * 2026, LlemonDuck. BSD-2-Clause notice is bundled under licenses/.
 */
final class LootOptions {
  private static final int LOBBY_REGION = 16470, LOOT_REGION = 16474, REVEAL_Y = 5791;
  @Inject private Client client;
  @Inject private TfaConfig config;
  private final Map<NPC, Map<Integer, Integer>> sparkles = new IdentityHashMap<>();
  private WorldView hiddenView;
  private int region = -1, delayedOwnChest;
  private boolean hiding, revealed;

  void tick() {
    if (client.getGameState() == GameState.LOGGED_IN) update(location());
  }

  private WorldPoint location() {
    Player player = client.getLocalPlayer();
    if (client.getGameState() != GameState.LOGGED_IN
        || player == null
        || player.getLocalLocation() == null) return null;
    return WorldPoint.fromLocalInstance(client, player.getLocalLocation());
  }

  void update(WorldPoint location) {
    WorldView current = client.getTopLevelWorldView();
    int next = location == null ? -1 : location.getRegionID();
    if (current != hiddenView && hiddenView != null) discard();
    if (next != LOOT_REGION) {
      // Do not write old chest state into a different room or account session.
      discard();
      region = next;
      return;
    }
    if (region != LOOT_REGION) revealed = false;
    region = next;
    if (!config.hiddenLoot() || location.getY() >= REVEAL_Y) {
      reveal();
      revealed = location.getY() >= REVEAL_Y;
      return;
    }
    if (revealed || current == null) return;
    hiddenView = current;
    hiding = true;
    delayChestSelection();
    for (NPC npc : current.npcs()) hideSparkle(npc);
  }

  void bankAll(MenuEntry entry) {
    if (config.bankAllSingleClick()
        && (region == LOBBY_REGION || region == LOOT_REGION)
        && entry != null
        && entry.getOption() != null
        && "Bank-all".equalsIgnoreCase(Text.removeTags(entry.getOption())))
      entry.setForceLeftClick(true);
  }

  void graphic(Actor actor) {
    if (actor instanceof NPC) hideSparkle((NPC) actor);
  }

  void ownChestChanged() {
    if (hiding) delayChestSelection();
  }

  private void delayChestSelection() {
    int value = client.getVarbitValue(VarbitID.TFA_VAULT_OWN_CHEST);
    if (value != 0) {
      delayedOwnChest = value;
      client.setVarbit(VarbitID.TFA_VAULT_OWN_CHEST, 0);
    }
  }

  private void hideSparkle(NPC npc) {
    if (!hiding || npc.getWorldView() != hiddenView || npc.getSpotAnims() == null) return;
    List<ActorSpotAnim> remove = new ArrayList<>();
    for (ActorSpotAnim spot : npc.getSpotAnims())
      if (spot.getId() == SpotanimID.VFX_TFA_SMALL_CHEST_SPARKLES
          && spot.getHash() >= 0
          && spot.getHash() <= Integer.MAX_VALUE) remove.add(spot);
    // Never mutate the spot-animation table while iterating it, and preserve unrelated effects.
    for (ActorSpotAnim spot : remove) {
      int slot = (int) spot.getHash();
      sparkles.computeIfAbsent(npc, n -> new HashMap<>()).putIfAbsent(slot, spot.getHeight());
      npc.removeSpotAnim(slot);
    }
  }

  private void reveal() {
    hiding = false;
    if (hiddenView != null && hiddenView == client.getTopLevelWorldView()) {
      for (Map.Entry<NPC, Map<Integer, Integer>> chest : sparkles.entrySet()) {
        NPC npc = chest.getKey();
        if (npc.getWorldView() != hiddenView || npc.isDead() || npc.getSpotAnims() == null)
          continue;
        for (Map.Entry<Integer, Integer> effect : chest.getValue().entrySet())
          if (npc.getSpotAnims().get(effect.getKey()) == null)
            npc.createSpotAnim(
                effect.getKey(), SpotanimID.VFX_TFA_SMALL_CHEST_SPARKLES, effect.getValue(), 0);
      }
      if (delayedOwnChest != 0 && client.getVarbitValue(VarbitID.TFA_VAULT_OWN_CHEST) == 0)
        client.setVarbit(VarbitID.TFA_VAULT_OWN_CHEST, delayedOwnChest);
    }
    sparkles.clear();
    delayedOwnChest = 0;
    hiddenView = null;
  }

  void shutdown() {
    reveal();
    discard();
  }

  void discard() {
    sparkles.clear();
    delayedOwnChest = 0;
    hiddenView = null;
    hiding = false;
    revealed = false;
    region = -1;
  }
}
