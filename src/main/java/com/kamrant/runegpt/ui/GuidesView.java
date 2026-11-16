package com.kamrant.runegpt.ui;

import com.kamrant.runegpt.guides.Guide;
import com.kamrant.runegpt.guides.GuideCatalog;
import com.kamrant.runegpt.guides.GuideCategory;
import com.kamrant.runegpt.guides.GuideStore;
import com.kamrant.runegpt.service.PlayerSnapshot;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.BiConsumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.Scrollable;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

/**
 * Category picker plus recommended guide cards. Each card can be asked as-is or edited
 * (fields like the monster or the whole prompt). Each category ends with an "Other" box.
 */
public class GuidesView extends JPanel {

   private static final int PAD = 6;
   private static final int TEXT_WIDTH = PluginPanel.PANEL_WIDTH - 2 * PAD - 48;

   private final GuideStore store;
   /** (text shown in chat, prompt sent to the LLM) */
   private final BiConsumer<String, String> onAsk;
   private final Runnable onRefresh;

   private final Map<GuideCategory, JToggleButton> categoryButtons = new EnumMap<>(GuideCategory.class);
   private final JPanel cards = new JPanel();
   private final JLabel status = new JLabel();
   private final JScrollPane scroll;

   private GuideCategory selected = GuideCategory.QUESTS;
   private PlayerSnapshot snapshot = PlayerSnapshot.LOGGED_OUT;

   public GuidesView(final GuideStore store, final BiConsumer<String, String> onAsk, final Runnable onRefresh) {
      this.store = store;
      this.onAsk = onAsk;
      this.onRefresh = onRefresh;
      setLayout(new BorderLayout());
      setBackground(ColorScheme.DARK_GRAY_COLOR);

      // Category grid
      final JPanel grid = new JPanel(new GridLayout(0, 3, 4, 4));
      grid.setBackground(ColorScheme.DARK_GRAY_COLOR);
      for (final GuideCategory category : GuideCategory.values()) {
         final JToggleButton b = new JToggleButton(category.getShortName());
         b.setFont(FontManager.getRunescapeSmallFont());
         b.setFocusable(false);
         b.setMargin(new java.awt.Insets(4, 2, 4, 2));
         b.addActionListener(e -> select(category));
         categoryButtons.put(category, b);
         grid.add(b);
      }

      status.setFont(FontManager.getRunescapeSmallFont());
      status.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
      final JButton refresh = new JButton("↻");
      refresh.setToolTipText("Refresh suggestions from your current stats and quests");
      refresh.setFocusable(false);
      refresh.setMargin(new java.awt.Insets(0, 4, 0, 4));
      refresh.addActionListener(e -> this.onRefresh.run());
      final JPanel statusRow = new JPanel(new BorderLayout(4, 0));
      statusRow.setOpaque(false);
      statusRow.add(status, BorderLayout.CENTER);
      statusRow.add(refresh, BorderLayout.EAST);

      final JPanel top = new JPanel(new BorderLayout(0, 6));
      top.setBackground(ColorScheme.DARK_GRAY_COLOR);
      top.setBorder(new EmptyBorder(PAD, PAD, PAD, PAD));
      top.add(grid, BorderLayout.CENTER);
      top.add(statusRow, BorderLayout.SOUTH);
      add(top, BorderLayout.NORTH);

      cards.setLayout(new BoxLayout(cards, BoxLayout.Y_AXIS));
      cards.setOpaque(false);
      final TrackingPanel list = new TrackingPanel();
      list.setLayout(new BorderLayout());
      list.setBackground(ColorScheme.DARK_GRAY_COLOR);
      list.add(cards, BorderLayout.NORTH);
      scroll = new JScrollPane(list, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
      scroll.setBorder(null);
      scroll.getVerticalScrollBar().setUnitIncrement(16);
      add(scroll, BorderLayout.CENTER);

      select(selected);
   }

   public void setSnapshot(final PlayerSnapshot snapshot) {
      this.snapshot = snapshot;
      render();
   }

   private void select(final GuideCategory category) {
      selected = category;
      categoryButtons.forEach((c, b) -> {
         b.setSelected(c == category);
         b.setForeground(c == category ? ColorScheme.BRAND_ORANGE : ColorScheme.LIGHT_GRAY_COLOR);
      });
      render();
      scroll.getViewport().setViewPosition(new java.awt.Point(0, 0));
   }

   private void render() {
      status.setText(snapshot.isLoggedIn()
         ? "Suggested for combat level " + snapshot.getCombatLevel()
         : "Log in for personalized suggestions");

      cards.removeAll();
      cards.add(sectionLabel(selected.getFullName()));
      for (final Guide guide : store.guides(selected, snapshot)) {
         cards.add(guideCard(guide));
      }
      cards.add(otherCard());
      cards.add(Box.createVerticalStrut(PAD));
      cards.revalidate();
      cards.repaint();
   }

   private JComponent sectionLabel(final String text) {
      final JLabel l = new JLabel(text);
      l.setFont(FontManager.getRunescapeBoldFont());
      l.setForeground(Color.WHITE);
      l.setBorder(new EmptyBorder(2, PAD, 2, PAD));
      return row(l);
   }

   private JComponent guideCard(final Guide guide) {
      final JPanel card = new JPanel(new BorderLayout(0, 4));
      card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
      card.setBorder(new CompoundBorder(
         new MatteBorder(0, 3, 0, 0, guide.isCustomized() ? ColorScheme.PROGRESS_COMPLETE_COLOR : ColorScheme.BRAND_ORANGE),
         new EmptyBorder(6, 8, 6, 6)));

      final JLabel title = new JLabel(html(guide.title(), TEXT_WIDTH));
      title.setFont(FontManager.getRunescapeBoldFont());
      title.setForeground(Color.WHITE);

      final JLabel preview = new JLabel(html(shorten(guide.prompt(), 90), TEXT_WIDTH));
      preview.setFont(FontManager.getRunescapeSmallFont());
      preview.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

      final JPanel text = new JPanel(new BorderLayout(0, 2));
      text.setOpaque(false);
      text.add(title, BorderLayout.NORTH);
      text.add(preview, BorderLayout.CENTER);
      if (guide.isCustomized()) {
         final JLabel edited = new JLabel("Edited by you");
         edited.setFont(FontManager.getRunescapeSmallFont());
         edited.setForeground(ColorScheme.PROGRESS_COMPLETE_COLOR);
         text.add(edited, BorderLayout.SOUTH);
      }

      final JButton ask = smallButton("Ask");
      ask.addActionListener(e -> onAsk.accept(guide.title(), guide.prompt()));
      final JButton edit = smallButton("Edit");
      edit.addActionListener(e -> edit(guide));

      final JPanel buttons = new JPanel(new GridLayout(1, 2, 4, 0));
      buttons.setOpaque(false);
      buttons.add(edit);
      buttons.add(ask);

      card.add(text, BorderLayout.CENTER);
      card.add(buttons, BorderLayout.SOUTH);
      addHover(card, ColorScheme.DARKER_GRAY_COLOR, ColorScheme.DARKER_GRAY_HOVER_COLOR);
      return row(card);
   }

   private JComponent otherCard() {
      final JPanel card = new JPanel(new BorderLayout(0, 4));
      card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
      card.setBorder(new CompoundBorder(
         new MatteBorder(0, 3, 0, 0, ColorScheme.MEDIUM_GRAY_COLOR),
         new EmptyBorder(6, 8, 6, 6)));

      final JLabel title = new JLabel("Other");
      title.setFont(FontManager.getRunescapeBoldFont());
      title.setForeground(Color.WHITE);
      final JLabel hint = new JLabel(html("Something else about " + selected.getFullName().toLowerCase()
         + "? Type what you're interested in.", TEXT_WIDTH));
      hint.setFont(FontManager.getRunescapeSmallFont());
      hint.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

      final JTextField field = new JTextField();
      field.setBackground(ColorScheme.DARK_GRAY_COLOR);
      field.setForeground(Color.WHITE);
      field.setCaretColor(Color.WHITE);
      field.setBorder(BorderFactory.createCompoundBorder(
         BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR), new EmptyBorder(3, 4, 3, 4)));
      final JButton ask = smallButton("Ask");
      final Runnable submit = () -> {
         final String interest = field.getText().trim();
         if (!interest.isEmpty()) {
            field.setText("");
            onAsk.accept(interest, GuideCatalog.otherPrompt(selected, interest));
         }
      };
      ask.addActionListener(e -> submit.run());
      field.addActionListener(e -> submit.run());

      final JPanel inputRow = new JPanel(new BorderLayout(4, 0));
      inputRow.setOpaque(false);
      inputRow.add(field, BorderLayout.CENTER);
      inputRow.add(ask, BorderLayout.EAST);

      final JPanel text = new JPanel(new BorderLayout(0, 2));
      text.setOpaque(false);
      text.add(title, BorderLayout.NORTH);
      text.add(hint, BorderLayout.CENTER);
      card.add(text, BorderLayout.NORTH);
      card.add(inputRow, BorderLayout.SOUTH);
      return row(card);
   }

   private void edit(final Guide original) {
      final Guide working = original.copy();
      switch (GuideEditDialog.show(this, working)) {
         case SAVE:
            store.save(working);
            render();
            break;
         case SAVE_AND_ASK:
            store.save(working);
            render();
            onAsk.accept(working.title(), working.prompt());
            break;
         case RESET:
            store.reset(original);
            render();
            break;
         default:
            break;
      }
   }

   private static JComponent row(final JComponent content) {
      final JPanel row = new JPanel(new BorderLayout());
      row.setOpaque(false);
      row.setBorder(new EmptyBorder(PAD, PAD, 0, PAD));
      row.add(content, BorderLayout.CENTER);
      row.setAlignmentX(Component.LEFT_ALIGNMENT);
      return row;
   }

   private static JButton smallButton(final String text) {
      final JButton b = new JButton(text);
      b.setFont(FontManager.getRunescapeSmallFont());
      b.setFocusable(false);
      b.setMargin(new java.awt.Insets(2, 6, 2, 6));
      return b;
   }

   private static void addHover(final JPanel card, final Color normal, final Color hover) {
      card.addMouseListener(new MouseAdapter() {
         @Override
         public void mouseEntered(final MouseEvent e) {
            card.setBackground(hover);
            card.setCursor(Cursor.getDefaultCursor());
         }

         @Override
         public void mouseExited(final MouseEvent e) {
            // Ignore exits into child components.
            if (!card.contains(e.getPoint())) {
               card.setBackground(normal);
            }
         }
      });
   }

   private static String html(final String text, final int width) {
      return "<html><body style='width:" + width + "px'>" + MarkdownRenderer.escape(text) + "</body></html>";
   }

   private static String shorten(final String s, final int max) {
      return s.length() <= max ? s : s.substring(0, max).trim() + "…";
   }

   /** Tracks viewport width so cards wrap instead of scrolling sideways. */
   private static final class TrackingPanel extends JPanel implements Scrollable {
      @Override
      public Dimension getPreferredScrollableViewportSize() {
         return getPreferredSize();
      }

      @Override
      public int getScrollableUnitIncrement(final Rectangle r, final int o, final int d) {
         return 16;
      }

      @Override
      public int getScrollableBlockIncrement(final Rectangle r, final int o, final int d) {
         return r.height;
      }

      @Override
      public boolean getScrollableTracksViewportWidth() {
         return true;
      }

      @Override
      public boolean getScrollableTracksViewportHeight() {
         return false;
      }
   }
}
