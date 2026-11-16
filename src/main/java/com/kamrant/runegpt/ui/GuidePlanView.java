package com.kamrant.runegpt.ui;

import com.kamrant.runegpt.model.GuideItem;
import com.kamrant.runegpt.model.GuideItemKind;
import com.kamrant.runegpt.model.GuidePlan;
import com.kamrant.runegpt.model.WorldTarget;
import com.kamrant.runegpt.service.PriceService;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;
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
 * Renders a {@link GuidePlan}: numbered steps, then a colour-coded checklist split into what to bring and
 * what to wear (green ready, yellow equip it, red missing / in bank) with live GE prices, then where to go
 * and who to find.
 */
public final class GuidePlanView {

   private static final Color TARGET = new Color(80, 220, 255);

   private GuidePlanView() {
   }

   /**
    * @param showSteps false in the compact tracking panel when space is tight
    * @param action    button under the plan (e.g. "Track guide"); null for none
    */
   public static JComponent build(final GuidePlan plan, final boolean showSteps, final JButton action) {
      final JPanel p = new JPanel();
      p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
      p.setOpaque(false);

      if (showSteps && !plan.getSteps().isEmpty()) {
         final StringBuilder html = new StringBuilder("<ol>");
         for (final String step : plan.getSteps()) {
            html.append("<li>").append(MarkdownRenderer.inlineHtml(step)).append("</li>");
         }
         final HtmlPane steps = new HtmlPane(ChatView.TEXT);
         steps.setHtml(html.append("</ol>").toString());
         add(p, steps);
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

   /** "3/5 ready" summary for headers. */
   public static String readySummary(final GuidePlan plan) {
      if (plan.getItems().isEmpty()) {
         return "";
      }
      final long ready = plan.getItems().stream().filter(i -> i.getStatus().isReady()).count();
      return ready + "/" + plan.getItems().size() + " ready";
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
