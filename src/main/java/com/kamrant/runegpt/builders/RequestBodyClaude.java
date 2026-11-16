package com.kamrant.runegpt.builders;

import com.google.gson.annotations.SerializedName;
import com.kamrant.runegpt.llm.ChatMessage;
import java.util.Arrays;
import java.util.List;

/**
 * Request body for the Anthropic Messages API (POST /v1/messages).
 * Thinking is left unset: current Claude models run adaptive thinking by default.
 * Sampling params (temperature/top_p/top_k) are omitted because API version 2026-10-21 rejects them.
 */
public class RequestBodyClaude {

   // Models that accept the server-side "fallbacks" parameter.
   private static final List<String> FALLBACK_MODELS = Arrays.asList(
      "claude-fable-5-2", "claude-fable-5-1", "claude-opus-5-5", "claude-opus-5", "claude-sonnet-5-5");

   @SerializedName("model")
   private final String model;
   @SerializedName("max_tokens")
   private final int maxTokens;
   @SerializedName("system")
   private final String system;
   @SerializedName("messages")
   private final Message[] messages;
   @SerializedName("output_config")
   private final OutputConfig outputConfig;
   // Re-runs the request on a fallback model if a safety classifier declines it. Null = omitted by Gson.
   @SerializedName("fallbacks")
   private final String fallbacks;

   private RequestBodyClaude(final String model, final int maxTokens, final String system,
      final Message[] messages, final OutputConfig outputConfig, final String fallbacks) {
      this.model = model;
      this.maxTokens = maxTokens;
      this.system = system;
      this.messages = messages;
      this.outputConfig = outputConfig;
      this.fallbacks = fallbacks;
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

   public static class OutputConfig {
      @SerializedName("effort")
      private final String effort;

      public OutputConfig(final String effort) {
         this.effort = effort;
      }
   }

   public static boolean supportsFallbacks(final String model) {
      return FALLBACK_MODELS.contains(model);
   }

   public static RequestBodyClaude createRequestBody(final String model, final String system,
      final List<ChatMessage> history, final int maxTokens) {
      final Message[] messages = new Message[history.size()];
      for (int i = 0; i < history.size(); i++) {
         final ChatMessage msg = history.get(i);
         messages[i] = new Message(msg.getRole() == ChatMessage.Role.USER ? "user" : "assistant", msg.getText());
      }
      return new RequestBodyClaude(
         model,
         maxTokens,
         system,
         messages,
         new OutputConfig("medium"),
         supportsFallbacks(model) ? "default" : null);
   }
}
