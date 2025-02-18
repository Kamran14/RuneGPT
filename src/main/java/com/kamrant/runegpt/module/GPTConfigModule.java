package com.kamrant.runegpt.module;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.kamrant.runegpt.RuneGPTConfig;
import com.kamrant.runegpt.handler.GPTClient;

import net.runelite.client.config.ConfigManager;

public class GPTConfigModule extends AbstractModule{

   @Override
   protected void configure() {}
   
   @Provides
   RuneGPTConfig provideConfig(final ConfigManager configManager){
      return configManager.getConfig(RuneGPTConfig.class);
   }

   @Provides
   GPTClient provideGTPService(final RuneGPTConfig config){
      return GPTClient.getInstance(config);
   }

}
