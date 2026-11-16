package com.kamrant.runegpt.llm;

import com.kamrant.runegpt.model.ChatMessage;
import static net.runelite.http.api.RuneLiteAPI.JSON;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.kamrant.runegpt.RuneGPTConfig;
import com.kamrant.runegpt.model.request.GroqRequest;
import com.kamrant.runegpt.config.LLMConfig;
import com.kamrant.runegpt.config.LLMProvider;
import java.util.List;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/** Groq: free tier (no card), OpenAI-compatible chat completions. */
public class GroqProviderClient implements LLMProviderClient {

   @Override
   public LLMProvider provider() {
      return LLMProvider.GROQ;
   }

   @Override
   public String keyName() {
      return RuneGPTConfig.KEY_GROQ;
   }

   @Override
   public String storedKey(final RuneGPTConfig config) {
      return config.groqApiKey();
   }

   @Override
   public Request buildRequest(final String apiKey, final String system, final List<ChatMessage> history,
      final RuneGPTConfig config, final Gson gson) {
      final String model = config.groqModel() == null || config.groqModel().trim().isEmpty()
         ? LLMConfig.GROQ_DEFAULT_MODEL
         : config.groqModel().trim();
      final GroqRequest body = GroqRequest.create(
         model, system, history, config.temperature(), LLMConfig.GROQ_MAX_TOKENS);
      return new Request.Builder()
         .url(LLMConfig.GROQ_URL)
         .addHeader(LLMConfig.HEADER_AUTHORIZATION, LLMConfig.BEARER_PREFIX + apiKey)
         .post(RequestBody.create(JSON, gson.toJson(body)))
         .build();
   }

   @Override
   public String parseResponse(final Response response, final String body, final Gson gson) throws ProviderException {
      final JsonObject json = gson.fromJson(body, JsonObject.class);
      final JsonArray choices = json.getAsJsonArray("choices");
      if (choices == null || choices.size() == 0) {
         throw new ProviderException(LLMConfig.ERR_NO_ANSWER);
      }
      final JsonObject choice = choices.get(0).getAsJsonObject();
      final JsonObject message = choice.getAsJsonObject("message");
      final JsonElement content = message == null ? null : message.get("content");
      if (content == null || content.isJsonNull() || content.getAsString().trim().isEmpty()) {
         throw new ProviderException(LLMConfig.ERR_EMPTY_ANSWER);
      }
      final String text = content.getAsString();
      final JsonElement finish = choice.get("finish_reason");
      if (finish != null && !finish.isJsonNull() && "length".equals(finish.getAsString())) {
         return text + LLMConfig.TRUNCATED_SUFFIX;
      }
      return text;
   }
}
