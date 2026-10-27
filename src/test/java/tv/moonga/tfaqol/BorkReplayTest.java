package tv.moonga.tfaqol;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.runelite.api.*;
import net.runelite.api.coords.*;
import org.junit.Test;

/** Real recording regression: successful bombs and the labelled 39/27/12 damage examples. */
public class BorkReplayTest {
  private final Map<Integer, Actor> actors = new LinkedHashMap<>();
  private WorldView view;
  private Client client;

  private static int number(JsonObject o, String key, int fallback) {
    return o.has(key) ? o.get(key).getAsInt() : fallback;
  }

  private WorldPoint point(JsonObject o) {
    return new WorldPoint(number(o, "x", 0), number(o, "y", 0), number(o, "plane", 0));
  }

  private Actor update(JsonObject data) {
    int key = number(data, "key", -1);
    Actor actor = actors.get(key);
    if (actor == null) {
      actor =
          data.get("kind").getAsString().equals("player") ? mock(Player.class) : mock(NPC.class);
      actors.put(key, actor);
    }
    when(actor.getWorldView()).thenReturn(view);
    when(actor.getName()).thenReturn(data.get("name").getAsString());
    WorldPoint at = point(data.getAsJsonObject("world"));
    when(actor.getWorldLocation()).thenReturn(at);
    when(actor.getAnimation()).thenReturn(number(data, "animation", -1));
    when(actor.getInteracting()).thenReturn(actors.get(number(data, "target", -1)));
    when(actor.isDead()).thenReturn(data.has("dead") && data.get("dead").getAsBoolean());
    if (actor instanceof NPC) {
      when(((NPC) actor).getId()).thenReturn(number(data, "id", -1));
      when(actor.getWorldArea())
          .thenReturn(new WorldArea(at, number(data, "size", 1), number(data, "size", 1)));
    }
    if (data.has("self") && data.get("self").getAsBoolean())
      when(client.getLocalPlayer()).thenReturn((Player) actor);
    return actor;
  }

  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  public void suppliedBorkRecordingMatchesAgreedExamples() throws Exception {
    client = mock(Client.class);
    view = mock(WorldView.class);
    IndexedObjectSet ns = mock(IndexedObjectSet.class), ps = mock(IndexedObjectSet.class);
    when(ns.iterator())
        .thenAnswer(
            i ->
                actors.values().stream()
                    .filter(a -> a instanceof NPC)
                    .map(a -> (NPC) a)
                    .iterator());
    when(ps.iterator())
        .thenAnswer(
            i ->
                actors.values().stream()
                    .filter(a -> a instanceof Player)
                    .map(a -> (Player) a)
                    .iterator());
    when(view.npcs()).thenReturn(ns);
    when(view.players()).thenReturn(ps);
    BorkObserver observer = new BorkObserver();
    MistakeLedger ledger = new MistakeLedger();
    ledger.begin(TfaBoss.BORK_AND_SMORKS, 890, 28330, true);
    JsonObject fixture;
    try (Reader reader =
        new InputStreamReader(
            getClass().getResourceAsStream("bork-replay.json"), StandardCharsets.UTF_8)) {
      fixture = new JsonParser().parse(reader).getAsJsonObject();
    }
    for (JsonElement element : fixture.getAsJsonArray("events")) {
      JsonObject row = element.getAsJsonObject(), data = row.getAsJsonObject("data");
      String type = row.get("type").getAsString();
      int cycle = number(row, "cycle", 0), tick = number(row, "tick", 0);
      when(client.getGameCycle()).thenReturn(cycle);
      when(client.getTickCount()).thenReturn(tick);
      if (type.equals("tick") || type.equals("baseline")) {
        for (JsonElement e : data.getAsJsonArray("actors")) update(e.getAsJsonObject());
        String prayers = data.getAsJsonArray("prayers").toString();
        when(client.isPrayerActive(Prayer.PROTECT_FROM_MAGIC))
            .thenReturn(prayers.contains("PROTECT_FROM_MAGIC"));
        when(client.isPrayerActive(Prayer.PROTECT_FROM_MISSILES))
            .thenReturn(prayers.contains("PROTECT_FROM_MISSILES"));
        observer.actors(view);
        for (Actor a : actors.values()) if (a instanceof Player) observer.participant((Player) a);
      } else if (type.equals("animation") || type.equals("graphic") || type.equals("death")) {
        Actor actor = update(data);
        if (actor instanceof NPC && type.equals("animation"))
          observer.animation((NPC) actor, cycle);
      } else if (type.equals("npc_despawn")) actors.remove(number(data, "key", -1));
      else if (type.equals("effect"))
        observer.floor(number(data, "x", 0), number(data, "y", 0), number(data, "startCycle", 0));
      else if (type.startsWith("object_")) {
        WorldPoint p = point(data.getAsJsonObject("world"));
        observer.ground(p.getX(), p.getY(), cycle, !type.equals("object_despawn"));
      } else if (type.equals("projectile")) {
        Projectile p = mock(Projectile.class);
        when(p.getId()).thenReturn(number(data, "id", -1));
        when(p.getEndCycle()).thenReturn(number(data, "endCycle", 0));
        when(p.getSourceActor()).thenReturn(actors.get(number(data, "source", -1)));
        when(p.getTargetActor()).thenReturn(actors.get(number(data, "target", -1)));
        if (data.has("sourcePoint"))
          when(p.getSourcePoint()).thenReturn(point(data.getAsJsonObject("sourcePoint")));
        if (data.has("targetPoint"))
          when(p.getTargetPoint()).thenReturn(point(data.getAsJsonObject("targetPoint")));
        observer.projectile(p, client);
      } else if (type.equals("hitsplat")) {
        Player player = (Player) update(data.getAsJsonObject("actor"));
        Hitsplat h = mock(Hitsplat.class);
        int amount = number(data, "amount", 0), id = number(data, "type", 0);
        when(h.getAmount()).thenReturn(amount);
        when(h.getHitsplatType()).thenReturn(id);
        observer.hit(player, h, id == 16 || id == 17, client);
      }
      observer.actors(view);
      observer.frame(client);
      observer.drain(client, ledger, true);
    }
    assertTrue(
        ledger.current().events.stream()
            .anyMatch(
                e ->
                    e.player.equals("Player013")
                        && e.tick == 1067
                        && e.damage == 39
                        && e.type.equals("Possible Bork attack")));
    assertTrue(
        ledger.current().events.stream()
            .anyMatch(
                e ->
                    e.player.equals("Player016")
                        && e.tick == 963
                        && e.type.equals("Possible Tank damage")));
    assertFalse(
        ledger.current().events.stream().anyMatch(e -> e.type.equals("Possible Bomb missed")));
    assertFalse(
        ledger.current().events.stream()
            .anyMatch(
                e ->
                    e.tick == 1083
                        && e.player.equals("Player013")
                        && e.type.equals("Possible Bork attack")));
    assertTrue(
        ledger.current().events.stream()
            .anyMatch(
                e ->
                    e.player.equals("Player013") && e.type.equals("Possible Flame ground damage")));
  }
}
