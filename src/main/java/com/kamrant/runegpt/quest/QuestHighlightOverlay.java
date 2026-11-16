package com.kamrant.runegpt.quest;

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
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

/** Outlines the quest target NPC, marks its tile and draws a dashed line to it from the player. */
public class QuestHighlightOverlay extends Overlay {

   private static final Color COLOR = new Color(80, 220, 255);
   private static final Color FILL = new Color(80, 220, 255, 40);
   private static final Stroke DASH = new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f,
      new float[] { 6f, 6f }, 0f);

   private final Client client;
   private final QuestHighlighter highlighter;

   @Inject
   public QuestHighlightOverlay(final Client client, final QuestHighlighter highlighter) {
      this.client = client;
      this.highlighter = highlighter;
      setPosition(OverlayPosition.DYNAMIC);
      setLayer(OverlayLayer.ABOVE_SCENE);
   }

   @Override
   public Dimension render(final Graphics2D g) {
      final NPC npc = highlighter.getTarget();
      if (npc == null) {
         return null;
      }

      final LocalPoint npcLp = npc.getLocalLocation();
      final Player local = client.getLocalPlayer();
      if (local != null && npcLp != null) {
         final Point from = Perspective.localToCanvas(client, local.getLocalLocation(), client.getPlane());
         final Point to = Perspective.localToCanvas(client, npcLp, client.getPlane());
         if (from != null && to != null) {
            final Stroke old = g.getStroke();
            g.setStroke(DASH);
            g.setColor(COLOR);
            g.drawLine(from.getX(), from.getY(), to.getX(), to.getY());
            g.setStroke(old);
         }
      }

      if (npcLp != null) {
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

      final String label = "Quest: talk to " + npc.getName();
      final Point text = npc.getCanvasTextLocation(g, label, npc.getLogicalHeight() + 40);
      if (text != null) {
         OverlayUtil.renderTextLocation(g, text, label, COLOR);
      }
      return null;
   }
}
