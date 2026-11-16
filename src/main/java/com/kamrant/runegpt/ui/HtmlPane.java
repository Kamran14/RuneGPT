package com.kamrant.runegpt.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import javax.swing.JEditorPane;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.LinkBrowser;

/**
 * Read-only HTML view that wraps to a fixed width, so long replies grow downwards instead of
 * overflowing the narrow sidebar. Uses its own stylesheet (Swing's default one is shared app-wide).
 */
public class HtmlPane extends JEditorPane {

   private final int wrapWidth;

   public HtmlPane(final int wrapWidth, final Color textColor) {
      this.wrapWidth = wrapWidth;
      setEditorKit(new Kit(textColor));
      setEditable(false);
      setOpaque(false);
      setBorder(null);
      setFocusable(false);
      addHyperlinkListener(e -> {
         if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED && e.getURL() != null) {
            final String url = e.getURL().toString();
            if (url.startsWith("https://") || url.startsWith("http://")) {
               LinkBrowser.browse(url);
            }
         }
      });
   }

   /** Sets the body content from an HTML fragment. */
   public void setHtml(final String fragment) {
      setText("<html><body>" + fragment + "</body></html>");
      setCaretPosition(0);
      revalidate();
   }

   @Override
   public Dimension getPreferredSize() {
      // Lay out at the fixed width first so the preferred height reflects wrapped text.
      setSize(wrapWidth, Short.MAX_VALUE);
      final Dimension d = super.getPreferredSize();
      return new Dimension(wrapWidth, d.height);
   }

   @Override
   public Dimension getMaximumSize() {
      return getPreferredSize();
   }

   private static String hex(final Color c) {
      return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
   }

   private static final class Kit extends HTMLEditorKit {
      private final StyleSheet styles = new StyleSheet();

      Kit(final Color textColor) {
         styles.addStyleSheet(super.getStyleSheet());
         final String text = hex(textColor);
         final String accent = hex(ColorScheme.BRAND_ORANGE);
         final String codeBg = hex(ColorScheme.DARK_GRAY_COLOR.darker());
         final String rule = hex(ColorScheme.MEDIUM_GRAY_COLOR);
         // RuneLite registers its RuneScape fonts, so CSS can name them directly.
         final Font body = FontManager.getRunescapeFont();
         final Font bold = FontManager.getRunescapeBoldFont();
         final String bodyFont = "font-family: '" + body.getFamily() + "'; font-size: " + body.getSize() + "pt;";
         final String boldFont = "font-family: '" + bold.getFamily() + "'; font-size: " + bold.getSize() + "pt;";
         styles.addRule("body { " + bodyFont + " color: " + text + "; margin: 0; }");
         styles.addRule("p { margin-top: 0; margin-bottom: 6px; }");
         styles.addRule("h2 { " + boldFont + " color: " + accent + "; margin-top: 6px; margin-bottom: 3px; }");
         styles.addRule("h3 { " + boldFont + " color: " + accent + "; margin-top: 5px; margin-bottom: 2px; }");
         styles.addRule("ul, ol { margin-top: 0; margin-bottom: 6px; margin-left: 14px; }");
         styles.addRule("li { margin-bottom: 2px; }");
         styles.addRule("b { color: #ffffff; }");
         styles.addRule("a { color: " + accent + "; }");
         styles.addRule("code { font-family: monospace; background-color: " + codeBg + "; }");
         styles.addRule("pre { font-family: monospace; font-size: 9pt; background-color: " + codeBg
            + "; margin-top: 0; margin-bottom: 6px; }");
         styles.addRule(".quote { color: #a0a0a0; font-style: italic; margin-left: 6px; margin-bottom: 6px; }");
         styles.addRule("table { margin-bottom: 6px; border-color: " + rule + "; }");
         styles.addRule("th { " + boldFont + " color: " + accent + "; text-align: left; }");
         styles.addRule("td { " + bodyFont + " }");
      }

      @Override
      public StyleSheet getStyleSheet() {
         return styles;
      }
   }
}
