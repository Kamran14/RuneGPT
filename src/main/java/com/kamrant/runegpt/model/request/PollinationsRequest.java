package com.kamrant.runegpt.model.request;

import com.google.gson.annotations.SerializedName;
import com.kamrant.runegpt.model.ChatMessage;
import com.kamrant.runegpt.model.ChatRole;
import java.util.List;

/**
 * Body for the keyless Pollinations "Classic" text endpoint (POST https://text.pollinations.ai/):
 * {"messages": [...]}, answered with plain text. POST is used rather than GET so questions never end up
 * in a URL (GET results are cached and viewable by anyone with the URL).
 */
public final class PollinationsRequest {

   @SerializedName("messages")
   private final PollinationsMessage[] messages;
   // Not in the Classic docs, but accepted: without it the free reasoning model can spend its whole
   // output budget thinking and return no answer for longer guide prompts.
   @SerializedName("reasoning_effort")
   private final String reasoningEffort = "low";

   private PollinationsRequest(final PollinationsMessage[] messages) {
      this.messages = messages;
   }

   public static PollinationsRequest create(final String system, final List<ChatMessage> history) {
      final PollinationsMessage[] messages = new PollinationsMessage[history.size() + 1];
      messages[0] = new PollinationsMessage("system", system);
      for (int i = 0; i < history.size(); i++) {
         final ChatMessage msg = history.get(i);
         messages[i + 1] = new PollinationsMessage(msg.getRole() == ChatRole.USER ? "user" : "assistant", msg.getText());
      }
      return new PollinationsRequest(messages);
   }
}
