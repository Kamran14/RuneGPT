package com.kamrant.runegpt;

import com.google.inject.Provides;
import com.kamrant.runegpt.handler.GPTClient;
import com.kamrant.runegpt.panels.GPTPanel;
import java.awt.image.BufferedImage;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

@Slf4j
@PluginDescriptor(name = "RuneGPT")
public class RuneGPT extends Plugin {
	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private Client client;

	@Inject
	private GPTClient gptClient;

	@Inject
	private ScheduledExecutorService executorService;

	private NavigationButton navButton;
	private GPTPanel gptPanel;
	public static final String LOGO = "logo.png";

	@Override
	protected void startUp() throws Exception {
		gptPanel = new GPTPanel(client, gptClient, executorService);
		navButton = NavigationButton.builder()
				.tooltip("RuneGPT")
				.icon(getImg())
				.priority(12)
				.panel(gptPanel)
				.build();

		clientToolbar.addNavigation(navButton);
	}

	@Override
	protected void shutDown() throws Exception {
		clientToolbar.removeNavigation(navButton);
	}
	@Provides
	RuneGPTConfig provideConfig(final ConfigManager configManager) {
		return configManager.getConfig(RuneGPTConfig.class);
	}

	private BufferedImage getImg(){
			return ImageUtil.loadImageResource(getClass(), LOGO);
		}
}
