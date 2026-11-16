package com.kamrant.runegpt.service;

import net.runelite.api.coords.WorldPoint;

/** Plain-language distance and direction between two world points, e.g. "~240 tiles north-east". */
public final class Navigation {

   private static final String[] DIRECTIONS = { "east", "north-east", "north", "north-west", "west", "south-west",
      "south", "south-east" };
   /** Underground areas sit about 6400 tiles north of the surface on the world map. */
   private static final int UNDERGROUND_OFFSET = 6400;

   private Navigation() {
   }

   public static String describe(final WorldPoint from, final WorldPoint to) {
      if (from == null || to == null) {
         return "";
      }
      final boolean fromUnder = from.getY() > UNDERGROUND_OFFSET;
      final boolean toUnder = to.getY() > UNDERGROUND_OFFSET;
      if (fromUnder != toUnder) {
         return toUnder ? "underground" : "on the surface";
      }
      final int dx = to.getX() - from.getX();
      final int dy = to.getY() - from.getY();
      final int dist = Math.max(Math.abs(dx), Math.abs(dy));
      if (dist <= 3) {
         return from.getPlane() == to.getPlane() ? "you're here" : (to.getPlane() > from.getPlane() ? "upstairs" : "downstairs");
      }
      final double angle = Math.toDegrees(Math.atan2(dy, dx));
      final int idx = (int) Math.round(((angle + 360) % 360) / 45.0) % 8;
      return "~" + dist + " tiles " + DIRECTIONS[idx];
   }
}
