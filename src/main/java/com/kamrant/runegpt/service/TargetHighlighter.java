package com.kamrant.runegpt.service;

import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;

/**
 * Tracks where the active guide wants the player to go: an NPC (by exact name) and/or a location.
 * Each game tick it points the game's hint arrow at the closest matching loaded NPC, or, when no such
 * NPC is loaded and the player is near the location, at the location tile. Overlays read this to draw
 * the NPC hull, tile, directional ground arrow, minimap arrow and world map route line.
 */
@Singleton
public class TargetHighlighter {

   /** Within this many tiles of the location, the game's native hint arrow points at the tile. */
   private static final int LOCATION_ARROW_RANGE = 60;

   private final Client client;

   private volatile String npcName;
   private volatile String targetName;
   private volatile WorldPoint location;
   private volatile NPC targetNpc;
   private volatile WorldPoint targetTile;
   private boolean arrowSet;

   @Inject
   public TargetHighlighter(final Client client) {
      this.client = client;
   }

   /** Set what to point at; any argument may be null. Safe from any thread. */
   public void setTarget(final String npcName, final String targetName, final WorldPoint location) {
      this.npcName = npcName;
      this.targetName = targetName != null ? targetName : npcName;
      this.location = location;
      this.targetNpc = null;
      this.targetTile = null;
   }

   public void clear() {
      setTarget(null, null, null);
   }

   /** The matching NPC in the scene this tick, or null. Read by the overlay. */
   public NPC getTargetNpc() {
      return targetNpc;
   }

   /** The location tile when it's in local range (no NPC loaded, player nearby), or null. */
   public WorldPoint getTargetTile() {
      return targetTile;
   }

   /** The tracked destination coordinates (at any distance), or null. */
   public WorldPoint getDestination() {
      final NPC npc = targetNpc;
      return npc != null ? npc.getWorldLocation() : location;
   }

   /** Display name of the tracked destination or NPC, or null. */
   public String getTargetName() {
      return targetName;
   }

   /** Call on the client thread every game tick. */
   public void onTick() {
      final String name = npcName;
      final WorldPoint loc = location;
      if (name == null && loc == null) {
         targetNpc = null;
         targetTile = null;
         clearArrow();
         return;
      }
      final Player local = client.getLocalPlayer();
      final WorldPoint here = local == null ? null : local.getWorldLocation();

      NPC best = null;
      if (name != null) {
         int bestDist = Integer.MAX_VALUE;
         for (final NPC npc : client.getTopLevelWorldView().npcs()) {
            if (npc.getName() != null && npc.getName().equalsIgnoreCase(name)) {
               final int d = here == null ? 0 : npc.getWorldLocation().distanceTo(here);
               if (d < bestDist) {
                  best = npc;
                  bestDist = d;
               }
            }
         }
      }
      targetNpc = best;
      if (best != null) {
         targetTile = null;
         client.setHintArrow(best);
         arrowSet = true;
         return;
      }

      if (loc != null && here != null && loc.getPlane() == here.getPlane() && loc.distanceTo(here) <= LOCATION_ARROW_RANGE) {
         targetTile = loc;
         client.setHintArrow(loc);
         arrowSet = true;
      } else {
         targetTile = null;
         clearArrow();
      }
   }

   private void clearArrow() {
      if (arrowSet) {
         client.clearHintArrow();
         arrowSet = false;
      }
   }

   /** Call on the client thread when the plugin stops. */
   public void shutdown() {
      clear();
      targetNpc = null;
      targetTile = null;
      clearArrow();
   }
}
