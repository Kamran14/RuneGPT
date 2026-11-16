package com.kamrant.runegpt.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;

/** Lenient helpers for reading the JSON the LLM returns (tolerates code fences and stray text). */
public final class ResponseParser {

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
}
