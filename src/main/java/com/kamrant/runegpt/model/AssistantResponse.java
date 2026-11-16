package com.kamrant.runegpt.model;

/** A parsed AI reply: concise Markdown instructions plus an optional trackable guide, or an error. */
public final class AssistantResponse {

   private final boolean success;
   private final String answer;
   private final GuidePlan guide;
   private final String provider;

   private AssistantResponse(final boolean success, final String answer, final GuidePlan guide, final String provider) {
      this.success = success;
      this.answer = answer;
      this.guide = guide;
      this.provider = provider;
   }

   public static AssistantResponse of(final String answer, final GuidePlan guide, final String provider) {
      return new AssistantResponse(true, answer, guide, provider);
   }

   public static AssistantResponse failure(final String message) {
      return new AssistantResponse(false, message, null, null);
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

   public String getProvider() {
      return provider;
   }
}
