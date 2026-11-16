package com.kamrant.runegpt.model.request;

import com.google.gson.annotations.SerializedName;

/** A message in an Anthropic Messages API request. */
public final class ClaudeMessage {

   @SerializedName("role")
   private final String role;
   @SerializedName("content")
   private final String content;

   public ClaudeMessage(final String role, final String content) {
      this.role = role;
      this.content = content;
   }
}
