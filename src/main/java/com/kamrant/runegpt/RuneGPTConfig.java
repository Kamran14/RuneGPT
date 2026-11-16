package com.kamrant.runegpt;

import com.kamrant.runegpt.config.LLMConfig;
import com.kamrant.runegpt.config.PrimaryProvider;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup(RuneGPTConfig.GROUP)
public interface RuneGPTConfig extends Config {

	String GROUP = "RuneGPTConfig";

	/** Settings holding API keys. They're stored encrypted (see KeyVault). */
	String[] KEY_SETTINGS = {"apiKey", "claudeApiKey", "groqApiKey"};

	/** What leaves the client, and where. Shown on the providers section; also in the plugin description and README. */
	String DATA_DISCLOSURE = "Data sent: your questions, skill levels, quest progress, inventory, worn items, bank "
		+ "contents (once you open your bank) and location are sent to the AI provider(s) you set a key for "
		+ "(Google Gemini, Anthropic Claude, Groq). Quest and item names are looked up on the OSRS Wiki. "
		+ "API keys are stored encrypted in your RuneLite settings (the decryption key stays on this computer) and "
		+ "are only sent to their own provider.";

	@ConfigSection(
		name = "AI providers",
		description = DATA_DISCLOSURE,
		position = 0
	)
	String providersSection = "providers";

	@ConfigSection(
		name = "Free starter service",
		description = "Optional keyless service for trying RuneGPT without an API key. Off by default.",
		position = 1
	)
	String starterSection = "starter";

	@ConfigSection(
		name = "In-game",
		description = "Overlays shown while a guide is tracked",
		position = 2
	)
	String inGameSection = "inGame";

	@ConfigItem(
		keyName = "provider",
		name = "Primary LLM Provider",
		description = "Provider to try first. If it fails or runs out of tokens, the others with a key are tried automatically",
		position = 0,
		section = providersSection
	)
	default PrimaryProvider provider() {
		return PrimaryProvider.GEMINI;
	}

	@ConfigItem(
		keyName = "apiKey",
		name = "Gemini API Key",
		description = "Key from Google AI Studio (aistudio.google.com). Questions and player data are sent to Google. Stored encrypted; re-enter it on a new computer.",
		secret = true,
		position = 1,
		section = providersSection
	)
	default String apiKey() {
		return "";
	}

	@ConfigItem(
		keyName = "claudeApiKey",
		name = "Claude API Key",
		description = "Key from console.anthropic.com. Questions and player data are sent to Anthropic. Stored encrypted; re-enter it on a new computer.",
		secret = true,
		position = 2,
		section = providersSection
	)
	default String claudeApiKey() {
		return "";
	}

	@ConfigItem(
		keyName = "claudeModel",
		name = "Claude Model",
		description = "Claude model ID, e.g. claude-opus-5-5, claude-sonnet-5-5, claude-haiku-5-5",
		position = 3,
		section = providersSection
	)
	default String claudeModel() {
		return LLMConfig.CLAUDE_DEFAULT_MODEL;
	}

	@ConfigItem(
		keyName = "groqApiKey",
		name = "Groq API Key",
		description = "Free key from console.groq.com (no card needed). Questions and player data are sent to Groq. Stored encrypted; re-enter it on a new computer.",
		secret = true,
		position = 4,
		section = providersSection
	)
	default String groqApiKey() {
		return "";
	}

	@ConfigItem(
		keyName = "groqModel",
		name = "Groq Model",
		description = "Groq model ID, e.g. openai/gpt-oss-120b, openai/gpt-oss-20b",
		position = 5,
		section = providersSection
	)
	default String groqModel() {
		return LLMConfig.GROQ_DEFAULT_MODEL;
	}

	@ConfigItem(
		keyName = "temperature",
		name = "LLM Temperature",
		description = "Set the temperature for the LLM (Gemini and Groq; current Claude models do not accept it)",
		position = 6,
		section = providersSection
	)
	default double temperature() {
		return 0.7;
	}

	@ConfigItem(
		keyName = "useStarterProvider",
		name = "Use free starter service",
		description = "Off by default. When on, your questions and player data (stats, quest progress, inventory, "
			+ "worn items, bank and location) are sent to a free third-party service (Pollinations, "
			+ "text.pollinations.ai). It needs no key, but is slow, limited to about one reply every 45 seconds, and "
			+ "its answers are often inaccurate. Used only after your keyed providers, or alone if you have no key.",
		position = 0,
		section = starterSection
	)
	default boolean useStarterProvider() {
		return false;
	}

	@ConfigItem(
		keyName = "showGuideOverlay",
		name = "Show guide checklist in game",
		description = "While a guide is tracked, show its item checklist and directions as an in-game overlay",
		position = 0,
		section = inGameSection
	)
	default boolean showGuideOverlay() {
		return true;
	}
}
