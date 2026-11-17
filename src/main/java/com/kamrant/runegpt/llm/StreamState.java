package com.kamrant.runegpt.llm;

/** The text and end state collected while one provider's reply streams in. */
public final class StreamState {

   private final StringBuilder text = new StringBuilder();
   private boolean truncated;

   void append(final String delta) {
      text.append(delta);
   }

   public String text() {
      return text.toString();
   }

   public boolean isEmpty() {
      return text.length() == 0;
   }

   /** The provider stopped because it hit the reply token limit. */
   public void markTruncated() {
      truncated = true;
   }

   public boolean isTruncated() {
      return truncated;
   }
}
