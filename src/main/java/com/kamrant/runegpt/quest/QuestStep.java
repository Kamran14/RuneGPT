package com.kamrant.runegpt.quest;

import java.util.Collections;
import java.util.List;

/** Result of a quest step lookup, ready for the panel to show. */
public final class QuestStep {

   public enum Kind {
      /** A next step was found. */
      STEP,
      /** The quest is already finished. */
      COMPLETE,
      /** The name isn't a quest; see suggestions / wikiMatches. */
      NOT_A_QUEST,
      /** Wiki or AI unavailable; see message. */
      ERROR
   }

   public enum ItemStatus {
      HAVE,
      WORN,
      IN_BANK,
      MISSING,
      UNKNOWN
   }

   public static final class ItemCheck {
      private final String name;
      private final int quantity;
      private final ItemStatus status;

      public ItemCheck(final String name, final int quantity, final ItemStatus status) {
         this.name = name;
         this.quantity = quantity;
         this.status = status;
      }

      public String getName() {
         return name;
      }

      public int getQuantity() {
         return quantity;
      }

      public ItemStatus getStatus() {
         return status;
      }
   }

   Kind kind;
   String quest;
   String input;
   String wikiUrl;
   String message;
   String progress;
   String confidence;
   String nextStep;
   String location;
   String npc;
   boolean npcNearby;
   List<ItemCheck> items = Collections.emptyList();
   List<String> then = Collections.emptyList();
   String tip;
   String provider;
   int removedUnverified;
   boolean bankKnown;
   List<String> suggestions = Collections.emptyList();
   List<String> wikiMatches = Collections.emptyList();

   static QuestStep error(final String quest, final String message) {
      final QuestStep s = new QuestStep();
      s.kind = Kind.ERROR;
      s.quest = quest;
      s.message = message;
      return s;
   }

   public Kind getKind() {
      return kind;
   }

   public String getQuest() {
      return quest;
   }

   /** What the player asked about (may not be a real quest name). */
   public String getInput() {
      return input;
   }

   public String getWikiUrl() {
      return wikiUrl;
   }

   public String getMessage() {
      return message;
   }

   public String getProgress() {
      return progress;
   }

   public String getConfidence() {
      return confidence;
   }

   public String getNextStep() {
      return nextStep;
   }

   public String getLocation() {
      return location;
   }

   /** NPC to talk to next, verified against the wiki text; null if none. */
   public String getNpc() {
      return npc;
   }

   public boolean isNpcNearby() {
      return npcNearby;
   }

   public List<ItemCheck> getItems() {
      return items;
   }

   public List<String> getThen() {
      return then;
   }

   public String getTip() {
      return tip;
   }

   public String getProvider() {
      return provider;
   }

   /** How many AI-suggested items/NPCs were dropped because the wiki text doesn't mention them. */
   public int getRemovedUnverified() {
      return removedUnverified;
   }

   public boolean isBankKnown() {
      return bankKnown;
   }

   public List<String> getSuggestions() {
      return suggestions;
   }

   public List<String> getWikiMatches() {
      return wikiMatches;
   }
}
