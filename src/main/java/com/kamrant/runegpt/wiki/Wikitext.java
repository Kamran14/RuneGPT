package com.kamrant.runegpt.wiki;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Converts OSRS Wiki markup to readable plain text for the AI, keeping what matters for a quest
 * (steps, item and NPC names, dialogue options, skill requirements) and dropping maps, icons,
 * prices and navigation templates.
 */
public final class Wikitext {

   private static final Pattern COMMENT = Pattern.compile("(?s)<!--.*?-->");
   private static final Pattern REF = Pattern.compile("(?s)<ref[^>]*?(/>|>.*?</ref>)");
   private static final Pattern TAG = Pattern.compile("</?[a-zA-Z][^>]*>");
   private static final Pattern FILE_LINK = Pattern.compile("\\[\\[(File|Image|Category):[^\\]]*]]");
   private static final Pattern PIPED_LINK = Pattern.compile("\\[\\[[^\\]|]*\\|([^\\]]*)]]");
   private static final Pattern LINK = Pattern.compile("\\[\\[([^\\]]*)]]");
   private static final Pattern EXT_LINK = Pattern.compile("\\[https?://\\S+ ([^\\]]*)]");
   private static final Pattern QUOTES = Pattern.compile("'{2,}");
   private static final Pattern HEADING = Pattern.compile("(?m)^=+\\s*(.*?)\\s*=+\\s*$");
   private static final Pattern BLANKS = Pattern.compile("\\n{3,}");

   private Wikitext() {
   }

   public static String toPlain(final String wikitext) {
      String s = COMMENT.matcher(wikitext).replaceAll("");
      s = REF.matcher(s).replaceAll("");
      s = expandTemplates(s);
      s = TAG.matcher(s).replaceAll("");
      s = FILE_LINK.matcher(s).replaceAll("");
      s = PIPED_LINK.matcher(s).replaceAll("$1");
      s = LINK.matcher(s).replaceAll("$1");
      s = EXT_LINK.matcher(s).replaceAll("$1");
      s = QUOTES.matcher(s).replaceAll("");
      s = HEADING.matcher(s).replaceAll("\n## $1");
      s = s.replace("&nbsp;", " ");
      s = BLANKS.matcher(s).replaceAll("\n\n");
      return s.trim();
   }

   /**
    * Returns the named parameters of the first {{templateName ...}} block, converted to plain text,
    * e.g. "requirements" -> "* Completion of Tai Bwo Wannai Trio ...". Empty map if not found.
    */
   public static Map<String, String> templateParams(final String wikitext, final String templateName) {
      final Map<String, String> out = new LinkedHashMap<>();
      final int start = indexOfTemplate(wikitext, templateName);
      if (start < 0) {
         return out;
      }
      final int end = matchingClose(wikitext, start);
      if (end < 0) {
         return out;
      }
      final List<String> args = splitArgs(wikitext.substring(start + 2, end));
      for (int i = 1; i < args.size(); i++) {
         final String arg = args.get(i);
         final int eq = arg.indexOf('=');
         if (eq > 0) {
            out.put(arg.substring(0, eq).trim().toLowerCase(), toPlain(arg.substring(eq + 1)).trim());
         }
      }
      return out;
   }

   private static int indexOfTemplate(final String s, final String name) {
      int from = 0;
      while (true) {
         final int i = s.indexOf("{{", from);
         if (i < 0) {
            return -1;
         }
         final String after = s.substring(i + 2).trim();
         if (after.toLowerCase().startsWith(name.toLowerCase())) {
            final char next = after.length() > name.length() ? after.charAt(name.length()) : '}';
            if (next == '|' || next == '}' || Character.isWhitespace(next)) {
               return i;
            }
         }
         from = i + 2;
      }
   }

   /** Index of the "}}" closing the "{{" at {@code open}, accounting for nesting. */
   private static int matchingClose(final String s, final int open) {
      int depth = 0;
      for (int i = open; i < s.length() - 1; i++) {
         if (s.charAt(i) == '{' && s.charAt(i + 1) == '{') {
            depth++;
            i++;
         } else if (s.charAt(i) == '}' && s.charAt(i + 1) == '}') {
            depth--;
            i++;
            if (depth == 0) {
               return i - 1;
            }
         }
      }
      return -1;
   }

   /** Splits template content on top-level pipes (not inside nested templates or links). */
   private static List<String> splitArgs(final String content) {
      final List<String> args = new ArrayList<>();
      int braces = 0;
      int brackets = 0;
      final StringBuilder cur = new StringBuilder();
      for (int i = 0; i < content.length(); i++) {
         final char c = content.charAt(i);
         final char n = i + 1 < content.length() ? content.charAt(i + 1) : 0;
         if (c == '{' && n == '{') {
            braces++;
            cur.append("{{");
            i++;
         } else if (c == '}' && n == '}') {
            braces--;
            cur.append("}}");
            i++;
         } else if (c == '[' && n == '[') {
            brackets++;
            cur.append("[[");
            i++;
         } else if (c == ']' && n == ']') {
            brackets--;
            cur.append("]]");
            i++;
         } else if (c == '|' && braces == 0 && brackets == 0) {
            args.add(cur.toString());
            cur.setLength(0);
         } else {
            cur.append(c);
         }
      }
      args.add(cur.toString());
      return args;
   }

   /** Replaces every template with its plain-text meaning (or nothing). Innermost first via recursion. */
   private static String expandTemplates(final String s) {
      final StringBuilder out = new StringBuilder();
      int i = 0;
      while (i < s.length()) {
         final int open = s.indexOf("{{", i);
         if (open < 0) {
            out.append(s, i, s.length());
            break;
         }
         out.append(s, i, open);
         final int close = matchingClose(s, open);
         if (close < 0) {
            out.append(s, open, s.length());
            break;
         }
         out.append(renderTemplate(s.substring(open + 2, close)));
         i = close + 2;
      }
      return out.toString();
   }

   private static String renderTemplate(final String content) {
      final List<String> args = splitArgs(content);
      final String name = args.get(0).trim().toLowerCase();
      final List<String> pos = new ArrayList<>();
      for (int i = 1; i < args.size(); i++) {
         if (!args.get(i).contains("=") || args.get(i).trim().startsWith("*")) {
            pos.add(args.get(i));
         }
      }
      switch (name) {
         case "checklist":
            // The checklist body is the step list itself.
            return pos.isEmpty() ? "" : "\n" + expandTemplates(String.join("|", pos)).trim() + "\n";
         case "chat option":
         case "chat options":
         case "chatoptions": {
            final List<String> opts = new ArrayList<>();
            for (final String p : pos) {
               opts.add(p.trim().replaceFirst("^\\d+", "").trim());
            }
            return opts.isEmpty() ? "" : " (dialogue: " + String.join(" > ", opts) + ")";
         }
         case "scp":
         case "skill clickpic":
            return pos.size() >= 2 ? "level " + pos.get(1).trim() + " " + pos.get(0).trim() : "";
         case "plink":
         case "plinkp":
         case "plinkt":
            return pos.isEmpty() ? "" : pos.get(0).trim();
         case "nowrap":
            return pos.isEmpty() ? "" : expandTemplates(pos.get(0));
         default:
            // Maps, icons, prices, boostable markers, navboxes, rewards pages, etc.
            return "";
      }
   }
}
