package com.kamrant.runegpt.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.kamrant.runegpt.model.TaskSuggestion;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Lenient helpers for reading the JSON the LLM returns (tolerates code fences and stray text). */
public final class ResponseParser {

   // Matches numbered or bulleted bold recommendations in Markdown: "1. **Barrows** — ~500k gp/hr"
   private static final Pattern BOLD_LIST_ITEM = Pattern.compile(
      "(?m)^\\s*(?:\\d+[.)]|[-*•])\\s*\\*\\*([^*\\n]{3,45})\\*\\*(?:\\s*[:\\-–—]\\s*([^\\n]{0,70}))?");
   private static final Pattern NUMBERED_HEADING = Pattern.compile(
      "(?m)^\\s*#{2,4}\\s*(?:\\d+[.)]\\s*)?([^#\\n]{3,45})$");

   private ResponseParser() {
   }

   /** The JSON object in {@code text}, or null if there isn't a readable one. */
   public static JsonObject extractJson(final Gson gson, final String text) {
      if (text == null) {
         return null;
      }
      final int start = text.indexOf('{');
      final int end = text.lastIndexOf('}');
      if (start < 0 || end <= start) {
         return null;
      }
      try {
         return gson.fromJson(text.substring(start, end + 1), JsonObject.class);
      } catch (final Exception e) {
         return null;
      }
   }

   /** Plain text with any Markdown code fence wrapper removed (fallback when JSON can't be read). */
   public static String stripFences(final String text) {
      String t = text.trim();
      if (t.startsWith("```")) {
         final int nl = t.indexOf('\n');
         t = nl > 0 ? t.substring(nl + 1) : t.substring(3);
         if (t.endsWith("```")) {
            t = t.substring(0, t.length() - 3);
         }
      }
      return t.trim();
   }

   /** String field, joined if it's an array; "" when missing or null. */
   public static String str(final JsonObject o, final String key) {
      if (o == null) {
         return "";
      }
      final JsonElement e = o.get(key);
      if (e == null || e.isJsonNull()) {
         return "";
      }
      if (e.isJsonArray()) {
         final List<String> parts = new ArrayList<>();
         e.getAsJsonArray().forEach(x -> parts.add(x.isJsonPrimitive() ? x.getAsString() : x.toString()));
         return String.join(", ", parts);
      }
      return e.isJsonPrimitive() ? e.getAsString().trim() : "";
   }

   /** Int field, or {@code fallback} when missing or not a number. */
   public static int intOr(final JsonObject o, final String key, final int fallback) {
      try {
         final JsonElement e = o.get(key);
         return e == null || e.isJsonNull() ? fallback : e.getAsInt();
      } catch (final Exception e) {
         return fallback;
      }
   }

   /** Non-empty strings of an array field, at most {@code max}. */
   public static List<String> strings(final JsonObject o, final String key, final int max) {
      final List<String> out = new ArrayList<>();
      final JsonElement e = o == null ? null : o.get(key);
      if (e != null && e.isJsonArray()) {
         for (final JsonElement x : e.getAsJsonArray()) {
            if (x.isJsonPrimitive() && !x.getAsString().trim().isEmpty() && out.size() < max) {
               out.add(x.getAsString().trim());
            }
         }
      }
      return out;
   }

   /** Array field, or an empty array. */
   public static JsonArray array(final JsonObject o, final String key) {
      final JsonElement e = o == null ? null : o.get(key);
      return e != null && e.isJsonArray() ? e.getAsJsonArray() : new JsonArray();
   }

   /** Object field, or null. */
   public static JsonObject object(final JsonObject o, final String key) {
      final JsonElement e = o == null ? null : o.get(key);
      return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
   }

   /**
    * Reads task suggestions from the JSON {@code "suggestions"} array, falling back to extracting bold
    * numbered/bulleted items from {@code answerMarkdown} when no single {@code guide} was returned.
    */
   public static List<TaskSuggestion> suggestions(final JsonObject json, final String answerMarkdown,
      final boolean hasGuide, final int max) {
      final List<TaskSuggestion> out = new ArrayList<>();
      final Set<String> seen = new LinkedHashSet<>();

      for (final JsonElement el : array(json, "suggestions")) {
         if (out.size() >= max) {
            break;
         }
         if (el.isJsonObject()) {
            final JsonObject o = el.getAsJsonObject();
            final String title = cleanTitle(str(o, "title"));
            if (!title.isEmpty() && seen.add(title.toLowerCase(Locale.ROOT))) {
               out.add(new TaskSuggestion(title, str(o, "category"), str(o, "summary")));
            }
         } else if (el.isJsonPrimitive()) {
            final String title = cleanTitle(el.getAsString());
            if (!title.isEmpty() && seen.add(title.toLowerCase(Locale.ROOT))) {
               out.add(new TaskSuggestion(title, "", ""));
            }
         }
      }

      if (!out.isEmpty() || hasGuide || answerMarkdown == null || answerMarkdown.isEmpty()) {
         return out;
      }

      final Matcher m = BOLD_LIST_ITEM.matcher(answerMarkdown);
      while (m.find() && out.size() < max) {
         final String title = cleanTitle(m.group(1));
         final String summary = m.group(2) == null ? "" : m.group(2).replaceAll("\\*+", "").trim();
         if (isPlausibleTaskTitle(title) && seen.add(title.toLowerCase(Locale.ROOT))) {
            out.add(new TaskSuggestion(title, "", summary));
         }
      }
      if (out.isEmpty()) {
         final Matcher hm = NUMBERED_HEADING.matcher(answerMarkdown);
         while (hm.find() && out.size() < max) {
            final String title = cleanTitle(hm.group(1));
            if (isPlausibleTaskTitle(title) && seen.add(title.toLowerCase(Locale.ROOT))) {
               out.add(new TaskSuggestion(title, "", ""));
            }
         }
      }
      return out;
   }

   private static String cleanTitle(final String raw) {
      if (raw == null) {
         return "";
      }
      return raw.replaceAll("\\*+", "")
         .replaceAll("^\\d+[.)]\\s*", "")
         .replaceAll("\\s*[:–—-]\\s*$", "")
         .trim();
   }

   private static boolean isPlausibleTaskTitle(final String title) {
      if (title.length() < 3 || title.length() > 42) {
         return false;
      }
      final String lower = title.toLowerCase(Locale.ROOT);
      return !(lower.equals("requirements") || lower.equals("recommended") || lower.equals("summary")
         || lower.equals("overview") || lower.equals("pros") || lower.equals("cons") || lower.equals("notes")
         || lower.equals("tips") || lower.equals("gear") || lower.equals("inventory") || lower.equals("levels"));
   }
}
