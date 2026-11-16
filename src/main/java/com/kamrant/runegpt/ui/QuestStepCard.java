package com.kamrant.runegpt.ui;

import com.kamrant.runegpt.model.QuestStep;
import java.awt.Color;
import java.awt.Component;
import java.awt.GridLayout;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.LinkBrowser;

/** Renders a {@link QuestStep} as a compact card inside the chat. */
public final class QuestStepCard {

   private QuestStepCard() {
   }

   public static String header(final QuestStep step) {
      switch (step.getKind()) {
         case STEP:
            return "Quest helper · " + step.getProvider() + " · Wiki";
         case NOT_A_QUEST:
            return "Quest helper";
         default:
            return "Quest helper · " + step.getQuest();
      }
   }

   public static JComponent build(final QuestStep step, final QuestStepActions actions) {
      final JPanel p = column();
      switch (step.getKind()) {
         case NOT_A_QUEST:
            buildNotAQuest(p, step, actions);
            break;
         case COMPLETE:
            add(p, title(step.getQuest()));
            add(p, text("You've already completed this quest.", ChatView.TEXT));
            add(p, Box.createVerticalStrut(6));
            add(p, buttons(wikiButton(step.getWikiUrl(), "Open on wiki")));
            break;
         case ERROR:
            add(p, text(step.getMessage(), ChatView.TEXT));
            break;
         default:
            buildStep(p, step, actions);
      }
      return p;
   }

   private static void buildNotAQuest(final JPanel p, final QuestStep step, final QuestStepActions actions) {
      add(p, text("“" + step.getInput() + "” isn't a quest in OSRS.", ChatView.TEXT));
      if (!step.getWikiMatches().isEmpty()) {
         add(p, Box.createVerticalStrut(4));
         add(p, small("On the OSRS Wiki that name matches: " + String.join(", ", step.getWikiMatches()) + "."));
      }
      if (!step.getSuggestions().isEmpty()) {
         add(p, Box.createVerticalStrut(6));
         add(p, label("Did you mean"));
         for (final String quest : step.getSuggestions()) {
            final JButton b = GuidesView.pillButton(quest);
            b.addActionListener(e -> actions.useQuest(quest));
            add(p, Box.createVerticalStrut(3));
            add(p, buttons(b));
         }
      }
   }

   private static void buildStep(final JPanel p, final QuestStep step, final QuestStepActions actions) {
      add(p, title(step.getQuest()));
      if (!step.getProgress().isEmpty()) {
         add(p, Box.createVerticalStrut(4));
         add(p, label("Where you are" + (step.getConfidence().isEmpty() ? "" : " (" + step.getConfidence() + " confidence)")));
         add(p, text(step.getProgress(), ChatView.TEXT));
      }
      add(p, Box.createVerticalStrut(6));
      add(p, label("Steps"));

      final JButton track = GuidesView.pillButton("Track guide");
      track.setToolTipText("Live checklist, in-game overlay, map marker and NPC highlight");
      track.addActionListener(e -> actions.track(step.toGuidePlan()));
      add(p, GuidePlanView.build(step.toGuidePlan(), true, track));

      if (!step.getItems().isEmpty() && !step.isBankKnown()) {
         add(p, small("Open your bank once so I can check it too."));
      }
      if (!step.getTip().isEmpty()) {
         add(p, Box.createVerticalStrut(4));
         add(p, small("Tip: " + step.getTip()));
      }

      add(p, Box.createVerticalStrut(4));
      final JButton done = GuidesView.pillButton("Done, next step");
      done.addActionListener(e -> actions.nextStep(step.getQuest(), step.getNextStep()));
      add(p, buttons(done, wikiButton(step.getWikiUrl(), "Wiki guide")));
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
