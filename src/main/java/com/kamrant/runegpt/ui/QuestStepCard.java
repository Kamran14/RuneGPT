package com.kamrant.runegpt.ui;

import com.kamrant.runegpt.quest.QuestStep;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.GridLayout;
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

/** Renders a {@link QuestStep} as a compact card inside the chat. */
public final class QuestStepCard {

   private static final Color GOOD = new Color(90, 190, 90);
   private static final Color WARN = ColorScheme.BRAND_ORANGE;
   private static final Color BAD = new Color(220, 80, 70);

   public interface Actions {
      /** Highlight (or clear, when npc is null) the quest target in-game. */
      void highlight(String quest, String npc);

      /** Player finished the step; find the next one. */
      void nextStep(String quest, String completedStep);

      /** Player picked a suggested real quest name. */
      void useQuest(String quest);
   }

   private QuestStepCard() {
   }

   public static String header(final QuestStep step) {
      switch (step.getKind()) {
         case STEP:
            return "Quest helper · " + step.getProvider() + " · Wiki";
         case NOT_A_QUEST:
            return "Quest helper";
         default:
            return "Quest helper  ·  " + step.getQuest();
      }
   }

   public static JComponent build(final QuestStep step, final Actions actions) {
      final JPanel p = column();
      switch (step.getKind()) {
         case NOT_A_QUEST:
            buildNotAQuest(p, step, actions);
            break;
         case COMPLETE:
            p.add(title(step.getQuest()));
            p.add(text("You've already completed this quest.", ChatView.TEXT));
            p.add(gap(6));
            p.add(buttons(wikiButton(step.getWikiUrl(), "Open on wiki")));
            break;
         case ERROR:
            p.add(text(step.getMessage(), ChatView.TEXT));
            break;
         default:
            buildStep(p, step, actions);
      }
      return p;
   }

   private static void buildNotAQuest(final JPanel p, final QuestStep step, final Actions actions) {
      p.add(text("“" + step.getInput() + "” isn't a quest in OSRS.", ChatView.TEXT));
      if (!step.getWikiMatches().isEmpty()) {
         p.add(gap(4));
         p.add(small("On the OSRS Wiki that name matches: " + String.join(", ", step.getWikiMatches()) + "."));
      }
      if (!step.getSuggestions().isEmpty()) {
         p.add(gap(6));
         p.add(label("Did you mean"));
         for (final String quest : step.getSuggestions()) {
            final JButton b = GuidesView.pillButton(quest);
            b.addActionListener(e -> actions.useQuest(quest));
            p.add(gap(3));
            p.add(full(b));
         }
      }
   }

   private static void buildStep(final JPanel p, final QuestStep step, final Actions actions) {
      p.add(title(step.getQuest()));

      if (!step.getProgress().isEmpty()) {
         p.add(gap(4));
         p.add(label("Where you are" + confidenceSuffix(step.getConfidence())));
         p.add(text(step.getProgress(), ChatView.TEXT));
      }

      p.add(gap(6));
      p.add(label("Next step"));
      p.add(text(step.getNextStep(), Color.WHITE));

      if (!step.getLocation().isEmpty()) {
         p.add(gap(4));
         p.add(fact("Go to", step.getLocation()));
      }
      if (step.getNpc() != null) {
         p.add(fact("Talk to", step.getNpc() + (step.isNpcNearby() ? "  (nearby)" : "")));
      }

      if (!step.getItems().isEmpty()) {
         p.add(gap(6));
         p.add(label("Items needed"));
         for (final QuestStep.ItemCheck item : step.getItems()) {
            p.add(itemRow(item));
         }
         if (!step.isBankKnown()) {
            p.add(small("Open your bank once so I can check it too."));
         }
      }

      if (!step.getThen().isEmpty()) {
         p.add(gap(6));
         p.add(label("After that"));
         final StringBuilder html = new StringBuilder("<ol>");
         for (final String t : step.getThen()) {
            html.append("<li>").append(MarkdownRenderer.escape(t)).append("</li>");
         }
         final HtmlPane list = new HtmlPane(ChatView.TEXT);
         list.setHtml(html.append("</ol>").toString());
         list.setAlignmentX(Component.LEFT_ALIGNMENT);
         p.add(list);
      }

      if (!step.getTip().isEmpty()) {
         p.add(gap(4));
         p.add(small("Tip: " + step.getTip()));
      }
      if (step.getRemovedUnverified() > 0) {
         p.add(gap(4));
         p.add(small("Hid " + step.getRemovedUnverified() + " suggestion(s) the wiki guide doesn't mention."));
      }

      p.add(gap(8));
      final JButton done = GuidesView.pillButton("Done, next step");
      done.addActionListener(e -> actions.nextStep(step.getQuest(), step.getNextStep()));
      if (step.getNpc() != null) {
         final JButton highlight = GuidesView.pillButton("Highlight " + firstWord(step.getNpc()));
         highlight.setToolTipText("Show an arrow and outline on " + step.getNpc() + " when they're nearby");
         highlight.addActionListener(e -> actions.highlight(step.getQuest(), step.getNpc()));
         p.add(buttons(highlight, done));
      } else {
         p.add(buttons(done));
      }
      p.add(gap(4));
      p.add(buttons(wikiButton(step.getWikiUrl(), "Open wiki guide")));
   }

   private static JComponent itemRow(final QuestStep.ItemCheck item) {
      final String mark;
      final Color color;
      final String where;
      switch (item.getStatus()) {
         case HAVE:
            mark = "✔";
            color = GOOD;
            where = "in inventory";
            break;
         case WORN:
            mark = "✔";
            color = GOOD;
            where = "worn";
            break;
         case IN_BANK:
            mark = "●";
            color = WARN;
            where = "in bank";
            break;
         case MISSING:
            mark = "✖";
            color = BAD;
            where = "missing";
            break;
         default:
            mark = "?";
            color = ColorScheme.LIGHT_GRAY_COLOR;
            where = "not checked";
      }
      final JLabel icon = new JLabel(mark);
      icon.setForeground(color);
      icon.setFont(FontManager.getRunescapeBoldFont());
      icon.setBorder(new EmptyBorder(0, 0, 0, 6));

      final HtmlPane name = new HtmlPane(ChatView.TEXT);
      name.setHtml(MarkdownRenderer.escape((item.getQuantity() > 1 ? item.getQuantity() + " x " : "") + item.getName())
         + " <span class=\"muted\">" + where + "</span>");

      final JPanel row = new JPanel(new BorderLayout());
      row.setOpaque(false);
      row.add(icon, BorderLayout.WEST);
      row.add(name, BorderLayout.CENTER);
      row.setAlignmentX(Component.LEFT_ALIGNMENT);
      return row;
   }

   private static String confidenceSuffix(final String confidence) {
      if (confidence == null || confidence.isEmpty()) {
         return "";
      }
      return " (" + confidence + " confidence)";
   }

   private static String firstWord(final String s) {
      final int sp = s.indexOf(' ');
      return sp > 0 ? s.substring(0, sp) : s;
   }

   private static JButton wikiButton(final String url, final String text) {
      final JButton b = GuidesView.pillButton(text);
      b.addActionListener(e -> LinkBrowser.browse(url));
      return b;
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
      t.setAlignmentX(Component.LEFT_ALIGNMENT);
      return t;
   }

   private static JComponent label(final String text) {
      final HtmlPane l = new HtmlPane(ColorScheme.MEDIUM_GRAY_COLOR.brighter(), FontManager.getRunescapeSmallFont());
      l.setPlainText(text);
      l.setAlignmentX(Component.LEFT_ALIGNMENT);
      return l;
   }

   private static JComponent text(final String text, final Color color) {
      final HtmlPane t = new HtmlPane(color);
      t.setPlainText(text);
      t.setAlignmentX(Component.LEFT_ALIGNMENT);
      return t;
   }

   private static JComponent small(final String text) {
      final HtmlPane t = new HtmlPane(ColorScheme.LIGHT_GRAY_COLOR, FontManager.getRunescapeSmallFont());
      t.setPlainText(text);
      t.setAlignmentX(Component.LEFT_ALIGNMENT);
      return t;
   }

   private static JComponent fact(final String label, final String value) {
      final HtmlPane t = new HtmlPane(ChatView.TEXT);
      t.setHtml("<span class=\"lbl\">" + MarkdownRenderer.escape(label) + ":</span> <b>"
         + MarkdownRenderer.escape(value) + "</b>");
      t.setAlignmentX(Component.LEFT_ALIGNMENT);
      return t;
   }

   private static JComponent buttons(final JButton... buttons) {
      final JPanel row = new JPanel(new GridLayout(1, buttons.length, 4, 0));
      row.setOpaque(false);
      for (final JButton b : buttons) {
         row.add(b);
      }
      row.setAlignmentX(Component.LEFT_ALIGNMENT);
      return row;
   }

   private static JComponent full(final JButton b) {
      return buttons(b);
   }

   private static Component gap(final int h) {
      return Box.createVerticalStrut(h);
   }
}
