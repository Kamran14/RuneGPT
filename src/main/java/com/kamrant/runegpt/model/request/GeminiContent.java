package com.kamrant.runegpt.model.request;

import com.google.gson.annotations.SerializedName;

/** A Gemini message (role "user" or "model"), or the system instruction when role is null. */
public final class GeminiContent {

   // Null for systemInstruction; Gson omits it.
   @SerializedName("role")
   private final String role;
   @SerializedName("parts")
   private final GeminiPart[] parts;

   public GeminiContent(final String role, final String text) {
      this.role = role;
      this.parts = new GeminiPart[] { new GeminiPart(text) };
   }
}
