package com.kamrant.runegpt.llm;

import java.time.Duration;

/**
 * A provider could not answer. {@code quotaExhausted} marks rate-limit / out-of-credit
 * failures, which put the provider on a cooldown so later prompts skip straight past it.
 */
public class ProviderException extends Exception {

   private final boolean quotaExhausted;
   private final Duration retryAfter;
   // HTTP status of the failed response, or 0 when the failure wasn't an HTTP error.
   private final int httpStatus;

   public ProviderException(final String message) {
      this(message, false, null);
   }

   public ProviderException(final String message, final boolean quotaExhausted, final Duration retryAfter) {
      this(message, quotaExhausted, retryAfter, 0);
   }

   public ProviderException(final String message, final boolean quotaExhausted, final Duration retryAfter,
      final int httpStatus) {
      super(message);
      this.quotaExhausted = quotaExhausted;
      this.retryAfter = retryAfter;
      this.httpStatus = httpStatus;
   }

   public int getHttpStatus() {
      return httpStatus;
   }

   public boolean isQuotaExhausted() {
      return quotaExhausted;
   }

   public Duration getRetryAfter() {
      return retryAfter;
   }
}
