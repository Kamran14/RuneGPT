package com.kamrant.runegpt.model;

/**
 * One actionable suggestion in an assistant reply (a quest, minigame, money maker, skilling method, boss or
 * monster task) that the player can launch into a step-by-step guided task with one click. Immutable.
 */
public final class TaskSuggestion {

   private final String title;
   private final String category;
   private final String summary;

   public TaskSuggestion(final String title, final String category, final String summary) {
      this.title = title;
      this.category = category == null ? "" : category;
      this.summary = summary == null ? "" : summary;
   }

   /** Short name of the task, e.g. "Guardians of the Rift", "Barrows", "Herb runs (Ranarr)". */
   public String getTitle() {
      return title;
   }

   /** Category hint ("quest", "minigame", "money", "skill", "boss", "monster"), or empty. */
   public String getCategory() {
      return category;
   }

   /** Short 1-line description (e.g. "~600K gp/hr · low effort" or "Unlocks fairy rings"), or empty. */
   public String getSummary() {
      return summary;
   }
}
