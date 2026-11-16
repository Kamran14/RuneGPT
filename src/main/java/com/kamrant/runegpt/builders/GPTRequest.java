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

  public static Request buildClaudeRequest(final String apiKey, final String model, final String prompt, final Gson gson) {
    final String resolvedModel = (model == null || model.trim().isEmpty())
        ? LLMConfig.CLAUDE_DEFAULT_MODEL
        : model.trim();
    final RequestBodyClaude request = RequestBodyClaude.createRequestBody(
        resolvedModel, LLMConfig.INSTRUCTIONS, prompt, LLMConfig.CLAUDE_MAX_TOKENS);
    final RequestBody requestBody = RequestBody.create(JSON, gson.toJson(request));

    final Request.Builder builder = new Request.Builder()
        .url(LLMConfig.CLAUDE_URL)
        .addHeader(MEDIA_TYPE[0], MEDIA_TYPE[1])
        .addHeader("x-api-key", apiKey)
        .addHeader("anthropic-version", LLMConfig.CLAUDE_API_VERSION)
        .post(requestBody);

    if (RequestBodyClaude.supportsFallbacks(resolvedModel)) {
      builder.addHeader("anthropic-beta", LLMConfig.CLAUDE_FALLBACK_BETA);
    }
    return builder.build();
  }
}
