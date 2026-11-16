package com.kamrant.runegpt.quest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;

/**
 * Remembers the completed ({@code <str>}) and active lines from any in-game Quest Journal the player opens
 * during the session. Updated on the client thread each tick while the Quest Journal widget is open and
 * cleared on logout.
 */
@Singleton
public class QuestJournalCache {

   private final Map<String, List<String>> completedByQuest = new ConcurrentHashMap<>();
   private final Map<String, List<String>> activeByQuest = new ConcurrentHashMap<>();

   /** Call on the client thread (e.g. from {@code onGameTick}) to capture the open Quest Journal, if any. */
   public void updateFromClient(final Client client) {
      final Widget titleWidget = client.getWidget(InterfaceID.Questjournal.TITLE);
      if (titleWidget == null || titleWidget.isHidden() || titleWidget.getText() == null) {
         return;
      }
      final String rawTitle = stripTags(titleWidget.getText()).trim();
      if (rawTitle.isEmpty()) {
         return;
      }
      final List<String> completed = new ArrayList<>();
      final List<String> active = new ArrayList<>();

      final Widget layer = client.getWidget(InterfaceID.Questjournal.TEXTLAYER);
      if (layer != null && layer.getDynamicChildren() != null) {
         for (final Widget child : layer.getDynamicChildren()) {
            collectLine(child, completed, active);
         }
      }
      for (int id = InterfaceID.Questjournal.QJ1; id <= InterfaceID.Questjournal.QJ18; id++) {
         collectLine(client.getWidget(id), completed, active);
      }

      if (!completed.isEmpty() || !active.isEmpty()) {
         final String key = normalizeKey(rawTitle);
         completedByQuest.put(key, Collections.unmodifiableList(completed));
         activeByQuest.put(key, Collections.unmodifiableList(active));
      }
   }

   public List<String> completedLines(final String questName) {
      return lookup(completedByQuest, questName);
   }

   public List<String> activeLines(final String questName) {
      return lookup(activeByQuest, questName);
   }

   public Map<String, List<String>> allCompleted() {
      return Collections.unmodifiableMap(completedByQuest);
   }

   public Map<String, List<String>> allActive() {
      return Collections.unmodifiableMap(activeByQuest);
   }

   public void clear() {
      completedByQuest.clear();
      activeByQuest.clear();
   }

   private static List<String> lookup(final Map<String, List<String>> map, final String questName) {
      if (questName == null || questName.isEmpty()) {
         return Collections.emptyList();
      }
      final String key = normalizeKey(questName);
      final List<String> exact = map.get(key);
      if (exact != null) {
         return exact;
      }
      for (final Map.Entry<String, List<String>> e : map.entrySet()) {
         if (e.getKey().contains(key) || key.contains(e.getKey())) {
            return e.getValue();
         }
      }
      return Collections.emptyList();
   }

   private static void collectLine(final Widget w, final List<String> completed, final List<String> active) {
      if (w == null || w.isHidden() || w.getText() == null) {
         return;
      }
      final String raw = w.getText().trim();
      final String clean = stripTags(raw).trim();
      if (clean.isEmpty()) {
         return;
      }
      if (raw.contains("<str>")) {
         completed.add(clean);
      } else {
         active.add(clean);
      }
   }

   private static String stripTags(final String s) {
      return s.replaceAll("<br\\s*/?>", " ").replaceAll("<[^>]+>", "").replaceAll("\\s+", " ");
   }

   private static String normalizeKey(final String s) {
      return s.toLowerCase(Locale.ROOT).replace("’", "'").replace("'", "").replaceAll("[^a-z0-9]+", " ").trim();
   }
}
