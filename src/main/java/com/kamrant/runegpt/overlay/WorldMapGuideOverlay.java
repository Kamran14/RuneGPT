package com.kamrant.runegpt.overlay;

import com.kamrant.runegpt.model.WorldTarget;
import com.kamrant.runegpt.service.GuideTracker;
import com.kamrant.runegpt.service.ShortestPathBridge;
import com.kamrant.runegpt.service.TargetHighlighter;
import com.kamrant.runegpt.ui.UiTheme;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.Area;
import java.awt.image.BufferedImage;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.worldmap.WorldMapOverlay;

/**
 * Quest-Helper-style map and minimap guider:
 * <ul>
 *    <li>when the player is far from the destination, pins a marker icon, directional arrow and tile-distance
 *    badge on the rim of the minimap in the exact camera-rotated direction of the target;</li>
 *    <li>when the player is close enough, draws the optimal walkable path and target marker on the minimap;</li>
 *    <li>when the World Map is open, draws a dashed route line and directional arrow from the player to the marker.</li>
 * </ul>
 */
public class WorldMapGuideOverlay extends Overlay {

   private static final Color WIKI_COLOR = UiTheme.ACCENT_CYAN;
   private static final Color APPROX_COLOR = UiTheme.APPROX_GREY;
   private static final Color PATH_COLOR = UiTheme.ACTIVE_STEP_ORANGE;
   private static final Color OUTLINE = UiTheme.SHADOW_OUTLINE;
   private static final Stroke MAP_DASH = new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f,
      new float[] { 7f, 6f }, 0f);
   private static final Stroke MINI_STROKE = new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
   private static final Stroke MINI_OUTLINE_STROKE = new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
   private static final int MINIMAP_RIM_RADIUS = 56;
   private static final int MINIMAP_ARROW_START = 34;

   private final Client client;
   private final GuideTracker tracker;
   private final TargetHighlighter highlighter;
   private final WorldMapOverlay worldMapOverlay;
   private final ShortestPathBridge shortestPath;

   @Inject
   public WorldMapGuideOverlay(final Client client, final GuideTracker tracker, final TargetHighlighter highlighter,
      final WorldMapOverlay worldMapOverlay, final ShortestPathBridge shortestPath) {
      this.client = client;
      this.tracker = tracker;
      this.highlighter = highlighter;
      this.worldMapOverlay = worldMapOverlay;
      this.shortestPath = shortestPath;
      setPosition(OverlayPosition.DYNAMIC);
      setPriority(PRIORITY_HIGH);
      setLayer(OverlayLayer.ABOVE_WIDGETS);
   }

   @Override
   public Dimension render(final Graphics2D g) {
      // The Shortest Path plugin is drawing the full route (world map, minimap and scene): don't draw a second one.
      if (shortestPath.isRouting()) {
         return null;
      }
      final WorldTarget target = tracker.getMapTarget();
      final WorldPoint dest = target != null ? target.getPoint() : highlighter.getDestination();
      final Player local = client.getLocalPlayer();
      if (dest == null || local == null) {
         return null;
      }
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      final boolean approximate = target != null && target.isApproximate();
      final Color color = approximate ? APPROX_COLOR : WIKI_COLOR;

      final Widget mapContainer = client.getWidget(InterfaceID.Worldmap.MAP_CONTAINER);
      if (mapContainer != null && !mapContainer.isHidden()) {
         renderOnWorldMap(g, mapContainer, local.getWorldLocation(), dest, color);
      } else {
         renderOnMinimap(g, local, dest, color, approximate);
      }
      return null;
   }

   private void renderOnWorldMap(final Graphics2D g, final Widget mapContainer, final WorldPoint fromWp,
      final WorldPoint toWp, final Color color) {
      final Point from = worldMapOverlay.mapWorldPointToGraphicsPoint(fromWp);
      final Point to = worldMapOverlay.mapWorldPointToGraphicsPoint(toWp);
      if (from == null || to == null) {
         return;
      }

      final Shape oldClip = g.getClip();
      final Stroke oldStroke = g.getStroke();
      g.setClip(worldMapClipArea(mapContainer.getBounds()));

      // Shadow + dashed route line from player to target on the World Map.
      g.setStroke(new BasicStroke(4.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
      g.setColor(OUTLINE);
      g.drawLine(from.getX(), from.getY(), to.getX(), to.getY());

      g.setStroke(MAP_DASH);
      g.setColor(color);
      g.drawLine(from.getX(), from.getY(), to.getX(), to.getY());

      // Midpoint directional arrow + target ring.
      final int midX = (from.getX() + to.getX()) / 2;
      final int midY = (from.getY() + to.getY()) / 2;
      g.setStroke(MINI_STROKE);
      TargetHighlightOverlay.drawArrowHead(g, from.getX(), from.getY(), midX, midY, 9);
      g.drawOval(to.getX() - 10, to.getY() - 10, 20, 20);

      g.setStroke(oldStroke);
      g.setClip(oldClip);
   }

   private void renderOnMinimap(final Graphics2D g, final Player local, final WorldPoint dest,
      final Color color, final boolean approximate) {
      final WorldPoint here = local.getWorldLocation();
      final int dx = dest.getX() - here.getX();
      final int dy = dest.getY() - here.getY();
      final double tileDist = Math.hypot(dx, dy);
      if (tileDist < 1.5) {
         return;
      }

      final LocalPoint playerLp = local.getLocalLocation();
      final Point playerMini = Perspective.localToMinimap(client, playerLp);
      if (playerMini == null) {
         return;
      }

      final WorldView wv = client.getTopLevelWorldView();
      final Stroke oldStroke = g.getStroke();

      // 1. If we have a local collision-checked walkable path, draw it on the minimap.
      final List<WorldPoint> path = highlighter.getWalkablePath();
      if (!path.isEmpty()) {
         drawMinimapPath(g, wv, playerMini, path);
      }

      // 2. Check if the destination tile itself is inside the minimap circle.
      Point targetMini = null;
      if (dest.getPlane() == here.getPlane()) {
         final LocalPoint destLp = LocalPoint.fromWorld(wv, dest);
         if (destLp != null) {
            targetMini = Perspective.localToMinimap(client, destLp);
         }
      }

      final BufferedImage pin = MapMarkerImage.create(approximate);
      if (targetMini != null) {
         // Destination is visible on the minimap: draw the marker pin and target dot directly on it.
         if (path.isEmpty()) {
            drawMinimapArrow(g, playerMini.getX(), playerMini.getY(), targetMini.getX(), targetMini.getY(), color);
         }
         g.setColor(OUTLINE);
         g.fillOval(targetMini.getX() - 4, targetMini.getY() - 4, 8, 8);
         g.setColor(color);
         g.fillOval(targetMini.getX() - 3, targetMini.getY() - 3, 6, 6);
         g.drawImage(pin, targetMini.getX() - pin.getWidth() / 2, targetMini.getY() - pin.getHeight(), null);
         g.setStroke(oldStroke);
         return;
      }

      // 3. Destination is outside the minimap: project a 2-tile reference vector onto the minimap to get the
      // exact camera-rotated direction and pin a marker + directional arrow + distance badge on the minimap rim.
      final int refStepX = (int) Math.round((dx / tileDist) * 2 * Perspective.LOCAL_TILE_SIZE);
      final int refStepY = (int) Math.round((dy / tileDist) * 2 * Perspective.LOCAL_TILE_SIZE);
      final LocalPoint refLp = new LocalPoint(playerLp.getX() + refStepX, playerLp.getY() + refStepY, wv);
      final Point refMini = Perspective.localToMinimap(client, refLp, Integer.MAX_VALUE);
      if (refMini == null) {
         g.setStroke(oldStroke);
         return;
      }

      final double vx = refMini.getX() - playerMini.getX();
      final double vy = refMini.getY() - playerMini.getY();
      final double vlen = Math.hypot(vx, vy);
      if (vlen < 0.5) {
         g.setStroke(oldStroke);
         return;
      }
      final double ux = vx / vlen;
      final double uy = vy / vlen;

      final int startX = (int) Math.round(playerMini.getX() + ux * MINIMAP_ARROW_START);
      final int startY = (int) Math.round(playerMini.getY() + uy * MINIMAP_ARROW_START);
      final int rimX = (int) Math.round(playerMini.getX() + ux * MINIMAP_RIM_RADIUS);
      final int rimY = (int) Math.round(playerMini.getY() + uy * MINIMAP_RIM_RADIUS);

      drawMinimapArrow(g, startX, startY, rimX, rimY, color);
      g.drawImage(pin, rimX - pin.getWidth() / 2, rimY - pin.getHeight() / 2, null);

      // Compact distance badge inward from the rim marker (e.g. "483t").
      final String distLabel = Math.round(tileDist) + "t";
      g.setFont(FontManager.getRunescapeSmallFont());
      final FontMetrics fm = g.getFontMetrics();
      final int textX = (int) Math.round(playerMini.getX() + ux * (MINIMAP_RIM_RADIUS - 16)) - fm.stringWidth(distLabel) / 2;
      final int textY = (int) Math.round(playerMini.getY() + uy * (MINIMAP_RIM_RADIUS - 16)) + fm.getAscent() / 2;
      g.setColor( Color.BLACK);
      g.drawString(distLabel, textX + 1, textY + 1);
      g.setColor(color);
      g.drawString(distLabel, textX, textY);

      g.setStroke(oldStroke);
   }

   private void drawMinimapPath(final Graphics2D g, final WorldView wv, final Point playerMini,
      final List<WorldPoint> path) {
      g.setStroke(MINI_STROKE);
      g.setColor(PATH_COLOR);
      Point prev = playerMini;
      for (final WorldPoint wp : path) {
         final LocalPoint lp = LocalPoint.fromWorld(wv, wp);
         if (lp == null) {
            continue;
         }
         final Point cur = Perspective.localToMinimap(client, lp);
         if (cur != null && prev != null) {
            g.drawLine(prev.getX(), prev.getY(), cur.getX(), cur.getY());
            prev = cur;
         }
      }
   }

   private static void drawMinimapArrow(final Graphics2D g, final int x1, final int y1, final int x2, final int y2,
      final Color color) {
      g.setStroke(MINI_OUTLINE_STROKE);
      g.setColor(OUTLINE);
      g.drawLine(x1, y1, x2, y2);
      TargetHighlightOverlay.drawArrowHead(g, x1, y1, x2, y2, 8);

      g.setStroke(MINI_STROKE);
      g.setColor(color);
      g.drawLine(x1, y1, x2, y2);
      TargetHighlightOverlay.drawArrowHead(g, x1, y1, x2, y2, 7);
   }

   private Shape worldMapClipArea(final Rectangle base) {
      final Area area = new Area(base);
      final Widget overview = client.getWidget(InterfaceID.Worldmap.OVERVIEW_CONTAINER);
      if (overview != null && !overview.isHidden()) {
         area.subtract(new Area(overview.getBounds()));
      }
      final Widget selector = client.getWidget(InterfaceID.Worldmap.MAPLIST_BOX_GRAPHIC0);
      if (selector != null && !selector.isHidden()) {
         area.subtract(new Area(selector.getBounds()));
      }
      return area;
   }
}
