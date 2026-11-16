package com.kamrant.runegpt.overlay;

import com.kamrant.runegpt.RuneGPTConfig;
import com.kamrant.runegpt.model.GuideItem;
import com.kamrant.runegpt.model.GuideItemKind;
import com.kamrant.runegpt.model.GuidePlan;
import com.kamrant.runegpt.model.WorldTarget;
import com.kamrant.runegpt.service.GuideTracker;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

/**
 * In-game checklist for the tracked guide: each item coloured by status (green ready, yellow equip it,
 * red missing / in bank), then where to go with live distance and direction.
 */
public class GuideChecklistOverlay extends OverlayPanel {

   private static final Color MUTED = new Color(190, 190, 190);
   private static final Color TARGET = new Color(80, 220, 255);
   private static final int MAX_NAME_CHARS = 22;

   private final GuideTracker tracker;
   private final RuneGPTConfig config;

   @Inject
   public GuideChecklistOverlay(final GuideTracker tracker, final RuneGPTConfig config) {
      this.tracker = tracker;
      this.config = config;
      setPosition(OverlayPosition.TOP_LEFT);
      panelComponent.setPreferredSize(new Dimension(190, 0));
   }

   @Override
   public Dimension render(final Graphics2D g) {
      final GuidePlan plan = tracker.getActive();
      if (plan == null || !config.showGuideOverlay()) {
         return null;
      }
      panelComponent.getChildren().add(TitleComponent.builder()
         .text(shorten(plan.getTitle(), 28))
         .color(ColorScheme.BRAND_ORANGE)
         .build());

      int ready = 0;
      for (final GuideItem item : plan.getItems()) {
         if (item.getStatus().isReady()) {
            ready++;
         }
         final String name = (item.getQuantity() > 1 ? item.getQuantity() + " x " : "") + item.getName();
         panelComponent.getChildren().add(LineComponent.builder()
            .left((item.getKind() == GuideItemKind.EQUIP ? "⚔ " : "") + shorten(name, MAX_NAME_CHARS))
            .leftColor(item.getStatus().getColor())
            .right(item.getStatus().getLabel())
            .rightColor(item.getStatus().getColor())
            .build());
      }
      if (!plan.getItems().isEmpty()) {
         panelComponent.getChildren().add(LineComponent.builder()
            .left("Ready")
            .leftColor(MUTED)
            .right(ready + "/" + plan.getItems().size())
            .rightColor(ready == plan.getItems().size() ? new Color(90, 200, 90) : MUTED)
            .build());
      }

      final WorldTarget where = plan.getLocation() != null ? plan.getLocation() : plan.getNpc();
      if (where != null) {
         panelComponent.getChildren().add(LineComponent.builder()
            .left("Go to: " + shorten(where.getName(), MAX_NAME_CHARS) + (where.isApproximate() ? " (approx.)" : ""))
            .leftColor(TARGET)
            .build());
         final String dir = tracker.directionText();
         if (!dir.isEmpty()) {
            panelComponent.getChildren().add(LineComponent.builder().left(dir).leftColor(MUTED).build());
         }
      }
      if (plan.getNpc() != null && plan.getLocation() != null) {
         panelComponent.getChildren().add(LineComponent.builder()
            .left("Find: " + shorten(plan.getNpc().getName(), MAX_NAME_CHARS))
            .leftColor(TARGET)
            .build());
      }
      return super.render(g);
   }

   private static String shorten(final String s, final int max) {
      return s.length() <= max ? s : s.substring(0, max - 1) + "…";
   }
}
