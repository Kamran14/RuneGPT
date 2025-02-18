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
import com.google.inject.Inject;
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
   
   private Client client;
   private PlayerStats playerStats;
   private GPTClient gptClient;
 
   @Inject
   public GPTPanel(final Client client, final GPTClient gptClient) {
      this.client = client;
      this.gptClient = gptClient;
      this.playerStats = new PlayerStats(client);
    
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

      panelArea.setText("Querying Gemini...");

      new Thread(() -> {
         // Fetch player stats
         final String statsConfig = playerStats.getPlayerStatsAsString();
         
         // Include player stats in the query
         final String response = gptClient.queryGPT(query + "\nPlayer Stats: " + statsConfig);
         SwingUtilities.invokeLater(() -> panelArea.setText(response));
      }).start();
   }
}
