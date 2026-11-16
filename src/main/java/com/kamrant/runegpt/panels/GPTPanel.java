package com.kamrant.runegpt.panels;

import com.kamrant.runegpt.RuneGPT;
import com.kamrant.runegpt.guides.GuideStore;
import com.kamrant.runegpt.handler.GPTClient;
import com.kamrant.runegpt.quest.QuestAssistant;
import com.kamrant.runegpt.quest.QuestHighlighter;
import com.kamrant.runegpt.quest.QuestStep;
import com.kamrant.runegpt.service.BankCache;
import com.kamrant.runegpt.service.PlayerSnapshot;
import com.kamrant.runegpt.ui.ChatView;
import com.kamrant.runegpt.ui.GuidesView;
import com.kamrant.runegpt.ui.QuestStepCard;
import com.kamrant.runegpt.ui.RoundedPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.Scrollable;
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
 * Sidebar "AI Hub": header with connection status, the chat on top and collapsible guide sections
 * below, separated by a draggable divider so the player can give either side more room.
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
   private final QuestAssistant questAssistant;
   private final QuestHighlighter highlighter;

   private final GuidesView guidesView;
   private final ChatView chatView;
   private final JLabel status = new JLabel();

   // LLM and wiki calls can take a while (and fail over across providers), so keep them off
   // RuneLite's shared executor.
   private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
      final Thread t = new Thread(r, "RuneGPT-worker");
      t.setDaemon(true);
      return t;
   });

   @Inject
   public GPTPanel(final Client client, final ClientThread clientThread, final ItemManager itemManager,
      final BankCache bankCache, final GPTClient gptClient, final GuideStore guideStore,
      final QuestAssistant questAssistant, final QuestHighlighter highlighter) {
      super(false);
      this.client = client;
      this.clientThread = clientThread;
      this.itemManager = itemManager;
      this.bankCache = bankCache;
      this.gptClient = gptClient;
      this.questAssistant = questAssistant;
      this.highlighter = highlighter;

      setLayout(new BorderLayout());
      setBackground(ColorScheme.DARK_GRAY_COLOR);
      setBorder(new EmptyBorder(6, 6, 6, 6));

      guidesView = new GuidesView(guideStore, this::ask, (quest, picks) -> questStep(quest, picks, null),
         this::questRequirements);
      chatView = new ChatView(text -> ask(text, text), gptClient::clearConversation);

      add(header(), BorderLayout.NORTH);

      // Sections scroll on their own, below the chat.
      final SectionsList sectionsList = new SectionsList();
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
      split.setToolTipText("Drag to resize the chat");
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
      final GPTClient.Status s = gptClient.status();
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
         final GPTClient.Reply reply = gptClient.ask(prompt, snapshot.toContext());
         SwingUtilities.invokeLater(() -> {
            if (reply.isSuccess()) {
               chatView.addAssistantMessage(reply.getProvider().getDisplayName(), reply.getText());
            } else {
               chatView.addErrorMessage(reply.getText());
            }
            updateStatus();
            chatView.focusInput();
         });
      });
   }

   /** Quest step assistant: wiki-grounded "where am I and what's next" for one quest. */
   private void questStep(final String quest, final Map<String, String> picks, final String completedStep) {
      chatView.addUserMessage(completedStep == null ? "Help me with " + quest : "Done: " + completedStep);
      chatView.showThinking("Checking the OSRS Wiki");
      withSnapshot(snapshot -> {
         final QuestStep step = questAssistant.nextStep(quest, picks == null ? Collections.emptyMap() : picks,
            snapshot, completedStep);
         SwingUtilities.invokeLater(() -> {
            chatView.addCardMessage(QuestStepCard.header(step), QuestStepCard.build(step, questActions));
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
               chatView.addCardMessage(QuestStepCard.header(notAQuest), QuestStepCard.build(notAQuest, questActions)));
            return;
         }
         final GPTClient.Reply reply = gptClient.ask(questAssistant.withWikiRequirements(quest, prompt), snapshot.toContext());
         SwingUtilities.invokeLater(() -> {
            if (reply.isSuccess()) {
               chatView.addAssistantMessage(reply.getProvider().getDisplayName() + "  ·  OSRS Wiki", reply.getText());
            } else {
               chatView.addErrorMessage(reply.getText());
            }
            updateStatus();
         });
      });
   }

   private final QuestStepCard.Actions questActions = new QuestStepCard.Actions() {
      @Override
      public void highlight(final String quest, final String npc) {
         highlighter.setTarget(quest, npc);
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

   public void shutdown() {
      worker.shutdownNow();
   }

   /** Tracks viewport width so sections never overflow sideways. */
   private static final class SectionsList extends JPanel implements Scrollable {
      @Override
      public Dimension getPreferredScrollableViewportSize() {
         return getPreferredSize();
      }

      @Override
      public int getScrollableUnitIncrement(final Rectangle r, final int o, final int d) {
         return 16;
      }

      @Override
      public int getScrollableBlockIncrement(final Rectangle r, final int o, final int d) {
         return r.height;
      }

      @Override
      public boolean getScrollableTracksViewportWidth() {
         return true;
      }

      @Override
      public boolean getScrollableTracksViewportHeight() {
         return false;
      }
   }
}
