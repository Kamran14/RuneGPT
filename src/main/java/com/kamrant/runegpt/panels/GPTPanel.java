package com.kamrant.runegpt.panels;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import com.kamrant.runegpt.handler.GPTClient;
import com.kamrant.runegpt.service.PlayerStats;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.ui.PluginPanel;

@Slf4j
public class GPTPanel extends PluginPanel {
   private final JTextArea panelArea = new JTextArea();
   private final JTextArea queryField = new JTextArea();
   private final JButton submitBtn = new JButton("Submit");
   
   private PlayerStats playerStats;
   private GPTClient gptClient;
   private ScheduledExecutorService executorService;

   @Inject
   public GPTPanel(final Client client, final GPTClient gptClient, final ScheduledExecutorService executorService) { 
      this.gptClient = gptClient;
      this.playerStats = new PlayerStats(client);
      this.executorService = executorService;
      setLayout(new BorderLayout());
      setPreferredSize(new Dimension(300, 400));

      final JLabel label = new JLabel("Ask Gemini about OSRS:");
      label.setHorizontalAlignment(SwingConstants.CENTER);
      label.setFont(new Font("Arial", Font.BOLD, 14));

      queryField.setPreferredSize(new Dimension(250, 30));
      queryField.setLineWrap(true);
      queryField.setWrapStyleWord(true);

      submitBtn.addActionListener(this::onSubmit);

      panelArea.setEditable(false);
      panelArea.setLineWrap(true);
      panelArea.setWrapStyleWord(true);
      final JScrollPane scrollPane = new JScrollPane(panelArea);
      scrollPane.setPreferredSize(new Dimension(280, 250));

      final JPanel inputPanel = new JPanel(new BorderLayout(5, 5));
      inputPanel.add(label, BorderLayout.NORTH);
      inputPanel.add(queryField, BorderLayout.CENTER);
      inputPanel.add(submitBtn, BorderLayout.EAST);

      add(inputPanel, BorderLayout.NORTH);
      add(scrollPane, BorderLayout.CENTER);
   }

   private void onSubmit(final ActionEvent e) {
      final String query = queryField.getText().trim();
      if (query.isEmpty()) {
         panelArea.setText("Please enter a query.");
         playerStats.getPlayerStatsAsString();
         return;
      }

      final String statsConfig = playerStats.getPlayerStatsAsString();
      panelArea.setText("Querying Gemini...");
      

      executorService.execute(() -> {
         final String response = gptClient.queryGPT(query + "\nPlayer Stats: " + statsConfig);
         SwingUtilities.invokeLater(() -> panelArea.setText(response));
      });
   }
}
