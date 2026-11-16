package com.kamrant.runegpt.ui;

import com.kamrant.runegpt.model.GuidePlan;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.IntConsumer;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * Pinned panel for the guide being tracked: "Tracking: title", step & item progress summary, Stop, and
 * (expanded) the colour-coded steps (completed green, current orange/red), live item checklist, Prev/Next
 * step controls and where to go. Hidden when nothing is tracked.
 */
public class TrackedGuideView extends RoundedPanel {

   private final JLabel title = new JLabel();
   private final JLabel summary = new JLabel();
   private final JLabel chevron = new JLabel();
   private final JPanel body = new JPanel(new BorderLayout());
   private final IntConsumer onSelectStep;
   private boolean expanded = true;
   private GuidePlan plan;

   public TrackedGuideView(final Runnable onStop, final IntConsumer onSelectStep) {
      super(new BorderLayout(0, 6), UiTheme.TRACKED_BG, 12);
      this.onSelectStep = onSelectStep;
      setOutline(UiTheme.ACCENT_CYAN_OUTLINE);
      setBorder(new EmptyBorder(7, 8, 8, 8));

      title.setFont(FontManager.getRunescapeBoldFont());
      title.setForeground(UiTheme.ACCENT_CYAN);
      summary.setFont(FontManager.getRunescapeSmallFont());
      summary.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
      chevron.setFont(FontManager.getRunescapeBoldFont());
      chevron.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

      final JButton stop = CardWidgets.pillButton(UiTheme.BTN_STOP);
      stop.setToolTipText(UiTheme.TIP_STOP_TRACKING);
      stop.addActionListener(e -> onStop.run());

      final JPanel text = new JPanel(new BorderLayout());
      text.setOpaque(false);
      text.add(title, BorderLayout.NORTH);
      text.add(summary, BorderLayout.SOUTH);

      final JPanel right = new JPanel(new BorderLayout(6, 0));
      right.setOpaque(false);
      right.add(chevron, BorderLayout.WEST);
      right.add(stop, BorderLayout.EAST);

      final JPanel header = new JPanel(new BorderLayout(6, 0));
      header.setOpaque(false);
      header.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      header.add(text, BorderLayout.CENTER);
      header.add(right, BorderLayout.EAST);
      header.addMouseListener(new MouseAdapter() {
         @Override
         public void mouseClicked(final MouseEvent e) {
            expanded = !expanded;
            render();
         }
      });

      body.setOpaque(false);
      add(header, BorderLayout.NORTH);
      add(body, BorderLayout.CENTER);
      setVisible(false);
   }

   /** Show the tracked plan (with fresh item statuses and step index), or hide when null. */
   public void setPlan(final GuidePlan plan) {
      this.plan = plan;
      render();
   }

   private void render() {
      if (plan == null) {
         setVisible(false);
         return;
      }
      setVisible(true);
      title.setText("Tracking: " + plan.getTitle());
      title.setToolTipText(plan.getTitle());
      final String ready = GuidePlanView.readySummary(plan);
      summary.setText(ready.isEmpty() ? "See overlay & map guider in game" : ready + "  ·  overlay active");
      chevron.setText(expanded ? "▴" : "▾");
      body.removeAll();
      if (expanded) {
         final JPanel col = CardWidgets.column();
         CardWidgets.addLeft(col, GuidePlanView.build(plan, true, null, onSelectStep));

         if (plan.getSteps().size() > 1) {
            CardWidgets.addLeft(col, Box.createVerticalStrut(6));
            final int cur = plan.getCurrentStep();
            final int total = plan.getSteps().size();
            final JButton prev = CardWidgets.pillButton(UiTheme.BTN_PREV_STEP);
            prev.setEnabled(cur > 0);
            prev.addActionListener(e -> onSelectStep.accept(Math.max(0, cur - 1)));

            final JButton next = CardWidgets.pillButton(cur < total ? UiTheme.BTN_NEXT_STEP : UiTheme.BTN_ALL_DONE);
            next.setEnabled(cur < total);
            next.addActionListener(e -> onSelectStep.accept(Math.min(total, cur + 1)));

            CardWidgets.addLeft(col, CardWidgets.buttonRow(prev, next));
         }
         body.add(col, BorderLayout.CENTER);
      }
      body.setVisible(expanded);
      revalidate();
      repaint();
   }
}
