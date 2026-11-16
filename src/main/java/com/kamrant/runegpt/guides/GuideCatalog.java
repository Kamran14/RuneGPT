package com.kamrant.runegpt.guides;

import com.kamrant.runegpt.prompt.Prompts;
import com.kamrant.runegpt.service.PlayerSnapshot;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.Skill;

/**
 * Default recommended guides per category. Defaults adapt to the player's snapshot
 * (combat level, lowest skill, quests in progress); the player can edit any of them.
 * Titles and prompts come from {@link Prompts}.
 */
public final class GuideCatalog {

   /** Guides whose id starts with this open the wiki-grounded quest step assistant. */
   public static final String QUEST_STEP_PREFIX = "quest-continue-";
   /** Guides whose id starts with this run the stat-based gear optimizer from owned items. */
   public static final String GEAR_OPTIMIZER_PREFIX = "gear-optimize-";
   public static final String QUEST_REQUIREMENTS_ID = "quest-requirements";

   private static final int MAX_CONTINUE_QUESTS = 3;
   private static final GuideOption QUEST_DETAIL = new GuideOption("Detail", "Quick steps", "Full walkthrough");

   private GuideCatalog() {
   }

   public static List<Guide> defaults(final GuideCategory category, final PlayerSnapshot p) {
      final List<Guide> out = new ArrayList<>();
      switch (category) {
         case QUESTS:
            int shown = 0;
            for (final String quest : p.getQuestsInProgress()) {
               if (shown++ >= MAX_CONTINUE_QUESTS) {
                  break;
               }
               out.add(new Guide(QUEST_STEP_PREFIX + slug(quest), category, Prompts.QUEST_CONTINUE_TITLE,
                  Prompts.QUEST_CONTINUE, fields("quest", quest)).withOptions(QUEST_DETAIL));
            }
            out.add(new Guide(QUEST_STEP_PREFIX + "custom", category, Prompts.QUEST_HELPER_TITLE,
               Prompts.QUEST_HELPER, fields("quest", "Cook's Assistant")).withOptions(QUEST_DETAIL));
            out.add(new Guide("quest-next", category, Prompts.QUEST_NEXT_TITLE, Prompts.QUEST_NEXT,
               fields("count", "3")).withOptions(new GuideOption("Focus", "Unlocks", "XP rewards", "Easiest first")));
            out.add(new Guide(QUEST_REQUIREMENTS_ID, category, Prompts.QUEST_REQUIREMENTS_TITLE,
               Prompts.QUEST_REQUIREMENTS, fields("quest", "Recipe for Disaster")));
            break;

         case MONSTERS:
            out.add(new Guide(GEAR_OPTIMIZER_PREFIX + "monster", category, Prompts.GEAR_OPTIMIZE_TITLE,
               Prompts.GEAR_OPTIMIZE, fields("monster", killTarget(p.getCombatLevel())))
               .withOptions(GuideOption.ATTACK_STYLE, GuideOption.SLAYER_TASK));
            out.add(new Guide("monster-kill", category, Prompts.MONSTER_KILL_TITLE, Prompts.MONSTER_KILL,
               fields("count", "200", "monster", killTarget(p.getCombatLevel())))
               .withOptions(GuideOption.ATTACK_STYLE, GuideOption.BUDGET));
            out.add(new Guide("monster-slayer-task", category, Prompts.MONSTER_SLAYER_TITLE, Prompts.MONSTER_SLAYER,
               fields("count", "150", "monster", "fire giants"))
               .withOptions(GuideOption.ATTACK_STYLE, new GuideOption("Goal", "Fast", "AFK", "Profit")));
            out.add(new Guide("monster-train", category, Prompts.MONSTER_TRAIN_TITLE, Prompts.MONSTER_TRAIN,
               fields()).withOptions(GuideOption.ATTACK_STYLE, new GuideOption("Goal", "Fast XP", "AFK", "Profit")));
            break;

         case BOSSES:
            out.add(new Guide(GEAR_OPTIMIZER_PREFIX + "boss", category, Prompts.BOSS_GEAR_TITLE,
               Prompts.BOSS_GEAR, fields("boss", bossTarget(p.getCombatLevel())))
               .withOptions(GuideOption.ATTACK_STYLE, GuideOption.SLAYER_TASK));
            out.add(new Guide("boss-ready", category, Prompts.BOSS_READY_TITLE, Prompts.BOSS_READY,
               fields("boss", bossTarget(p.getCombatLevel())))
               .withOptions(GuideOption.ATTACK_STYLE, GuideOption.BUDGET));
            out.add(new Guide("boss-suggest", category, Prompts.BOSS_SUGGEST_TITLE, Prompts.BOSS_SUGGEST,
               fields("count", "3")).withOptions(new GuideOption("Goal", "Profit", "Unlocks", "Pets")));
            break;

         case SKILLING:
            final Skill lowest = p.lowestSkill();
            final int lowLevel = lowest == null ? 1 : p.level(lowest);
            out.add(new Guide("skill-train", category, Prompts.SKILL_TRAIN_TITLE, Prompts.SKILL_TRAIN,
               fields("skill", lowest == null ? "Agility" : lowest.getName(),
                  "target", String.valueOf(nextMilestone(lowLevel))))
               .withOptions(GuideOption.PACE));
            final Skill top = p.highestUnmaxedSkill();
            out.add(new Guide("skill-99", category, Prompts.SKILL_99_TITLE, Prompts.SKILL_99,
               fields("skill", top == null ? "Woodcutting" : top.getName())).withOptions(GuideOption.PACE));
            out.add(new Guide("skill-quest-xp", category, Prompts.SKILL_QUEST_XP_TITLE, Prompts.SKILL_QUEST_XP,
               fields("skill", lowest == null ? "Agility" : lowest.getName())));
            break;

         case MONEY:
            out.add(new Guide("money-best", category, Prompts.MONEY_BEST_TITLE, Prompts.MONEY_BEST,
               fields("count", "5")).withOptions(new GuideOption("Style", "Active", "AFK"),
               new GuideOption("Type", "Combat", "Skilling", "Either")));
            out.add(new Guide("money-skill", category, Prompts.MONEY_SKILL_TITLE, Prompts.MONEY_SKILL,
               fields("skill", top(p, "Fishing"))).withOptions(new GuideOption("Style", "Active", "AFK")));
            out.add(new Guide("money-afk", category, Prompts.MONEY_AFK_TITLE, Prompts.MONEY_AFK,
               fields()).withOptions(new GuideOption("Type", "Combat", "Skilling", "Either")));
            break;

         case MINIGAMES:
            out.add(new Guide("minigame-worth", category, Prompts.MINIGAME_WORTH_TITLE, Prompts.MINIGAME_WORTH,
               fields()).withOptions(new GuideOption("Goal", "XP", "Rewards", "Fun")));
            out.add(new Guide("minigame-guide", category, Prompts.MINIGAME_GUIDE_TITLE, Prompts.MINIGAME_GUIDE,
               fields("minigame", p.level(Skill.RUNECRAFT) >= 27 ? "Guardians of the Rift" : "Pest Control")));
            break;
      }
      return out;
   }

   /** Prompt used for the per-category "Something else?" box. */
   public static String otherPrompt(final GuideCategory category, final String interest) {
      return String.format(Prompts.OTHER_INTEREST, category.getFullName().toLowerCase(), interest);
   }

   private static String top(final PlayerSnapshot p, final String fallback) {
      final Skill s = p.highestUnmaxedSkill();
      return s == null ? fallback : s.getName();
   }

   private static String killTarget(final int combat) {
      if (combat < 40) {
         return "hill giants";
      }
      if (combat < 70) {
         return "blue dragons";
      }
      return "black dragons";
   }

   private static String bossTarget(final int combat) {
      if (combat < 60) {
         return "Obor";
      }
      if (combat < 85) {
         return "Barrows";
      }
      if (combat < 100) {
         return "Zulrah";
      }
      return "Vorkath";
   }

   private static int nextMilestone(final int level) {
      if (level >= 90) {
         return 99;
      }
      return Math.min(99, (level / 10 + 1) * 10);
   }

   private static String slug(final String s) {
      return s.toLowerCase().replaceAll("[^a-z0-9]+", "-");
   }

   private static Map<String, String> fields(final String... kv) {
      if (kv.length == 0) {
         return Collections.emptyMap();
      }
      final Map<String, String> m = new LinkedHashMap<>();
      for (int i = 0; i + 1 < kv.length; i += 2) {
         m.put(kv[i], kv[i + 1]);
      }
      return m;
   }
}
