package com.kamrant.runegpt.model;

/** A parsed AI reply: concise Markdown instructions plus an optional trackable guide, or an error. */
public final class AssistantResponse {

   private final boolean success;
   private final String answer;
   private final GuidePlan guide;

   private AssistantResponse(final boolean success, final String answer, final GuidePlan guide) {
      this.success = success;
      this.answer = answer;
      this.guide = guide;
   }

   public static AssistantResponse of(final String answer, final GuidePlan guide) {
      return new AssistantResponse(true, answer, guide);
   }

   public static AssistantResponse failure(final String message) {
      return new AssistantResponse(false, message, null);
   }

   public boolean isSuccess() {
      return success;
   }

   /** Markdown answer, or the error message when not successful. */
   public String getAnswer() {
      return answer;
   }

   /** Null when the reply has nothing to track (e.g. a plain question). */
   public GuidePlan getGuide() {
      return guide;
   }

}
