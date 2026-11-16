package com.kamrant.runegpt.quest;

import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Player;

/**
 * Tracks the NPC the quest helper wants the player to visit. Each game tick it finds the closest
 * loaded NPC with that exact name and points the game's hint arrow at it; the overlay draws the
 * outline and a line from the player. Only ever targets NPCs that exist in the loaded scene, so it
 * never points at a made-up location.
 */
@Singleton
public class QuestHighlighter {

   private final Client client;

   private volatile String quest;
   private volatile String npcName;
   private volatile NPC target;
   private boolean arrowSet;

   @Inject
   public QuestHighlighter(final Client client) {
      this.client = client;
   }

   /** Start highlighting {@code npcName}; null clears. Safe to call from any thread. */
   public void setTarget(final String quest, final String npcName) {
      this.quest = quest;
      this.npcName = npcName;
      this.target = null;
   }

   public String getQuest() {
      return quest;
   }

   public String getNpcName() {
      return npcName;
   }

   /** The matching NPC in the scene this tick, or null. Read by the overlay. */
   public NPC getTarget() {
      return target;
   }

   /** Call on the client thread every game tick. */
   public void onTick() {
      final String name = npcName;
      if (name == null) {
         target = null;
         clearArrow();
         return;
      }
      final Player local = client.getLocalPlayer();
      NPC best = null;
      int bestDist = Integer.MAX_VALUE;
      for (final NPC npc : client.getNpcs()) {
         if (npc.getName() != null && npc.getName().equalsIgnoreCase(name)) {
            final int d = local == null ? 0 : npc.getWorldLocation().distanceTo(local.getWorldLocation());
            if (d < bestDist) {
               best = npc;
               bestDist = d;
            }
         }
      }
      target = best;
      if (best != null) {
         client.setHintArrow(best);
         arrowSet = true;
      } else {
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
      npcName = null;
      target = null;
      clearArrow();
   }
}
