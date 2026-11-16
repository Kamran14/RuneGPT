package com.kamrant.runegpt.model;

import java.util.List;

/** Outcome of matching typed text to a real quest name. */
public final class QuestMatch {

   private final String questName;
   private final List<String> suggestions;

   public QuestMatch(final String questName, final List<String> suggestions) {
      this.questName = questName;
      this.suggestions = suggestions;
   }

   /** Exact quest name, or null if the input isn't a quest. */
   public String getQuestName() {
      return questName;
   }

   /** Closest real quest names when there's no exact match. */
   public List<String> getSuggestions() {
      return suggestions;
   }
}
