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
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

/**
 * Quest-Helper-style 3D scene guider: highlights the target NPC (hull + tile + line) or destination tile
 * when nearby, and when the destination is far away draws a directional ground arrow from the player's
 * feet pointing toward the destination with its name and distance.
 */
public class TargetHighlightOverlay extends Overlay {

   private static final Color COLOR = new Color(80, 220, 255);
   private static final Color FILL = new Color(80, 220, 255, 45);
   private static final Color DIRECTION_COLOR = new Color(255, 145, 55);
   private static final Stroke DASH = new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f,
      new float[] { 6f, 6f }, 0f);
   private static final Stroke SOLID = new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
   private static final int COMPASS_TILES = 4;

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
      final NPC npc = highlighter.getTargetNpc();
      if (npc != null) {
         renderNpc(g, npc);
         return null;
      }
      final WorldPoint tile = highlighter.getTargetTile();
      if (tile != null) {
         final LocalPoint lp = LocalPoint.fromWorld(client.getTopLevelWorldView(), tile);
         if (lp != null) {
            drawLineFromPlayer(g, lp, COLOR);
            final Polygon poly = Perspective.getCanvasTilePoly(client, lp);
            if (poly != null) {
               OverlayUtil.renderPolygon(g, poly, COLOR);
            }
            final String label = highlighter.getTargetName() != null ? highlighter.getTargetName() : "Go here";
            final Point text = Perspective.getCanvasTextLocation(client, g, lp, label, 0);
            if (text != null) {
               OverlayUtil.renderTextLocation(g, text, label, COLOR);
            }
            return null;
         }
      }
      // Target is outside the loaded scene: draw a Quest-Helper-style directional arrow from the player.
      final WorldPoint dest = highlighter.getDestination();
      if (dest != null) {
         renderDirectionalGuide(g, dest);
      }
      return null;
   }

   private void renderNpc(final Graphics2D g, final NPC npc) {
      final LocalPoint npcLp = npc.getLocalLocation();
      if (npcLp != null) {
         drawLineFromPlayer(g, npcLp, COLOR);
         final Polygon tile = Perspective.getCanvasTilePoly(client, npcLp);
         if (tile != null) {
            OverlayUtil.renderPolygon(g, tile, COLOR);
         }
      }
      final Shape hull = npc.getConvexHull();
      if (hull != null) {
         g.setColor(FILL);
         g.fill(hull);
         g.setColor(COLOR);
         g.draw(hull);
      }
      final String label = "Guide: " + npc.getName();
      final Point text = npc.getCanvasTextLocation(g, label, npc.getLogicalHeight() + 40);
      if (text != null) {
         OverlayUtil.renderTextLocation(g, text, label, COLOR);
      }
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
      final int stepX = (int) Math.round((dx / len) * COMPASS_TILES * Perspective.LOCAL_TILE_SIZE);
      final int stepY = (int) Math.round((dy / len) * COMPASS_TILES * Perspective.LOCAL_TILE_SIZE);
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
      final String label = dist.isEmpty() ? name : name + " (" + dist + ")";
      OverlayUtil.renderTextLocation(g, new Point(to.getX() + 6, to.getY()), label, DIRECTION_COLOR);
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
