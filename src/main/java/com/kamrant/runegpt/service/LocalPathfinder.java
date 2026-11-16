package com.kamrant.runegpt.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.CollisionData;
import net.runelite.api.CollisionDataFlag;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;

/**
 * Collision-aware local pathfinder on the loaded scene's {@link CollisionData} grid.
 * Finds the shortest walkable tile path from the player toward the destination (whether the destination
 * is inside the scene or hundreds of tiles away), routing cleanly around buildings, walls, fences, water
 * and solid objects, and never placing path tiles on blocked or dead-end building tiles.
 */
public final class LocalPathfinder {

   private static final int MAX_SEARCH_TILES = 24;

   // Cardinal directions first so orthogonal steps are preferred on equal-length paths, then diagonals.
   private static final int[] DX = { 0, 0, 1, -1, 1, -1, 1, -1 };
   private static final int[] DY = { 1, -1, 0, 0, 1, 1, -1, -1 };

   private LocalPathfinder() {
   }

   /**
    * Computes the optimal walkable path from {@code start} toward {@code target} in the current {@link WorldView},
    * routing around buildings and walls. Works for both nearby targets inside the scene and far-away targets.
    */
   public static List<WorldPoint> findPath(final Client client, final WorldPoint start, final WorldPoint target) {
      if (client == null || start == null || target == null) {
         return Collections.emptyList();
      }
      final WorldView wv = client.getTopLevelWorldView();
      if (wv == null || wv.getPlane() != start.getPlane()) {
         return Collections.emptyList();
      }
      final CollisionData[] maps = wv.getCollisionMaps();
      final int plane = wv.getPlane();
      if (maps == null || plane < 0 || plane >= maps.length || maps[plane] == null) {
         return Collections.emptyList();
      }
      final LocalPoint startLp = LocalPoint.fromWorld(wv, start);
      if (startLp == null) {
         return Collections.emptyList();
      }
      final int baseX = wv.getBaseX();
      final int baseY = wv.getBaseY();
      final int gx = target.getX() - baseX;
      final int gy = target.getY() - baseY;

      final int[][] flags = maps[plane].getFlags();
      final List<int[]> scenePath = findScenePath(flags, startLp.getSceneX(), startLp.getSceneY(), gx, gy);
      if (scenePath.isEmpty()) {
         return Collections.emptyList();
      }
      final List<WorldPoint> worldPath = new ArrayList<>(scenePath.size());
      for (final int[] xy : scenePath) {
         worldPath.add(new WorldPoint(baseX + xy[0], baseY + xy[1], plane));
      }
      return worldPath;
   }

   /**
    * Shortest walkable path on a {@link CollisionData#getFlags()} grid from {@code (sx, sy)} to {@code (gx, gy)}
    * (or to the reachable tile within {@link #MAX_SEARCH_TILES} that makes the greatest progress toward a far-away
    * {@code (gx, gy)} around local buildings and obstacles). Excludes the starting tile.
    */
   public static List<int[]> findScenePath(final int[][] flags, final int sx, final int sy, final int gx, final int gy) {
      if (flags == null || flags.length == 0 || flags[0].length == 0) {
         return Collections.emptyList();
      }
      final int w = flags.length;
      final int h = flags[0].length;
      if (!inBounds(sx, sy, w, h) || (sx == gx && sy == gy)) {
         return Collections.emptyList();
      }

      final boolean goalInScene = inBounds(gx, gy, w, h);
      final boolean goalBlocked = goalInScene && isBlocked(flags[gx][gy]);
      final int[] prev = new int[w * h];
      Arrays.fill(prev, -1);
      final boolean[] visited = new boolean[w * h];
      final int[] queue = new int[w * h];
      int head = 0;
      int tail = 0;

      final int startIdx = sx * h + sy;
      visited[startIdx] = true;
      queue[tail++] = startIdx;

      int reachedIdx = -1;
      int closestIdx = -1;
      long closestDistSq = distSq(sx, sy, gx, gy);

      while (head < tail) {
         final int curr = queue[head++];
         final int cx = curr / h;
         final int cy = curr % h;

         if (cx == gx && cy == gy) {
            reachedIdx = curr;
            break;
         }
         if (goalBlocked && Math.abs(cx - gx) <= 1 && Math.abs(cy - gy) <= 1) {
            reachedIdx = curr;
            break;
         }
         final long dSq = distSq(cx, cy, gx, gy);
         if (dSq < closestDistSq) {
            closestDistSq = dSq;
            closestIdx = curr;
         }

         for (int d = 0; d < DX.length; d++) {
            final int nx = cx + DX[d];
            final int ny = cy + DY[d];
            if (!inBounds(nx, ny, w, h)) {
               continue;
            }
            final int nextIdx = nx * h + ny;
            if (visited[nextIdx]) {
               continue;
            }
            if (Math.max(Math.abs(nx - sx), Math.abs(ny - sy)) > MAX_SEARCH_TILES) {
               continue;
            }
            if (!canStep(flags, cx, cy, DX[d], DY[d], w, h)) {
               continue;
            }
            visited[nextIdx] = true;
            prev[nextIdx] = curr;
            queue[tail++] = nextIdx;
         }
      }

      final int endIdx = reachedIdx != -1 ? reachedIdx : closestIdx;
      if (endIdx == -1 || endIdx == startIdx) {
         return Collections.emptyList();
      }

      final List<int[]> path = new ArrayList<>();
      for (int cur = endIdx; cur != -1 && cur != startIdx; cur = prev[cur]) {
         path.add(new int[] { cur / h, cur % h });
      }
      Collections.reverse(path);
      return path;
   }

   /** Checks OSRS directional wall flags and full-tile collision flags for a step from (x, y) by (dx, dy). */
   public static boolean canStep(final int[][] flags, final int x, final int y, final int dx, final int dy,
      final int w, final int h) {
      final int nx = x + dx;
      final int ny = y + dy;
      if (!inBounds(nx, ny, w, h) || isBlocked(flags[nx][ny])) {
         return false;
      }
      final int srcFlag = flags[x][y];
      final int dstFlag = flags[nx][ny];

      if (dx == 0 && dy == 1) {
         return (srcFlag & CollisionDataFlag.BLOCK_MOVEMENT_NORTH) == 0
            && (dstFlag & CollisionDataFlag.BLOCK_MOVEMENT_SOUTH) == 0;
      }
      if (dx == 0 && dy == -1) {
         return (srcFlag & CollisionDataFlag.BLOCK_MOVEMENT_SOUTH) == 0
            && (dstFlag & CollisionDataFlag.BLOCK_MOVEMENT_NORTH) == 0;
      }
      if (dx == 1 && dy == 0) {
         return (srcFlag & CollisionDataFlag.BLOCK_MOVEMENT_EAST) == 0
            && (dstFlag & CollisionDataFlag.BLOCK_MOVEMENT_WEST) == 0;
      }
      if (dx == -1 && dy == 0) {
         return (srcFlag & CollisionDataFlag.BLOCK_MOVEMENT_WEST) == 0
            && (dstFlag & CollisionDataFlag.BLOCK_MOVEMENT_EAST) == 0;
      }
      // Diagonal steps require both cardinal components to be walkable plus the diagonal corner flag clear.
      if (!canStep(flags, x, y, dx, 0, w, h) || !canStep(flags, x, y, 0, dy, w, h)
         || !canStep(flags, x + dx, y, 0, dy, w, h) || !canStep(flags, x, y + dy, dx, 0, w, h)) {
         return false;
      }
      if (dx == 1 && dy == 1) {
         return (srcFlag & CollisionDataFlag.BLOCK_MOVEMENT_NORTH_EAST) == 0
            && (dstFlag & CollisionDataFlag.BLOCK_MOVEMENT_SOUTH_WEST) == 0;
      }
      if (dx == -1 && dy == 1) {
         return (srcFlag & CollisionDataFlag.BLOCK_MOVEMENT_NORTH_WEST) == 0
            && (dstFlag & CollisionDataFlag.BLOCK_MOVEMENT_SOUTH_EAST) == 0;
      }
      if (dx == 1 && dy == -1) {
         return (srcFlag & CollisionDataFlag.BLOCK_MOVEMENT_SOUTH_EAST) == 0
            && (dstFlag & CollisionDataFlag.BLOCK_MOVEMENT_NORTH_WEST) == 0;
      }
      if (dx == -1 && dy == -1) {
         return (srcFlag & CollisionDataFlag.BLOCK_MOVEMENT_SOUTH_WEST) == 0
            && (dstFlag & CollisionDataFlag.BLOCK_MOVEMENT_NORTH_EAST) == 0;
      }
      return false;
   }

   private static boolean isBlocked(final int flag) {
      return (flag & CollisionDataFlag.BLOCK_MOVEMENT_FULL) != 0;
   }

   private static boolean inBounds(final int x, final int y, final int w, final int h) {
      return x >= 0 && x < w && y >= 0 && y < h;
   }

   private static long distSq(final long x1, final long y1, final long x2, final long y2) {
      final long dx = x1 - x2;
      final long dy = y1 - y2;
      return dx * dx + dy * dy;
   }
}
