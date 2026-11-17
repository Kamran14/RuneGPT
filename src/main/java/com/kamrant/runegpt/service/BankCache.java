package com.kamrant.runegpt.service;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Singleton;
import net.runelite.api.Item;

/**
 * Last-seen bank contents (item id to quantity). The game only sends bank contents while the bank is open, so
 * this is filled when the bank is opened, or restored at login from the copy saved on this computer
 * ({@link BankStore}), in which case {@link #isFromEarlierSession()} is true until the bank is opened again.
 */
@Singleton
public class BankCache {

   private volatile Map<Integer, Integer> items = Collections.emptyMap();
   private volatile long seenAt;
   private volatile boolean fromEarlierSession;

   /** Live bank contents from the game. */
   public void update(final Item[] bankItems) {
      final Map<Integer, Integer> m = new HashMap<>();
      for (final Item item : bankItems) {
         // Placeholders have quantity 0.
         if (item.getId() > 0 && item.getQuantity() > 0) {
            m.merge(item.getId(), item.getQuantity(), Integer::sum);
         }
      }
      items = Collections.unmodifiableMap(m);
      seenAt = System.currentTimeMillis();
      fromEarlierSession = false;
   }

   /** Restores a bank saved in an earlier session (only if the bank hasn't been seen live yet). */
   public void restore(final Map<Integer, Integer> saved, final long savedAt) {
      if (isKnown() || saved.isEmpty()) {
         return;
      }
      items = Collections.unmodifiableMap(new HashMap<>(saved));
      seenAt = savedAt;
      fromEarlierSession = true;
   }

   /** True once the bank has been opened this session, or restored from an earlier one. */
   public boolean isKnown() {
      return !items.isEmpty();
   }

   /** True when the contents come from an earlier session and may have changed since. */
   public boolean isFromEarlierSession() {
      return fromEarlierSession;
   }

   /** Epoch millis when these contents were seen in game (0 when unknown). */
   public long getSeenAt() {
      return seenAt;
   }

   public Map<Integer, Integer> getItems() {
      return items;
   }

   public void clear() {
      items = Collections.emptyMap();
      seenAt = 0;
      fromEarlierSession = false;
   }
}
