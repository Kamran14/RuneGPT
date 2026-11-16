package com.kamrant.runegpt.model;

import java.awt.Color;

/**
 * Where a required item is relative to the player, with the colour used in the panel and overlay:
 * green = ready, yellow = in inventory but needs equipping, red = missing or still in the bank.
 */
public enum ItemStatus {
   /** In the inventory (or worn) and that's all it needs. */
   READY("ready", new Color(90, 200, 90)),
   /** Equipped, as the guide requires. */
   EQUIPPED("equipped", new Color(90, 200, 90)),
   /** Gear that's in the inventory but needs to be equipped. */
   EQUIP_IT("equip it", new Color(240, 200, 60)),
   /** Only in the bank. */
   IN_BANK("in bank", new Color(225, 85, 70)),
   /** Not in inventory, equipment or (known) bank. */
   MISSING("missing", new Color(225, 85, 70)),
   /** Can't tell: bank not seen yet and not carried. */
   UNKNOWN("not checked", new Color(225, 85, 70));

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
