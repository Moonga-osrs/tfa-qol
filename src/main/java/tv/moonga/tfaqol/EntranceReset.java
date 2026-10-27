package tv.moonga.tfaqol;

import net.runelite.api.coords.WorldPoint;

/**
 * A door click arms the reset; crossing its horizontal threshold or an entry teleport confirms it.
 */
final class EntranceReset {
  private WorldPoint origin, door;
  private int deadline, side;
  private boolean near;

  void arm(WorldPoint player, WorldPoint door, int tick) {
    if (player == null || door == null) return;
    this.origin = player;
    this.door = door;
    deadline = tick + 60;
    side = Integer.compare(player.getY(), door.getY());
    near = player.distanceTo(door) <= 12;
  }

  void cancel() {
    origin = null;
    door = null;
  }

  boolean observe(WorldPoint player, int tick) {
    if (origin == null || player == null) return false;
    if (tick > deadline) {
      cancel();
      return false;
    }
    boolean movedScene =
        near && (player.getPlane() != origin.getPlane() || player.distanceTo(origin) > 32);
    boolean crossed =
        player.getPlane() == door.getPlane()
            && Math.abs(player.getX() - door.getX()) <= 12
            && (side == 0
                ? Math.abs(player.getY() - door.getY()) >= 2
                : Integer.compare(player.getY(), door.getY()) == -side);
    near |= player.distanceTo(door) <= 12;
    if (movedScene || crossed) {
      cancel();
      return true;
    }
    return false;
  }
}
