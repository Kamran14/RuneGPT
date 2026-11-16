package com.kamrant.runegpt.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.kamrant.runegpt.llm.GPTClient;
import com.kamrant.runegpt.model.AssistantResponse;
import com.kamrant.runegpt.model.GuidePlan;
import com.kamrant.runegpt.model.LlmReply;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * One chat turn end to end: ask the LLM (with failover), parse its JSON reply into instructions plus an
 * optional guide, verify the guide against the wiki, and record the readable answer in the history.
 */
@Singleton
public class AssistantService {

   private final GPTClient gpt;
   private final GuideVerifier verifier;
   private final Gson gson;

   @Inject
   public AssistantService(final GPTClient gpt, final GuideVerifier verifier, final Gson gson) {
      this.gpt = gpt;
      this.verifier = verifier;
      this.gson = gson;
   }

   /** Blocking: LLM and wiki calls. */
   public AssistantResponse ask(final String prompt, final PlayerSnapshot snapshot) {
      final LlmReply reply = gpt.ask(prompt, snapshot.toContext());
      if (!reply.isSuccess()) {
         return AssistantResponse.failure(reply.getText());
      }
      final JsonObject json = ResponseParser.extractJson(gson, reply.getText());

      String answer = ResponseParser.str(json, "answer");
      GuidePlan guide = null;
      if (json == null || (answer.isEmpty() && !json.has("guide"))) {
         // Not our JSON shape: show it as plain Markdown rather than failing.
         answer = ResponseParser.stripFences(reply.getText());
      } else {
         guide = verifier.verify(ResponseParser.object(json, "guide"), snapshot.holdings());
      }
      if (answer.isEmpty() && guide != null) {
         answer = "### " + guide.getTitle();
      }
      gpt.remember(prompt, answer);
      return AssistantResponse.of(answer, guide);
   }
}
