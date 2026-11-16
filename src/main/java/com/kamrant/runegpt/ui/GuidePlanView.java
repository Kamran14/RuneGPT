package com.kamrant.runegpt.ui;

import com.kamrant.runegpt.model.GuideItem;
import com.kamrant.runegpt.model.GuideItemKind;
import com.kamrant.runegpt.model.GuidePlan;
import com.kamrant.runegpt.model.WorldTarget;
import com.kamrant.runegpt.service.PriceService;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * Renders a {@link GuidePlan}: numbered steps coloured by progress (green = completed, orange/red = current
 * task, grey = upcoming), then a colour-coded checklist split into what to wear and what to bring with live
 * GE prices, then where to go and who to find.
 */
public final class GuidePlanView {

   private static final Color TARGET = new Color(80, 220, 255);
   private static final Color STEP_DONE = new Color(90, 200, 90);
   private static final Color STEP_CURRENT = new Color(255, 115, 50);
   private static final Color STEP_CURRENT_BG = new Color(68, 36, 22);
   private static final Color STEP_UPCOMING = new Color(165, 165, 165);

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
      final JPanel p = new JPanel();
      p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
      p.setOpaque(false);

      final List<String> steps = plan.getSteps();
      if (showSteps && !steps.isEmpty()) {
         if (steps.size() > 1) {
            add(p, label(stepProgressTitle(plan)));
            add(p, Box.createVerticalStrut(2));
         }
         final int cur = plan.getCurrentStep();
         for (int i = 0; i < steps.size(); i++) {
            add(p, stepRow(i, steps.get(i), cur, steps.size(), onSelectStep));
            if (i + 1 < steps.size()) {
               add(p, Box.createVerticalStrut(2));
            }
         }
      }

      final List<GuideItem> bring = new ArrayList<>();
      final List<GuideItem> wear = new ArrayList<>();
      for (final GuideItem item : plan.getItems()) {
         (item.getKind() == GuideItemKind.EQUIP ? wear : bring).add(item);
      }
      if (!wear.isEmpty()) {
         add(p, Box.createVerticalStrut(4));
         add(p, label("Wear"));
         wear.forEach(item -> add(p, itemRow(item)));
      }
      if (!bring.isEmpty()) {
         add(p, Box.createVerticalStrut(4));
         add(p, label("Bring"));
         bring.forEach(item -> add(p, itemRow(item)));
      }
      final long missingGp = PriceService.missingCost(plan.getItems());
      if (missingGp > 0) {
         add(p, Box.createVerticalStrut(3));
         add(p, small("Missing on GE: ~" + PriceService.formatShort(missingGp)));
      }

      if (plan.getLocation() != null || plan.getNpc() != null) {
         add(p, Box.createVerticalStrut(4));
      }
      if (plan.getLocation() != null) {
         add(p, target("Go to", plan.getLocation()));
      }
      if (plan.getNpc() != null) {
         add(p, target("Find", plan.getNpc()));
      }
      if (plan.getRemovedUnverified() > 0) {
         add(p, Box.createVerticalStrut(4));
         add(p, small("Hid " + plan.getRemovedUnverified() + " suggestion(s) the OSRS Wiki has no page for."));
      }
      if (action != null) {
         add(p, Box.createVerticalStrut(8));
         final JPanel row = new JPanel(new GridLayout(1, 1));
         row.setOpaque(false);
         row.add(action);
         add(p, row);
      }
      return p;
   }

   /** "Step 6 of 6 (5 done)" summary for multi-step guides. */
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

   /** "3/5 ready" summary for headers. */
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

      final Color color = done ? STEP_DONE : current ? STEP_CURRENT : STEP_UPCOMING;
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
         final RoundedPanel hi = new RoundedPanel(new BorderLayout(), STEP_CURRENT_BG, 8);
         hi.setOutline(new Color(255, 115, 50, 110));
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
      final HtmlPane text = new HtmlPane(ChatView.TEXT);
      text.setHtml(MarkdownRenderer.escape(name) + " <span class=\"muted\">"
         + MarkdownRenderer.escape(item.getStatus().getLabel() + priceNote) + "</span>");
      text.setForeground(color.brighter());

      final JPanel row = new JPanel(new BorderLayout());
      row.setOpaque(false);
      row.add(dot, BorderLayout.WEST);
      row.add(text, BorderLayout.CENTER);
      return row;
   }

   private static JComponent target(final String label, final WorldTarget t) {
      final String note = t.isApproximate() ? " (approx. location)" : t.getPoint() == null ? " (no map marker)" : "";
      final HtmlPane pane = new HtmlPane(TARGET);
      pane.setHtml("<span class=\"lbl\">" + label + ":</span> <b>" + MarkdownRenderer.escape(t.getName()) + "</b>"
         + "<span class=\"muted\">" + MarkdownRenderer.escape(note) + "</span>");
      return pane;
   }

   private static JComponent label(final String text) {
      final HtmlPane l = new HtmlPane(ColorScheme.MEDIUM_GRAY_COLOR.brighter(), FontManager.getRunescapeSmallFont());
      l.setPlainText(text);
      return l;
   }

   private static JComponent small(final String text) {
      final HtmlPane l = new HtmlPane(ColorScheme.LIGHT_GRAY_COLOR, FontManager.getRunescapeSmallFont());
      l.setPlainText(text);
      return l;
   }

   private static void add(final JPanel p, final Component c) {
      if (c instanceof JComponent) {
         ((JComponent) c).setAlignmentX(Component.LEFT_ALIGNMENT);
      }
      p.add(c);
   }
}
