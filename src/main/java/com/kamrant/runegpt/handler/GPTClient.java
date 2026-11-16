package com.kamrant.runegpt.handler;

import com.google.gson.Gson;
import com.google.inject.Singleton;
import com.kamrant.runegpt.RuneGPTConfig;
import com.kamrant.runegpt.config.LLMConfig;
import com.kamrant.runegpt.config.LLMProvider;
import com.kamrant.runegpt.llm.ChatMessage;
import com.kamrant.runegpt.llm.ClaudeProviderClient;
import com.kamrant.runegpt.llm.ConversationHistory;
import com.kamrant.runegpt.llm.GeminiProviderClient;
import com.kamrant.runegpt.llm.GroqProviderClient;
import com.kamrant.runegpt.llm.LLMProviderClient;
import com.kamrant.runegpt.llm.ProviderException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;

/**
 * Sends prompts to the configured LLM providers with automatic failover. The conversation is kept
 * here on the client, so whichever provider answers sees the full history. The player only gets an
 * error when every provider with an API key has failed.
 */
@Slf4j
@Singleton
public class GPTClient {

   // How long to skip a provider after it reports rate limit / quota exhaustion, if it doesn't say.
   private static final Duration DEFAULT_COOLDOWN = Duration.ofMinutes(1);

   @Inject
   private OkHttpClient httpClient;
   @Inject
   private Gson gson;

   private final RuneGPTConfig config;
   private static GPTClient instance;

   // Fixed failover order after the primary provider.
   private final List<LLMProviderClient> providers = Arrays.asList(
      new GeminiProviderClient(), new ClaudeProviderClient(), new GroqProviderClient());
   private final ConversationHistory history = new ConversationHistory();
   private final Map<LLMProvider, Instant> cooldownUntil = new EnumMap<>(LLMProvider.class);

   @Inject
   public GPTClient(final RuneGPTConfig config, final OkHttpClient httpClient) {
      this.config = config;
      this.httpClient = httpClient;
   }

   public static synchronized GPTClient getInstance(final RuneGPTConfig config, final OkHttpClient httpClient) {
      if (instance == null) {
         instance = new GPTClient(config, httpClient);
      }
      return instance;
   }

   /** Result of a prompt: the reply and which provider gave it, or an error after all providers failed. */
   public static final class Reply {
      private final boolean success;
      private final String text;
      private final LLMProvider provider;

      private Reply(final boolean success, final String text, final LLMProvider provider) {
         this.success = success;
         this.text = text;
         this.provider = provider;
      }

      public boolean isSuccess() {
         return success;
      }

      public String getText() {
         return text;
      }

      /** The provider that answered; null on failure. */
      public LLMProvider getProvider() {
         return provider;
      }
   }

   public void clearConversation() {
      history.clear();
   }

   /**
    * Ask a question. {@code playerContext} (stats etc.) goes in the system prompt and is refreshed on
    * every call, so it never goes stale in the history.
    */
   public Reply ask(final String prompt, final String playerContext) {
      final String system = LLMConfig.INSTRUCTIONS + "\n\nCurrent player data:\n" + playerContext;
      final List<ChatMessage> messages = history.withPendingPrompt(prompt);

      final List<LLMProviderClient> candidates = orderedCandidates();
      if (candidates.isEmpty()) {
         return new Reply(false, "No API keys set. Add at least one (Gemini, Claude or Groq) in the RuneGPT plugin settings.", null);
      }

      final List<String> failures = new ArrayList<>();
      for (final LLMProviderClient client : candidates) {
         final LLMProvider provider = client.provider();
         try {
            final String reply = client.send(httpClient, gson, system, messages, config);
            clearCooldown(provider);
            history.addExchange(prompt, reply);
            return new Reply(true, reply, provider);
         } catch (final ProviderException e) {
            log.warn("RuneGPT: {} failed, trying next provider: {}", provider, e.getMessage());
            failures.add(provider.getDisplayName() + ": " + e.getMessage());
            if (e.isQuotaExhausted()) {
               startCooldown(provider, e.getRetryAfter());
            }
         } catch (final RuntimeException e) {
            // Unexpected response shape etc. Don't let one provider's bug block the others.
            log.warn("RuneGPT: {} returned an unreadable response", provider, e);
            failures.add(provider.getDisplayName() + ": unreadable response");
         }
      }

      return new Reply(false, "All LLM providers are unavailable right now (out of tokens or erroring). "
         + "Try again in a bit.\n\n" + String.join("\n", failures), null);
   }

   /**
    * Providers with an API key: the primary first, then the rest in fixed order. Providers on a quota
    * cooldown move to the back rather than being dropped, so a stale cooldown never blocks an answer.
    */
   private List<LLMProviderClient> orderedCandidates() {
      final LLMProvider primary = config.provider();
      final List<LLMProviderClient> ready = new ArrayList<>();
      final List<LLMProviderClient> coolingDown = new ArrayList<>();

      final List<LLMProviderClient> ordered = new ArrayList<>(providers);
      ordered.sort((a, b) -> Boolean.compare(b.provider() == primary, a.provider() == primary));

      for (final LLMProviderClient client : ordered) {
         final String key = client.apiKey(config);
         if (key == null || key.trim().isEmpty()) {
            continue;
         }
         (isCoolingDown(client.provider()) ? coolingDown : ready).add(client);
      }
      ready.addAll(coolingDown);
      return ready;
   }

   private synchronized boolean isCoolingDown(final LLMProvider provider) {
      final Instant until = cooldownUntil.get(provider);
      return until != null && Instant.now().isBefore(until);
   }

   private synchronized void startCooldown(final LLMProvider provider, final Duration retryAfter) {
      cooldownUntil.put(provider, Instant.now().plus(retryAfter != null ? retryAfter : DEFAULT_COOLDOWN));
   }

   private synchronized void clearCooldown(final LLMProvider provider) {
      cooldownUntil.remove(provider);
   }
}
