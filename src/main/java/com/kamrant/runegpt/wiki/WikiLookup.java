package com.kamrant.runegpt.wiki;

import com.kamrant.runegpt.model.WikiItem;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;

/**
 * Answers "does this exist in OSRS, and where/what is it?" from the OSRS Wiki:
 * items (and their equipment slot) and coordinates for NPCs and places. Follows redirects and
 * caches results for the session.
 */
@Slf4j
@Singleton
public class WikiLookup {

   private static final Pattern REDIRECT = Pattern.compile("(?i)^\\s*#REDIRECT\\s*\\[\\[([^\\]|#]+)");
   private static final Pattern MAP_TEMPLATE = Pattern.compile("(?is)\\{\\{\\s*map\\s*\\|(.*?)}}");
   private static final Pattern NAMED_X = Pattern.compile("(?i)\\|\\s*x\\s*=\\s*(\\d+)");
   private static final Pattern NAMED_Y = Pattern.compile("(?i)\\|\\s*y\\s*=\\s*(\\d+)");
   private static final Pattern NAMED_PLANE = Pattern.compile("(?i)\\|\\s*plane\\s*=\\s*(\\d+)");
   private static final Pattern XY_LABELLED = Pattern.compile("x:(\\d+),\\s*y:(\\d+)");
   private static final Pattern XY_BARE = Pattern.compile("\\|\\s*(\\d{3,5})\\s*,\\s*(\\d{3,5})\\s*(?=[|}])");
   private static final Pattern LOCLINE = Pattern.compile("(?is)\\{\\{\\s*LocLine(.*?)}}");
   private static final int MAX_REDIRECTS = 2;

   private final WikiClient wiki;
   private final Map<String, WikiItem> itemCache = new ConcurrentHashMap<>();
   private final Map<String, WorldPoint> pointCache = new ConcurrentHashMap<>();
   // Sentinel for "looked up, nothing found".
   private static final WikiItem NO_ITEM = new WikiItem("", false, null);
   private static final WorldPoint NO_POINT = new WorldPoint(-1, -1, -1);

   @Inject
   public WikiLookup(final WikiClient wiki) {
      this.wiki = wiki;
   }

   /** The item's wiki data, or null if there's no item page with that name. Blocking. */
   public WikiItem item(final String name) {
      final String key = name.trim().toLowerCase(Locale.ROOT);
      final WikiItem cached = itemCache.get(key);
      if (cached != null) {
         return cached == NO_ITEM ? null : cached;
      }
      WikiItem result = null;
      try {
         final WikiPage page = resolve(capitalize(name.trim()));
         if (page != null && page.getText().contains("{{Infobox Item")) {
            final Map<String, String> item = Wikitext.templateParams(page.getText(), "Infobox Item");
            final boolean equipable = isYes(firstVersioned(item, "equipable"));
            String slot = null;
            if (equipable) {
               final String s = firstVersioned(Wikitext.templateParams(page.getText(), "Infobox Bonuses"), "slot");
               slot = s == null || s.isEmpty() ? null : s.toLowerCase(Locale.ROOT);
            }
            result = new WikiItem(page.getTitle(), equipable, slot);
         }
      } catch (final IOException e) {
         log.debug("RuneGPT: wiki item lookup failed for {}", name, e);
         // Don't cache network failures.
         return null;
      }
      itemCache.put(key, result == null ? NO_ITEM : result);
      return result;
   }

   /**
    * True if the wiki has a page with this name, i.e. the thing exists. Network failures count as
    * "unknown" and return true so a flaky connection doesn't hide real things.
    */
   public boolean exists(final String name) {
      try {
         return resolve(capitalize(name.trim())) != null;
      } catch (final IOException e) {
         return true;
      }
   }

   /** Coordinates of an NPC or place from its wiki page, or null if the page has none. Blocking. */
   public WorldPoint coordinates(final String name) {
      final String key = name.trim().toLowerCase(Locale.ROOT);
      final WorldPoint cached = pointCache.get(key);
      if (cached != null) {
         return cached == NO_POINT ? null : cached;
      }
      WorldPoint result = null;
      try {
         final WikiPage page = resolve(capitalize(name.trim()));
         if (page != null) {
            result = parseCoordinates(page.getText());
         }
      } catch (final IOException e) {
         log.debug("RuneGPT: wiki coordinate lookup failed for {}", name, e);
         return null;
      }
      pointCache.put(key, result == null ? NO_POINT : result);
      return result;
   }

   /**
    * First coordinates on a page: a {{Map}} template (named x=/y=/plane= or positional "x,y" / "x:..,y:.."),
    * else the first {{LocLine}} spawn point.
    */
   static WorldPoint parseCoordinates(final String text) {
      final Matcher map = MAP_TEMPLATE.matcher(text);
      while (map.find()) {
         final String body = "|" + map.group(1);
         final WorldPoint p = pointIn(body);
         if (p != null) {
            return p;
         }
      }
      final Matcher loc = LOCLINE.matcher(text);
      while (loc.find()) {
         final WorldPoint p = pointIn(loc.group(1));
         if (p != null) {
            return p;
         }
      }
      return null;
   }

   private static WorldPoint pointIn(final String body) {
      final int plane = intOr(NAMED_PLANE.matcher(body), 0);
      final Matcher nx = NAMED_X.matcher(body);
      final Matcher ny = NAMED_Y.matcher(body);
      if (nx.find() && ny.find()) {
         return new WorldPoint(Integer.parseInt(nx.group(1)), Integer.parseInt(ny.group(1)), plane);
      }
      final Matcher lab = XY_LABELLED.matcher(body);
      if (lab.find()) {
         return new WorldPoint(Integer.parseInt(lab.group(1)), Integer.parseInt(lab.group(2)), plane);
      }
      final Matcher bare = XY_BARE.matcher(body);
      if (bare.find()) {
         return new WorldPoint(Integer.parseInt(bare.group(1)), Integer.parseInt(bare.group(2)), plane);
      }
      return null;
   }

   private static int intOr(final Matcher m, final int fallback) {
      return m.find() ? Integer.parseInt(m.group(1)) : fallback;
   }

   private WikiPage resolve(final String title) throws IOException {
      String current = title;
      for (int i = 0; i <= MAX_REDIRECTS; i++) {
         final String text = wiki.raw(current);
         if (text == null) {
            return null;
         }
         final Matcher r = REDIRECT.matcher(text);
         if (!r.find()) {
            return new WikiPage(current, text);
         }
         current = r.group(1).trim();
      }
      return null;
   }

   /** Item infoboxes with several versions use equipable1, equipable2...; take the first present. */
   private static String firstVersioned(final Map<String, String> params, final String key) {
      if (params.containsKey(key)) {
         return params.get(key);
      }
      for (int i = 1; i <= 5; i++) {
         if (params.containsKey(key + i)) {
            return params.get(key + i);
         }
      }
      return null;
   }

   private static boolean isYes(final String v) {
      return v != null && v.trim().toLowerCase(Locale.ROOT).startsWith("yes");
   }

   /** Wiki titles start with a capital letter ("knife" -> "Knife"). */
   private static String capitalize(final String s) {
      return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
   }
}
