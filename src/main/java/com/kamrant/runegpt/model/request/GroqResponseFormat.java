package com.kamrant.runegpt.model.request;

import com.google.gson.annotations.SerializedName;

/** Asks Groq for a JSON object reply ({"type": "json_object"}). */
public final class GroqResponseFormat {

   @SerializedName("type")
   private final String type = "json_object";
}
