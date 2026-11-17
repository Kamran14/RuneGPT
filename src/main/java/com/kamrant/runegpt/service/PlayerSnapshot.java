package com.kamrant.runegpt.service;

import com.kamrant.runegpt.model.ItemHoldings;
import com.kamrant.runegpt.model.ItemSource;
import com.kamrant.runegpt.model.OwnedGear;
import com.kamrant.runegpt.model.TargetSource;
import com.kamrant.runegpt.model.TaskSuggestion;
import com.kamrant.runegpt.model.WorldTarget;
import com.kamrant.runegpt.prompt.Prompts;
import com.kamrant.runegpt.quest.QuestJournalCache;
import com.kamrant.runegpt.quest.QuestProgress;
import com.kamrant.runegpt.quest.QuestResolver;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.NPC;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Player;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Scene;
import net.runelite.api.Skill;
import net.runelite.api.Tile;
import net.runelite.api.TileObject;
import net.runelite.api.WorldView;
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
   private static final int MAX_BANK_CHARS = 2400;
   private static final int SCENE_SCAN_RADIUS = 48;
   private static final Skill[] COMBAT_SKILLS = {
      Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE, Skill.RANGED, Skill.MAGIC, Skill.HITPOINTS
   };
   private static final Skill[] MONEY_SKILLS = {
      Skill.SLAYER, Skill.RUNECRAFT, Skill.FARMING, Skill.HUNTER, Skill.THIEVING,
      Skill.MINING, Skill.SMITHING, Skill.FISHING, Skill.WOODCUTTING, Skill.COOKING,
      Skill.HERBLORE, Skill.CRAFTING, Skill.FLETCHING
   };
   private static final Pattern SKILL_TARGET_PATTERN = Pattern.compile(
      "(?:train|level|plan)\\s+([a-z]+)\\s+(?:to\\s+)?(\\d{1,2})\\b|\\b(\\d{1,2})\\s+([a-z]+)\\b",
      Pattern.CASE_INSENSITIVE);

   public static final PlayerSnapshot LOGGED_OUT = new PlayerSnapshot();

   private boolean loggedIn;
   private final Map<Skill, Integer> levels = new EnumMap<>(Skill.class);
   private int combatLevel;
   private int totalLevel;
   private final List<String> questsInProgress = new ArrayList<>();
   private final List<String> questsCompletedList = new ArrayList<>();
   private final List<String> questsNotStarted = new ArrayList<>();
   private final Map<String, QuestState> questStates = new LinkedHashMap<>();
   private final Map<String, Double> questRatios = new LinkedHashMap<>();
   private final Map<String, String> questStageTexts = new LinkedHashMap<>();
   private final Map<String, Map<String, Boolean>> questSubTasks = new LinkedHashMap<>();
   private QuestJournalCache journalCache;
   private int questsCompleted;
   private WorldPoint location;
   private final List<String> nearbyNpcs = new ArrayList<>();
   // Lowercase entity name -> closest loaded NPC or TileObject in the current scene.
   private final Map<String, WorldTarget> sceneEntities = new LinkedHashMap<>();
   // Item name -> quantity. Bank is empty (and bankKnown false) until the bank has been opened.
   private final Map<String, Integer> inventory = new LinkedHashMap<>();
   private final Map<String, Integer> equipment = new LinkedHashMap<>();
   private final Map<String, Integer> bank = new LinkedHashMap<>();
   // Bank item name -> total GE value, and the names of equipable bank items: used to decide which bank items
   // the AI sees first when the whole bank doesn't fit.
   private final Map<String, Long> bankValue = new HashMap<>();
   private final Set<String> bankGear = new HashSet<>();
   private final List<OwnedGear> ownedGear = new ArrayList<>();
   private long cash;
   private boolean bankKnown;
   private boolean bankFromEarlierSession;
   private long bankSeenAt;

   private PlayerSnapshot() {
   }

   /** Call on the client thread. */
   public static PlayerSnapshot capture(final Client client, final ItemManager itemManager, final BankCache bankCache,
      final QuestJournalCache journalCache) {
      if (client.getGameState() != GameState.LOGGED_IN) {
         return LOGGED_OUT;
      }
      final PlayerSnapshot s = new PlayerSnapshot();
      s.loggedIn = true;
      s.journalCache = journalCache;
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
            s.questsCompletedList.add(quest.getName());
         } else {
            s.questsNotStarted.add(quest.getName());
         }
      }

      if (journalCache != null) {
         journalCache.updateFromClient(client);
      }
      QuestProgress.captureFromClient(client, s.questStates, s.questRatios, s.questStageTexts, s.questSubTasks);

      // Nearby NPCs and scene objects, closest first: strong hints about where the player is and exact tiles for "nearest X".
      if (local != null) {
         final List<NPC> npcs = new ArrayList<>();
         client.getTopLevelWorldView().npcs().forEach(npcs::add);
         final WorldPoint here = local.getWorldLocation();
         npcs.sort((x, y) -> Integer.compare(x.getWorldLocation().distanceTo(here), y.getWorldLocation().distanceTo(here)));
         final Set<String> names = new LinkedHashSet<>();
         for (final NPC npc : npcs) {
            final String n = npc.getName();
            if (n != null && !n.equals("null") && !n.isEmpty()) {
               if (names.size() < MAX_NEARBY_NPCS) {
                  names.add(n);
               }
               s.sceneEntities.putIfAbsent(n.toLowerCase(Locale.ROOT),
                  new WorldTarget(n, npc.getWorldLocation(), TargetSource.WIKI));
            }
         }
         s.nearbyNpcs.addAll(names);
         captureSceneObjects(client, here, s.sceneEntities);
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
      s.bankFromEarlierSession = bankCache.isFromEarlierSession();
      s.bankSeenAt = bankCache.getSeenAt();
      for (final Map.Entry<Integer, Integer> e : bankCache.getItems().entrySet()) {
         final int id = e.getKey();
         final int qty = e.getValue();
         final ItemComposition comp = itemManager.getItemComposition(id);
         if (comp.getPlaceholderTemplateId() != -1) {
            continue;
         }
         s.bank.merge(comp.getName(), qty, Integer::sum);
         s.bankValue.merge(comp.getName(), (long) itemManager.getItemPrice(itemManager.canonicalize(id)) * qty,
            Long::sum);
         s.cash += cashOf(id, qty);
         addGear(id, comp, itemManager, ItemSource.BANK, gearById);
      }
      for (final OwnedGear gear : gearById.values()) {
         if (gear.getSource() == ItemSource.BANK) {
            s.bankGear.add(gear.getName());
         }
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

   private static void captureSceneObjects(final Client client, final WorldPoint here,
      final Map<String, WorldTarget> out) {
      if (here == null) {
         return;
      }
      final WorldView wv = client.getTopLevelWorldView();
      final Scene scene = wv == null ? null : wv.getScene();
      if (scene == null || scene.getTiles() == null) {
         return;
      }
      final int plane = wv.getPlane();
      final Tile[][][] tiles = scene.getTiles();
      if (plane < 0 || plane >= tiles.length || tiles[plane] == null) {
         return;
      }
      final Map<Integer, String> idToName = new HashMap<>();
      final Map<String, Integer> bestDist = new HashMap<>();
      final Tile[][] planeTiles = tiles[plane];
      for (int x = 0; x < planeTiles.length; x++) {
         for (int y = 0; y < planeTiles[x].length; y++) {
            final Tile tile = planeTiles[x][y];
            if (tile == null) {
               continue;
            }
            final WorldPoint wp = tile.getWorldLocation();
            if (wp == null) {
               continue;
            }
            final int dist = wp.distanceTo2D(here);
            if (dist > SCENE_SCAN_RADIUS) {
               continue;
            }
            if (tile.getGameObjects() != null) {
               for (final GameObject go : tile.getGameObjects()) {
                  if (go != null) {
                     recordObject(client, go.getId(), wp, dist, idToName, bestDist, out);
                  }
               }
            }
            for (final TileObject to : new TileObject[] { tile.getWallObject(), tile.getGroundObject(), tile.getDecorativeObject() }) {
               if (to != null) {
                  recordObject(client, to.getId(), wp, dist, idToName, bestDist, out);
               }
            }
         }
      }
   }

   private static void recordObject(final Client client, final int id, final WorldPoint wp, final int dist,
      final Map<Integer, String> idToName, final Map<String, Integer> bestDist, final Map<String, WorldTarget> out) {
      final String name = idToName.computeIfAbsent(id, k -> {
         try {
            ObjectComposition comp = client.getObjectDefinition(k);
            if (comp != null && comp.getImpostorIds() != null) {
               final ObjectComposition imp = comp.getImpostor();
               if (imp != null) {
                  comp = imp;
               }
            }
            final String n = comp == null ? null : comp.getName();
            return n == null || n.equals("null") || n.isEmpty() ? "" : n;
         } catch (final RuntimeException e) {
            return "";
         }
      });
      if (name.length() < 3) {
         return;
      }
      final String key = name.toLowerCase(Locale.ROOT);
      final Integer prev = bestDist.get(key);
      if (prev == null || dist < prev) {
         bestDist.put(key, dist);
         out.put(key, new WorldTarget(name, wp, TargetSource.WIKI));
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

   public int getTotalLevel() {
      return totalLevel;
   }

   public int getQuestsCompleted() {
      return questsCompleted;
   }

   public List<String> getQuestsInProgress() {
      return Collections.unmodifiableList(questsInProgress);
   }

   public List<String> getQuestsNotStarted() {
      return Collections.unmodifiableList(questsNotStarted);
   }

   /** State of a quest by its exact name, or null if unknown. */
   public QuestState questState(final String questName) {
      return questStates.get(questName);
   }

   /** True when the player is logged in and has completed {@code quest}. */
   public boolean isQuestCompleted(final Quest quest) {
      return quest != null && isQuestCompleted(quest.getName());
   }

   /** True when the player is logged in and has completed {@code questName}. */
   public boolean isQuestCompleted(final String questName) {
      return loggedIn && questName != null && questStates.get(questName) == QuestState.FINISHED;
   }

   /** True when logged out, or when at least one quest is still in progress or not started. */
   public boolean hasIncompleteQuests() {
      return !loggedIn || !questsInProgress.isEmpty() || !questsNotStarted.isEmpty();
   }

   /** Count of quests that are IN_PROGRESS or NOT_STARTED (or Quest.values().length when logged out). */
   public int incompleteQuestCount() {
      return !loggedIn ? Quest.values().length : questsInProgress.size() + questsNotStarted.size();
   }

   /** True when logged in and all primary combat skills (Attack, Strength, Defence, Ranged, Magic, Hitpoints) are 99+. */
   public boolean isAllCombatMaxed() {
      if (!loggedIn) {
         return false;
      }
      for (final Skill s : COMBAT_SKILLS) {
         if (level(s) < 99) {
            return false;
         }
      }
      return true;
   }

   public WorldPoint getLocation() {
      return location;
   }

   /** Sets the player's location and optional nearby scene entities (for unit testing). */
   public PlayerSnapshot withLocation(final WorldPoint location) {
      this.location = location;
      return this;
   }

   /** Adds a simulated nearby scene entity (for unit testing). */
   public PlayerSnapshot withSceneEntity(final String name, final WorldPoint point) {
      if (name != null && point != null) {
         sceneEntities.put(name.toLowerCase(Locale.ROOT), new WorldTarget(name, point, TargetSource.WIKI));
      }
      return this;
   }

   /**
    * Returns the closest loaded NPC or interactable object in the current scene matching {@code query},
    * or null if none is loaded nearby.
    */
   public WorldTarget nearestSceneEntity(final String query) {
      if (query == null || query.isEmpty() || sceneEntities.isEmpty()) {
         return null;
      }
      final String q = query.trim().toLowerCase(Locale.ROOT);
      final String singular = q.endsWith("s") && q.length() > 3 ? q.substring(0, q.length() - 1) : q;
      final WorldTarget exact = sceneEntities.get(q);
      if (exact != null) {
         return exact;
      }
      final WorldTarget exactSing = sceneEntities.get(singular);
      if (exactSing != null) {
         return exactSing;
      }
      WorldTarget best = null;
      int bestDist = Integer.MAX_VALUE;
      for (final Map.Entry<String, WorldTarget> e : sceneEntities.entrySet()) {
         final String k = e.getKey();
         if (k.contains(singular) || singular.contains(k)) {
            final WorldPoint p = e.getValue().getPoint();
            final int d = location == null || p == null ? 0 : p.distanceTo2D(location);
            if (best == null || d < bestDist) {
               best = e.getValue();
               bestDist = d;
            }
         }
      }
      return best;
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

   /** True when the bank list was restored from an earlier session (may be out of date). */
   public boolean isBankFromEarlierSession() {
      return bankFromEarlierSession;
   }

   public boolean isBankKnown() {
      return bankKnown;
   }

   public ItemHoldings holdings() {
      return new ItemHoldings(inventory, equipment, bank, bankKnown, loggedIn);
   }

   /**
    * Detailed in-game stage breakdown for any quest (varbit/varp stage vs game-cache completion threshold,
    * sub-task varbits, RFD subquests, and cached in-game Quest Journal strikethrough/active lines).
    */
   public String questProgressDetails(final String questName) {
      if (questName == null || questName.isEmpty()) {
         return "";
      }
      final StringBuilder sb = new StringBuilder();
      final String stage = questStageTexts.get(questName);
      if (stage != null && !stage.isEmpty()) {
         sb.append("In-game progress for ").append(questName)
            .append(" (from game varbits; COMPLETED stages are already done): ")
            .append(stage).append('\n');
      }
      if (questName.toLowerCase(Locale.ROOT).startsWith("recipe for disaster")) {
         sb.append("Recipe for Disaster subquest states:\n");
         questStates.forEach((name, state) -> {
            if (name.startsWith("Recipe for Disaster - ")) {
               sb.append("- ").append(name).append(": ")
                  .append(state.name().toLowerCase(Locale.ROOT).replace('_', ' ')).append('\n');
            }
         });
      }
      if (journalCache != null) {
         final List<String> done = journalCache.completedLines(questName);
         final List<String> active = journalCache.activeLines(questName);
         if (!done.isEmpty()) {
            sb.append("Completed in-game Quest Journal lines: ")
               .append(String.join(" | ", done)).append('\n');
         }
         if (!active.isEmpty()) {
            sb.append("Active in-game Quest Journal lines: ")
               .append(String.join(" | ", active)).append('\n');
         }
      }
      return sb.toString();
   }

   /**
    * Dynamically resolves the 0-based index of the current active step in {@code steps} for any quest,
    * combining the AI's {@code aiStepOneBased}, cached Quest Journal lines, sub-varbits, owned quest items,
    * and the quest's game-cache progress ratio.
    */
   public int resolveCurrentStep(final String title, final List<String> steps, final int aiStepOneBased) {
      return QuestProgress.resolveCurrentStep(title, steps, aiStepOneBased,
         questStates, questRatios, questSubTasks, journalCache, holdings());
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

   /** Highest skill below 99 excluding {@code exclude}, or {@link #highestUnmaxedSkill()} if only one unmaxed skill remains. */
   public Skill highestUnmaxedSkillExcept(final Skill exclude) {
      Skill best = null;
      for (final Map.Entry<Skill, Integer> e : levels.entrySet()) {
         if (e.getKey() != exclude && e.getValue() < 99 && (best == null || e.getValue() > levels.get(best))) {
            best = e.getKey();
         }
      }
      return best != null ? best : highestUnmaxedSkill();
   }

   /** Highest-level profitable skill the player has (even at 99), or null when logged out. */
   public Skill bestMoneySkill() {
      if (!loggedIn) {
         return null;
      }
      Skill best = null;
      int bestLvl = -1;
      for (final Skill s : MONEY_SKILLS) {
         final int lvl = level(s);
         if (lvl > bestLvl) {
            bestLvl = lvl;
            best = s;
         }
      }
      return best;
   }

   /**
    * Removes task suggestions that are already completed by the player (e.g. finished quests or skill targets
    * the player has already reached).
    */
   public List<TaskSuggestion> filterValidSuggestions(final List<TaskSuggestion> suggestions) {
      if (!loggedIn || suggestions == null || suggestions.isEmpty()) {
         return suggestions;
      }
      final List<TaskSuggestion> out = new ArrayList<>(suggestions.size());
      for (final TaskSuggestion s : suggestions) {
         if (isSuggestionValid(s)) {
            out.add(s);
         }
      }
      return out;
   }

   private boolean isSuggestionValid(final TaskSuggestion s) {
      if (s == null || s.getTitle() == null || s.getTitle().isEmpty()) {
         return false;
      }
      final String rawTitle = s.getTitle().trim();
      final String cleanedQuestCandidate = rawTitle
         .replaceFirst("(?i)^(?:complete|finish|do|start|continue|quest:?|miniquest:?)\\s+", "")
         .trim();
      final String matchedQuest = QuestResolver.resolve(cleanedQuestCandidate).getQuestName();
      if (matchedQuest != null && isQuestCompleted(matchedQuest)) {
         return false;
      }
      final Matcher m = SKILL_TARGET_PATTERN.matcher(rawTitle);
      if (m.find()) {
         final String skillName = m.group(1) != null ? m.group(1) : m.group(4);
         final String targetStr = m.group(2) != null ? m.group(2) : m.group(3);
         final Skill skill = skillByName(skillName);
         if (skill != null && targetStr != null) {
            try {
               final int target = Integer.parseInt(targetStr);
               if (level(skill) >= target) {
                  return false;
               }
            } catch (final NumberFormatException ignored) {
            }
         }
      }
      return true;
   }

   /** Looks up a {@link Skill} by name (case-insensitive), or null if none matches. */
   public static Skill skillByName(final String name) {
      if (name == null || name.isEmpty()) {
         return null;
      }
      for (final Skill s : Skill.values()) {
         if (s.getName().equalsIgnoreCase(name.trim())) {
            return s;
         }
      }
      return null;
   }

   /** Builds an in-memory snapshot with specific stats and quest states for testing progression logic. */
   public static PlayerSnapshot ofStats(final int combatLevel, final Map<Skill, Integer> skillLevels,
      final Map<Quest, QuestState> quests) {
      final PlayerSnapshot s = new PlayerSnapshot();
      s.loggedIn = true;
      s.combatLevel = combatLevel;
      int total = 0;
      for (final Skill skill : Skill.values()) {
         final int lvl = skillLevels != null && skillLevels.containsKey(skill) ? skillLevels.get(skill) : 1;
         s.levels.put(skill, lvl);
         total += lvl;
      }
      s.totalLevel = total;
      for (final Quest q : Quest.values()) {
         final QuestState st = quests != null && quests.containsKey(q) ? quests.get(q) : QuestState.NOT_STARTED;
         s.questStates.put(q.getName(), st);
         if (st == QuestState.IN_PROGRESS) {
            s.questsInProgress.add(q.getName());
         } else if (st == QuestState.FINISHED) {
            s.questsCompleted++;
            s.questsCompletedList.add(q.getName());
         } else {
            s.questsNotStarted.add(q.getName());
         }
      }
      return s;
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
      return toContext("", false);
   }

   /**
    * Player context for the LLM. With {@code fullBank} the whole bank is listed; otherwise it's trimmed to the
    * most useful items (see {@link #bankText(String, boolean)}).
    */
   public String toContext(final String question, final boolean fullBank) {
      if (!loggedIn) {
         return Prompts.LOGGED_OUT_CONTEXT;
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
      if (questsInProgress.isEmpty() && questsNotStarted.isEmpty()) {
         sb.append("Quest status: ALL quests are FINISHED. Never suggest doing or completing any quest.\n");
      } else if (questsCompleted == 0) {
         sb.append("Completed quests: none (all quests except any in-progress ones are NOT_STARTED)\n");
      } else if (questsCompletedList.size() <= questsNotStarted.size()) {
         sb.append("Completed quests (already FINISHED — never suggest these): ")
            .append(String.join(", ", questsCompletedList)).append('\n');
      } else {
         sb.append("Quests not started yet (all other quests not in progress are already FINISHED — never suggest finished quests): ")
            .append(String.join(", ", questsNotStarted)).append('\n');
      }
      if (questsInProgress.isEmpty()) {
         sb.append("Quests in progress: none\n");
      } else {
         final List<String> withStages = new ArrayList<>();
         for (final String q : questsInProgress) {
            final String st = questStageTexts.get(q);
            withStages.add(st != null && !st.contains("\n") ? q + " (" + st + ")" : q);
         }
         sb.append("Quests in progress: ").append(String.join(", ", withStages)).append('\n');
         for (final String q : questsInProgress) {
            final String st = questStageTexts.get(q);
            if (st != null && st.contains("\n")) {
               sb.append(questProgressDetails(q));
            }
         }
      }
      sb.append("Location: ").append(locationText()).append('\n');
      sb.append("Wearing: ").append(items(equipment)).append('\n');
      sb.append("Inventory: ").append(items(inventory)).append('\n');
      sb.append("Bank").append(bankKnown && bankFromEarlierSession ? " (" + savedBankAge() + ")" : "").append(": ")
         .append(bankKnown ? bankText(question, fullBank) : "unknown (not opened yet)").append('\n');
      return sb.toString();
   }

   /**
    * The bank as text, most useful items first: items named in the question, then equipable gear (most valuable
    * first), then everything else by GE value. Unless {@code full}, it stops at {@link #MAX_BANK_CHARS} (for
    * token-limited providers) and summarises the rest as a count.
    */
   public String bankText(final String question, final boolean full) {
      if (bank.isEmpty()) {
         return "nothing";
      }
      final String q = question == null ? "" : question.toLowerCase(Locale.ROOT);
      final List<String> names = new ArrayList<>(bank.keySet());
      names.sort((a, b) -> {
         final int byMention = Boolean.compare(mentioned(q, b), mentioned(q, a));
         if (byMention != 0) {
            return byMention;
         }
         final int byGear = Boolean.compare(bankGear.contains(b), bankGear.contains(a));
         if (byGear != 0) {
            return byGear;
         }
         return Long.compare(bankValue.getOrDefault(b, 0L), bankValue.getOrDefault(a, 0L));
      });
      final StringBuilder sb = new StringBuilder();
      int shown = 0;
      for (final String name : names) {
         final int qty = bank.get(name);
         final String part = qty > 1 ? name + " x" + qty : name;
         if (!full && sb.length() > 0 && sb.length() + part.length() + 2 > MAX_BANK_CHARS && !mentioned(q, name)) {
            break;
         }
         if (sb.length() > 0) {
            sb.append(", ");
         }
         sb.append(part);
         shown++;
      }
      if (shown < names.size()) {
         sb.append(" ... and ").append(names.size() - shown)
            .append(" more lower-value items (not listed; ask if you need to know about a specific item)");
      }
      return sb.toString();
   }

   /** e.g. "saved 3 days ago, may have changed since". */
   private String savedBankAge() {
      final long hours = Math.max(0, (System.currentTimeMillis() - bankSeenAt) / 3_600_000L);
      final String age = hours < 1 ? "less than an hour" : hours < 48 ? hours + " hours" : hours / 24 + " days";
      return "as last seen " + age + " ago, may have changed since";
   }

   private static boolean mentioned(final String questionLower, final String itemName) {
      return itemName.length() >= 4 && !questionLower.isEmpty()
         && questionLower.contains(itemName.toLowerCase(Locale.ROOT));
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
