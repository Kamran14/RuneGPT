package com.kamrant.runegpt.model;

import java.util.Collections;
import java.util.Map;

/** A bank snapshot saved on this computer: item id to quantity, and when it was last seen in game. */
public final class SavedBank {

   private final long savedAt;
   private final Map<Integer, Integer> items;

   public SavedBank(final long savedAt, final Map<Integer, Integer> items) {
      this.savedAt = savedAt;
      this.items = items;
   }

   /** Epoch millis when the bank was last seen. */
   public long getSavedAt() {
      return savedAt;
   }

   public Map<Integer, Integer> getItems() {
      return items == null ? Collections.emptyMap() : items;
   }
}
