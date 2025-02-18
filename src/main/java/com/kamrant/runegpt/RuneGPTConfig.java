package com.kamrant.runegpt;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import okhttp3.MediaType;

@ConfigGroup("RuneGPTConfig")
public interface RuneGPTConfig extends Config{
	public static final String GPT_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=";
	public static final String[] MEDIA_TYPE = {"Content-Type", MediaType.get("application/json").toString()};

	@ConfigItem(
		keyName = "apiKey",
		name = "Gemini API Key",
		description = "Enter your Gemini key"
	)
	default String apiKey(){
		return "";
	}

	@ConfigItem(
		keyName = "enable",
		name = "Set API Key",
		description = "Toggle when you have updated your API Key"
	)
	default boolean enable(){
		return false;
	}
}
