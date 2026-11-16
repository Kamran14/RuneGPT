package com.kamrant.runegpt.builders;

import com.google.gson.annotations.SerializedName;
import com.kamrant.runegpt.llm.ChatMessage;
import java.util.List;

/** Request body for Groq's OpenAI-compatible chat completions endpoint. */
public class RequestBodyGroq {

   @SerializedName("model")
   private final String model;
   @SerializedName("messages")
   private final Message[] messages;
   @SerializedName("temperature")
   private final double temperature;
   @SerializedName("max_completion_tokens")
   private final int maxCompletionTokens;

   private RequestBodyGroq(final String model, final Message[] messages, final double temperature,
      final int maxCompletionTokens) {
      this.model = model;
      this.messages = messages;
      this.temperature = temperature;
      this.maxCompletionTokens = maxCompletionTokens;
   }

   public static class Message {
      @SerializedName("role")
      private final String role;
      @SerializedName("content")
      private final String content;

      public Message(final String role, final String content) {
         this.role = role;
         this.content = content;
      }
   }

   public static RequestBodyGroq createRequestBody(final String model, final String system,
      final List<ChatMessage> history, final double temperature, final int maxCompletionTokens) {
      // OpenAI format: the system prompt is the first message.
      final Message[] messages = new Message[history.size() + 1];
      messages[0] = new Message("system", system);
      for (int i = 0; i < history.size(); i++) {
         final ChatMessage msg = history.get(i);
         messages[i + 1] = new Message(msg.getRole() == ChatMessage.Role.USER ? "user" : "assistant", msg.getText());
      }
      return new RequestBodyGroq(model, messages, temperature, maxCompletionTokens);
   }
}
