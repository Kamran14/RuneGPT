package com.kamrant.runegpt.config;

import okhttp3.MediaType;

public class LLMConfig {
    public static final String INSTRUCTIONS = "You are an assistant for Old School RuneScape (OSRS). " +
    "Provide detailed and accurate information based on the player's stats. " + 
    "Keep prompts clear and concise. Do not add any custom formatting, use plain text";

    public static final String GPT_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=";
	public static final String[] MEDIA_TYPE = { "Content-Type", MediaType.get("application/json").toString() };
}
