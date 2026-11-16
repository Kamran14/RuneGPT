package com.kamrant.runegpt.model;

import net.runelite.client.game.ItemEquipmentStats;

/**
 * An equipable item the player owns, with its combat bonuses from RuneLite's item stats. Captured on the
 * client thread by the player snapshot. Immutable.
 */
public final class OwnedGear {

   private final int id;
   private final String name;
   private final ItemEquipmentStats stats;
   private final ItemSource source;

   public OwnedGear(final int id, final String name, final ItemEquipmentStats stats, final ItemSource source) {
      this.id = id;
      this.name = name;
      this.stats = stats;
      this.source = source;
   }

   public int getId() {
      return id;
   }

   public String getName() {
      return name;
   }

   public ItemEquipmentStats getStats() {
      return stats;
   }

   public ItemSource getSource() {
      return source;
   }

   /** Equipment slot index (see {@link net.runelite.api.EquipmentInventorySlot}). */
   public int getSlot() {
      return stats.getSlot();
   }
}
