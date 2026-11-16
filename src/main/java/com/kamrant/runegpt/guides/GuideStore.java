package com.kamrant.runegpt.guides;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.kamrant.runegpt.service.PlayerSnapshot;
import java.util.LinkedHashMap;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;

/**
 * Merges the default guides with the player's saved edits. Edits are stored per guide id in
 * RuneLite's config, so they survive restarts and sync with the player's RuneLite profile.
 */
@Slf4j
@Singleton
public class GuideStore {

   private static final String GROUP = "RuneGPTConfig";
   private static final String KEY_PREFIX = "guide.";

   private final ConfigManager configManager;
   private final Gson gson;

   /** Saved form of an edited guide. */
   private static final class Saved {
      String title;
      String prompt;
      LinkedHashMap<String, String> fields;
   }

   @Inject
   public GuideStore(final ConfigManager configManager, final Gson gson) {
      this.configManager = configManager;
      this.gson = gson;
   }

   public List<Guide> guides(final GuideCategory category, final PlayerSnapshot snapshot) {
      final List<Guide> guides = GuideCatalog.defaults(category, snapshot);
      for (final Guide guide : guides) {
         final String json = configManager.getConfiguration(GROUP, KEY_PREFIX + guide.getId());
         if (json == null || json.isEmpty()) {
            continue;
         }
         try {
            final Saved saved = gson.fromJson(json, Saved.class);
            if (saved != null && saved.title != null && saved.prompt != null && saved.fields != null) {
               guide.apply(saved.title, saved.prompt, saved.fields);
            }
         } catch (final JsonSyntaxException e) {
            log.warn("RuneGPT: ignoring unreadable saved guide {}", guide.getId());
         }
      }
      return guides;
   }

   public void save(final Guide guide) {
      final Saved saved = new Saved();
      saved.title = guide.getTitleTemplate();
      saved.prompt = guide.getPromptTemplate();
      saved.fields = new LinkedHashMap<>(guide.getFields());
      configManager.setConfiguration(GROUP, KEY_PREFIX + guide.getId(), gson.toJson(saved));
   }

   public void reset(final Guide guide) {
      configManager.unsetConfiguration(GROUP, KEY_PREFIX + guide.getId());
   }
}
