package com.kamrant.runegpt.model;

/** Where an owned item is right now. */
public enum ItemSource {
   WORN("worn"),
   INVENTORY("inventory"),
   BANK("bank");

   private final String label;

   ItemSource(final String label) {
      this.label = label;
   }

   public String getLabel() {
      return label;
   }
}
