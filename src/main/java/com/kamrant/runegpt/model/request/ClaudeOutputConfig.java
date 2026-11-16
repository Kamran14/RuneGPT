package com.kamrant.runegpt.model.request;

import com.google.gson.annotations.SerializedName;

/** Claude output settings (effort level). */
public final class ClaudeOutputConfig {

   @SerializedName("effort")
   private final String effort;

   public ClaudeOutputConfig(final String effort) {
      this.effort = effort;
   }
}
