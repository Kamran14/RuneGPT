package com.kamrant.runegpt.parser;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Response;

@Slf4j
public class GPTResponseParser {
   // Each service will require its own parser (e.g. ChatGPT, Deepseek, Gemini,
   // llama, etc.)

   private static Gson gson = new Gson();

   public static String parseResponse(final Response response) throws Exception {
      if (!response.isSuccessful() || response.body() == null) {
         return "GPT request failed: " + response.message();
      }

      @SuppressWarnings("null")
      final JsonObject json = gson.fromJson(response.body().string(), JsonObject.class);
      return json.getAsJsonArray("candidates")
         .get(0).getAsJsonObject()
         .getAsJsonObject("content")
         .getAsJsonArray("parts")
         .get(0).getAsJsonObject()
         .get("text")
         .getAsString();
   }
}
