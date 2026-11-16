package com.kamrant.runegpt.model.request;

import com.google.gson.annotations.SerializedName;

/** Gemini generation settings. Replies are requested as JSON (see Prompts.SYSTEM for the shape). */
public final class GeminiGenerationConfig {

   @SerializedName("temperature")
   private final double temperature;
   @SerializedName("topK")
   private final int topK = 40;
   @SerializedName("topP")
   private final double topP = 0.95;
   @SerializedName("maxOutputTokens")
   private final int maxOutputTokens = 2048;
   @SerializedName("responseMimeType")
   private final String responseMimeType = "application/json";

   public GeminiGenerationConfig(final double temperature) {
      this.temperature = temperature;
   }
}
