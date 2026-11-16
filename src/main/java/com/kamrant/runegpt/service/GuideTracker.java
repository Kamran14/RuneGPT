package com.kamrant.runegpt.service;

import com.kamrant.runegpt.model.GuidePlan;
import com.kamrant.runegpt.model.ItemHoldings;
import com.kamrant.runegpt.model.WorldTarget;
import com.kamrant.runegpt.overlay.MapMarkerImage;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.worldmap.WorldMapPoint;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;

/**
 * "Track guide" mode. Holds the accepted guide, re-checks its items whenever the inventory, equipment or
 * bank changes (green = ready, yellow = equip it, red = missing / in bank), and drives navigation:
 * a world map marker at the location and the in-game highlight on the NPC or location tile.
 * Listeners (the panel) are notified on the client thread with the updated plan, or null when stopped.
 */
@Singleton
public class GuideTracker {

   private final Client client;
   private final ClientThread clientThread;
   private final ItemManager itemManager;
   private final BankCache bankCache;
   private final TargetHighlighter highlighter;
   private final WorldMapPointManager worldMapPointManager;

   // The panel; replaced on every plugin start and cleared on shutdown.
   private volatile Consumer<GuidePlan> listener;
   private volatile GuidePlan active;
   private WorldMapPoint mapPoint;

   @Inject
   public GuideTracker(final Client client, final ClientThread clientThread, final ItemManager itemManager,
      final BankCache bankCache, final TargetHighlighter highlighter, final WorldMapPointManager worldMapPointManager) {
      this.client = client;
      this.clientThread = clientThread;
      this.itemManager = itemManager;
      this.bankCache = bankCache;
      this.highlighter = highlighter;
      this.worldMapPointManager = worldMapPointManager;
   }

   /** Sets (or clears, with null) who is told about changes to the tracked guide. */
   public void setListener(final Consumer<GuidePlan> listener) {
      this.listener = listener;
   }

   /** The tracked guide with up-to-date item statuses, or null. */
   public GuidePlan getActive() {
      return active;
   }

   /** Start tracking (replaces any current guide). Safe from any thread. */
   public void start(final GuidePlan plan) {
      clientThread.invokeLater(() -> {
         clearNavigation();
         active = plan;
         refreshItems();
         setNavigation(plan);
      });
   }

   /** Stop tracking and remove markers and highlights. Safe from any thread. */
   public void stop() {
      clientThread.invokeLater(() -> {
         active = null;
         clearNavigation();
         notifyListeners();
      });
   }

   /** Call on the client thread when the inventory, equipment or bank changes. */
   public void onItemsChanged() {
      if (active != null) {
         refreshItems();
      }
   }

   private void refreshItems() {
      final GuidePlan plan = active;
      if (plan == null) {
         return;
      }
      active = plan.withItems(ItemStatusResolver.resolve(plan.getItems(), liveHoldings()));
      notifyListeners();
   }

   /** Live container contents. Client thread only. */
   private ItemHoldings liveHoldings() {
      if (client.getGameState() != GameState.LOGGED_IN) {
         return ItemHoldings.NONE;
      }
      final Map<String, Integer> inventory = new LinkedHashMap<>();
      final Map<String, Integer> equipment = new LinkedHashMap<>();
      final Map<String, Integer> bank = new LinkedHashMap<>();
      PlayerSnapshot.readContainer(client.getItemContainer(InventoryID.INV), itemManager, inventory);
      PlayerSnapshot.readContainer(client.getItemContainer(InventoryID.WORN), itemManager, equipment);
      bankCache.getItems().forEach((id, qty) -> bank.merge(itemManager.getItemComposition(id).getName(), qty, Integer::sum));
      return new ItemHoldings(inventory, equipment, bank, bankCache.isKnown(), true);
   }

   private void setNavigation(final GuidePlan plan) {
      final WorldTarget npc = plan.getNpc();
      final WorldTarget location = plan.getLocation();
      // Prefer the location's coordinates for the map; fall back to the NPC's.
      final WorldTarget mapTarget = location != null && location.getPoint() != null ? location
         : npc != null && npc.getPoint() != null ? npc : null;

      highlighter.setTarget(npc == null ? null : npc.getName(), mapTarget == null ? null : mapTarget.getPoint());

      if (mapTarget != null) {
         final WorldPoint point = mapTarget.getPoint();
         mapPoint = WorldMapPoint.builder()
            .worldPoint(point)
            .image(MapMarkerImage.create(mapTarget.isApproximate()))
            .tooltip("RuneGPT: " + mapTarget.getName() + (mapTarget.isApproximate() ? " (approximate)" : ""))
            .snapToEdge(true)
            .jumpOnClick(true)
            .target(point)
            .build();
         worldMapPointManager.add(mapPoint);
      }
   }

   private void clearNavigation() {
      highlighter.clear();
      if (mapPoint != null) {
         worldMapPointManager.remove(mapPoint);
         mapPoint = null;
      }
   }

   private void notifyListeners() {
      final Consumer<GuidePlan> l = listener;
      if (l != null) {
         l.accept(active);
      }
   }

   /** Distance/direction from the player to the guide's target, e.g. "~120 tiles north". Client thread. */
   public String directionText() {
      final GuidePlan plan = active;
      if (plan == null || client.getLocalPlayer() == null) {
         return "";
      }
      final WorldTarget t = plan.getLocation() != null && plan.getLocation().getPoint() != null ? plan.getLocation()
         : plan.getNpc() != null && plan.getNpc().getPoint() != null ? plan.getNpc() : null;
      return t == null ? "" : Navigation.describe(client.getLocalPlayer().getWorldLocation(), t.getPoint());
   }

   /** Client thread: plugin shutdown. */
   public void shutdown() {
      active = null;
      clearNavigation();
   }
}
