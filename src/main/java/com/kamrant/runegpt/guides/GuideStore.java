package com.kamrant.runegpt.guides;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.kamrant.runegpt.RuneGPTConfig;
import com.kamrant.runegpt.model.SavedGuide;
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

   private static final String KEY_PREFIX = "guide.";

   private final ConfigManager configManager;
   private final Gson gson;

   @Inject
   public GuideStore(final ConfigManager configManager, final Gson gson) {
      this.configManager = configManager;
      this.gson = gson;
   }

   public List<Guide> guides(final GuideCategory category, final PlayerSnapshot snapshot) {
      final List<Guide> guides = GuideCatalog.defaults(category, snapshot);
      for (final Guide guide : guides) {
         final String json = configManager.getConfiguration(RuneGPTConfig.GROUP, KEY_PREFIX + guide.getId());
         if (json == null || json.isEmpty()) {
            continue;
         }
         try {
            final SavedGuide saved = gson.fromJson(json, SavedGuide.class);
            if (saved != null && saved.title != null && saved.prompt != null && saved.fields != null) {
               GuideCatalog.applySavedIfValid(guide, saved.title, saved.prompt, saved.fields, snapshot);
            }
         } catch (final JsonSyntaxException e) {
            log.warn("RuneGPT: ignoring unreadable saved guide {}", guide.getId());
         }
      }
      return guides;
   }

   public void save(final Guide guide) {
      final SavedGuide saved = new SavedGuide();
      saved.title = guide.getTitleTemplate();
      saved.prompt = guide.getPromptTemplate();
      saved.fields = new LinkedHashMap<>(guide.getFields());
      configManager.setConfiguration(RuneGPTConfig.GROUP, KEY_PREFIX + guide.getId(), gson.toJson(saved));
   }

   public void reset(final Guide guide) {
      configManager.unsetConfiguration(RuneGPTConfig.GROUP, KEY_PREFIX + guide.getId());
   }
}
