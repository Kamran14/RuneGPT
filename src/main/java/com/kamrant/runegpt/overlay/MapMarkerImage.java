package com.kamrant.runegpt.overlay;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/** Draws the pin used for the guide's world map marker (orange = from wiki, grey = AI estimate). */
public final class MapMarkerImage {

   private static final int SIZE = 18;

   private MapMarkerImage() {
   }

   public static BufferedImage create(final boolean approximate) {
      final BufferedImage img = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
      final Graphics2D g = img.createGraphics();
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.setColor(approximate ? new Color(170, 170, 170) : new Color(255, 152, 31));
      g.fillOval(2, 2, SIZE - 4, SIZE - 4);
      g.setColor(Color.BLACK);
      g.setStroke(new BasicStroke(2f));
      g.drawOval(2, 2, SIZE - 4, SIZE - 4);
      g.setColor(Color.WHITE);
      g.fillOval(SIZE / 2 - 2, SIZE / 2 - 2, 4, 4);
      g.dispose();
      return img;
   }
}
