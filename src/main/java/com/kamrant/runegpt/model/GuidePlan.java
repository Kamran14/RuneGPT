package com.kamrant.runegpt.model;

import java.util.Collections;
import java.util.List;

/**
 * The actionable part of an answer: short steps plus what to bring, what to wear, and where to go.
 * Items, NPC and location have been checked against the OSRS Wiki. Immutable.
 */
public final class GuidePlan {

   private final String title;
   private final List<String> steps;
   private final List<GuideItem> items;
   private final WorldTarget location;
   private final WorldTarget npc;
   private final int removedUnverified;

   public GuidePlan(final String title, final List<String> steps, final List<GuideItem> items,
      final WorldTarget location, final WorldTarget npc, final int removedUnverified) {
      this.title = title;
      this.steps = Collections.unmodifiableList(steps);
      this.items = Collections.unmodifiableList(items);
      this.location = location;
      this.npc = npc;
      this.removedUnverified = removedUnverified;
   }

   public GuidePlan withItems(final List<GuideItem> newItems) {
      return new GuidePlan(title, steps, newItems, location, npc, removedUnverified);
   }

   public String getTitle() {
      return title;
   }

   public List<String> getSteps() {
      return steps;
   }

   public List<GuideItem> getItems() {
      return items;
   }

   /** Where to go, or null. */
   public WorldTarget getLocation() {
      return location;
   }

   /** NPC to talk to or kill, or null. */
   public WorldTarget getNpc() {
      return npc;
   }

   /** How many AI-suggested items/NPCs/places were dropped because the wiki has no such thing. */
   public int getRemovedUnverified() {
      return removedUnverified;
   }

   public boolean isEmpty() {
      return steps.isEmpty() && items.isEmpty() && location == null && npc == null;
   }
}
