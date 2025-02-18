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

   private final String apiKey;
   private final OkHttpClient client;
   private static GPTClient instance;
   
   @Inject
   public GPTClient(final RuneGPTConfig config){
      this.apiKey = config.apiKey();   
      this.client = new OkHttpClient();
   }

   public static synchronized GPTClient getInstance(final RuneGPTConfig config){
      if (instance == null){
         instance = new GPTClient(config);
      }
      return instance;
   }


   public String queryGPT(final String prompt) {
      //TODO: model selection, instruction and context, user info
      final Request request = GPTRequest.buildRequest(apiKey, prompt);

      try (final Response response = client.newCall(request).execute()){
         
         return GPTResponseParser.parseResponse(response);
      } catch (final Exception e) {
         log.error("Error with LLM ", e);
         return "Error with LLM " + e.getMessage();
      }
   }
   
}
