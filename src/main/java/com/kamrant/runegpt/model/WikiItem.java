package com.kamrant.runegpt.model;

/** What the OSRS Wiki says about an item: its canonical name and whether/where it's worn. */
public final class WikiItem {

   private final String name;
   private final boolean equipable;
   private final String slot;

   public WikiItem(final String name, final boolean equipable, final String slot) {
      this.name = name;
      this.equipable = equipable;
      this.slot = slot;
   }

   /** Page title, i.e. the item's real in-game name. */
   public String getName() {
      return name;
   }

   public boolean isEquipable() {
      return equipable;
   }

   /** Equipment slot such as "weapon" or "head"; null if not equipable or unknown. */
   public String getSlot() {
      return slot;
   }
}
