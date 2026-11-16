package com.kamrant.runegpt;

import com.kamrant.runegpt.config.LLMConfig;
import com.kamrant.runegpt.config.LLMProvider;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("RuneGPTConfig")
public interface RuneGPTConfig extends Config {

	@ConfigItem(
		keyName = "provider",
		name = "Primary LLM Provider",
		description = "Provider to try first. If it fails or runs out of tokens, the others with a key are tried automatically",
		position = 0
	)
	default LLMProvider provider() {
		return LLMProvider.GEMINI;
	}

	@ConfigItem(
		keyName = "apiKey",
		name = "Gemini API Key",
		description = "Enter your Gemini key",
		secret = true,
		position = 1
	)
	default String apiKey(){
		return "";
	}

	@ConfigItem(
		keyName = "claudeApiKey",
		name = "Claude API Key",
		description = "Enter your Anthropic API key (console.anthropic.com)",
		secret = true,
		position = 2
	)
	default String claudeApiKey() {
		return "";
	}

	@ConfigItem(
		keyName = "claudeModel",
		name = "Claude Model",
		description = "Claude model ID, e.g. claude-opus-5-5, claude-sonnet-5-5, claude-haiku-5-5",
		position = 3
	)
	default String claudeModel() {
		return LLMConfig.CLAUDE_DEFAULT_MODEL;
	}

	@ConfigItem(
		keyName = "groqApiKey",
		name = "Groq API Key",
		description = "Free key from console.groq.com (no card needed)",
		secret = true,
		position = 4
	)
	default String groqApiKey() {
		return "";
	}

	@ConfigItem(
		keyName = "groqModel",
		name = "Groq Model",
		description = "Groq model ID, e.g. openai/gpt-oss-120b, openai/gpt-oss-20b",
		position = 5
	)
	default String groqModel() {
		return LLMConfig.GROQ_DEFAULT_MODEL;
	}

 	@ConfigItem(
	   keyName = "temperature",
	   name = "LLM Temperature",
	   description = "Set the temperature for the LLM (Gemini and Groq; current Claude models do not accept it)",
	   position = 6
	)
	default double temperature() {
	        return 0.7;

	}

	@ConfigItem(
		keyName = "enable",
		name = "Set API Key",
		description = "Toggle when you have updated your API Key",
		position = 7
	)
	default boolean enable() {
		return false;
	}
}
