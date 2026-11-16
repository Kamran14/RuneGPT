package com.kamrant.runegpt.quest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import com.kamrant.runegpt.model.QuestMatch;
import com.kamrant.runegpt.service.TextUtils;
import net.runelite.api.Quest;

/**
 * Maps what the player typed to a real quest name from RuneLite's quest list, so we never ask the
 * AI about a quest that doesn't exist. Handles case, punctuation and "2" vs "II".
 */
public final class QuestResolver {

   private static final double SUGGEST_THRESHOLD = 0.34;

   private QuestResolver() {
   }

   public static QuestMatch resolve(final String input) {
      final String norm = normalize(input);
      for (final Quest quest : Quest.values()) {
         if (normalize(quest.getName()).equals(norm)) {
            return new QuestMatch(quest.getName(), Collections.emptyList());
         }
      }

      final List<Object[]> scored = new ArrayList<>();
      for (final Quest quest : Quest.values()) {
         final double score = similarity(norm, normalize(quest.getName()));
         if (score >= SUGGEST_THRESHOLD) {
            scored.add(new Object[] { quest.getName(), score });
         }
      }
      scored.sort((a, b) -> Double.compare((double) b[1], (double) a[1]));
      final List<String> suggestions = new ArrayList<>();
      for (int i = 0; i < scored.size() && i < 3; i++) {
         suggestions.add((String) scored.get(i)[0]);
      }
      return new QuestMatch(null, suggestions);
   }

   static String normalize(final String s) {
      final String n = TextUtils.normalizeWords(s).replaceAll("\\bquest\\b", " ").replaceAll("\\s+", " ").trim();
      // Trailing number to roman numeral: "monkey madness 2" -> "monkey madness ii".
      return n.replaceAll(" 1$", " i").replaceAll(" 2$", " ii").replaceAll(" 3$", " iii");
   }

   /** Word overlap plus a bonus when one name contains the other. */
   private static double similarity(final String a, final String b) {
      final Set<String> wa = new HashSet<>(Arrays.asList(a.split(" ")));
      final Set<String> wb = new HashSet<>(Arrays.asList(b.split(" ")));
      final Set<String> inter = new HashSet<>(wa);
      inter.retainAll(wb);
      final Set<String> union = new HashSet<>(wa);
      union.addAll(wb);
      double score = union.isEmpty() ? 0 : (double) inter.size() / union.size();
      // Prefix matches on words ("barb" ~ "barbarian").
      for (final String x : wa) {
         for (final String y : wb) {
            if (!x.equals(y) && x.length() >= 4 && (y.startsWith(x) || x.startsWith(y))) {
               score += 0.15;
            }
         }
      }
      if (!a.isEmpty() && (b.contains(a) || a.contains(b))) {
         score += 0.3;
      }
      return score;
   }
}
