package com.kamrant.runegpt.service;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.kamrant.runegpt.model.GuideItem;
import com.kamrant.runegpt.model.GuideItemKind;
import com.kamrant.runegpt.model.GuidePlan;
import com.kamrant.runegpt.model.ItemHoldings;
import com.kamrant.runegpt.model.ItemStatus;
import com.kamrant.runegpt.model.TargetSource;
import com.kamrant.runegpt.model.WikiItem;
import com.kamrant.runegpt.model.WorldTarget;
import com.kamrant.runegpt.wiki.WikiLookup;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;

/**
 * Turns the "guide" object from an AI reply into a {@link GuidePlan} where everything has been checked
 * against the OSRS Wiki: items must have an item page (gear that can't be worn becomes a carry item),
 * NPCs must have a page, and coordinates come from the wiki first, then the AI's estimate (marked
 * approximate). Anything that doesn't exist is dropped and counted.
 */
@Slf4j
@Singleton
public class GuideVerifier {

   private static final int MAX_STEPS = 6;
   private static final int MAX_ITEMS = 12;
   private static final long LOOKUP_TIMEOUT_SECONDS = 20;
   private static final int MAX_NPC_LOCATION_DISTANCE = 200;

   private final WikiLookup wiki;
   private final PriceService prices;
   // Created on first use and shut down by the plugin's shutDown(); this class is a Guice singleton
   // that outlives a plugin stop/start, so a stopped pool is replaced the next time it's needed.
   private ExecutorService pool;

   @Inject
   public GuideVerifier(final WikiLookup wiki, final PriceService prices) {
      this.wiki = wiki;
      this.prices = prices;
   }

   /** Blocking (wiki lookups). Returns null if the guide is empty after verification. */
   public GuidePlan verify(final JsonObject guide, final PlayerSnapshot snapshot) {
      if (guide == null) {
         return null;
      }
      final ItemHoldings holdings = snapshot.holdings();
      final String title = ResponseParser.str(guide, "title");
      final List<String> steps = ResponseParser.strings(guide, "steps", MAX_STEPS);
      final int currentStep = snapshot.resolveCurrentStep(title, steps, ResponseParser.intOr(guide, "current_step", 1));

      // Requested items, de-duplicated by name: gear first so a worn item isn't also listed as carry.
      final Map<String, GuideItem> requested = new LinkedHashMap<>();
      for (final JsonElement el : ResponseParser.array(guide, "recommended_gear")) {
         final GuideItem item = requestedItem(el, GuideItemKind.EQUIP);
         if (item != null) {
            requested.putIfAbsent(item.getName().toLowerCase(Locale.ROOT), item);
         }
      }
      for (final JsonElement el : ResponseParser.array(guide, "required_items")) {
         final GuideItem item = requestedItem(el, GuideItemKind.CARRY);
         if (item != null) {
            requested.putIfAbsent(item.getName().toLowerCase(Locale.ROOT), item);
         }
      }

      // Look everything up in parallel.
      final ExecutorService pool = pool();
      final List<CompletableFuture<GuideItem>> itemLookups = new ArrayList<>();
      for (final GuideItem item : requested.values()) {
         if (itemLookups.size() >= MAX_ITEMS) {
            break;
         }
         itemLookups.add(CompletableFuture.supplyAsync(() -> checkItem(item), pool));
      }
      final JsonObject loc = ResponseParser.object(guide, "location");
      final CompletableFuture<WorldTarget> locationLookup = CompletableFuture.supplyAsync(() -> checkLocation(loc), pool);
      final String npcName = ResponseParser.str(guide, "npc");
      final CompletableFuture<WorldTarget> npcLookup = CompletableFuture.supplyAsync(() -> checkNpc(npcName), pool);

      int removed = 0;
      final List<GuideItem> items = new ArrayList<>();
      for (final CompletableFuture<GuideItem> f : itemLookups) {
         final GuideItem checked = join(f);
         if (checked == null) {
            removed++;
         } else {
            items.add(checked);
         }
      }
      final WorldTarget location = join(locationLookup);
      if (loc != null && !ResponseParser.str(loc, "name").isEmpty() && location == null) {
         removed++;
      }
      WorldTarget npc = join(npcLookup);
      if (!npcName.isEmpty() && npc == null) {
         removed++;
      }
      // Monsters have many spawns and the wiki's first one may be far from the guide's spot: when the NPC's
      // coordinates disagree with the location, keep the NPC as name-only (still highlighted in the scene).
      if (npc != null && npc.getPoint() != null && location != null && location.getPoint() != null
         && npc.getPoint().distanceTo2D(location.getPoint()) > MAX_NPC_LOCATION_DISTANCE) {
         npc = WorldTarget.named(npc.getName());
      }

      final List<GuideItem> resolved = prices.withPrices(ItemStatusResolver.resolve(items, holdings));
      final GuidePlan plan = new GuidePlan(title.isEmpty() ? "Guide" : title, steps, currentStep,
         resolved, location, npc, removed);
      return plan.isEmpty() ? null : plan;
   }

   private static GuideItem requestedItem(final JsonElement el, final GuideItemKind kind) {
      if (el.isJsonPrimitive()) {
         final String name = el.getAsString().trim();
         return name.isEmpty() ? null : new GuideItem(name, 1, kind, null, ItemStatus.UNKNOWN);
      }
      if (!el.isJsonObject()) {
         return null;
      }
      final JsonObject o = el.getAsJsonObject();
      final String name = ResponseParser.str(o, "name");
      if (name.isEmpty()) {
         return null;
      }
      final String slot = ResponseParser.str(o, "slot");
      return new GuideItem(name, ResponseParser.intOr(o, "quantity", 1), kind, slot.isEmpty() ? null : slot,
         ItemStatus.UNKNOWN);
   }

   /** The item with its wiki name and slot, or null if the wiki has no such item. */
   private GuideItem checkItem(final GuideItem item) {
      final WikiItem info = wiki.item(item.getName());
      if (info == null) {
         return null;
      }
      if (item.getKind() == GuideItemKind.EQUIP && info.isEquipable()) {
         final String slot = info.getSlot() != null ? info.getSlot() : item.getSlot();
         return new GuideItem(info.getName(), 1, GuideItemKind.EQUIP, slot, ItemStatus.UNKNOWN);
      }
      // Not wearable (or asked to carry): bring it.
      return new GuideItem(info.getName(), item.getQuantity(), GuideItemKind.CARRY, null, ItemStatus.UNKNOWN);
   }

   /** Wiki coordinates if the place has a page with a map; else the AI's estimate; else name only. */
   private WorldTarget checkLocation(final JsonObject loc) {
      if (loc == null) {
         return null;
      }
      final String name = ResponseParser.str(loc, "name");
      if (name.isEmpty()) {
         return null;
      }
      final WorldPoint fromWiki = wiki.coordinates(name);
      if (fromWiki != null) {
         return new WorldTarget(name, fromWiki, TargetSource.WIKI);
      }
      final int x = ResponseParser.intOr(loc, "x", 0);
      final int y = ResponseParser.intOr(loc, "y", 0);
      if (plausible(x, y)) {
         return new WorldTarget(name, new WorldPoint(x, y, ResponseParser.intOr(loc, "plane", 0)), TargetSource.AI_ESTIMATE);
      }
      return wiki.exists(name) ? WorldTarget.named(name) : null;
   }

   /** The NPC with wiki coordinates when available; null if the wiki has no page for it. */
   private WorldTarget checkNpc(final String name) {
      if (name.isEmpty() || !wiki.exists(name)) {
         return null;
      }
      final WorldPoint p = wiki.coordinates(name);
      return p == null ? WorldTarget.named(name) : new WorldTarget(name, p, TargetSource.WIKI);
   }

   /** Rough bounds of the OSRS world map (surface and underground). */
   private static boolean plausible(final int x, final int y) {
      return x >= 1000 && x <= 4300 && y >= 2400 && y <= 13000;
   }

   private static <T> T join(final CompletableFuture<T> f) {
      try {
         return f.get(LOOKUP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
      } catch (final Exception e) {
         log.debug("RuneGPT: wiki lookup failed", e);
         return null;
      }
   }

   private synchronized ExecutorService pool() {
      if (pool == null || pool.isShutdown()) {
         pool = Executors.newFixedThreadPool(4, r -> {
            final Thread t = new Thread(r, "RuneGPT-wiki");
            t.setDaemon(true);
            return t;
         });
      }
      return pool;
   }

   /** Stops the lookup threads. Called from the plugin's shutDown(). */
   public synchronized void shutdown() {
      if (pool != null) {
         pool.shutdownNow();
         pool = null;
      }
   }
}
