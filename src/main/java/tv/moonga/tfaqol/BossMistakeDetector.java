package tv.moonga.tfaqol;

/** Each room gets its own verified NPC IDs, lifecycle and evidence rules here. */
public interface BossMistakeDetector {
  TfaBoss boss();

  boolean available();

  boolean matchesNpc(int id);

  boolean startsEncounter(int animation);

  boolean endsEncounter(int animation);

  String floorMechanic(int graphicId);
}
