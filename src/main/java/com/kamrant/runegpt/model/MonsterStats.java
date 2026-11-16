package com.kamrant.runegpt.model;

import java.util.Collections;
import java.util.Set;

/** A monster's combat stats from its OSRS Wiki infobox (first version on the page). Immutable. */
public final class MonsterStats {

   private final String name;
   private final int combatLevel;
   private final int hitpoints;
   private final int defenceLevel;
   private final int magicLevel;
   private final int magicAttack;
   private final int defStab;
   private final int defSlash;
   private final int defCrush;
   private final int defMagic;
   private final int defLight;
   private final int defStandard;
   private final int defHeavy;
   private final Set<String> attributes;
   private final String weakness;
   private final int weaknessPercent;

   public MonsterStats(final String name, final int combatLevel, final int hitpoints, final int defenceLevel,
      final int magicLevel, final int magicAttack, final int defStab, final int defSlash, final int defCrush,
      final int defMagic, final int defLight, final int defStandard, final int defHeavy, final Set<String> attributes,
      final String weakness, final int weaknessPercent) {
      this.name = name;
      this.combatLevel = combatLevel;
      this.hitpoints = hitpoints;
      this.defenceLevel = defenceLevel;
      this.magicLevel = magicLevel;
      this.magicAttack = magicAttack;
      this.defStab = defStab;
      this.defSlash = defSlash;
      this.defCrush = defCrush;
      this.defMagic = defMagic;
      this.defLight = defLight;
      this.defStandard = defStandard;
      this.defHeavy = defHeavy;
      this.attributes = Collections.unmodifiableSet(attributes);
      this.weakness = weakness;
      this.weaknessPercent = weaknessPercent;
   }

   /** Wiki page title. */
   public String getName() {
      return name;
   }

   public int getCombatLevel() {
      return combatLevel;
   }

   public int getHitpoints() {
      return hitpoints;
   }

   public int getDefenceLevel() {
      return defenceLevel;
   }

   public int getMagicLevel() {
      return magicLevel;
   }

   /** The monster's magic attack bonus (used by the Twisted bow). */
   public int getMagicAttack() {
      return magicAttack;
   }

   public int getDefStab() {
      return defStab;
   }

   public int getDefSlash() {
      return defSlash;
   }

   public int getDefCrush() {
      return defCrush;
   }

   public int getDefMagic() {
      return defMagic;
   }

   /** Ranged defence against darts, knives and other light ammo. */
   public int getDefLight() {
      return defLight;
   }

   /** Ranged defence against arrows. */
   public int getDefStandard() {
      return defStandard;
   }

   /** Ranged defence against bolts, javelins and chinchompas. */
   public int getDefHeavy() {
      return defHeavy;
   }

   /** Lower-case wiki attributes, e.g. "dragon", "undead", "demon". */
   public Set<String> getAttributes() {
      return attributes;
   }

   public boolean is(final String attribute) {
      return attributes.contains(attribute);
   }

   /** Elemental weakness ("Water", "Fire", "Earth", "Air"), or null. */
   public String getWeakness() {
      return weakness;
   }

   /** Extra spell damage (percent) for the weakness element. */
   public int getWeaknessPercent() {
      return weaknessPercent;
   }
}
