package com.kamrant.runegpt.model.request;

import com.google.gson.annotations.SerializedName;
import com.kamrant.runegpt.model.ChatMessage;
import com.kamrant.runegpt.model.ChatRole;
import java.util.List;

/** Request body for Gemini's generateContent endpoint. */
public final class GeminiRequest {

   @SerializedName("systemInstruction")
   private final GeminiContent systemInstruction;
   @SerializedName("contents")
   private final GeminiContent[] contents;
   @SerializedName("generationConfig")
   private final GeminiGenerationConfig generationConfig;

   private GeminiRequest(final GeminiContent systemInstruction, final GeminiContent[] contents,
      final GeminiGenerationConfig generationConfig) {
      this.systemInstruction = systemInstruction;
      this.contents = contents;
      this.generationConfig = generationConfig;
   }

   public static GeminiRequest create(final String system, final List<ChatMessage> history, final double temperature) {
      final GeminiContent[] contents = new GeminiContent[history.size()];
      for (int i = 0; i < history.size(); i++) {
         final ChatMessage msg = history.get(i);
         // Gemini calls the assistant role "model".
         contents[i] = new GeminiContent(msg.getRole() == ChatRole.USER ? "user" : "model", msg.getText());
      }
      return new GeminiRequest(new GeminiContent(null, system), contents, new GeminiGenerationConfig(temperature));
   }
}
