package com.kamrant.runegpt;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("RuneGPTConfig")
public interface RuneGPTConfig extends Config {

	@ConfigItem(
		keyName = "apiKey",
		name = "Gemini API Key",
		description = "Enter your Gemini key"
	)
	default String apiKey(){
		return "";
	}

 	@ConfigItem(
	   keyName = "temperature",
	   name = "LLM Temperature",
	   description = "Set the temperature for the LLM",
	   position = 3
	)
	default double temperature() {
	        return 0.7;
	
	}

	@ConfigItem(
		keyName = "enable",
		name = "Set API Key",
		description = "Toggle when you have updated your API Key"
	)
	default boolean enable() {
		return false;
	}
}
