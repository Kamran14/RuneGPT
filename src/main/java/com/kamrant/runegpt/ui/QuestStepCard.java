package com.kamrant.runegpt.ui;

import com.kamrant.runegpt.model.QuestStep;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;

/** Renders a {@link QuestStep} as a compact card inside the chat. */
public final class QuestStepCard {

   private QuestStepCard() {
   }

   public static String header(final QuestStep step) {
      if (!step.getHeaderLabel().isEmpty()) {
         return step.getHeaderLabel();
      }
      switch (step.getKind()) {
         case STEP:
            return UiTheme.HDR_QUEST_WIKI;
         case NOT_A_QUEST:
            return UiTheme.HDR_QUEST;
         default:
            return UiTheme.HDR_QUEST + " · " + step.getQuest();
      }
   }

   public static JComponent build(final QuestStep step, final QuestStepActions actions) {
      final JPanel p = CardWidgets.column();
      switch (step.getKind()) {
         case NOT_A_QUEST:
            buildNotAQuest(p, step, actions);
            break;
         case COMPLETE:
            CardWidgets.addLeft(p, CardWidgets.title(step.getQuest()));
            CardWidgets.addLeft(p, CardWidgets.text("You've already completed this quest.", UiTheme.TEXT_PRIMARY));
            CardWidgets.addLeft(p, Box.createVerticalStrut(6));
            CardWidgets.addLeft(p, CardWidgets.buttonRow(CardWidgets.wikiButton(step.getWikiUrl(), UiTheme.BTN_OPEN_WIKI)));
            break;
         case ERROR:
            CardWidgets.addLeft(p, CardWidgets.text(step.getMessage(), UiTheme.TEXT_PRIMARY));
            break;
         default:
            buildStep(p, step, actions);
      }
      return p;
   }

   private static void buildNotAQuest(final JPanel p, final QuestStep step, final QuestStepActions actions) {
      CardWidgets.addLeft(p, CardWidgets.text("“" + step.getInput() + "” isn't a quest in OSRS.", UiTheme.TEXT_PRIMARY));
      if (!step.getWikiMatches().isEmpty()) {
         CardWidgets.addLeft(p, Box.createVerticalStrut(4));
         CardWidgets.addLeft(p, CardWidgets.small(
            "On the OSRS Wiki that name matches: " + String.join(", ", step.getWikiMatches()) + "."));
      }
      if (!step.getSuggestions().isEmpty()) {
         CardWidgets.addLeft(p, Box.createVerticalStrut(6));
         CardWidgets.addLeft(p, CardWidgets.label("Did you mean"));
         for (final String quest : step.getSuggestions()) {
            final JButton b = CardWidgets.pillButton(quest);
            b.addActionListener(e -> actions.useQuest(quest));
            CardWidgets.addLeft(p, Box.createVerticalStrut(3));
            CardWidgets.addLeft(p, CardWidgets.buttonRow(b));
         }
      }
   }

   private static void buildStep(final JPanel p, final QuestStep step, final QuestStepActions actions) {
      CardWidgets.addLeft(p, CardWidgets.title(step.getQuest()));
      if (!step.getProgress().isEmpty()) {
         CardWidgets.addLeft(p, Box.createVerticalStrut(4));
         CardWidgets.addLeft(p, CardWidgets.label("Where you are"
            + (step.getConfidence().isEmpty() ? "" : " (" + step.getConfidence() + " confidence)")));
         CardWidgets.addLeft(p, CardWidgets.text(step.getProgress(), UiTheme.TEXT_PRIMARY));
      }
      CardWidgets.addLeft(p, Box.createVerticalStrut(6));
      CardWidgets.addLeft(p, CardWidgets.label(UiTheme.LBL_STEPS));

      final JButton track = CardWidgets.pillButton(UiTheme.BTN_TRACK_GUIDE);
      track.setToolTipText(UiTheme.TIP_TRACK_GUIDE);
      track.addActionListener(e -> actions.track(step.toGuidePlan()));
      CardWidgets.addLeft(p, GuidePlanView.build(step.toGuidePlan(), true, track));

      if (!step.getItems().isEmpty() && !step.isBankKnown()) {
         CardWidgets.addLeft(p, CardWidgets.small(UiTheme.NOTE_OPEN_BANK_ITEMS));
      }
      if (!step.getTip().isEmpty()) {
         CardWidgets.addLeft(p, Box.createVerticalStrut(4));
         CardWidgets.addLeft(p, CardWidgets.small("Tip: " + step.getTip()));
      }

      CardWidgets.addLeft(p, Box.createVerticalStrut(4));
      final JButton done = CardWidgets.pillButton(UiTheme.BTN_DONE_NEXT);
      done.addActionListener(e -> actions.nextStep(step.getQuest(), step.getNextStep()));
      if (step.getWikiUrl() != null && !step.getWikiUrl().isEmpty()) {
         CardWidgets.addLeft(p, CardWidgets.buttonRow(done, CardWidgets.wikiButton(step.getWikiUrl(), UiTheme.BTN_WIKI_GUIDE)));
      } else {
         CardWidgets.addLeft(p, CardWidgets.buttonRow(done));
      }
   }
}
