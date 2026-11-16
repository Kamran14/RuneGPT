package com.kamrant.runegpt.overlay;

import com.kamrant.runegpt.service.Navigation;
import com.kamrant.runegpt.service.TargetHighlighter;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
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
 *    <li>draws highlighted stepping-stone ground tiles leading from the player toward the active step's target;</li>
 *    <li>highlights the target tile itself (plus a 3x3 beacon zone for destination spots) and any matching
 *    NPC hull or interactable object clickbox;</li>
 *    <li>labels the marker with the active step number and target name.</li>
 * </ol>
 */
public class TargetHighlightOverlay extends Overlay {

   private static final Color TILE_BORDER = new Color(80, 220, 255);
   private static final Color TILE_FILL = new Color(80, 220, 255, 55);
   private static final Color ZONE_BORDER = new Color(80, 220, 255, 110);
   private static final Color PATH_TILE_BORDER = new Color(255, 145, 55, 200);
   private static final Color PATH_TILE_FILL = new Color(255, 145, 55, 45);
   private static final Color DIRECTION_COLOR = new Color(255, 145, 55);
   private static final Stroke TILE_STROKE = new BasicStroke(2f);
   private static final Stroke ZONE_STROKE = new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f,
      new float[] { 5f, 5f }, 0f);
   private static final Stroke DASH = new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f,
      new float[] { 6f, 6f }, 0f);
   private static final Stroke SOLID = new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);

   /** Interval in tiles between highlighted stepping-stone path tiles. */
   private static final int PATH_TILE_STEP = 3;
   /** Maximum distance ahead (in tiles) to draw stepping-stone path tiles from the player. */
   private static final int MAX_PATH_TILES_AHEAD = 18;

   private final Client client;
   private final TargetHighlighter highlighter;

   @Inject
   public TargetHighlightOverlay(final Client client, final TargetHighlighter highlighter) {
      this.client = client;
      this.highlighter = highlighter;
      setPosition(OverlayPosition.DYNAMIC);
      setLayer(OverlayLayer.ABOVE_SCENE);
   }

   @Override
   public Dimension render(final Graphics2D g) {
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

      final WorldPoint dest = highlighter.getDestination();
      if (dest != null) {
         renderSteppingStoneTiles(g, dest);
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
            return null;
         }
      }

      if (dest != null) {
         renderDirectionalGuide(g, dest);
      }
      return null;
   }

   /** Draws highlighted stepping-stone tiles every few tiles from the player toward {@code dest}. */
   private void renderSteppingStoneTiles(final Graphics2D g, final WorldPoint dest) {
      final Player local = client.getLocalPlayer();
      if (local == null) {
         return;
      }
      final WorldPoint here = local.getWorldLocation();
      final int dx = dest.getX() - here.getX();
      final int dy = dest.getY() - here.getY();
      final double totalDist = Math.hypot(dx, dy);
      if (totalDist < PATH_TILE_STEP + 1) {
         return;
      }

      final WorldView wv = client.getTopLevelWorldView();
      final int maxDist = (int) Math.min(totalDist - 1, MAX_PATH_TILES_AHEAD);
      final Stroke old = g.getStroke();
      g.setStroke(new BasicStroke(1.5f));
      for (int d = PATH_TILE_STEP; d <= maxDist; d += PATH_TILE_STEP) {
         final int tx = here.getX() + (int) Math.round((dx / totalDist) * d);
         final int ty = here.getY() + (int) Math.round((dy / totalDist) * d);
         final LocalPoint lp = LocalPoint.fromWorld(wv, new WorldPoint(tx, ty, wv.getPlane()));
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
      g.setStroke(old);
   }

   private void renderNpc(final Graphics2D g, final NPC npc) {
      final LocalPoint npcLp = npc.getLocalLocation();
      if (npcLp != null) {
         drawLineFromPlayer(g, npcLp, TILE_BORDER);
      }
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
      final LocalPoint objLp = obj.getLocalLocation();
      if (objLp != null) {
         drawLineFromPlayer(g, objLp, TILE_BORDER);
      }
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
      drawLineFromPlayer(g, lp, TILE_BORDER);

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

   private void renderDirectionalGuide(final Graphics2D g, final WorldPoint dest) {
      final Player local = client.getLocalPlayer();
      if (local == null) {
         return;
      }
      final WorldPoint here = local.getWorldLocation();
      final int dx = dest.getX() - here.getX();
      final int dy = dest.getY() - here.getY();
      final double len = Math.hypot(dx, dy);
      if (len < 2) {
         return;
      }
      final LocalPoint playerLp = local.getLocalLocation();
      final int stepX = (int) Math.round((dx / len) * 4 * Perspective.LOCAL_TILE_SIZE);
      final int stepY = (int) Math.round((dy / len) * 4 * Perspective.LOCAL_TILE_SIZE);
      final LocalPoint tipLp = new LocalPoint(playerLp.getX() + stepX, playerLp.getY() + stepY,
         client.getTopLevelWorldView());

      final int plane = client.getTopLevelWorldView().getPlane();
      final Point from = Perspective.localToCanvas(client, playerLp, plane);
      final Point to = Perspective.localToCanvas(client, tipLp, plane);
      if (from == null || to == null) {
         return;
      }

      final Stroke old = g.getStroke();
      g.setStroke(DASH);
      g.setColor(DIRECTION_COLOR);
      g.drawLine(from.getX(), from.getY(), to.getX(), to.getY());
      g.setStroke(SOLID);
      drawArrowHead(g, from.getX(), from.getY(), to.getX(), to.getY(), 9);
      g.setStroke(old);

      final String name = highlighter.getTargetName() != null ? highlighter.getTargetName() : "Target";
      final String dist = Navigation.describe(here, dest);
      final String label = stepLabel(dist.isEmpty() ? name : name + " (" + dist + ")");
      OverlayUtil.renderTextLocation(g, new Point(to.getX() + 6, to.getY()), label, DIRECTION_COLOR);
   }

   private String stepLabel(final String target) {
      return "Step " + highlighter.getStepNumber() + ": " + target;
   }

   private void drawLineFromPlayer(final Graphics2D g, final LocalPoint target, final Color color) {
      final Player local = client.getLocalPlayer();
      if (local == null) {
         return;
      }
      final int plane = client.getTopLevelWorldView().getPlane();
      final Point from = Perspective.localToCanvas(client, local.getLocalLocation(), plane);
      final Point to = Perspective.localToCanvas(client, target, plane);
      if (from != null && to != null) {
         final Stroke old = g.getStroke();
         g.setStroke(DASH);
         g.setColor(color);
         g.drawLine(from.getX(), from.getY(), to.getX(), to.getY());
         g.setStroke(SOLID);
         drawArrowHead(g, from.getX(), from.getY(), to.getX(), to.getY(), 8);
         g.setStroke(old);
      }
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
