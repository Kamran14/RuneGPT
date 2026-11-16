package com.kamrant.runegpt.model;

import com.kamrant.runegpt.config.LLMProvider;

/** Result of an LLM request: the reply text and which provider gave it, or an error after all providers failed. */
public final class LlmReply {

   private final boolean success;
   private final String text;
   private final LLMProvider provider;

   private LlmReply(final boolean success, final String text, final LLMProvider provider) {
      this.success = success;
      this.text = text;
      this.provider = provider;
   }

   public static LlmReply success(final String text, final LLMProvider provider) {
      return new LlmReply(true, text, provider);
   }

   public static LlmReply failure(final String message) {
      return new LlmReply(false, message, null);
   }

   public boolean isSuccess() {
      return success;
   }

   public String getText() {
      return text;
   }

   /** The provider that answered; null on failure. */
   public LLMProvider getProvider() {
      return provider;
   }
}
