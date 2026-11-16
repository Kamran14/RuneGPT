package com.kamrant.runegpt.guides;

public enum GuideCategory {
   QUESTS("Quests", "Quests", "Quest Helper"),
   MONSTERS("Monsters", "Monsters & Slayer", "Combat & Slayer"),
   BOSSES("Bosses", "Bosses", "Boss Prep"),
   SKILLING("Skilling", "Skilling", "Skill Trainer"),
   MONEY("Money", "Money making", "Money Maker"),
   MINIGAMES("Minigames", "Minigames", "Minigames");

   private final String shortName;
   private final String fullName;
   private final String sectionName;

   GuideCategory(final String shortName, final String fullName, final String sectionName) {
      this.shortName = shortName;
      this.fullName = fullName;
      this.sectionName = sectionName;
   }

   /** Heading shown on the collapsible section in the panel. */
   public String getSectionName() {
      return sectionName;
   }

   public String getShortName() {
      return shortName;
   }

   public String getFullName() {
      return fullName;
   }
}
