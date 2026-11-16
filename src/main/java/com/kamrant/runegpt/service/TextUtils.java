package com.kamrant.runegpt.service;

import java.util.Locale;

/** Shared string normalization, capitalization and HTML-stripping helpers. */
public final class TextUtils {

   private TextUtils() {
   }

   /** Uppercases the first character ("knife" -> "Knife"); returns "" for null or empty. */
   public static String capitalize(final String s) {
      if (s == null || s.isEmpty()) {
         return "";
      }
      return Character.toUpperCase(s.charAt(0)) + s.substring(1);
   }

   /** Lowercase alphanumeric words separated by single spaces, with apostrophes stripped ("Cook's" -> "cooks"). */
   public static String normalizeWords(final String s) {
      if (s == null || s.isEmpty()) {
         return "";
      }
      return s.toLowerCase(Locale.ROOT)
         .replace("’", "'")
         .replace("'", "")
         .replaceAll("[^a-z0-9]+", " ")
         .trim()
         .replaceAll("\\s+", " ");
   }

   /** Strips widget HTML tags ({@code <br>}, {@code <col=...>}, {@code <str>}) and collapses whitespace. */
   public static String stripHtmlTags(final String s) {
      if (s == null || s.isEmpty()) {
         return "";
      }
      return s.replaceAll("<br\\s*/?>", " ")
         .replaceAll("<[^>]+>", "")
         .replaceAll("\\s+", " ")
         .trim();
   }

   /** Truncates {@code s} to at most {@code maxChars}, appending "…" when shortened. */
   public static String shorten(final String s, final int maxChars) {
      if (s == null || s.length() <= maxChars) {
         return s == null ? "" : s;
      }
      return s.substring(0, Math.max(0, maxChars - 1)) + "…";
   }
}
