package com.kamrant.runegpt.overlay;

import com.kamrant.runegpt.service.ShortestPathBridge;
import com.kamrant.runegpt.service.TargetHighlighter;
import com.kamrant.runegpt.ui.UiTheme;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.TileObject;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

/**
 * In-game tile & entity marker overlay for the active guide step:
 * <ol>
 *    <li>only renders in the 3D scene once the player is within local range ({@link TargetHighlighter#isCloseEnough()});
 *    when far away, the minimap marker guides the player instead so buildings are never obstructed;</li>
 *    <li>draws the collision-checked optimal walkable path around walls, buildings and obstacles;</li>
 *    <li>highlights the target NPC hull, interactable object clickbox, or destination tile.</li>
 * </ol>
 */
public class TargetHighlightOverlay extends Overlay {

   private static final Color TILE_BORDER = UiTheme.ACCENT_CYAN;
   private static final Color TILE_FILL = UiTheme.ACCENT_CYAN_FILL;
   private static final Color ZONE_BORDER = UiTheme.ACCENT_CYAN_OUTLINE;
   private static final Color PATH_TILE_BORDER = UiTheme.ACTIVE_STEP_BORDER;
   private static final Color PATH_TILE_FILL = UiTheme.ACTIVE_STEP_FILL;
   private static final Stroke TILE_STROKE = new BasicStroke(2f);
   private static final Stroke PATH_TILE_STROKE = new BasicStroke(1.4f);
   private static final Stroke ZONE_STROKE = new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f,
      new float[] { 5f, 5f }, 0f);
   private static final Stroke PATH_LINE_STROKE = new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f,
      new float[] { 6f, 5f }, 0f);
   private static final Stroke SOLID = new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);

   /** Interval in steps along the walkable path at which stepping-stone tiles are highlighted. */
   private static final int PATH_TILE_STEP = 2;

   private final Client client;
   private final TargetHighlighter highlighter;
   private final ShortestPathBridge shortestPath;

   @Inject
   public TargetHighlightOverlay(final Client client, final TargetHighlighter highlighter,
      final ShortestPathBridge shortestPath) {
      this.client = client;
      this.highlighter = highlighter;
      this.shortestPath = shortestPath;
      setPosition(OverlayPosition.DYNAMIC);
      setLayer(OverlayLayer.ABOVE_SCENE);
   }

   @Override
   public Dimension render(final Graphics2D g) {
      // When the Shortest Path plugin draws the route, keep only the target outline, not a second walking path.
      final List<WorldPoint> path = shortestPath.isRouting() ? Collections.emptyList() : highlighter.getWalkablePath();
      if (path.isEmpty() && !highlighter.isCloseEnough()) {
         return null;
      }
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

      if (!path.isEmpty()) {
         renderWalkablePath(g, path);
      }

      final NPC npc = highlighter.getTargetNpc();
      if (npc != null) {
         renderNpc(g, npc);
         return null;
      }

      final TileObject obj = highlighter.getTargetObject();
      if (obj != null) {
         renderObject(g, obj, highlighter.getTargetObjectName());
         return null;
      }

      final WorldPoint tile = highlighter.getTargetTile();
      if (tile != null) {
         final LocalPoint lp = LocalPoint.fromWorld(client.getTopLevelWorldView(), tile);
         if (lp != null) {
            renderDestinationTile(g, lp);
         }
      }
      return null;
   }

   /**
    * Renders the collision-checked walkable path around buildings and obstacles: a dashed ground path line
    * through each walkable tile center plus stepping-stone tile markers every {@link #PATH_TILE_STEP} tiles.
    */
   private void renderWalkablePath(final Graphics2D g, final List<WorldPoint> path) {
      final Player local = client.getLocalPlayer();
      if (local == null) {
         return;
      }
      final WorldView wv = client.getTopLevelWorldView();
      final int plane = wv.getPlane();
      final Stroke old = g.getStroke();

      // 1. Stepping-stone walkable tiles (skip the last tile so the cyan destination highlight stands out).
      g.setStroke(PATH_TILE_STROKE);
      for (int i = 1; i + 1 < path.size(); i += PATH_TILE_STEP) {
         final LocalPoint lp = LocalPoint.fromWorld(wv, path.get(i));
         if (lp != null) {
            final Polygon poly = Perspective.getCanvasTilePoly(client, lp);
            if (poly != null) {
               g.setColor(PATH_TILE_FILL);
               g.fillPolygon(poly);
               g.setColor(PATH_TILE_BORDER);
               g.drawPolygon(poly);
            }
         }
      }

      // 2. Connected dashed path line from player along the walkable path.
      g.setStroke(PATH_LINE_STROKE);
      g.setColor(PATH_TILE_BORDER);
      Point prev = Perspective.localToCanvas(client, local.getLocalLocation(), plane);
      Point prevValid = null;
      Point lastValid = prev;
      for (final WorldPoint wp : path) {
         final LocalPoint lp = LocalPoint.fromWorld(wv, wp);
         if (lp == null) {
            continue;
         }
         final Point cur = Perspective.localToCanvas(client, lp, plane);
         if (cur != null && prev != null) {
            g.drawLine(prev.getX(), prev.getY(), cur.getX(), cur.getY());
            prevValid = prev;
            lastValid = cur;
         }
         if (cur != null) {
            prev = cur;
         }
      }
      if (prevValid != null && lastValid != null) {
         g.setStroke(SOLID);
         drawArrowHead(g, prevValid.getX(), prevValid.getY(), lastValid.getX(), lastValid.getY(), 7);
      }
      g.setStroke(old);
   }

   private void renderNpc(final Graphics2D g, final NPC npc) {
      final Polygon tile = npc.getCanvasTilePoly();
      if (tile != null) {
         renderHighlightedTile(g, tile, TILE_FILL, TILE_BORDER);
      }
      final Shape hull = npc.getConvexHull();
      if (hull != null) {
         g.setColor(TILE_FILL);
         g.fill(hull);
         g.setColor(TILE_BORDER);
         g.draw(hull);
      }
      final String label = stepLabel(npc.getName());
      final Point text = npc.getCanvasTextLocation(g, label, npc.getLogicalHeight() + 40);
      if (text != null) {
         OverlayUtil.renderTextLocation(g, text, label, TILE_BORDER);
      }
   }

   private void renderObject(final Graphics2D g, final TileObject obj, final String objName) {
      final Polygon tile = obj.getCanvasTilePoly();
      if (tile != null) {
         renderHighlightedTile(g, tile, TILE_FILL, TILE_BORDER);
      }
      final Shape clickbox = obj.getClickbox();
      if (clickbox != null) {
         g.setColor(TILE_FILL);
         g.fill(clickbox);
         g.setColor(TILE_BORDER);
         g.draw(clickbox);
      }
      final String label = stepLabel(objName != null ? objName : "Interact here");
      final Point text = obj.getCanvasTextLocation(g, label, 40);
      if (text != null) {
         OverlayUtil.renderTextLocation(g, text, label, TILE_BORDER);
      }
   }

   private void renderDestinationTile(final Graphics2D g, final LocalPoint lp) {
      // 3x3 dashed outer zone + solid highlighted center tile.
      final Polygon area = Perspective.getCanvasTileAreaPoly(client, lp, 3);
      if (area != null) {
         final Stroke old = g.getStroke();
         g.setStroke(ZONE_STROKE);
         g.setColor(ZONE_BORDER);
         g.drawPolygon(area);
         g.setStroke(old);
      }
      final Polygon poly = Perspective.getCanvasTilePoly(client, lp);
      if (poly != null) {
         renderHighlightedTile(g, poly, TILE_FILL, TILE_BORDER);
      }
      final String label = stepLabel(highlighter.getTargetName() != null ? highlighter.getTargetName() : "Go here");
      final Point text = Perspective.getCanvasTextLocation(client, g, lp, label, 0);
      if (text != null) {
         OverlayUtil.renderTextLocation(g, text, label, TILE_BORDER);
      }
   }

   private static void renderHighlightedTile(final Graphics2D g, final Polygon poly, final Color fill,
      final Color border) {
      final Stroke old = g.getStroke();
      g.setColor(fill);
      g.fillPolygon(poly);
      g.setStroke(TILE_STROKE);
      g.setColor(border);
      g.drawPolygon(poly);
      g.setStroke(old);
   }

   private String stepLabel(final String target) {
      return "Step " + highlighter.getStepNumber() + ": " + target;
   }

   static void drawArrowHead(final Graphics2D g, final int x1, final int y1, final int x2, final int y2, final int size) {
      final double angle = Math.atan2(y2 - y1, x2 - x1);
      final int ax1 = (int) Math.round(x2 - size * Math.cos(angle - Math.PI / 6));
      final int ay1 = (int) Math.round(y2 - size * Math.sin(angle - Math.PI / 6));
      final int ax2 = (int) Math.round(x2 - size * Math.cos(angle + Math.PI / 6));
      final int ay2 = (int) Math.round(y2 - size * Math.sin(angle + Math.PI / 6));
      final Polygon head = new Polygon(new int[] { x2, ax1, ax2 }, new int[] { y2, ay1, ay2 }, 3);
      g.fillPolygon(head);
   }
}
