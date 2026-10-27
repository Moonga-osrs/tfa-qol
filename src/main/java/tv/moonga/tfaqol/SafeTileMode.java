package tv.moonga.tfaqol;

/** One manually selected encounter; no automatic phase selection. */
enum SafeTileMode {
  OFF("off"),
  GUARDIAN("1"),
  BORK("2"),
  JELLY("3"),
  XARTH("4"),
  OCELLUS_P1("p1"),
  OCELLUS_P2("p2"),
  OCELLUS_P3("p3");
  final String command;

  SafeTileMode(String command) {
    this.command = command;
  }

  static SafeTileMode parse(String value) {
    for (SafeTileMode mode : values()) if (mode.command.equalsIgnoreCase(value)) return mode;
    return null;
  }
}
