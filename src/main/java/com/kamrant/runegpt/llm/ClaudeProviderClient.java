package com.kamrant.runegpt.llm;

import com.kamrant.runegpt.model.ChatMessage;
import static net.runelite.http.api.RuneLiteAPI.JSON;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.kamrant.runegpt.RuneGPTConfig;
import com.kamrant.runegpt.model.request.ClaudeRequest;
import com.kamrant.runegpt.config.LLMConfig;
import com.kamrant.runegpt.config.LLMProvider;
import java.time.Duration;
import java.util.List;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ClaudeProviderClient implements LLMProviderClient {

   @Override
   public LLMProvider provider() {
      return LLMProvider.CLAUDE;
   }

   @Override
   public String apiKey(final RuneGPTConfig config) {
      return config.claudeApiKey();
   }

   @Override
   public Duration readTimeout() {
      // Claude thinks before answering.
      return Duration.ofSeconds(180);
   }

   @Override
   public Request buildRequest(final String apiKey, final String system, final List<ChatMessage> history,
      final RuneGPTConfig config, final Gson gson) {
      final String model = config.claudeModel() == null || config.claudeModel().trim().isEmpty()
         ? LLMConfig.CLAUDE_DEFAULT_MODEL
         : config.claudeModel().trim();
      final ClaudeRequest body = ClaudeRequest.create(
         model, system, history, LLMConfig.CLAUDE_MAX_TOKENS);

      final Request.Builder builder = new Request.Builder()
         .url(LLMConfig.CLAUDE_URL)
         .addHeader("x-api-key", apiKey)
         .addHeader("anthropic-version", LLMConfig.CLAUDE_API_VERSION)
         .post(RequestBody.create(JSON, gson.toJson(body)));
      if (ClaudeRequest.supportsFallbacks(model)) {
         builder.addHeader("anthropic-beta", LLMConfig.CLAUDE_FALLBACK_BETA);
      }
      return builder.build();
   }

   @Override
   public ProviderException httpError(final Response response, final String body, final Gson gson) {
      final ProviderException base = LLMProviderClient.super.httpError(response, body, gson);
      // 529 = overloaded; a 400 about credit balance = account out of credit. Both mean "try elsewhere".
      final boolean outOfCredit = response.code() == 400 && base.getMessage().toLowerCase().contains("credit");
      if (response.code() == 529 || outOfCredit) {
         return new ProviderException(base.getMessage(), true, base.getRetryAfter());
      }
      return base;
   }

   @Override
   public String parseResponse(final Response response, final String body, final Gson gson) throws ProviderException {
      final JsonObject json = gson.fromJson(body, JsonObject.class);
      final String stopReason = json.has("stop_reason") && !json.get("stop_reason").isJsonNull()
         ? json.get("stop_reason").getAsString()
         : "";
      if ("refusal".equals(stopReason)) {
         throw new ProviderException("declined to answer");
      }

      // Content is a list of blocks (thinking, text, ...). Only text blocks are shown to the player.
      final StringBuilder text = new StringBuilder();
      for (final JsonElement el : json.getAsJsonArray("content")) {
         final JsonObject block = el.getAsJsonObject();
         if ("text".equals(block.get("type").getAsString())) {
            text.append(block.get("text").getAsString());
         }
      }
      if (text.length() == 0) {
         throw new ProviderException("empty answer returned");
      }
      if ("max_tokens".equals(stopReason)) {
         text.append("\n\n[Response truncated]");
      }
      return text.toString();
   }
}
