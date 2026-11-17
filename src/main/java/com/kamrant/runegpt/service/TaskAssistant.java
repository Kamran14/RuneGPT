package com.kamrant.runegpt.service;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.kamrant.runegpt.llm.GPTClient;
import com.kamrant.runegpt.model.GuideItem;
import com.kamrant.runegpt.model.GuideItemKind;
import com.kamrant.runegpt.model.ItemStatus;
import com.kamrant.runegpt.model.LlmReply;
import com.kamrant.runegpt.model.QuestStep;
import com.kamrant.runegpt.model.QuestStepKind;
import com.kamrant.runegpt.model.TargetSource;
import com.kamrant.runegpt.model.WikiItem;
import com.kamrant.runegpt.model.WorldTarget;
import com.kamrant.runegpt.prompt.Prompts;
import com.kamrant.runegpt.quest.QuestAssistant;
import com.kamrant.runegpt.quest.QuestResolver;
import com.kamrant.runegpt.ui.UiTheme;
import com.kamrant.runegpt.wiki.WikiClient;
import com.kamrant.runegpt.wiki.WikiLookup;
import com.kamrant.runegpt.wiki.Wikitext;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;

/**
 * Guides the player step by step through any activity (a minigame, money maker, skilling method, boss or
 * monster task), using the same interactive view as {@link QuestAssistant}: fetches the matching OSRS Wiki
 * guide when available, checks the player's stats/gear/location, verifies every item and NPC against the
 * wiki, and returns a trackable {@link QuestStep} that can be stepped through with "Done, next step".
 */
@Slf4j
@Singleton
public class TaskAssistant {

   private static final int MAX_WIKI_CHARS = 7000;
   private static final int MAX_ITEMS = 12;

   private final WikiClient wiki;
   private final WikiLookup lookup;
   private final QuestAssistant questAssistant;
   private final PriceService prices;
   private final GPTClient gpt;
   private final Gson gson;
   private final RelevantItemChecker itemChecker;

   @Inject
   public TaskAssistant(final WikiClient wiki, final WikiLookup lookup, final QuestAssistant questAssistant,
      final PriceService prices, final GPTClient gpt, final Gson gson, final RelevantItemChecker itemChecker) {
      this.wiki = wiki;
      this.lookup = lookup;
      this.questAssistant = questAssistant;
      this.prices = prices;
      this.gpt = gpt;
      this.gson = gson;
      this.itemChecker = itemChecker;
   }

   /**
    * Blocking: fetches wiki data and asks the AI for the player's current step in {@code taskInput}.
    * Routes real quests directly to {@link QuestAssistant}.
    */
   public QuestStep nextStep(final String taskInput, final String category, final Map<String, String> picks,
      final PlayerSnapshot player, final String completedStep) {
      final String trimmed = taskInput == null ? "" : taskInput.trim();
      if (trimmed.isEmpty()) {
         return QuestStep.error("Task", "Choose a task to start first.");
      }
      if ("quest".equalsIgnoreCase(category) || QuestResolver.resolve(trimmed).getQuestName() != null) {
         return questAssistant.nextStep(trimmed, picks, player, completedStep);
      }

      // 1. Find the best matching OSRS Wiki page (strategy, money making guide, or main article).
      final String[] wikiData = fetchWikiGuide(trimmed, category);
      final String wikiTitle = wikiData[0];
      final String wikiText = wikiData[1];
      final String wikiUrl = wikiData[2];

      // 2. Ask the AI for the structured step-by-step plan.
      final String itemCheck = itemChecker.factsFor(trimmed, player, Collections.emptyList());
      final LlmReply reply = gpt.completeOnce(Prompts.TASK_SYSTEM,
         buildPrompt(trimmed, category, picks, player, completedStep, wikiText, true) + itemCheck,
         buildPrompt(trimmed, category, picks, player, completedStep, wikiText, false) + itemCheck);
      if (!reply.isSuccess()) {
         return QuestStep.error(trimmed, reply.getText()).setHeaderLabel(UiTheme.HDR_TASK);
      }
      final JsonObject json = ResponseParser.extractJson(gson, reply.getText());
      if (json == null) {
         return QuestStep.error(trimmed, Prompts.ERR_UNREADABLE_AI_REPLY)
            .setHeaderLabel(UiTheme.HDR_TASK);
      }
      final String nextStep = ResponseParser.str(json, "next_step");
      if (nextStep.isEmpty()) {
         return QuestStep.error(trimmed, "Couldn't work out the next step for " + trimmed + ".")
            .setHeaderLabel(UiTheme.HDR_TASK);
      }

      // 3. Verify NPC, location and items against the OSRS Wiki.
      int removed = 0;

      WorldTarget npc = null;
      boolean npcNearby = false;
      final String npcName = ResponseParser.str(json, "npc");
      if (!npcName.isEmpty()) {
         final WorldTarget resolvedNpc = NearestTargetResolver.resolve(npcName, player, lookup);
         if (resolvedNpc != null && resolvedNpc.getPoint() != null) {
            npc = new WorldTarget(npcName, resolvedNpc.getPoint(), TargetSource.WIKI);
            npcNearby = player.getNearbyNpcs().stream().anyMatch(n -> n.equalsIgnoreCase(npcName));
         } else if (lookup.exists(npcName)) {
            npc = WorldTarget.named(npcName);
            npcNearby = player.getNearbyNpcs().stream().anyMatch(n -> n.equalsIgnoreCase(npcName));
         } else {
            removed++;
         }
      }

      WorldTarget location = null;
      final String locationName = ResponseParser.str(json, "location");
      if (!locationName.isEmpty()) {
         final WorldTarget resolvedLoc = NearestTargetResolver.resolve(locationName, player, lookup);
         if (resolvedLoc != null && resolvedLoc.getPoint() != null) {
            location = resolvedLoc;
         } else if (lookup.exists(locationName)) {
            location = WorldTarget.named(locationName);
         } else {
            removed++;
         }
      }
      if (location == null || location.getPoint() == null) {
         final WorldTarget stepTarget = NearestTargetResolver.resolveFromText(nextStep, player, lookup);
         if (stepTarget != null && stepTarget.getPoint() != null) {
            location = stepTarget;
         }
      }

      final List<GuideItem> items = new ArrayList<>();
      for (final JsonElement el : ResponseParser.array(json, "items")) {
         if (items.size() >= MAX_ITEMS) {
            break;
         }
         final String name;
         int qty = 1;
         boolean equip = false;
         if (el.isJsonObject()) {
            final JsonObject o = el.getAsJsonObject();
            name = ResponseParser.str(o, "name");
            qty = ResponseParser.intOr(o, "quantity", 1);
            equip = o.has("equip") && !o.get("equip").isJsonNull() && o.get("equip").getAsBoolean();
         } else if (el.isJsonPrimitive()) {
            name = el.getAsString().trim();
         } else {
            continue;
         }
         if (name.isEmpty()) {
            continue;
         }
         final WikiItem info = lookup.item(name);
         if (info == null) {
            removed++;
            continue;
         }
         final GuideItemKind kind = equip && info.isEquipable() ? GuideItemKind.EQUIP : GuideItemKind.CARRY;
         items.add(new GuideItem(info.getName(), kind == GuideItemKind.EQUIP ? 1 : qty,
            kind, info.getSlot(), ItemStatus.UNKNOWN));
      }

      final List<String> completed = new ArrayList<>(ResponseParser.strings(json, "completed_steps", 4));
      if (completedStep != null && !completedStep.isEmpty() && !completed.contains(completedStep)) {
         completed.add(completedStep);
      }

      return QuestStep.of(QuestStepKind.STEP)
         .setHeaderLabel(UiTheme.HDR_TASK_WIKI)
         .setQuest(wikiTitle != null ? wikiTitle : trimmed)
         .setInput(trimmed)
         .setWikiUrl(wikiUrl)
         .setBankKnown(player.isBankKnown())
         .setProgress(ResponseParser.str(json, "progress"))
         .setConfidence(ResponseParser.str(json, "confidence").toLowerCase(Locale.ROOT))
         .setCompletedSteps(completed)
         .setNextStep(nextStep)
         .setLocation(location)
         .setNpc(npc)
         .setNpcNearby(npcNearby)
         .setItems(prices.withPrices(ItemStatusResolver.resolve(items, player.holdings())))
         .setThen(ResponseParser.strings(json, "then", 4))
         .setTip(ResponseParser.str(json, "tip"))
         .setRemovedUnverified(removed);
   }

   /** Returns { resolvedTitle, plainTextExcerpt, pageUrl }. */
   private String[] fetchWikiGuide(final String task, final String category) {
      final List<String> candidates = new ArrayList<>();
      final String cap = TextUtils.capitalize(task);
      if ("money".equalsIgnoreCase(category)) {
         candidates.add("Money making guide/" + cap);
      }
      if ("boss".equalsIgnoreCase(category) || "minigame".equalsIgnoreCase(category)) {
         candidates.add(cap + "/Strategies");
      }
      candidates.add(cap);

      for (final String title : candidates) {
         try {
            final String raw = wiki.raw(title);
            if (raw != null && !raw.isEmpty()) {
               return new String[] { task, truncate(Wikitext.toPlain(raw)), WikiClient.pageUrl(title) };
            }
         } catch (final IOException e) {
            log.debug("RuneGPT: wiki fetch failed for {}", title, e);
         }
      }

      for (final String hit : wiki.search(task, 3)) {
         try {
            final String raw = wiki.raw(hit);
            if (raw != null && !raw.isEmpty()) {
               return new String[] { hit, truncate(Wikitext.toPlain(raw)), WikiClient.pageUrl(hit) };
            }
         } catch (final IOException e) {
            log.debug("RuneGPT: wiki search fetch failed for {}", hit, e);
         }
      }
      return new String[] { task, "", WikiClient.pageUrl(cap) };
   }

   private String buildPrompt(final String task, final String category, final Map<String, String> picks,
      final PlayerSnapshot p, final String completedStep, final String wikiText, final boolean fullBank) {
      final StringBuilder sb = new StringBuilder();
      sb.append("Task: ").append(task);
      if (category != null && !category.isEmpty()) {
         sb.append(" (category: ").append(category).append(')');
      }
      sb.append('\n');
      sb.append(p.toContext(task, fullBank));
      sb.append(NearestTargetResolver.locationFactsFor(task, p, lookup));
      sb.append(prices.priceFactsFor(task, p));
      if (completedStep != null && !completedStep.isEmpty()) {
         sb.append(String.format(Prompts.QUEST_COMPLETED_STEP, completedStep));
      }
      if (picks != null && !picks.isEmpty()) {
         picks.forEach((k, v) -> sb.append(k).append(": ").append(v).append('\n'));
      }
      if (wikiText != null && !wikiText.isEmpty()) {
         sb.append(Prompts.QUEST_WIKI_HEADER).append(wikiText).append("\n\n");
      }
      sb.append(Prompts.TASK_STEP_INSTRUCTIONS);
      return sb.toString();
   }

   private static String truncate(final String s) {
      return s.length() > MAX_WIKI_CHARS ? s.substring(0, MAX_WIKI_CHARS) + "\n[...truncated]" : s;
   }
}
