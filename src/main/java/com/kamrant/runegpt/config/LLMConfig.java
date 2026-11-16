package com.kamrant.runegpt.config;

public class LLMConfig {
    public static final String INSTRUCTIONS = "You are an assistant for Old School RuneScape (OSRS). " +
    "Provide accurate, practical advice tailored to the player's stats and quest progress below. " +
    "Answers are shown in a narrow sidebar (about 200px wide), so keep them short and scannable: " +
    "use Markdown with brief ### headings, bullet lists and **bold** for key items, gear and locations. " +
    "Avoid tables wider than 2 columns, long paragraphs and code blocks. " +
    "If something depends on information you don't have (e.g. bank contents), say so briefly.";

    // Gemini
    public static final String GEMINI_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent";

    // Claude (Anthropic Messages API)
    public static final String CLAUDE_URL = "https://api.anthropic.com/v1/messages";
    public static final String CLAUDE_API_VERSION = "2026-10-21";
    public static final String CLAUDE_DEFAULT_MODEL = "claude-opus-5-5";
    public static final String CLAUDE_FALLBACK_BETA = "server-side-fallback-2026-07-01";
    public static final int CLAUDE_MAX_TOKENS = 16000;

    // Groq (free tier, OpenAI-compatible)
    public static final String GROQ_URL = "https://api.groq.com/openai/v1/chat/completions";
    public static final String GROQ_DEFAULT_MODEL = "openai/gpt-oss-120b";
    // Free tier allows ~8K tokens/minute, so keep the reply budget modest.
    public static final int GROQ_MAX_TOKENS = 2048;
}
