package com.kamrant.runegpt.quest;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.kamrant.runegpt.llm.GPTClient;
import com.kamrant.runegpt.model.GuideItem;
import com.kamrant.runegpt.model.GuideItemKind;
import com.kamrant.runegpt.model.ItemStatus;
import com.kamrant.runegpt.model.LlmReply;
import com.kamrant.runegpt.model.QuestMatch;
import com.kamrant.runegpt.model.QuestStep;
import com.kamrant.runegpt.model.QuestStepKind;
import com.kamrant.runegpt.model.TargetSource;
import com.kamrant.runegpt.model.WorldTarget;
import com.kamrant.runegpt.prompt.Prompts;
import com.kamrant.runegpt.service.ItemStatusResolver;
import com.kamrant.runegpt.service.PlayerSnapshot;
import com.kamrant.runegpt.service.PriceService;
import com.kamrant.runegpt.service.ResponseParser;
import com.kamrant.runegpt.wiki.WikiClient;
import com.kamrant.runegpt.wiki.WikiLookup;
import com.kamrant.runegpt.wiki.Wikitext;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.QuestState;
import net.runelite.api.coords.WorldPoint;

/**
 * Works out where the player is in a quest and what to do next, grounded in the OSRS Wiki:
 * <ol>
 *    <li>the quest name must match RuneLite's quest list (otherwise we offer the closest real names);</li>
 *    <li>the wiki's quick guide and quest details are fetched and given to the AI as the only source;</li>
 *    <li>any item or NPC the AI returns that the wiki text doesn't mention is dropped;</li>
 *    <li>NPC and location coordinates come from their wiki pages, for navigation.</li>
 * </ol>
 */
@Slf4j
@Singleton
public class QuestAssistant {

   // Keep the wiki excerpt within free-tier token budgets.
   private static final int MAX_WIKI_CHARS = 9000;
   // Chat grounding: facts for at most this many mentioned quests, with a short walkthrough excerpt each.
   private static final int MAX_FACT_QUESTS = 2;
   private static final int MAX_FACT_GUIDE_CHARS = 1500;
   private static final String[] DETAIL_KEYS = { "start", "difficulty", "length", "requirements", "items", "recommended", "kills" };
   private static final String[] REQUIREMENT_KEYS = { "requirements", "items", "recommended", "kills", "start" };

   private final WikiClient wiki;
   private final WikiLookup lookup;
   private final GPTClient gpt;
   private final Gson gson;
   private final PriceService prices;

   @Inject
   public QuestAssistant(final WikiClient wiki, final WikiLookup lookup, final GPTClient gpt, final Gson gson,
      final PriceService prices) {
      this.wiki = wiki;
      this.lookup = lookup;
      this.gpt = gpt;
      this.gson = gson;
      this.prices = prices;
   }

   /** Blocking: does network calls. {@code completedStep} is the step the player just finished, or null. */
   public QuestStep nextStep(final String questInput, final Map<String, String> picks, final PlayerSnapshot player,
      final String completedStep) {
      // 1. Is it a real quest?
      final QuestStep notAQuest = checkQuest(questInput);
      if (notAQuest != null) {
         return notAQuest;
      }
      final String quest = QuestResolver.resolve(questInput).getQuestName();

      final QuestState state = player.questState(quest);
      if (state == QuestState.FINISHED) {
         return QuestStep.of(QuestStepKind.COMPLETE).setQuest(quest).setInput(questInput)
            .setWikiUrl(WikiClient.pageUrl(quest));
      }

      // 2. Wiki text: quick guide (steps) + main page (requirements, items, enemies).
      final String guideText;
      final String wikiUrl;
      try {
         final String main = wiki.raw(quest);
         if (main == null) {
            return QuestStep.error(quest, "The OSRS Wiki has no page for " + quest + ", so I can't give verified steps.");
         }
         final String quick = wiki.raw(quest + "/Quick guide");
         wikiUrl = WikiClient.pageUrl(quick != null ? quest + "/Quick guide" : quest);
         guideText = buildWikiText(quest, main, quick);
      } catch (final IOException e) {
         log.warn("RuneGPT: wiki fetch failed for {}", quest, e);
         return QuestStep.error(quest, "Couldn't reach the OSRS Wiki (" + e.getMessage() + "). "
            + "I won't guess quest steps without it; try again in a moment.");
      }

      // 3. Ask the AI for a structured step.
      final LlmReply reply = gpt.completeOnce(Prompts.QUEST_SYSTEM,
         buildPrompt(quest, state, picks, player, completedStep, guideText));
      if (!reply.isSuccess()) {
         return QuestStep.error(quest, reply.getText());
      }
      final JsonObject json = ResponseParser.extractJson(gson, reply.getText());
      if (json == null) {
         return QuestStep.error(quest, "The AI's answer couldn't be read. Try again, or open the wiki guide.");
      }
      final String nextStep = ResponseParser.str(json, "next_step");
      if (nextStep.isEmpty()) {
         return QuestStep.error(quest, "I couldn't work out a next step from the wiki guide. Open the guide to check.");
      }

      // 4. Keep only what the wiki text supports, and look up coordinates for navigation.
      final String corpus = guideText.toLowerCase(Locale.ROOT);
      int removed = 0;

      WorldTarget npc = null;
      boolean npcNearby = false;
      final String npcName = ResponseParser.str(json, "npc");
      if (!npcName.isEmpty()) {
         if (mentioned(corpus, npcName)) {
            final WorldPoint p = lookup.coordinates(npcName);
            npc = p == null ? WorldTarget.named(npcName) : new WorldTarget(npcName, p, TargetSource.WIKI);
            npcNearby = player.getNearbyNpcs().stream().anyMatch(n -> n.equalsIgnoreCase(npcName));
         } else {
            removed++;
         }
      }

      WorldTarget location = null;
      final String locationName = ResponseParser.str(json, "location");
      if (!locationName.isEmpty()) {
         final WorldPoint p = lookup.coordinates(locationName);
         location = p == null ? WorldTarget.named(locationName) : new WorldTarget(locationName, p, TargetSource.WIKI);
      }

      final List<GuideItem> items = new ArrayList<>();
      for (final JsonElement el : ResponseParser.array(json, "items")) {
         final String name;
         int qty = 1;
         if (el.isJsonObject()) {
            name = ResponseParser.str(el.getAsJsonObject(), "name");
            qty = ResponseParser.intOr(el.getAsJsonObject(), "quantity", 1);
         } else if (el.isJsonPrimitive()) {
            name = el.getAsString().trim();
         } else {
            continue;
         }
         if (name.isEmpty()) {
            continue;
         }
         if (!mentioned(corpus, name)) {
            removed++;
            continue;
         }
         items.add(new GuideItem(name, qty, GuideItemKind.CARRY, null, ItemStatus.UNKNOWN));
      }

      return QuestStep.of(QuestStepKind.STEP)
         .setQuest(quest)
         .setInput(questInput)
         .setWikiUrl(wikiUrl)
         .setBankKnown(player.isBankKnown())
         .setProgress(ResponseParser.str(json, "progress"))
         .setConfidence(ResponseParser.str(json, "confidence").toLowerCase(Locale.ROOT))
         .setCompletedSteps(ResponseParser.strings(json, "completed_steps", 4))
         .setNextStep(nextStep)
         .setLocation(location)
         .setNpc(npc)
         .setNpcNearby(npcNearby)
         .setItems(prices.withPrices(ItemStatusResolver.resolve(items, player.holdings())))
         .setThen(ResponseParser.strings(json, "then", 3))
         .setTip(ResponseParser.str(json, "tip"))
         .setRemovedUnverified(removed);
   }

   /** Returns a NOT_A_QUEST result if {@code input} isn't a real quest, or null if it is. Blocking (wiki search). */
   public QuestStep checkQuest(final String input) {
      final QuestMatch match = QuestResolver.resolve(input);
      if (match.getQuestName() != null) {
         return null;
      }
      return QuestStep.of(QuestStepKind.NOT_A_QUEST)
         .setInput(input)
         .setSuggestions(match.getSuggestions())
         .setWikiMatches(wiki.search(input, 4));
   }

   /**
    * Adds the wiki's requirement data for {@code questInput} to {@code prompt} so the AI answers from it.
    * Blocking. Returns the prompt unchanged if the wiki can't be reached (the system prompt still forbids guessing).
    */
   public String withWikiRequirements(final String questInput, final String prompt) {
      final String quest = QuestResolver.resolve(questInput).getQuestName();
      if (quest == null) {
         return prompt;
      }
      try {
         final String main = wiki.raw(quest);
         if (main == null) {
            return prompt;
         }
         final Map<String, String> d = Wikitext.templateParams(main, "Quest details");
         final StringBuilder sb = new StringBuilder(prompt.replace(questInput, quest));
         sb.append(String.format(Prompts.REQUIREMENTS_WIKI_HEADER, quest));
         for (final String key : REQUIREMENT_KEYS) {
            final String v = d.get(key);
            if (v != null && !v.isEmpty()) {
               sb.append(key).append(":\n").append(v).append('\n');
            }
         }
         return sb.toString();
      } catch (final IOException e) {
         log.warn("RuneGPT: wiki fetch failed for {}", quest, e);
         return prompt;
      }
   }

   /**
    * Verified OSRS Wiki facts for the quests mentioned in {@code question} (official quest list + wiki), to
    * attach to a general chat request. Empty when none are mentioned. Blocking.
    */
   public String wikiFactsFor(final String question, final PlayerSnapshot player) {
      final List<String> quests = QuestMentions.find(question, MAX_FACT_QUESTS);
      if (quests.isEmpty()) {
         return "";
      }
      final StringBuilder sb = new StringBuilder(Prompts.WIKI_FACTS_HEADER);
      for (final String quest : quests) {
         try {
            final String main = wiki.raw(quest);
            final boolean mini = main != null && main.contains("{{Infobox Miniquest");
            sb.append("\n").append(quest).append(" (a real OSRS ").append(mini ? "miniquest" : "quest").append(")\n");
            final QuestState state = player.questState(quest);
            if (state != null) {
               sb.append("player status: ").append(state.name().toLowerCase(Locale.ROOT).replace('_', ' ')).append('\n');
            }
            sb.append(player.questProgressDetails(quest));
            if (main == null) {
               continue;
            }
            final Map<String, String> d = Wikitext.templateParams(main, "Quest details");
            for (final String key : DETAIL_KEYS) {
               final String v = d.get(key);
               if (v != null && !v.isEmpty()) {
                  sb.append(key).append(": ").append(v).append('\n');
               }
            }
            final String quick = wiki.raw(quest + "/Quick guide");
            if (quick != null) {
               final String steps = Wikitext.toPlain(quick);
               sb.append("walkthrough (excerpt): ")
                  .append(steps.length() > MAX_FACT_GUIDE_CHARS ? steps.substring(0, MAX_FACT_GUIDE_CHARS) + "..." : steps)
                  .append('\n');
            }
         } catch (final IOException e) {
            log.debug("RuneGPT: wiki facts unavailable for {}", quest, e);
         }
      }
      return sb.toString();
   }

   private static String buildWikiText(final String quest, final String mainRaw, final String quickRaw) {
      final StringBuilder sb = new StringBuilder();
      final Map<String, String> details = Wikitext.templateParams(mainRaw, "Quest details");
      sb.append("Quest: ").append(quest).append('\n');
      for (final String key : DETAIL_KEYS) {
         final String v = details.get(key);
         if (v != null && !v.isEmpty()) {
            sb.append(key).append(": ").append(v).append('\n');
         }
      }
      sb.append("\nWalkthrough:\n");
      sb.append(Wikitext.toPlain(quickRaw != null ? quickRaw : mainRaw));
      return sb.length() > MAX_WIKI_CHARS ? sb.substring(0, MAX_WIKI_CHARS) + "\n[...truncated]" : sb.toString();
   }

   private static String buildPrompt(final String quest, final QuestState state, final Map<String, String> picks,
      final PlayerSnapshot p, final String completedStep, final String wikiText) {
      final StringBuilder sb = new StringBuilder();
      sb.append("Quest: ").append(quest).append(" (status: ")
         .append(state == null ? "unknown" : state.name().toLowerCase(Locale.ROOT).replace('_', ' ')).append(")\n");
      sb.append(p.questProgressDetails(quest));
      sb.append("Player location: ").append(p.locationText()).append('\n');
      sb.append("Inventory: ").append(PlayerSnapshot.items(p.getInventory())).append('\n');
      sb.append("Wearing: ").append(PlayerSnapshot.items(p.getEquipment())).append('\n');
      sb.append("Bank: ").append(p.isBankKnown() ? PlayerSnapshot.items(p.getBank()) : "unknown (not opened yet)").append('\n');
      if (completedStep != null) {
         sb.append(String.format(Prompts.QUEST_COMPLETED_STEP, completedStep));
      }
      if (picks != null && !picks.isEmpty()) {
         picks.forEach((k, v) -> sb.append(k).append(": ").append(v).append('\n'));
      }
      sb.append(Prompts.QUEST_WIKI_HEADER).append(wikiText).append("\n\n");
      sb.append(Prompts.QUEST_STEP_INSTRUCTIONS);
      return sb.toString();
   }

   /** True if the wiki text mentions the name (also accepting simple singular/plural forms). */
   static boolean mentioned(final String corpusLower, final String name) {
      final String n = name.toLowerCase(Locale.ROOT).trim();
      if (n.isEmpty()) {
         return false;
      }
      if (corpusLower.contains(n)) {
         return true;
      }
      if (n.endsWith("es") && corpusLower.contains(n.substring(0, n.length() - 2))) {
         return true;
      }
      if (n.endsWith("s") && corpusLower.contains(n.substring(0, n.length() - 1))) {
         return true;
      }
      return corpusLower.contains(n + "s");
   }
}
