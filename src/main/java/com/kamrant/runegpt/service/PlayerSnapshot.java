package com.kamrant.runegpt.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.game.ItemManager;

/**
 * Immutable snapshot of the player's progress, gear and surroundings. Must be captured on the
 * client thread (quest state lookups run client scripts; item names need the item cache).
 */
public final class PlayerSnapshot {

   // Container ids (gameval InventoryID): inventory, worn equipment.
   private static final int INVENTORY = 93;
   private static final int EQUIPMENT = 94;
   private static final int MAX_NEARBY_NPCS = 15;

   public static final PlayerSnapshot LOGGED_OUT = new PlayerSnapshot();

   private final boolean loggedIn;
   private final Map<Skill, Integer> levels;
   private final int combatLevel;
   private final int totalLevel;
   private final List<String> questsInProgress;
   private final Map<String, QuestState> questStates;
   private final int questsCompleted;
   private final WorldPoint location;
   private final List<String> nearbyNpcs;
   // Item name -> quantity. Bank is empty (and bankKnown false) until the bank has been opened.
   private final Map<String, Integer> inventory;
   private final Map<String, Integer> equipment;
   private final Map<String, Integer> bank;
   private final boolean bankKnown;

   private PlayerSnapshot() {
      loggedIn = false;
      levels = Collections.emptyMap();
      combatLevel = 0;
      totalLevel = 0;
      questsInProgress = Collections.emptyList();
      questStates = Collections.emptyMap();
      questsCompleted = 0;
      location = null;
      nearbyNpcs = Collections.emptyList();
      inventory = Collections.emptyMap();
      equipment = Collections.emptyMap();
      bank = Collections.emptyMap();
      bankKnown = false;
   }

   private PlayerSnapshot(final Builder b) {
      loggedIn = true;
      levels = Collections.unmodifiableMap(b.levels);
      combatLevel = b.combatLevel;
      totalLevel = b.totalLevel;
      questsInProgress = Collections.unmodifiableList(b.questsInProgress);
      questStates = Collections.unmodifiableMap(b.questStates);
      questsCompleted = b.questsCompleted;
      location = b.location;
      nearbyNpcs = Collections.unmodifiableList(b.nearbyNpcs);
      inventory = Collections.unmodifiableMap(b.inventory);
      equipment = Collections.unmodifiableMap(b.equipment);
      bank = Collections.unmodifiableMap(b.bank);
      bankKnown = b.bankKnown;
   }

   private static final class Builder {
      final Map<Skill, Integer> levels = new EnumMap<>(Skill.class);
      int combatLevel;
      int totalLevel;
      final List<String> questsInProgress = new ArrayList<>();
      final Map<String, QuestState> questStates = new LinkedHashMap<>();
      int questsCompleted;
      WorldPoint location;
      final List<String> nearbyNpcs = new ArrayList<>();
      final Map<String, Integer> inventory = new LinkedHashMap<>();
      final Map<String, Integer> equipment = new LinkedHashMap<>();
      final Map<String, Integer> bank = new LinkedHashMap<>();
      boolean bankKnown;
   }

   /** Call on the client thread. */
   public static PlayerSnapshot capture(final Client client, final ItemManager itemManager, final BankCache bankCache) {
      if (client.getGameState() != GameState.LOGGED_IN) {
         return LOGGED_OUT;
      }
      final Builder b = new Builder();
      for (final Skill skill : Skill.values()) {
         b.levels.put(skill, client.getRealSkillLevel(skill));
      }
      b.totalLevel = client.getTotalLevel();

      final Player local = client.getLocalPlayer();
      if (local != null) {
         b.combatLevel = local.getCombatLevel();
         b.location = local.getWorldLocation();
      }

      for (final Quest quest : Quest.values()) {
         final QuestState state = quest.getState(client);
         b.questStates.put(quest.getName(), state);
         if (state == QuestState.IN_PROGRESS) {
            b.questsInProgress.add(quest.getName());
         } else if (state == QuestState.FINISHED) {
            b.questsCompleted++;
         }
      }

      // Nearby NPC names, closest first: strong hints about where the player is.
      final Set<String> npcNames = new LinkedHashSet<>();
      if (local != null) {
         final List<NPC> npcs = new ArrayList<>(client.getNpcs());
         final WorldPoint here = local.getWorldLocation();
         npcs.sort((x, y) -> Integer.compare(x.getWorldLocation().distanceTo(here), y.getWorldLocation().distanceTo(here)));
         for (final NPC npc : npcs) {
            if (npc.getName() != null && !npc.getName().equals("null") && npcNames.size() < MAX_NEARBY_NPCS) {
               npcNames.add(npc.getName());
            }
         }
      }
      b.nearbyNpcs.addAll(npcNames);

      addContainer(client.getItemContainer(INVENTORY), itemManager, b.inventory);
      addContainer(client.getItemContainer(EQUIPMENT), itemManager, b.equipment);
      b.bankKnown = bankCache.isKnown();
      for (final Map.Entry<Integer, Integer> e : bankCache.getItems().entrySet()) {
         b.bank.merge(itemManager.getItemComposition(e.getKey()).getName(), e.getValue(), Integer::sum);
      }
      return new PlayerSnapshot(b);
   }

   private static void addContainer(final ItemContainer container, final ItemManager itemManager,
      final Map<String, Integer> out) {
      if (container == null) {
         return;
      }
      for (final Item item : container.getItems()) {
         if (item.getId() > 0 && item.getQuantity() > 0) {
            out.merge(itemManager.getItemComposition(item.getId()).getName(), item.getQuantity(), Integer::sum);
         }
      }
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

   /** State of a quest by its exact name, or null if unknown. */
   public QuestState questState(final String questName) {
      return questStates.get(questName);
   }

   public List<String> getNearbyNpcs() {
      return nearbyNpcs;
   }

   public Map<String, Integer> getInventory() {
      return inventory;
   }

   public Map<String, Integer> getEquipment() {
      return equipment;
   }

   public Map<String, Integer> getBank() {
      return bank;
   }

   public boolean isBankKnown() {
      return bankKnown;
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

   /** Player position as text for the LLM. */
   public String locationText() {
      if (location == null) {
         return "unknown";
      }
      return "x=" + location.getX() + ", y=" + location.getY() + ", plane=" + location.getPlane()
         + (nearbyNpcs.isEmpty() ? "" : " (nearby NPCs: " + String.join(", ", nearbyNpcs) + ")");
   }

   /** Text block sent to the LLM as player context for general questions. */
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
      sb.append("Wearing: ").append(items(equipment)).append('\n');
      sb.append("Inventory: ").append(items(inventory)).append('\n');
      return sb.toString();
   }

   public static String items(final Map<String, Integer> items) {
      if (items.isEmpty()) {
         return "nothing";
      }
      final List<String> parts = new ArrayList<>();
      items.forEach((name, qty) -> parts.add(qty > 1 ? name + " x" + qty : name));
      return String.join(", ", parts);
   }
}
