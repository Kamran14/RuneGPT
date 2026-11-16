package com.kamrant.runegpt.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.function.Consumer;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

/** Conversation view: message bubbles with rendered Markdown, plus the input box. */
public class ChatView extends JPanel {

   private static final int ROW_PAD = 6;
   private static final int BUBBLE_PAD = 8;
   private static final int USER_INDENT = 20;
   // Width available for text inside an assistant bubble.
   private static final int TEXT_WIDTH = PluginPanel.PANEL_WIDTH - 2 * ROW_PAD - 2 * BUBBLE_PAD - 4;

   private static final Color USER_BG = new Color(74, 54, 22);
   private static final Color ASSISTANT_BG = ColorScheme.DARKER_GRAY_COLOR;
   private static final Color ERROR_BG = new Color(84, 32, 32);
   private static final Color TEXT = new Color(220, 220, 220);

   private final MessageList list = new MessageList();
   // Rows stack at the top of the list; BorderLayout.NORTH stops BoxLayout stretching them.
   private final JPanel messages = new JPanel();
   private final JScrollPane scroll;
   private final JTextArea input = new JTextArea(3, 10);
   private final JButton send = new JButton("Send");
   private final JButton newChat = new JButton("New chat");
   private final JComponent emptyState;

   private JComponent thinkingRow;
   private Timer thinkingTimer;

   public ChatView(final Consumer<String> onSend, final Runnable onNewChat) {
      setLayout(new BorderLayout());
      setBackground(ColorScheme.DARK_GRAY_COLOR);

      // Header
      final JPanel header = new JPanel(new BorderLayout());
      header.setBackground(ColorScheme.DARK_GRAY_COLOR);
      header.setBorder(new EmptyBorder(6, ROW_PAD, 6, ROW_PAD));
      final JLabel title = new JLabel("Conversation");
      title.setFont(FontManager.getRunescapeBoldFont());
      title.setForeground(Color.WHITE);
      header.add(title, BorderLayout.WEST);
      newChat.setFont(FontManager.getRunescapeSmallFont());
      newChat.setFocusable(false);
      newChat.setToolTipText("Clear the conversation and start over");
      newChat.addActionListener(e -> {
         onNewChat.run();
         clear();
      });
      header.add(newChat, BorderLayout.EAST);
      add(header, BorderLayout.NORTH);

      // Messages
      messages.setLayout(new BoxLayout(messages, BoxLayout.Y_AXIS));
      messages.setOpaque(false);
      list.setLayout(new BorderLayout());
      list.setBackground(ColorScheme.DARK_GRAY_COLOR);
      list.add(messages, BorderLayout.NORTH);
      emptyState = emptyState();
      messages.add(emptyState);
      scroll = new JScrollPane(list, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
         JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
      scroll.setBorder(null);
      scroll.getVerticalScrollBar().setUnitIncrement(16);
      scroll.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
      add(scroll, BorderLayout.CENTER);

      // Input
      input.setLineWrap(true);
      input.setWrapStyleWord(true);
      input.setBorder(new EmptyBorder(6, 6, 6, 6));
      input.setBackground(ColorScheme.DARKER_GRAY_COLOR);
      input.setForeground(TEXT);
      input.setCaretColor(TEXT);
      // Enter sends, Shift+Enter adds a new line.
      input.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "send");
      input.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, KeyEvent.SHIFT_DOWN_MASK), "insert-break");
      final Runnable submit = () -> {
         final String text = input.getText().trim();
         if (!text.isEmpty() && send.isEnabled()) {
            input.setText("");
            onSend.accept(text);
         }
      };
      input.getActionMap().put("send", new AbstractAction() {
         @Override
         public void actionPerformed(final ActionEvent e) {
            submit.run();
         }
      });
      send.setFocusable(false);
      send.addActionListener(e -> submit.run());

      final JLabel inputHint = new JLabel("Shift+Enter: new line");
      inputHint.setFont(FontManager.getRunescapeSmallFont());
      inputHint.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);

      final JScrollPane inputScroll = new JScrollPane(input, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
         JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
      inputScroll.setBorder(BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR));
      input.setToolTipText("Ask anything about OSRS. Enter sends, Shift+Enter adds a new line.");

      final JPanel actionRow = new JPanel(new BorderLayout(6, 0));
      actionRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
      actionRow.add(inputHint, BorderLayout.CENTER);
      actionRow.add(send, BorderLayout.EAST);

      final JLabel inputTitle = new JLabel("Ask anything about OSRS");
      inputTitle.setFont(FontManager.getRunescapeSmallFont());
      inputTitle.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

      final JPanel south = new JPanel(new BorderLayout(0, 4));
      south.setBackground(ColorScheme.DARK_GRAY_COLOR);
      south.setBorder(new EmptyBorder(6, ROW_PAD, 8, ROW_PAD));
      south.add(inputTitle, BorderLayout.NORTH);
      south.add(inputScroll, BorderLayout.CENTER);
      south.add(actionRow, BorderLayout.SOUTH);
      add(south, BorderLayout.SOUTH);
   }

   public void focusInput() {
      input.requestFocusInWindow();
   }

   public void setInputText(final String text) {
      input.setText(text);
   }

   public void addUserMessage(final String text) {
      removeEmptyState();
      final JLabel body = new JLabel("<html><body style='width:" + (TEXT_WIDTH - USER_INDENT) + "px'>"
         + MarkdownRenderer.escape(text).replace("\n", "<br>") + "</body></html>");
      body.setForeground(TEXT);
      body.setFont(FontManager.getRunescapeFont());
      addRow(bubble(null, body, USER_BG), USER_INDENT, 0);
   }

   public void addAssistantMessage(final String providerName, final String markdown) {
      removeThinking();
      final HtmlPane pane = new HtmlPane(TEXT_WIDTH, TEXT);
      pane.setHtml(MarkdownRenderer.toHtml(markdown));
      addRow(bubble("RuneGPT  ·  " + providerName, pane, ASSISTANT_BG), 0, 0);
   }

   public void addErrorMessage(final String text) {
      removeThinking();
      final HtmlPane pane = new HtmlPane(TEXT_WIDTH, TEXT);
      pane.setHtml(MarkdownRenderer.escape(text).replace("\n", "<br>"));
      addRow(bubble("Couldn't get an answer", pane, ERROR_BG), 0, 0);
   }

   public void showThinking() {
      removeEmptyState();
      final JLabel dots = new JLabel("Thinking");
      dots.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
      dots.setFont(FontManager.getRunescapeFont());
      thinkingRow = addRow(bubble("RuneGPT", dots, ASSISTANT_BG), 0, 0);
      final int[] tick = { 0 };
      thinkingTimer = new Timer(400, e -> {
         tick[0] = (tick[0] + 1) % 4;
         dots.setText("Thinking" + "...".substring(0, tick[0]));
      });
      thinkingTimer.start();
      setBusy(true);
   }

   public void setBusy(final boolean busy) {
      send.setEnabled(!busy);
      newChat.setEnabled(!busy);
   }

   public void clear() {
      removeThinking();
      messages.removeAll();
      messages.add(emptyState);
      messages.revalidate();
      messages.repaint();
   }

   private void removeThinking() {
      if (thinkingTimer != null) {
         thinkingTimer.stop();
         thinkingTimer = null;
      }
      if (thinkingRow != null) {
         messages.remove(thinkingRow);
         thinkingRow = null;
      }
      setBusy(false);
   }

   private void removeEmptyState() {
      messages.remove(emptyState);
   }

   private JComponent bubble(final String header, final JComponent body, final Color bg) {
      final JPanel b = new JPanel(new BorderLayout(0, 4));
      b.setBackground(bg);
      b.setBorder(new EmptyBorder(BUBBLE_PAD - 2, BUBBLE_PAD, BUBBLE_PAD, BUBBLE_PAD));
      if (header != null) {
         final JLabel h = new JLabel(header);
         h.setFont(FontManager.getRunescapeSmallFont());
         h.setForeground(ColorScheme.BRAND_ORANGE);
         b.add(h, BorderLayout.NORTH);
      }
      b.add(body, BorderLayout.CENTER);
      return b;
   }

   private JComponent addRow(final JComponent bubble, final int leftIndent, final int rightIndent) {
      final JPanel row = new JPanel(new BorderLayout());
      row.setOpaque(false);
      row.setBorder(new EmptyBorder(ROW_PAD, ROW_PAD + leftIndent, 0, ROW_PAD + rightIndent));
      row.add(bubble, BorderLayout.CENTER);
      row.setAlignmentX(Component.LEFT_ALIGNMENT);
      messages.add(row);
      messages.revalidate();
      messages.repaint();
      scrollToBottom();
      return row;
   }

   private void scrollToBottom() {
      // Run after layout so the new row's height is known.
      SwingUtilities.invokeLater(() -> {
         list.scrollRectToVisible(new Rectangle(0, list.getPreferredSize().height - 1, 1, 1));
      });
   }

   private JComponent emptyState() {
      final JPanel p = new JPanel();
      p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
      p.setOpaque(false);
      p.setBorder(new EmptyBorder(40, 16, 0, 16));
      p.setAlignmentX(Component.LEFT_ALIGNMENT);
      final JLabel title = new JLabel("No questions yet");
      title.setFont(FontManager.getRunescapeBoldFont());
      title.setForeground(Color.WHITE);
      title.setAlignmentX(Component.CENTER_ALIGNMENT);
      final JLabel body = new JLabel("<html><div style='text-align:center; width:160px'>Pick a guide from the "
         + "Guides tab, or type a question below. Your stats and quest progress are included automatically."
         + "</div></html>");
      body.setFont(FontManager.getRunescapeSmallFont());
      body.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
      body.setHorizontalAlignment(SwingConstants.CENTER);
      body.setAlignmentX(Component.CENTER_ALIGNMENT);
      p.add(title);
      p.add(Box.createVerticalStrut(6));
      p.add(body);
      return p;
   }

   /** Tracks the viewport width so rows never overflow horizontally. */
   private static final class MessageList extends JPanel implements Scrollable {
      @Override
      public Dimension getPreferredScrollableViewportSize() {
         return getPreferredSize();
      }

      @Override
      public int getScrollableUnitIncrement(final Rectangle visibleRect, final int orientation, final int direction) {
         return 16;
      }

      @Override
      public int getScrollableBlockIncrement(final Rectangle visibleRect, final int orientation, final int direction) {
         return visibleRect.height;
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
