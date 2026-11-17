package com.kamrant.runegpt.model;

import java.util.Collections;
import java.util.List;

/**
 * The actionable part of an answer: short steps (with the index of the step the player is currently on)
 * plus what to bring, what to wear, and where to go. Items, NPC and location have been checked against the
 * OSRS Wiki. Immutable.
 */
public final class GuidePlan {

   private final String title;
   private final List<String> steps;
   private final int currentStep;
   private final List<GuideItem> items;
   private final WorldTarget location;
   private final WorldTarget npc;
   private final int removedUnverified;

   public GuidePlan(final String title, final List<String> steps, final List<GuideItem> items,
      final WorldTarget location, final WorldTarget npc, final int removedUnverified) {
      this(title, steps, 0, items, location, npc, removedUnverified);
   }

   public GuidePlan(final String title, final List<String> steps, final int currentStep, final List<GuideItem> items,
      final WorldTarget location, final WorldTarget npc, final int removedUnverified) {
      this.title = title;
      this.steps = Collections.unmodifiableList(steps);
      this.currentStep = steps.isEmpty() ? 0 : Math.max(0, Math.min(currentStep, steps.size()));
      this.items = Collections.unmodifiableList(items);
      this.location = location;
      this.npc = npc;
      this.removedUnverified = removedUnverified;
   }

   public GuidePlan withItems(final List<GuideItem> newItems) {
      return new GuidePlan(title, steps, currentStep, newItems, location, npc, removedUnverified);
   }

   public GuidePlan withCurrentStep(final int newStep) {
      return new GuidePlan(title, steps, newStep, items, location, npc, removedUnverified);
   }

   public String getTitle() {
      return title;
   }

   public List<String> getSteps() {
      return steps;
   }

   /**
    * Zero-based index of the current active step in {@link #getSteps()}. Steps before this index are
    * completed; {@code currentStep == steps.size()} means all steps are finished.
    */
   public int getCurrentStep() {
      return currentStep;
   }

   /** Text of the current active step, or empty when there are no steps. */
   public String currentStepText() {
      if (steps.isEmpty()) {
         return "";
      }
      final int idx = Math.min(currentStep, steps.size() - 1);
      return steps.get(idx);
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

   /**
    * Where to send the player: wiki-verified coordinates first (the location, then the NPC), and an AI-estimated
    * location only when nothing verified is available. Falls back to a name-only target, or null.
    */
   public WorldTarget navigationTarget() {
      if (location != null && location.getPoint() != null && !location.isApproximate()) {
         return location;
      }
      if (npc != null && npc.getPoint() != null) {
         return npc;
      }
      if (location != null && location.getPoint() != null) {
         return location;
      }
      return location != null ? location : npc;
   }

   /** How many AI-suggested items/NPCs/places were dropped because the wiki has no such thing. */
   public int getRemovedUnverified() {
      return removedUnverified;
   }

   public boolean isEmpty() {
      return steps.isEmpty() && items.isEmpty() && location == null && npc == null;
   }
}
