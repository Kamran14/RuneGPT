package com.kamrant.runegpt.model;

/** Connection state plus a short label, e.g. "Connected (Groq)". */
public final class ProviderStatus {

   private final ProviderStatusState state;
   private final String text;

   public ProviderStatus(final ProviderStatusState state, final String text) {
      this.state = state;
      this.text = text;
   }

   public ProviderStatusState getState() {
      return state;
   }

   public String getText() {
      return text;
   }
}
