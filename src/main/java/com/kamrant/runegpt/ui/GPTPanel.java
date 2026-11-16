package com.kamrant.runegpt.ui;

import com.kamrant.runegpt.guides.GuideStore;
import com.kamrant.runegpt.llm.GPTClient;
import com.kamrant.runegpt.model.AssistantResponse;
import com.kamrant.runegpt.model.GuidePlan;
import com.kamrant.runegpt.model.QuestStep;
import com.kamrant.runegpt.quest.QuestAssistant;
import com.kamrant.runegpt.service.AssistantService;
import com.kamrant.runegpt.service.BankCache;
import com.kamrant.runegpt.service.GuideTracker;
import com.kamrant.runegpt.service.PlayerSnapshot;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
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
import net.runelite.client.ui.PluginPanel;

/**
 * Sidebar panel: the tracked guide (when one is active), then the chat and the collapsible guide
 * sections separated by a draggable divider. A new panel is created on every plugin start.
 */
@Slf4j
public class GPTPanel extends PluginPanel {

   private final Client client;
   private final ClientThread clientThread;
   private final ItemManager itemManager;
   private final BankCache bankCache;
   private final AssistantService assistant;
   private final QuestAssistant questAssistant;
   private final GuideTracker tracker;

   private final GuidesView guidesView;
   private final ChatView chatView;
   private final TrackedGuideView trackedView;

   // LLM and wiki calls can take a while (and fail over across providers), so keep them off
   // RuneLite's shared executor. Shut down in shutdown(), called from the plugin's shutDown().
   private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
      final Thread t = new Thread(r, "RuneGPT-worker");
      t.setDaemon(true);
      return t;
   });

   private final QuestStepActions actions = new QuestStepActions() {
      @Override
      public void track(final GuidePlan plan) {
         tracker.start(plan);
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
      tracker.setListener(plan -> SwingUtilities.invokeLater(() -> trackedView.setPlan(plan)));

      final JPanel trackedWrap = new JPanel(new BorderLayout());
      trackedWrap.setOpaque(false);
      trackedWrap.setBorder(new EmptyBorder(0, 0, 6, 0));
      trackedWrap.add(trackedView, BorderLayout.CENTER);
      add(trackedWrap, BorderLayout.NORTH);

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
   }

   /** Re-read stats and quest progress, then rebuild the guide suggestions. */
   public void refreshGuides() {
      withSnapshot(snapshot -> SwingUtilities.invokeLater(() -> guidesView.setSnapshot(snapshot)));
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
         try {
            worker.execute(() -> then.accept(s));
         } catch (final RejectedExecutionException e) {
            // Plugin is shutting down.
            log.debug("RuneGPT: worker stopped, dropping request");
         }
      });
   }

   /** Show {@code display} in the chat and send {@code prompt} with fresh player context. */
   private void ask(final String display, final String prompt) {
      chatView.addUserMessage(display);
      chatView.showThinking("Thinking");
      withSnapshot(snapshot -> {
         final AssistantResponse response = assistant.ask(prompt, snapshot);
         SwingUtilities.invokeLater(() -> {
            showResponse(response);
            chatView.focusInput();
         });
      });
   }

   private void showResponse(final AssistantResponse response) {
      if (!response.isSuccess()) {
         chatView.addErrorMessage(response.getAnswer());
      } else {
         chatView.addAssistantMessage(response.getAnswer(), guideComponent(response.getGuide()));
      }
   }

   /** The guide checklist with a Track button, or null when the reply has nothing to track. */
   private JComponent guideComponent(final GuidePlan guide) {
      if (guide == null) {
         return null;
      }
      final JButton track = GuidesView.pillButton("Track guide");
      track.setToolTipText("Live checklist, in-game overlay, map marker and highlights");
      track.addActionListener(e -> tracker.start(guide));
      return GuidePlanView.build(guide, true, track);
   }

   /** Quest step assistant: wiki-grounded "where am I and what's next" for one quest. */
   private void questStep(final String quest, final Map<String, String> picks, final String completedStep) {
      chatView.addUserMessage(completedStep == null ? "Help me with " + quest : "Done: " + completedStep);
      chatView.showThinking("Checking the OSRS Wiki");
      withSnapshot(snapshot -> {
         final QuestStep step = questAssistant.nextStep(quest, picks == null ? Collections.emptyMap() : picks,
            snapshot, completedStep);
         SwingUtilities.invokeLater(() ->
            chatView.addCardMessage(QuestStepCard.header(step), QuestStepCard.build(step, actions)));
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
         SwingUtilities.invokeLater(() -> showResponse(response));
      });
   }

   /** Stops the worker thread and UI timers. Called from the plugin's shutDown(). */
   public void shutdown() {
      worker.shutdownNow();
      tracker.setListener(null);
      SwingUtilities.invokeLater(chatView::shutdown);
   }
}
