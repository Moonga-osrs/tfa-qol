package tv.moonga.tfaqol;

import java.util.*;
import net.runelite.api.*;

/**
 * Competing source evidence, not a claim that every hit during a crossbow animation is self-damage.
 */
final class WeaponSelfDamage {
  static String evidence(Client client, Player player, Hitsplat hit) {
    if (hit.getAmount() <= 0
        || (hit.getHitsplatType() != HitsplatID.DAMAGE_ME
            && hit.getHitsplatType() != HitsplatID.DAMAGE_OTHER)
        || player.getAnimation() != 9168) return null;
    WorldView view = player.getWorldView();
    if (view == null) return null;
    Actor target = player.getInteracting();
    Map<String, String> matches = new LinkedHashMap<>();
    if (target instanceof NPC) inspect((NPC) target, view, client.getGameCycle(), matches);
    else if (view.npcs() != null)
      for (NPC npc : view.npcs()) inspect(npc, view, client.getGameCycle(), matches);
    return matches.size() == 1
        ? "Possible Blood Forfeit self-damage: animation=9168 ZCB_ATTACK_PVN; "
            + matches.values().iterator().next()
            + ". Keep as competing evidence, not an automatic boss mistake."
        : null;
  }

  private static void inspect(NPC npc, WorldView view, int cycle, Map<String, String> matches) {
    if (npc.getWorldView() != view
        || npc.getId() < 16640
        || npc.getId() > 16688
        || npc.getSpotAnims() == null) return;
    for (ActorSpotAnim s : npc.getSpotAnims())
      if (s.getId() == 754 && Math.abs(cycle - s.getStartCycle() - 30) <= 12)
        matches.put(
            npc.getIndex() + ":" + s.getStartCycle(),
            "targetNpc="
                + npc.getId()
                + " index="
                + npc.getIndex()
                + " gfx=754 XBOWS_BLOOD_SACRIFICE_SPOT_ANIM start="
                + s.getStartCycle()
                + " ageCycles="
                + (cycle - s.getStartCycle()));
  }
}
