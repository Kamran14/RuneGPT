package com.kamrant.runegpt.model.request;

import com.google.gson.annotations.SerializedName;
import com.kamrant.runegpt.model.ChatMessage;
import com.kamrant.runegpt.model.ChatRole;
import java.util.List;

/** Request body for Groq's OpenAI-compatible chat completions endpoint. */
public final class GroqRequest {

   @SerializedName("model")
   private final String model;
   @SerializedName("messages")
   private final GroqMessage[] messages;
   @SerializedName("temperature")
   private final double temperature;
   @SerializedName("max_completion_tokens")
   private final int maxCompletionTokens;
   @SerializedName("response_format")
   private final GroqResponseFormat responseFormat = new GroqResponseFormat();

   private GroqRequest(final String model, final GroqMessage[] messages, final double temperature,
      final int maxCompletionTokens) {
      this.model = model;
      this.messages = messages;
      this.temperature = temperature;
      this.maxCompletionTokens = maxCompletionTokens;
   }

   public static GroqRequest create(final String model, final String system, final List<ChatMessage> history,
      final double temperature, final int maxCompletionTokens) {
      // OpenAI format: the system prompt is the first message.
      final GroqMessage[] messages = new GroqMessage[history.size() + 1];
      messages[0] = new GroqMessage("system", system);
      for (int i = 0; i < history.size(); i++) {
         final ChatMessage msg = history.get(i);
         messages[i + 1] = new GroqMessage(msg.getRole() == ChatRole.USER ? "user" : "assistant", msg.getText());
      }
      return new GroqRequest(model, messages, temperature, maxCompletionTokens);
   }
}
