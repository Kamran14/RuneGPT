package com.kamrant.runegpt.model;

/** An item a guide needs, with its current status for the player. Immutable. */
public final class GuideItem {

   private final String name;
   private final int quantity;
   private final GuideItemKind kind;
   private final String slot;
   private final ItemStatus status;

   public GuideItem(final String name, final int quantity, final GuideItemKind kind, final String slot,
      final ItemStatus status) {
      this.name = name;
      this.quantity = Math.max(1, quantity);
      this.kind = kind;
      this.slot = slot;
      this.status = status;
   }

   public GuideItem withStatus(final ItemStatus newStatus) {
      return new GuideItem(name, quantity, kind, slot, newStatus);
   }

   public String getName() {
      return name;
   }

   public int getQuantity() {
      return quantity;
   }

   public GuideItemKind getKind() {
      return kind;
   }

   /** Equipment slot from the wiki (e.g. "weapon", "head"), or null for carried items. */
   public String getSlot() {
      return slot;
   }

   public ItemStatus getStatus() {
      return status;
   }
}
