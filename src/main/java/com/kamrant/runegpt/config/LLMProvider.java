package com.kamrant.runegpt.config;

public enum LLMProvider {
    GEMINI("Gemini"),
    CLAUDE("Claude"),
    GROQ("Groq");

    private final String displayName;

    LLMProvider(final String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
