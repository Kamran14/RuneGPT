package com.kamrant.runegpt.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.kamrant.runegpt.llm.GPTClient;
import com.kamrant.runegpt.llm.StreamListener;
import com.kamrant.runegpt.model.AssistantResponse;
import com.kamrant.runegpt.model.GuidePlan;
import com.kamrant.runegpt.model.LlmReply;
import com.kamrant.runegpt.model.TaskSuggestion;
import com.kamrant.runegpt.model.WorldTarget;
import com.kamrant.runegpt.quest.QuestAssistant;
import com.kamrant.runegpt.wiki.WikiLookup;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * One chat turn end to end: ask the LLM (with failover), parse its JSON reply into instructions plus an
 * optional guide and actionable task suggestions, verify the guide against the wiki, and record the readable
 * answer in the history.
 */
@Singleton
public class AssistantService {

   private static final int MAX_SUGGESTIONS = 5;

   private final GPTClient gpt;
   private final GuideVerifier verifier;
   private final Gson gson;
   private final QuestAssistant quests;
   private final PriceService prices;
   private final WikiLookup lookup;
   private final RelevantItemChecker itemChecker;

   @Inject
   public AssistantService(final GPTClient gpt, final GuideVerifier verifier, final Gson gson,
      final QuestAssistant quests, final PriceService prices, final WikiLookup lookup,
      final RelevantItemChecker itemChecker) {
      this.gpt = gpt;
      this.quests = quests;
      this.verifier = verifier;
      this.gson = gson;
      this.prices = prices;
      this.lookup = lookup;
      this.itemChecker = itemChecker;
   }

   /** Blocking: LLM and wiki calls. */
   public AssistantResponse ask(final String prompt, final PlayerSnapshot snapshot) {
      return ask(prompt, snapshot, null);
   }

   /**
    * Like {@link #ask(String, PlayerSnapshot)}, but passes the readable answer text to {@code display} while the
    * reply streams in (JSON wrapping removed). The returned response is still the fully parsed and verified one.
    */
   public AssistantResponse ask(final String prompt, final PlayerSnapshot snapshot, final StreamListener display) {
      final WorldTarget promptTarget = NearestTargetResolver.resolveFromText(prompt, snapshot, lookup);
      // Ground answers in official quest + wiki data, nearest coordinates from the player, and live GE prices.
      // The items this question needs are checked locally against inventory, worn items and bank, so the AI knows
      // what the player has even when only a trimmed bank is sent (token-limited providers).
      final String facts = NearestTargetResolver.locationFactsFor(prompt, snapshot, lookup)
         + quests.wikiFactsFor(prompt, snapshot)
         + itemChecker.factsFor(prompt, snapshot, Collections.emptyList())
         + prices.priceFactsFor(prompt, snapshot);
      final LlmReply reply = gpt.ask(prompt, snapshot.toContext(prompt, true) + facts,
         snapshot.toContext(prompt, false) + facts, display == null ? null : answerStream(display));
      if (!reply.isSuccess()) {
         return AssistantResponse.failure(reply.getText());
      }
      final JsonObject json = ResponseParser.extractJson(gson, reply.getText());

      String answer = ResponseParser.str(json, "answer");
      GuidePlan guide = null;
      if (json == null || (answer.isEmpty() && !json.has("guide") && !json.has("suggestions"))) {
         // Not our JSON shape: show it as plain Markdown rather than failing.
         answer = ResponseParser.stripFences(reply.getText());
      } else {
         guide = verifier.verify(ResponseParser.object(json, "guide"), snapshot);
      }
      if (promptTarget != null && promptTarget.getPoint() != null) {
         if (guide == null) {
            guide = new GuidePlan(promptTarget.getName(),
               Collections.singletonList("Go to " + promptTarget.getName()), 0,
               Collections.emptyList(), promptTarget, null, 0);
         } else if (guide.getLocation() == null || guide.getLocation().getPoint() == null) {
            guide = new GuidePlan(guide.getTitle(), guide.getSteps(), guide.getCurrentStep(),
               guide.getItems(), promptTarget, guide.getNpc(), guide.getRemovedUnverified());
         }
      }
      if (answer.isEmpty() && guide != null) {
         answer = "### " + guide.getTitle();
      }
      final List<TaskSuggestion> suggestions = snapshot.filterValidSuggestions(
         ResponseParser.suggestions(json, answer, guide != null, MAX_SUGGESTIONS));
      gpt.remember(prompt, answer);
      return AssistantResponse.of(answer, guide, suggestions);
   }

   /** Turns raw streamed reply text into the answer text the player should see so far. */
   private static StreamListener answerStream(final StreamListener display) {
      final StreamingAnswerExtractor extractor = new StreamingAnswerExtractor();
      return new StreamListener() {
         @Override
         public void onText(final String delta) {
            final String visible = extractor.feed(delta);
            if (!visible.isEmpty()) {
               display.onText(visible);
            }
         }

         @Override
         public void onReset() {
            extractor.reset();
            display.onReset();
         }
      };
   }
}
