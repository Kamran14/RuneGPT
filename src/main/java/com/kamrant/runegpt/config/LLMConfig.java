package com.kamrant.runegpt.config;

/** Provider endpoints, models, headers and limits. */
public final class LLMConfig {

   private LLMConfig() {
   }

   // Prompts live in com.kamrant.runegpt.prompt.Prompts.

   // Gemini
   public static final String GEMINI_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent";
   public static final String HEADER_GEMINI_KEY = "x-goog-api-key";

   // Claude (Anthropic Messages API)
   public static final String CLAUDE_URL = "https://api.anthropic.com/v1/messages";
   public static final String CLAUDE_API_VERSION = "2026-10-21";
   public static final String CLAUDE_DEFAULT_MODEL = "claude-opus-5-5";
   public static final String CLAUDE_FALLBACK_BETA = "server-side-fallback-2026-07-01";
   public static final String HEADER_CLAUDE_KEY = "x-api-key";
   public static final String HEADER_CLAUDE_VERSION = "anthropic-version";
   public static final String HEADER_CLAUDE_BETA = "anthropic-beta";
   public static final int CLAUDE_MAX_TOKENS = 16000;

   // Groq (free tier, OpenAI-compatible)
   public static final String GROQ_URL = "https://api.groq.com/openai/v1/chat/completions";
   public static final String GROQ_DEFAULT_MODEL = "openai/gpt-oss-120b";
   public static final String HEADER_AUTHORIZATION = "Authorization";
   public static final String BEARER_PREFIX = "Bearer ";
   // Free tier allows ~8K tokens/minute, so keep the reply budget modest.
   public static final int GROQ_MAX_TOKENS = 2048;

   // Starter: keyless Pollinations "Classic" text endpoint (plain-text replies, rate-limited per IP).
   public static final String STARTER_URL = "https://text.pollinations.ai/";
   // Live limit observed: 1 reply every 45 seconds per IP (402 + Retry-After when exceeded).
   public static final int STARTER_MIN_INTERVAL_SECONDS = 45;
   // Wait at most this long client-side for the next free slot before giving up on Starter.
   public static final int STARTER_MAX_WAIT_SECONDS = 60;

   // Shared provider response / error constants
   public static final String TRUNCATED_SUFFIX = "\n\n[Response truncated]";
   public static final String ERR_EMPTY_ANSWER = "empty answer returned";
   public static final String ERR_NO_ANSWER = "no answer returned";
   public static final String ERR_BLOCKED_ANSWER = "no answer returned (prompt may have been blocked)";
   public static final String ERR_DECLINED = "declined to answer";
   public static final String ERR_CANCELLED = "cancelled";
   public static final String ERR_UNREADABLE = "unreadable response";
}
