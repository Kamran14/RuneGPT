package com.kamrant.runegpt.model;

/** The three combat styles the gear optimiser compares. */
public enum CombatStyle {
   MELEE("Melee"),
   RANGED("Ranged"),
   MAGIC("Magic");

   private final String label;

   CombatStyle(final String label) {
      this.label = label;
   }

   public String getLabel() {
      return label;
   }

   /** Matches a quick-choice chip ("Melee", "Ranged", "Magic"); null for anything else (e.g. "Best"). */
   public static CombatStyle fromLabel(final String label) {
      if (label == null) {
         return null;
      }
      for (final CombatStyle s : values()) {
         if (s.label.equalsIgnoreCase(label.trim())) {
            return s;
         }
      }
      return null;
   }
}
