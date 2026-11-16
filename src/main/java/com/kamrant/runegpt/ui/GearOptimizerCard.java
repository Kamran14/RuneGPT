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
import java.awt.Component;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.LinkBrowser;

/**
 * Renders a {@link GearResult} inside the chat: monster defences and weakness from the OSRS Wiki, a
 * DPS comparison across Melee / Ranged / Magic, the best owned item for every slot, and a Track setup
 * button that pins the loadout to the live checklist and in-game overlay.
 */
public final class GearOptimizerCard {

   private static final Color GREEN = new Color(90, 200, 90);
   private static final Color YELLOW = new Color(240, 200, 60);
   private static final Color BANK_ORANGE = new Color(235, 145, 70);
   private static final Color ACCENT = new Color(80, 220, 255);

   private GearOptimizerCard() {
   }

   public static String header(final GearResult result) {
      return result.isSuccess() ? "Gear optimizer · OSRS Wiki" : "Gear optimizer";
   }

   public static JComponent build(final GearResult result, final ItemHoldings holdings,
      final BiFunction<GearSetup, ItemHoldings, GuidePlan> toPlan, final Consumer<GuidePlan> onTrack) {
      final JPanel p = column();
      if (!result.isSuccess()) {
         add(p, text(result.getError(), ChatView.TEXT));
         return p;
      }

      final MonsterStats m = result.getMonster();
      add(p, title(m.getName() + (m.getCombatLevel() > 0 ? " (lvl " + m.getCombatLevel() + ")" : "")
         + (result.isOnTask() ? " · on task" : "")));
      add(p, small(monsterSummary(m)));

      final List<GearSetup> setups = result.getSetups();
      add(p, Box.createVerticalStrut(6));
      add(p, label("Styles compared (from your gear)"));
      for (int i = 0; i < setups.size(); i++) {
         add(p, styleSummaryRow(setups.get(i), i == 0));
      }

      // Active setup detail panel; when multiple styles are available, pill buttons switch between them.
      final JPanel detailHolder = new JPanel(new BorderLayout());
      detailHolder.setOpaque(false);
      if (setups.size() > 1) {
         add(p, Box.createVerticalStrut(6));
         final JPanel switcher = new JPanel(new GridLayout(1, setups.size(), 4, 0));
         switcher.setOpaque(false);
         for (final GearSetup s : setups) {
            final JButton tab = GuidesView.pillButton(s.getStyle().getLabel());
            tab.addActionListener(e -> showSetup(detailHolder, result, s, holdings, toPlan, onTrack));
            switcher.add(tab);
         }
         add(p, switcher);
      }
      showSetup(detailHolder, result, setups.get(0), holdings, toPlan, onTrack);
      add(p, detailHolder);

      if (!result.getHidden().isEmpty()) {
         add(p, Box.createVerticalStrut(4));
         add(p, small("Skipped (level too low): " + String.join(", ", result.getHidden()) + "."));
      }
      if (!result.isBankKnown()) {
         add(p, Box.createVerticalStrut(4));
         add(p, small("Open your bank once so I can include banked gear."));
      }
      return p;
   }

   private static void showSetup(final JPanel holder, final GearResult result, final GearSetup setup,
      final ItemHoldings holdings, final BiFunction<GearSetup, ItemHoldings, GuidePlan> toPlan,
      final Consumer<GuidePlan> onTrack) {
      holder.removeAll();
      final JPanel body = column();

      add(body, Box.createVerticalStrut(6));
      add(body, label(setup.getStyle().getLabel() + " loadout · "
         + String.format(Locale.ROOT, "%.2f DPS", setup.getDps())));
      add(body, small(setup.getAttack() + " · max hit " + setup.getMaxHit() + " · "
         + Math.round(setup.getHitChance() * 100) + "% accuracy"));
      add(body, Box.createVerticalStrut(4));

      for (final Map.Entry<Integer, OwnedGear> e : setup.getItems().entrySet()) {
         add(body, gearRow(GearOptimizer.slotName(e.getKey()), e.getValue()));
      }
      if (!setup.getCarry().isEmpty()) {
         add(body, Box.createVerticalStrut(4));
         add(body, small("Bring: " + String.join(", ", setup.getCarry())));
      }

      add(body, Box.createVerticalStrut(8));
      final JButton track = GuidesView.pillButton("Track " + setup.getStyle().getLabel().toLowerCase(Locale.ROOT) + " setup");
      track.setToolTipText("Live checklist, in-game overlay, map marker and NPC highlight");
      track.addActionListener(e -> onTrack.accept(toPlan.apply(setup, holdings)));
      final JButton wiki = GuidesView.pillButton("Monster wiki");
      wiki.addActionListener(e -> LinkBrowser.browse(result.getWikiUrl()));
      add(body, buttons(track, wiki));

      holder.add(body, BorderLayout.CENTER);
      holder.revalidate();
      holder.repaint();
   }

   private static JComponent styleSummaryRow(final GearSetup s, final boolean winner) {
      final HtmlPane row = new HtmlPane(winner ? ACCENT : ChatView.TEXT);
      final String prefix = winner ? "★ " : "• ";
      row.setHtml(prefix + "<b>" + MarkdownRenderer.escape(s.getStyle().getLabel()) + "</b>: "
         + String.format(Locale.ROOT, "<b>%.2f DPS</b>", s.getDps())
         + " <span class=\"muted\">(max " + s.getMaxHit() + ", "
         + Math.round(s.getHitChance() * 100) + "% hit, "
         + MarkdownRenderer.escape(s.getWeaponName()) + ")</span>");
      return row;
   }

   private static JComponent gearRow(final String slot, final OwnedGear g) {
      final Color color = g.getSource() == ItemSource.WORN ? GREEN
         : g.getSource() == ItemSource.INVENTORY ? YELLOW : BANK_ORANGE;
      final String status = g.getSource() == ItemSource.WORN ? "equipped"
         : g.getSource() == ItemSource.INVENTORY ? "equip it" : "in bank";

      final JLabel dot = new JLabel("●");
      dot.setForeground(color);
      dot.setFont(FontManager.getRunescapeBoldFont());
      dot.setBorder(new EmptyBorder(0, 0, 0, 6));

      final HtmlPane text = new HtmlPane(ChatView.TEXT);
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

   private static JPanel column() {
      final JPanel p = new JPanel();
      p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
      p.setOpaque(false);
      return p;
   }

   private static JComponent title(final String text) {
      final HtmlPane t = new HtmlPane(ColorScheme.BRAND_ORANGE, FontManager.getRunescapeBoldFont());
      t.setPlainText(text);
      return t;
   }

   private static JComponent label(final String text) {
      final HtmlPane l = new HtmlPane(ColorScheme.MEDIUM_GRAY_COLOR.brighter(), FontManager.getRunescapeSmallFont());
      l.setPlainText(text);
      return l;
   }

   private static JComponent text(final String text, final Color color) {
      final HtmlPane t = new HtmlPane(color);
      t.setPlainText(text);
      return t;
   }

   private static JComponent small(final String text) {
      final HtmlPane t = new HtmlPane(ColorScheme.LIGHT_GRAY_COLOR, FontManager.getRunescapeSmallFont());
      t.setPlainText(text);
      return t;
   }

   private static JComponent buttons(final JButton... buttons) {
      final JPanel row = new JPanel(new GridLayout(1, buttons.length, 4, 0));
      row.setOpaque(false);
      for (final JButton b : buttons) {
         row.add(b);
      }
      return row;
   }

   private static void add(final JPanel p, final Component c) {
      if (c instanceof JComponent) {
         ((JComponent) c).setAlignmentX(Component.LEFT_ALIGNMENT);
      }
      p.add(c);
   }
}
