package com.kamrant.runegpt.ui;

import com.kamrant.runegpt.llm.StreamListener;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * Bridges streamed answer text from the worker thread to the chat. Text is collected as it arrives and the
 * bubble is re-rendered on the EDT at most every {@link #MIN_RENDER_INTERVAL_MS}, since re-rendering Markdown
 * on every token would be wasteful. {@link #close} (on the EDT, before the final answer is shown) stops any
 * late update from touching the chat.
 */
public final class StreamingTextSink implements StreamListener {

   private static final int MIN_RENDER_INTERVAL_MS = 100;

   private final ChatView chatView;
   private final StringBuilder text = new StringBuilder();
   private final AtomicBoolean scheduled = new AtomicBoolean();
   // EDT-only state.
   private boolean closed;
   private long lastRender;
   private Timer delay;

   public StreamingTextSink(final ChatView chatView) {
      this.chatView = chatView;
   }

   @Override
   public void onText(final String delta) {
      synchronized (text) {
         text.append(delta);
      }
      if (scheduled.compareAndSet(false, true)) {
         SwingUtilities.invokeLater(this::flush);
      }
   }

   @Override
   public void onReset() {
      synchronized (text) {
         text.setLength(0);
      }
      SwingUtilities.invokeLater(() -> {
         if (!closed) {
            chatView.resetStreaming();
         }
      });
   }

   /** Stops further updates. Call on the EDT before showing the final answer or error. */
   public void close() {
      closed = true;
      if (delay != null) {
         delay.stop();
         delay = null;
      }
   }

   private void flush() {
      if (closed) {
         return;
      }
      final long wait = MIN_RENDER_INTERVAL_MS - (System.currentTimeMillis() - lastRender);
      if (wait > 0) {
         if (delay == null) {
            delay = new Timer((int) wait, e -> {
               delay = null;
               flush();
            });
            delay.setRepeats(false);
            delay.start();
         }
         return;
      }
      scheduled.set(false);
      lastRender = System.currentTimeMillis();
      final String snapshot;
      synchronized (text) {
         snapshot = text.toString();
      }
      chatView.showStreamingText(snapshot);
   }
}
