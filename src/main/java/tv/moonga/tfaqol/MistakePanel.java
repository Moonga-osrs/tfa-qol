package tv.moonga.tfaqol;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.PluginPanel;

/** Local counters with cache-model thumbnails. All Swing state is accessed on the EDT. */
final class MistakePanel extends PluginPanel {
  static final Color BG = new Color(30, 32, 36), MUTED = new Color(160, 168, 177);

  static final class Row {
    final MistakeKind kind;
    final String player;
    final int damage, tick;

    Row(MistakeKind kind, String player, int damage, int tick) {
      this.kind = kind;
      this.player = player;
      this.damage = damage;
      this.tick = tick;
    }
  }

  private static final Map<String, BufferedImage> IMAGES =
      new java.util.concurrent.ConcurrentHashMap<>();

  static ImageIcon icon(String name, int size) {
    try {
      BufferedImage source = IMAGES.get(name);
      if (source == null) {
        try (java.io.InputStream stream =
            MistakePanel.class.getResourceAsStream("icons/" + name + ".png")) {
          if (stream == null) throw new java.io.IOException("Missing icon resource");
          source = ImageIO.read(stream);
        }
        IMAGES.put(name, source);
      }
      return new ImageIcon(source.getScaledInstance(size, size, Image.SCALE_SMOOTH));
    } catch (Exception e) {
      throw new IllegalStateException("Missing mistake icon: " + name, e);
    }
  }

  static final class Fight {
    final int id;
    final int reviewHits, unresolved;
    final TfaBoss boss;
    final java.util.List<Row> rows;
    final Set<String> players;

    Fight(int id, TfaBoss boss, List<Row> rows, Set<String> players) {
      this(id, boss, rows, players, 0, 0);
    }

    Fight(
        int id, TfaBoss boss, List<Row> rows, Set<String> players, int reviewHits, int unresolved) {
      this.reviewHits = reviewHits;
      this.unresolved = unresolved;
      this.id = id;
      this.boss = boss;
      this.rows = new ArrayList<>(rows);
      this.players = new LinkedHashSet<>(players);
      for (Row row : rows) this.players.add(row.player);
    }
  }

  private final JLabel title = new JLabel("Current raid"), status = new JLabel("Waiting for TFA");
  private final JPanel cards = new JPanel();
  private List<Fight> fights = Collections.emptyList();
  private String signature = "";

  MistakePanel(Runnable save, Runnable reset, Runnable reports) {
    super(false); // Fill RuneLite's sidebar directly; do not create a second outer scroll pane.
    setLayout(new BorderLayout());
    setBackground(BG);
    setBorder(new EmptyBorder(8, 6, 8, 6));
    JPanel content = new JPanel();
    content.setBackground(BG);
    content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
    JPanel header = new JPanel(new BorderLayout(5, 0));
    header.setOpaque(false);
    title.setForeground(Color.WHITE);
    header.add(title, BorderLayout.CENTER);
    header.setMaximumSize(new Dimension(Integer.MAX_VALUE, 29));
    content.add(header);
    content.add(gap(5));
    JPanel tools = new JPanel(new BorderLayout());
    tools.setOpaque(false);
    status.setForeground(MUTED);
    status.setFont(status.getFont().deriveFont(9f));
    tools.add(status, BorderLayout.CENTER);
    JButton menu = new JButton("...");
    menu.setMargin(new Insets(1, 6, 1, 6));
    JPopupMenu popup = new JPopupMenu();
    addAction(popup, "Save report", save);
    addAction(popup, "Report location", reports);
    popup.addSeparator();
    addAction(popup, "Reset counters", reset);
    menu.addActionListener(e -> popup.show(menu, 0, menu.getHeight()));
    tools.add(menu, BorderLayout.EAST);
    tools.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
    content.add(tools);
    content.add(gap(8));
    cards.setLayout(new BoxLayout(cards, BoxLayout.Y_AXIS));
    cards.setBackground(BG);
    content.add(cards);
    content.add(gap(6));
    JLabel confidence = new JLabel("Possible mistakes - needs validation");
    confidence.setForeground(MUTED);
    confidence.setFont(confidence.getFont().deriveFont(9f));
    content.add(confidence);
    for (Component c : content.getComponents())
      if (c instanceof JComponent) ((JComponent) c).setAlignmentX(Component.LEFT_ALIGNMENT);
    JScrollPane scroll = new JScrollPane(content);
    scroll.setBorder(null);
    scroll.getVerticalScrollBar().setUnitIncrement(18);
    scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
    scroll.setWheelScrollingEnabled(false);
    scroll.getViewport().setBackground(BG);
    scroll.addMouseWheelListener(
        e -> {
          JViewport viewport = scroll.getViewport();
          Point at = viewport.getViewPosition();
          int max = Math.max(0, viewport.getViewSize().height - viewport.getExtentSize().height);
          int delta =
              e.getScrollType() == java.awt.event.MouseWheelEvent.WHEEL_BLOCK_SCROLL
                  ? e.getWheelRotation() * viewport.getExtentSize().height
                  : e.getUnitsToScroll() * 18;
          viewport.setViewPosition(new Point(0, Math.max(0, Math.min(max, at.y + delta))));
          e.consume();
        });
    add(scroll, BorderLayout.CENTER);
    rebuild();
  }

  private static void addAction(JPopupMenu popup, String text, Runnable callback) {
    JMenuItem item = new JMenuItem(text);
    item.addActionListener(e -> callback.run());
    popup.add(item);
  }

  private static Component gap(int n) {
    return Box.createVerticalStrut(n);
  }

  void status(String text) {
    status.setText(text);
    status.setToolTipText(text);
  }

  void updateFights(List<Fight> next, String text) {
    status(text);
    StringBuilder key = new StringBuilder();
    for (Fight f : next) {
      key.append(f.id).append(f.boss).append(f.players).append(f.reviewHits).append(f.unresolved);
      for (Row r : f.rows)
        key.append(r.kind)
            .append('|')
            .append(r.player)
            .append('|')
            .append(r.damage)
            .append('|')
            .append(r.tick)
            .append(';');
    }
    String nextSignature = key.toString();
    if (nextSignature.equals(signature)) return;
    signature = nextSignature;
    fights = new ArrayList<>(next);
    rebuild();
  }

  private void rebuild() {
    cards.removeAll();
    for (Fight fight : fights) {
      Map<String, Map<MistakeKind, int[]>> counts = new LinkedHashMap<>();
      int sum = 0;
      for (String name : fight.players)
        counts.computeIfAbsent(name, n -> new EnumMap<>(MistakeKind.class));
      for (Row row : fight.rows) {
        Map<MistakeKind, int[]> byKind =
            counts.computeIfAbsent(row.player, n -> new EnumMap<>(MistakeKind.class));
        int[] c = byKind.computeIfAbsent(row.kind, k -> new int[2]);
        c[0]++;
        c[1] += row.damage;
        sum++;
      }
      JLabel bossTitle = new JLabel(fight.boss.label);
      bossTitle.setForeground(Color.WHITE);
      bossTitle.setFont(bossTitle.getFont().deriveFont(Font.BOLD, 12f));
      bossTitle.setBorder(new EmptyBorder(3, 0, 5, 0));
      cards.add(bossTitle);
      JPanel overview = new JPanel(new BorderLayout(6, 0));
      overview.setBackground(new Color(24, 24, 24));
      overview.setBorder(new EmptyBorder(7, 6, 7, 6));
      JLabel portrait =
          new JLabel(
              icon(
                  fight.boss == TfaBoss.XARTH
                      ? "xarth"
                      : fight.boss == TfaBoss.DREADNOTH
                          ? "dreadnoth"
                          : fight.boss == TfaBoss.OCELLUS
                              ? "ocellus"
                              : fight.boss == TfaBoss.EZEKIEL
                                  ? "ezekiel"
                                  : fight.boss == TfaBoss.BORK_AND_SMORKS ? "bork" : "guardian",
                  46));
      overview.add(portrait, BorderLayout.WEST);
      JPanel numbers = new JPanel(new GridLayout((fight.boss != TfaBoss.EZEKIEL) ? 4 : 3, 1));
      numbers.setOpaque(false);
      for (String text :
          (fight.boss != TfaBoss.EZEKIEL)
              ? new String[] {
                "Total players: " + counts.size(),
                "Possible mistakes: " + sum,
                "Review hits: " + fight.reviewHits,
                "Unresolved: " + fight.unresolved
              }
              : new String[] {
                "Total players: " + counts.size(), "Total mistakes: " + sum, "Tracked fights: 1"
              }) {
        JLabel label = new JLabel(text);
        label.setForeground(new Color(190, 190, 190));
        label.setFont(label.getFont().deriveFont(11f));
        numbers.add(label);
      }
      overview.add(numbers, BorderLayout.CENTER);
      overview.setMaximumSize(new Dimension(Integer.MAX_VALUE, 70));
      cards.add(overview);
      cards.add(gap(7));
      for (Map.Entry<String, Map<MistakeKind, int[]>> player : counts.entrySet()) {
        JPanel box = new JPanel(new BorderLayout(0, 1));
        box.setOpaque(false);
        JPanel heading = new JPanel(new BorderLayout(4, 0));
        heading.setBackground(new Color(18, 18, 18));
        heading.setBorder(new EmptyBorder(7, 6, 7, 6));
        JLabel name = new JLabel(player.getKey());
        name.setForeground(Color.WHITE);
        name.setFont(name.getFont().deriveFont(12f));
        int count = 0;
        for (int[] c : player.getValue().values()) count += c[0];
        JLabel total = new JLabel(count + (count == 1 ? " Mistake" : " Mistakes"));
        total.setForeground(Color.LIGHT_GRAY);
        total.setFont(total.getFont().deriveFont(11f));
        heading.add(name, BorderLayout.CENTER);
        heading.add(total, BorderLayout.EAST);
        box.add(heading, BorderLayout.NORTH);
        int slots = Math.max(5, ((player.getValue().size() + 4) / 5) * 5);
        JPanel grid = new JPanel(new GridLayout(0, 5, 1, 1));
        grid.setBackground(BG);
        for (int i = 0; i < slots; i++) {
          JPanel slot = new JPanel(new BorderLayout());
          slot.setBackground(new Color(34, 34, 34));
          slot.setPreferredSize(new Dimension(36, 39));
          grid.add(slot);
        }
        int index = 0;
        for (Map.Entry<MistakeKind, int[]> entry : player.getValue().entrySet()) {
          MistakeKind kind = entry.getKey();
          int[] c = entry.getValue();
          JPanel slot = (JPanel) grid.getComponent(index++);
          JLabel image = new JLabel(icon(kind.icon, 36));
          image.setHorizontalAlignment(SwingConstants.CENTER);
          image.setLayout(new BorderLayout());
          JLabel quantity = new JLabel(String.valueOf(c[0]));
          quantity.setForeground(new Color(255, 239, 70));
          quantity.setFont(quantity.getFont().deriveFont(Font.BOLD, 11f));
          image.add(quantity, BorderLayout.NORTH);
          String tip =
              kind.boss.label
                  + " - "
                  + kind.label
                  + ": "
                  + c[0]
                  + " possible; "
                  + c[1]
                  + " damage. "
                  + kind.description;
          image.setToolTipText(tip);
          quantity.setToolTipText(tip);
          slot.add(image);
        }
        box.add(grid, BorderLayout.CENTER);
        box.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32 + (slots / 5) * 40));
        cards.add(box);
        cards.add(gap(6));
      }
      cards.add(gap(10));
    }
    for (Component c : cards.getComponents())
      if (c instanceof JComponent) ((JComponent) c).setAlignmentX(Component.LEFT_ALIGNMENT);
    if (fights.isEmpty()) {
      JLabel empty = new JLabel("Waiting for a supported fight");
      empty.setForeground(MUTED);
      cards.add(empty);
    }
    cards.revalidate();
    cards.repaint();
  }
}
