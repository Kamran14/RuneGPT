package com.kamrant.runegpt.llm;

import com.kamrant.runegpt.model.ChatMessage;
import static net.runelite.http.api.RuneLiteAPI.JSON;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.kamrant.runegpt.RuneGPTConfig;
import com.kamrant.runegpt.model.request.GeminiRequest;
import com.kamrant.runegpt.config.LLMConfig;
import com.kamrant.runegpt.config.LLMProvider;
import java.util.List;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class GeminiProviderClient implements LLMProviderClient {

   @Override
   public LLMProvider provider() {
      return LLMProvider.GEMINI;
   }

   @Override
   public String keyName() {
      return RuneGPTConfig.KEY_GEMINI;
   }

   @Override
   public String storedKey(final RuneGPTConfig config) {
      return config.apiKey();
   }

   @Override
   public Request buildRequest(final String apiKey, final String system, final List<ChatMessage> history,
      final RuneGPTConfig config, final Gson gson) {
      final GeminiRequest body = GeminiRequest.create(system, history, config.temperature());
      return new Request.Builder()
         .url(LLMConfig.GEMINI_URL)
         .addHeader(LLMConfig.HEADER_GEMINI_KEY, apiKey)
         .post(RequestBody.create(JSON, gson.toJson(body)))
         .build();
   }

   @Override
   public Request buildStreamRequest(final String apiKey, final String system, final List<ChatMessage> history,
      final RuneGPTConfig config, final Gson gson) {
      final GeminiRequest body = GeminiRequest.create(system, history, config.temperature());
      return new Request.Builder()
         .url(LLMConfig.GEMINI_STREAM_URL)
         .addHeader(LLMConfig.HEADER_GEMINI_KEY, apiKey)
         .post(RequestBody.create(JSON, gson.toJson(body)))
         .build();
   }

   /** Each SSE event is a partial GenerateContentResponse; text is in candidates[0].content.parts[].text. */
   @Override
   public String streamDelta(final JsonObject event, final StreamState state) throws ProviderException {
      final String error = LLMProviderClient.streamError(event);
      if (error != null) {
         throw new ProviderException("stream error: " + error);
      }
      final JsonArray candidates = event.getAsJsonArray("candidates");
      if (candidates == null || candidates.size() == 0) {
         final JsonObject feedback = event.getAsJsonObject("promptFeedback");
         if (feedback != null && feedback.has("blockReason")) {
            throw new ProviderException(LLMConfig.ERR_BLOCKED_ANSWER);
         }
         return "";
      }
      final JsonObject candidate = candidates.get(0).getAsJsonObject();
      final JsonElement finish = candidate.get("finishReason");
      if (finish != null && !finish.isJsonNull() && "MAX_TOKENS".equals(finish.getAsString())) {
         state.markTruncated();
      }
      final JsonObject content = candidate.getAsJsonObject("content");
      if (content == null || !content.has("parts")) {
         return "";
      }
      final StringBuilder text = new StringBuilder();
      for (final JsonElement part : content.getAsJsonArray("parts")) {
         final JsonObject p = part.getAsJsonObject();
         if (p.has("text")) {
            text.append(p.get("text").getAsString());
         }
      }
      return text.toString();
   }

   @Override
   public String parseResponse(final Response response, final String body, final Gson gson) throws ProviderException {
      final JsonObject json = gson.fromJson(body, JsonObject.class);
      final JsonArray candidates = json.getAsJsonArray("candidates");
      if (candidates == null || candidates.size() == 0) {
         throw new ProviderException(LLMConfig.ERR_BLOCKED_ANSWER);
      }
      final JsonObject content = candidates.get(0).getAsJsonObject().getAsJsonObject("content");
      if (content == null || !content.has("parts")) {
         throw new ProviderException(LLMConfig.ERR_EMPTY_ANSWER);
      }
      final StringBuilder text = new StringBuilder();
      for (final JsonElement part : content.getAsJsonArray("parts")) {
         final JsonObject p = part.getAsJsonObject();
         if (p.has("text")) {
            text.append(p.get("text").getAsString());
         }
      }
      if (text.length() == 0) {
         throw new ProviderException(LLMConfig.ERR_EMPTY_ANSWER);
      }
      return text.toString();
   }
}
