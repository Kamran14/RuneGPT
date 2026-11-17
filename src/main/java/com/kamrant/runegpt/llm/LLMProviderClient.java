package com.kamrant.runegpt.llm;

import com.kamrant.runegpt.model.ChatMessage;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.kamrant.runegpt.RuneGPTConfig;
import com.kamrant.runegpt.config.LLMConfig;
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

   /** The stored (normally encrypted) API key setting for this provider; blank means the provider is skipped. */
   String storedKey(RuneGPTConfig config);

   /** Name of the setting that holds this provider's key (also binds its encryption); "" if keyless. */
   String keyName();

   /** False for keyless providers (Starter), which GPTClient adds on its own rules instead of by key. */
   default boolean requiresKey() {
      return true;
   }

   Request buildRequest(String apiKey, String system, List<ChatMessage> history, RuneGPTConfig config, Gson gson);

   /** Returns the reply text, or throws if the response is an error or has no usable text. */
   String parseResponse(Response response, String body, Gson gson) throws ProviderException;

   /** Read timeout for this provider; reasoning models need longer than RuneLite's default. */
   default Duration readTimeout() {
      return Duration.ofSeconds(120);
   }

   /** Sends a request. {@code apiKey} is the decrypted key (held only for the duration of the call). */
   default String send(final OkHttpClient httpClient, final Gson gson, final String apiKey, final String system,
      final List<ChatMessage> history, final RuneGPTConfig config) throws ProviderException {
      final Request request = buildRequest(apiKey, system, history, config, gson);
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
    * Request for a streamed (SSE) reply, or null if this provider doesn't stream. The default is null, so a
    * provider only streams once it implements this and {@link #streamDelta}.
    */
   default Request buildStreamRequest(final String apiKey, final String system, final List<ChatMessage> history,
      final RuneGPTConfig config, final Gson gson) {
      return null;
   }

   /**
    * Reads one SSE event: returns the reply text it adds ("" for none) and records the stop state in
    * {@code state}. Throws for an error event or a refusal.
    */
   default String streamDelta(final JsonObject event, final StreamState state) throws ProviderException {
      return "";
   }

   /**
    * Streams a reply, passing each text delta to {@code listener} as it arrives, and returns the full text
    * (same contract as {@link #send}). Falls back to {@link #parseResponse} when the server answers with a
    * plain JSON body instead of an event stream.
    */
   default String stream(final OkHttpClient httpClient, final Gson gson, final String apiKey, final String system,
      final List<ChatMessage> history, final RuneGPTConfig config, final StreamListener listener)
      throws ProviderException {
      final Request request = buildStreamRequest(apiKey, system, history, config, gson);
      if (request == null) {
         return send(httpClient, gson, apiKey, system, history, config);
      }
      final OkHttpClient client = httpClient.newBuilder().readTimeout(readTimeout()).build();
      try (Response response = client.newCall(request).execute()) {
         if (response.body() == null) {
            throw new ProviderException(LLMConfig.ERR_EMPTY_ANSWER);
         }
         if (!response.isSuccessful()) {
            throw httpError(response, response.body().string(), gson);
         }
         final String type = response.header("Content-Type", "");
         if (!type.toLowerCase().contains("text/event-stream")) {
            final String text = parseResponse(response, response.body().string(), gson);
            listener.onText(text);
            return text;
         }
         final StreamState state = new StreamState();
         final SseReader reader = new SseReader(response.body().source());
         String data;
         while ((data = reader.next()) != null) {
            final JsonObject event;
            try {
               event = gson.fromJson(data, JsonObject.class);
            } catch (final RuntimeException e) {
               continue; // keep-alive or non-JSON line
            }
            if (event == null) {
               continue;
            }
            final String delta = streamDelta(event, state);
            if (delta != null && !delta.isEmpty()) {
               state.append(delta);
               listener.onText(delta);
            }
         }
         if (state.isEmpty()) {
            throw new ProviderException(LLMConfig.ERR_EMPTY_ANSWER);
         }
         return state.isTruncated() ? state.text() + LLMConfig.TRUNCATED_SUFFIX : state.text();
      } catch (final IOException e) {
         throw new ProviderException("network error: " + e.getMessage());
      }
   }

   /** Reads error.message from an {"error": {...}} SSE event, or null when the event isn't an error. */
   static String streamError(final JsonObject event) {
      final JsonElement error = event.get("error");
      if (error == null || error.isJsonNull()) {
         return null;
      }
      if (error.isJsonObject() && error.getAsJsonObject().has("message")) {
         return error.getAsJsonObject().get("message").getAsString();
      }
      return error.toString();
   }

   /**
    * Maps an HTTP error to a ProviderException. 429 (rate limit / quota) and 402 (no credit) count as
    * exhausted; providers can override for provider-specific signals.
    */
   default ProviderException httpError(final Response response, final String body, final Gson gson) {
      final int code = response.code();
      final String message = errorMessage(body, gson, response.message());
      final boolean exhausted = code == 429 || code == 402;
      return new ProviderException("HTTP " + code + ": " + message, exhausted, retryAfter(response), code);
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
