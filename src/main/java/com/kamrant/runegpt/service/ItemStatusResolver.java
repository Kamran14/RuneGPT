package com.kamrant.runegpt.service;

import com.kamrant.runegpt.model.GuideItem;
import com.kamrant.runegpt.model.GuideItemKind;
import com.kamrant.runegpt.model.ItemHoldings;
import com.kamrant.runegpt.model.ItemStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Works out each guide item's status from what the player holds:
 * gear is EQUIPPED (green), EQUIP_IT when only in the inventory (yellow), else IN_BANK / MISSING (red);
 * carried items are READY (green) when held. Names match loosely: case, plurals and potion doses
 * ("Prayer potion(4)") are ignored.
 */
public final class ItemStatusResolver {

   private ItemStatusResolver() {
   }

   public static List<GuideItem> resolve(final List<GuideItem> items, final ItemHoldings h) {
      final List<GuideItem> out = new ArrayList<>(items.size());
      for (final GuideItem item : items) {
         out.add(item.withStatus(status(item, h)));
      }
      return out;
   }

   public static ItemStatus status(final GuideItem item, final ItemHoldings h) {
      if (!h.isLoggedIn()) {
         return ItemStatus.UNKNOWN;
      }
      final int inInventory = count(h.getInventory(), item.getName());
      final int worn = count(h.getEquipment(), item.getName());
      final int inBank = count(h.getBank(), item.getName());

      if (item.getKind() == GuideItemKind.EQUIP) {
         if (worn > 0) {
            return ItemStatus.EQUIPPED;
         }
         if (inInventory > 0) {
            return ItemStatus.EQUIP_IT;
         }
      } else if (inInventory + worn >= item.getQuantity()) {
         return ItemStatus.READY;
      }
      if (inBank > 0) {
         return ItemStatus.IN_BANK;
      }
      return h.isBankKnown() ? ItemStatus.MISSING : ItemStatus.UNKNOWN;
   }

   private static int count(final Map<String, Integer> items, final String wanted) {
      final String w = normalize(wanted);
      int total = 0;
      for (final Map.Entry<String, Integer> e : items.entrySet()) {
         if (normalize(e.getKey()).equals(w)) {
            total += e.getValue();
         }
      }
      return total;
   }

   /** Lowercase, drop "(4)"-style dose/charge suffixes and a trailing plural "s". */
   static String normalize(final String name) {
      String n = name.toLowerCase(Locale.ROOT).replaceAll("\\(\\d+\\)", "").trim();
      if (n.endsWith("s") && n.length() > 3) {
         n = n.substring(0, n.length() - 1);
      }
      return n;
   }
}
