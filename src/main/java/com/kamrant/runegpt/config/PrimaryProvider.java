package com.kamrant.runegpt.config;

/**
 * Choices for the "Primary LLM Provider" setting: the providers that use your own API key. The keyless
 * Starter provider isn't listed; it's controlled by its own setting and always tried last.
 * Constant names match {@link LLMProvider} so saved settings keep working.
 */
public enum PrimaryProvider {
   GEMINI(LLMProvider.GEMINI),
   CLAUDE(LLMProvider.CLAUDE),
   GROQ(LLMProvider.GROQ);

   private final LLMProvider provider;

   PrimaryProvider(final LLMProvider provider) {
      this.provider = provider;
   }

   public LLMProvider toProvider() {
      return provider;
   }

   @Override
   public String toString() {
      return provider.getDisplayName();
   }
}
