package com.kamrant.runegpt.model;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * The best setup the optimiser found for one combat style: the items per slot (all owned by the player),
 * how to attack, and the estimated max hit, accuracy and damage per second against the target. Immutable.
 */
public final class GearSetup {

   private final CombatStyle style;
   private final Map<Integer, OwnedGear> items;
   private final String attack;
   private final int maxHit;
   private final double hitChance;
   private final double dps;
   private final int speedTicks;
   private final List<String> carry;

   /**
    * @param items  slot index to item, in display order
    * @param attack how to attack, e.g. "Slash, aggressive" or "Fire Wave"
    * @param carry  supplies the setup needs (runes, darts), by exact item name
    */
   public GearSetup(final CombatStyle style, final Map<Integer, OwnedGear> items, final String attack, final int maxHit,
      final double hitChance, final double dps, final int speedTicks, final List<String> carry) {
      this.style = style;
      this.items = Collections.unmodifiableMap(items);
      this.attack = attack;
      this.maxHit = maxHit;
      this.hitChance = hitChance;
      this.dps = dps;
      this.speedTicks = speedTicks;
      this.carry = Collections.unmodifiableList(carry);
   }

   public CombatStyle getStyle() {
      return style;
   }

   public Map<Integer, OwnedGear> getItems() {
      return items;
   }

   public String getAttack() {
      return attack;
   }

   public int getMaxHit() {
      return maxHit;
   }

   /** Chance that an attack hits, 0..1. */
   public double getHitChance() {
      return hitChance;
   }

   /** Average damage per second, including misses. */
   public double getDps() {
      return dps;
   }

   public int getSpeedTicks() {
      return speedTicks;
   }

   public List<String> getCarry() {
      return carry;
   }

   /** The weapon's name, or "Unarmed". */
   public String getWeaponName() {
      final OwnedGear w = items.get(net.runelite.api.EquipmentInventorySlot.WEAPON.getSlotIdx());
      return w == null ? "Unarmed" : w.getName();
   }
}
