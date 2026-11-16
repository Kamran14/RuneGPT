package com.kamrant.runegpt.handler;

import com.google.gson.Gson;
import com.google.inject.Singleton;
import com.kamrant.runegpt.RuneGPTConfig;
import com.kamrant.runegpt.builders.GPTRequest;
import com.kamrant.runegpt.config.LLMProvider;
import com.kamrant.runegpt.parser.GPTResponseParser;
import java.time.Duration;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

@Slf4j
@Singleton
public class GPTClient {

   @Inject
   private OkHttpClient httpClient;
   @Inject
   private Gson gson;

   private static final Duration CLAUDE_READ_TIMEOUT = Duration.ofSeconds(180);

   private final RuneGPTConfig config;
   private static GPTClient instance;

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

   public String getProviderName() {
      return config.provider().getDisplayName();
   }

   public String queryGPT(final String prompt) {
      // TODO: instruction and context, user info
      final LLMProvider provider = config.provider();
      final String apiKey = provider == LLMProvider.CLAUDE ? config.claudeApiKey() : config.apiKey();
      if (apiKey == null || apiKey.trim().isEmpty()) {
         return "No " + provider.getDisplayName() + " API key set. Add one in the RuneGPT plugin settings.";
      }

      final Request request = provider == LLMProvider.CLAUDE
         ? GPTRequest.buildClaudeRequest(apiKey.trim(), config.claudeModel(), prompt, gson)
         : GPTRequest.buildRequest(apiKey.trim(), prompt, config.temperature(), gson);
      // Claude may think before answering, so allow longer than RuneLite's default read timeout.
      final OkHttpClient callClient = provider == LLMProvider.CLAUDE
         ? httpClient.newBuilder().readTimeout(CLAUDE_READ_TIMEOUT).build()
         : httpClient;
      try (final Response response = callClient.newCall(request).execute()) {

         return provider == LLMProvider.CLAUDE
            ? GPTResponseParser.parseClaudeResponse(gson, response)
            : GPTResponseParser.parseResponse(gson, response);
      } catch (final Exception e) {
         log.error("Error with LLM ", e);
         return "Error with LLM " + e.getMessage();
      }
   }

}
