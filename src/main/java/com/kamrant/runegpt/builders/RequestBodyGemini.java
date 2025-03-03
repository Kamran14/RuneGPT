package com.kamrant.runegpt.builders;

import com.google.gson.annotations.SerializedName;

public class RequestBodyGemini{

   @SerializedName("contents")
   private final Content[] contents;
   @SerializedName("generationConfig")
   private final GenerationConfig generationConfig;

   public RequestBodyGemini(final Content[] content, final GenerationConfig generationConfig) {
      this.contents = content;
      this.generationConfig = generationConfig;
   }

   public static class Content {
      @SerializedName("role")
      private final String role;
      @SerializedName("parts")
      private final Part[] parts;

      public Content(final String role, final Part[] parts) {
         this.role = role;
         this.parts = parts;
      }
   }

   public static class Part {
      @SerializedName("text")
      private final String text;

      public Part(final String text) {
         this.text = text;
      }
   }

   public static class GenerationConfig {
      /*
       * Eventually these can all be configurable, but for now they are hardcoded.
       * Maybe some presets to help creative answers vs definitive answers.
       */
      @SerializedName("temperature")
      private final double temperature;
      @SerializedName("topK")
      private final int topK = 40;
      @SerializedName("topP")
      private final double topP = 0.95;
      @SerializedName("maxOutputTokens")
      private final int maxOutputTokens = 1000;
      @SerializedName("responseMimeType")
      private final String responseMimeType = "text/plain";

      public GenerationConfig(final double temperature) {
         this.temperature = temperature;
      }
   }

   public static RequestBodyGemini createRequestBody(final String fullPrompt, final double temp) {
      final Part part = new Part(fullPrompt);
      final Content content = new Content("user", new Part[] { part });
      final GenerationConfig generationConfig = new GenerationConfig(temp);
      final RequestBodyGemini requestBodyData = new RequestBodyGemini(new Content[] { content }, generationConfig);
  
      return requestBodyData;
   }
}