package com.kamrant.runegpt.wiki;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Reads pages from the Old School RuneScape Wiki (raw wikitext) and its search. Results are cached
 * for the session. Uses RuneLite's HTTP client, which identifies itself as RuneLite.
 */
@Slf4j
@Singleton
public class WikiClient {

   private static final String BASE = "https://oldschool.runescape.wiki";

   private final OkHttpClient http;
   private final Gson gson;
   // Title -> raw wikitext ("" = page doesn't exist).
   private final Map<String, String> pageCache = new ConcurrentHashMap<>();

   @Inject
   public WikiClient(final OkHttpClient http, final Gson gson) {
      this.http = http.newBuilder().readTimeout(Duration.ofSeconds(15)).build();
      this.gson = gson;
   }

   public static String pageUrl(final String title) {
      return BASE + "/w/" + encodeTitle(title);
   }

   /** Raw wikitext of a page, or null if it doesn't exist. Throws on network errors. */
   public String raw(final String title) throws IOException {
      final String cached = pageCache.get(title);
      if (cached != null) {
         return cached.isEmpty() ? null : cached;
      }
      final Request request = new Request.Builder().url(pageUrl(title) + "?action=raw").build();
      try (Response response = http.newCall(request).execute()) {
         if (response.code() == 404) {
            pageCache.put(title, "");
            return null;
         }
         if (!response.isSuccessful() || response.body() == null) {
            throw new IOException("wiki returned HTTP " + response.code());
         }
         final String text = response.body().string();
         pageCache.put(title, text);
         return text;
      }
   }

   /** Page titles matching a search term, best first. Empty on any error. */
   public List<String> search(final String term, final int limit) {
      final HttpUrl url = HttpUrl.parse(BASE + "/api.php").newBuilder()
         .addQueryParameter("action", "opensearch")
         .addQueryParameter("search", term)
         .addQueryParameter("limit", String.valueOf(limit))
         .addQueryParameter("namespace", "0")
         .addQueryParameter("format", "json")
         .build();
      try (Response response = http.newCall(new Request.Builder().url(url).build()).execute()) {
         if (!response.isSuccessful() || response.body() == null) {
            return Collections.emptyList();
         }
         // ["term", [titles], [descriptions], [urls]]
         final JsonArray json = gson.fromJson(response.body().string(), JsonArray.class);
         final List<String> titles = new ArrayList<>();
         json.get(1).getAsJsonArray().forEach(t -> titles.add(t.getAsString()));
         return titles;
      } catch (final Exception e) {
         log.debug("RuneGPT: wiki search failed", e);
         return Collections.emptyList();
      }
   }

   private static String encodeTitle(final String title) {
      try {
         return URLEncoder.encode(title.replace(' ', '_'), StandardCharsets.UTF_8.name())
            .replace("%2F", "/");
      } catch (final java.io.UnsupportedEncodingException e) {
         throw new IllegalStateException(e);
      }
   }
}
