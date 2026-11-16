package com.kamrant.runegpt.quest;

import com.kamrant.runegpt.service.TextUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.runelite.api.Quest;

/**
 * Finds real quest names (from RuneLite's official quest list, miniquests included) mentioned in a
 * question, so the answer can be grounded in that quest's OSRS Wiki data. Longest names are matched
 * first so "Dragon Slayer II" isn't also read as "Dragon Slayer I"; "2" is accepted for "II".
 */
public final class QuestMentions {

   private static final int MIN_NAME_CHARS = 5;

   private QuestMentions() {
   }

   /** Quest names mentioned in {@code text}, at most {@code max}, in order of length (most specific first). */
   public static List<String> find(final String text, final int max) {
      String haystack = " " + normalize(text) + " ";
      final List<Quest> quests = new ArrayList<>(Arrays.asList(Quest.values()));
      quests.sort((a, b) -> Integer.compare(b.getName().length(), a.getName().length()));

      final List<String> found = new ArrayList<>();
      for (final Quest quest : quests) {
         if (found.size() >= max) {
            break;
         }
         final String name = normalize(quest.getName());
         if (name.length() < MIN_NAME_CHARS) {
            continue;
         }
         for (final String variant : variants(name)) {
            final String needle = " " + variant + " ";
            if (haystack.contains(needle)) {
               found.add(quest.getName());
               // Blank it out so a shorter quest name inside it doesn't match too.
               haystack = haystack.replace(needle, " ");
               break;
            }
         }
      }
      return found;
   }

   /** The name as written, plus with a trailing roman numeral as a digit ("monkey madness ii" -> "... 2"). */
   private static List<String> variants(final String name) {
      final List<String> out = new ArrayList<>();
      out.add(name);
      if (name.endsWith(" iii")) {
         out.add(name.substring(0, name.length() - 4) + " 3");
      } else if (name.endsWith(" ii")) {
         out.add(name.substring(0, name.length() - 3) + " 2");
      } else if (name.endsWith(" i")) {
         out.add(name.substring(0, name.length() - 2) + " 1");
      }
      return out;
   }

   private static String normalize(final String s) {
      return TextUtils.normalizeWords(s);
   }
}
