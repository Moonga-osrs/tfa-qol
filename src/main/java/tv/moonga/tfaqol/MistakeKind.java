package tv.moonga.tfaqol;

/** Explicit allowlist: adding an observation does not silently add a new mistake category. */
enum MistakeKind {
  DEATH(null, "Death", "death", "Observed death during this encounter; the cause is not assigned."),
  XARTH_MAGIC(
      TfaBoss.XARTH,
      "Magic hit off prayer",
      "xarth-magic",
      "A targeted basic fireball hits without Protect from Magic at impact. Correct-prayer chip is"
          + " context."),
  XARTH_RANGE(
      TfaBoss.XARTH,
      "Ranged hit off prayer",
      "xarth-range",
      "Xarth projectile 4374 hits without Protect from Missiles at impact."),
  XARTH_MELEE(
      TfaBoss.XARTH,
      "Melee hit",
      "xarth",
      "Positive damage beside the 3x3 boss during melee animation 14983."),
  XARTH_AOE(
      TfaBoss.XARTH,
      "AoE hit",
      "xarth-aoe",
      "Positive ordinary damage at a matching 4378/4379 impact tile."),
  XARTH_FIRE(
      TfaBoss.XARTH,
      "Fire ground damage",
      "xarth-fire",
      "Positive ordinary damage on active fire 62713 with no competing source. BURN status ticks"
          + " never count."),
  XARTH_EXPLOSION(
      TfaBoss.XARTH,
      "Xarth explosion",
      "xarth",
      "Positive damage three ticks after Xarth animation 14989. Competing damage sources remain"
          + " unresolved."),
  XARTH_CHARGED(
      TfaBoss.XARTH,
      "Xarth charged",
      "xarth-corpse",
      "A corpse performs animation 15005 targeting Xarth. Group observation; individual"
          + " responsibility is unknown."),

  DREAD_MELEE(
      TfaBoss.DREADNOTH,
      "Boss melee hit",
      "dreadnoth",
      "Positive damage next to the 5x5 boss during melee animation 14779."),
  DREAD_STOMP(
      TfaBoss.DREADNOTH,
      "Stomp off prayer",
      "dread-landing",
      "Stomp 14777 impact one tick later without Protect from Melee."),
  DREAD_RANGE(
      TfaBoss.DREADNOTH,
      "Ranged hit off prayer",
      "dread-range",
      "One of the three 4211 projectiles hits without Protect from Missiles at impact."),
  DREAD_JELLY(
      TfaBoss.DREADNOTH,
      "Jelly melee off prayer",
      "dread-jelly",
      "Jelly 16666 attack 1586 hits its target at the animation start without Protect from Melee."),
  DREAD_BLOOD(
      TfaBoss.DREADNOTH,
      "Blood tile damage",
      "dread-blood",
      "Positive damage on an active blood tile, object 62609."),
  DREAD_LANDING(
      TfaBoss.DREADNOTH,
      "Enrage landing off prayer",
      "dread-landing",
      "Landing three ticks after jump 14780 without Protect from Melee."),
  DREAD_UNDER(
      TfaBoss.DREADNOTH,
      "Under boss on landing",
      "dreadnoth",
      "Inside the actual 5x5 boss footprint at enrage landing impact."),
  DREAD_ENRAGE_RANGE(
      TfaBoss.DREADNOTH,
      "Enrage ranged off prayer",
      "dread-range",
      "Enrage 4211 projectile hits without Protect from Missiles at impact."),
  DREAD_ENRAGE_BLOOD(
      TfaBoss.DREADNOTH,
      "Enrage blood hit",
      "dread-blood-shot",
      "Blood projectile 4212 hits the tile the player still occupies."),

  OC_CRACKS(
      TfaBoss.OCELLUS,
      "Floor cracks",
      "oc-cracks",
      "Positive damage with one matching Ocellus hazard family; provisional correlation, not a"
          + " confirmed source."),
  OC_EXPLOSION(
      TfaBoss.OCELLUS,
      "Charge explosion",
      "oc-explosion",
      "Positive damage with one matching Ocellus hazard family; provisional correlation, not a"
          + " confirmed source."),
  OC_PORTAL(
      TfaBoss.OCELLUS,
      "Portal projectile",
      "oc-portal",
      "Positive damage with one matching Ocellus hazard family; provisional correlation, not a"
          + " confirmed source."),
  OC_DODGE(
      TfaBoss.OCELLUS,
      "AoE dodge",
      "oc-portal",
      "Positive damage with one matching Ocellus hazard family; provisional correlation, not a"
          + " confirmed source."),
  OC_BOMB(
      TfaBoss.OCELLUS,
      "Quadrant bomb",
      "oc-bomb",
      "Positive damage with one matching Ocellus hazard family; provisional correlation, not a"
          + " confirmed source."),
  OC_RESIDUE(
      TfaBoss.OCELLUS,
      "Shadow residue",
      "oc-shadow",
      "Positive damage with one matching Ocellus hazard family; provisional correlation, not a"
          + " confirmed source."),
  OC_SLASH(
      TfaBoss.OCELLUS,
      "Shadow slash",
      "oc-shadow",
      "Positive damage with one matching Ocellus hazard family; provisional correlation, not a"
          + " confirmed source."),
  OC_SUPERIOR(
      TfaBoss.OCELLUS,
      "Superior shadow slash",
      "oc-shadow",
      "Positive damage with one matching Ocellus hazard family; provisional correlation, not a"
          + " confirmed source."),
  OC_DEBRIS(
      TfaBoss.OCELLUS,
      "Falling debris",
      "oc-debris",
      "Positive damage with one matching Ocellus hazard family; provisional correlation, not a"
          + " confirmed source."),
  OC_CHAIN(
      TfaBoss.OCELLUS,
      "Void chain",
      "oc-chain",
      "Positive damage with one matching Ocellus hazard family; provisional correlation, not a"
          + " confirmed source."),
  OC_UNLEASH(
      TfaBoss.OCELLUS,
      "Unleash",
      "ocellus",
      "Positive damage with one matching Ocellus hazard family; provisional correlation, not a"
          + " confirmed source."),

  BORK_TANK(
      TfaBoss.BORK_AND_SMORKS,
      "Tank damage",
      "bork",
      "Damage from the two quick floor attacks on Bork's target."),
  BORK_FLOOR(
      TfaBoss.BORK_AND_SMORKS,
      "Bork attack",
      "bork-floor",
      "Damage on Bork's ground GFX 4395 outside the identified tank hits."),
  BORK_RANGE(
      TfaBoss.BORK_AND_SMORKS,
      "Ranged hit off prayer",
      "smorks-range",
      "Smorks projectile 4056 hits without Protect from Missiles at impact."),
  BORK_MAGIC(
      TfaBoss.BORK_AND_SMORKS,
      "Magic hit off prayer",
      "smorks-magic",
      "Smorks projectile 4057 hits without Protect from Magic at impact."),
  BORK_FIRE(
      TfaBoss.BORK_AND_SMORKS,
      "Flame ground damage",
      "flame-ground",
      "Damage while standing on flame ball residue, object 62544."),
  BORK_BOMB(
      TfaBoss.BORK_AND_SMORKS,
      "Bomb missed",
      "bomb",
      "Bomb 4440 misses Bork and no stun follows; shared mistake for participants."),
  MELEE(
      TfaBoss.ARCHIVE_GUARDIAN,
      "Melee attack",
      "guardian",
      "Standing under the Guardian during the hard stomp."),
  CRASH(
      TfaBoss.ARCHIVE_GUARDIAN,
      "Crash damage",
      "crash",
      "Standing next to the Guardian when its shield drops and it falls."),
  SMALL_MUSHROOM(
      TfaBoss.ARCHIVE_GUARDIAN,
      "Small mushroom explosion",
      "mushroom",
      "Inside the outer mushroom explosion radius."),
  BIG_MUSHROOM(
      TfaBoss.ARCHIVE_GUARDIAN,
      "Big mushroom explosion",
      "mushroom",
      "Directly beside an exploding mushroom."),
  VINE(TfaBoss.ARCHIVE_GUARDIAN, "Vine attack", "vine", "Hit by a Vine's attack."),
  RANGED_PRAYER(
      TfaBoss.ARCHIVE_GUARDIAN,
      "Taking hit off prayer",
      "ranged",
      "Protect from Missiles absent at the ranged attack start."),
  FIRE_PRAYER(
      TfaBoss.ARCHIVE_GUARDIAN,
      "Firebreath off prayer",
      "firebreath",
      "Firebreath hit without Protect from Magic. Timing is provisional."),
  FIRE_MISSED(
      TfaBoss.ARCHIVE_GUARDIAN,
      "Firebreath missed",
      "firebreath",
      "An engaged participant misses the shared firebreath hit."),
  TEGELZWAAI(
      TfaBoss.EZEKIEL, "Tegelzwaai", "tegelzwaai", "Damage on a matching ground attack tile."),
  ZWAARDGOLF(
      TfaBoss.EZEKIEL, "Zwaardgolf", "zwaardgolf", "Damage on a matching sword attack tile."),
  TEGELDANS(TfaBoss.EZEKIEL, "Tegeldans", "tegeldans", "Damage on a matching tile dance effect.");
  final TfaBoss boss;
  final String label, icon, description;

  MistakeKind(TfaBoss boss, String label, String icon, String description) {
    this.boss = boss;
    this.label = label;
    this.icon = icon;
    this.description = description;
  }

  static MistakeKind find(TfaBoss boss, String type) {
    if (boss == null) return null;
    if ("Death".equals(type)) return DEATH;
    for (MistakeKind k : values())
      if (k.boss == boss && ("Possible " + k.label).equals(type)) return k;
    return null;
  }
}
