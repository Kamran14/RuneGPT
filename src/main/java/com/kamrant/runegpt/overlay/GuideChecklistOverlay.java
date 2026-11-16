package com.kamrant.runegpt.overlay;

import com.kamrant.runegpt.RuneGPTConfig;
import com.kamrant.runegpt.model.GuideItem;
import com.kamrant.runegpt.model.GuideItemKind;
import com.kamrant.runegpt.model.GuidePlan;
import com.kamrant.runegpt.model.WorldTarget;
import com.kamrant.runegpt.service.GuideTracker;
import com.kamrant.runegpt.service.TextUtils;
import com.kamrant.runegpt.ui.UiTheme;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.List;
import javax.inject.Inject;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

/**
 * In-game checklist window for the tracked guide: the current step in orange/red (with completed step count),
 * each item coloured by status (green ready, yellow equip it, red missing / in bank), and where to go with
 * live distance and direction.
 */
public class GuideChecklistOverlay extends OverlayPanel {

   private static final int MAX_NAME_CHARS = 22;

   private final GuideTracker tracker;
   private final RuneGPTConfig config;

   @Inject
   public GuideChecklistOverlay(final GuideTracker tracker, final RuneGPTConfig config) {
      this.tracker = tracker;
      this.config = config;
      setPosition(OverlayPosition.TOP_LEFT);
      panelComponent.setPreferredSize(new Dimension(205, 0));
   }

   @Override
   public Dimension render(final Graphics2D g) {
      final GuidePlan plan = tracker.getActive();
      if (plan == null || !config.showGuideOverlay()) {
         return null;
      }
      panelComponent.getChildren().add(TitleComponent.builder()
         .text(TextUtils.shorten(plan.getTitle(), 28))
         .color(ColorScheme.BRAND_ORANGE)
         .build());

      final List<String> steps = plan.getSteps();
      if (!steps.isEmpty()) {
         final int cur = plan.getCurrentStep();
         if (cur >= steps.size()) {
            panelComponent.getChildren().add(LineComponent.builder()
               .left("✓ All " + steps.size() + " steps done")
               .leftColor(UiTheme.READY_GREEN)
               .build());
         } else {
            if (steps.size() > 1) {
               panelComponent.getChildren().add(LineComponent.builder()
                  .left("Step " + (cur + 1) + "/" + steps.size())
                  .leftColor(UiTheme.ACTIVE_STEP_ORANGE)
                  .right(cur > 0 ? cur + " done ✓" : "")
                  .rightColor(UiTheme.READY_GREEN)
                  .build());
            }
            panelComponent.getChildren().add(LineComponent.builder()
               .left("▶ " + steps.get(cur))
               .leftColor(UiTheme.ACTIVE_STEP_ORANGE)
               .build());
         }
      }

      int ready = 0;
      for (final GuideItem item : plan.getItems()) {
         if (item.getStatus().isReady()) {
            ready++;
         }
         final String name = (item.getQuantity() > 1 ? item.getQuantity() + " x " : "") + item.getName();
         panelComponent.getChildren().add(LineComponent.builder()
            .left((item.getKind() == GuideItemKind.EQUIP ? "⚔ " : "") + TextUtils.shorten(name, MAX_NAME_CHARS))
            .leftColor(item.getStatus().getColor())
            .right(item.getStatus().getLabel())
            .rightColor(item.getStatus().getColor())
            .build());
      }
      if (!plan.getItems().isEmpty()) {
         panelComponent.getChildren().add(LineComponent.builder()
            .left(UiTheme.LBL_READY)
            .leftColor(UiTheme.OVERLAY_MUTED)
            .right(ready + "/" + plan.getItems().size())
            .rightColor(ready == plan.getItems().size() ? UiTheme.READY_GREEN : UiTheme.OVERLAY_MUTED)
            .build());
      }

      final WorldTarget where = plan.getLocation() != null ? plan.getLocation() : plan.getNpc();
      if (where != null) {
         panelComponent.getChildren().add(LineComponent.builder()
            .left(UiTheme.LBL_GO_TO + ": " + TextUtils.shorten(where.getName(), MAX_NAME_CHARS)
               + (where.isApproximate() ? " (approx.)" : ""))
            .leftColor(UiTheme.ACCENT_CYAN)
            .build());
         final String dir = tracker.directionText();
         if (!dir.isEmpty()) {
            panelComponent.getChildren().add(LineComponent.builder().left(dir).leftColor(UiTheme.OVERLAY_MUTED).build());
         }
      }
      if (plan.getNpc() != null && plan.getLocation() != null) {
         panelComponent.getChildren().add(LineComponent.builder()
            .left(UiTheme.LBL_FIND + ": " + TextUtils.shorten(plan.getNpc().getName(), MAX_NAME_CHARS))
            .leftColor(UiTheme.ACCENT_CYAN)
            .build());
      }
      return super.render(g);
   }
}
