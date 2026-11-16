package com.kamrant.runegpt;

import com.google.inject.Injector;
import com.google.inject.Provides;
import com.kamrant.runegpt.overlay.GuideChecklistOverlay;
import com.kamrant.runegpt.overlay.TargetHighlightOverlay;
import com.kamrant.runegpt.service.BankCache;
import com.kamrant.runegpt.service.GuideTracker;
import com.kamrant.runegpt.service.GuideVerifier;
import com.kamrant.runegpt.service.KeyVault;
import com.kamrant.runegpt.service.TargetHighlighter;
import com.kamrant.runegpt.llm.GPTClient;
import com.kamrant.runegpt.ui.GPTPanel;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;

@Slf4j
@PluginDescriptor(
	name = "RuneGPT",
	description = "AI assistant for quests, bossing, skilling and money making, with wiki-checked guides and a live "
		+ "item checklist. Sends your questions, stats, quest progress, inventory, worn items, bank (once opened) "
		+ "and location to the AI provider you set a key for; looks up quest and item names on the OSRS Wiki.",
	tags = {"ai", "gemini", "claude", "groq", "quest", "guide", "helper", "assistant", "wiki"}
)
public class RuneGPT extends Plugin {
	public static final String LOGO = "logo.png";

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
	private TargetHighlighter targetHighlighter;

	@Inject
	private GuideTracker guideTracker;

	@Inject
	private GuideVerifier guideVerifier;

	@Inject
	private GPTClient gptClient;

	@Inject
	private ConfigManager configManager;

	@Inject
	private KeyVault keyVault;

	@Inject
	private TargetHighlightOverlay targetHighlightOverlay;

	@Inject
	private GuideChecklistOverlay guideChecklistOverlay;

	private NavigationButton navButton;
	private GPTPanel gptPanel;

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
		// Encrypt any API keys still stored in plain text (e.g. from older versions).
		encryptStoredKeys();
		// Make sure the saved keys decrypt on this computer; tell the player now if not.
		gptPanel.checkKeys();
		overlayManager.add(targetHighlightOverlay);
		overlayManager.add(guideChecklistOverlay);
		gptPanel.refreshGuides();
	}

	@Override
	protected void shutDown() throws Exception {
		clientToolbar.removeNavigation(navButton);
		overlayManager.remove(targetHighlightOverlay);
		overlayManager.remove(guideChecklistOverlay);
		// Stop background threads (panel worker, wiki lookups) and UI timers.
		gptPanel.shutdown();
		guideVerifier.shutdown();
		// Remove the world map marker, highlight target and hint arrow (client thread).
		clientThread.invokeLater(() -> {
			guideTracker.shutdown();
			targetHighlighter.shutdown();
		});
		gptClient.clearConversation();
		bankCache.clear();
		gptPanel = null;
		navButton = null;
	}

	@Subscribe
	public void onConfigChanged(final ConfigChanged event) {
		// A key was just entered in the settings: replace it with the encrypted form right away. RuneLite only
		// writes settings to disk / profile sync in batches, so the plain value is never persisted.
		if (RuneGPTConfig.GROUP.equals(event.getGroup())
			&& Arrays.asList(RuneGPTConfig.KEY_SETTINGS).contains(event.getKey())
			&& event.getNewValue() != null && !event.getNewValue().trim().isEmpty()
			&& !KeyVault.isEncrypted(event.getNewValue())) {
			encryptStoredKeys();
		}
	}

	private void encryptStoredKeys() {
		for (final String setting : RuneGPTConfig.KEY_SETTINGS) {
			final String value = configManager.getConfiguration(RuneGPTConfig.GROUP, setting);
			if (value == null || value.trim().isEmpty() || KeyVault.isEncrypted(value)) {
				continue;
			}
			final String encrypted = keyVault.encrypt(value.trim(), setting);
			if (encrypted != null) {
				configManager.setConfiguration(RuneGPTConfig.GROUP, setting, encrypted);
			} else {
				log.warn("RuneGPT: couldn't encrypt the {} setting; it stays as entered", setting);
			}
		}
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
		final int id = event.getContainerId();
		if (id == InventoryID.BANK) {
			bankCache.update(event.getItemContainer().getItems());
		}
		if (id == InventoryID.BANK || id == InventoryID.INV || id == InventoryID.WORN) {
			// Live checklist: re-colour the tracked guide's items.
			guideTracker.onItemsChanged();
		}
	}

	@Subscribe
	public void onGameTick(final GameTick tick) {
		targetHighlighter.onTick();
	}

	@Provides
	RuneGPTConfig provideConfig(final ConfigManager configManager) {
		return configManager.getConfig(RuneGPTConfig.class);
	}

	private BufferedImage getImg() {
		return ImageUtil.loadImageResource(getClass(), LOGO);
	}
}
