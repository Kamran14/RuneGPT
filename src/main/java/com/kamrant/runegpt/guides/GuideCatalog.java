package com.kamrant.runegpt.guides;

import com.kamrant.runegpt.prompt.Prompts;
import com.kamrant.runegpt.quest.QuestResolver;
import com.kamrant.runegpt.service.PlayerSnapshot;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;

/**
 * Default recommended guides per category. Defaults adapt dynamically to the player's snapshot
 * (combat level, Slayer and skill levels, quests in progress, and completed quests) so outgrown
 * or already-completed prompts are never shown. Titles and prompts come from {@link Prompts}.
 */
public final class GuideCatalog {

   /** Guides whose id starts with this open the wiki-grounded quest step assistant. */
   public static final String QUEST_STEP_PREFIX = "quest-continue-";
   /** Guides whose id starts with this run the stat-based gear optimizer from owned items. */
   public static final String GEAR_OPTIMIZER_PREFIX = "gear-optimize-";
   public static final String QUEST_REQUIREMENTS_ID = "quest-requirements";

   private static final int MAX_CONTINUE_QUESTS = 3;
   private static final GuideOption QUEST_DETAIL = new GuideOption("Detail", "Quick steps", "Full walkthrough");

   private static final Quest[] EARLY_QUESTS = {
      Quest.COOKS_ASSISTANT, Quest.WATERFALL_QUEST, Quest.ROMEO__JULIET, Quest.THE_RESTLESS_GHOST,
      Quest.RUNE_MYSTERIES, Quest.VAMPYRE_SLAYER, Quest.TREE_GNOME_VILLAGE, Quest.PRIEST_IN_PERIL,
      Quest.THE_KNIGHTS_SWORD, Quest.DORICS_QUEST, Quest.DRUIDIC_RITUAL, Quest.LOST_CITY,
      Quest.FAIRYTALE_I__GROWING_PAINS, Quest.THE_GRAND_TREE, Quest.BONE_VOYAGE
   };

   private static final Quest[] MID_QUESTS = {
      Quest.DRAGON_SLAYER_I, Quest.MONKEY_MADNESS_I, Quest.ANIMAL_MAGNETISM, Quest.THE_FREMENNIK_TRIALS,
      Quest.THE_FREMENNIK_ISLES, Quest.UNDERGROUND_PASS, Quest.GHOSTS_AHOY, Quest.DESERT_TREASURE_I,
      Quest.KINGS_RANSOM, Quest.LUNAR_DIPLOMACY, Quest.REGICIDE, Quest.RECIPE_FOR_DISASTER,
      Quest.PERILOUS_MOONS, Quest.BENEATH_CURSED_SANDS
   };

   private static final Quest[] LATE_QUESTS = {
      Quest.RECIPE_FOR_DISASTER, Quest.SINS_OF_THE_FATHER, Quest.MONKEY_MADNESS_II,
      Quest.THE_FREMENNIK_EXILES, Quest.A_KINGDOM_DIVIDED, Quest.BENEATH_CURSED_SANDS,
      Quest.DRAGON_SLAYER_II, Quest.SONG_OF_THE_ELVES, Quest.SECRETS_OF_THE_NORTH,
      Quest.WHILE_GUTHIX_SLEEPS, Quest.DESERT_TREASURE_II__THE_FALLEN_EMPIRE
   };

   private GuideCatalog() {
   }

   public static List<Guide> defaults(final GuideCategory category, final PlayerSnapshot p) {
      final List<Guide> out = new ArrayList<>();
      switch (category) {
         case QUESTS:
            if (p.isLoggedIn() && !p.hasIncompleteQuests()) {
               break;
            }
            final Set<String> shownQuests = new HashSet<>();
            int shown = 0;
            for (final String quest : p.getQuestsInProgress()) {
               if (shown++ >= MAX_CONTINUE_QUESTS) {
                  break;
               }
               shownQuests.add(quest);
               out.add(new Guide(QUEST_STEP_PREFIX + slug(quest), category, Prompts.QUEST_CONTINUE_TITLE,
                  Prompts.QUEST_CONTINUE, fields("quest", quest)).withOptions(QUEST_DETAIL));
            }
            final String helperQuest = recommendedQuest(p, shownQuests);
            if (helperQuest != null) {
               shownQuests.add(helperQuest);
               out.add(new Guide(QUEST_STEP_PREFIX + "custom", category, Prompts.QUEST_HELPER_TITLE,
                  Prompts.QUEST_HELPER, fields("quest", helperQuest)).withOptions(QUEST_DETAIL));
            }
            final int remaining = p.incompleteQuestCount();
            if (remaining > 0) {
               out.add(new Guide("quest-next", category, Prompts.QUEST_NEXT_TITLE, Prompts.QUEST_NEXT,
                  fields("count", String.valueOf(Math.min(3, remaining))))
                  .withOptions(new GuideOption("Focus", "Unlocks", "XP rewards", "Easiest first")));
            }
            final String reqQuest = milestoneQuest(p, shownQuests, helperQuest);
            if (reqQuest != null) {
               out.add(new Guide(QUEST_REQUIREMENTS_ID, category, Prompts.QUEST_REQUIREMENTS_TITLE,
                  Prompts.QUEST_REQUIREMENTS, fields("quest", reqQuest)));
            }
            break;

         case MONSTERS:
            final String monster = killTarget(p);
            out.add(new Guide(GEAR_OPTIMIZER_PREFIX + "monster", category, Prompts.GEAR_OPTIMIZE_TITLE,
               Prompts.GEAR_OPTIMIZE, fields("monster", monster))
               .withOptions(GuideOption.ATTACK_STYLE, GuideOption.SLAYER_TASK));
            out.add(new Guide("monster-kill", category, Prompts.MONSTER_KILL_TITLE, Prompts.MONSTER_KILL,
               fields("count", killCount(p), "monster", monster))
               .withOptions(GuideOption.ATTACK_STYLE, GuideOption.BUDGET));
            out.add(new Guide("monster-slayer-task", category, Prompts.MONSTER_SLAYER_TITLE, Prompts.MONSTER_SLAYER,
               fields("count", "150", "monster", slayerTarget(p)))
               .withOptions(GuideOption.ATTACK_STYLE, new GuideOption("Goal", "Fast", "AFK", "Profit")));
            if (!p.isAllCombatMaxed()) {
               out.add(new Guide("monster-train", category, Prompts.MONSTER_TRAIN_TITLE, Prompts.MONSTER_TRAIN,
                  fields()).withOptions(GuideOption.ATTACK_STYLE, new GuideOption("Goal", "Fast XP", "AFK", "Profit")));
            }
            break;

         case BOSSES:
            final String boss = bossTarget(p);
            out.add(new Guide(GEAR_OPTIMIZER_PREFIX + "boss", category, Prompts.BOSS_GEAR_TITLE,
               Prompts.BOSS_GEAR, fields("boss", boss))
               .withOptions(GuideOption.ATTACK_STYLE, GuideOption.SLAYER_TASK));
            out.add(new Guide("boss-ready", category, Prompts.BOSS_READY_TITLE, Prompts.BOSS_READY,
               fields("boss", boss))
               .withOptions(GuideOption.ATTACK_STYLE, GuideOption.BUDGET));
            out.add(new Guide("boss-suggest", category, Prompts.BOSS_SUGGEST_TITLE, Prompts.BOSS_SUGGEST,
               fields("count", "3")).withOptions(new GuideOption("Goal", "Profit", "Unlocks", "Pets")));
            break;

         case SKILLING:
            if (p.isLoggedIn() && p.highestUnmaxedSkill() == null) {
               break;
            }
            final Skill lowest = p.lowestSkill();
            final int lowLevel = lowest == null ? 1 : p.level(lowest);
            final String lowestName = lowest == null ? "Agility" : lowest.getName();
            if (lowLevel < 99) {
               out.add(new Guide("skill-train", category, Prompts.SKILL_TRAIN_TITLE, Prompts.SKILL_TRAIN,
                  fields("skill", lowestName, "target", String.valueOf(nextMilestone(lowLevel))))
                  .withOptions(GuideOption.PACE));
            }
            final Skill top = p.highestUnmaxedSkillExcept(lowest);
            if (!p.isLoggedIn() || top != null) {
               out.add(new Guide("skill-99", category, Prompts.SKILL_99_TITLE, Prompts.SKILL_99,
                  fields("skill", top == null ? "Woodcutting" : top.getName())).withOptions(GuideOption.PACE));
            }
            if (p.hasIncompleteQuests() && lowLevel < 70) {
               out.add(new Guide("skill-quest-xp", category, Prompts.SKILL_QUEST_XP_TITLE, Prompts.SKILL_QUEST_XP,
                  fields("skill", lowestName)));
            }
            break;

         case MONEY:
            out.add(new Guide("money-best", category, Prompts.MONEY_BEST_TITLE, Prompts.MONEY_BEST,
               fields("count", "5")).withOptions(new GuideOption("Style", "Active", "AFK"),
               new GuideOption("Type", "Combat", "Skilling", "Either")));
            final Skill moneySkill = p.bestMoneySkill();
            out.add(new Guide("money-skill", category, Prompts.MONEY_SKILL_TITLE, Prompts.MONEY_SKILL,
               fields("skill", moneySkill == null ? "Fishing" : moneySkill.getName()))
               .withOptions(new GuideOption("Style", "Active", "AFK")));
            out.add(new Guide("money-afk", category, Prompts.MONEY_AFK_TITLE, Prompts.MONEY_AFK,
               fields()).withOptions(new GuideOption("Type", "Combat", "Skilling", "Either")));
            break;

         case MINIGAMES:
            out.add(new Guide("minigame-worth", category, Prompts.MINIGAME_WORTH_TITLE, Prompts.MINIGAME_WORTH,
               fields()).withOptions(new GuideOption("Goal", "XP", "Rewards", "Fun")));
            out.add(new Guide("minigame-guide", category, Prompts.MINIGAME_GUIDE_TITLE, Prompts.MINIGAME_GUIDE,
               fields("minigame", minigameTarget(p))));
            break;
      }
      return out;
   }

   /**
    * Applies a saved guide customization only if its target is still valid for the player's current
    * progress (e.g. the saved quest isn't finished yet, and the saved skill target hasn't been passed).
    */
   public static void applySavedIfValid(final Guide guide, final String savedTitle, final String savedPrompt,
      final Map<String, String> savedFields, final PlayerSnapshot p) {
      if (!p.isLoggedIn()) {
         guide.apply(savedTitle, savedPrompt, savedFields);
         return;
      }
      final Map<String, String> adjusted = new LinkedHashMap<>(savedFields);
      if (adjusted.containsKey("quest")) {
         final String resolved = QuestResolver.resolve(adjusted.get("quest")).getQuestName();
         if (resolved != null && p.isQuestCompleted(resolved)) {
            return;
         }
      }
      if (adjusted.containsKey("skill")) {
         final Skill s = PlayerSnapshot.skillByName(adjusted.get("skill"));
         if (s != null) {
            final int currentLevel = p.level(s);
            if (guide.getId().equals("skill-99") && currentLevel >= 99) {
               return;
            }
            if (guide.getId().equals("skill-quest-xp") && (!p.hasIncompleteQuests() || currentLevel >= 70)) {
               return;
            }
            if (adjusted.containsKey("target")) {
               try {
                  final int target = Integer.parseInt(adjusted.get("target").trim());
                  if (currentLevel >= 99) {
                     return;
                  }
                  if (currentLevel >= target) {
                     adjusted.put("target", String.valueOf(nextMilestone(currentLevel)));
                  }
               } catch (final NumberFormatException ignored) {
               }
            }
         }
      }
      if (adjusted.containsKey("monster")) {
         final String m = adjusted.get("monster").trim().toLowerCase(Locale.ROOT);
         if ((m.equals("cows") && p.getCombatLevel() >= 35)
            || (m.equals("hill giants") && p.getCombatLevel() >= 55)
            || (m.equals("moss giants") && p.getCombatLevel() >= 75)
            || (m.equals("fire giants") && p.getCombatLevel() >= 85 && p.level(Skill.SLAYER) >= 65)) {
            return;
         }
      }
      guide.apply(savedTitle, savedPrompt, adjusted);
   }

   /** Prompt used for the per-category "Something else?" box. */
   public static String otherPrompt(final GuideCategory category, final String interest) {
      return String.format(Prompts.OTHER_INTEREST, category.getFullName().toLowerCase(), interest);
   }

   /**
    * Picks an uncompleted quest suited to the player's current combat/total level that isn't already
    * shown in the active "Continue {quest}" cards.
    */
   private static String recommendedQuest(final PlayerSnapshot p, final Set<String> exclude) {
      if (!p.isLoggedIn()) {
         return Quest.COOKS_ASSISTANT.getName();
      }
      final int combat = p.getCombatLevel();
      final Quest[][] order = combat < 45
         ? new Quest[][] { EARLY_QUESTS, MID_QUESTS, LATE_QUESTS }
         : combat < 85
            ? new Quest[][] { MID_QUESTS, EARLY_QUESTS, LATE_QUESTS }
            : new Quest[][] { LATE_QUESTS, MID_QUESTS, EARLY_QUESTS };

      for (final Quest[] tier : order) {
         for (final Quest q : tier) {
            final String name = q.getName();
            if (!p.isQuestCompleted(name) && !exclude.contains(name)) {
               return name;
            }
         }
      }
      for (final String q : p.getQuestsInProgress()) {
         if (!exclude.contains(q)) {
            return q;
         }
      }
      for (final String q : p.getQuestsNotStarted()) {
         if (!exclude.contains(q)) {
            return q;
         }
      }
      return null;
   }

   /**
    * Picks a milestone quest for "Requirements for {quest}" that the player has not completed yet,
    * preferring NOT_STARTED milestone quests slightly ahead of or at their progression tier.
    */
   private static String milestoneQuest(final PlayerSnapshot p, final Set<String> exclude, final String fallback) {
      if (!p.isLoggedIn()) {
         return Quest.RECIPE_FOR_DISASTER.getName();
      }
      final int combat = p.getCombatLevel();
      final Quest[][] order = combat < 45
         ? new Quest[][] { MID_QUESTS, EARLY_QUESTS, LATE_QUESTS }
         : new Quest[][] { LATE_QUESTS, MID_QUESTS, EARLY_QUESTS };

      // Prefer a NOT_STARTED milestone quest first so "Requirements for {quest}" is maximally useful.
      for (final Quest[] tier : order) {
         for (final Quest q : tier) {
            final String name = q.getName();
            if (p.questState(name) == QuestState.NOT_STARTED && !exclude.contains(name)) {
               return name;
            }
         }
      }
      for (final String q : p.getQuestsNotStarted()) {
         if (!exclude.contains(q)) {
            return q;
         }
      }
      if (!p.getQuestsNotStarted().isEmpty()) {
         return p.getQuestsNotStarted().get(0);
      }
      return fallback;
   }

   /** Scales monster targets dynamically with the player's combat level, Slayer level and quest unlocks. */
   public static String killTarget(final PlayerSnapshot p) {
      if (!p.isLoggedIn()) {
         return "moss giants";
      }
      final int combat = p.getCombatLevel();
      final int slayer = p.level(Skill.SLAYER);
      if (combat < 20) {
         return "cows";
      }
      if (combat < 40) {
         return "hill giants";
      }
      if (combat < 60) {
         return "moss giants";
      }
      if (combat < 75) {
         return "blue dragons";
      }
      if (combat < 90) {
         if (p.isQuestCompleted(Quest.SINS_OF_THE_FATHER)) {
            return "vyrewatch sentinels";
         }
         if (slayer >= 65) {
            return "dust devils";
         }
         return "black dragons";
      }
      if (combat < 105) {
         if (p.isQuestCompleted(Quest.MONKEY_MADNESS_II) && slayer >= 69) {
            return "demonic gorillas";
         }
         if (slayer >= 77) {
            return "brutal black dragons";
         }
         if (p.isQuestCompleted(Quest.SINS_OF_THE_FATHER)) {
            return "vyrewatch sentinels";
         }
         if (slayer >= 72) {
            return "skeletal wyverns";
         }
         return "black dragons";
      }
      if (p.isQuestCompleted(Quest.WHILE_GUTHIX_SLEEPS)) {
         return "tormented demons";
      }
      if (p.isQuestCompleted(Quest.DRAGON_SLAYER_II)) {
         return "rune dragons";
      }
      if (p.isQuestCompleted(Quest.MONKEY_MADNESS_II) && slayer >= 69) {
         return "demonic gorillas";
      }
      if (slayer >= 77) {
         return "brutal black dragons";
      }
      return "black dragons";
   }

   private static String killCount(final PlayerSnapshot p) {
      if (!p.isLoggedIn() || p.getCombatLevel() >= 40) {
         return "150";
      }
      return "50";
   }

   /** Scales Slayer task examples to the player's actual Slayer and combat levels. */
   public static String slayerTarget(final PlayerSnapshot p) {
      if (!p.isLoggedIn()) {
         return "fire giants";
      }
      final int slayer = p.level(Skill.SLAYER);
      final int combat = p.getCombatLevel();
      if (slayer < 30 || combat < 40) {
         return "kalphites";
      }
      if (slayer < 50 || combat < 60) {
         return "fire giants";
      }
      if (slayer < 65) {
         return "bloodveld";
      }
      if (slayer < 70) {
         return "dust devils";
      }
      if (slayer < 75) {
         return "kurasks";
      }
      if (slayer < 80) {
         return "gargoyles";
      }
      if (slayer < 85) {
         return "nechryael";
      }
      if (slayer < 92) {
         return "abyssal demons";
      }
      if (slayer < 95) {
         return "araxytes";
      }
      return "alchemical hydra";
   }

   /** Scales boss targets to the player's combat level, Slayer level and quest unlocks. */
   public static String bossTarget(final PlayerSnapshot p) {
      if (!p.isLoggedIn()) {
         return "Scurrius";
      }
      final int combat = p.getCombatLevel();
      final int slayer = p.level(Skill.SLAYER);
      if (combat < 55) {
         return "Obor";
      }
      if (combat < 75) {
         return "Scurrius";
      }
      if (combat < 90) {
         return "Sarachnis";
      }
      if (combat < 105) {
         if (p.isQuestCompleted(Quest.DRAGON_SLAYER_II)) {
            return "Vorkath";
         }
         if (p.isQuestCompleted(Quest.SECRETS_OF_THE_NORTH)) {
            return "Phantom Muspah";
         }
         if (p.isQuestCompleted(Quest.REGICIDE)) {
            return "Zulrah";
         }
         if (slayer >= 91) {
            return "Cerberus";
         }
         return "General Graardor";
      }
      if (p.isQuestCompleted(Quest.DESERT_TREASURE_II__THE_FALLEN_EMPIRE)) {
         return "Vardorvis";
      }
      if (slayer >= 95) {
         return "Alchemical Hydra";
      }
      if (p.isQuestCompleted(Quest.DRAGON_SLAYER_II)) {
         return "Vorkath";
      }
      if (p.isQuestCompleted(Quest.SECRETS_OF_THE_NORTH)) {
         return "Phantom Muspah";
      }
      if (p.isQuestCompleted(Quest.REGICIDE)) {
         return "Zulrah";
      }
      return "General Graardor";
   }

   /** Picks a minigame suited to the player's current skills, combat level and quest unlocks. */
   public static String minigameTarget(final PlayerSnapshot p) {
      if (!p.isLoggedIn()) {
         return "Guardians of the Rift";
      }
      if (p.level(Skill.AGILITY) >= 72 && p.level(Skill.AGILITY) < 99 && p.isQuestCompleted(Quest.SINS_OF_THE_FATHER)) {
         return "Hallowed Sepulchre";
      }
      if (p.getCombatLevel() >= 95 && p.isQuestCompleted(Quest.BENEATH_CURSED_SANDS)) {
         return "Tombs of Amascut";
      }
      if (p.level(Skill.RANGED) >= 70 && p.getCombatLevel() >= 80) {
         return "TzHaar Fight Cave";
      }
      if (p.level(Skill.RUNECRAFT) >= 27 && p.level(Skill.RUNECRAFT) < 85) {
         return "Guardians of the Rift";
      }
      if (p.level(Skill.FIREMAKING) >= 50 && p.level(Skill.FIREMAKING) < 85) {
         return "Wintertodt";
      }
      if (p.level(Skill.FISHING) >= 35 && p.level(Skill.FISHING) < 80) {
         return "Tempoross";
      }
      if (p.getCombatLevel() >= 40) {
         return "Pest Control";
      }
      return "Tempoross";
   }

   public static int nextMilestone(final int level) {
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
