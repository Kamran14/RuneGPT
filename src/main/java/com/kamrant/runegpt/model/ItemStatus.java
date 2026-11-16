package com.kamrant.runegpt.model;

import com.kamrant.runegpt.ui.UiTheme;
import java.awt.Color;

/**
 * Where a required item is relative to the player, with the colour used in the panel and overlay:
 * green = ready, yellow = in inventory but needs equipping, red = missing or still in the bank.
 */
public enum ItemStatus {
   /** In the inventory (or worn) and that's all it needs. */
   READY("ready", UiTheme.READY_GREEN),
   /** Equipped, as the guide requires. */
   EQUIPPED("equipped", UiTheme.READY_GREEN),
   /** Gear that's in the inventory but needs to be equipped. */
   EQUIP_IT("equip it", UiTheme.EQUIP_YELLOW),
   /** Only in the bank. */
   IN_BANK("in bank", UiTheme.MISSING_RED),
   /** Not in inventory, equipment or (known) bank. */
   MISSING("missing", UiTheme.MISSING_RED),
   /** Can't tell: bank not seen yet and not carried. */
   UNKNOWN("not checked", UiTheme.MISSING_RED);

   private final String label;
   private final Color color;

   ItemStatus(final String label, final Color color) {
      this.label = label;
      this.color = color;
   }

   public String getLabel() {
      return label;
   }

   public Color getColor() {
      return color;
   }

   public boolean isReady() {
      return this == READY || this == EQUIPPED;
   }
}
