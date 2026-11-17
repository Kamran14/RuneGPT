package com.kamrant.runegpt.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.CollisionData;
import net.runelite.api.CollisionDataFlag;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Point;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.WallObject;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;

/**
 * Collision-aware local pathfinder on the loaded scene's {@link CollisionData} grid.
 * Finds the shortest walkable tile path from the player toward the destination (whether the destination
 * is inside the scene or hundreds of tiles away), routing cleanly around buildings, walls, fences, water
 * and solid objects. Closed doors and gates count as passable, since the player can open them. The whole
 * loaded scene is searched (at most a few hundred tiles across, cheap enough for every game tick), so a
 * target anywhere in the scene gets a complete path.
 */
public final class LocalPathfinder {

   private static final String ACTION_OPEN = "Open";

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

      final int[][] flags = withOpenableDoors(client, wv, plane, maps[plane].getFlags());
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
    * Copy of {@code flags} with the walls of closed doors and gates (wall objects with an "Open" action) removed,
    * so paths go through doors instead of around buildings. Returns {@code flags} itself when there are none.
    * Client thread only.
    */
   private static int[][] withOpenableDoors(final Client client, final WorldView wv, final int plane,
      final int[][] flags) {
      final Scene scene = wv.getScene();
      final Tile[][][] tiles = scene == null ? null : scene.getTiles();
      if (tiles == null || plane >= tiles.length || tiles[plane] == null) {
         return flags;
      }
      int[][] out = flags;
      for (final Tile[] column : tiles[plane]) {
         if (column == null) {
            continue;
         }
         for (final Tile tile : column) {
            final WallObject wall = tile == null ? null : tile.getWallObject();
            if (wall == null || !isOpenable(client, wall.getId())) {
               continue;
            }
            final Point at = tile.getSceneLocation();
            if (at == null) {
               continue;
            }
            if (out == flags) {
               out = copy(flags);
            }
            openWall(out, at.getX(), at.getY(), wall.getOrientationA());
         }
      }
      return out;
   }

   private static boolean isOpenable(final Client client, final int objectId) {
      ObjectComposition def = client.getObjectDefinition(objectId);
      if (def != null && def.getImpostorIds() != null) {
         def = def.getImpostor();
      }
      if (def == null || def.getActions() == null) {
         return false;
      }
      for (final String action : def.getActions()) {
         if (ACTION_OPEN.equalsIgnoreCase(action)) {
            return true;
         }
      }
      return false;
   }

   /**
    * Clears the movement-blocking wall on tile {@code (x, y)} for the sides in {@code orientation}
    * ({@link WallObject#getOrientationA()} bits: 1 west, 2 north, 4 east, 8 south) and the matching side of the
    * neighbouring tile. Diagonal walls are left as they are.
    */
   static void openWall(final int[][] flags, final int x, final int y, final int orientation) {
      final int w = flags.length;
      final int h = flags[0].length;
      if (!inBounds(x, y, w, h)) {
         return;
      }
      if ((orientation & 1) != 0) {
         clearSide(flags, x, y, CollisionDataFlag.BLOCK_MOVEMENT_WEST, x - 1, y, CollisionDataFlag.BLOCK_MOVEMENT_EAST);
      }
      if ((orientation & 2) != 0) {
         clearSide(flags, x, y, CollisionDataFlag.BLOCK_MOVEMENT_NORTH, x, y + 1, CollisionDataFlag.BLOCK_MOVEMENT_SOUTH);
      }
      if ((orientation & 4) != 0) {
         clearSide(flags, x, y, CollisionDataFlag.BLOCK_MOVEMENT_EAST, x + 1, y, CollisionDataFlag.BLOCK_MOVEMENT_WEST);
      }
      if ((orientation & 8) != 0) {
         clearSide(flags, x, y, CollisionDataFlag.BLOCK_MOVEMENT_SOUTH, x, y - 1, CollisionDataFlag.BLOCK_MOVEMENT_NORTH);
      }
   }

   private static void clearSide(final int[][] flags, final int x, final int y, final int flag,
      final int nx, final int ny, final int neighbourFlag) {
      flags[x][y] &= ~flag;
      if (inBounds(nx, ny, flags.length, flags[0].length)) {
         flags[nx][ny] &= ~neighbourFlag;
      }
   }

   private static int[][] copy(final int[][] flags) {
      final int[][] out = new int[flags.length][];
      for (int i = 0; i < flags.length; i++) {
         out[i] = flags[i].clone();
      }
      return out;
   }

   /**
    * Shortest walkable path on a {@link CollisionData#getFlags()} grid from {@code (sx, sy)} to {@code (gx, gy)},
    * or, when the goal is unreachable or outside the grid, to the reachable tile closest to it. Excludes the
    * starting tile.
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
