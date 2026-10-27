package tv.moonga.tfaqol;

/** Geometry and timing are independent of damage amounts. Distances use the whole NPC footprint. */
final class GuardianRules {
  static int distance(int x, int y, int bx, int by, int size) {
    return Math.max(Math.max(bx - x, x - (bx + size - 1)), Math.max(by - y, y - (by + size - 1)));
  }

  static String physical(int animation, int age, int distance) {
    if (animation == 14752 && age == 1 && distance <= 0) return "Melee attack";
    if (animation == 14747 && age == 0 && distance == 1) return "Crash damage";
    if (animation == 14768 && age == 0 && distance == 1) return "Vine attack";
    return null;
  }

  static String mushroom(int age, int distance) {
    return age == 1 && distance >= 0 && distance <= 2
        ? (distance <= 1 ? "Big mushroom explosion" : "Small mushroom explosion")
        : null;
  }

  static boolean fireImpact(int age) {
    return age == 10;
  }
}
