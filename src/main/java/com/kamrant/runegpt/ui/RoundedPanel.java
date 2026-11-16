package com.kamrant.runegpt.ui;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.RenderingHints;
import javax.swing.JPanel;

/** Panel with a rounded, optionally outlined background. */
public class RoundedPanel extends JPanel {

   private final int arc;
   private Color outline;

   public RoundedPanel(final LayoutManager layout, final Color background, final int arc) {
      super(layout);
      this.arc = arc;
      setBackground(background);
      setOpaque(false);
   }

   public void setOutline(final Color outline) {
      this.outline = outline;
      repaint();
   }

   @Override
   protected void paintComponent(final Graphics g) {
      final Graphics2D g2 = (Graphics2D) g.create();
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2.setColor(getBackground());
      g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
      if (outline != null) {
         g2.setColor(outline);
         g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
      }
      g2.dispose();
      super.paintComponent(g);
   }
}
