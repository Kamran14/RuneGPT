package com.kamrant.runegpt.config;

import okhttp3.MediaType;

public class LLMConfig {
    public static final String INSTRUCTIONS = "You are an assistant for Old School RuneScape (OSRS). " +
    "Provide detailed and accurate information based on the player's stats. " +
    "Keep prompts clear and concise. Do not add any custom formatting, use plain text";

    public static final String GPT_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=";
	public static final String[] MEDIA_TYPE = { "Content-Type", MediaType.get("application/json").toString() };

    // Claude (Anthropic Messages API)
    public static final String CLAUDE_URL = "https://api.anthropic.com/v1/messages";
    public static final String CLAUDE_API_VERSION = "2026-10-21";
    public static final String CLAUDE_DEFAULT_MODEL = "claude-opus-5-5";
    public static final String CLAUDE_FALLBACK_BETA = "server-side-fallback-2026-07-01";
    public static final int CLAUDE_MAX_TOKENS = 16000;
}
