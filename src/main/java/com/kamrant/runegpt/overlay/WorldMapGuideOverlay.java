package com.kamrant.runegpt.overlay;

import com.kamrant.runegpt.model.WorldTarget;
import com.kamrant.runegpt.service.GuideTracker;
import com.kamrant.runegpt.service.TargetHighlighter;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.Area;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.worldmap.WorldMapOverlay;

/**
 * Quest-Helper-style map guider: draws a directional arrow on the minimap pointing toward the tracked
 * destination, and when the World Map is open draws a route line with arrowheads from the player's
 * position to the destination marker.
 */
public class WorldMapGuideOverlay extends Overlay {

   private static final Color WIKI_COLOR = new Color(80, 220, 255);
   private static final Color APPROX_COLOR = new Color(210, 210, 210);
   private static final Color OUTLINE = new Color(0, 0, 0, 180);
   private static final Stroke MAP_DASH = new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f,
      new float[] { 7f, 6f }, 0f);
   private static final Stroke MINI_STROKE = new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
   private static final int MINIMAP_EDGE_TILES = 14;

   private final Client client;
   private final GuideTracker tracker;
   private final TargetHighlighter highlighter;
   private final WorldMapOverlay worldMapOverlay;

   @Inject
   public WorldMapGuideOverlay(final Client client, final GuideTracker tracker, final TargetHighlighter highlighter,
      final WorldMapOverlay worldMapOverlay) {
      this.client = client;
      this.tracker = tracker;
      this.highlighter = highlighter;
      this.worldMapOverlay = worldMapOverlay;
      setPosition(OverlayPosition.DYNAMIC);
      setPriority(PRIORITY_HIGH);
      setLayer(OverlayLayer.ABOVE_WIDGETS);
      drawAfterInterface(InterfaceID.WORLDMAP);
   }

   @Override
   public Dimension render(final Graphics2D g) {
      final WorldTarget target = tracker.getMapTarget();
      final WorldPoint dest = target != null ? target.getPoint() : highlighter.getDestination();
      final Player local = client.getLocalPlayer();
      if (dest == null || local == null) {
         return null;
      }
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      final Color color = target != null && target.isApproximate() ? APPROX_COLOR : WIKI_COLOR;

      final Widget mapContainer = client.getWidget(InterfaceID.Worldmap.MAP_CONTAINER);
      if (mapContainer != null && !mapContainer.isHidden()) {
         renderOnWorldMap(g, mapContainer, local.getWorldLocation(), dest, color);
      } else {
         renderOnMinimap(g, local, dest, color);
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

   private void renderOnMinimap(final Graphics2D g, final Player local, final WorldPoint dest, final Color color) {
      final WorldPoint here = local.getWorldLocation();
      final int dx = dest.getX() - here.getX();
      final int dy = dest.getY() - here.getY();
      final double dist = Math.hypot(dx, dy);
      if (dist < 2) {
         return;
      }

      final LocalPoint playerLp = local.getLocalLocation();
      final Point playerMini = Perspective.localToMinimap(client, playerLp);
      if (playerMini == null) {
         return;
      }

      // If the destination tile is inside the minimap, point directly to it; otherwise point to the minimap edge.
      Point targetMini = null;
      final LocalPoint destLp = LocalPoint.fromWorld(client.getTopLevelWorldView(), dest);
      if (destLp != null) {
         targetMini = Perspective.localToMinimap(client, destLp);
      }
      final boolean onMinimap = targetMini != null;
      if (targetMini == null) {
         final int stepX = (int) Math.round((dx / dist) * MINIMAP_EDGE_TILES * Perspective.LOCAL_TILE_SIZE);
         final int stepY = (int) Math.round((dy / dist) * MINIMAP_EDGE_TILES * Perspective.LOCAL_TILE_SIZE);
         final LocalPoint edgeLp = new LocalPoint(playerLp.getX() + stepX, playerLp.getY() + stepY,
            client.getTopLevelWorldView());
         targetMini = Perspective.localToMinimap(client, edgeLp, Integer.MAX_VALUE);
      }
      if (targetMini == null) {
         return;
      }

      // Start the arrow a few pixels outside the player dot so the center stays uncluttered.
      final double vx = targetMini.getX() - playerMini.getX();
      final double vy = targetMini.getY() - playerMini.getY();
      final double vlen = Math.hypot(vx, vy);
      if (vlen < 6) {
         return;
      }
      final int startX = (int) Math.round(playerMini.getX() + (vx / vlen) * (onMinimap ? 4 : Math.max(8, vlen - 18)));
      final int startY = (int) Math.round(playerMini.getY() + (vy / vlen) * (onMinimap ? 4 : Math.max(8, vlen - 18)));

      final Stroke old = g.getStroke();
      g.setStroke( new BasicStroke(3.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
      g.setColor(OUTLINE);
      g.drawLine(startX, startY, targetMini.getX(), targetMini.getY());

      g.setStroke(MINI_STROKE);
      g.setColor(color);
      g.drawLine(startX, startY, targetMini.getX(), targetMini.getY());
      TargetHighlightOverlay.drawArrowHead(g, startX, startY, targetMini.getX(), targetMini.getY(), 7);
      if (onMinimap) {
         g.fillOval(targetMini.getX() - 3, targetMini.getY() - 3, 6, 6);
      }
      g.setStroke(old);
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
