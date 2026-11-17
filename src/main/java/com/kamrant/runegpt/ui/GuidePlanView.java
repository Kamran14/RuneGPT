package com.kamrant.runegpt.ui;

import com.kamrant.runegpt.model.GuideItem;
import com.kamrant.runegpt.model.GuideItemKind;
import com.kamrant.runegpt.model.GuidePlan;
import com.kamrant.runegpt.model.ItemStatus;
import com.kamrant.runegpt.service.PriceService;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.FontManager;

/**
 * Renders a {@link GuidePlan}: numbered steps coloured by progress (green = completed, orange/red = current
 * task, grey = upcoming), then a colour-coded checklist split into what to wear and what to bring with live
 * GE prices, then where to go and who to find.
 */
public final class GuidePlanView {

   private GuidePlanView() {
   }

   /**
    * @param showSteps false in the compact tracking panel when space is tight
    * @param action    button under the plan (e.g. "Track guide"); null for none
    */
   public static JComponent build(final GuidePlan plan, final boolean showSteps, final JButton action) {
      return build(plan, showSteps, action, null);
   }

   /**
    * @param onSelectStep optional callback when the player clicks a step row to mark it as the current step
    */
   public static JComponent build(final GuidePlan plan, final boolean showSteps, final JButton action,
      final IntConsumer onSelectStep) {
      final JPanel p = CardWidgets.column();

      final List<String> steps = plan.getSteps();
      if (showSteps && !steps.isEmpty()) {
         if (steps.size() > 1) {
            CardWidgets.addLeft(p, CardWidgets.label(stepProgressTitle(plan)));
            CardWidgets.addLeft(p, Box.createVerticalStrut(2));
         }
         final int cur = plan.getCurrentStep();
         for (int i = 0; i < steps.size(); i++) {
            CardWidgets.addLeft(p, stepRow(i, steps.get(i), cur, steps.size(), onSelectStep));
            if (i + 1 < steps.size()) {
               CardWidgets.addLeft(p, Box.createVerticalStrut(2));
            }
         }
      }

      final List<GuideItem> bring = new ArrayList<>();
      final List<GuideItem> wear = new ArrayList<>();
      for (final GuideItem item : plan.getItems()) {
         (item.getKind() == GuideItemKind.EQUIP ? wear : bring).add(item);
      }
      if (!wear.isEmpty()) {
         CardWidgets.addLeft(p, Box.createVerticalStrut(4));
         CardWidgets.addLeft(p, CardWidgets.label(UiTheme.LBL_WEAR));
         wear.forEach(item -> CardWidgets.addLeft(p, itemRow(item)));
      }
      if (!bring.isEmpty()) {
         CardWidgets.addLeft(p, Box.createVerticalStrut(4));
         CardWidgets.addLeft(p, CardWidgets.label(UiTheme.LBL_BRING));
         bring.forEach(item -> CardWidgets.addLeft(p, itemRow(item)));
      }
      if (plan.getItems().stream().anyMatch(item -> item.getStatus() == ItemStatus.UNKNOWN)) {
         CardWidgets.addLeft(p, Box.createVerticalStrut(3));
         CardWidgets.addLeft(p, CardWidgets.small(UiTheme.NOTE_OPEN_BANK_ITEMS));
      }
      final long missingGp = PriceService.missingCost(plan.getItems());
      if (missingGp > 0) {
         CardWidgets.addLeft(p, Box.createVerticalStrut(3));
         CardWidgets.addLeft(p, CardWidgets.small("Missing on GE: ~" + PriceService.formatShort(missingGp)));
      }

      if (plan.getLocation() != null || plan.getNpc() != null) {
         CardWidgets.addLeft(p, Box.createVerticalStrut(4));
      }
      if (plan.getLocation() != null) {
         CardWidgets.addLeft(p, CardWidgets.targetRow(UiTheme.LBL_GO_TO, plan.getLocation()));
      }
      if (plan.getNpc() != null) {
         CardWidgets.addLeft(p, CardWidgets.targetRow(UiTheme.LBL_FIND, plan.getNpc()));
      }
      if (plan.getRemovedUnverified() > 0) {
         CardWidgets.addLeft(p, Box.createVerticalStrut(4));
         CardWidgets.addLeft(p, CardWidgets.small(
            "Hid " + plan.getRemovedUnverified() + " suggestion(s) the OSRS Wiki has no page for."));
      }
      if (action != null) {
         CardWidgets.addLeft(p, Box.createVerticalStrut(8));
         CardWidgets.addLeft(p, CardWidgets.buttonRow(action));
      }
      return p;
   }

   /** "Progress · Step 6 of 6 (5 completed)" summary for multi-step guides. */
   public static String stepProgressTitle(final GuidePlan plan) {
      final int total = plan.getSteps().size();
      if (total == 0) {
         return "";
      }
      final int done = Math.min(plan.getCurrentStep(), total);
      if (done >= total) {
         return "Progress · All " + total + " steps completed";
      }
      return "Progress · Step " + (done + 1) + " of " + total
         + (done > 0 ? " (" + done + " completed)" : "");
   }

   /** "step 6/6 · 3/5 items ready" summary for headers. */
   public static String readySummary(final GuidePlan plan) {
      final List<String> parts = new ArrayList<>();
      if (plan.getSteps().size() > 1) {
         final int done = Math.min(plan.getCurrentStep(), plan.getSteps().size());
         parts.add(done >= plan.getSteps().size()
            ? "all steps done"
            : "step " + (done + 1) + "/" + plan.getSteps().size());
      }
      if (!plan.getItems().isEmpty()) {
         final long ready = plan.getItems().stream().filter(i -> i.getStatus().isReady()).count();
         parts.add(ready + "/" + plan.getItems().size() + " items ready");
      }
      return String.join("  ·  ", parts);
   }

   private static JComponent stepRow(final int idx, final String step, final int currentStep, final int totalSteps,
      final IntConsumer onSelectStep) {
      final boolean done = totalSteps > 1 && idx < currentStep;
      final boolean current = totalSteps == 1 || idx == currentStep;

      final Color color = done ? UiTheme.READY_GREEN : current ? UiTheme.ACTIVE_STEP_ORANGE : UiTheme.UPCOMING_GREY;
      final String marker = done ? "✓ " : current ? "▶ " : "";
      final String prefix = (idx + 1) + ". " + marker;

      final HtmlPane pane = new HtmlPane(color);
      final String body = MarkdownRenderer.inlineHtml(step);
      pane.setHtml(current
         ? "<b>" + MarkdownRenderer.escape(prefix) + body + "</b>"
         : MarkdownRenderer.escape(prefix) + body);
      pane.setForeground(color);

      final JComponent container;
      if (current && totalSteps > 1) {
         final RoundedPanel hi = new RoundedPanel(new BorderLayout(), UiTheme.ACTIVE_STEP_BG, 8);
         hi.setOutline(UiTheme.ACTIVE_STEP_OUTLINE);
         hi.setBorder(new EmptyBorder(3, 5, 3, 5));
         hi.add(pane, BorderLayout.CENTER);
         container = hi;
      } else {
         final JPanel plain = new JPanel(new BorderLayout());
         plain.setOpaque(false);
         plain.setBorder(new EmptyBorder(1, 2, 1, 2));
         plain.add(pane, BorderLayout.CENTER);
         container = plain;
      }

      if (onSelectStep != null && totalSteps > 1) {
         pane.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
         pane.setToolTipText("Click to set Step " + (idx + 1) + " as current");
         pane.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(final MouseEvent e) {
               onSelectStep.accept(idx);
            }
         });
      }
      return container;
   }

   private static JComponent itemRow(final GuideItem item) {
      final Color color = item.getStatus().getColor();
      final JLabel dot = new JLabel("●");
      dot.setForeground(color);
      dot.setFont(FontManager.getRunescapeBoldFont());
      dot.setBorder(new EmptyBorder(0, 0, 0, 6));

      final String name = (item.getQuantity() > 1 ? item.getQuantity() + " x " : "") + item.getName();
      final String priceNote = item.getTotalPrice() > 0 ? " · " + PriceService.formatShort(item.getTotalPrice()) : "";
      final HtmlPane text = new HtmlPane(UiTheme.TEXT_PRIMARY);
      text.setHtml(MarkdownRenderer.escape(name) + " <span class=\"muted\">"
         + MarkdownRenderer.escape(item.getStatus().getLabel() + priceNote) + "</span>");
      text.setForeground(color.brighter());

      final JPanel row = new JPanel(new BorderLayout());
      row.setOpaque(false);
      row.add(dot, BorderLayout.WEST);
      row.add(text, BorderLayout.CENTER);
      return row;
   }
}
