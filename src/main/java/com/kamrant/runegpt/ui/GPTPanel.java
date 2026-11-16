package com.kamrant.runegpt.ui;

import com.kamrant.runegpt.RuneGPT;
import com.kamrant.runegpt.guides.GuideStore;
import com.kamrant.runegpt.llm.GPTClient;
import com.kamrant.runegpt.model.AssistantResponse;
import com.kamrant.runegpt.model.GuidePlan;
import com.kamrant.runegpt.model.ProviderStatus;
import com.kamrant.runegpt.model.QuestStep;
import com.kamrant.runegpt.quest.QuestAssistant;
import com.kamrant.runegpt.service.AssistantService;
import com.kamrant.runegpt.service.BankCache;
import com.kamrant.runegpt.service.GuideTracker;
import com.kamrant.runegpt.service.PlayerSnapshot;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.ImageUtil;

/**
 * Sidebar "AI Hub": header with connection status, the tracked guide (when one is active), then the chat
 * and the collapsible guide sections separated by a draggable divider.
 */
@Slf4j
public class GPTPanel extends PluginPanel {

   private static final Color STATUS_OK = new Color(90, 190, 90);
   private static final Color STATUS_READY = ColorScheme.LIGHT_GRAY_COLOR;
   private static final Color STATUS_ERROR = new Color(220, 80, 70);

   private final Client client;
   private final ClientThread clientThread;
   private final ItemManager itemManager;
   private final BankCache bankCache;
   private final GPTClient gptClient;
   private final AssistantService assistant;
   private final QuestAssistant questAssistant;
   private final GuideTracker tracker;

   private final GuidesView guidesView;
   private final ChatView chatView;
   private final TrackedGuideView trackedView;
   private final JLabel status = new JLabel();

   // LLM and wiki calls can take a while (and fail over across providers), so keep them off
   // RuneLite's shared executor.
   private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
      final Thread t = new Thread(r, "RuneGPT-worker");
      t.setDaemon(true);
      return t;
   });

   private final QuestStepActions actions = new QuestStepActions() {
      @Override
      public void track(final GuidePlan plan) {
         startTracking(plan);
      }

      @Override
      public void nextStep(final String quest, final String completedStep) {
         questStep(quest, null, completedStep);
      }

      @Override
      public void useQuest(final String quest) {
         questStep(quest, null, null);
      }
   };

   @Inject
   public GPTPanel(final Client client, final ClientThread clientThread, final ItemManager itemManager,
      final BankCache bankCache, final GPTClient gptClient, final AssistantService assistant,
      final GuideStore guideStore, final QuestAssistant questAssistant, final GuideTracker tracker) {
      super(false);
      this.client = client;
      this.clientThread = clientThread;
      this.itemManager = itemManager;
      this.bankCache = bankCache;
      this.gptClient = gptClient;
      this.assistant = assistant;
      this.questAssistant = questAssistant;
      this.tracker = tracker;

      setLayout(new BorderLayout());
      setBackground(ColorScheme.DARK_GRAY_COLOR);
      setBorder(new EmptyBorder(6, 6, 6, 6));

      guidesView = new GuidesView(guideStore, this::ask, (quest, picks) -> questStep(quest, picks, null),
         this::questRequirements);
      chatView = new ChatView(text -> ask(text, text), gptClient::clearConversation);
      trackedView = new TrackedGuideView(tracker::stop);
      tracker.addListener(plan -> SwingUtilities.invokeLater(() -> trackedView.setPlan(plan)));

      final JPanel north = new JPanel(new BorderLayout());
      north.setOpaque(false);
      north.add(header(), BorderLayout.NORTH);
      final JPanel trackedWrap = new JPanel(new BorderLayout());
      trackedWrap.setOpaque(false);
      trackedWrap.setBorder(new EmptyBorder(0, 0, 6, 0));
      trackedWrap.add(trackedView, BorderLayout.CENTER);
      north.add(trackedWrap, BorderLayout.SOUTH);
      add(north, BorderLayout.NORTH);

      // Sections scroll on their own, below the chat.
      final ViewportWidthPanel sectionsList = new ViewportWidthPanel();
      sectionsList.setLayout(new BorderLayout());
      sectionsList.setOpaque(false);
      sectionsList.add(guidesView, BorderLayout.NORTH);
      final JScrollPane sectionsScroll = new JScrollPane(sectionsList, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
         JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
      sectionsScroll.setBorder(null);
      sectionsScroll.setOpaque(false);
      sectionsScroll.getViewport().setOpaque(false);
      sectionsScroll.getVerticalScrollBar().setUnitIncrement(16);
      sectionsScroll.setMinimumSize(new Dimension(0, 40));

      final JPanel sectionsWrap = new JPanel(new BorderLayout());
      sectionsWrap.setOpaque(false);
      sectionsWrap.setBorder(new EmptyBorder(6, 0, 0, 0));
      sectionsWrap.add(sectionsScroll, BorderLayout.CENTER);

      chatView.setMinimumSize(new Dimension(0, 140));
      final JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, chatView, sectionsWrap);
      split.setResizeWeight(0.5);
      split.setDividerSize(8);
      split.setContinuousLayout(true);
      split.setOneTouchExpandable(true);
      split.setBorder(BorderFactory.createEmptyBorder());
      split.setOpaque(false);
      add(split, BorderLayout.CENTER);

      updateStatus();
   }

   private JPanel header() {
      final RoundedPanel card = new RoundedPanel(new BorderLayout(8, 0), ColorScheme.DARKER_GRAY_COLOR, 12);
      card.setBorder(new EmptyBorder(8, 8, 8, 8));

      final BufferedImage logo = ImageUtil.loadImageResource(RuneGPT.class, RuneGPT.LOGO);
      card.add(new JLabel(new ImageIcon(ImageUtil.resizeImage(logo, 30, 30))), BorderLayout.WEST);

      final JLabel title = new JLabel("RuneGPT AI Hub");
      title.setFont(FontManager.getRunescapeBoldFont());
      title.setForeground(ColorScheme.BRAND_ORANGE);
      status.setFont(FontManager.getRunescapeSmallFont());

      final JPanel text = new JPanel(new BorderLayout());
      text.setOpaque(false);
      text.add(title, BorderLayout.NORTH);
      text.add(status, BorderLayout.SOUTH);
      card.add(text, BorderLayout.CENTER);

      final JPanel wrap = new JPanel(new BorderLayout());
      wrap.setOpaque(false);
      wrap.setBorder(new EmptyBorder(0, 0, 6, 0));
      wrap.add(card, BorderLayout.CENTER);
      return wrap;
   }

   private void updateStatus() {
      final ProviderStatus s = gptClient.status();
      status.setText("● " + s.getText());
      switch (s.getState()) {
         case CONNECTED:
            status.setForeground(STATUS_OK);
            break;
         case ERROR:
            status.setForeground(STATUS_ERROR);
            break;
         default:
            status.setForeground(STATUS_READY);
      }
   }

   /** Re-read stats and quest progress, then rebuild the guide suggestions. */
   public void refreshGuides() {
      withSnapshot(snapshot -> SwingUtilities.invokeLater(() -> {
         guidesView.setSnapshot(snapshot);
         updateStatus();
      }));
   }

   /** Captures player data on the client thread, then runs {@code then} on the worker thread. */
   private void withSnapshot(final Consumer<PlayerSnapshot> then) {
      clientThread.invokeLater(() -> {
         PlayerSnapshot snapshot;
         try {
            snapshot = PlayerSnapshot.capture(client, itemManager, bankCache);
         } catch (final RuntimeException e) {
            log.warn("RuneGPT: couldn't read player data", e);
            snapshot = PlayerSnapshot.LOGGED_OUT;
         }
         final PlayerSnapshot s = snapshot;
         worker.execute(() -> then.accept(s));
      });
   }

   /** Show {@code display} in the chat and send {@code prompt} with fresh player context. */
   private void ask(final String display, final String prompt) {
      chatView.addUserMessage(display);
      chatView.showThinking("Thinking");
      withSnapshot(snapshot -> {
         final AssistantResponse response = assistant.ask(prompt, snapshot);
         SwingUtilities.invokeLater(() -> {
            showResponse(response, "");
            chatView.focusInput();
         });
      });
   }

   private void showResponse(final AssistantResponse response, final String providerSuffix) {
      if (!response.isSuccess()) {
         chatView.addErrorMessage(response.getAnswer());
      } else {
         chatView.addAssistantMessage(response.getProvider() + providerSuffix, response.getAnswer(),
            guideComponent(response.getGuide()));
      }
      updateStatus();
   }

   /** The guide checklist with a Track button, or null when the reply has nothing to track. */
   private JComponent guideComponent(final GuidePlan guide) {
      if (guide == null) {
         return null;
      }
      final JButton track = GuidesView.pillButton("Track guide");
      track.setToolTipText("Live checklist, in-game overlay, map marker and highlights");
      track.addActionListener(e -> startTracking(guide));
      return GuidePlanView.build(guide, true, track);
   }

   private void startTracking(final GuidePlan plan) {
      tracker.start(plan);
   }

   /** Quest step assistant: wiki-grounded "where am I and what's next" for one quest. */
   private void questStep(final String quest, final Map<String, String> picks, final String completedStep) {
      chatView.addUserMessage(completedStep == null ? "Help me with " + quest : "Done: " + completedStep);
      chatView.showThinking("Checking the OSRS Wiki");
      withSnapshot(snapshot -> {
         final QuestStep step = questAssistant.nextStep(quest, picks == null ? Collections.emptyMap() : picks,
            snapshot, completedStep);
         SwingUtilities.invokeLater(() -> {
            chatView.addCardMessage(QuestStepCard.header(step), QuestStepCard.build(step, actions));
            updateStatus();
         });
      });
   }

   /** "Requirements for quest": refuse made-up quests, otherwise answer from the wiki's requirement data. */
   private void questRequirements(final String quest, final String prompt) {
      chatView.addUserMessage("Requirements for " + quest);
      chatView.showThinking("Checking the OSRS Wiki");
      withSnapshot(snapshot -> {
         final QuestStep notAQuest = questAssistant.checkQuest(quest);
         if (notAQuest != null) {
            SwingUtilities.invokeLater(() ->
               chatView.addCardMessage(QuestStepCard.header(notAQuest), QuestStepCard.build(notAQuest, actions)));
            return;
         }
         final AssistantResponse response = assistant.ask(questAssistant.withWikiRequirements(quest, prompt), snapshot);
         SwingUtilities.invokeLater(() -> showResponse(response, "  ·  OSRS Wiki"));
      });
   }

   public void shutdown() {
      worker.shutdownNow();
   }
}
