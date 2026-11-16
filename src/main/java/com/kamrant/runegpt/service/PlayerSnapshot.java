package com.kamrant.runegpt.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;

/**
 * Immutable snapshot of the player's progress. Must be captured on the client thread
 * (quest state lookups run client scripts).
 */
public final class PlayerSnapshot {

   public static final PlayerSnapshot LOGGED_OUT = new PlayerSnapshot(false, Collections.emptyMap(), 0, 0,
      Collections.emptyList(), 0);

   private final boolean loggedIn;
   private final Map<Skill, Integer> levels;
   private final int combatLevel;
   private final int totalLevel;
   private final List<String> questsInProgress;
   private final int questsCompleted;

   private PlayerSnapshot(final boolean loggedIn, final Map<Skill, Integer> levels, final int combatLevel,
      final int totalLevel, final List<String> questsInProgress, final int questsCompleted) {
      this.loggedIn = loggedIn;
      this.levels = levels;
      this.combatLevel = combatLevel;
      this.totalLevel = totalLevel;
      this.questsInProgress = questsInProgress;
      this.questsCompleted = questsCompleted;
   }

   /** Call on the client thread. */
   public static PlayerSnapshot capture(final Client client) {
      if (client.getGameState() != GameState.LOGGED_IN) {
         return LOGGED_OUT;
      }
      final Map<Skill, Integer> levels = new EnumMap<>(Skill.class);
      for (final Skill skill : Skill.values()) {
         levels.put(skill, client.getRealSkillLevel(skill));
      }
      final Player local = client.getLocalPlayer();
      final int combat = local == null ? 0 : local.getCombatLevel();

      final List<String> inProgress = new ArrayList<>();
      int completed = 0;
      for (final Quest quest : Quest.values()) {
         final QuestState state = quest.getState(client);
         if (state == QuestState.IN_PROGRESS) {
            inProgress.add(quest.getName());
         } else if (state == QuestState.FINISHED) {
            completed++;
         }
      }
      return new PlayerSnapshot(true, Collections.unmodifiableMap(levels), combat, client.getTotalLevel(),
         Collections.unmodifiableList(inProgress), completed);
   }

   public boolean isLoggedIn() {
      return loggedIn;
   }

   public int level(final Skill skill) {
      return levels.getOrDefault(skill, 1);
   }

   public int getCombatLevel() {
      return combatLevel;
   }

   public List<String> getQuestsInProgress() {
      return questsInProgress;
   }

   /** Lowest skill, ties broken by enum order. Null when logged out. */
   public Skill lowestSkill() {
      Skill lowest = null;
      for (final Map.Entry<Skill, Integer> e : levels.entrySet()) {
         if (lowest == null || e.getValue() < levels.get(lowest)) {
            lowest = e.getKey();
         }
      }
      return lowest;
   }

   /** Highest skill below 99, or null if none / logged out. */
   public Skill highestUnmaxedSkill() {
      Skill best = null;
      for (final Map.Entry<Skill, Integer> e : levels.entrySet()) {
         if (e.getValue() < 99 && (best == null || e.getValue() > levels.get(best))) {
            best = e.getKey();
         }
      }
      return best;
   }

   /** Text block sent to the LLM as player context. */
   public String toContext() {
      if (!loggedIn) {
         return "The player is not logged in, so their stats are unknown.";
      }
      final StringBuilder sb = new StringBuilder();
      sb.append("Combat level: ").append(combatLevel).append('\n');
      sb.append("Total level: ").append(totalLevel).append('\n');
      sb.append("Skills: ");
      boolean first = true;
      for (final Map.Entry<Skill, Integer> e : levels.entrySet()) {
         if (!first) {
            sb.append(", ");
         }
         sb.append(e.getKey().getName()).append(' ').append(e.getValue());
         first = false;
      }
      sb.append('\n');
      sb.append("Quests completed: ").append(questsCompleted).append('\n');
      sb.append("Quests in progress: ")
         .append(questsInProgress.isEmpty() ? "none" : String.join(", ", questsInProgress)).append('\n');
      return sb.toString();
   }
}
