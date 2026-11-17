package com.kamrant.runegpt.llm;

/**
 * Receives reply text as it streams in. Called on the worker thread. {@link #onReset} means the text so far
 * is void (the provider failed mid-stream and the next provider starts over).
 */
public interface StreamListener {

   void onText(String delta);

   void onReset();
}
