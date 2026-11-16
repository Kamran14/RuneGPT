package com.kamrant.runegpt.model;

import net.runelite.api.coords.WorldPoint;

/** A place or NPC a guide points to, with coordinates when known. Immutable. */
public final class WorldTarget {

   private final String name;
   private final WorldPoint point;
   private final TargetSource source;

   public WorldTarget(final String name, final WorldPoint point, final TargetSource source) {
      this.name = name;
      this.point = point;
      this.source = point == null ? TargetSource.NONE : source;
   }

   public static WorldTarget named(final String name) {
      return new WorldTarget(name, null, TargetSource.NONE);
   }

   public String getName() {
      return name;
   }

   /** Coordinates, or null when only the name is known. */
   public WorldPoint getPoint() {
      return point;
   }

   public TargetSource getSource() {
      return source;
   }

   public boolean isApproximate() {
      return source == TargetSource.AI_ESTIMATE;
   }
}
