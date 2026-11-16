package com.kamrant.runegpt.model;

import java.util.Collections;
import java.util.Map;

/**
 * What the OSRS Wiki says about an item: its canonical name, whether/where it's worn, its weapon category
 * (the wiki's "combatstyle", e.g. "Whip", "Crossbow", "Powered Staff") and the levels needed to wear it.
 */
public final class WikiItem {

   private final String name;
   private final boolean equipable;
   private final String slot;
   private final String combatStyle;
   private final Map<String, Integer> requirements;

   public WikiItem(final String name, final boolean equipable, final String slot) {
      this(name, equipable, slot, null, Collections.emptyMap());
   }

   public WikiItem(final String name, final boolean equipable, final String slot, final String combatStyle,
      final Map<String, Integer> requirements) {
      this.name = name;
      this.equipable = equipable;
      this.slot = slot;
      this.combatStyle = combatStyle;
      this.requirements = Collections.unmodifiableMap(requirements);
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

   /** Weapon category as written on the wiki (e.g. "Slash Sword"); null for non-weapons or unknown. */
   public String getCombatStyle() {
      return combatStyle;
   }

   /** Skill name (e.g. "Attack") to level needed to wear or wield it, as stated on the wiki. May be empty. */
   public Map<String, Integer> getRequirements() {
      return requirements;
   }
}
