package com.kamrant.runegpt.service;

import com.kamrant.runegpt.model.AccountMode;
import com.kamrant.runegpt.model.GuidePlan;
import com.kamrant.runegpt.model.TaskSuggestion;
import com.kamrant.runegpt.ui.UiTheme;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Makes sure Hardcore accounts get a risk warning on answers about content that can kill them. The prompt asks the
 * AI for a "Hardcore risk:" line; weaker models sometimes skip it, so a plain generic warning is added in code when
 * the question or reply is clearly about combat and the AI didn't write one.
 */
public final class HardcoreWarning {

   private static final String MARKER = "hardcore risk";
   // Words in the question or guide title that mean combat or the Wilderness.
   private static final Pattern DANGER = Pattern.compile("\\b(boss|bosses|bossing|raid|raids|wilderness|wildy|kill|"
      + "killing|kills|fight|fighting|slay|slaying|slayer|pk|pking|pvm|revenants?|inferno|fight caves|jad|zuk|"
      + "colosseum|gauntlet|barrows|zulrah|vorkath)\\b");

   private HardcoreWarning() {
   }

   /** {@code answer}, with a Hardcore risk line in front when one is needed and missing. */
   public static String apply(final AccountMode mode, final String question, final String answer,
      final GuidePlan guide, final List<TaskSuggestion> suggestions) {
      if (mode == null || !mode.isHardcore() || answer == null
         || answer.toLowerCase(Locale.ROOT).contains(MARKER)) {
         return answer;
      }
      if (!isDangerous(question, guide, suggestions)) {
         return answer;
      }
      return UiTheme.NOTE_HARDCORE_RISK + "\n\n" + answer;
   }

   /** The task helper's tip, with a short Hardcore warning in front for boss and monster tasks. */
   public static String taskTip(final AccountMode mode, final String task, final String category, final String tip) {
      final String t = tip == null ? "" : tip;
      if (mode == null || !mode.isHardcore() || t.toLowerCase(Locale.ROOT).contains(MARKER)) {
         return t;
      }
      final String c = category == null ? "" : category.toLowerCase(Locale.ROOT);
      final boolean dangerous = c.equals("boss") || c.equals("monster")
         || DANGER.matcher(task == null ? "" : task.toLowerCase(Locale.ROOT)).find();
      if (!dangerous) {
         return t;
      }
      return UiTheme.TIP_HARDCORE_RISK + (t.isEmpty() ? "" : " " + t);
   }

   static boolean isDangerous(final String question, final GuidePlan guide, final List<TaskSuggestion> suggestions) {
      if (suggestions != null) {
         for (final TaskSuggestion s : suggestions) {
            final String c = s.getCategory() == null ? "" : s.getCategory().toLowerCase(Locale.ROOT);
            if (c.equals("boss") || c.equals("monster")) {
               return true;
            }
         }
      }
      final String text = ((question == null ? "" : question) + " " + (guide == null ? "" : guide.getTitle()))
         .toLowerCase(Locale.ROOT);
      return DANGER.matcher(text).find();
   }
}
