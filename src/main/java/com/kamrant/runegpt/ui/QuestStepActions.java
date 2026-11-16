package com.kamrant.runegpt.ui;

import com.kamrant.runegpt.model.GuidePlan;

/** Buttons on quest step and guide cards. */
public interface QuestStepActions {

   /** Start tracking this guide (live checklist, overlay, map marker, highlights). */
   void track(GuidePlan plan);

   /** Player finished the quest step; find the next one. */
   void nextStep(String quest, String completedStep);

   /** Player picked a suggested real quest name. */
   void useQuest(String quest);
}
