package com.kamrant.runegpt.builders;

import okhttp3.Request;
import okhttp3.RequestBody;
import static com.kamrant.runegpt.RuneGPTConfig.GPT_URL;
import static com.kamrant.runegpt.RuneGPTConfig.MEDIA_TYPE;
import static net.runelite.http.api.RuneLiteAPI.JSON;

public class GPTRequest {

    public static Request buildRequest(final String apiKey, final String prompt){
      final double temp = 0.7;
      final String body = String.format(
        "{\"contents\":[{\"role\":\"user\",\"parts\":[{\"text\": \"%s\"}]}],\"generationConfig\":{\"temperature\": %f,\"topK\":40,\"topP\":0.95,\"maxOutputTokens\":1000,\"responseMimeType\":\"text/plain\"}}",
        prompt,
        temp);

      final RequestBody requestBody = RequestBody.create(JSON, body);

      return new Request.Builder()
            .url(GPT_URL + apiKey)
            .addHeader(MEDIA_TYPE[0], MEDIA_TYPE[1])
            .post(requestBody)
            .build();
   }

   
}
