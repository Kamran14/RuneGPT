package com.kamrant.runegpt.service;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Singleton;
import net.runelite.api.Item;

/**
 * Last-seen bank contents (item id to quantity). The game only sends bank contents while the
 * bank is open, so this is empty until the player opens their bank once per session.
 */
@Singleton
public class BankCache {

   private volatile Map<Integer, Integer> items = Collections.emptyMap();

   public void update(final Item[] bankItems) {
      final Map<Integer, Integer> m = new HashMap<>();
      for (final Item item : bankItems) {
         // Placeholders have quantity 0.
         if (item.getId() > 0 && item.getQuantity() > 0) {
            m.merge(item.getId(), item.getQuantity(), Integer::sum);
         }
      }
      items = Collections.unmodifiableMap(m);
   }

   /** True once the bank has been opened this session. */
   public boolean isKnown() {
      return !items.isEmpty();
   }

   public Map<Integer, Integer> getItems() {
      return items;
   }

   public void clear() {
      items = Collections.emptyMap();
   }
}
