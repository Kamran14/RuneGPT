package com.kamrant.runegpt.panels;

import com.kamrant.runegpt.guides.GuideStore;
import com.kamrant.runegpt.handler.GPTClient;
import com.kamrant.runegpt.service.PlayerSnapshot;
import com.kamrant.runegpt.ui.ChatView;
import com.kamrant.runegpt.ui.GuidesView;
import java.awt.BorderLayout;
import java.awt.Color;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.inject.Inject;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.materialtabs.MaterialTab;
import net.runelite.client.ui.components.materialtabs.MaterialTabGroup;

/** Sidebar panel: a Guides tab (categories and recommended guides) and a Chat tab. */
@Slf4j
public class GPTPanel extends PluginPanel {

   private final Client client;
   private final ClientThread clientThread;
   private final GPTClient gptClient;

   private final GuidesView guidesView;
   private final ChatView chatView;
   private final MaterialTabGroup tabs;
   private final MaterialTab chatTab;

   // LLM calls can take a while (and fail over across providers), so keep them off
   // RuneLite's shared executor.
   private final ExecutorService llmExecutor = Executors.newSingleThreadExecutor(r -> {
      final Thread t = new Thread(r, "RuneGPT-LLM");
      t.setDaemon(true);
      return t;
   });

   @Inject
   public GPTPanel(final Client client, final ClientThread clientThread, final GPTClient gptClient,
      final GuideStore guideStore) {
      super(false);
      this.client = client;
      this.clientThread = clientThread;
      this.gptClient = gptClient;

      setLayout(new BorderLayout());
      setBackground(ColorScheme.DARK_GRAY_COLOR);

      guidesView = new GuidesView(guideStore, this::ask, this::refreshGuides);
      chatView = new ChatView(text -> ask(text, text), gptClient::clearConversation);

      final JLabel title = new JLabel("RuneGPT");
      title.setFont(FontManager.getRunescapeBoldFont());
      title.setForeground(Color.WHITE);
      final JLabel subtitle = new JLabel("Your OSRS assistant");
      subtitle.setFont(FontManager.getRunescapeSmallFont());
      subtitle.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
      final JPanel header = new JPanel(new BorderLayout());
      header.setBackground(ColorScheme.DARK_GRAY_COLOR);
      header.setBorder(new EmptyBorder(8, 8, 4, 8));
      header.add(title, BorderLayout.NORTH);
      header.add(subtitle, BorderLayout.SOUTH);

      final JPanel display = new JPanel(new BorderLayout());
      display.setBackground(ColorScheme.DARK_GRAY_COLOR);
      tabs = new MaterialTabGroup(display);
      tabs.setBorder(new EmptyBorder(4, 0, 4, 0));
      final MaterialTab guidesTab = new MaterialTab("Guides", tabs, guidesView);
      chatTab = new MaterialTab("Chat", tabs, chatView);
      tabs.addTab(guidesTab);
      tabs.addTab(chatTab);
      tabs.select(guidesTab);

      final JPanel north = new JPanel(new BorderLayout());
      north.setBackground(ColorScheme.DARK_GRAY_COLOR);
      north.add(header, BorderLayout.NORTH);
      north.add(tabs, BorderLayout.SOUTH);

      add(north, BorderLayout.NORTH);
      add(display, BorderLayout.CENTER);
   }

   /** Re-read stats and quest progress, then rebuild the guide suggestions. */
   public void refreshGuides() {
      clientThread.invokeLater(() -> {
         final PlayerSnapshot snapshot = PlayerSnapshot.capture(client);
         SwingUtilities.invokeLater(() -> guidesView.setSnapshot(snapshot));
      });
   }

   /** Show {@code display} in the chat and send {@code prompt} with fresh player context. */
   private void ask(final String display, final String prompt) {
      tabs.select(chatTab);
      chatView.addUserMessage(display);
      chatView.showThinking();

      clientThread.invokeLater(() -> {
         final PlayerSnapshot snapshot = PlayerSnapshot.capture(client);
         llmExecutor.execute(() -> {
            final GPTClient.Reply reply = gptClient.ask(prompt, snapshot.toContext());
            SwingUtilities.invokeLater(() -> {
               if (reply.isSuccess()) {
                  chatView.addAssistantMessage(reply.getProvider().getDisplayName(), reply.getText());
               } else {
                  chatView.addErrorMessage(reply.getText());
               }
               chatView.focusInput();
            });
         });
      });
   }

   public void shutdown() {
      llmExecutor.shutdownNow();
   }
}
