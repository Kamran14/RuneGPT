package com.kamrant.runegpt.llm;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.kamrant.runegpt.RuneGPTConfig;
import com.kamrant.runegpt.config.LLMProvider;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/** One LLM backend. Implementations translate the shared history into their own wire format. */
public interface LLMProviderClient {

   LLMProvider provider();

   /** API key for this provider from config; blank means the provider is skipped. */
   String apiKey(RuneGPTConfig config);

   Request buildRequest(String apiKey, String system, List<ChatMessage> history, RuneGPTConfig config, Gson gson);

   /** Returns the reply text, or throws if the response is an error or has no usable text. */
   String parseResponse(Response response, String body, Gson gson) throws ProviderException;

   /** Read timeout for this provider; reasoning models need longer than RuneLite's default. */
   default Duration readTimeout() {
      return Duration.ofSeconds(120);
   }

   default String send(final OkHttpClient httpClient, final Gson gson, final String system,
      final List<ChatMessage> history, final RuneGPTConfig config) throws ProviderException {
      final Request request = buildRequest(apiKey(config).trim(), system, history, config, gson);
      final OkHttpClient client = httpClient.newBuilder().readTimeout(readTimeout()).build();
      try (Response response = client.newCall(request).execute()) {
         final String body = response.body() == null ? "" : response.body().string();
         if (!response.isSuccessful()) {
            throw httpError(response, body, gson);
         }
         return parseResponse(response, body, gson);
      } catch (final IOException e) {
         throw new ProviderException("network error: " + e.getMessage());
      }
   }

   /**
    * Maps an HTTP error to a ProviderException. 429 (rate limit / quota) and 402 (no credit) count as
    * exhausted; providers can override for provider-specific signals.
    */
   default ProviderException httpError(final Response response, final String body, final Gson gson) {
      final int code = response.code();
      final String message = errorMessage(body, gson, response.message());
      final boolean exhausted = code == 429 || code == 402;
      return new ProviderException("HTTP " + code + ": " + message, exhausted, retryAfter(response));
   }

   /** Reads error.message from the common {"error": {"message": ...}} shape all three providers use. */
   static String errorMessage(final String body, final Gson gson, final String fallback) {
      try {
         final JsonElement error = gson.fromJson(body, JsonObject.class).get("error");
         if (error != null && error.isJsonObject() && error.getAsJsonObject().has("message")) {
            return error.getAsJsonObject().get("message").getAsString();
         }
      } catch (final Exception ignored) {
         // fall through
      }
      return fallback;
   }

   static Duration retryAfter(final Response response) {
      final String header = response.header("retry-after");
      if (header == null) {
         return null;
      }
      try {
         return Duration.ofSeconds((long) Math.ceil(Double.parseDouble(header.trim())));
      } catch (final NumberFormatException e) {
         return null;
      }
   }
}
