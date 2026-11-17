package com.kamrant.runegpt.llm;

/** Passes streamed text through and remembers whether any arrived, so failover knows to reset the shown text. */
final class TrackingStreamListener implements StreamListener {

   private final StreamListener delegate;
   private boolean hasText;

   TrackingStreamListener(final StreamListener delegate) {
      this.delegate = delegate;
   }

   boolean hasText() {
      return hasText;
   }

   @Override
   public void onText(final String delta) {
      hasText = true;
      delegate.onText(delta);
   }

   @Override
   public void onReset() {
      hasText = false;
      delegate.onReset();
   }
}
