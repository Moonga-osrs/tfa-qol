package tv.moonga.tfaqol;

import static org.junit.Assert.*;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import javax.swing.*;
import org.junit.Test;

public class MistakePanelTest {
  private static void layout(Component c) {
    if (c instanceof Container) {
      ((Container) c).doLayout();
      for (Component child : ((Container) c).getComponents()) layout(child);
    }
  }

  private static java.util.List<JComboBox> combos(Component c) {
    java.util.List<JComboBox> result = new ArrayList<>();
    if (c instanceof JComboBox) result.add((JComboBox) c);
    if (c instanceof Container)
      for (Component child : ((Container) c).getComponents()) result.addAll(combos(child));
    return result;
  }

  private static boolean hasText(Component c, String value) {
    if (c instanceof JLabel && value.equals(((JLabel) c).getText())) return true;
    if (c instanceof Container)
      for (Component child : ((Container) c).getComponents())
        if (hasText(child, value)) return true;
    return false;
  }

  @Test
  public void everyAgreedCategoryHasAPackagedModelIcon() {
    for (MistakeKind kind : MistakeKind.values())
      assertEquals(44, MistakePanel.icon(kind.icon, 44).getIconWidth());
    assertEquals(
        8,
        Arrays.stream(MistakeKind.values())
            .filter(k -> k.boss == TfaBoss.ARCHIVE_GUARDIAN)
            .count());
  }

  @Test
  public void arbitraryDamageDeathsAndEntranceAttemptsNeverBecomeMistakes() {
    MistakeLedger l = new MistakeLedger();
    l.begin(TfaBoss.ARCHIVE_GUARDIAN, 1, 30, false);
    for (String type :
        Arrays.asList("Other damage", "Death", "Missing salve", "Possible Unknown attack"))
      l.observation("Player", type, 44, 2, 60, "Unagreed");
    assertTrue(l.current().events.isEmpty());
    l.observation("Player", "Possible Melee attack", 9, 2, 60, "Agreed");
    assertEquals(1, l.current().events.size());
  }

  private static JButton button(Component c, String label) {
    if (c instanceof JButton && label.equals(((JButton) c).getText())) return (JButton) c;
    if (c instanceof Container)
      for (Component child : ((Container) c).getComponents()) {
        JButton found = button(child, label);
        if (found != null) return found;
      }
    return null;
  }

  @Test
  public void completedRoomsStayAboveNewRoomsWithSeparatePlayersAndTotals() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          MistakePanel p = new MistakePanel(() -> {}, () -> {}, () -> {});
          MistakePanel.Fight one =
              new MistakePanel.Fight(
                  1,
                  TfaBoss.ARCHIVE_GUARDIAN,
                  Arrays.asList(new MistakePanel.Row(MistakeKind.MELEE, "Alice", 44, 10)),
                  new LinkedHashSet<>(Arrays.asList("Alice", "Zero mistakes")));
          MistakePanel.Fight two =
              new MistakePanel.Fight(
                  2,
                  TfaBoss.BORK_AND_SMORKS,
                  Arrays.asList(new MistakePanel.Row(MistakeKind.BORK_TANK, "Bob", 5, 12)),
                  Collections.singleton("Bob"));
          p.updateFights(Collections.singletonList(one), "Guardian");
          p.updateFights(Arrays.asList(one, two), "Bork");
          assertTrue(hasText(p, "Current raid"));
          assertTrue(hasText(p, "Archive Guardian"));
          assertTrue(hasText(p, "Bork and the Smorks"));
          assertTrue(hasText(p, "Alice"));
          assertTrue(hasText(p, "Bob"));
          assertTrue(hasText(p, "Total players: 2"));
          assertTrue(hasText(p, "Total players: 1"));
          assertTrue(hasText(p, "0 Mistakes"));
          p.updateFights(Collections.emptyList(), "New attempt");
          assertFalse(hasText(p, "Alice"));
          assertFalse(hasText(p, "Bob"));
        });
  }

  @Test
  public void renderGuardianPanelAtActualSidebarWidth() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          try {
            MistakePanel p = new MistakePanel(() -> {}, () -> {}, () -> {});
            java.util.List<MistakePanel.Row> rows = new ArrayList<>();
            int n = 0;
            for (MistakeKind k : MistakeKind.values())
              if (k.boss == TfaBoss.ARCHIVE_GUARDIAN)
                rows.add(
                    new MistakePanel.Row(
                        k, "Example player", k == MistakeKind.FIRE_MISSED ? 0 : 6, 10 + n++));
            rows.add(new MistakePanel.Row(MistakeKind.MELEE, "Second player", 8, 20));
            rows.add(new MistakePanel.Row(MistakeKind.VINE, "Second player", 10, 21));
            p.updateFights(
                Arrays.asList(
                    new MistakePanel.Fight(
                        1,
                        TfaBoss.ARCHIVE_GUARDIAN,
                        rows,
                        new LinkedHashSet<>(Arrays.asList("Example player", "Second player"))),
                    new MistakePanel.Fight(
                        2,
                        TfaBoss.BORK_AND_SMORKS,
                        Arrays.asList(
                            new MistakePanel.Row(MistakeKind.BORK_TANK, "Example player", 7, 30)),
                        new LinkedHashSet<>(Arrays.asList("Example player", "Second player")))),
                "Bork - tracking");
            p.setSize(225, 860);
            layout(p);
            BufferedImage image = new BufferedImage(225, 860, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = image.createGraphics();
            p.printAll(g);
            g.dispose();
            Files.createDirectories(Path.of("preview"));
            ImageIO.write(image, "png", Path.of("preview/guardian-cards.png").toFile());
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
  }

  @Test
  public void ocellusReviewHitsAreSeparateAndFitTheSidebar() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          try {
            MistakePanel p = new MistakePanel(() -> {}, () -> {}, () -> {});
            java.util.List<MistakePanel.Row> rows = new ArrayList<>();
            int tick = 10;
            for (MistakeKind k : MistakeKind.values())
              if (k.boss == TfaBoss.OCELLUS)
                rows.add(new MistakePanel.Row(k, "Example player", 7, tick++));
            p.updateFights(
                Arrays.asList(
                    new MistakePanel.Fight(
                        1,
                        TfaBoss.OCELLUS,
                        rows,
                        new LinkedHashSet<>(Arrays.asList("Example player", "Second player")),
                        4909,
                        1592)),
                "Ocellus - tracking");
            assertTrue(hasText(p, "Review hits: 4909"));
            assertTrue(hasText(p, "Unresolved: 1592"));
            assertTrue(hasText(p, "Possible mistakes: 11"));
            p.setSize(225, 680);
            layout(p);
            BufferedImage image = new BufferedImage(225, 680, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = image.createGraphics();
            p.printAll(g);
            g.dispose();
            Files.createDirectories(Path.of("preview"));
            ImageIO.write(image, "png", Path.of("preview/ocellus-cards.png").toFile());
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
  }

  @Test
  public void renderDreadnothBelowCompletedRoomsWithNineIcons() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          try {
            MistakePanel p = new MistakePanel(() -> {}, () -> {}, () -> {});
            java.util.List<MistakePanel.Row> rows = new ArrayList<>();
            int t = 20;
            for (MistakeKind k : MistakeKind.values())
              if (k.boss == TfaBoss.DREADNOTH)
                rows.add(new MistakePanel.Row(k, "Example player", 8, t++));
            assertEquals(9, rows.size());
            p.updateFights(
                Arrays.asList(
                    new MistakePanel.Fight(
                        1,
                        TfaBoss.BORK_AND_SMORKS,
                        Collections.emptyList(),
                        Collections.singleton("Example player")),
                    new MistakePanel.Fight(
                        2,
                        TfaBoss.DREADNOTH,
                        rows,
                        new LinkedHashSet<>(Arrays.asList("Example player", "Second player")),
                        66,
                        23)),
                "Dreadnoth - tracking");
            assertTrue(hasText(p, "Dreadnoth the Jellied"));
            assertTrue(hasText(p, "Bork and the Smorks"));
            assertTrue(hasText(p, "Possible mistakes: 9"));
            assertTrue(hasText(p, "Review hits: 66"));
            p.setSize(225, 720);
            layout(p);
            BufferedImage image = new BufferedImage(225, 720, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = image.createGraphics();
            p.printAll(g);
            g.dispose();
            Files.createDirectories(Path.of("preview"));
            ImageIO.write(image, "png", Path.of("preview/dreadnoth-cards.png").toFile());
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
  }

  @Test
  public void renderAllFiveTestRoomsWithTheirOwnCounters() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          try {
            MistakePanel p = new MistakePanel(() -> {}, () -> {}, () -> {});
            java.util.List<MistakePanel.Fight> fights = new ArrayList<>();
            int id = 1;
            for (TfaBoss boss :
                new TfaBoss[] {
                  TfaBoss.ARCHIVE_GUARDIAN,
                  TfaBoss.BORK_AND_SMORKS,
                  TfaBoss.DREADNOTH,
                  TfaBoss.XARTH,
                  TfaBoss.OCELLUS
                }) {
              java.util.List<MistakePanel.Row> rows = new ArrayList<>();
              for (MistakeKind k : MistakeKind.values())
                if (k.boss == boss)
                  rows.add(new MistakePanel.Row(k, "Example player", 7, 100 + rows.size()));
              fights.add(
                  new MistakePanel.Fight(
                      id++,
                      boss,
                      rows,
                      new LinkedHashSet<>(Arrays.asList("Example player", "Second player")),
                      40,
                      5));
            }
            p.updateFights(fights, "Five-boss test build 0.9.0");
            for (MistakePanel.Fight f : fights) assertTrue(hasText(p, f.boss.label));
            p.setSize(225, 1800);
            layout(p);
            BufferedImage image = new BufferedImage(225, 1800, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = image.createGraphics();
            p.printAll(g);
            g.dispose();
            Files.createDirectories(Path.of("preview"));
            ImageIO.write(image, "png", Path.of("preview/five-boss-test.png").toFile());
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
  }
}
