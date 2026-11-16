package com.kamrant.runegpt.llm;

import com.google.gson.Gson;
import com.google.inject.Singleton;
import com.kamrant.runegpt.RuneGPTConfig;
import com.kamrant.runegpt.config.LLMProvider;
import com.kamrant.runegpt.model.ChatMessage;
import com.kamrant.runegpt.model.ChatRole;
import com.kamrant.runegpt.model.LlmReply;
import com.kamrant.runegpt.prompt.Prompts;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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

   private final RuneGPTConfig config;
   private final OkHttpClient httpClient;
   private final Gson gson;

   // Fixed failover order after the primary provider.
   private final List<LLMProviderClient> providers = Arrays.asList(
      new GeminiProviderClient(), new ClaudeProviderClient(), new GroqProviderClient());
   // Keyless provider, added last by orderedCandidates() rather than by API key.
   private final StarterProviderClient starter = new StarterProviderClient();
   private final ConversationHistory history = new ConversationHistory();
   private final Map<LLMProvider, Instant> cooldownUntil = new EnumMap<>(LLMProvider.class);

   @Inject
   public GPTClient(final RuneGPTConfig config, final OkHttpClient httpClient, final Gson gson) {
      this.config = config;
      this.httpClient = httpClient;
      this.gson = gson;
   }

   public void clearConversation() {
      history.clear();
   }

   /**
    * Ask a question in the ongoing conversation. {@code playerContext} (stats, gear...) goes in the system
    * prompt and is refreshed on every call, so it never goes stale in the history. The exchange is not
    * recorded until the caller has parsed the reply: see {@link #remember}.
    */
   public LlmReply ask(final String prompt, final String playerContext) {
      final String system = Prompts.SYSTEM + Prompts.PLAYER_CONTEXT_HEADER + playerContext;
      return send(system, history.withPendingPrompt(prompt));
   }

   /** Records a finished exchange. Store the readable answer, not raw JSON, to keep the history small. */
   public void remember(final String prompt, final String answer) {
      history.addExchange(prompt, answer);
   }

   /**
    * One-off request with its own system prompt, outside the chat history (e.g. structured quest
    * steps). Same provider failover as {@link #ask}.
    */
   public LlmReply completeOnce(final String system, final String prompt) {
      return send(system, Collections.singletonList(new ChatMessage(ChatRole.USER, prompt)));
   }

   private LlmReply send(final String system, final List<ChatMessage> messages) {
      final List<LLMProviderClient> candidates = orderedCandidates();
      if (candidates.isEmpty()) {
         return LlmReply.failure("No API key set. Add a Gemini, Claude or Groq key in the RuneGPT plugin settings "
            + "(Groq is free), or turn on the free starter service.");
      }

      final List<String> failures = new ArrayList<>();
      for (final LLMProviderClient client : candidates) {
         final LLMProvider provider = client.provider();
         try {
            final String reply = client.send(httpClient, gson, system, messages, config);
            clearCooldown(provider);
            return LlmReply.success(reply, provider);
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

      if (candidates.size() == 1 && candidates.get(0) == starter) {
         return LlmReply.failure("The free Starter service couldn't answer (" + failures.get(0).replaceFirst("^Starter: ", "")
            + "). It allows about one reply every 45 seconds; try again shortly, or add a free Groq key in the "
            + "RuneGPT settings for faster, better answers.");
      }
      return LlmReply.failure("All LLM providers are unavailable right now (out of tokens or erroring). "
         + "Try again in a bit.\n\n" + String.join("\n", failures));
   }

   /**
    * Providers with an API key: the primary first, then the rest in fixed order. Providers on a quota
    * cooldown move to the back rather than being dropped, so a stale cooldown never blocks an answer.
    */
   private List<LLMProviderClient> orderedCandidates() {
      final LLMProvider primary = config.provider() == null ? null : config.provider().toProvider();
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

      // Keyless Starter: only when the player has turned it on, and always last.
      if (config.useStarterProvider()) {
         ready.add(starter);
      }
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
