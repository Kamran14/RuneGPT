package com.kamrant.runegpt.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.util.function.Consumer;
import javax.swing.AbstractAction;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * Conversation view: message bubbles (rendered Markdown that wraps to the real panel width)
 * above a compact input that grows as the player types.
 */
public class ChatView extends JPanel {

   private static final int ROW_PAD = 6;
   private static final int USER_INDENT = 18;
   private static final int INPUT_MIN_ROWS = 2;
   private static final int INPUT_MAX_ROWS = 5;

   private static final Color USER_BG = new Color(74, 54, 22);
   private static final Color ASSISTANT_BG = ColorScheme.DARKER_GRAY_COLOR;
   private static final Color ERROR_BG = new Color(84, 32, 32);
   private static final Color NOTICE_BG = new Color(44, 40, 28);
   /** How long after a message is added its resizes still re-scroll the view. */
   private static final long RESCROLL_WINDOW_MS = 3000;
   static final Color TEXT = new Color(220, 220, 220);

   private final ViewportWidthPanel list = new ViewportWidthPanel();
   // Rows stack at the top of the list; BorderLayout.NORTH stops BoxLayout stretching them.
   private final JPanel messages = new JPanel();
   private final JScrollPane scroll;
   private final PlaceholderTextArea input = new PlaceholderTextArea("Ask about OSRS...");
   private final JScrollPane inputScroll;
   private final JButton send = GuidesView.pillButton("Send");
   private final JButton newChat = GuidesView.pillButton("New chat");
   private final JComponent emptyState;

   private JComponent newestRow;
   private long newestAddedAt;
   private JComponent thinkingRow;
   private Timer thinkingTimer;

   public ChatView(final Consumer<String> onSend, final Runnable onNewChat) {
      setLayout(new BorderLayout());
      setOpaque(false);

      // Header
      final JLabel title = new JLabel("Chat");
      title.setFont(FontManager.getRunescapeBoldFont());
      title.setForeground(Color.WHITE);
      newChat.setToolTipText("Clear the conversation and start over");
      newChat.addActionListener(e -> {
         onNewChat.run();
         clear();
      });
      final JPanel header = new JPanel(new BorderLayout());
      header.setOpaque(false);
      header.setBorder(new EmptyBorder(0, 2, 4, 0));
      header.add(title, BorderLayout.WEST);
      header.add(newChat, BorderLayout.EAST);
      add(header, BorderLayout.NORTH);

      // Messages
      messages.setLayout(new BoxLayout(messages, BoxLayout.Y_AXIS));
      messages.setOpaque(false);
      list.setLayout(new BorderLayout());
      list.setBackground(ColorScheme.DARKER_GRAY_COLOR.darker());
      list.add(messages, BorderLayout.NORTH);
      emptyState = emptyState();
      messages.add(emptyState);
      scroll = new JScrollPane(list, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
      scroll.setBorder(null);
      scroll.getVerticalScrollBar().setUnitIncrement(16);
      scroll.getViewport().setBackground(list.getBackground());
      scroll.setMinimumSize(new Dimension(0, 60));
      final RoundedPanel messagesCard = new RoundedPanel(new BorderLayout(), list.getBackground(), 12);
      messagesCard.setBorder(new EmptyBorder(4, 2, 4, 2));
      messagesCard.add(scroll, BorderLayout.CENTER);
      add(messagesCard, BorderLayout.CENTER);

      // Input: starts at 2 rows, grows up to 5 as the player types.
      input.setRows(INPUT_MIN_ROWS);
      input.setLineWrap(true);
      input.setWrapStyleWord(true);
      input.setFont(FontManager.getRunescapeFont());
      input.setBorder(new EmptyBorder(4, 6, 4, 6));
      input.setBackground(ColorScheme.DARKER_GRAY_COLOR);
      input.setForeground(TEXT);
      input.setCaretColor(TEXT);
      input.setToolTipText("Enter sends, Shift+Enter adds a new line");
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
      input.getDocument().addDocumentListener(new DocumentListener() {
         @Override
         public void insertUpdate(final DocumentEvent e) {
            resizeInput();
         }

         @Override
         public void removeUpdate(final DocumentEvent e) {
            resizeInput();
         }

         @Override
         public void changedUpdate(final DocumentEvent e) {
            resizeInput();
         }
      });
      send.addActionListener(e -> submit.run());

      inputScroll = new JScrollPane(input, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
      inputScroll.setBorder(null);
      final RoundedPanel inputCard = new RoundedPanel(new BorderLayout(4, 0), ColorScheme.DARKER_GRAY_COLOR, 12);
      inputCard.setOutline(ColorScheme.MEDIUM_GRAY_COLOR.darker());
      inputCard.setBorder(new EmptyBorder(3, 2, 3, 4));
      inputCard.add(inputScroll, BorderLayout.CENTER);
      final JPanel sendWrap = new JPanel(new BorderLayout());
      sendWrap.setOpaque(false);
      sendWrap.add(send, BorderLayout.SOUTH);
      inputCard.add(sendWrap, BorderLayout.EAST);

      final JPanel south = new JPanel(new BorderLayout());
      south.setOpaque(false);
      south.setBorder(new EmptyBorder(6, 0, 0, 0));
      south.add(inputCard, BorderLayout.CENTER);
      add(south, BorderLayout.SOUTH);
   }

   private void resizeInput() {
      SwingUtilities.invokeLater(() -> {
         // Count wrapped lines at the current width.
         int lines = INPUT_MIN_ROWS;
         try {
            final int len = input.getDocument().getLength();
            if (len > 0 && input.getWidth() > 0) {
               final java.awt.geom.Rectangle2D end = input.modelToView2D(len);
               if (end != null) {
                  lines = (int) end.getY() / input.getFontMetrics(input.getFont()).getHeight() + 1;
               }
            }
         } catch (final Exception ignored) {
            // keep default
         }
         final int rows = Math.max(INPUT_MIN_ROWS, Math.min(INPUT_MAX_ROWS, lines));
         if (input.getRows() != rows) {
            input.setRows(rows);
            inputScroll.revalidate();
            revalidate();
         }
      });
   }

   public void focusInput() {
      input.requestFocusInWindow();
   }

   public void addUserMessage(final String text) {
      removeEmptyState();
      final HtmlPane body = new HtmlPane(TEXT);
      body.setPlainText(text);
      addRow(bubble(null, body, USER_BG), USER_INDENT);
   }

   public void addAssistantMessage(final String markdown) {
      addAssistantMessage(markdown, null);
   }

   /** Answer text, optionally followed by a component (e.g. the guide checklist with Track button). */
   public void addAssistantMessage(final String markdown, final JComponent extra) {
      removeThinking();
      final HtmlPane pane = new HtmlPane(TEXT);
      pane.setHtml(MarkdownRenderer.toHtml(markdown));
      final JComponent body;
      if (extra == null) {
         body = pane;
      } else {
         final JPanel col = new JPanel();
         col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
         col.setOpaque(false);
         pane.setAlignmentX(Component.LEFT_ALIGNMENT);
         extra.setAlignmentX(Component.LEFT_ALIGNMENT);
         col.add(pane);
         col.add(Box.createVerticalStrut(6));
         col.add(extra);
         body = col;
      }
      addRow(bubble("RuneGPT", body, ASSISTANT_BG), 0);
   }

   /** A custom card (e.g. a quest step) in place of a plain text answer. */
   public void addCardMessage(final String header, final JComponent body) {
      removeThinking();
      addRow(bubble(header, body, ASSISTANT_BG), 0);
   }

   /** A neutral notice from RuneGPT itself (e.g. about API keys), shown like an answer. */
   public void addNotice(final String text) {
      removeEmptyState();
      final HtmlPane pane = new HtmlPane(TEXT);
      pane.setPlainText(text);
      addRow(bubble("RuneGPT", pane, NOTICE_BG), 0);
   }

   public void addErrorMessage(final String text) {
      removeThinking();
      final HtmlPane pane = new HtmlPane(TEXT);
      pane.setPlainText(text);
      addRow(bubble("Couldn't get an answer", pane, ERROR_BG), 0);
   }

   public void showThinking(final String label) {
      removeEmptyState();
      final JLabel dots = new JLabel(label);
      dots.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
      dots.setFont(FontManager.getRunescapeFont());
      thinkingRow = addRow(bubble("RuneGPT", dots, ASSISTANT_BG), 0);
      final int[] tick = { 0 };
      thinkingTimer = new Timer(400, e -> {
         tick[0] = (tick[0] + 1) % 4;
         dots.setText(label + "...".substring(0, tick[0]));
      });
      thinkingTimer.start();
      setBusy(true);
   }

   public void setBusy(final boolean busy) {
      send.setEnabled(!busy);
      newChat.setEnabled(!busy);
   }

   /** Stops the "Thinking..." animation timer. Called when the plugin shuts down. */
   public void shutdown() {
      removeThinking();
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
      final RoundedPanel b = new RoundedPanel(new BorderLayout(0, 4), bg, 12);
      b.setBorder(new EmptyBorder(6, 8, 8, 8));
      if (header != null) {
         final JLabel h = new JLabel(header);
         h.setFont(FontManager.getRunescapeSmallFont());
         h.setForeground(ColorScheme.BRAND_ORANGE);
         b.add(h, BorderLayout.NORTH);
      }
      b.add(body, BorderLayout.CENTER);
      return b;
   }

   private JComponent addRow(final JComponent bubble, final int leftIndent) {
      final JPanel row = new JPanel(new BorderLayout());
      row.setOpaque(false);
      row.setBorder(new EmptyBorder(ROW_PAD, ROW_PAD - 2 + leftIndent, 0, ROW_PAD - 2));
      row.add(bubble, BorderLayout.CENTER);
      row.setAlignmentX(Component.LEFT_ALIGNMENT);
      messages.add(row);
      messages.revalidate();
      messages.repaint();
      scrollToNewest(row);
      return row;
   }

   /**
    * Bring the newest message into view: the whole message when it fits, otherwise its first line (long
    * answers read from the top). Wrapped text only gets its final height after a layout pass or two, so this
    * re-runs whenever the newest row resizes shortly after being added, instead of using a stale height that
    * leaves the first or last line cut off.
    */
   private void scrollToNewest(final JComponent row) {
      newestRow = row;
      newestAddedAt = System.currentTimeMillis();
      row.addComponentListener(new ComponentAdapter() {
         @Override
         public void componentResized(final ComponentEvent e) {
            if (row == newestRow && System.currentTimeMillis() - newestAddedAt < RESCROLL_WINDOW_MS) {
               scheduleScroll(row);
            }
         }
      });
      scheduleScroll(row);
   }

   private void scheduleScroll(final JComponent row) {
      SwingUtilities.invokeLater(() -> SwingUtilities.invokeLater(() -> {
         if (row != newestRow) {
            return;
         }
         final Rectangle r = row.getBounds();
         final int viewport = scroll.getViewport().getHeight();
         // A rect taller than the viewport would scroll to its bottom; cap it so the top stays visible.
         list.scrollRectToVisible(new Rectangle(0, r.y, 1, Math.min(r.height + ROW_PAD, viewport)));
      }));
   }

   private JComponent emptyState() {
      final JPanel p = new JPanel();
      p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
      p.setOpaque(false);
      p.setBorder(new EmptyBorder(16, 12, 12, 12));
      p.setAlignmentX(Component.LEFT_ALIGNMENT);
      final JLabel title = new JLabel("Ask me anything");
      title.setFont(FontManager.getRunescapeBoldFont());
      title.setForeground(Color.WHITE);
      title.setAlignmentX(Component.LEFT_ALIGNMENT);
      final HtmlPane body = new HtmlPane(ColorScheme.LIGHT_GRAY_COLOR, FontManager.getRunescapeSmallFont());
      body.setPlainText("Open a section below and press Ask, or type a question. "
         + "Your stats, gear and quest progress are included automatically.");
      body.setAlignmentX(Component.LEFT_ALIGNMENT);
      p.add(title);
      p.add(Box.createVerticalStrut(4));
      p.add(body);
      return p;
   }
}
