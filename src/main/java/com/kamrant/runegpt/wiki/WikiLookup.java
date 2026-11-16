package com.kamrant.runegpt.wiki;

import com.kamrant.runegpt.model.MonsterStats;
import com.kamrant.runegpt.model.WikiItem;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;

/**
 * Answers "does this exist in OSRS, and what are its stats/where is it?" from the OSRS Wiki:
 * items (equipment slot, weapon category and level requirements), monster combat stats, and coordinates
 * for NPCs and places. Follows redirects and caches results for the session.
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
   private static final Pattern INT_VALUE = Pattern.compile("([+-]?\\d+)");

   // Wearable skill requirements in the opening paragraph: "60 Attack", "level 50 Ranged", "Attack level of 70".
   private static final String WEAR_SKILLS = "(Attack|Strength|Defence|Ranged|Magic|Prayer|Hitpoints|Slayer)";
   private static final Pattern LEVEL_BEFORE_SKILL = Pattern.compile(
      "(?i)(?<![+\\-])\\b(?:level\\s+)?(\\d{1,2})\\s+" + WEAR_SKILLS + "\\b(?!\\s*bonus)");
   private static final Pattern SKILL_BEFORE_LEVEL = Pattern.compile(
      "(?i)\\b" + WEAR_SKILLS + "\\s+level(?:\\s+of)?\\s+(\\d{1,2})\\b");

   private static final int MAX_REDIRECTS = 2;

   private final WikiClient wiki;
   private final Map<String, WikiItem> itemCache = new ConcurrentHashMap<>();
   private final Map<String, MonsterStats> monsterCache = new ConcurrentHashMap<>();
   private final Map<String, WorldPoint> pointCache = new ConcurrentHashMap<>();
   // Sentinels for "looked up, nothing found".
   private static final WikiItem NO_ITEM = new WikiItem("", false, null);
   private static final MonsterStats NO_MONSTER = new MonsterStats("", 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
      Collections.emptySet(), null, 0);
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
            String combatStyle = null;
            Map<String, Integer> reqs = Collections.emptyMap();
            if (equipable) {
               final Map<String, String> bonuses = Wikitext.templateParams(page.getText(), "Infobox Bonuses");
               final String s = firstVersioned(bonuses, "slot");
               slot = s == null || s.isEmpty() ? null : s.toLowerCase(Locale.ROOT);
               final String cs = firstVersioned(bonuses, "combatstyle");
               combatStyle = cs == null || cs.isEmpty() ? null : cs.trim();
               reqs = parseRequirements(page.getText());
            }
            result = new WikiItem(page.getTitle(), equipable, slot, combatStyle, reqs);
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
    * Combat stats of a monster or boss from its wiki page (accepts plurals and searches on typos), or null
    * if no monster page matches. Blocking.
    */
   public MonsterStats monster(final String name) {
      final String key = name.trim().toLowerCase(Locale.ROOT);
      if (key.isEmpty()) {
         return null;
      }
      final MonsterStats cached = monsterCache.get(key);
      if (cached != null) {
         return cached == NO_MONSTER ? null : cached;
      }
      MonsterStats result = null;
      try {
         final WikiPage page = resolveMonsterPage(name.trim());
         if (page != null) {
            result = parseMonster(page.getTitle(), page.getText());
         }
      } catch (final IOException e) {
         log.debug("RuneGPT: wiki monster lookup failed for {}", name, e);
         return null;
      }
      monsterCache.put(key, result == null ? NO_MONSTER : result);
      return result;
   }

   private WikiPage resolveMonsterPage(final String rawName) throws IOException {
      for (final String candidate : singularCandidates(rawName)) {
         final WikiPage page = resolve(capitalize(candidate));
         if (page != null && page.getText().contains("{{Infobox Monster")) {
            return page;
         }
      }
      for (final String title : wiki.search(rawName, 4)) {
         final WikiPage page = resolve(title);
         if (page != null && page.getText().contains("{{Infobox Monster")) {
            return page;
         }
      }
      return null;
   }

   private static List<String> singularCandidates(final String name) {
      final Set<String> out = new LinkedHashSet<>();
      out.add(name);
      final String lower = name.toLowerCase(Locale.ROOT);
      if (lower.endsWith("ies") && name.length() > 4) {
         out.add(name.substring(0, name.length() - 3) + "y");
      }
      if (lower.endsWith("es") && name.length() > 3) {
         out.add(name.substring(0, name.length() - 2));
      }
      if (lower.endsWith("s") && name.length() > 2) {
         out.add(name.substring(0, name.length() - 1));
      }
      return new java.util.ArrayList<>(out);
   }

   static MonsterStats parseMonster(final String title, final String wikitext) {
      final Map<String, String> m = Wikitext.templateParams(wikitext, "Infobox Monster");
      if (m.isEmpty()) {
         return null;
      }
      final int drange = intParam(m, "drange", 0);
      final Set<String> attrs = new LinkedHashSet<>();
      final String rawAttrs = firstVersioned(m, "attributes");
      if (rawAttrs != null) {
         for (final String part : rawAttrs.split(",")) {
            final String a = part.trim().toLowerCase(Locale.ROOT);
            if (!a.isEmpty()) {
               attrs.add(a);
            }
         }
      }
      String weakness = firstVersioned(m, "elementalweaknesstype");
      if (weakness != null) {
         weakness = weakness.trim();
         if (weakness.isEmpty() || weakness.equalsIgnoreCase("none")) {
            weakness = null;
         }
      }
      final String displayName = firstVersioned(m, "name");
      return new MonsterStats(
         displayName != null && !displayName.isEmpty() ? displayName : title,
         intParam(m, "combat", 0),
         Math.max(1, intParam(m, "hitpoints", 1)),
         Math.max(1, intParam(m, "def", 1)),
         Math.max(1, intParam(m, "mage", 1)),
         intParam(m, "amagic", 0),
         intParam(m, "dstab", 0),
         intParam(m, "dslash", 0),
         intParam(m, "dcrush", 0),
         intParam(m, "dmagic", 0),
         intParam(m, "dlight", drange),
         intParam(m, "dstandard", drange),
         intParam(m, "dheavy", drange),
         attrs,
         weakness,
         intParam(m, "elementalweaknesspercent", 0));
   }

   /**
    * Extracts combat skill levels required to equip an item from the opening paragraph of its wiki page.
    * Only checks the first paragraph before any section heading so creation requirements ("requires 78
    * Fletching to make") are ignored.
    */
   static Map<String, Integer> parseRequirements(final String wikitext) {
      final int heading = wikitext.indexOf("\n==");
      final String leadSection = heading > 0 ? wikitext.substring(0, heading) : wikitext;
      final String plain = Wikitext.toPlain(leadSection);
      final int blank = plain.indexOf("\n\n");
      final String firstPara = blank > 0 ? plain.substring(0, blank) : plain;

      final Map<String, Integer> reqs = new LinkedHashMap<>();
      final Matcher m1 = LEVEL_BEFORE_SKILL.matcher(firstPara);
      while (m1.find()) {
         putReq(reqs, m1.group(2), Integer.parseInt(m1.group(1)));
      }
      final Matcher m2 = SKILL_BEFORE_LEVEL.matcher(firstPara);
      while (m2.find()) {
         putReq(reqs, m2.group(1), Integer.parseInt(m2.group(2)));
      }
      return reqs;
   }

   private static void putReq(final Map<String, Integer> out, final String rawSkill, final int level) {
      if (level >= 1 && level <= 99) {
         final String skill = capitalize(rawSkill.toLowerCase(Locale.ROOT));
         out.merge(skill, level, Math::max);
      }
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
         WikiPage page = resolve(capitalize(name.trim()));
         if (page == null) {
            for (final String s : singularCandidates(name.trim())) {
               page = resolve(capitalize(s));
               if (page != null) {
                  break;
               }
            }
         }
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

   private static int intParam(final Map<String, String> params, final String key, final int fallback) {
      final String v = firstVersioned(params, key);
      if (v == null || v.isEmpty()) {
         return fallback;
      }
      final Matcher m = INT_VALUE.matcher(v.replace(",", ""));
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

   /** Infoboxes with several versions use key1, key2...; take the first present. */
   private static String firstVersioned(final Map<String, String> params, final String key) {
      if (params.containsKey(key) && !params.get(key).isEmpty()) {
         return params.get(key);
      }
      for (int i = 1; i <= 5; i++) {
         final String v = params.get(key + i);
         if (v != null && !v.isEmpty()) {
            return v;
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
