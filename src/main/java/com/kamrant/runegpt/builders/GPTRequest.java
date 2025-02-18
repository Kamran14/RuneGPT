package com.kamrant.runegpt.builders;

import static com.kamrant.runegpt.config.LLMConfig.GPT_URL;
import static com.kamrant.runegpt.config.LLMConfig.MEDIA_TYPE;
import static net.runelite.http.api.RuneLiteAPI.JSON;

import com.kamrant.runegpt.config.LLMConfig;
import okhttp3.Request;
import okhttp3.RequestBody;

public class GPTRequest {

    public static Request buildRequest(final String apiKey, final String prompt, final double temp){
      final String fullPrompt = LLMConfig.INSTRUCTIONS + "\n\n" + prompt;
      final String body = String.format(
        "{\"contents\":[{\"role\":\"user\",\"parts\":[{\"text\": \"%s\"}]}],\"generationConfig\":{\"temperature\": %f,\"topK\":40,\"topP\":0.95,\"maxOutputTokens\":1000,\"responseMimeType\":\"text/plain\"}}",
        fullPrompt,
        temp);

      final RequestBody requestBody = RequestBody.create(JSON, body);

      return new Request.Builder()
            .url(GPT_URL + apiKey)
            .addHeader(MEDIA_TYPE[0], MEDIA_TYPE[1])
            .post(requestBody)
            .build();
   }
}
