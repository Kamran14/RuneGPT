package com.kamrant.runegpt.model;

import java.util.LinkedHashMap;

/** Stored form of a guide the player edited (saved as JSON in RuneLite's config). */
public final class SavedGuide {
   public String title;
   public String prompt;
   public LinkedHashMap<String, String> fields;
}
