package com.kamrant.runegpt;

import com.google.inject.Injector;
import com.google.inject.Provides;
import com.kamrant.runegpt.model.SavedBank;
import com.kamrant.runegpt.overlay.GuideChecklistOverlay;
import com.kamrant.runegpt.overlay.TargetHighlightOverlay;
import com.kamrant.runegpt.overlay.WorldMapGuideOverlay;
import com.kamrant.runegpt.quest.QuestJournalCache;
import com.kamrant.runegpt.quest.QuestProgress;
import com.kamrant.runegpt.service.BankCache;
import com.kamrant.runegpt.service.BankStore;
import com.kamrant.runegpt.service.GuideTracker;
import com.kamrant.runegpt.service.GuideVerifier;
import com.kamrant.runegpt.service.KeyVault;
import com.kamrant.runegpt.service.TargetHighlighter;
import com.kamrant.runegpt.llm.GPTClient;
import com.kamrant.runegpt.ui.GPTPanel;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
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
	private Client client;

	@Inject
	private BankCache bankCache;

	@Inject
	private BankStore bankStore;

	@Inject
	private RuneGPTConfig config;

	@Inject
	private QuestJournalCache questJournalCache;

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

	@Inject
	private WorldMapGuideOverlay worldMapGuideOverlay;

	private NavigationButton navButton;
	private GPTPanel gptPanel;
	private int lastTotalLevel;
	private boolean questProgressDirty;
	// Bank saving: written at most every BANK_SAVE_INTERVAL_MS while banking, and flushed on logout / shutdown.
	private static final long BANK_SAVE_INTERVAL_MS = 5000;
	private boolean bankSaveDirty;
	private long bankSavedAt;
	private long bankAccountHash = -1;

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
		overlayManager.add(worldMapGuideOverlay);
		gptPanel.refreshGuides();
	}

	@Override
	protected void shutDown() throws Exception {
		// Null-safe: if startUp failed part way, RuneLite still calls shutDown.
		if (navButton != null) {
			clientToolbar.removeNavigation(navButton);
		}
		overlayManager.remove(targetHighlightOverlay);
		overlayManager.remove(guideChecklistOverlay);
		overlayManager.remove(worldMapGuideOverlay);
		// Stop background threads (panel worker, wiki lookups) and UI timers.
		if (gptPanel != null) {
			gptPanel.shutdown();
		}
		guideVerifier.shutdown();
		// Remove the world map marker, highlight target and hint arrow (client thread).
		clientThread.invokeLater(() -> {
			guideTracker.shutdown();
			targetHighlighter.shutdown();
		});
		gptClient.clearConversation();
		flushBankSave();
		bankCache.clear();
		questJournalCache.clear();
		gptPanel = null;
		navButton = null;
	}

	@Subscribe
	public void onConfigChanged(final ConfigChanged event) {
		if (RuneGPTConfig.GROUP.equals(event.getGroup()) && RuneGPTConfig.KEY_REMEMBER_BANK.equals(event.getKey())
			&& !config.rememberBank()) {
			bankSaveDirty = false;
			bankStore.deleteAll();
		}
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
		if (event.getGameState() == GameState.LOGGED_IN) {
			restoreSavedBank();
		}
		// Re-personalize guide suggestions on login / logout.
		if (event.getGameState() == GameState.LOGGED_IN || event.getGameState() == GameState.LOGIN_SCREEN) {
			lastTotalLevel = event.getGameState() == GameState.LOGGED_IN ? client.getTotalLevel() : 0;
			questProgressDirty = false;
			gptPanel.refreshGuides();
		}
		if (event.getGameState() == GameState.LOGIN_SCREEN) {
			// Bank and quest journal contents belong to the account that just logged out.
			flushBankSave();
			bankCache.clear();
			bankAccountHash = -1;
			questJournalCache.clear();
		}
	}

	@Subscribe
	public void onStatChanged(final StatChanged event) {
		if (client.getGameState() == GameState.LOGGED_IN) {
			final int total = client.getTotalLevel();
			if (total > 0 && total != lastTotalLevel) {
				lastTotalLevel = total;
				gptPanel.refreshGuides();
			}
		}
	}

	@Subscribe
	public void onVarbitChanged(final VarbitChanged event) {
		if (client.getGameState() == GameState.LOGGED_IN
			&& QuestProgress.isQuestVar(event.getVarbitId(), event.getVarpId())) {
			questProgressDirty = true;
		}
	}

	@Subscribe
	public void onItemContainerChanged(final ItemContainerChanged event) {
		final int id = event.getContainerId();
		if (id == InventoryID.BANK) {
			bankCache.update(event.getItemContainer().getItems());
			bankAccountHash = client.getAccountHash();
			bankSaveDirty = config.rememberBank();
			saveBankIfDue();
		}
		if (id == InventoryID.BANK || id == InventoryID.INV || id == InventoryID.WORN) {
			// Live checklist: re-colour the tracked guide's items.
			guideTracker.onItemsChanged();
		}
	}

	@Subscribe
	public void onGameTick(final GameTick tick) {
		targetHighlighter.onTick();
		questJournalCache.updateFromClient(client);
		saveBankIfDue();
		if (questProgressDirty && gptPanel != null) {
			questProgressDirty = false;
			gptPanel.refreshGuides();
		}
	}

	/** Restores this account's bank from the copy saved on this computer, if the bank hasn't been seen yet. */
	private void restoreSavedBank() {
		if (!config.rememberBank() || bankCache.isKnown()) {
			return;
		}
		final long account = client.getAccountHash();
		final SavedBank saved = bankStore.load(account);
		if (saved != null) {
			bankCache.restore(saved.getItems(), saved.getSavedAt());
			bankAccountHash = account;
			guideTracker.onItemsChanged();
		}
	}

	private void saveBankIfDue() {
		if (bankSaveDirty && System.currentTimeMillis() - bankSavedAt >= BANK_SAVE_INTERVAL_MS) {
			flushBankSave();
		}
	}

	/** Writes the bank now if it changed since the last save (live bank data only, never a restored copy). */
	private void flushBankSave() {
		if (!bankSaveDirty) {
			return;
		}
		bankSaveDirty = false;
		if (config.rememberBank() && bankCache.isKnown() && !bankCache.isFromEarlierSession()) {
			bankStore.save(bankAccountHash, bankCache.getItems(), bankCache.getSeenAt());
			bankSavedAt = System.currentTimeMillis();
		}
	}

	@Provides
	RuneGPTConfig provideConfig(final ConfigManager configManager) {
		return configManager.getConfig(RuneGPTConfig.class);
	}

	/**
	 * The sidebar icon. If the logo resource is missing (e.g. an IDE build that didn't copy resources), use a
	 * plain fallback icon rather than failing to start the whole plugin.
	 */
	private BufferedImage getImg() {
		try {
			return ImageUtil.loadImageResource(getClass(), LOGO);
		} catch (final IllegalArgumentException e) {
			log.warn("RuneGPT: sidebar icon {} not found, using a fallback icon", LOGO);
			return fallbackIcon();
		}
	}

	private static BufferedImage fallbackIcon() {
		final BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		final Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(ColorScheme.BRAND_ORANGE);
		g.fillRoundRect(1, 1, 14, 14, 5, 5);
		g.setColor(Color.BLACK);
		g.setFont(FontManager.getRunescapeBoldFont());
		g.drawString("R", 5, 13);
		g.dispose();
		return img;
	}
}
