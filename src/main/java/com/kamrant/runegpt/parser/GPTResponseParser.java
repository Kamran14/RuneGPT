package com.kamrant.runegpt.parser;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
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

   public static String parseClaudeResponse(final Gson gson, final Response response) throws Exception {
      final String raw = response.body() == null ? "" : response.body().string();

      if (!response.isSuccessful()) {
         // Error shape: {"type":"error","error":{"type":"...","message":"..."}}
         try {
            final JsonObject err = gson.fromJson(raw, JsonObject.class).getAsJsonObject("error");
            return "Claude request failed (" + response.code() + "): " + err.get("message").getAsString();
         } catch (final Exception ignored) {
            return "Claude request failed (" + response.code() + "): " + response.message();
         }
      }

      final JsonObject json = gson.fromJson(raw, JsonObject.class);
      final String stopReason = json.has("stop_reason") && !json.get("stop_reason").isJsonNull()
         ? json.get("stop_reason").getAsString()
         : "";

      if ("refusal".equals(stopReason)) {
         return "Claude declined to answer this request.";
      }

      // Content is a list of blocks (thinking, text, ...). Only text blocks are shown to the player.
      final StringBuilder text = new StringBuilder();
      for (final JsonElement el : json.getAsJsonArray("content")) {
         final JsonObject block = el.getAsJsonObject();
         if ("text".equals(block.get("type").getAsString())) {
            text.append(block.get("text").getAsString());
         }
      }

      if ("max_tokens".equals(stopReason)) {
         text.append("\n\n[Response truncated]");
      }
      return text.length() > 0 ? text.toString() : "Claude returned no text.";
   }
}
