package com.kamrant.runegpt.model.request;

import com.google.gson.annotations.SerializedName;

/** A text part of a Gemini message. */
public final class GeminiPart {

   @SerializedName("text")
   private final String text;

   public GeminiPart(final String text) {
      this.text = text;
   }
}
