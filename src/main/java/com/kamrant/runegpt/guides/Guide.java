package com.kamrant.runegpt.guides;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A recommended guide: a title and an LLM prompt, both templates with {field} placeholders
 * filled from editable fields (e.g. {monster} = "blue dragons"). The player can edit the
 * fields or the templates themselves.
 */
public final class Guide {

   private final String id;
   private final GuideCategory category;
   private String titleTemplate;
   private String promptTemplate;
   private final LinkedHashMap<String, String> fields;
   private boolean customized;
   // Optional quick choices (attack style, budget...). Not part of saved edits.
   private List<GuideOption> options = Collections.emptyList();

   public Guide(final String id, final GuideCategory category, final String titleTemplate,
      final String promptTemplate, final Map<String, String> fields) {
      this.id = id;
      this.category = category;
      this.titleTemplate = titleTemplate;
      this.promptTemplate = promptTemplate;
      this.fields = new LinkedHashMap<>(fields);
   }

   public Guide copy() {
      final Guide g = new Guide(id, category, titleTemplate, promptTemplate, fields);
      g.customized = customized;
      g.options = options;
      return g;
   }

   /** Adds optional quick choices; returns this for chaining. */
   public Guide withOptions(final GuideOption... opts) {
      this.options = Collections.unmodifiableList(new ArrayList<>(Arrays.asList(opts)));
      return this;
   }

   public List<GuideOption> getOptions() {
      return options;
   }

   /**
    * The prompt plus any quick choices the player picked, e.g. "My preferences: Attack style: Ranged."
    * {@code picks} maps option label to choice; missing labels mean no preference.
    */
   public String prompt(final Map<String, String> picks) {
      final List<String> prefs = new ArrayList<>();
      for (final GuideOption option : options) {
         final String pick = picks.get(option.getLabel());
         if (pick != null) {
            prefs.add(option.getLabel() + ": " + pick);
         }
      }
      return prefs.isEmpty() ? prompt() : prompt() + "\n\nMy preferences: " + String.join("; ", prefs) + ".";
   }

   public String getId() {
      return id;
   }

   public GuideCategory getCategory() {
      return category;
   }

   public String getTitleTemplate() {
      return titleTemplate;
   }

   public String getPromptTemplate() {
      return promptTemplate;
   }

   public Map<String, String> getFields() {
      return fields;
   }

   public boolean isCustomized() {
      return customized;
   }

   public void apply(final String titleTemplate, final String promptTemplate, final Map<String, String> fields) {
      this.titleTemplate = titleTemplate;
      this.promptTemplate = promptTemplate;
      this.fields.clear();
      this.fields.putAll(fields);
      this.customized = true;
   }

   public String title() {
      return fill(titleTemplate, fields);
   }

   public String prompt() {
      return fill(promptTemplate, fields);
   }

   public static String fill(final String template, final Map<String, String> fields) {
      String out = template;
      for (final Map.Entry<String, String> e : fields.entrySet()) {
         out = out.replace("{" + e.getKey() + "}", e.getValue());
      }
      return out;
   }
}
