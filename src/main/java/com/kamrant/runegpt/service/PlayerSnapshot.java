package com.kamrant.runegpt.service;

import com.kamrant.runegpt.model.ItemHoldings;
import com.kamrant.runegpt.model.ItemSource;
import com.kamrant.runegpt.model.OwnedGear;
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
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;

/**
 * Snapshot of the player's progress, gear and surroundings, captured on the client thread (quest
 * state lookups run client scripts; item names need the item cache). Treat as read-only once captured.
 */
public final class PlayerSnapshot {

   private static final int MAX_NEARBY_NPCS = 15;
   private static final int MAX_BANK_CHARS = 1500;

   public static final PlayerSnapshot LOGGED_OUT = new PlayerSnapshot();

   private boolean loggedIn;
   private final Map<Skill, Integer> levels = new EnumMap<>(Skill.class);
   private int combatLevel;
   private int totalLevel;
   private final List<String> questsInProgress = new ArrayList<>();
   private final Map<String, QuestState> questStates = new LinkedHashMap<>();
   private int questsCompleted;
   private WorldPoint location;
   private final List<String> nearbyNpcs = new ArrayList<>();
   // Item name -> quantity. Bank is empty (and bankKnown false) until the bank has been opened.
   private final Map<String, Integer> inventory = new LinkedHashMap<>();
   private final Map<String, Integer> equipment = new LinkedHashMap<>();
   private final Map<String, Integer> bank = new LinkedHashMap<>();
   private final List<OwnedGear> ownedGear = new ArrayList<>();
   private long cash;
   private boolean bankKnown;

   private PlayerSnapshot() {
   }

   /** Call on the client thread. */
   public static PlayerSnapshot capture(final Client client, final ItemManager itemManager, final BankCache bankCache) {
      if (client.getGameState() != GameState.LOGGED_IN) {
         return LOGGED_OUT;
      }
      final PlayerSnapshot s = new PlayerSnapshot();
      s.loggedIn = true;
      for (final Skill skill : Skill.values()) {
         s.levels.put(skill, client.getRealSkillLevel(skill));
      }
      s.totalLevel = client.getTotalLevel();

      final Player local = client.getLocalPlayer();
      if (local != null) {
         s.combatLevel = local.getCombatLevel();
         s.location = local.getWorldLocation();
      }

      for (final Quest quest : Quest.values()) {
         final QuestState state = quest.getState(client);
         s.questStates.put(quest.getName(), state);
         if (state == QuestState.IN_PROGRESS) {
            s.questsInProgress.add(quest.getName());
         } else if (state == QuestState.FINISHED) {
            s.questsCompleted++;
         }
      }

      // Nearby NPC names, closest first: strong hints about where the player is.
      if (local != null) {
         final List<NPC> npcs = new ArrayList<>();
         client.getTopLevelWorldView().npcs().forEach(npcs::add);
         final WorldPoint here = local.getWorldLocation();
         npcs.sort((x, y) -> Integer.compare(x.getWorldLocation().distanceTo(here), y.getWorldLocation().distanceTo(here)));
         final Set<String> names = new LinkedHashSet<>();
         for (final NPC npc : npcs) {
            if (npc.getName() != null && !npc.getName().equals("null") && names.size() < MAX_NEARBY_NPCS) {
               names.add(npc.getName());
            }
         }
         s.nearbyNpcs.addAll(names);
      }

      final Map<Integer, OwnedGear> gearById = new LinkedHashMap<>();
      final ItemContainer worn = client.getItemContainer(InventoryID.WORN);
      final ItemContainer inv = client.getItemContainer(InventoryID.INV);
      readContainer(worn, itemManager, s.equipment);
      readContainer(inv, itemManager, s.inventory);
      readGear(worn, itemManager, ItemSource.WORN, gearById);
      readGear(inv, itemManager, ItemSource.INVENTORY, gearById);
      s.cash += countCash(inv);

      s.bankKnown = bankCache.isKnown();
      for (final Map.Entry<Integer, Integer> e : bankCache.getItems().entrySet()) {
         final int id = e.getKey();
         final int qty = e.getValue();
         final ItemComposition comp = itemManager.getItemComposition(id);
         if (comp.getPlaceholderTemplateId() != -1) {
            continue;
         }
         s.bank.merge(comp.getName(), qty, Integer::sum);
         s.cash += cashOf(id, qty);
         addGear(id, comp, itemManager, ItemSource.BANK, gearById);
      }
      s.ownedGear.addAll(gearById.values());
      return s;
   }

   /** Item names and quantities in a container. Call on the client thread. */
   public static void readContainer(final ItemContainer container, final ItemManager itemManager,
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

   private static void readGear(final ItemContainer container, final ItemManager itemManager, final ItemSource source,
      final Map<Integer, OwnedGear> out) {
      if (container == null) {
         return;
      }
      for (final Item item : container.getItems()) {
         if (item.getId() > 0 && item.getQuantity() > 0) {
            addGear(item.getId(), itemManager.getItemComposition(item.getId()), itemManager, source, out);
         }
      }
   }

   private static void addGear(final int itemId, final ItemComposition comp, final ItemManager itemManager,
      final ItemSource source, final Map<Integer, OwnedGear> out) {
      if (comp.getName() == null || comp.getNote() != -1 || comp.getPlaceholderTemplateId() != -1) {
         return;
      }
      final int canonical = itemManager.canonicalize(itemId);
      if (out.containsKey(canonical)) {
         return;
      }
      final ItemStats stats = itemManager.getItemStats(canonical);
      if (stats != null && stats.isEquipable() && stats.getEquipment() != null) {
         out.put(canonical, new OwnedGear(canonical, comp.getName(), stats.getEquipment(), source));
      }
   }

   private static long countCash(final ItemContainer container) {
      if (container == null) {
         return 0;
      }
      long total = 0;
      for (final Item item : container.getItems()) {
         total += cashOf(item.getId(), item.getQuantity());
      }
      return total;
   }

   private static long cashOf(final int id, final int qty) {
      if (qty <= 0) {
         return 0;
      }
      if (id == ItemID.COINS) {
         return qty;
      }
      if (id == ItemID.PLATINUM) {
         return qty * 1000L;
      }
      return 0;
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
      return Collections.unmodifiableList(questsInProgress);
   }

   /** State of a quest by its exact name, or null if unknown. */
   public QuestState questState(final String questName) {
      return questStates.get(questName);
   }

   public WorldPoint getLocation() {
      return location;
   }

   public List<String> getNearbyNpcs() {
      return Collections.unmodifiableList(nearbyNpcs);
   }

   public Map<String, Integer> getInventory() {
      return Collections.unmodifiableMap(inventory);
   }

   public Map<String, Integer> getEquipment() {
      return Collections.unmodifiableMap(equipment);
   }

   public Map<String, Integer> getBank() {
      return Collections.unmodifiableMap(bank);
   }

   /** Equipable items the player has worn, in inventory or in the bank (worn first). */
   public List<OwnedGear> getOwnedGear() {
      return Collections.unmodifiableList(ownedGear);
   }

   /** Total GP across inventory and (known) bank, including platinum tokens. */
   public long getCash() {
      return cash;
   }

   public boolean isBankKnown() {
      return bankKnown;
   }

   public ItemHoldings holdings() {
      return new ItemHoldings(inventory, equipment, bank, bankKnown, loggedIn);
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
      sb.append("Location: ").append(locationText()).append('\n');
      sb.append("Wearing: ").append(items(equipment)).append('\n');
      sb.append("Inventory: ").append(items(inventory)).append('\n');
      sb.append("Bank: ").append(bankKnown ? capped(items(bank)) : "unknown (not opened this session)").append('\n');
      return sb.toString();
   }

   /** Keeps a big bank from blowing the free-tier token budgets. */
   private static String capped(final String s) {
      return s.length() <= MAX_BANK_CHARS ? s : s.substring(0, MAX_BANK_CHARS) + "... (more items)";
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
