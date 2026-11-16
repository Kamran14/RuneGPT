package com.kamrant.runegpt.model;

/** What kind of answer a quest step lookup produced. */
public enum QuestStepKind {
   /** A next step was found. */
   STEP,
   /** The quest is already finished. */
   COMPLETE,
   /** The name isn't a quest; see suggestions / wiki matches. */
   NOT_A_QUEST,
   /** Wiki or AI unavailable; see message. */
   ERROR
}
