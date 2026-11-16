package com.kamrant.runegpt.model;

import java.util.Collections;
import java.util.List;

/** A completed chat turn: the Markdown answer, optional task suggestions, and an optional verified guide. */
public final class AssistantResponse {

   private final boolean success;
   private final String answer;
   private final GuidePlan guide;
   private final List<TaskSuggestion> suggestions;

   private AssistantResponse(final boolean success, final String answer, final GuidePlan guide,
      final List<TaskSuggestion> suggestions) {
      this.success = success;
      this.answer = answer;
      this.guide = guide;
      this.suggestions = Collections.unmodifiableList(suggestions);
   }

   public static AssistantResponse of(final String answer, final GuidePlan guide) {
      return new AssistantResponse(true, answer, guide, Collections.emptyList());
   }

   public static AssistantResponse of(final String answer, final GuidePlan guide,
      final List<TaskSuggestion> suggestions) {
      return new AssistantResponse(true, answer, guide,
         suggestions == null ? Collections.emptyList() : suggestions);
   }

   public static AssistantResponse failure(final String errorMessage) {
      return new AssistantResponse(false, errorMessage, null, Collections.emptyList());
   }

   public boolean isSuccess() {
      return success;
   }

   public String getAnswer() {
      return answer;
   }

   /** Null when the answer is informational and has nothing to track. */
   public GuidePlan getGuide() {
      return guide;
   }

   /** Suggested tasks/methods the player can start and be guided through step by step. */
   public List<TaskSuggestion> getSuggestions() {
      return suggestions;
   }
}
