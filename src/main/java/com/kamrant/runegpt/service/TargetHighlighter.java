package com.kamrant.runegpt.service;

import java.util.Locale;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.NPC;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Player;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.TileObject;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;

/**
 * Tracks where the active guide step wants the player to go: an NPC, an interactable game object mentioned
 * in the current step (anvil, bed, pyre site, fishing spot, altar, bank booth, ladder...), and/or a world
 * location tile. Each game tick it finds the closest matching loaded NPC or object, sets the game's hint
 * arrow, and exposes the target for the tile-marker and map overlays.
 */
@Singleton
public class TargetHighlighter {

   /** Within this many tiles of the location, the game's native hint arrow points at the tile. */
   private static final int LOCATION_ARROW_RANGE = 60;
   /** Radius (in tiles) around the player scanned for objects mentioned in the current step. */
   private static final int OBJECT_SCAN_RADIUS = 26;

   private final Client client;

   private volatile String npcName;
   private volatile String targetName;
   private volatile String stepText = "";
   private volatile int stepNumber = 1;
   private volatile WorldPoint location;
   private volatile NPC targetNpc;
   private volatile TileObject targetObject;
   private volatile String targetObjectName;
   private volatile WorldPoint targetTile;
   private boolean arrowSet;

   @Inject
   public TargetHighlighter(final Client client) {
      this.client = client;
   }

   /** Set what to point at; any argument may be null. Safe from any thread. */
   public void setTarget(final String npcName, final String targetName, final WorldPoint location,
      final int stepNumber, final String stepText) {
      this.npcName = npcName;
      this.targetName = targetName != null ? targetName : npcName;
      this.location = location;
      this.stepNumber = Math.max(1, stepNumber);
      this.stepText = stepText == null ? "" : stepText;
      this.targetNpc = null;
      this.targetObject = null;
      this.targetObjectName = null;
      this.targetTile = null;
   }

   /** Update the active step number and text without resetting the map target. Safe from any thread. */
   public void setStep(final int stepNumber, final String stepText) {
      this.stepNumber = Math.max(1, stepNumber);
      this.stepText = stepText == null ? "" : stepText;
   }

   public void clear() {
      setTarget(null, null, null, 1, "");
   }

   /** The matching NPC in the scene this tick, or null. */
   public NPC getTargetNpc() {
      return targetNpc;
   }

   /** The matching interactable TileObject in the scene this tick, or null. */
   public TileObject getTargetObject() {
      return targetObject;
   }

   public String getTargetObjectName() {
      return targetObjectName;
   }

   /** The location tile when it's in local range, or null. */
   public WorldPoint getTargetTile() {
      return targetTile;
   }

   /** The tracked destination coordinates (NPC, object, or location at any distance), or null. */
   public WorldPoint getDestination() {
      final NPC npc = targetNpc;
      if (npc != null) {
         return npc.getWorldLocation();
      }
      final TileObject obj = targetObject;
      if (obj != null) {
         return obj.getWorldLocation();
      }
      return location;
   }

   /** Display name of the tracked destination or NPC, or null. */
   public String getTargetName() {
      return targetName;
   }

   /** 1-based index of the current active step. */
   public int getStepNumber() {
      return stepNumber;
   }

   public String getStepText() {
      return stepText;
   }

   /** Call on the client thread every game tick. */
   public void onTick() {
      final String name = npcName;
      final WorldPoint loc = location;
      final String step = stepText;
      if (name == null && loc == null && (step == null || step.isEmpty())) {
         targetNpc = null;
         targetObject = null;
         targetObjectName = null;
         targetTile = null;
         clearArrow();
         return;
      }
      final Player local = client.getLocalPlayer();
      final WorldPoint here = local == null ? null : local.getWorldLocation();
      final String stepLower = step == null ? "" : step.toLowerCase(Locale.ROOT);

      // 1. Check loaded NPCs (matching either the guide's npcName or an NPC named in the current step).
      NPC bestNpc = null;
      int bestNpcDist = Integer.MAX_VALUE;
      for (final NPC npc : client.getTopLevelWorldView().npcs()) {
         final String n = npc.getName();
         if (n == null || n.equals("null")) {
            continue;
         }
         final boolean matches = (name != null && n.equalsIgnoreCase(name))
            || (!stepLower.isEmpty() && n.length() >= 3 && stepMentionsEntity(stepLower, n));
         if (matches) {
            final int d = here == null ? 0 : npc.getWorldLocation().distanceTo(here);
            if (d < bestNpcDist) {
               bestNpc = npc;
               bestNpcDist = d;
            }
         }
      }
      targetNpc = bestNpc;
      if (bestNpc != null) {
         targetObject = null;
         targetObjectName = null;
         targetTile = bestNpc.getWorldLocation();
         client.setHintArrow(bestNpc);
         arrowSet = true;
         return;
      }

      // 2. Check nearby TileObjects mentioned in the current step (anvil, bed, pyre site, whirlpool, bank...).
      TileObject bestObj = null;
      String bestObjName = null;
      if (here != null && !stepLower.isEmpty()) {
         final Object[] found = findMatchingObject(here, stepLower);
         if (found != null) {
            bestObj = (TileObject) found[0];
            bestObjName = (String) found[1];
         }
      }
      targetObject = bestObj;
      targetObjectName = bestObjName;
      if (bestObj != null) {
         targetTile = bestObj.getWorldLocation();
         client.setHintArrow(bestObj.getWorldLocation());
         arrowSet = true;
         return;
      }

      // 3. Otherwise point at the location tile when within range.
      if (loc != null && here != null && loc.getPlane() == here.getPlane() && loc.distanceTo(here) <= LOCATION_ARROW_RANGE) {
         targetTile = loc;
         client.setHintArrow(loc);
         arrowSet = true;
      } else {
         targetTile = null;
         clearArrow();
      }
   }

   private Object[] findMatchingObject(final WorldPoint here, final String stepLower) {
      final WorldView wv = client.getTopLevelWorldView();
      final Scene scene = wv.getScene();
      if (scene == null) {
         return null;
      }
      final int plane = wv.getPlane();
      final Tile[][][] tiles = scene.getTiles();
      if (tiles == null || plane < 0 || plane >= tiles.length || tiles[plane] == null) {
         return null;
      }

      TileObject best = null;
      String bestName = null;
      int bestScore = 0;
      int bestDist = Integer.MAX_VALUE;

      final Tile[][] planeTiles = tiles[plane];
      for (int x = 0; x < planeTiles.length; x++) {
         for (int y = 0; y < planeTiles[x].length; y++) {
            final Tile tile = planeTiles[x][y];
            if (tile == null) {
               continue;
            }
            final WorldPoint wp = tile.getWorldLocation();
            final int dist = wp == null ? Integer.MAX_VALUE : wp.distanceTo2D(here);
            if (dist > OBJECT_SCAN_RADIUS) {
               continue;
            }
            if (tile.getGameObjects() != null) {
               for (final GameObject go : tile.getGameObjects()) {
                  if (go != null) {
                     final String objName = objectName(go.getId());
                     final int score = matchScore(stepLower, objName);
                     if (score > bestScore || (score > 0 && score == bestScore && dist < bestDist)) {
                        best = go;
                        bestName = objName;
                        bestScore = score;
                        bestDist = dist;
                     }
                  }
               }
            }
            for (final TileObject to : new TileObject[] { tile.getWallObject(), tile.getGroundObject(), tile.getDecorativeObject() }) {
               if (to != null) {
                  final String objName = objectName(to.getId());
                  final int score = matchScore(stepLower, objName);
                  if (score > bestScore || (score > 0 && score == bestScore && dist < bestDist)) {
                     best = to;
                     bestName = objName;
                     bestScore = score;
                     bestDist = dist;
                  }
               }
            }
         }
      }
      return best == null ? null : new Object[] { best, bestName };
   }

   private String objectName(final int id) {
      try {
         ObjectComposition comp = client.getObjectDefinition(id);
         if (comp == null) {
            return null;
         }
         if (comp.getImpostorIds() != null) {
            final ObjectComposition imp = comp.getImpostor();
            if (imp != null) {
               comp = imp;
            }
         }
         final String n = comp.getName();
         return n == null || n.equals("null") || n.isEmpty() ? null : n;
      } catch (final RuntimeException e) {
         return null;
      }
   }

   private static int matchScore(final String stepLower, final String entityName) {
      if (entityName == null || entityName.length() < 3) {
         return 0;
      }
      final String n = entityName.toLowerCase(Locale.ROOT).trim();
      if (stepMentionsEntity(stepLower, n)) {
         // Prefer longer, more specific object names ("barbarian anvil" > "anvil").
         return n.length();
      }
      return 0;
   }

   private static boolean stepMentionsEntity(final String stepLower, final String entityName) {
      final String n = entityName.toLowerCase(Locale.ROOT).trim();
      if (n.length() < 3) {
         return false;
      }
      return stepLower.contains(n) || stepLower.contains(n + "s");
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
      targetObject = null;
      targetObjectName = null;
      targetTile = null;
      clearArrow();
   }
}
