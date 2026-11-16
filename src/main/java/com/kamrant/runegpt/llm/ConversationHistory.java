package com.kamrant.runegpt.llm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Client-side conversation memory. Lives in the plugin (not in any provider), so the
 * conversation carries over unchanged when a request fails over to another provider.
 */
public class ConversationHistory {

   // Keep the most recent turns only, so long sessions don't blow past free-tier token limits.
   private static final int MAX_MESSAGES = 20;

   private final List<ChatMessage> messages = new ArrayList<>();

   /** Messages so far plus the pending user prompt, ready to send to a provider. */
   public synchronized List<ChatMessage> withPendingPrompt(final String prompt) {
      final List<ChatMessage> out = new ArrayList<>(messages);
      out.add(new ChatMessage(ChatMessage.Role.USER, prompt));
      return Collections.unmodifiableList(out);
   }

   /** Record a completed exchange. Only called once a provider has answered. */
   public synchronized void addExchange(final String prompt, final String reply) {
      messages.add(new ChatMessage(ChatMessage.Role.USER, prompt));
      messages.add(new ChatMessage(ChatMessage.Role.ASSISTANT, reply));
      // Trim whole exchanges from the front so history always starts with a user turn.
      while (messages.size() > MAX_MESSAGES) {
         messages.remove(0);
         messages.remove(0);
      }
   }

   public synchronized void clear() {
      messages.clear();
   }
}
