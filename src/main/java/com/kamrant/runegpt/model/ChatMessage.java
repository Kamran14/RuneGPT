package com.kamrant.runegpt.model;

/**
 * One turn of the conversation, stored in a provider-neutral form so the same
 * history can be replayed to any LLM provider.
 */
public final class ChatMessage {

   private final ChatRole role;
   private final String text;

   public ChatMessage(final ChatRole role, final String text) {
      this.role = role;
      this.text = text;
   }

   public ChatRole getRole() {
      return role;
   }

   public String getText() {
      return text;
   }
}
