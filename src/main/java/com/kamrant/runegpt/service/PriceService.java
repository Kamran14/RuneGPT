package com.kamrant.runegpt.service;

import com.kamrant.runegpt.model.GuideItem;
import com.kamrant.runegpt.model.ItemStatus;
import com.kamrant.runegpt.prompt.Prompts;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.game.ItemManager;
import net.runelite.http.api.item.ItemPrice;

/**
 * Looks up live Grand Exchange prices through RuneLite's {@link ItemManager} (actively traded wiki prices
 * with manipulation checks, falling back to the GE guide price). Used both to attach real GP figures to
 * every prompt so the AI never guesses prices, and to price items in guide checklists. Thread-safe.
 */
@Singleton
public class PriceService {

   private static final int MAX_MENTIONED_ITEMS = 15;
   private static final int MAX_OWNED_PRICED = 20;
   private static final int MAX_NGRAM_WORDS = 5;
   private static final int MIN_SINGLE_WORD_CHARS = 4;

   // Words that are also real item names ("Logs", "Beer", "Bones", "Ring", "Cape") or common English words:
   // only match them when part of a multi-word item name or when the whole query is that word.
   private static final Set<String> AMBIGUOUS_SINGLE_WORDS = Set.of(
      "logs", "bones", "beer", "meat", "pie", "cake", "bread", "stew", "wine", "coal", "gold", "iron",
      "steel", "silver", "flax", "sand", "salt", "rock", "rope", "saw", "pot", "jug", "bowl", "bucket",
      "spade", "knife", "hammer", "chisel", "needle", "thread", "feather", "staff", "bow", "dart", "arrow",
      "bolts", "ring", "cape", "hood", "robe", "skirt", "boots", "gloves", "shield", "helm", "sword", "mace",
      "spear", "lance", "whip", "club", "axe", "pickaxe", "harpoon", "net", "bait", "herb", "seed", "ash",
      "ashes", "clay", "orb", "vial", "key", "book", "page", "scroll", "note", "map", "watch", "chart"
   );

   private final ItemManager itemManager;

   // Normalized name -> ItemPrice, built lazily from ItemManager's in-memory price table.
   private volatile Map<String, ItemPrice> byName = Collections.emptyMap();
   private volatile int indexedCount;

   @Inject
   public PriceService(final ItemManager itemManager) {
      this.itemManager = itemManager;
   }

   /**
    * Live GE price of one item by name, in gp, or 0 if untradeable / unknown. Potions without a dose
    * suffix ("Prayer potion") resolve to their 4-dose price.
    */
   public long priceOf(final String rawName) {
      if (rawName == null) {
         return 0;
      }
      final String trimmed = rawName.trim();
      if (trimmed.isEmpty()) {
         return 0;
      }
      if (trimmed.equalsIgnoreCase("Coins") || trimmed.equalsIgnoreCase("Coin")) {
         return 1;
      }
      if (trimmed.equalsIgnoreCase("Platinum token") || trimmed.equalsIgnoreCase("Platinum tokens")) {
         return 1000;
      }
      final ItemPrice ip = findPrice(trimmed);
      return ip == null ? 0 : wikiOrGePrice(ip);
   }

   /** Copies {@code items} with their live GE prices filled in. */
   public List<GuideItem> withPrices(final List<GuideItem> items) {
      final List<GuideItem> out = new ArrayList<>(items.size());
      for (final GuideItem item : items) {
         out.add(item.withPrice(priceOf(item.getName())));
      }
      return out;
   }

   /** Total GP needed to buy the items in {@code items} that the player is missing. */
   public static long missingCost(final List<GuideItem> items) {
      long total = 0;
      for (final GuideItem item : items) {
         if (item.getStatus() == ItemStatus.MISSING || item.getStatus() == ItemStatus.UNKNOWN) {
            total += item.getTotalPrice();
         }
      }
      return total;
   }

   /**
    * Verified live GE prices to attach to a chat or guide prompt: items named in the question, the player's
    * worn gear, and valuable tradeable items they carry or have in the bank, plus their cash stack.
    */
   public String priceFactsFor(final String prompt, final PlayerSnapshot player) {
      final Map<String, ItemPrice> index = index();
      if (index.isEmpty() && (!player.isLoggedIn() || player.getCash() == 0)) {
         return "";
      }

      final Map<String, Long> lines = new LinkedHashMap<>();
      for (final ItemPrice ip : findMentioned(prompt, index, MAX_MENTIONED_ITEMS)) {
         final long p = wikiOrGePrice(ip);
         if (p > 0) {
            lines.putIfAbsent(ip.getName(), p);
         }
      }

      if (player.isLoggedIn()) {
         addOwnedPrices(player.getEquipment().keySet(), lines, MAX_OWNED_PRICED);
         addOwnedPrices(player.getInventory().keySet(), lines, MAX_OWNED_PRICED);
         if (player.isBankKnown()) {
            // Valuable bank items first so the AI knows what gear/supplies can be sold or upgraded.
            final List<Map.Entry<String, Long>> bankPriced = new ArrayList<>();
            for (final Map.Entry<String, Integer> e : player.getBank().entrySet()) {
               final long unit = priceOf(e.getKey());
               if (unit > 0) {
                  bankPriced.add(Map.entry(e.getKey(), unit));
               }
            }
            bankPriced.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
            for (final Map.Entry<String, Long> e : bankPriced) {
               if (lines.size() >= MAX_MENTIONED_ITEMS + MAX_OWNED_PRICED) {
                  break;
               }
               lines.putIfAbsent(e.getKey(), e.getValue());
            }
         }
      }

      if (lines.isEmpty() && (!player.isLoggedIn() || player.getCash() == 0)) {
         return "";
      }

      final StringBuilder sb = new StringBuilder(Prompts.PRICE_FACTS_HEADER);
      if (player.isLoggedIn()) {
         sb.append("Player cash (inventory").append(player.isBankKnown() ? " + bank" : "")
            .append("): ").append(formatExact(player.getCash())).append('\n');
      }
      lines.forEach((name, price) -> sb.append("- ").append(name).append(": ").append(formatExact(price)).append('\n'));
      return sb.toString();
   }

   private void addOwnedPrices(final Iterable<String> names, final Map<String, Long> out, final int limit) {
      for (final String name : names) {
         if (out.size() >= MAX_MENTIONED_ITEMS + limit) {
            break;
         }
         if (name.equalsIgnoreCase("Coins") || name.equalsIgnoreCase("Platinum token")) {
            continue;
         }
         final long p = priceOf(name);
         if (p > 0) {
            out.putIfAbsent(name, p);
         }
      }
   }

   /** Compact GP string for tight UI rows: "1.45M gp", "120K gp", "850 gp". */
   public static String formatShort(final long gp) {
      if (gp >= 1_000_000_000L) {
         return trimZero(String.format(Locale.ROOT, "%.2fB gp", gp / 1_000_000_000.0));
      }
      if (gp >= 10_000_000L) {
         return trimZero(String.format(Locale.ROOT, "%.1fM gp", gp / 1_000_000.0));
      }
      if (gp >= 1_000_000L) {
         return trimZero(String.format(Locale.ROOT, "%.2fM gp", gp / 1_000_000.0));
      }
      if (gp >= 10_000L) {
         return String.format(Locale.ROOT, "%dK gp", Math.round(gp / 1_000.0));
      }
      if (gp >= 1_000L) {
         return trimZero(String.format(Locale.ROOT, "%.1fK gp", gp / 1_000.0));
      }
      return gp + " gp";
   }

   /** Full GP string for the AI prompt: "1,450,000 gp (1.45M gp)". */
   public static String formatExact(final long gp) {
      final String exact = NumberFormat.getIntegerInstance(Locale.US).format(gp) + " gp";
      return gp >= 10_000L ? exact + " (" + formatShort(gp) + ")" : exact;
   }

   private static String trimZero(final String s) {
      return s.replace(".00", "").replace(".0", "");
   }

   private ItemPrice findPrice(final String rawName) {
      final Map<String, ItemPrice> index = index();
      if (index.isEmpty()) {
         return null;
      }
      final String key = normalizeKeepDose(rawName);
      for (final String candidate : lookupKeys(key)) {
         final ItemPrice ip = index.get(candidate);
         if (ip != null) {
            return ip;
         }
      }
      return null;
   }

   private static List<String> lookupKeys(final String key) {
      final Set<String> keys = new LinkedHashSet<>();
      keys.add(key);
      keys.add(key + " (4)");
      if (key.endsWith("es") && key.length() > 4) {
         final String s = key.substring(0, key.length() - 2);
         keys.add(s);
         keys.add(s + " (4)");
      }
      if (key.endsWith("s") && key.length() > 3) {
         final String s = key.substring(0, key.length() - 1);
         keys.add(s);
         keys.add(s + " (4)");
      } else {
         keys.add(key + "s");
      }
      return new ArrayList<>(keys);
   }

   /** Finds tradeable items mentioned in {@code text}, longest multi-word names first. */
   private static List<ItemPrice> findMentioned(final String text, final Map<String, ItemPrice> index, final int max) {
      if (text == null || text.isEmpty() || index.isEmpty()) {
         return Collections.emptyList();
      }
      final String cleaned = normalizeWords(text);
      if (cleaned.isEmpty()) {
         return Collections.emptyList();
      }
      final String[] words = cleaned.split(" ");
      final boolean[] used = new boolean[words.length];
      final List<ItemPrice> found = new ArrayList<>();
      final Set<Integer> seenIds = new LinkedHashSet<>();

      for (int len = Math.min(MAX_NGRAM_WORDS, words.length); len >= 1; len--) {
         for (int start = 0; start + len <= words.length; start++) {
            if (found.size() >= max) {
               return found;
            }
            if (anyUsed(used, start, len)) {
               continue;
            }
            final String phrase = join(words, start, len);
            if (len == 1 && (phrase.length() < MIN_SINGLE_WORD_CHARS || AMBIGUOUS_SINGLE_WORDS.contains(phrase))) {
               continue;
            }
            ItemPrice match = null;
            for (final String candidate : lookupKeys(phrase)) {
               match = index.get(candidate);
               if (match != null) {
                  break;
               }
            }
            if (match != null && seenIds.add(match.getId())) {
               found.add(match);
               for (int i = start; i < start + len; i++) {
                  used[i] = true;
               }
            }
         }
      }
      return found;
   }

   private static boolean anyUsed(final boolean[] used, final int start, final int len) {
      for (int i = start; i < start + len; i++) {
         if (used[i]) {
            return true;
         }
      }
      return false;
   }

   private static String join(final String[] words, final int start, final int len) {
      final StringBuilder sb = new StringBuilder(words[start]);
      for (int i = 1; i < len; i++) {
         sb.append(' ').append(words[start + i]);
      }
      return sb.toString();
   }

   private long wikiOrGePrice(final ItemPrice ip) {
      if (itemManager != null) {
         return Math.max(0, itemManager.getWikiPrice(ip));
      }
      return Math.max(0, ip.getWikiPrice() > 0 ? ip.getWikiPrice() : ip.getPrice());
   }

   private Map<String, ItemPrice> index() {
      final List<ItemPrice> all = itemManager == null ? Collections.emptyList() : itemManager.search("");
      if (all.isEmpty()) {
         return byName;
      }
      if (all.size() == indexedCount && !byName.isEmpty()) {
         return byName;
      }
      synchronized (this) {
         if (all.size() == indexedCount && !byName.isEmpty()) {
            return byName;
         }
         final Map<String, ItemPrice> map = new LinkedHashMap<>(all.size() * 2);
         for (final ItemPrice ip : all) {
            if (ip.getName() == null || ip.getName().isEmpty()) {
               continue;
            }
            final String key = normalizeKeepDose(ip.getName());
            final ItemPrice existing = map.get(key);
            if (existing == null || itemManager.getWikiPrice(ip) > itemManager.getWikiPrice(existing)) {
               map.put(key, ip);
            }
            // Also index 4-dose potions under their base name ("prayer potion(4)" -> "prayer potion").
            if (key.endsWith(" (4)")) {
               final String base = key.substring(0, key.length() - 4).trim();
               map.putIfAbsent(base, ip);
            }
         }
         byName = Collections.unmodifiableMap(map);
         indexedCount = all.size();
         return byName;
      }
   }

   /** Lowercase words, keeping "(4)" dose tags as a separate "(4)" token. */
   private static String normalizeKeepDose(final String s) {
      return s.toLowerCase(Locale.ROOT)
         .replace("’", "'")
         .replace("'", "")
         .replaceAll("\\((\\d+)\\)", " ($1) ")
         .replaceAll("[^a-z0-9()]+", " ")
         .trim()
         .replaceAll("\\s+", " ");
   }

   private static String normalizeWords(final String s) {
      return s.toLowerCase(Locale.ROOT)
         .replace("’", "'")
         .replace("'", "")
         .replaceAll("[^a-z0-9]+", " ")
         .trim()
         .replaceAll("\\s+", " ");
   }
}
