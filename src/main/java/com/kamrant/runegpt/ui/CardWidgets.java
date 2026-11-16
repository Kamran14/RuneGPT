package com.kamrant.runegpt.ui;

import com.kamrant.runegpt.model.WorldTarget;
import java.awt.Color;
import java.awt.Component;
import java.awt.GridLayout;
import java.awt.Insets;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.LinkBrowser;

/** Shared Swing factory methods for cards, labels, pill buttons and target rows in the sidebar. */
public final class CardWidgets {

   private CardWidgets() {
   }

   public static JPanel column() {
      final JPanel p = new JPanel();
      p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
      p.setOpaque(false);
      return p;
   }

   public static JComponent title(final String text) {
      final HtmlPane t = new HtmlPane(ColorScheme.BRAND_ORANGE, FontManager.getRunescapeBoldFont());
      t.setPlainText(text);
      return t;
   }

   public static JComponent label(final String text) {
      final HtmlPane l = new HtmlPane(ColorScheme.MEDIUM_GRAY_COLOR.brighter(), FontManager.getRunescapeSmallFont());
      l.setPlainText(text);
      return l;
   }

   public static JComponent text(final String text, final Color color) {
      final HtmlPane t = new HtmlPane(color);
      t.setPlainText(text);
      return t;
   }

   public static JComponent small(final String text) {
      final HtmlPane t = new HtmlPane(ColorScheme.LIGHT_GRAY_COLOR, FontManager.getRunescapeSmallFont());
      t.setPlainText(text);
      return t;
   }

   public static JButton pillButton(final String text) {
      final JButton b = new JButton(text);
      b.setFont(FontManager.getRunescapeSmallFont());
      b.setFocusable(false);
      b.setMargin(new Insets(2, 8, 2, 8));
      return b;
   }

   public static JButton wikiButton(final String url, final String text) {
      final JButton b = pillButton(text);
      b.addActionListener(e -> LinkBrowser.browse(url));
      return b;
   }

   public static JComponent buttonRow(final JButton... buttons) {
      final JPanel row = new JPanel(new GridLayout(1, buttons.length, 4, 0));
      row.setOpaque(false);
      for (final JButton b : buttons) {
         row.add(b);
      }
      return row;
   }

   public static JComponent targetRow(final String label, final WorldTarget t) {
      final String note = t.isApproximate() ? " (approx. location)" : t.getPoint() == null ? " (no map marker)" : "";
      final HtmlPane pane = new HtmlPane(UiTheme.ACCENT_CYAN);
      pane.setHtml("<span class=\"lbl\">" + label + ":</span> <b>" + MarkdownRenderer.escape(t.getName()) + "</b>"
         + "<span class=\"muted\">" + MarkdownRenderer.escape(note) + "</span>");
      return pane;
   }

   public static void addLeft(final JPanel panel, final Component c) {
      if (c instanceof JComponent) {
         ((JComponent) c).setAlignmentX(Component.LEFT_ALIGNMENT);
      }
      panel.add(c);
   }
}
