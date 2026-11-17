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
   public String keyName() {
      return RuneGPTConfig.KEY_CLAUDE;
   }

   @Override
   public String storedKey(final RuneGPTConfig config) {
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
      return build(apiKey, system, history, config, gson, false);
   }

   @Override
   public Request buildStreamRequest(final String apiKey, final String system, final List<ChatMessage> history,
      final RuneGPTConfig config, final Gson gson) {
      return build(apiKey, system, history, config, gson, true);
   }

   private Request build(final String apiKey, final String system, final List<ChatMessage> history,
      final RuneGPTConfig config, final Gson gson, final boolean stream) {
      final String model = config.claudeModel() == null || config.claudeModel().trim().isEmpty()
         ? LLMConfig.CLAUDE_DEFAULT_MODEL
         : config.claudeModel().trim();
      final ClaudeRequest body = ClaudeRequest.create(
         model, system, history, LLMConfig.CLAUDE_MAX_TOKENS);
      if (stream) {
         body.streaming();
      }

      final Request.Builder builder = new Request.Builder()
         .url(LLMConfig.CLAUDE_URL)
         .addHeader(LLMConfig.HEADER_CLAUDE_KEY, apiKey)
         .addHeader(LLMConfig.HEADER_CLAUDE_VERSION, LLMConfig.CLAUDE_API_VERSION)
         .post(RequestBody.create(JSON, gson.toJson(body)));
      if (ClaudeRequest.supportsFallbacks(model)) {
         builder.addHeader(LLMConfig.HEADER_CLAUDE_BETA, LLMConfig.CLAUDE_FALLBACK_BETA);
      }
      return builder.build();
   }

   @Override
   public ProviderException httpError(final Response response, final String body, final Gson gson) {
      final ProviderException base = LLMProviderClient.super.httpError(response, body, gson);
      // 529 = overloaded; a 400 about credit balance = account out of credit. Both mean "try elsewhere".
      final boolean outOfCredit = response.code() == 400 && base.getMessage().toLowerCase().contains("credit");
      if (response.code() == 529 || outOfCredit) {
         return new ProviderException(base.getMessage(), true, base.getRetryAfter(), base.getHttpStatus());
      }
      return base;
   }

   /**
    * Messages API stream: text arrives in content_block_delta / text_delta events (thinking deltas are skipped);
    * message_delta carries the stop reason. A refusal fallback continues on the same stream, so its text simply
    * keeps arriving.
    */
   @Override
   public String streamDelta(final JsonObject event, final StreamState state) throws ProviderException {
      final String type = event.has("type") ? event.get("type").getAsString() : "";
      switch (type) {
         case "error": {
            final JsonObject error = event.getAsJsonObject("error");
            final String errType = error != null && error.has("type") ? error.get("type").getAsString() : "";
            // Overloaded mid-stream: try another provider and skip this one for a while.
            throw new ProviderException("stream error: " + LLMProviderClient.streamError(event),
               "overloaded_error".equals(errType), null);
         }
         case "content_block_delta": {
            final JsonObject delta = event.getAsJsonObject("delta");
            if (delta != null && delta.has("type") && "text_delta".equals(delta.get("type").getAsString())
               && delta.has("text")) {
               return delta.get("text").getAsString();
            }
            return "";
         }
         case "message_delta": {
            final JsonObject delta = event.getAsJsonObject("delta");
            final JsonElement stop = delta == null ? null : delta.get("stop_reason");
            final String stopReason = stop == null || stop.isJsonNull() ? "" : stop.getAsString();
            if ("refusal".equals(stopReason)) {
               // Partial output from a declined attempt must be discarded, not shown as an answer.
               throw new ProviderException(LLMConfig.ERR_DECLINED);
            }
            if ("max_tokens".equals(stopReason)) {
               state.markTruncated();
            }
            return "";
         }
         default:
            return "";
      }
   }

   @Override
   public String parseResponse(final Response response, final String body, final Gson gson) throws ProviderException {
      final JsonObject json = gson.fromJson(body, JsonObject.class);
      final String stopReason = json.has("stop_reason") && !json.get("stop_reason").isJsonNull()
         ? json.get("stop_reason").getAsString()
         : "";
      if ("refusal".equals(stopReason)) {
         throw new ProviderException(LLMConfig.ERR_DECLINED);
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
         throw new ProviderException(LLMConfig.ERR_EMPTY_ANSWER);
      }
      if ("max_tokens".equals(stopReason)) {
         text.append(LLMConfig.TRUNCATED_SUFFIX);
      }
      return text.toString();
   }
}
