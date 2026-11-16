package com.kamrant.runegpt.model;

/** Where a target's coordinates came from. */
public enum TargetSource {
   /** Read from the OSRS Wiki. */
   WIKI,
   /** Estimated by the AI; may be off. */
   AI_ESTIMATE,
   /** No coordinates: name only. */
   NONE
}
