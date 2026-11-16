package com.kamrant.runegpt.guides;

import java.util.LinkedHashMap;
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
      return g;
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
