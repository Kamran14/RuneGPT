package com.kamrant.runegpt.ui;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JTextArea;
import net.runelite.client.ui.ColorScheme;

/** Text area that shows a grey hint while empty. */
public class PlaceholderTextArea extends JTextArea {

   private final String placeholder;

   public PlaceholderTextArea(final String placeholder) {
      this.placeholder = placeholder;
   }

   @Override
   protected void paintComponent(final Graphics g) {
      super.paintComponent(g);
      if (getDocument().getLength() == 0) {
         final Graphics2D g2 = (Graphics2D) g.create();
         g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
         g2.setColor(ColorScheme.MEDIUM_GRAY_COLOR);
         g2.setFont(getFont());
         g2.drawString(placeholder, getInsets().left, getInsets().top + g2.getFontMetrics().getAscent());
         g2.dispose();
      }
   }
}
