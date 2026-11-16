package com.kamrant.runegpt;

import com.google.inject.Injector;
import com.google.inject.Provides;
import com.kamrant.runegpt.panels.GPTPanel;
import com.kamrant.runegpt.quest.QuestHighlightOverlay;
import com.kamrant.runegpt.quest.QuestHighlighter;
import com.kamrant.runegpt.service.BankCache;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;

@Slf4j
@PluginDescriptor(name = "RuneGPT")
public class RuneGPT extends Plugin {
	// gameval InventoryID.BANK
	private static final int BANK_CONTAINER = 95;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private Injector injector;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private ClientThread clientThread;

	@Inject
	private BankCache bankCache;

	@Inject
	private QuestHighlighter questHighlighter;

	@Inject
	private QuestHighlightOverlay questHighlightOverlay;

	private NavigationButton navButton;
	private GPTPanel gptPanel;
	public static final String LOGO = "logo.png";

	@Override
	protected void startUp() throws Exception {
		gptPanel = injector.getInstance(GPTPanel.class);
		navButton = NavigationButton.builder()
				.tooltip("RuneGPT")
				.icon(getImg())
				.priority(12)
				.panel(gptPanel)
				.build();

		clientToolbar.addNavigation(navButton);
		overlayManager.add(questHighlightOverlay);
		gptPanel.refreshGuides();
	}

	@Override
	protected void shutDown() throws Exception {
		clientToolbar.removeNavigation(navButton);
		overlayManager.remove(questHighlightOverlay);
		clientThread.invokeLater(questHighlighter::shutdown);
		gptPanel.shutdown();
	}

	@Subscribe
	public void onGameStateChanged(final GameStateChanged event) {
		// Re-personalize guide suggestions on login / logout.
		if (event.getGameState() == GameState.LOGGED_IN || event.getGameState() == GameState.LOGIN_SCREEN) {
			gptPanel.refreshGuides();
		}
		if (event.getGameState() == GameState.LOGIN_SCREEN) {
			// Bank contents belong to the account that just logged out.
			bankCache.clear();
		}
	}

	@Subscribe
	public void onItemContainerChanged(final ItemContainerChanged event) {
		if (event.getContainerId() == BANK_CONTAINER) {
			bankCache.update(event.getItemContainer().getItems());
		}
	}

	@Subscribe
	public void onGameTick(final GameTick tick) {
		questHighlighter.onTick();
	}

	@Provides
	RuneGPTConfig provideConfig(final ConfigManager configManager) {
		return configManager.getConfig(RuneGPTConfig.class);
	}

	private BufferedImage getImg(){
			return ImageUtil.loadImageResource(getClass(), LOGO);
		}
}
