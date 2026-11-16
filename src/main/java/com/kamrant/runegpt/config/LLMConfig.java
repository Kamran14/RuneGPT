package com.kamrant.runegpt.config;

/** Provider endpoints, models and limits. */
public final class LLMConfig {

    private LLMConfig() {
    }

    // Prompts live in com.kamrant.runegpt.prompt.Prompts.

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
