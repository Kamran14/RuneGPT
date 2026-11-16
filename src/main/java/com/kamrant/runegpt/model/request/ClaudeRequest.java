package com.kamrant.runegpt.model.request;

import com.google.gson.annotations.SerializedName;
import com.kamrant.runegpt.model.ChatMessage;
import com.kamrant.runegpt.model.ChatRole;
import java.util.Arrays;
import java.util.List;

/**
 * Request body for the Anthropic Messages API (POST /v1/messages).
 * Thinking is left unset: current Claude models run adaptive thinking by default.
 * Sampling params (temperature/top_p/top_k) are omitted because API version 2026-10-21 rejects them.
 */
public final class ClaudeRequest {

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
   private final ClaudeMessage[] messages;
   @SerializedName("output_config")
   private final ClaudeOutputConfig outputConfig;
   // Re-runs the request on a fallback model if a safety classifier declines it. Null = omitted by Gson.
   @SerializedName("fallbacks")
   private final String fallbacks;

   private ClaudeRequest(final String model, final int maxTokens, final String system, final ClaudeMessage[] messages,
      final ClaudeOutputConfig outputConfig, final String fallbacks) {
      this.model = model;
      this.maxTokens = maxTokens;
      this.system = system;
      this.messages = messages;
      this.outputConfig = outputConfig;
      this.fallbacks = fallbacks;
   }

   public static boolean supportsFallbacks(final String model) {
      return FALLBACK_MODELS.contains(model);
   }

   public static ClaudeRequest create(final String model, final String system, final List<ChatMessage> history,
      final int maxTokens) {
      final ClaudeMessage[] messages = new ClaudeMessage[history.size()];
      for (int i = 0; i < history.size(); i++) {
         final ChatMessage msg = history.get(i);
         messages[i] = new ClaudeMessage(msg.getRole() == ChatRole.USER ? "user" : "assistant", msg.getText());
      }
      return new ClaudeRequest(model, maxTokens, system, messages, new ClaudeOutputConfig("medium"),
         supportsFallbacks(model) ? "default" : null);
   }
}
