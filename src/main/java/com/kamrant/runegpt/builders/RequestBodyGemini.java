package com.kamrant.runegpt.builders;

import com.google.gson.annotations.SerializedName;
import com.kamrant.runegpt.llm.ChatMessage;
import java.util.List;

public class RequestBodyGemini{

   @SerializedName("systemInstruction")
   private final Content systemInstruction;
   @SerializedName("contents")
   private final Content[] contents;
   @SerializedName("generationConfig")
   private final GenerationConfig generationConfig;

   public RequestBodyGemini(final Content systemInstruction, final Content[] content,
      final GenerationConfig generationConfig) {
      this.systemInstruction = systemInstruction;
      this.contents = content;
      this.generationConfig = generationConfig;
   }

   public static class Content {
      // Null for systemInstruction; Gson omits it.
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

   public static RequestBodyGemini createRequestBody(final String system, final List<ChatMessage> history,
      final double temp) {
      final Content[] contents = new Content[history.size()];
      for (int i = 0; i < history.size(); i++) {
         final ChatMessage msg = history.get(i);
         // Gemini calls the assistant role "model".
         final String role = msg.getRole() == ChatMessage.Role.USER ? "user" : "model";
         contents[i] = new Content(role, new Part[] { new Part(msg.getText()) });
      }
      final Content systemInstruction = new Content(null, new Part[] { new Part(system) });
      return new RequestBodyGemini(systemInstruction, contents, new GenerationConfig(temp));
   }
}
