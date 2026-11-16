package com.kamrant.runegpt.model.request;

import com.google.gson.annotations.SerializedName;

/** A message in Groq's OpenAI-compatible chat format (role system, user or assistant). */
public final class GroqMessage {

   @SerializedName("role")
   private final String role;
   @SerializedName("content")
   private final String content;

   public GroqMessage(final String role, final String content) {
      this.role = role;
      this.content = content;
   }
}
