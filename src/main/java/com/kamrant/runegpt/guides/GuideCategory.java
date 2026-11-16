package com.kamrant.runegpt.guides;

public enum GuideCategory {
   QUESTS("Quests", "Quests"),
   MONSTERS("Monsters", "Monsters & Slayer"),
   BOSSES("Bosses", "Bosses"),
   SKILLING("Skilling", "Skilling"),
   MONEY("Money", "Money making"),
   MINIGAMES("Minigames", "Minigames");

   private final String shortName;
   private final String fullName;

   GuideCategory(final String shortName, final String fullName) {
      this.shortName = shortName;
      this.fullName = fullName;
   }

   public String getShortName() {
      return shortName;
   }

   public String getFullName() {
      return fullName;
   }
}
