package com.kamrant.runegpt.overlay;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/** Draws the pin used for the guide's world map marker (cyan/orange = from wiki, grey = AI estimate). */
public final class MapMarkerImage {

   private static final int SIZE = 22;

   private MapMarkerImage() {
   }

   public static BufferedImage create(final boolean approximate) {
      final BufferedImage img = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
      final Graphics2D g = img.createGraphics();
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

      final Color main = approximate ? new Color(180, 180, 180) : new Color(80, 220, 255);
      // Teardrop pointer down to the bottom center of the icon.
      final Polygon pointer = new Polygon(
         new int[] { 5, SIZE - 5, SIZE / 2 },
         new int[] { 11, 11, SIZE - 1 },
         3);
      g.setColor(main);
      g.fillPolygon(pointer);
      g.fillOval(3, 1, SIZE - 6, SIZE - 6);

      g.setColor(Color.BLACK);
      g.setStroke(new BasicStroke(2f));
      g.drawOval(3, 1, SIZE - 6, SIZE - 6);
      g.drawLine(5, 12, SIZE / 2, SIZE - 1);
      g.drawLine(SIZE - 5, 12, SIZE / 2, SIZE - 1);

      g.setColor(Color.WHITE);
      g.fillOval(SIZE / 2 - 3, 6, 6, 6);
      g.dispose();
      return img;
   }
}
