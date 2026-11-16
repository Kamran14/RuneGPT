package com.kamrant.runegpt.model.request;

import com.google.gson.annotations.SerializedName;

/** A message in a Pollinations text request (role system, user or assistant). */
public final class PollinationsMessage {

   @SerializedName("role")
   private final String role;
   @SerializedName("content")
   private final String content;

   public PollinationsMessage(final String role, final String content) {
      this.role = role;
      this.content = content;
   }
}
