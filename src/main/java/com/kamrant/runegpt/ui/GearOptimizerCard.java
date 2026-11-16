package com.kamrant.runegpt.ui;

import com.kamrant.runegpt.model.GearResult;
import com.kamrant.runegpt.model.GearSetup;
import com.kamrant.runegpt.model.GuidePlan;
import com.kamrant.runegpt.model.ItemHoldings;
import com.kamrant.runegpt.model.ItemSource;
import com.kamrant.runegpt.model.MonsterStats;
import com.kamrant.runegpt.model.OwnedGear;
import com.kamrant.runegpt.service.GearOptimizer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.FontManager;

/**
 * Renders a {@link GearResult} inside the chat: monster defences and weakness from the OSRS Wiki, a
 * DPS comparison across Melee / Ranged / Magic, the best owned item for every slot, and a Track setup
 * button that pins the loadout to the live checklist and in-game overlay.
 */
public final class GearOptimizerCard {

   private GearOptimizerCard() {
   }

   public static String header(final GearResult result) {
      return result.isSuccess() ? UiTheme.HDR_GEAR_WIKI : UiTheme.HDR_GEAR;
   }

   public static JComponent build(final GearResult result, final ItemHoldings holdings,
      final BiFunction<GearSetup, ItemHoldings, GuidePlan> toPlan, final Consumer<GuidePlan> onTrack) {
      final JPanel p = CardWidgets.column();
      if (!result.isSuccess()) {
         CardWidgets.addLeft(p, CardWidgets.text(result.getError(), UiTheme.TEXT_PRIMARY));
         return p;
      }

      final MonsterStats m = result.getMonster();
      CardWidgets.addLeft(p, CardWidgets.title(m.getName() + (m.getCombatLevel() > 0 ? " (lvl " + m.getCombatLevel() + ")" : "")
         + (result.isOnTask() ? " · on task" : "")));
      CardWidgets.addLeft(p, CardWidgets.small(monsterSummary(m)));

      final List<GearSetup> setups = result.getSetups();
      CardWidgets.addLeft(p, Box.createVerticalStrut(6));
      CardWidgets.addLeft(p, CardWidgets.label("Styles compared (from your gear)"));
      for (int i = 0; i < setups.size(); i++) {
         CardWidgets.addLeft(p, styleSummaryRow(setups.get(i), i == 0));
      }

      // Active setup detail panel; when multiple styles are available, pill buttons switch between them.
      final JPanel detailHolder = new JPanel(new BorderLayout());
      detailHolder.setOpaque(false);
      if (setups.size() > 1) {
         CardWidgets.addLeft(p, Box.createVerticalStrut(6));
         final JPanel switcher = new JPanel(new GridLayout(1, setups.size(), 4, 0));
         switcher.setOpaque(false);
         for (final GearSetup s : setups) {
            final JButton tab = CardWidgets.pillButton(s.getStyle().getLabel());
            tab.addActionListener(e -> showSetup(detailHolder, result, s, holdings, toPlan, onTrack));
            switcher.add(tab);
         }
         CardWidgets.addLeft(p, switcher);
      }
      showSetup(detailHolder, result, setups.get(0), holdings, toPlan, onTrack);
      CardWidgets.addLeft(p, detailHolder);

      if (!result.getHidden().isEmpty()) {
         CardWidgets.addLeft(p, Box.createVerticalStrut(4));
         CardWidgets.addLeft(p, CardWidgets.small("Skipped (level too low): " + String.join(", ", result.getHidden()) + "."));
      }
      if (!result.isBankKnown()) {
         CardWidgets.addLeft(p, Box.createVerticalStrut(4));
         CardWidgets.addLeft(p, CardWidgets.small(UiTheme.NOTE_OPEN_BANK_GEAR));
      }
      return p;
   }

   private static void showSetup(final JPanel holder, final GearResult result, final GearSetup setup,
      final ItemHoldings holdings, final BiFunction<GearSetup, ItemHoldings, GuidePlan> toPlan,
      final Consumer<GuidePlan> onTrack) {
      holder.removeAll();
      final JPanel body = CardWidgets.column();

      CardWidgets.addLeft(body, Box.createVerticalStrut(6));
      CardWidgets.addLeft(body, CardWidgets.label(setup.getStyle().getLabel() + " loadout · "
         + String.format(Locale.ROOT, "%.2f DPS", setup.getDps())));
      CardWidgets.addLeft(body, CardWidgets.small(setup.getAttack() + " · max hit " + setup.getMaxHit() + " · "
         + Math.round(setup.getHitChance() * 100) + "% accuracy"));
      CardWidgets.addLeft(body, Box.createVerticalStrut(4));

      for (final Map.Entry<Integer, OwnedGear> e : setup.getItems().entrySet()) {
         CardWidgets.addLeft(body, gearRow(GearOptimizer.slotName(e.getKey()), e.getValue()));
      }
      if (!setup.getCarry().isEmpty()) {
         CardWidgets.addLeft(body, Box.createVerticalStrut(4));
         CardWidgets.addLeft(body, CardWidgets.small(UiTheme.LBL_BRING + ": " + String.join(", ", setup.getCarry())));
      }

      CardWidgets.addLeft(body, Box.createVerticalStrut(8));
      final JButton track = CardWidgets.pillButton(
         "Track " + setup.getStyle().getLabel().toLowerCase(Locale.ROOT) + " setup");
      track.setToolTipText(UiTheme.TIP_TRACK_GUIDE);
      track.addActionListener(e -> onTrack.accept(toPlan.apply(setup, holdings)));
      CardWidgets.addLeft(body, CardWidgets.buttonRow(track,
         CardWidgets.wikiButton(result.getWikiUrl(), UiTheme.BTN_MONSTER_WIKI)));

      holder.add(body, BorderLayout.CENTER);
      holder.revalidate();
      holder.repaint();
   }

   private static JComponent styleSummaryRow(final GearSetup s, final boolean winner) {
      final HtmlPane row = new HtmlPane(winner ? UiTheme.ACCENT_CYAN : UiTheme.TEXT_PRIMARY);
      final String prefix = winner ? "★ " : "• ";
      row.setHtml(prefix + "<b>" + MarkdownRenderer.escape(s.getStyle().getLabel()) + "</b>: "
         + String.format(Locale.ROOT, "<b>%.2f DPS</b>", s.getDps())
         + " <span class=\"muted\">(max " + s.getMaxHit() + ", "
         + Math.round(s.getHitChance() * 100) + "% hit, "
         + MarkdownRenderer.escape(s.getWeaponName()) + ")</span>");
      return row;
   }

   private static JComponent gearRow(final String slot, final OwnedGear g) {
      final Color color = g.getSource() == ItemSource.WORN ? UiTheme.READY_GREEN
         : g.getSource() == ItemSource.INVENTORY ? UiTheme.EQUIP_YELLOW : UiTheme.BANK_ORANGE;
      final String status = g.getSource() == ItemSource.WORN ? "equipped"
         : g.getSource() == ItemSource.INVENTORY ? "equip it" : "in bank";

      final JLabel dot = new JLabel("●");
      dot.setForeground(color);
      dot.setFont(FontManager.getRunescapeBoldFont());
      dot.setBorder(new EmptyBorder(0, 0, 0, 6));

      final HtmlPane text = new HtmlPane(UiTheme.TEXT_PRIMARY);
      text.setHtml("<span class=\"muted\">" + MarkdownRenderer.escape(slot) + ":</span> "
         + MarkdownRenderer.escape(g.getName())
         + " <span class=\"muted\">" + status + "</span>");
      text.setForeground(color.brighter());

      final JPanel row = new JPanel(new BorderLayout());
      row.setOpaque(false);
      row.add(dot, BorderLayout.WEST);
      row.add(text, BorderLayout.CENTER);
      return row;
   }

   private static String monsterSummary(final MonsterStats m) {
      final List<String> parts = new ArrayList<>();
      parts.add("HP " + m.getHitpoints() + ", Def " + m.getDefenceLevel());
      parts.add("weakest def: " + weakestDefence(m));
      if (m.getWeakness() != null && m.getWeaknessPercent() > 0) {
         parts.add(m.getWeaknessPercent() + "% " + m.getWeakness() + " weakness");
      }
      if (!m.getAttributes().isEmpty()) {
         parts.add(String.join("/", m.getAttributes()));
      }
      return String.join(" · ", parts);
   }

   private static String weakestDefence(final MonsterStats m) {
      final String[] names = { "stab", "slash", "crush", "magic", "bolts", "arrows", "darts" };
      final int[] vals = {
         m.getDefStab(), m.getDefSlash(), m.getDefCrush(), m.getDefMagic(),
         m.getDefHeavy(), m.getDefStandard(), m.getDefLight()
      };
      int bestIdx = 0;
      for (int i = 1; i < vals.length; i++) {
         if (vals[i] < vals[bestIdx]) {
            bestIdx = i;
         }
      }
      return names[bestIdx] + " (" + (vals[bestIdx] >= 0 ? "+" : "") + vals[bestIdx] + ")";
   }
}
