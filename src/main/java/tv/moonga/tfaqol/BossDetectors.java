package tv.moonga.tfaqol;

import java.util.*;

public final class BossDetectors {
  private static final Map<TfaBoss, BossMistakeDetector> DETECTORS = new EnumMap<>(TfaBoss.class);

  static {
    for (TfaBoss boss : TfaBoss.values()) DETECTORS.put(boss, new Pending(boss));
    DETECTORS.put(TfaBoss.EZEKIEL, new Ezekiel());
    DETECTORS.put(TfaBoss.ARCHIVE_GUARDIAN, new Guardian());
    DETECTORS.put(TfaBoss.BORK_AND_SMORKS, new Bork());
    DETECTORS.put(TfaBoss.OCELLUS, new Ocellus());
    DETECTORS.put(TfaBoss.DREADNOTH, new Dreadnoth());
    DETECTORS.put(TfaBoss.XARTH, new Xarth());
  }

  private BossDetectors() {}

  public static BossMistakeDetector forBoss(TfaBoss boss) {
    return DETECTORS.get(boss);
  }

  public static BossMistakeDetector forNpc(int id) {
    for (BossMistakeDetector d : DETECTORS.values())
      if (d.available() && d.matchesNpc(id)) return d;
    return null;
  }

  public static List<BossMistakeDetector> all() {
    return Collections.unmodifiableList(new ArrayList<>(DETECTORS.values()));
  }

  private static final class Pending implements BossMistakeDetector {
    private final TfaBoss boss;

    Pending(TfaBoss boss) {
      this.boss = boss;
    }

    public TfaBoss boss() {
      return boss;
    }

    public boolean available() {
      return false;
    }

    public boolean matchesNpc(int id) {
      return false;
    }

    public boolean startsEncounter(int animation) {
      return false;
    }

    public boolean endsEncounter(int animation) {
      return false;
    }

    public String floorMechanic(int id) {
      return null;
    }
  }

  private static final class Xarth implements BossMistakeDetector {
    public TfaBoss boss() {
      return TfaBoss.XARTH;
    }

    public boolean available() {
      return true;
    }

    public boolean matchesNpc(int id) {
      return id == 16684;
    }

    public boolean startsEncounter(int a) {
      return a == 14983 || a == 14984 || a == 14985 || a == 14989 || a == 14992;
    }

    public boolean endsEncounter(int a) {
      return a == 14995;
    }

    public String floorMechanic(int id) {
      return null;
    }
  }

  private static final class Dreadnoth implements BossMistakeDetector {
    public TfaBoss boss() {
      return TfaBoss.DREADNOTH;
    }

    public boolean available() {
      return true;
    }

    public boolean matchesNpc(int id) {
      return id == 16665;
    }

    public boolean startsEncounter(int a) {
      return a == 14777 || a == 14779 || a == 14780;
    }

    public boolean endsEncounter(int a) {
      return a == 14782;
    }

    public String floorMechanic(int id) {
      return null;
    }
  }

  private static final class Ocellus implements BossMistakeDetector {
    public TfaBoss boss() {
      return TfaBoss.OCELLUS;
    }

    public boolean available() {
      return true;
    }

    public boolean matchesNpc(int id) {
      return OcellusObserver.main(id);
    }

    public boolean startsEncounter(int a) {
      return a >= 14806 && a <= 14814 || a == 14837 || a >= 14842 && a <= 14871 || a == 14881;
    }

    public boolean endsEncounter(int a) {
      return a == 14872;
    }

    public String floorMechanic(int id) {
      return null;
    }
  }

  private static final class Bork implements BossMistakeDetector {
    public TfaBoss boss() {
      return TfaBoss.BORK_AND_SMORKS;
    }

    public boolean available() {
      return true;
    }

    public boolean matchesNpc(int id) {
      return id == 16640 || id == 16641;
    }

    public boolean startsEncounter(int a) {
      return a == 14542
          || a == 14545
          || a == 14549
          || a == 14550
          || a == 14551
          || a == 14554
          || a == 14555;
    }

    public boolean endsEncounter(int a) {
      return false;
    }

    public String floorMechanic(int id) {
      return null;
    }
  }

  private static final class Guardian implements BossMistakeDetector {
    public TfaBoss boss() {
      return TfaBoss.ARCHIVE_GUARDIAN;
    }

    public boolean available() {
      return true;
    }

    public boolean matchesNpc(int id) {
      return id >= 16658 && id <= 16660;
    }

    public boolean startsEncounter(int a) {
      return a >= 14750 && a <= 14754 || a == 14747;
    }

    public boolean endsEncounter(int a) {
      return false;
    }

    public String floorMechanic(int id) {
      return null;
    }
  }

  private static final class Ezekiel implements BossMistakeDetector {
    public TfaBoss boss() {
      return TfaBoss.EZEKIEL;
    }

    public boolean available() {
      return true;
    }

    public boolean matchesNpc(int id) {
      return id == 16652;
    }

    public boolean startsEncounter(int a) {
      return a == 14685 || a == 14687 || a == 14691 || a == 14694;
    }

    public boolean endsEncounter(int a) {
      return a == 14697;
    }

    public String floorMechanic(int id) {
      return id == 4394
          ? "Tegelzwaai"
          : id >= 4170 && id <= 4172 ? "Zwaardgolf" : id == 4197 || id == 4198 ? "Tegeldans" : null;
    }
  }
}
