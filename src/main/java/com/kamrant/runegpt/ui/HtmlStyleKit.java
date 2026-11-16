package com.kamrant.runegpt.ui;

import java.awt.Color;
import java.awt.Font;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * HTML editor kit with its own stylesheet for {@link HtmlPane} (Swing's default stylesheet is shared
 * app-wide, so it must not be modified). Styles headings, lists, bold, links and table records.
 */
final class HtmlStyleKit extends HTMLEditorKit {

   private final StyleSheet styles = new StyleSheet();

   HtmlStyleKit(final Color textColor, final Font bodyFont) {
      styles.addStyleSheet(super.getStyleSheet());
      final String text = hex(textColor);
      final String accent = hex(ColorScheme.BRAND_ORANGE);
      final String muted = hex(ColorScheme.LIGHT_GRAY_COLOR);
      final String codeBg = hex(ColorScheme.DARK_GRAY_COLOR.darker());
      // RuneLite registers its RuneScape fonts, so CSS can name them directly.
      final String body = css(bodyFont);
      final String bold = css(FontManager.getRunescapeBoldFont());
      final String small = css(FontManager.getRunescapeSmallFont());
      styles.addRule("body { " + body + " color: " + text + "; margin: 0; }");
      styles.addRule("p { margin-top: 0; margin-bottom: 5px; }");
      styles.addRule("h2 { " + bold + " color: " + accent + "; margin-top: 6px; margin-bottom: 3px; }");
      styles.addRule("h3 { " + bold + " color: " + accent + "; margin-top: 5px; margin-bottom: 2px; }");
      styles.addRule("ul, ol { margin-top: 0; margin-bottom: 5px; margin-left: 12px; }");
      styles.addRule("li { margin-bottom: 2px; }");
      styles.addRule("b { color: #ffffff; }");
      styles.addRule("a { color: " + accent + "; }");
      styles.addRule("code { background-color: " + codeBg + "; }");
      styles.addRule("pre { " + small + " background-color: " + codeBg + "; margin-top: 0; margin-bottom: 5px; }");
      styles.addRule(".quote { color: #a0a0a0; font-style: italic; margin-left: 6px; margin-bottom: 5px; }");
      styles.addRule("table { margin-bottom: 5px; }");
      styles.addRule("th { " + bold + " color: " + accent + "; text-align: left; }");
      styles.addRule("td { " + body + " }");
      // Rows of a wide table, re-laid out as stacked records.
      styles.addRule(".rec { margin-top: 0; margin-bottom: 5px; }");
      styles.addRule(".lbl { " + small + " color: " + muted + "; }");
      styles.addRule(".muted { " + small + " color: " + muted + "; }");
   }

   @Override
   public StyleSheet getStyleSheet() {
      return styles;
   }

   private static String hex(final Color c) {
      return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
   }

   private static String css(final Font f) {
      return "font-family: '" + f.getFamily() + "'; font-size: " + f.getSize() + "pt;";
   }
}
