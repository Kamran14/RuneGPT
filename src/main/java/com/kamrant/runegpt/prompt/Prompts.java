package com.kamrant.runegpt.prompt;

/**
 * Every prompt sent to the LLM lives here: system prompts, the JSON response contracts, and the
 * user prompt templates behind the guide cards. Templates use {field} placeholders filled from the
 * guide's editable fields; %s placeholders are filled with String.format.
 */
public final class Prompts {

   private Prompts() {
   }

   // ---------------------------------------------------------------------------------------------
   // Main assistant
   // ---------------------------------------------------------------------------------------------

   /** System prompt for every chat and guide request. Replies must follow {@link #RESPONSE_FORMAT}. */
   public static final String SYSTEM =
      "You are RuneGPT, a tactical Old School RuneScape assistant in the RuneLite sidebar. "
         + "Answer like an experienced player giving orders: direct, specific, no small talk.\n"
         + "Rules:\n"
         + "- No greetings, sign-offs, filler or pleasantries. Never write things like \"Happy grinding!\", "
         + "\"Good luck!\", \"Hope this helps\", \"Great question\", \"Let me know if you need anything else\", "
         + "and never restate the question.\n"
         + "- Tailor everything to the player data below. Use exact in-game names for items, NPCs, monsters "
         + "and locations.\n"
         + "- Never invent quests, items, NPCs, monsters or locations. If you are not sure something exists "
         + "under that exact name, say so and name the closest real thing.\n"
         + "- Anything named in the player data or in VERIFIED OSRS WIKI DATA below exists in OSRS (miniquests "
         + "count as quests). Never say it doesn't exist; answer from that data, which overrides your own memory.\n"
         + "- For item prices and gear/supply costs, use ONLY the numbers in VERIFIED LIVE GRAND EXCHANGE PRICES "
         + "below. Never guess or recall a GP price from memory; if an item's price isn't listed there, don't state "
         + "a GP price for it.\n"
         + "- If the answer depends on data you don't have (e.g. bank contents), say so in one short line.\n\n";

   /** The JSON contract for {@link #SYSTEM}, appended to it. */
   public static final String RESPONSE_FORMAT =
      "Always reply with exactly one JSON object and nothing else (no code fences):\n"
         + "{\"answer\": \"...\", \"guide\": null}\n"
         + "- \"answer\": concise Markdown for a panel about 200px wide: short ### headings, bullet lists, "
         + "**bold** for key names. At most about 120 words. No tables wider than 2 columns. Don't repeat the "
         + "guide's item lists here.\n"
         + "- \"guide\": include ONLY when the player wants to do something (a quest, boss, monster, skill "
         + "method, money maker, minigame or trip setup); otherwise null. Shape:\n"
         + "{\"title\": \"short name, e.g. Blue dragons in Taverley Dungeon\", "
         + "\"steps\": [\"imperative steps, at most 6, each under 15 words\"], "
         + "\"current_step\": 1, "
         + "\"required_items\": [{\"name\": \"exact item name\", \"quantity\": 1}], "
         + "\"recommended_gear\": [{\"name\": \"exact item name\", \"slot\": \"head|cape|neck|ammo|weapon|body|"
         + "shield|legs|hands|feet|ring\"}], "
         + "\"location\": {\"name\": \"exact place name\", \"x\": 0, \"y\": 0, \"plane\": 0}, "
         + "\"npc\": \"exact NPC or monster name, or empty\"}\n"
         + "- current_step: 1-based index in steps of the step the player is on right now. Steps before current_step "
         + "are stages they have already completed (check their quest/task progress, items, location and question). "
         + "For a quest or multi-part activity, list the main stages in order and set current_step to the first "
         + "incomplete stage (e.g. 6 if steps 1-5 are already completed).\n"
         + "- required_items: things to carry for current_step onward (don't list items only needed for already-"
         + "completed steps). recommended_gear: things to wear or wield, at most one per slot, matched to the "
         + "player's levels and what they own.\n"
         + "- location x/y/plane: your best estimate of the OSRS world coordinates of the spot; omit x and y "
         + "if you don't know them.\n";

   /** Header before the per-request player data block. */
   public static final String PLAYER_CONTEXT_HEADER = RESPONSE_FORMAT + "\nCurrent player data:\n";

   // ---------------------------------------------------------------------------------------------
   // Quest step assistant (wiki-grounded)
   // ---------------------------------------------------------------------------------------------

   public static final String QUEST_SYSTEM =
      "You are a tactical Old School RuneScape quest assistant. You are given the official OSRS Wiki text for "
         + "ONE quest and the player's current state. Use ONLY facts from the wiki text. Never invent steps, "
         + "items, NPCs or locations; if the wiki text doesn't say, leave the field empty. No filler or "
         + "pleasantries. Respond with a single JSON object and nothing else (no markdown, no code fences).";

   /** Appended after the wiki text in a quest step request. */
   public static final String QUEST_STEP_INSTRUCTIONS =
      "Work out where the player most likely is in this quest from the status, their items, location and "
         + "nearby NPCs, then what they should do next. If the quest is not started, the next step is how to "
         + "start it. Reply with JSON exactly like:\n"
         + "{\"progress\": \"where they likely are, one sentence\", "
         + "\"confidence\": \"high|medium|low\", "
         + "\"completed_steps\": [\"up to 4 major steps already completed before the current step\"], "
         + "\"next_step\": \"what to do now, 1-2 sentences, include dialogue options if the wiki lists them\", "
         + "\"location\": \"where to go, as named in the wiki\", "
         + "\"npc\": \"exact name of the NPC to talk to next as written in the wiki, or empty\", "
         + "\"items\": [{\"name\": \"exact item name from the wiki\", \"quantity\": 1}], "
         + "\"then\": [\"up to 3 following steps, short\"], "
         + "\"tip\": \"one short tip from the wiki, or empty\"}\n"
         + "Only list items needed for next_step and the following steps, never for completed_steps.";

   public static final String QUEST_WIKI_HEADER = "\nOSRS WIKI TEXT (the only source you may use):\n";
   public static final String QUEST_COMPLETED_STEP = "The player says they just completed this step: %s\n";

   /** Header for wiki facts attached to a chat request about specific quests. */
   public static final String WIKI_FACTS_HEADER = "\nVERIFIED OSRS WIKI DATA (from the official quest list and the "
      + "OSRS Wiki; authoritative):\n";

   /** Header for live Grand Exchange prices attached to a chat or guide request. */
   public static final String PRICE_FACTS_HEADER = "\nVERIFIED LIVE GRAND EXCHANGE PRICES (use only these numbers "
      + "for item prices; never invent GP prices):\n";

   /** Header for the wiki requirement data appended to a "Requirements for quest" prompt. */
   public static final String REQUIREMENTS_WIKI_HEADER = "\n\nOfficial OSRS Wiki data for %s (use only this for requirements):\n";

   /** Prompt behind each section's "Something else?" box: category, then the player's text. */
   public static final String OTHER_INTEREST = "About %s: %s";

   /** Preferences picked with the quick-choice chips, appended to a guide prompt. */
   public static final String PREFERENCES = "\n\nMy preferences: %s.";

   // ---------------------------------------------------------------------------------------------
   // Guide card templates: title, then the prompt sent when the player presses Ask.
   // ---------------------------------------------------------------------------------------------

   public static final String QUEST_CONTINUE_TITLE = "Continue {quest}";
   public static final String QUEST_CONTINUE = "Help me continue the quest {quest}: where I am in it, the next "
      + "step and the items I need.";
   public static final String QUEST_HELPER_TITLE = "Quest helper: {quest}";
   public static final String QUEST_HELPER = QUEST_CONTINUE;
   public static final String QUEST_NEXT_TITLE = "Which {count} quests next?";
   public static final String QUEST_NEXT = "Based on my stats and quest progress, recommend the next {count} quests "
      + "I should do, in order. For each, say why it's worth doing (rewards or unlocks) and confirm I meet the "
      + "requirements.";
   public static final String QUEST_REQUIREMENTS_TITLE = "Requirements for {quest}";
   public static final String QUEST_REQUIREMENTS = "What do I still need before I can start {quest}? Check my "
      + "stats and list missing skill levels and prerequisite quests, with the quickest way to get each.";

   public static final String GEAR_OPTIMIZE_TITLE = "Best gear I own for {monster}";
   public static final String GEAR_OPTIMIZE = "Pick the best gear setup I own for fighting {monster} from my worn "
      + "items, inventory and bank.";
   public static final String MONSTER_KILL_TITLE = "Kill {count} {monster}";
   public static final String MONSTER_KILL = "Set me up to kill {count} {monster}: best location, gear and "
      + "inventory for my stats, rough time needed, and drops to watch for.";
   public static final String MONSTER_SLAYER_TITLE = "Slayer task: {count} {monster}";
   public static final String MONSTER_SLAYER = "My Slayer task is {count} {monster}. Where should I kill them, what "
      + "gear and inventory should I bring, and should I do, skip or block it at my level?";
   public static final String MONSTER_TRAIN_TITLE = "Best combat training spot";
   public static final String MONSTER_TRAIN = "Where should I train combat right now for good XP per hour? Use my "
      + "combat stats and say which stat to train first.";

   public static final String BOSS_GEAR_TITLE = "Best gear I own for {boss}";
   public static final String BOSS_GEAR = "Pick the best gear setup I own for fighting {boss} from my worn items, "
      + "inventory and bank.";
   public static final String BOSS_READY_TITLE = "Am I ready for {boss}?";
   public static final String BOSS_READY = "Am I ready to fight {boss}? Check my stats against the recommended "
      + "levels, set up my gear and inventory, list the key mechanics, and estimate kills per hour.";
   public static final String BOSS_SUGGEST_TITLE = "Bosses for my level";
   public static final String BOSS_SUGGEST = "Which bosses suit my stats best right now? Give {count} options with "
      + "what each is good for (profit, unlocks or pets) and what I need first.";

   public static final String SKILL_TRAIN_TITLE = "Train {skill} to {target}";
   public static final String SKILL_TRAIN = "Fastest way to train {skill} from my current level to {target}? "
      + "Compare a fast or expensive method with a cheap or AFK one, with XP per hour and costs.";
   public static final String SKILL_99_TITLE = "Plan 99 {skill}";
   public static final String SKILL_99 = "Plan my route to 99 {skill} from my current level: methods per level "
      + "range, total time and cost, and quests or unlocks that speed it up.";
   public static final String SKILL_QUEST_XP_TITLE = "Quests with {skill} XP";
   public static final String SKILL_QUEST_XP = "Which quests give XP rewards in {skill} that I could use to skip "
      + "early levels?";

   public static final String MONEY_BEST_TITLE = "Top {count} money makers";
   public static final String MONEY_BEST = "Give me the {count} best money making methods for my current stats, "
      + "with estimated GP per hour, requirements and how active each one is.";
   public static final String MONEY_SKILL_TITLE = "Make money with {skill}";
   public static final String MONEY_SKILL = "Best ways to make money with {skill} at my level? Include GP per hour "
      + "and startup cost.";
   public static final String MONEY_AFK_TITLE = "AFK money making";
   public static final String MONEY_AFK = "Best low-attention (AFK) money making methods for my stats, with GP per "
      + "hour.";

   public static final String MINIGAME_WORTH_TITLE = "Minigames worth doing";
   public static final String MINIGAME_WORTH = "Which minigames are worth doing for my account right now, and which "
      + "rewards should I aim for in each?";
   public static final String MINIGAME_GUIDE_TITLE = "Guide to {minigame}";
   public static final String MINIGAME_GUIDE = "Guide me through {minigame}: requirements, how it works, the best "
      + "strategy, and which rewards to buy first.";
}
