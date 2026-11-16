package com.kamrant.runegpt.guides;

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
 */
public final class GuideCatalog {

   private static final int MAX_CONTINUE_QUESTS = 3;

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
               out.add(new Guide("quest-continue-" + slug(quest), category, "Continue {quest}",
                  "Help me continue the quest {quest}. I'm not sure exactly where I left off, so list the main "
                     + "checkpoints in order so I can find my place, then the remaining steps, the items to bring, "
                     + "and any tricky fights or puzzles.",
                  fields("quest", quest)).withOptions(new GuideOption("Detail", "Quick steps", "Full walkthrough")));
            }
            out.add(new Guide("quest-continue-custom", category, "Quest helper: {quest}",
               "Help me with the quest {quest}: where I am in it, the next step and the items I need.",
               fields("quest", "Cook's Assistant"))
               .withOptions(new GuideOption("Detail", "Quick steps", "Full walkthrough")));
            out.add(new Guide("quest-next", category, "Which {count} quests next?",
               "Based on my stats and quest progress, recommend the next {count} quests I should do, in order. "
                  + "For each, say why it's worth doing (rewards or unlocks) and confirm I meet the requirements.",
               fields("count", "3")).withOptions(new GuideOption("Focus", "Unlocks", "XP rewards", "Easiest first")));
            out.add(new Guide("quest-requirements", category, "Requirements for {quest}",
               "What do I still need before I can start {quest}? Check my stats and list missing skill levels and "
                  + "prerequisite quests, with the quickest way to get each.",
               fields("quest", "Recipe for Disaster")));
            break;

         case MONSTERS:
            out.add(new Guide("monster-kill", category, "Kill {count} {monster}",
               "I want to kill {count} {monster}. Tell me the best location, the gear and inventory to bring for "
                  + "my stats, roughly how long it will take, and which drops to look out for.",
               fields("count", "200", "monster", killTarget(p.getCombatLevel())))
               .withOptions(GuideOption.ATTACK_STYLE, GuideOption.BUDGET));
            out.add(new Guide("monster-slayer-task", category, "Slayer task: {count} {monster}",
               "My Slayer task is {count} {monster}. Where should I kill them, what gear and inventory should I "
                  + "bring, and is it worth doing, skipping or blocking at my level?",
               fields("count", "150", "monster", "fire giants"))
               .withOptions(GuideOption.ATTACK_STYLE, new GuideOption("Goal", "Fast", "AFK", "Profit")));
            out.add(new Guide("monster-train", category, "Best combat training spot",
               "Where should I train combat right now for good XP per hour? Look at my combat stats and suggest "
                  + "which stat to train first.",
               fields()).withOptions(GuideOption.ATTACK_STYLE, new GuideOption("Goal", "Fast XP", "AFK", "Profit")));
            break;

         case BOSSES:
            out.add(new Guide("boss-ready", category, "Am I ready for {boss}?",
               "Am I ready to fight {boss}? Check my stats against what's recommended, suggest gear and inventory, "
                  + "explain the key mechanics, and estimate kills per hour.",
               fields("boss", bossTarget(p.getCombatLevel())))
               .withOptions(GuideOption.ATTACK_STYLE, GuideOption.BUDGET));
            out.add(new Guide("boss-suggest", category, "Bosses for my level",
               "Which bosses suit my stats best right now? Give me {count} options, with what each is good for "
                  + "(profit, unlocks or pets) and what I need before trying.",
               fields("count", "3")).withOptions(new GuideOption("Goal", "Profit", "Unlocks", "Pets")));
            break;

         case SKILLING:
            final Skill lowest = p.lowestSkill();
            final int lowLevel = lowest == null ? 1 : p.level(lowest);
            out.add(new Guide("skill-train", category, "Train {skill} to {target}",
               "What's the fastest way to train {skill} from my current level to {target}? Compare a fast or "
                  + "expensive method with a cheap or AFK one, with XP per hour and costs.",
               fields("skill", lowest == null ? "Agility" : lowest.getName(),
                  "target", String.valueOf(nextMilestone(lowLevel))))
               .withOptions(GuideOption.PACE));
            final Skill top = p.highestUnmaxedSkill();
            out.add(new Guide("skill-99", category, "Plan 99 {skill}",
               "Plan my route to 99 {skill} from my current level: methods per level range, total time and "
                  + "cost, and any quests or unlocks that speed it up.",
               fields("skill", top == null ? "Woodcutting" : top.getName())).withOptions(GuideOption.PACE));
            out.add(new Guide("skill-quest-xp", category, "Quests with {skill} XP",
               "Which quests give XP rewards in {skill} that I could use to skip early levels?",
               fields("skill", lowest == null ? "Agility" : lowest.getName())));
            break;

         case MONEY:
            out.add(new Guide("money-best", category, "Top {count} money makers",
               "Give me the {count} best money making methods for my current stats, with estimated GP per hour, "
                  + "requirements and how active each one is.",
               fields("count", "5")).withOptions(new GuideOption("Style", "Active", "AFK"),
               new GuideOption("Type", "Combat", "Skilling", "Either")));
            out.add(new Guide("money-skill", category, "Make money with {skill}",
               "What are the best ways to make money using {skill} at my level? Include GP per hour and startup cost.",
               fields("skill", p.highestUnmaxedSkill() == null ? "Fishing" : p.highestUnmaxedSkill().getName()))
               .withOptions(new GuideOption("Style", "Active", "AFK")));
            out.add(new Guide("money-afk", category, "AFK money making",
               "What are the best low-attention (AFK) money making methods for my stats? Include GP per hour.",
               fields()).withOptions(new GuideOption("Type", "Combat", "Skilling", "Either")));
            break;

         case MINIGAMES:
            out.add(new Guide("minigame-worth", category, "Minigames worth doing",
               "Which minigames are worth doing for my account right now, and what rewards should I aim for in each?",
               fields()).withOptions(new GuideOption("Goal", "XP", "Rewards", "Fun")));
            out.add(new Guide("minigame-guide", category, "Guide to {minigame}",
               "Give me a guide to {minigame}: requirements, how it works, the best strategy, and which rewards to "
                  + "buy first.",
               fields("minigame", p.level(Skill.RUNECRAFT) >= 27 ? "Guardians of the Rift" : "Pest Control")));
            break;
      }
      return out;
   }

   /** Prompt used for the per-category "Other" box. */
   public static String otherPrompt(final GuideCategory category, final String interest) {
      return "About " + category.getFullName().toLowerCase() + ": " + interest;
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
