package tv.moonga.tfaqol;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.*;
import net.runelite.api.*;
import org.junit.*;

public class WeaponSelfDamageTest {
  private Client client;
  private Player p;
  private NPC boss;
  private WorldView view;
  private Hitsplat hit;

  @Before
  @SuppressWarnings({"unchecked", "rawtypes"})
  public void setup() {
    client = mock(Client.class);
    p = mock(Player.class);
    boss = mock(NPC.class);
    view = mock(WorldView.class);
    hit = mock(Hitsplat.class);
    when(client.getGameCycle()).thenReturn(3000);
    when(p.getAnimation()).thenReturn(9168);
    when(p.getWorldView()).thenReturn(view);
    when(p.getInteracting()).thenReturn(boss);
    when(boss.getId()).thenReturn(16677);
    when(boss.getWorldView()).thenReturn(view);
    when(hit.getAmount()).thenReturn(9);
    when(hit.getHitsplatType()).thenReturn(HitsplatID.DAMAGE_OTHER);
    ActorSpotAnim s = mock(ActorSpotAnim.class);
    when(s.getId()).thenReturn(754);
    when(s.getStartCycle()).thenReturn(2972);
    IterableHashTable table = mock(IterableHashTable.class);
    when(table.iterator()).thenAnswer(i -> Arrays.asList(s).iterator());
    when(boss.getSpotAnims()).thenReturn(table);
    IndexedObjectSet npcs = mock(IndexedObjectSet.class);
    when(npcs.iterator()).thenAnswer(i -> Arrays.asList(boss).iterator());
    when(view.npcs()).thenReturn(npcs);
  }

  @Test
  public void animationAndMatchingBloodSacrificeGiveCompetingEvidence() {
    assertNotNull(WeaponSelfDamage.evidence(client, p, hit));
  }

  @Test
  public void animationAloneIsNotEnough() {
    when(boss.getSpotAnims()).thenReturn(null);
    assertNull(WeaponSelfDamage.evidence(client, p, hit));
  }

  @Test
  public void bloodEffectAloneIsNotEnough() {
    when(p.getAnimation()).thenReturn(9166);
    assertNull(WeaponSelfDamage.evidence(client, p, hit));
  }

  @Test
  public void noTargetReferenceCanUseOneUnambiguousVisibleBoss() {
    when(p.getInteracting()).thenReturn(null);
    assertNotNull(WeaponSelfDamage.evidence(client, p, hit));
  }

  @Test
  public void oldEffectDoesNotHideARealHit() {
    when(client.getGameCycle()).thenReturn(3060);
    assertNull(WeaponSelfDamage.evidence(client, p, hit));
  }

  @Test
  public void burnAndZeroDamageAreNotRubyEvidence() {
    when(hit.getHitsplatType()).thenReturn(HitsplatID.BURN);
    assertNull(WeaponSelfDamage.evidence(client, p, hit));
    when(hit.getHitsplatType()).thenReturn(HitsplatID.DAMAGE_OTHER);
    when(hit.getAmount()).thenReturn(0);
    assertNull(WeaponSelfDamage.evidence(client, p, hit));
  }
}
