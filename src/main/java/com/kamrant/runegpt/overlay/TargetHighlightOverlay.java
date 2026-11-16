package com.kamrant.runegpt.overlay;

import com.kamrant.runegpt.service.TargetHighlighter;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
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
 * Highlights the active guide's target in the scene: the NPC (outline, tile, label) or, when no NPC is
 * loaded, the location tile; plus a dashed line from the player to it.
 */
public class TargetHighlightOverlay extends Overlay {

   private static final Color COLOR = new Color(80, 220, 255);
   private static final Color FILL = new Color(80, 220, 255, 40);
   private static final Stroke DASH = new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f,
      new float[] { 6f, 6f }, 0f);

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
      final NPC npc = highlighter.getTargetNpc();
      if (npc != null) {
         renderNpc(g, npc);
         return null;
      }
      final WorldPoint tile = highlighter.getTargetTile();
      if (tile != null) {
         final LocalPoint lp = LocalPoint.fromWorld(client, tile);
         if (lp != null) {
            drawLineFromPlayer(g, lp);
            final Polygon poly = Perspective.getCanvasTilePoly(client, lp);
            if (poly != null) {
               OverlayUtil.renderPolygon(g, poly, COLOR);
            }
            final Point text = Perspective.getCanvasTextLocation(client, g, lp, "Go here", 0);
            if (text != null) {
               OverlayUtil.renderTextLocation(g, text, "Go here", COLOR);
            }
         }
      }
      return null;
   }

   private void renderNpc(final Graphics2D g, final NPC npc) {
      final LocalPoint npcLp = npc.getLocalLocation();
      if (npcLp != null) {
         drawLineFromPlayer(g, npcLp);
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

   private void drawLineFromPlayer(final Graphics2D g, final LocalPoint target) {
      final Player local = client.getLocalPlayer();
      if (local == null) {
         return;
      }
      final Point from = Perspective.localToCanvas(client, local.getLocalLocation(), client.getPlane());
      final Point to = Perspective.localToCanvas(client, target, client.getPlane());
      if (from != null && to != null) {
         final Stroke old = g.getStroke();
         g.setStroke(DASH);
         g.setColor(COLOR);
         g.drawLine(from.getX(), from.getY(), to.getX(), to.getY());
         g.setStroke(old);
      }
   }
}
