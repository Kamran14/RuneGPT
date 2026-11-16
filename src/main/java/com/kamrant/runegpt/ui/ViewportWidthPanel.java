package com.kamrant.runegpt.ui;

import java.awt.Dimension;
import java.awt.Rectangle;
import javax.swing.JPanel;
import javax.swing.Scrollable;

/** Scroll pane content that always matches the viewport width, so nothing scrolls sideways. */
public class ViewportWidthPanel extends JPanel implements Scrollable {

   private static final int UNIT_INCREMENT = 16;

   @Override
   public Dimension getPreferredScrollableViewportSize() {
      return getPreferredSize();
   }

   @Override
   public int getScrollableUnitIncrement(final Rectangle visibleRect, final int orientation, final int direction) {
      return UNIT_INCREMENT;
   }

   @Override
   public int getScrollableBlockIncrement(final Rectangle visibleRect, final int orientation, final int direction) {
      return visibleRect.height;
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
