package com.kamrant.runegpt.config;

public enum LLMProvider {
    GEMINI("Gemini"),
    CLAUDE("Claude"),
    GROQ("Groq"),
    /** Keyless Pollinations service: always available, slow and rate-limited. */
    STARTER("Starter");

    private final String displayName;

    LLMProvider(final String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * True for providers with tight token budgets (Groq's free tier, the keyless Starter service): they get the
     * compact prompt with a trimmed bank instead of the full one.
     */
    public boolean isTokenLimited() {
        return this == GROQ || this == STARTER;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
