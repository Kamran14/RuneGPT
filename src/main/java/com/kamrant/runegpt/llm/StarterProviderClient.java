package com.kamrant.runegpt.llm;

import static net.runelite.http.api.RuneLiteAPI.JSON;
import com.google.gson.Gson;
import com.kamrant.runegpt.RuneGPTConfig;
import com.kamrant.runegpt.config.LLMConfig;
import com.kamrant.runegpt.config.LLMProvider;
import com.kamrant.runegpt.model.ChatMessage;
import com.kamrant.runegpt.model.request.PollinationsRequest;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * "Starter": the keyless Pollinations Classic text endpoint, so new players can try RuneGPT without
 * signing up. Anonymous use is limited per IP (one reply about every 45 seconds; 402 + Retry-After when
 * exceeded), so this client paces itself: it waits for the next free slot (up to a minute) instead of
 * hitting the server early, and reports rate limits as quota exhaustion so GPTClient's cooldown applies.
 */
public class StarterProviderClient implements LLMProviderClient {

   // Earliest time the next request may be sent. Static: the free limit is per IP, so every caller shares it.
   private static Instant nextAllowed = Instant.EPOCH;
   private static final Object LOCK = new Object();

   @Override
   public LLMProvider provider() {
      return LLMProvider.STARTER;
   }

   @Override
   public String storedKey(final RuneGPTConfig config) {
      return "";
   }

   @Override
   public String keyName() {
      return "";
   }

   @Override
   public boolean requiresKey() {
      return false;
   }

   @Override
   public Duration readTimeout() {
      // Reasoning model on a shared free tier: replies can be slow.
      return Duration.ofSeconds(90);
   }

   @Override
   public Request buildRequest(final String apiKey, final String system, final List<ChatMessage> history,
      final RuneGPTConfig config, final Gson gson) {
      return new Request.Builder()
         .url(LLMConfig.STARTER_URL)
         .post(RequestBody.create(JSON, gson.toJson(PollinationsRequest.create(system, history))))
         .build();
   }

   @Override
   public String send(final OkHttpClient httpClient, final Gson gson, final String apiKey, final String system,
      final List<ChatMessage> history, final RuneGPTConfig config) throws ProviderException {
      waitForSlot();
      try {
         return LLMProviderClient.super.send(httpClient, gson, apiKey, system, history, config);
      } catch (final ProviderException e) {
         // Server says the slot isn't free yet (e.g. another RuneGPT window on this IP): wait once and retry.
         if (!e.isQuotaExhausted()) {
            throw e;
         }
         waitForSlot();
         return LLMProviderClient.super.send(httpClient, gson, apiKey, system, history, config);
      } finally {
         reserveNextSlot(null);
      }
   }

   /** Blocks until the next free slot, or throws (as quota exhausted) if that's too far away. */
   private static void waitForSlot() throws ProviderException {
      final long waitMs;
      synchronized (LOCK) {
         waitMs = Duration.between(Instant.now(), nextAllowed).toMillis();
      }
      if (waitMs <= 0) {
         return;
      }
      if (waitMs > LLMConfig.STARTER_MAX_WAIT_SECONDS * 1000L) {
         throw new ProviderException("free limit reached, next reply in " + (waitMs / 1000 + 1) + "s", true,
            Duration.ofMillis(waitMs));
      }
      try {
         Thread.sleep(waitMs);
      } catch (final InterruptedException e) {
         Thread.currentThread().interrupt();
         throw new ProviderException(LLMConfig.ERR_CANCELLED);
      }
   }

   /** Next request no sooner than the free-tier interval, or later if the server said so. */
   private static void reserveNextSlot(final Duration retryAfter) {
      synchronized (LOCK) {
         final Instant now = Instant.now();
         // After a 402 the server's Retry-After is exact; otherwise wait the full free-tier interval.
         final Instant next = retryAfter != null ? now.plus(retryAfter).plusSeconds(1)
            : now.plusSeconds(LLMConfig.STARTER_MIN_INTERVAL_SECONDS);
         if (retryAfter != null || next.isAfter(nextAllowed)) {
            nextAllowed = next;
         }
      }
   }

   @Override
   public ProviderException httpError(final Response response, final String body, final Gson gson) {
      final Duration retryAfter = LLMProviderClient.retryAfter(response);
      // 402 Payment Required is Pollinations' "free limit reached"; 429 for good measure.
      if (response.code() == 402 || response.code() == 429) {
         reserveNextSlot(retryAfter);
         return new ProviderException("free limit reached (one reply about every "
            + LLMConfig.STARTER_MIN_INTERVAL_SECONDS + "s)", true, retryAfter);
      }
      final String text = body == null ? "" : body.trim();
      return new ProviderException("HTTP " + response.code() + (text.isEmpty() ? "" : ": " + firstLine(text)));
   }

   @Override
   public String parseResponse(final Response response, final String body, final Gson gson) throws ProviderException {
      // Plain-text reply (our system prompt asks for JSON inside it; AssistantService falls back to Markdown).
      final String text = body == null ? "" : body.trim();
      if (text.isEmpty()) {
         throw new ProviderException(LLMConfig.ERR_EMPTY_ANSWER);
      }
      return text;
   }

   private static String firstLine(final String s) {
      final int nl = s.indexOf('\n');
      final String line = nl > 0 ? s.substring(0, nl) : s;
      return line.length() > 160 ? line.substring(0, 160) : line;
   }
}
