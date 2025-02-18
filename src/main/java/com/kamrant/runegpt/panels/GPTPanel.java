package com.kamrant.runegpt.panels;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import com.google.inject.Inject;
import com.kamrant.runegpt.handler.GPTClient;
import com.kamrant.runegpt.service.PlayerStatsService;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.PluginPanel;

@Slf4j
public class GPTPanel extends PluginPanel {
   private final JTextArea panelArea = new JTextArea();
   private final JTextField queryField = new JTextField();
   private final JButton submitBtn = new JButton("Submit");
   
   @Inject
   private PlayerStatsService playerStatsService;
   
   private final GPTClient gptClient;

   @Inject
   public GPTPanel(final GPTClient gptClient) {
      this.gptClient = gptClient;

      setLayout(new BorderLayout());
      setPreferredSize(new Dimension(300, 400));

      JLabel label = new JLabel("Ask the GPT about OSRS:");
      label.setHorizontalAlignment(SwingConstants.CENTER);
      label.setFont(new Font("Arial", Font.BOLD, 14));

      queryField.setPreferredSize(new Dimension(250, 30));

      submitBtn.addActionListener(this::onSubmit);

      panelArea.setEditable(false);
      panelArea.setLineWrap(true);
      panelArea.setWrapStyleWord(true);
      JScrollPane scrollPane = new JScrollPane(panelArea);
      scrollPane.setPreferredSize(new Dimension(280, 250));

      JPanel inputPanel = new JPanel(new BorderLayout(5, 5));
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
         return;
      }

      panelArea.setText("Querying Gemini...");

      new Thread(() -> {
         // Fetch player stats
         final String playerStats = playerStatsService.getPlayerStatsAsString();
         
         // Include player stats in the query
         final String response = gptClient.queryGPT(query + "\nPlayer Stats: " + playerStats);
         SwingUtilities.invokeLater(() -> panelArea.setText(response));
      }).start();
   }
}
