package com.kamrant.runegpt.handler;

import com.kamrant.runegpt.RuneGPTConfig;
import com.kamrant.runegpt.builders.GPTRequest;
import com.kamrant.runegpt.parser.GPTResponseParser;
import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

@Slf4j
public class GPTClient {

   private final RuneGPTConfig config;
   private static OkHttpClient httpClient;
   private static GPTClient instance;

   @Inject
   public GPTClient(final RuneGPTConfig config, final OkHttpClient httpClient) {
      this.config = config;
      this.httpClient = httpClient;
   }

   public static synchronized GPTClient getInstance(final RuneGPTConfig config) {
      if (instance == null) {
         instance = new GPTClient(config, httpClient.newBuilder().build());
      }
      return instance;
   }

   public String queryGPT(final String prompt) {
      // TODO: model selection, instruction and context, user info
      final Request request = GPTRequest.buildRequest(config.apiKey(), prompt, config.temperature());
   
      try (final Response response = httpClient.newCall(request).execute()) {

         return GPTResponseParser.parseResponse(response);
      } catch (final Exception e) {
         log.error("Error with LLM ", e);
         return "Error with LLM " + e.getMessage();
      }
   }

}
