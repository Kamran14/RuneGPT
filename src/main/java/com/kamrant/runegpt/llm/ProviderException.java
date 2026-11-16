package com.kamrant.runegpt.llm;

import java.time.Duration;

/**
 * A provider could not answer. {@code quotaExhausted} marks rate-limit / out-of-credit
 * failures, which put the provider on a cooldown so later prompts skip straight past it.
 */
public class ProviderException extends Exception {

   private final boolean quotaExhausted;
   private final Duration retryAfter;

   public ProviderException(final String message) {
      this(message, false, null);
   }

   public ProviderException(final String message, final boolean quotaExhausted, final Duration retryAfter) {
      super(message);
      this.quotaExhausted = quotaExhausted;
      this.retryAfter = retryAfter;
   }

   public boolean isQuotaExhausted() {
      return quotaExhausted;
   }

   public Duration getRetryAfter() {
      return retryAfter;
   }
}
