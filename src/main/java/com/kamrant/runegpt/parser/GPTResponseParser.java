package com.kamrant.runegpt.parser;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import okhttp3.Response;

public class GPTResponseParser {
   // Each service will require its own parser (e.g. ChatGPT, Deepseek, Gemini,
   // llama, etc.)
   public static String parseResponse(final Gson gson, final Response response) throws Exception {
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
