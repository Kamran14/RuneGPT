package com.kamrant.runegpt.ui;

import com.kamrant.runegpt.guides.GuideStore;
import com.kamrant.runegpt.llm.GPTClient;
import com.kamrant.runegpt.model.AssistantResponse;
import com.kamrant.runegpt.model.GearResult;
import com.kamrant.runegpt.model.GuidePlan;
import com.kamrant.runegpt.model.QuestStep;
import com.kamrant.runegpt.model.QuestStepKind;
import com.kamrant.runegpt.model.TaskSuggestion;
import com.kamrant.runegpt.quest.QuestAssistant;
import com.kamrant.runegpt.quest.QuestJournalCache;
import com.kamrant.runegpt.service.AssistantService;
import com.kamrant.runegpt.service.BankCache;
import com.kamrant.runegpt.service.GearOptimizer;
import com.kamrant.runegpt.service.GuideTracker;
import com.kamrant.runegpt.service.NearestTargetResolver;
import com.kamrant.runegpt.service.PlayerSnapshot;
import com.kamrant.runegpt.service.TaskAssistant;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
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
import net.runelite.client.ui.FontManager;
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
   private final QuestJournalCache journalCache;
   private final AssistantService assistant;
   private final QuestAssistant questAssistant;
   private final TaskAssistant taskAssistant;
   private final GearOptimizer gearOptimizer;
   private final GuideTracker tracker;
   private final GPTClient gptClient;

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
      public void nextStep(final String questOrTask, final String completedStep) {
         startTask(questOrTask, "", null, completedStep);
      }

      @Override
      public void useQuest(final String quest) {
         startTask(quest, "quest", null, null);
      }
   };

   @Inject
   public GPTPanel(final Client client, final ClientThread clientThread, final ItemManager itemManager,
      final BankCache bankCache, final QuestJournalCache journalCache, final GPTClient gptClient,
      final AssistantService assistant, final GuideStore guideStore, final QuestAssistant questAssistant,
      final TaskAssistant taskAssistant, final GearOptimizer gearOptimizer, final GuideTracker tracker) {
      super(false);
      this.client = client;
      this.clientThread = clientThread;
      this.itemManager = itemManager;
      this.bankCache = bankCache;
      this.journalCache = journalCache;
      this.assistant = assistant;
      this.questAssistant = questAssistant;
      this.taskAssistant = taskAssistant;
      this.gearOptimizer = gearOptimizer;
      this.tracker = tracker;
      this.gptClient = gptClient;

      setLayout(new BorderLayout());
      setBackground(ColorScheme.DARK_GRAY_COLOR);
      setBorder(new EmptyBorder(6, 6, 6, 6));

      guidesView = new GuidesView(guideStore, this::ask,
         (task, picks) -> startTask(task, "", picks, null),
         this::questRequirements, this::gearOptimize);
      chatView = new ChatView(text -> ask(text, text), gptClient::clearConversation);
      trackedView = new TrackedGuideView(tracker::stop, tracker::setStep);
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

   /**
    * Checks the saved API keys can be decrypted (and that at least one provider is usable) as soon as
    * RuneGPT opens, and tells the player in the chat if not, instead of failing on their first question.
    */
   public void checkKeys() {
      try {
         worker.execute(() -> {
            final String notice = gptClient.keyNotice();
            if (notice != null) {
               SwingUtilities.invokeLater(() -> chatView.addNotice(notice));
            }
         });
      } catch (final RejectedExecutionException e) {
         log.debug("RuneGPT: worker stopped, skipping key check");
      }
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
            snapshot = PlayerSnapshot.capture(client, itemManager, bankCache, journalCache);
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
      chatView.showThinking(UiTheme.LBL_THINKING);
      withSnapshot(snapshot -> {
         final AssistantResponse response = assistant.ask(prompt, snapshot);
         if (response.isSuccess() && response.getGuide() != null
            && NearestTargetResolver.isNavigationRequest(prompt)) {
            tracker.start(response.getGuide());
         }
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
         chatView.addAssistantMessage(response.getAnswer(),
            extraComponent(response.getGuide(), response.getSuggestions()));
      }
   }

   /** Combines the verified guide checklist and any "Start task" suggestion buttons under the reply. */
   private JComponent extraComponent(final GuidePlan guide, final List<TaskSuggestion> suggestions) {
      final JComponent guideComp = guideComponent(guide);
      final JComponent sugComp = suggestionsComponent(suggestions);
      if (guideComp == null) {
         return sugComp;
      }
      if (sugComp == null) {
         return guideComp;
      }
      final JPanel both = CardWidgets.column();
      CardWidgets.addLeft(both, guideComp);
      CardWidgets.addLeft(both, Box.createVerticalStrut(6));
      CardWidgets.addLeft(both, sugComp);
      return both;
   }

   /** The guide checklist with a Track button, or null when the reply has nothing to track. */
   private JComponent guideComponent(final GuidePlan guide) {
      if (guide == null) {
         return null;
      }
      final JButton track = CardWidgets.pillButton(UiTheme.BTN_TRACK_GUIDE);
      track.setToolTipText(UiTheme.TIP_TRACK_GUIDE);
      track.addActionListener(e -> tracker.start(guide));
      return GuidePlanView.build(guide, true, track);
   }

   /** Renders a "Start task" button for every suggested quest, minigame, money maker, boss or skill method. */
   private JComponent suggestionsComponent(final List<TaskSuggestion> suggestions) {
      if (suggestions == null || suggestions.isEmpty()) {
         return null;
      }
      final JPanel list = CardWidgets.column();
      CardWidgets.addLeft(list, CardWidgets.label("Start a guided task"));
      CardWidgets.addLeft(list, Box.createVerticalStrut(3));

      for (int i = 0; i < suggestions.size(); i++) {
         final TaskSuggestion s = suggestions.get(i);
         final RoundedPanel row = new RoundedPanel(new BorderLayout(6, 2), UiTheme.SUBCARD_BG, 8);
         row.setBorder(new EmptyBorder(5, 6, 5, 6));

         final HtmlPane text = new HtmlPane(UiTheme.TEXT_PRIMARY);
         final String html = "<b>" + MarkdownRenderer.escape(s.getTitle()) + "</b>"
            + (s.getSummary().isEmpty() ? "" : "<br><span class=\"muted\">"
               + MarkdownRenderer.escape(s.getSummary()) + "</span>");
         text.setHtml(html);

         final JButton start = CardWidgets.pillButton(UiTheme.BTN_START_TASK);
         start.setToolTipText("Guide me through " + s.getTitle() + " step by step");
         start.addActionListener(e -> startTask(s.getTitle(), s.getCategory(), Collections.emptyMap(), null));

         final JPanel btnWrap = new JPanel(new BorderLayout());
         btnWrap.setOpaque(false);
         btnWrap.add(start, BorderLayout.NORTH);

         row.add(text, BorderLayout.CENTER);
         row.add(btnWrap, BorderLayout.EAST);
         CardWidgets.addLeft(list, row);
         if (i + 1 < suggestions.size()) {
            CardWidgets.addLeft(list, Box.createVerticalStrut(3));
         }
      }
      return list;
   }

   /**
    * Interactive step-by-step assistant for any quest or activity (minigame, money maker, skilling method,
    * boss or monster task), grounded in the OSRS Wiki.
    */
   private void startTask(final String task, final String category, final Map<String, String> picks,
      final String completedStep) {
      chatView.addUserMessage(completedStep == null ? "Start task: " + task : "Done: " + completedStep);
      chatView.showThinking(UiTheme.LBL_CHECKING_WIKI);
      withSnapshot(snapshot -> {
         final QuestStep step = taskAssistant.nextStep(task, category,
            picks == null ? Collections.emptyMap() : picks, snapshot, completedStep);
         if (step.getKind() == QuestStepKind.STEP) {
            tracker.start(step.toGuidePlan());
         }
         SwingUtilities.invokeLater(() ->
            chatView.addCardMessage(QuestStepCard.header(step), QuestStepCard.build(step, actions)));
      });
   }

   /** Gear optimizer: picks the highest-DPS setup the player owns against one monster or boss. */
   private void gearOptimize(final String target, final Map<String, String> picks) {
      chatView.addUserMessage("Best gear I own for " + target);
      chatView.showThinking(UiTheme.LBL_CHECKING_WIKI);
      withSnapshot(snapshot -> {
         final GearResult result = gearOptimizer.optimize(target, picks, snapshot);
         SwingUtilities.invokeLater(() -> chatView.addCardMessage(GearOptimizerCard.header(result),
            GearOptimizerCard.build(result, snapshot.holdings(),
               (setup, h) -> gearOptimizer.toGuidePlan(result, setup, h), tracker::start)));
      });
   }

   /** "Requirements for quest": refuse made-up quests, otherwise answer from the wiki's requirement data. */
   private void questRequirements(final String quest, final String prompt) {
      chatView.addUserMessage("Requirements for " + quest);
      chatView.showThinking(UiTheme.LBL_CHECKING_WIKI);
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
