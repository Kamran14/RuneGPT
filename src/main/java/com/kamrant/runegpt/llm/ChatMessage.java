package com.kamrant.runegpt.llm;

/**
 * One turn of the conversation, stored in a provider-neutral form so the same
 * history can be replayed to any LLM provider.
 */
public final class ChatMessage {

   public enum Role {
      USER,
      ASSISTANT
   }

   private final Role role;
   private final String text;

   public ChatMessage(final Role role, final String text) {
      this.role = role;
      this.text = text;
   }

   public Role getRole() {
      return role;
   }

   public String getText() {
      return text;
   }
}
