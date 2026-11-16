package com.kamrant.runegpt.quest;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.kamrant.runegpt.handler.GPTClient;
import com.kamrant.runegpt.service.PlayerSnapshot;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.QuestState;

/**
 * Works out where the player is in a quest and what to do next, grounded in the OSRS Wiki:
 * <ol>
 *    <li>the quest name must match RuneLite's quest list (otherwise we offer the closest real names);</li>
 *    <li>the wiki's quick guide and quest details are fetched and given to the AI as the only source;</li>
 *    <li>any item or NPC the AI returns that the wiki text doesn't mention is dropped.</li>
 * </ol>
 */
@Slf4j
@Singleton
public class QuestAssistant {

   // Keep the wiki excerpt within free-tier token budgets.
   private static final int MAX_WIKI_CHARS = 9000;

   private static final String SYSTEM = "You are an Old School RuneScape quest assistant. You are given the "
      + "official OSRS Wiki text for ONE quest and the player's current state. Use ONLY facts from the wiki "
      + "text. Never invent steps, items, NPCs or locations; if the wiki text doesn't say, leave the field "
      + "empty. Respond with a single JSON object and nothing else (no markdown, no code fences).";

   private final WikiClient wiki;
   private final GPTClient gpt;
   private final Gson gson;

   @Inject
   public QuestAssistant(final WikiClient wiki, final GPTClient gpt, final Gson gson) {
      this.wiki = wiki;
      this.gpt = gpt;
      this.gson = gson;
   }

   /**
    * Blocking: does network calls. {@code completedStep} is the step the player just finished, or null.
    */
   public QuestStep nextStep(final String questInput, final Map<String, String> picks, final PlayerSnapshot player,
      final String completedStep) {
      // 1. Is it a real quest?
      final QuestResolver.Result resolved = QuestResolver.resolve(questInput);
      if (resolved.getQuestName() == null) {
         final QuestStep s = new QuestStep();
         s.kind = QuestStep.Kind.NOT_A_QUEST;
         s.input = questInput;
         s.suggestions = resolved.getSuggestions();
         s.wikiMatches = wiki.search(questInput, 4);
         return s;
      }
      final String quest = resolved.getQuestName();

      final QuestState state = player.questState(quest);
      if (state == QuestState.FINISHED) {
         final QuestStep s = new QuestStep();
         s.kind = QuestStep.Kind.COMPLETE;
         s.quest = quest;
         s.input = questInput;
         s.wikiUrl = WikiClient.pageUrl(quest);
         return s;
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
      final GPTClient.Reply reply = gpt.completeOnce(SYSTEM, buildPrompt(quest, state, picks, player, completedStep, guideText));
      if (!reply.isSuccess()) {
         return QuestStep.error(quest, reply.getText());
      }
      final JsonObject json = parseJson(reply.getText());
      if (json == null) {
         return QuestStep.error(quest, "The AI's answer couldn't be read. Try again, or open the wiki guide.");
      }

      // 4. Keep only what the wiki text supports.
      final String corpus = guideText.toLowerCase(Locale.ROOT);
      final QuestStep s = new QuestStep();
      s.kind = QuestStep.Kind.STEP;
      s.quest = quest;
      s.input = questInput;
      s.wikiUrl = wikiUrl;
      s.provider = reply.getProvider().getDisplayName();
      s.bankKnown = player.isBankKnown();
      s.progress = str(json, "progress");
      s.confidence = str(json, "confidence").toLowerCase(Locale.ROOT);
      s.nextStep = str(json, "next_step");
      s.location = str(json, "location");
      s.tip = str(json, "tip");

      final String npc = str(json, "npc");
      if (!npc.isEmpty()) {
         if (mentioned(corpus, npc)) {
            s.npc = npc;
            s.npcNearby = player.getNearbyNpcs().stream().anyMatch(n -> n.equalsIgnoreCase(npc));
         } else {
            s.removedUnverified++;
         }
      }

      final List<QuestStep.ItemCheck> items = new ArrayList<>();
      final JsonElement itemsEl = json.get("items");
      if (itemsEl != null && itemsEl.isJsonArray()) {
         for (final JsonElement el : itemsEl.getAsJsonArray()) {
            final String name;
            int qty = 1;
            if (el.isJsonObject()) {
               name = str(el.getAsJsonObject(), "name");
               try {
                  qty = Math.max(1, el.getAsJsonObject().get("quantity").getAsInt());
               } catch (final Exception ignored) {
                  // default 1
               }
            } else if (el.isJsonPrimitive()) {
               name = el.getAsString().trim();
            } else {
               continue;
            }
            if (name.isEmpty()) {
               continue;
            }
            if (!mentioned(corpus, name)) {
               s.removedUnverified++;
               continue;
            }
            items.add(new QuestStep.ItemCheck(name, qty, itemStatus(name, qty, player)));
         }
      }
      s.items = items;

      final List<String> then = new ArrayList<>();
      final JsonElement thenEl = json.get("then");
      if (thenEl != null && thenEl.isJsonArray()) {
         for (final JsonElement el : thenEl.getAsJsonArray()) {
            if (el.isJsonPrimitive() && !el.getAsString().trim().isEmpty() && then.size() < 3) {
               then.add(el.getAsString().trim());
            }
         }
      }
      s.then = then;

      if (s.nextStep.isEmpty()) {
         return QuestStep.error(quest, "I couldn't work out a next step from the wiki guide. Open the guide to check.");
      }
      return s;
   }

   /** Returns a NOT_A_QUEST result if {@code input} isn't a real quest, or null if it is. Blocking (wiki search). */
   public QuestStep checkQuest(final String input) {
      final QuestResolver.Result resolved = QuestResolver.resolve(input);
      if (resolved.getQuestName() != null) {
         return null;
      }
      final QuestStep s = new QuestStep();
      s.kind = QuestStep.Kind.NOT_A_QUEST;
      s.input = input;
      s.suggestions = resolved.getSuggestions();
      s.wikiMatches = wiki.search(input, 4);
      return s;
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
         sb.append("\n\nOfficial OSRS Wiki data for ").append(quest).append(" (use only this for requirements):\n");
         for (final String key : new String[] { "requirements", "items", "recommended", "kills", "start" }) {
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

   private static String buildWikiText(final String quest, final String mainRaw, final String quickRaw) {
      final StringBuilder sb = new StringBuilder();
      final Map<String, String> details = Wikitext.templateParams(mainRaw, "Quest details");
      sb.append("Quest: ").append(quest).append('\n');
      for (final String key : new String[] { "start", "difficulty", "length", "requirements", "items", "recommended", "kills" }) {
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
      sb.append("Player location: ").append(p.locationText()).append('\n');
      sb.append("Inventory: ").append(PlayerSnapshot.items(p.getInventory())).append('\n');
      sb.append("Wearing: ").append(PlayerSnapshot.items(p.getEquipment())).append('\n');
      sb.append("Bank: ").append(p.isBankKnown() ? PlayerSnapshot.items(p.getBank()) : "unknown (not opened yet)").append('\n');
      if (completedStep != null) {
         sb.append("The player says they just completed this step: ").append(completedStep).append('\n');
      }
      if (picks != null && !picks.isEmpty()) {
         picks.forEach((k, v) -> sb.append(k).append(": ").append(v).append('\n'));
      }
      sb.append("\nOSRS WIKI TEXT (the only source you may use):\n").append(wikiText).append("\n\n");
      sb.append("Work out where the player most likely is in this quest from the status, their items, location ")
         .append("and nearby NPCs, then what they should do next. If the quest is not started, the next step is ")
         .append("how to start it. Reply with JSON exactly like:\n")
         .append("{\"progress\": \"where they likely are, one sentence\", ")
         .append("\"confidence\": \"high|medium|low\", ")
         .append("\"next_step\": \"what to do now, 1-2 sentences, include dialogue options if the wiki lists them\", ")
         .append("\"location\": \"where to go, as named in the wiki\", ")
         .append("\"npc\": \"exact name of the NPC to talk to next as written in the wiki, or empty\", ")
         .append("\"items\": [{\"name\": \"exact item name from the wiki\", \"quantity\": 1}], ")
         .append("\"then\": [\"up to 3 following steps, short\"], ")
         .append("\"tip\": \"one short tip from the wiki, or empty\"}\n")
         .append("Only list items needed for the next few steps.");
      return sb.toString();
   }

   private JsonObject parseJson(final String text) {
      final int start = text.indexOf('{');
      final int end = text.lastIndexOf('}');
      if (start < 0 || end <= start) {
         return null;
      }
      try {
         return gson.fromJson(text.substring(start, end + 1), JsonObject.class);
      } catch (final Exception e) {
         log.debug("RuneGPT: unparseable quest step JSON: {}", text);
         return null;
      }
   }

   private static String str(final JsonObject o, final String key) {
      final JsonElement e = o.get(key);
      if (e == null || e.isJsonNull()) {
         return "";
      }
      if (e.isJsonArray()) {
         final JsonArray arr = e.getAsJsonArray();
         final List<String> parts = new ArrayList<>();
         arr.forEach(x -> parts.add(x.isJsonPrimitive() ? x.getAsString() : x.toString()));
         return String.join(", ", parts);
      }
      return e.isJsonPrimitive() ? e.getAsString().trim() : "";
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

   private static QuestStep.ItemStatus itemStatus(final String name, final int qty, final PlayerSnapshot p) {
      if (!p.isLoggedIn()) {
         return QuestStep.ItemStatus.UNKNOWN;
      }
      if (count(p.getInventory(), name) >= qty) {
         return QuestStep.ItemStatus.HAVE;
      }
      if (count(p.getEquipment(), name) >= qty) {
         return QuestStep.ItemStatus.WORN;
      }
      if (count(p.getBank(), name) + count(p.getInventory(), name) >= qty) {
         return QuestStep.ItemStatus.IN_BANK;
      }
      return p.isBankKnown() ? QuestStep.ItemStatus.MISSING : QuestStep.ItemStatus.UNKNOWN;
   }

   private static int count(final Map<String, Integer> items, final String name) {
      final String n = name.toLowerCase(Locale.ROOT);
      int total = 0;
      for (final Map.Entry<String, Integer> e : items.entrySet()) {
         final String have = e.getKey().toLowerCase(Locale.ROOT);
         if (have.equals(n) || have.equals(n + "s") || n.equals(have + "s")) {
            total += e.getValue();
         }
      }
      return total;
   }
}
