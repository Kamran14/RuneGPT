package com.kamrant.runegpt.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import javax.swing.JEditorPane;
import javax.swing.SwingUtilities;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.View;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.LinkBrowser;

/**
 * Read-only HTML view that wraps to whatever width its container gives it, so text never
 * runs off the edge of the sidebar and reflows when the panel is resized. Uses its own
 * stylesheet (Swing's default one is shared app-wide).
 */
public class HtmlPane extends JEditorPane {

   // Width used to estimate height before the first layout pass.
   private static final int FALLBACK_WIDTH = 160;

   private int lastWidth = -1;

   /** Body text in the standard RuneScape font. */
   public HtmlPane(final Color textColor) {
      this(textColor, FontManager.getRunescapeFont());
   }

   /** Body text in the given font (e.g. bold for titles, small for hints). */
   public HtmlPane(final Color textColor, final Font bodyFont) {
      setEditorKit(new HtmlStyleKit(textColor, bodyFont));
      // RuneLite's look-and-feel makes HTML panes use the component's font and colour for the body,
      // so set them here too (the stylesheet still styles headings, bold, links etc.).
      putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
      setFont(bodyFont);
      setForeground(textColor);
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
      // When the real width changes (first layout, or the player resizes the panel), recompute height.
      addComponentListener(new ComponentAdapter() {
         @Override
         public void componentResized(final ComponentEvent e) {
            if (getWidth() != lastWidth) {
               lastWidth = getWidth();
               SwingUtilities.invokeLater(() -> {
                  revalidate();
                  repaint();
               });
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

   /** Sets plain text (escaped, newlines kept). */
   public void setPlainText(final String text) {
      setHtml(MarkdownRenderer.escape(text).replace("\n", "<br>"));
   }

   @Override
   public Dimension getPreferredSize() {
      final Insets in = getInsets();
      int width = getWidth();
      if (width <= 0 && getParent() != null) {
         final Insets pin = getParent().getInsets();
         width = getParent().getWidth() - pin.left - pin.right;
      }
      if (width <= 0) {
         width = FALLBACK_WIDTH;
      }
      final View root = getUI().getRootView(this);
      root.setSize(Math.max(1, width - in.left - in.right), Integer.MAX_VALUE);
      final int height = (int) Math.ceil(root.getPreferredSpan(View.Y_AXIS)) + in.top + in.bottom;
      // Small preferred width: let the layout decide the width, we only report the height for it.
      return new Dimension(40, height);
   }

   @Override
   public Dimension getMinimumSize() {
      return new Dimension(20, getPreferredSize().height);
   }

   @Override
   public Dimension getMaximumSize() {
      return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
   }
}
