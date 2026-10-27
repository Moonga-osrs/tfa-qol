package tv.moonga.tfaqol;

public enum TfaBoss {
  ARCHIVE_GUARDIAN("Archive Guardian"),
  EZEKIEL("Ezekiel"),
  BORK_AND_SMORKS("Bork and the Smorks"),
  MOIA("Moia"),
  DREADNOTH("Dreadnoth the Jellied"),
  DAGONHAI("Dagon'hai Elder and monks"),
  XARTH("Xarth"),
  OCELLUS("Ocellus");
  public final String label;

  TfaBoss(String label) {
    this.label = label;
  }

  @Override
  public String toString() {
    return label;
  }
}
