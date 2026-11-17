package com.kamrant.runegpt.service;

import com.kamrant.runegpt.model.GuideItem;
import com.kamrant.runegpt.model.GuidePlan;
import com.kamrant.runegpt.model.WikiItem;
import com.kamrant.runegpt.prompt.Prompts;
import com.kamrant.runegpt.quest.QuestMentions;
import com.kamrant.runegpt.wiki.WikiClient;
import com.kamrant.runegpt.wiki.WikiLookup;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Works out which items matter for a question (the tracked guide's items, the required and recommended items of
 * any quest involved, per the OSRS Wiki, and owned items named in the question) and checks each one locally
 * against the player's inventory, worn items and bank. The result goes into the prompt as verified facts, so the
 * AI knows exactly what the player has for the task even when the full bank isn't sent.
 */
@Slf4j
@Singleton
public class RelevantItemChecker {

   private static final int MAX_ITEMS = 20;
   private static final int MAX_QUESTS = 2;
   private static final String[] QUEST_ITEM_KEYS = { "items", "recommended" };
   // [[Target]] / [[Target|text]] links and {{plink|Target}} / {{plinkp|Target}} item templates.
   private static final Pattern LINK = Pattern.compile("\\[\\[([^\\]|#]+)(?:[|#][^\\]]*)?]]");
   private static final Pattern PLINK = Pattern.compile("\\{\\{plinkp?\\|([^}|]+)");

   private final WikiClient wiki;
   private final WikiLookup lookup;
   private final GuideTracker tracker;

   @Inject
   public RelevantItemChecker(final WikiClient wiki, final WikiLookup lookup, final GuideTracker tracker) {
      this.wiki = wiki;
      this.lookup = lookup;
      this.tracker = tracker;
   }

   /**
    * Verified item-check block for the prompt, or "" when there's nothing relevant or the player is logged out.
    * {@code extraQuests} are quests known to be involved (e.g. the one the quest helper is working on). Blocking.
    */
   public String factsFor(final String question, final PlayerSnapshot player, final Collection<String> extraQuests) {
      if (!player.isLoggedIn()) {
         return "";
      }
      final Set<String> items = new LinkedHashSet<>();

      // 1. The guide being tracked right now (current task).
      final GuidePlan tracked = tracker == null ? null : tracker.getActive();
      if (tracked != null) {
         for (final GuideItem item : tracked.getItems()) {
            items.add(item.getName());
         }
      }

      // 2. Items for the quests involved, from the wiki's quest details (only links that are real items).
      final Set<String> quests = new LinkedHashSet<>(QuestMentions.find(question == null ? "" : question, MAX_QUESTS));
      if (extraQuests != null) {
         quests.addAll(extraQuests);
      }
      for (final String quest : quests) {
         if (items.size() >= MAX_ITEMS) {
            break;
         }
         items.addAll(questItems(quest, MAX_ITEMS - items.size()));
      }

      // 3. Items the player owns that the question names.
      final String q = question == null ? "" : question.toLowerCase(Locale.ROOT);
      for (final Map<String, Integer> owned : ownedContainers(player)) {
         for (final String name : owned.keySet()) {
            if (name.length() >= 4 && q.contains(name.toLowerCase(Locale.ROOT))) {
               items.add(name);
            }
         }
      }

      if (items.isEmpty()) {
         return "";
      }
      final StringBuilder sb = new StringBuilder(Prompts.ITEM_CHECK_HEADER);
      int n = 0;
      for (final String item : items) {
         if (n++ >= MAX_ITEMS) {
            break;
         }
         sb.append("- ").append(item).append(": ").append(where(item, player)).append('\n');
      }
      return sb.toString();
   }

   /** Where the player has {@code item}: e.g. "in inventory (x2), in bank (x5)", "not owned", or bank unknown. */
   static String where(final String item, final PlayerSnapshot player) {
      final int inv = count(player.getInventory(), item);
      final int worn = count(player.getEquipment(), item);
      final int bank = count(player.getBank(), item);
      final List<String> parts = new ArrayList<>();
      if (worn > 0) {
         parts.add("worn");
      }
      if (inv > 0) {
         parts.add("in inventory" + (inv > 1 ? " (x" + inv + ")" : ""));
      }
      if (bank > 0) {
         parts.add("in bank" + (bank > 1 ? " (x" + bank + ")" : "")
            + (player.isBankFromEarlierSession() ? " as last seen" : ""));
      }
      if (!parts.isEmpty()) {
         return String.join(", ", parts);
      }
      return player.isBankKnown() ? "not owned" : "not carried (bank unknown)";
   }

   private static int count(final Map<String, Integer> items, final String wanted) {
      final String w = ItemStatusResolver.normalize(wanted);
      int total = 0;
      for (final Map.Entry<String, Integer> e : items.entrySet()) {
         if (ItemStatusResolver.normalize(e.getKey()).equals(w)) {
            total += e.getValue();
         }
      }
      return total;
   }

   private static List<Map<String, Integer>> ownedContainers(final PlayerSnapshot p) {
      final List<Map<String, Integer>> out = new ArrayList<>();
      out.add(p.getEquipment());
      out.add(p.getInventory());
      out.add(p.getBank());
      return out;
   }

   /** Item names from a quest's {{Quest details}} items / recommended fields that the wiki confirms are items. */
   private List<String> questItems(final String quest, final int max) {
      final List<String> out = new ArrayList<>();
      try {
         final String main = wiki.raw(quest);
         if (main == null) {
            return out;
         }
         for (final String key : QUEST_ITEM_KEYS) {
            // The raw field, not Wikitext.templateParams (which turns links into plain text).
            for (final String target : itemLinks(rawParam(main, key))) {
               if (out.size() >= max) {
                  return out;
               }
               final WikiItem item = lookup.item(target);
               if (item != null && !out.contains(item.getName())) {
                  out.add(item.getName());
               }
            }
         }
      } catch (final IOException e) {
         log.debug("RuneGPT: quest items unavailable for {}", quest, e);
      }
      return out;
   }

   /**
    * The raw wikitext of the {@code |key = ...} field in the {{Quest details}} template, up to the next field or
    * the end of the template; null if absent.
    */
   static String rawParam(final String page, final String key) {
      final int start = page.indexOf("{{Quest details");
      if (start < 0) {
         return null;
      }
      final Matcher m = Pattern.compile("\\n\\s*\\|\\s*" + Pattern.quote(key) + "\\s*=", Pattern.CASE_INSENSITIVE)
         .matcher(page);
      if (!m.find(start)) {
         return null;
      }
      final Matcher next = Pattern.compile("\\n\\s*(\\|\\s*\\w+\\s*=|}})").matcher(page);
      final int end = next.find(m.end()) ? next.start() : page.length();
      return page.substring(m.end(), end);
   }

   /** Link targets in a wikitext field: [[Egg]], [[Pot of flour|flour]], {{plink|Bucket of milk}}. */
   static List<String> itemLinks(final String wikitext) {
      final List<String> out = new ArrayList<>();
      if (wikitext == null || wikitext.isEmpty()) {
         return out;
      }
      for (final Pattern p : new Pattern[] { PLINK, LINK }) {
         final Matcher m = p.matcher(wikitext);
         while (m.find()) {
            final String target = m.group(1).trim();
            if (!target.isEmpty() && !target.contains(":") && !out.contains(target)) {
               out.add(target);
            }
         }
      }
      return out;
   }
}
