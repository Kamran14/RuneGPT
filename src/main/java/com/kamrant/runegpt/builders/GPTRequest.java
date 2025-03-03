package com.kamrant.runegpt.builders;

import static com.kamrant.runegpt.config.LLMConfig.GPT_URL;
import static com.kamrant.runegpt.config.LLMConfig.MEDIA_TYPE;
import static net.runelite.http.api.RuneLiteAPI.JSON;
import com.google.gson.Gson;
import com.kamrant.runegpt.config.LLMConfig;
import okhttp3.Request;
import okhttp3.RequestBody;

public class GPTRequest {

  public static Request buildRequest(final String apiKey, final String prompt, final double temp, final Gson gson) {
    final String fullPrompt = LLMConfig.INSTRUCTIONS + "\n\n" + prompt;
    final RequestBodyGemini request = RequestBodyGemini.createRequestBody(fullPrompt, temp);
    final String body = gson.toJson(request);
    final RequestBody requestBody = RequestBody.create(JSON, body);

    return new Request.Builder()
        .url(GPT_URL + apiKey)
        .addHeader(MEDIA_TYPE[0], MEDIA_TYPE[1])
        .post(requestBody)
        .build();
  }
}
