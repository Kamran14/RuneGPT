package com.kamrant.runegpt;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class RuneGPTTest{
	
	@SuppressWarnings("unchecked")
	public static void main(final String[] args) throws Exception{
		ExternalPluginManager.loadBuiltin(RuneGPT.class);
		RuneLite.main(args);
	}
}