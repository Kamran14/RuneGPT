package com.kamrant.runegpt.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/** Accordion section: a clickable header ("Quest Helper  2  ▾") over a body that shows when expanded. */
public class CollapsibleSection extends RoundedPanel {

   private static final Color HEADER_BG = ColorScheme.DARKER_GRAY_COLOR;
   private static final Color HEADER_HOVER = ColorScheme.DARKER_GRAY_HOVER_COLOR;

   private final JLabel chevron = new JLabel();
   private final JLabel badge = new JLabel();
   private final JPanel body = new JPanel(new BorderLayout());
   private boolean expanded;

   public CollapsibleSection(final String title, final Runnable onToggle) {
      super(new BorderLayout(), HEADER_BG, 12);

      final JLabel titleLabel = new JLabel(title);
      titleLabel.setFont(FontManager.getRunescapeBoldFont());
      titleLabel.setForeground(Color.WHITE);

      badge.setFont(FontManager.getRunescapeSmallFont());
      badge.setForeground(ColorScheme.BRAND_ORANGE);
      chevron.setFont(FontManager.getRunescapeBoldFont());
      chevron.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

      final JPanel right = new JPanel(new BorderLayout(8, 0));
      right.setOpaque(false);
      right.add(badge, BorderLayout.WEST);
      right.add(chevron, BorderLayout.EAST);

      final JPanel header = new JPanel(new BorderLayout());
      header.setOpaque(false);
      header.setBorder(new EmptyBorder(9, 10, 9, 10));
      header.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      header.add(titleLabel, BorderLayout.CENTER);
      header.add(right, BorderLayout.EAST);
      header.addMouseListener(new MouseAdapter() {
         @Override
         public void mouseClicked(final MouseEvent e) {
            onToggle.run();
         }

         @Override
         public void mouseEntered(final MouseEvent e) {
            setBackground(HEADER_HOVER);
            repaint();
         }

         @Override
         public void mouseExited(final MouseEvent e) {
            setBackground(HEADER_BG);
            repaint();
         }
      });

      body.setOpaque(false);
      body.setBorder(new EmptyBorder(0, 6, 8, 6));
      add(header, BorderLayout.NORTH);
      add(body, BorderLayout.CENTER);
      setExpanded(false);
   }

   public boolean isExpanded() {
      return expanded;
   }

   public void setExpanded(final boolean expanded) {
      this.expanded = expanded;
      chevron.setText(expanded ? "▴" : "▾");
      body.setVisible(expanded);
      revalidate();
      repaint();
   }

   /** Small orange count next to the chevron, e.g. number of quests in progress. Null hides it. */
   public void setBadge(final String text) {
      badge.setText(text == null ? "" : text);
   }

   public void setBody(final JComponent content) {
      body.removeAll();
      body.add(content, BorderLayout.CENTER);
      body.revalidate();
      body.repaint();
   }
}
