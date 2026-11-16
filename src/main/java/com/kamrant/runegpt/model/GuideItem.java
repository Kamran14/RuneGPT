package com.kamrant.runegpt.model;

/** An item a guide needs, with its current status for the player and its live GE price. Immutable. */
public final class GuideItem {

   private final String name;
   private final int quantity;
   private final GuideItemKind kind;
   private final String slot;
   private final ItemStatus status;
   private final long price;

   public GuideItem(final String name, final int quantity, final GuideItemKind kind, final String slot,
      final ItemStatus status) {
      this(name, quantity, kind, slot, status, 0);
   }

   private GuideItem(final String name, final int quantity, final GuideItemKind kind, final String slot,
      final ItemStatus status, final long price) {
      this.name = name;
      this.quantity = Math.max(1, quantity);
      this.kind = kind;
      this.slot = slot;
      this.status = status;
      this.price = Math.max(0, price);
   }

   public GuideItem withStatus(final ItemStatus newStatus) {
      return new GuideItem(name, quantity, kind, slot, newStatus, price);
   }

   public GuideItem withPrice(final long newPrice) {
      return new GuideItem(name, quantity, kind, slot, status, newPrice);
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

   /** Live Grand Exchange price for one, in gp; 0 when unknown or untradeable. */
   public long getPrice() {
      return price;
   }

   /** Price for the whole quantity, in gp; 0 when unknown. */
   public long getTotalPrice() {
      return price * quantity;
   }
}
