package com.kamrant.runegpt.service;

import com.kamrant.runegpt.model.TargetSource;
import com.kamrant.runegpt.model.WorldTarget;
import com.kamrant.runegpt.prompt.Prompts;
import com.kamrant.runegpt.wiki.WikiLookup;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.api.coords.WorldPoint;

/**
 * Resolves "nearest X" and multi-location OSRS targets relative to the player's live world position:
 * <ol>
 *    <li>checks loaded NPCs and interactable scene objects around the player first;</li>
 *    <li>checks a built-in coordinate index of OSRS world facilities (banks, anvils, furnaces, altars,
 *    cooking ranges, fairy rings, spirit trees, spinning wheels, potter's wheels, looms, sawmills);</li>
 *    <li>falls back to parsing all spawn/location coordinates on the OSRS Wiki page and picking the closest one.</li>
 * </ol>
 */
public final class NearestTargetResolver {

   private static final Pattern PREFIX_STRIP = Pattern.compile(
      "(?i)^(?:go\\s+to|head\\s+to|walk\\s+to|run\\s+to|travel\\s+to|take\\s+me\\s+to|show\\s+me|"
         + "where\\s+is|where's|find|locate|use|visit|bank\\s+at|smelt\\s+at|smith\\s+at|pray\\s+at|cook\\s+at)\\s+");
   private static final Pattern NEAREST_STRIP = Pattern.compile(
      "(?i)^(?:the\\s+|a\\s+|an\\s+|any\\s+)?(?:nearest|closest)\\s+");
   private static final Pattern ARTICLE_STRIP = Pattern.compile("(?i)^(?:the|a|an|any)\\s+");

   // Matches "nearest <target>" or "closest <target>" anywhere in a prompt or step.
   private static final Pattern NEAREST_IN_TEXT = Pattern.compile(
      "(?i)\\b(?:nearest|closest)\\s+([a-z][a-z'\\- ]{1,32}?)(?=\\s+(?:to\\b|in\\b|at\\b|from\\b|and\\b|then\\b|or\\b|with\\b|for\\b)|[,.;?!]|$)");

   // Matches direct navigation queries: "go to <target>", "where is <target>", "find <target>".
   private static final Pattern NAV_QUERY = Pattern.compile(
      "(?i)^\\s*(?:can\\s+you\\s+|please\\s+)?(?:go\\s+to|head\\s+to|walk\\s+to|travel\\s+to|take\\s+me\\s+to|"
         + "where\\s+is|where's|find|locate|point\\s+me\\s+to)\\s+(?:the\\s+|a\\s+|an\\s+)?(?:nearest\\s+|closest\\s+)?"
         + "([a-z0-9][a-z0-9'\\- ]{1,36}?)[?.!]*\\s*$");

   private static final Map<String, List<WorldTarget>> FACILITIES = new LinkedHashMap<>();
   private static final Map<String, String> FACILITY_ALIASES = new LinkedHashMap<>();

   static {
      registerFacility("bank", Arrays.asList("bank", "banks", "banker", "bank booth", "bank chest", "deposit box"),
         target("Lumbridge Castle Bank", 3208, 3220, 2),
         target("Draynor Village Bank", 3092, 3245, 0),
         target("Al Kharid Bank", 3269, 3167, 0),
         target("Varrock West Bank", 3185, 3436, 0),
         target("Varrock East Bank", 3253, 3420, 0),
         target("Grand Exchange Bank", 3164, 3487, 0),
         target("Edgeville Bank", 3094, 3491, 0),
         target("Falador West Bank", 2946, 3368, 0),
         target("Falador East Bank", 3013, 3355, 0),
         target("Catherby Bank", 2809, 3441, 0),
         target("Seers' Village Bank", 2725, 3493, 0),
         target("East Ardougne North Bank", 2615, 3332, 0),
         target("East Ardougne South Bank", 2655, 3283, 0),
         target("Yanille Bank", 2613, 3094, 0),
         target("Canifis Bank", 3512, 3480, 0),
         target("Burgh de Rott Bank", 3496, 3211, 0),
         target("Port Phasmatys Bank", 3688, 3467, 0),
         target("Hosidius Bank", 1749, 3598, 0),
         target("Shayzien Bank", 1488, 3592, 0),
         target("Arceuus Bank", 1629, 3745, 0),
         target("Lovakengj Bank", 1526, 3739, 0),
         target("Port Piscarilius Bank", 1803, 3790, 0),
         target("Kourend Castle Bank", 1612, 3681, 2),
         target("Farming Guild Bank", 1253, 3741, 0),
         target("Woodcutting Guild Bank", 1591, 3476, 0),
         target("Prifddinas North Bank", 3257, 6106, 0),
         target("Prifddinas South Bank", 3295, 6060, 0),
         target("Ferox Enclave Bank", 3130, 3631, 0),
         target("Zanaris Bank", 2383, 4458, 0),
         target("Shilo Village Bank", 2852, 2954, 0),
         target("Tree Gnome Stronghold Bank", 2445, 3424, 1),
         target("Neitiznot Bank", 2337, 3807, 0),
         target("Lunar Isle Bank", 2099, 3919, 0),
         target("Nardah Bank", 3428, 2892, 0),
         target("Civitas illa Fortis West Bank", 1648, 3118, 0),
         target("Civitas illa Fortis East Bank", 1779, 3094, 0));

      registerFacility("anvil", Arrays.asList("anvil", "anvils", "smithing anvil"),
         target("Varrock West Anvil", 3188, 3426, 0),
         target("Varrock East Anvil", 3229, 3433, 0),
         target("Lumbridge Anvil", 3228, 3254, 0),
         target("Doric's Anvil (North of Falador)", 2951, 3451, 0),
         target("Edgeville Anvil", 3112, 3495, 0),
         target("Yanille Anvil", 2613, 3081, 0),
         target("Seers' Village Anvil", 2712, 3495, 0),
         target("East Ardougne Anvil", 2614, 3307, 0),
         target("Catherby Anvil", 2806, 3454, 0),
         target("Port Khazard Anvil", 2668, 3163, 0),
         target("Prifddinas Anvil", 3287, 6054, 0),
         target("Hosidius Anvil", 1743, 3592, 0),
         target("Lovakengj Anvil", 1505, 3751, 0),
         target("Tai Bwo Wannai Anvil", 2790, 3076, 0),
         target("Barbarian Outpost Anvil", 2504, 3500, 0));

      registerFacility("furnace", Arrays.asList("furnace", "furnaces", "smelting furnace"),
         target("Lumbridge Furnace", 3227, 3254, 0),
         target("Al Kharid Furnace", 3274, 3186, 0),
         target("Edgeville Furnace", 3109, 3499, 0),
         target("Falador Furnace", 2975, 3369, 0),
         target("East Ardougne Furnace", 2613, 3308, 0),
         target("Port Phasmatys Furnace", 3687, 3479, 0),
         target("Shilo Village Furnace", 2857, 2966, 0),
         target("Neitiznot Furnace", 2344, 3810, 0),
         target("Prifddinas Furnace", 3286, 6056, 0),
         target("Lovakengj Furnace", 1506, 3768, 0),
         target("Civitas illa Fortis Furnace", 1661, 3128, 0));

      registerFacility("altar", Arrays.asList("altar", "altars", "prayer altar", "church altar"),
         target("Lumbridge Church Altar", 3243, 3207, 0),
         target("Varrock Palace Altar", 3208, 3495, 0),
         target("Varrock South-East Altar", 3254, 3383, 0),
         target("Edgeville Monastery Altar", 3051, 3497, 1),
         target("Falador Castle Altar", 2981, 3342, 0),
         target("Seers' Village Church Altar", 2694, 3463, 0),
         target("East Ardougne Church Altar", 2616, 3304, 0),
         target("Ardougne Monastery Altar", 2606, 3209, 0),
         target("Hosidius Church Altar", 1735, 3574, 0),
         target("Ferox Enclave Altar", 3129, 3636, 0),
         target("Nature Grotto Altar", 3442, 3337, 0));

      registerFacility("fairy ring", Arrays.asList("fairy ring", "fairy rings"),
         target("Fairy Ring DKR (Edgeville / GE)", 3129, 3496, 0),
         target("Fairy Ring DIS (Wizards' Tower)", 3108, 3149, 0),
         target("Fairy Ring DJP (Tower of Life / Ardougne)", 2658, 3230, 0),
         target("Fairy Ring BLR (Legends' Guild)", 2740, 3351, 0),
         target("Fairy Ring ALS (McGrubor's Wood / Seers')", 2644, 3495, 0),
         target("Fairy Ring AIQ (Mudskipper Point)", 2996, 3114, 0),
         target("Fairy Ring BIQ (Kalphite / Al Kharid)", 3251, 3095, 0),
         target("Fairy Ring CKS (Canifis)", 3447, 3470, 0),
         target("Fairy Ring AJR (Rellekka)", 2780, 3613, 0),
         target("Fairy Ring CIS (Arceuus / Hosidius)", 1639, 3869, 0),
         target("Fairy Ring DJR (Chasm of Fire)", 1455, 3658, 0),
         target("Zanaris Fairy Ring Hub", 2412, 4434, 0));

      registerFacility("spirit tree", Arrays.asList("spirit tree", "spirit trees"),
         target("Spirit Tree (Grand Exchange)", 3184, 3508, 0),
         target("Spirit Tree (Tree Gnome Village)", 2542, 3170, 0),
         target("Spirit Tree (Gnome Stronghold)", 2461, 3444, 0),
         target("Spirit Tree (Battlefield of Khazard)", 2555, 3259, 0),
         target("Spirit Tree (Feldip Hills)", 2488, 2850, 0),
         target("Spirit Tree (Prifddinas)", 3274, 6123, 0),
         target("Spirit Tree (Farming Guild)", 1253, 3750, 0));

      registerFacility("range", Arrays.asList("range", "cooking range", "stove"),
         target("Lumbridge Castle Range", 3212, 3216, 0),
         target("Al Kharid Range", 3272, 3180, 0),
         target("Catherby Range", 2817, 3443, 0),
         target("Hosidius Kitchen Range", 1677, 3621, 0),
         target("Varrock East Range", 3247, 3397, 0),
         target("Falador Range", 2990, 3364, 0),
         target("East Ardougne Range", 2667, 3302, 0));

      registerFacility("spinning wheel", Arrays.asList("spinning wheel", "spinning wheels"),
         target("Lumbridge Castle Spinning Wheel", 3209, 3213, 1),
         target("Seers' Village Spinning Wheel", 2715, 3471, 1),
         target("Falador Spinning Wheel", 3006, 3347, 0),
         target("Crafting Guild Spinning Wheel", 2936, 3286, 1),
         target("Neitiznot Spinning Wheel", 2334, 3799, 0),
         target("Fossil Island Spinning Wheel", 3734, 3816, 0));

      registerFacility("potter's wheel", Arrays.asList("potter's wheel", "pottery wheel", "pottery oven"),
         target("Barbarian Village Pottery", 3086, 3409, 0),
         target("Crafting Guild Pottery", 2934, 3289, 0),
         target("East Ardougne Pottery", 2584, 3322, 0),
         target("Hosidius Pottery", 1720, 3544, 0));

      registerFacility("loom", Arrays.asList("loom", "looms"),
         target("Lumbridge Farm Loom", 3156, 3279, 0),
         target("Falador Farm Loom", 3039, 3287, 0),
         target("Seers' Village Loom", 2716, 3471, 0),
         target("Hosidius Loom", 1739, 3538, 0));

      registerFacility("sawmill", Arrays.asList("sawmill", "sawmill operator"),
         target("Varrock Lumber Yard Sawmill", 3302, 3491, 0),
         target("Woodcutting Guild Sawmill", 1624, 3500, 0),
         target("Prifddinas Sawmill", 3315, 6086, 0),
         target("Auburnvale Sawmill", 1396, 3365, 0));

      registerFacility("grand exchange", Arrays.asList("grand exchange", "ge"),
         target("Grand Exchange", 3164, 3487, 0));
   }

   private NearestTargetResolver() {
   }

   private static void registerFacility(final String key, final List<String> aliases, final WorldTarget... targets) {
      final List<WorldTarget> list = Collections.unmodifiableList(Arrays.asList(targets));
      FACILITIES.put(key, list);
      for (final String alias : aliases) {
         FACILITY_ALIASES.put(alias.toLowerCase(Locale.ROOT), key);
      }
   }

   private static WorldTarget target(final String name, final int x, final int y, final int plane) {
      return new WorldTarget(name, new WorldPoint(x, y, plane), TargetSource.WIKI);
   }

   /**
    * True when {@code prompt} is asking to go to, find, or locate a nearest/specific place or NPC.
    */
   public static boolean isNavigationRequest(final String prompt) {
      if (prompt == null || prompt.isEmpty()) {
         return false;
      }
      return NEAREST_IN_TEXT.matcher(prompt).find() || NAV_QUERY.matcher(prompt).find();
   }

   /**
    * Extracts and resolves a target from free text (a user chat prompt or a guide step), picking the
    * closest instance to the player's current position. Returns null if no target is mentioned or found.
    */
   public static WorldTarget resolveFromText(final String text, final PlayerSnapshot snapshot, final WikiLookup wiki) {
      if (text == null || text.isEmpty()) {
         return null;
      }
      final Matcher nearest = NEAREST_IN_TEXT.matcher(text);
      if (nearest.find()) {
         final WorldTarget resolved = resolve(nearest.group(1), snapshot, wiki);
         if (resolved != null && resolved.getPoint() != null) {
            return resolved;
         }
      }
      final Matcher nav = NAV_QUERY.matcher(text);
      if (nav.find()) {
         final WorldTarget resolved = resolve(nav.group(1), snapshot, wiki);
         if (resolved != null && resolved.getPoint() != null) {
            return resolved;
         }
      }
      return null;
   }

   /**
    * Non-blocking client-thread helper: if {@code stepText} mentions "nearest &lt;facility&gt;" (bank, anvil,
    * furnace, altar, fairy ring, spirit tree, range, etc.), returns the closest facility to {@code from}.
    */
   public static WorldTarget resolveFacilityInStep(final String stepText, final WorldPoint from) {
      if (stepText == null || stepText.isEmpty()) {
         return null;
      }
      final Matcher m = NEAREST_IN_TEXT.matcher(stepText);
      if (m.find()) {
         return nearestFacility(cleanTargetName(m.group(1)), from);
      }
      return null;
   }

   /**
    * Resolves {@code rawTarget} (e.g. "Nearest anvil", "bank", "Cow", "Otto Godblessed") to the closest
    * {@link WorldTarget} relative to {@code snapshot.getLocation()}.
    */
   public static WorldTarget resolve(final String rawTarget, final PlayerSnapshot snapshot, final WikiLookup wiki) {
      if (rawTarget == null || rawTarget.trim().isEmpty()) {
         return null;
      }
      final String cleaned = cleanTargetName(rawTarget);
      if (cleaned.isEmpty()) {
         return null;
      }
      final WorldPoint from = snapshot == null ? null : snapshot.getLocation();

      // 1. Live loaded scene objects & NPCs around the player.
      if (snapshot != null && snapshot.isLoggedIn()) {
         final WorldTarget inScene = snapshot.nearestSceneEntity(cleaned);
         if (inScene != null && inScene.getPoint() != null) {
            return inScene;
         }
      }

      // 2. Built-in OSRS world facilities (banks, anvils, furnaces, altars, fairy rings, etc.).
      final WorldTarget facility = nearestFacility(cleaned, from);
      if (facility != null) {
         return facility;
      }

      // 3. OSRS Wiki multi-coordinate lookup, picking the coordinate closest to the player.
      if (wiki != null) {
         final WorldTarget fromWiki = wiki.nearestTarget(cleaned, from);
         if (fromWiki != null) {
            return fromWiki;
         }
         // Also try the raw name in case stripping a prefix changed a proper noun.
         final String rawTrim = rawTarget.trim();
         if (!rawTrim.equalsIgnoreCase(cleaned)) {
            return wiki.nearestTarget(rawTrim, from);
         }
      }
      return null;
   }

   /**
    * Formats a verified nearest-location context block for the LLM so its text reply names the exact
    * closest spot and distance from the player.
    */
   public static String locationFactsFor(final String prompt, final PlayerSnapshot snapshot, final WikiLookup wiki) {
      final WorldTarget target = resolveFromText(prompt, snapshot, wiki);
      if (target == null || target.getPoint() == null) {
         return "";
      }
      final WorldPoint p = target.getPoint();
      final String dist = snapshot != null && snapshot.getLocation() != null
         ? " (" + Navigation.describe(snapshot.getLocation(), p) + " from player)"
         : "";
      return Prompts.NEAREST_LOCATION_HEADER
         + target.getName() + ": x=" + p.getX() + ", y=" + p.getY() + ", plane=" + p.getPlane() + dist + '\n';
   }

   /** Closest built-in facility matching {@code cleanName}, or null if {@code cleanName} isn't a facility. */
   public static WorldTarget nearestFacility(final String cleanName, final WorldPoint from) {
      if (cleanName == null || cleanName.isEmpty()) {
         return null;
      }
      final String key = FACILITY_ALIASES.get(cleanName.toLowerCase(Locale.ROOT));
      if (key != null) {
         return closest(FACILITIES.get(key), from);
      }
      // Also match specific named facilities in the table (e.g. "Varrock West Bank", "Edgeville Furnace").
      for (final List<WorldTarget> list : FACILITIES.values()) {
         for (final WorldTarget t : list) {
            if (t.getName().equalsIgnoreCase(cleanName)) {
               return t;
            }
         }
      }
      return null;
   }

   private static WorldTarget closest(final List<WorldTarget> candidates, final WorldPoint from) {
      if (candidates == null || candidates.isEmpty()) {
         return null;
      }
      if (from == null) {
         return candidates.get(0);
      }
      WorldTarget best = null;
      int bestScore = Integer.MAX_VALUE;
      for (final WorldTarget t : candidates) {
         final WorldPoint p = t.getPoint();
         if (p == null) {
            continue;
         }
         final int score = p.distanceTo2D(from) + (p.getPlane() != from.getPlane() ? 80 : 0);
         if (score < bestScore) {
            bestScore = score;
            best = t;
         }
      }
      return best != null ? best : candidates.get(0);
   }

   /** Strips "go to", "nearest", "closest", articles and trailing punctuation. */
   public static String cleanTargetName(final String raw) {
      if (raw == null) {
         return "";
      }
      String s = raw.trim().replaceAll("[?.!;]+$", "").trim();
      s = PREFIX_STRIP.matcher(s).replaceFirst("").trim();
      s = NEAREST_STRIP.matcher(s).replaceFirst("").trim();
      s = ARTICLE_STRIP.matcher(s).replaceFirst("").trim();
      return s;
   }
}
