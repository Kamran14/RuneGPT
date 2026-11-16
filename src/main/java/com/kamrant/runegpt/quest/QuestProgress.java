package com.kamrant.runegpt.quest;

import com.kamrant.runegpt.model.ItemHoldings;
import com.kamrant.runegpt.service.ItemStatusResolver;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;

/**
 * Dynamically reads how far the player is in any OSRS quest on the client thread:
 * <ol>
 *    <li>looks up the quest's unstarted and completion states from the game cache's {@link DBTableID.Quest} table;</li>
 *    <li>reads the quest's live Varbit or VarPlayer value (plus sub-varbits for multi-branch quests);</li>
 *    <li>combines that with any open/cached in-game Quest Journal lines and owned quest items to determine
 *    which steps in a guide are already completed (green) and which step is the current task (orange/red).</li>
 * </ol>
 */
public final class QuestProgress {

   // Positive values = VarbitID; negative values = -(VarPlayerID + 1).
   private static final Map<Quest, Integer> QUEST_VARS = new EnumMap<>(Quest.class);
   private static final Set<Integer> TRACKED_VARBITS = new HashSet<>();
   private static final Set<Integer> TRACKED_VARPS = new HashSet<>();

   // Multi-branch sub-varbits: Quest -> { label, varbitId, completedThreshold }.
   private static final Map<Quest, Object[][]> SUB_VARBITS = new EnumMap<>(Quest.class);

   static {
      // F2P quests
      varbit(Quest.BELOW_ICE_MOUNTAIN, VarbitID.BIM);
      varp(Quest.BLACK_KNIGHTS_FORTRESS, 130);
      varp(Quest.COOKS_ASSISTANT, 29);
      varbit(Quest.THE_CORSAIR_CURSE, VarbitID.CORSCURS_PROGRESS);
      varbit(Quest.DEMON_SLAYER, VarbitID.DEMONSLAYER_MAIN);
      varp(Quest.DORICS_QUEST, 31);
      varp(Quest.DRAGON_SLAYER_I, 176);
      varp(Quest.ERNEST_THE_CHICKEN, 32);
      varbit(Quest.GOBLIN_DIPLOMACY, VarbitID.GOBDIP_MAIN);
      varp(Quest.IMP_CATCHER, 160);
      varbit(Quest.THE_IDES_OF_MILK, VarbitID.COWQUEST);
      varp(Quest.THE_KNIGHTS_SWORD, 122);
      varbit(Quest.MISTHALIN_MYSTERY, VarbitID.MISTMYST_PROGRESS);
      varp(Quest.PIRATES_TREASURE, 71);
      varp(Quest.PRINCE_ALI_RESCUE, 273);
      varp(Quest.THE_RESTLESS_GHOST, 107);
      varp(Quest.ROMEO__JULIET, 144);
      varp(Quest.RUNE_MYSTERIES, 63);
      varp(Quest.SHEEP_SHEARER, 179);
      varp(Quest.SHIELD_OF_ARRAV, 145);
      varp(Quest.VAMPYRE_SLAYER, 178);
      varp(Quest.WITCHS_POTION, 67);
      varbit(Quest.X_MARKS_THE_SPOT, VarbitID.CLUEQUEST);

      // Members' quests & miniquests
      varbit(Quest.ANIMAL_MAGNETISM, VarbitID.ANMA_MAIN);
      varbit(Quest.ANOTHER_SLICE_OF_HAM, VarbitID.SLICE_QUEST);
      varbit(Quest.THE_ASCENT_OF_ARCEUUS, VarbitID.ARCQUEST);
      varp(Quest.ALFRED_GRIMHANDS_BARCRAWL, 77);
      varbit(Quest.AT_FIRST_LIGHT, VarbitID.AFL);
      varbit(Quest.BARBARIAN_TRAINING, VarbitID.BRUT_MINIQUEST);
      varbit(Quest.BEAR_YOUR_SOUL, VarbitID.ARCEUUS_SOULBEARER_STORY);
      varbit(Quest.BENEATH_CURSED_SANDS, VarbitID.BCS);
      varbit(Quest.BETWEEN_A_ROCK, VarbitID.DWARFROCK_QUEST);
      varp(Quest.BIG_CHOMPY_BIRD_HUNTING, 293);
      varp(Quest.BIOHAZARD, 68);
      varbit(Quest.THE_BLOOD_MOON_RISES, VarbitID.MYQ6);
      varbit(Quest.BONE_VOYAGE, VarbitID.FOSSILQUEST_PROGRESS);
      varp(Quest.CABIN_FEVER, 655);
      varbit(Quest.CHILDREN_OF_THE_SUN, VarbitID.VMQ1);
      varbit(Quest.CLIENT_OF_KOUREND, VarbitID.VEOS_PROGRESS);
      varp(Quest.CLOCK_TOWER, 10);
      varbit(Quest.COLD_WAR, VarbitID.PENG_QUEST);
      varbit(Quest.CONTACT, VarbitID.CONTACT);
      varbit(Quest.CRAB_QUEST, VarbitID.CRAB_MAIN);
      varp(Quest.CREATURE_OF_FENKENSTRAIN, 399);
      varbit(Quest.CURRENT_AFFAIRS, VarbitID.CURRENT_AFFAIRS);
      varbit(Quest.THE_CURSE_OF_ARRAV, VarbitID.COA);
      varbit(Quest.CURSE_OF_THE_EMPTY_LORD, VarbitID.SECRET_GHOST6_BACKSTORY);
      varbit(Quest.DADDYS_HOME, VarbitID.DADDYSHOME_STATUS);
      varbit(Quest.DARKNESS_OF_HALLOWVALE, VarbitID.MYQ3_MAIN_QUEST);
      varbit(Quest.DEATH_ON_THE_ISLE, VarbitID.DOTI);
      varp(Quest.DEATH_PLATEAU, VarPlayerID.DEATH_EQUIPROOM);
      varbit(Quest.DEATH_TO_THE_DORGESHUUN, VarbitID.DTTD_MAIN);
      varbit(Quest.DEFENDER_OF_VARROCK, VarbitID.DOV);
      varbit(Quest.THE_DEPTHS_OF_DESPAIR, VarbitID.HOSIDIUSQUEST);
      varbit(Quest.DESERT_TREASURE_I, VarbitID.DESERTTREASURE);
      varbit(Quest.DESERT_TREASURE_II__THE_FALLEN_EMPIRE, VarbitID.DT2);
      varbit(Quest.DEVIOUS_MINDS, VarbitID.DEVIOUS_MAIN);
      varp(Quest.THE_DIG_SITE, 131);
      varbit(Quest.DRAGON_SLAYER_II, VarbitID.DS2);
      varbit(Quest.DREAM_MENTOR, VarbitID.DREAM_PROG);
      varp(Quest.DRUIDIC_RITUAL, 80);
      varp(Quest.DWARF_CANNON, 0);
      varp(Quest.EADGARS_RUSE, 335);
      varbit(Quest.EAGLES_PEAK, VarbitID.EAGLEPEAK_QUEST);
      varp(Quest.ELEMENTAL_WORKSHOP_I, 299);
      varbit(Quest.ELEMENTAL_WORKSHOP_II, VarbitID.ELEMENTAL_QUEST_2_MAIN);
      varbit(Quest.ENAKHRAS_LAMENT, VarbitID.ENAKH_QUEST);
      varbit(Quest.THE_ENCHANTED_KEY, VarbitID.MAKINGHISTORY_LOCSTATUS);
      varbit(Quest.ENLIGHTENED_JOURNEY, VarbitID.ZEP_QUEST);
      varp(Quest.ENTER_THE_ABYSS, 492);
      varbit(Quest.ETHICALLY_ACQUIRED_ANTIQUITIES, VarbitID.EAA);
      varbit(Quest.THE_EYES_OF_GLOUPHRIE, VarbitID.EYEGLO_QUEST);
      varbit(Quest.FAIRYTALE_I__GROWING_PAINS, VarbitID.FAIRY_FARMERS_QUEST);
      varbit(Quest.FAIRYTALE_II__CURE_A_QUEEN, VarbitID.FAIRY2_QUEENCURE_QUEST);
      varbit(Quest.FALLEN_FROM_GRACE, VarbitID.FFG);
      varp(Quest.FAMILY_CREST, 148);
      varbit(Quest.FAMILY_PEST, VarbitID.FAMILY_QUEST_PROGRESS);
      varbit(Quest.THE_FEUD, VarbitID.FEUD_VAR);
      varp(Quest.FIGHT_ARENA, 17);
      varbit(Quest.THE_FINAL_DAWN, VarbitID.VMQ4);
      varp(Quest.FISHING_CONTEST, 11);
      varbit(Quest.FORGETTABLE_TALE, VarbitID.FORGET_QUEST);
      varbit(Quest.THE_FORSAKEN_TOWER, VarbitID.LOVAQUEST);
      varbit(Quest.THE_FREMENNIK_EXILES, VarbitID.VIKINGEXILE);
      varbit(Quest.THE_FREMENNIK_ISLES, VarbitID.FRIS_QUEST);
      varp(Quest.THE_FREMENNIK_TRIALS, 347);
      varbit(Quest.THE_GARDEN_OF_DEATH, VarbitID.TGOD);
      varbit(Quest.GARDEN_OF_TRANQUILLITY, VarbitID.GARDEN_QUEST);
      varbit(Quest.THE_GENERALS_SHADOW, VarbitID.SHADOW_MAJ_MAIN);
      varp(Quest.GERTRUDES_CAT, 180);
      varbit(Quest.GETTING_AHEAD, VarbitID.GA);
      varbit(Quest.GHOSTS_AHOY, VarbitID.AHOY_QUESTVAR);
      varbit(Quest.THE_GIANT_DWARF, VarbitID.GIANTDWARF_QUEST);
      varbit(Quest.THE_GOLEM, VarbitID.GOLEM_A);
      varp(Quest.THE_GRAND_TREE, 150);
      varp(Quest.THE_GREAT_BRAIN_ROBBERY, 980);
      varbit(Quest.GRIM_TALES, VarbitID.GRIM_QUEST);
      varbit(Quest.THE_HAND_IN_THE_SAND, VarbitID.HANDSAND_QUEST);
      varp(Quest.HAUNTED_MINE, 382);
      varp(Quest.HAZEEL_CULT, 223);
      varbit(Quest.THE_HEART_OF_DARKNESS, VarbitID.VMQ3);
      varp(Quest.HEROES_QUEST, 188);
      varbit(Quest.HIS_FAITHFUL_SERVANTS, VarbitID.HFS);
      varp(Quest.HOLY_GRAIL, 5);
      varbit(Quest.HOPESPEARS_WILL, VarbitID.HOPESPEAR);
      varbit(Quest.HORROR_FROM_THE_DEEP, VarbitID.HORRORQUEST);
      varbit(Quest.ICTHLARINS_LITTLE_HELPER, VarbitID.ICS_LITTLE_VAR);
      varbit(Quest.IN_AID_OF_THE_MYREQUE, VarbitID.MYREQUE_2_QUEST);
      varbit(Quest.IN_SEARCH_OF_KNOWLEDGE, VarbitID.HOSDUN_KNOWLEDGE_SEARCH);
      varp(Quest.IN_SEARCH_OF_THE_MYREQUE, 387);
      varp(Quest.JUNGLE_POTION, 175);
      varbit(Quest.A_KINGDOM_DIVIDED, VarbitID.AKD);
      varbit(Quest.KINGS_RANSOM, VarbitID.KR_QUEST);
      varbit(Quest.LAIR_OF_TARN_RAZORLOR, VarbitID.LOTR_INSTANCE_ENTERED);
      varbit(Quest.LAND_OF_THE_GOBLINS, VarbitID.LOTG);
      varp(Quest.LEGENDS_QUEST, 139);
      varp(Quest.LOST_CITY, 147);
      varbit(Quest.THE_LOST_TRIBE, VarbitID.LOST_TRIBE_QUEST);
      varbit(Quest.LUNAR_DIPLOMACY, VarbitID.LUNAR_QUEST_MAIN);
      varp(Quest.MAGE_ARENA_I, 267);
      varbit(Quest.MAGE_ARENA_II, VarbitID.MA2_PROGRESS);
      varbit(Quest.MAKING_FRIENDS_WITH_MY_ARM, VarbitID.MY2ARM_STATUS);
      varbit(Quest.MAKING_HISTORY, VarbitID.MAKINGHISTORY_PROG);
      varbit(Quest.MEAT_AND_GREET, VarbitID.MAG);
      varp(Quest.MERLINS_CRYSTAL, 14);
      varp(Quest.MONKEY_MADNESS_I, 365);
      varbit(Quest.MONKEY_MADNESS_II, VarbitID.MM2_PROGRESS);
      varp(Quest.MONKS_FRIEND, 30);
      varbit(Quest.MOUNTAIN_DAUGHTER, VarbitID.MDAUGHTER_QUEST_VAR);
      varp(Quest.MOURNINGS_END_PART_I, 517);
      varbit(Quest.MOURNINGS_END_PART_II, VarbitID.MOURNING_QUEST_MAIN);
      varp(Quest.MURDER_MYSTERY, 192);
      varbit(Quest.MY_ARMS_BIG_ADVENTURE, VarbitID.MYARM);
      varp(Quest.NATURE_SPIRIT, 307);
      varbit(Quest.A_NIGHT_AT_THE_THEATRE, VarbitID.TOBQUEST);
      varp(Quest.OBSERVATORY_QUEST, 112);
      varbit(Quest.OLAFS_QUEST, VarbitID.OLAF_QUEST_VAR);
      varp(Quest.ONE_SMALL_FAVOUR, 416);
      varbit(Quest.PANDEMONIUM, VarbitID.SAILING_INTRO);
      varbit(Quest.THE_PATH_OF_GLOUPHRIE, VarbitID.POG);
      varbit(Quest.PERILOUS_MOONS, VarbitID.PMOON_QUEST);
      varp(Quest.PLAGUE_CITY, 165);
      varbit(Quest.A_PORCINE_OF_INTEREST, VarbitID.PORCINE);
      varp(Quest.PRIEST_IN_PERIL, 302);
      varbit(Quest.PRYING_TIMES, VarbitID.QUEST_PRY);
      varbit(Quest.THE_QUEEN_OF_THIEVES, VarbitID.PISCQUEST);
      varp(Quest.RAG_AND_BONE_MAN_I, 714);
      varp(Quest.RAG_AND_BONE_MAN_II, 714);
      varbit(Quest.RATCATCHERS, VarbitID.RATCATCH_VAR);
      varbit(Quest.RECIPE_FOR_DISASTER, VarbitID.HUNDRED_MAIN_QUEST_VAR);
      varbit(Quest.RECIPE_FOR_DISASTER__ANOTHER_COOKS_QUEST, VarbitID.HUNDRED_MAIN_QUEST_VAR);
      varbit(Quest.RECIPE_FOR_DISASTER__MOUNTAIN_DWARF, VarbitID.HUNDRED_DWARF_QUEST);
      varbit(Quest.RECIPE_FOR_DISASTER__WARTFACE__BENTNOZE, VarbitID._100GOBLIN);
      varbit(Quest.RECIPE_FOR_DISASTER__PIRATE_PETE, VarbitID._100_PIRATE_QUEST_VAR);
      varbit(Quest.RECIPE_FOR_DISASTER__LUMBRIDGE_GUIDE, VarbitID._100GUIDE_PROG);
      varbit(Quest.RECIPE_FOR_DISASTER__EVIL_DAVE, VarbitID.HUNDRED_DAVE_MAIN);
      varbit(Quest.RECIPE_FOR_DISASTER__SKRACH_UGLOGWEE, VarbitID._100_OGRE_PROG);
      varbit(Quest.RECIPE_FOR_DISASTER__SIR_AMIK_VARZE, VarbitID.CHICKENQUEST);
      varbit(Quest.RECIPE_FOR_DISASTER__KING_AWOWOGEI, VarbitID.HUNDRED_ILM_QUEST);
      varbit(Quest.RECIPE_FOR_DISASTER__CULINAROMANCER, VarbitID.HUNDRED_MAIN_QUEST_VAR);
      varbit(Quest.RECRUITMENT_DRIVE, VarbitID.RD_MAIN);
      varbit(Quest.THE_RED_REEF, VarbitID.TRR);
      varp(Quest.REGICIDE, 328);
      varbit(Quest.THE_RIBBITING_TALE_OF_A_LILY_PAD_LABOUR_DISPUTE, VarbitID.FROG_QUEST);
      varp(Quest.ROVING_ELVES, 402);
      varbit(Quest.ROYAL_TROUBLE, VarbitID.ROYAL_QUEST);
      varbit(Quest.A_RUFF_SITUATION, 15889);
      varp(Quest.RUM_DEAL, 600);
      varp(Quest.SCORPION_CATCHER, 76);
      varbit(Quest.SCRAMBLED, VarbitID.SCRAMBLED);
      varp(Quest.SEA_SLUG, 159);
      varbit(Quest.SECRETS_OF_THE_NORTH, VarbitID.SOTN);
      varp(Quest.SHADES_OF_MORTTON, 339);
      varbit(Quest.SHADOW_OF_THE_STORM, VarbitID.AGRITH_QUEST);
      varbit(Quest.SHADOWS_OF_CUSTODIA, VarbitID.SOC);
      varp(Quest.SHEEP_HERDER, 60);
      varp(Quest.SHILO_VILLAGE, 116);
      varbit(Quest.SINS_OF_THE_FATHER, VarbitID.MYQ5);
      varbit(Quest.SKIPPY_AND_THE_MOGRES, VarbitID.SKIPPY_STATE);
      varbit(Quest.SLEEPING_GIANTS, VarbitID.SLEEPING_GIANTS);
      varbit(Quest.THE_SLUG_MENACE, VarbitID.SLUG2_MAIN);
      varbit(Quest.SONG_OF_THE_ELVES, VarbitID.SOTE);
      varbit(Quest.A_SOULS_BANE, VarbitID.SOULBANE_PROG);
      varbit(Quest.SPIRITS_OF_THE_ELID, VarbitID.ELIDQUEST);
      varbit(Quest.SWAN_SONG, VarbitID.SWANSONG);
      varp(Quest.TAI_BWO_WANNAI_TRIO, 320);
      varbit(Quest.A_TAIL_OF_TWO_CATS, VarbitID.TWOCATS_QUEST);
      varbit(Quest.TALE_OF_THE_RIGHTEOUS, VarbitID.SHAYZIENQUEST);
      varbit(Quest.A_TASTE_OF_HOPE, VarbitID.MYQ4);
      varbit(Quest.TEARS_OF_GUTHIX, VarbitID.TOG_JUNA_BOWL);
      varp(Quest.TEMPLE_OF_IKOV, 26);
      varbit(Quest.TEMPLE_OF_THE_EYE, VarbitID.TOTE);
      varp(Quest.THRONE_OF_MISCELLANIA, 359);
      varp(Quest.THE_TOURIST_TRAP, 197);
      varbit(Quest.TOWER_OF_LIFE, VarbitID.TOL_PROG);
      varp(Quest.TREE_GNOME_VILLAGE, 111);
      varp(Quest.TRIBAL_TOTEM, 200);
      varp(Quest.TROLL_ROMANCE, 385);
      varp(Quest.TROLL_STRONGHOLD, 317);
      varbit(Quest.TROUBLED_TORTUGANS, VarbitID.TT);
      varbit(Quest.TWILIGHTS_PROMISE, VarbitID.VMQ2);
      varp(Quest.UNDERGROUND_PASS, 161);
      varbit(Quest.VALE_TOTEMS, VarbitID.ENT_TOTEMS_INTRO);
      varbit(Quest.WANTED, VarbitID.WANTED_MAIN);
      varp(Quest.WATCHTOWER, 212);
      varp(Quest.WATERFALL_QUEST, 65);
      varbit(Quest.WHAT_LIES_BELOW, VarbitID.SUROK_QUEST);
      varbit(Quest.WHILE_GUTHIX_SLEEPS, VarbitID.WGS);
      varp(Quest.WITCHS_HOUSE, 226);
      varbit(Quest.ZOGRE_FLESH_EATERS, VarbitID.ZOGRE);

      // Multi-branch quest sub-varbits: { label, varbitId, completedThreshold, keywords... }
      SUB_VARBITS.put(Quest.BARBARIAN_TRAINING, new Object[][] {
         { "Heavy rod fishing (barbarian rod, leaping fish, roe)", VarbitID.BRUT_FISHING_R, 3,
            "barbarian rod", "leaping", "dissect", "heavy rod" },
         { "Barehanded fishing (harpoonless)", VarbitID.BRUT_FISHING_S, 3,
            "barehand", "harpoon" },
         { "Barbarian herblore (roe on 2-dose potion)", VarbitID.BRUT_HERB_POTION, 3,
            "potion", "herblore", "caviar", "drink" },
         { "Bow firemaking (lighting logs with a bow)", VarbitID.BRUT_FIRE, 3,
            "bow", "firemaking", "light " },
         { "Barehanded seed planting", VarbitID.BRUT_FARMING_PLANTING, 3,
            "seed", "plant", "dibber" },
         { "Pot-smashing (planting saplings)", VarbitID.BRUT_FARMING_SMASHING, 3,
            "sapling", "smash", "pot" },
         { "Barbarian spear smithing", VarbitID.BRUT_SMITH_SPEAR, 3,
            "spear", "smith", "anvil", "bar" },
         { "Barbarian hasta smithing", VarbitID.BRUT_SMITH_HASTA, 3,
            "hasta" },
         { "Pyre ships & chewed bones (kill Mithril dragons)", VarbitID.BRUT_CRAFT_SHIP, 3,
            "mithril dragon", "chewed bone", "pyre" }
      });

      SUB_VARBITS.put(Quest.GHOSTS_AHOY, new Object[][] {
         { "Nettle tea / Porcelain cup", VarbitID.AHOY_SUBQUEST_NETTLETEA, 3, "nettle", "porcelain", "tea" },
         { "Model ship / Toy boat", VarbitID.AHOY_SUBQUEST_TOYBOAT, 5, "toy boat", "model ship", "silk" },
         { "Longbow / Oak longbow signing", VarbitID.AHOY_SUBQUEST_BOW, 5, "oak longbow", "robin", "bow" }
      });

      for (final Object[][] rows : SUB_VARBITS.values()) {
         for (final Object[] row : rows) {
            TRACKED_VARBITS.add((int) row[1]);
         }
      }
   }

   private QuestProgress() {
   }

   private static void varbit(final Quest q, final int varbitId) {
      QUEST_VARS.put(q, varbitId);
      TRACKED_VARBITS.add(varbitId);
   }

   private static void varp(final Quest q, final int varpId) {
      QUEST_VARS.put(q, -(varpId + 1));
      TRACKED_VARPS.add(varpId);
   }

   /** True if the changed Varbit or VarPlayer tracks quest progression. */
   public static boolean isQuestVar(final int varbitId, final int varpId) {
      return (varbitId >= 0 && TRACKED_VARBITS.contains(varbitId))
         || (varpId >= 0 && TRACKED_VARPS.contains(varpId));
   }

   /**
    * Captures the numeric completion ratio (0.0 .. 1.0) for every in-progress quest on the client thread,
    * using the quest's varbit/varp and its {@link DBTableID.Quest#COL_ENDSTATE} from the game cache.
    */
   public static void captureFromClient(final Client client, final Map<String, QuestState> states,
      final Map<String, Double> ratiosOut, final Map<String, String> stageDescriptionsOut,
      final Map<String, Map<String, Boolean>> subTasksOut) {
      for (final Quest quest : Quest.values()) {
         final QuestState state = states.get(quest.getName());
         final int[] bounds = readQuestBounds(client, quest);
         final int unstarted = bounds[0];
         final int endState = bounds[1];
         final int currentVal = readVar(client, quest);

         if (state == QuestState.FINISHED) {
            ratiosOut.put(quest.getName(), 1.0);
         } else if (state == QuestState.IN_PROGRESS && currentVal >= 0) {
            if (endState > unstarted) {
               final double ratio = Math.max(0.05, Math.min(0.95,
                  (currentVal - unstarted) / (double) (endState - unstarted)));
               ratiosOut.put(quest.getName(), ratio);
               stageDescriptionsOut.put(quest.getName(), "stage " + currentVal + " / " + endState
                  + " (~" + Math.round(ratio * 100) + "% through the quest)");
            } else {
               stageDescriptionsOut.put(quest.getName(), "stage var = " + currentVal);
            }
         }

         final Object[][] subs = SUB_VARBITS.get(quest);
         if (subs != null) {
            final Map<String, Boolean> subMap = new LinkedHashMap<>();
            final StringBuilder desc = new StringBuilder();
            int doneCount = 0;
            for (final Object[] row : subs) {
               final String label = (String) row[0];
               final int val = client.getVarbitValue((int) row[1]);
               final int doneVal = (int) row[2];
               final boolean done = val >= doneVal;
               if (done) {
                  doneCount++;
               }
               subMap.put(label, done);
               desc.append("- ").append(label).append(": ")
                  .append(done ? "COMPLETED" : val > 0 ? "IN PROGRESS (var=" + val + ")" : "NOT STARTED")
                  .append('\n');
            }
            subTasksOut.put(quest.getName(), Collections.unmodifiableMap(subMap));
            if (doneCount > 0 || state == QuestState.IN_PROGRESS) {
               ratiosOut.put(quest.getName(), doneCount / (double) subs.length);
               stageDescriptionsOut.put(quest.getName(), doneCount + " / " + subs.length
                  + " sub-tasks completed:\n" + desc);
            }
         }
      }
   }

   /**
    * Dynamically determines the 0-based index of the current active step in {@code steps} for ANY quest,
    * combining the AI's {@code aiStepOneBased}, cached in-game Quest Journal {@code <str>} lines, multi-part
    * sub-varbits, owned quest items, and the quest's game-cache progress ratio.
    */
   public static int resolveCurrentStep(final String title, final List<String> steps, final int aiStepOneBased,
      final Map<String, QuestState> questStates, final Map<String, Double> questRatios,
      final Map<String, Map<String, Boolean>> subTasks, final QuestJournalCache journalCache,
      final ItemHoldings holdings) {
      if (steps.isEmpty()) {
         return 0;
      }
      int current = aiStepOneBased > 0 ? Math.min(steps.size() - 1, aiStepOneBased - 1) : 0;

      final String questName = matchQuestName(title, steps);
      if (questName == null) {
         return current;
      }
      final QuestState state = questStates.get(questName);
      if (state == QuestState.FINISHED) {
         return steps.size();
      }
      final Map<String, Boolean> subs = subTasks.getOrDefault(questName, Collections.emptyMap());
      final boolean anySubDone = subs.values().stream().anyMatch(Boolean::booleanValue);
      if (state == QuestState.NOT_STARTED && !anySubDone) {
         return 0;
      }

      final List<String> journalDone = journalCache == null
         ? Collections.emptyList() : journalCache.completedLines(questName);
      final List<String> journalActive = journalCache == null
         ? Collections.emptyList() : journalCache.activeLines(questName);
      final Object[][] subDefs = findSubDefs(questName);

      // Walk the steps in order and advance past any leading steps that are provably completed.
      int verifiedDone = 0;
      for (int i = 0; i < steps.size(); i++) {
         final String step = steps.get(i);
         if (isStepCompleted(step, i == 0 && steps.size() > 1, state == QuestState.IN_PROGRESS || anySubDone,
            subs, subDefs, journalDone, journalActive, holdings)) {
            verifiedDone = i + 1;
         } else if (subDefs != null && matchesAnySubDef(step, subDefs)) {
            // This step maps to a known sub-task that is NOT completed yet -> stop here.
            return Math.max(current, i);
         } else {
            break;
         }
      }
      current = Math.max(current, Math.min(steps.size() - 1, verifiedDone));

      // If the AI left current_step at the beginning (step 1) on an in-progress quest whose game-cache
      // progress ratio is already well into the quest, advance proportionally so completed stages show green.
      final Double ratio = questRatios.get(questName);
      if (aiStepOneBased <= 1 && ratio != null && ratio >= 0.25 && steps.size() > 1) {
         final int fromRatio = Math.min(steps.size() - 1, (int) Math.floor(ratio * steps.size()));
         current = Math.max(current, fromRatio);
      }
      return current;
   }

   private static boolean isStepCompleted(final String stepText, final boolean isFirstStep, final boolean questStarted,
      final Map<String, Boolean> subs, final Object[][] subDefs, final List<String> journalDone,
      final List<String> journalActive, final ItemHoldings holdings) {
      final String s = stepText.toLowerCase(Locale.ROOT);

      // 1. Sub-task varbit check (when the quest has per-branch varbits).
      if (subDefs != null && !subs.isEmpty()) {
         for (final Object[] def : subDefs) {
            if (matchesSubDef(s, def)) {
               return Boolean.TRUE.equals(subs.get((String) def[0]));
            }
         }
      }

      // 2. Opening "Start the quest / Talk to <start NPC>" step on a quest that is already IN_PROGRESS.
      if (isFirstStep && questStarted
         && (s.startsWith("start ") || s.startsWith("speak to ") || s.startsWith("talk to "))) {
         return true;
      }

      // 3. In-game Quest Journal <str> completed lines match.
      if (!journalDone.isEmpty() && matchesJournalLines(s, journalDone) && !matchesJournalLines(s, journalActive)) {
         return true;
      }

      // 4. Item-acquisition step where the player already holds the item in inventory, equipment or bank.
      if (holdings != null && holdings.isLoggedIn() && isItemAcquisitionStep(s, holdings)) {
         return true;
      }
      return false;
   }

   private static boolean matchesAnySubDef(final String stepText, final Object[][] subDefs) {
      final String s = stepText.toLowerCase(Locale.ROOT);
      for (final Object[] def : subDefs) {
         if (matchesSubDef(s, def)) {
            return true;
         }
      }
      return false;
   }

   private static boolean matchesSubDef(final String stepLower, final Object[] def) {
      for (int i = 3; i < def.length; i++) {
         if (stepLower.contains((String) def[i])) {
            return true;
         }
      }
      return false;
   }

   private static Object[][] findSubDefs(final String questName) {
      for (final Map.Entry<Quest, Object[][]> e : SUB_VARBITS.entrySet()) {
         if (e.getKey().getName().equalsIgnoreCase(questName)) {
            return e.getValue();
         }
      }
      return null;
   }

   private static boolean matchesJournalLines(final String stepLower, final List<String> lines) {
      final String[] words = stepLower.replaceAll("[^a-z0-9 ]", " ").trim().split("\\s+");
      final String corpus = String.join(" ", lines).toLowerCase(Locale.ROOT);
      int sig = 0;
      int hits = 0;
      for (final String w : words) {
         if (w.length() < 4 || isStopWord(w)) {
            continue;
         }
         sig++;
         if (corpus.contains(w)) {
            hits++;
         }
      }
      return sig >= 2 && hits * 2 >= sig;
   }

   private static boolean isItemAcquisitionStep(final String stepLower, final ItemHoldings holdings) {
      if (!(stepLower.startsWith("obtain ") || stepLower.startsWith("get ") || stepLower.startsWith("collect ")
         || stepLower.startsWith("acquire ") || stepLower.startsWith("bring "))) {
         return false;
      }
      for (final Map<String, Integer> map : List.of(holdings.getInventory(), holdings.getEquipment(), holdings.getBank())) {
         for (final Map.Entry<String, Integer> e : map.entrySet()) {
            if (e.getValue() > 0 && e.getKey().length() >= 4
               && stepLower.contains(e.getKey().toLowerCase(Locale.ROOT))) {
               return true;
            }
         }
      }
      return false;
   }

   private static boolean isStopWord(final String w) {
      return w.equals("talk") || w.equals("speak") || w.equals("with") || w.equals("from") || w.equals("then")
         || w.equals("your") || w.equals("into") || w.equals("near") || w.equals("have") || w.equals("take");
   }

   private static String matchQuestName(final String title, final List<String> steps) {
      if (title != null && !title.isEmpty()) {
         final String resolved = QuestResolver.resolve(title).getQuestName();
         if (resolved != null) {
            return resolved;
         }
         final List<String> mentioned = QuestMentions.find(title, 1);
         if (!mentioned.isEmpty()) {
            return mentioned.get(0);
         }
      }
      final String combined = (title == null ? "" : title) + " " + String.join(" ", steps);
      final List<String> inSteps = QuestMentions.find(combined, 1);
      if (!inSteps.isEmpty()) {
         return inSteps.get(0);
      }
      // Heuristic for Barbarian Training when the title says "Otto Godblessed" / "Mithril dragons" without the miniquest title.
      final String lower = combined.toLowerCase(Locale.ROOT);
      if (lower.contains("otto") && (lower.contains("baxtorian") || lower.contains("barbarian rod")
         || lower.contains("chewed bones"))) {
         return Quest.BARBARIAN_TRAINING.getName();
      }
      return null;
   }

   private static int readVar(final Client client, final Quest quest) {
      final Integer code = QUEST_VARS.get(quest);
      if (code == null) {
         return -1;
      }
      return code >= 0 ? client.getVarbitValue(code) : client.getVarpValue(-code - 1);
   }

   private static int[] readQuestBounds(final Client client, final Quest quest) {
      try {
         final List<Integer> rows = client.getDBRowsByValue(
            DBTableID.Quest.ID, DBTableID.Quest.COL_ID, 0, quest.getId());
         if (rows != null && !rows.isEmpty()) {
            final int row = rows.get(0);
            final Object[] startField = client.getDBTableField(row, DBTableID.Quest.COL_UNSTARTEDSTATE, 0);
            final Object[] endField = client.getDBTableField(row, DBTableID.Quest.COL_ENDSTATE, 0);
            final int start = startField != null && startField.length > 0 && startField[0] instanceof Integer
               ? (Integer) startField[0] : 0;
            final int end = endField != null && endField.length > 0 && endField[0] instanceof Integer
               ? (Integer) endField[0] : 0;
            return new int[] { start, end };
         }
      } catch (final RuntimeException ignored) {
         // DBTable not available in unit tests or before login.
      }
      return new int[] { 0, 0 };
   }
}
