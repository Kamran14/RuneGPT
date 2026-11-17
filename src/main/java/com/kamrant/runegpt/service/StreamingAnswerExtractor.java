package com.kamrant.runegpt.service;

/**
 * Pulls the readable answer out of a reply while it is still streaming. Replies normally follow the JSON
 * contract ({"answer": "...", "guide": {...}}), so only the top-level "answer" string is passed on, decoded,
 * as it arrives; the guide and suggestions are shown once the full reply has been parsed. A reply that isn't
 * JSON (plain Markdown) is passed through as is. A leading ``` fence line is skipped in both cases.
 */
public final class StreamingAnswerExtractor {

   private enum Mode { DETECT, PLAIN, JSON }

   private Mode mode = Mode.DETECT;
   private final StringBuilder lead = new StringBuilder();

   // JSON scanner state.
   private int depth;
   private boolean inString;
   private boolean escape;
   private boolean stringIsKey;
   private boolean stringIsAnswer;
   private boolean expectKey;
   private boolean awaitingAnswerValue;
   private boolean answerDone;
   private final StringBuilder key = new StringBuilder();
   private String lastKey = "";
   private final StringBuilder unicode = new StringBuilder();
   private boolean inUnicode;

   /** Starts over (the provider failed mid-stream and another one is answering). */
   public void reset() {
      mode = Mode.DETECT;
      lead.setLength(0);
      depth = 0;
      inString = false;
      escape = false;
      stringIsKey = false;
      stringIsAnswer = false;
      expectKey = false;
      awaitingAnswerValue = false;
      answerDone = false;
      key.setLength(0);
      lastKey = "";
      unicode.setLength(0);
      inUnicode = false;
   }

   /** Feeds the next chunk of raw reply text; returns the readable text it adds ("" if none yet). */
   public String feed(final String chunk) {
      if (chunk == null || chunk.isEmpty()) {
         return "";
      }
      if (mode == Mode.PLAIN) {
         return chunk;
      }
      if (mode == Mode.JSON) {
         return scan(chunk);
      }
      lead.append(chunk);
      return detect();
   }

   /** Decides JSON vs plain text once the first meaningful character has arrived. */
   private String detect() {
      String s = lead.toString();
      int i = 0;
      while (true) {
         while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
            i++;
         }
         if (s.startsWith("```", i)) {
            final int nl = s.indexOf('\n', i);
            if (nl < 0) {
               return ""; // wait for the rest of the fence line
            }
            i = nl + 1;
            continue;
         }
         break;
      }
      if (i >= s.length()) {
         return "";
      }
      // A lone '`' or "``" might still become a fence; wait for more text.
      if (s.charAt(i) == '`' && s.length() - i < 3) {
         return "";
      }
      final String rest = s.substring(i);
      lead.setLength(0);
      if (rest.charAt(0) == '{') {
         mode = Mode.JSON;
         return scan(rest);
      }
      mode = Mode.PLAIN;
      return rest;
   }

   private String scan(final String chunk) {
      final StringBuilder out = new StringBuilder();
      for (int i = 0; i < chunk.length(); i++) {
         final char c = chunk.charAt(i);
         if (inString) {
            scanStringChar(c, out);
            continue;
         }
         switch (c) {
            case '{':
            case '[':
               depth++;
               expectKey = c == '{' && depth == 1;
               awaitingAnswerValue = false;
               break;
            case '}':
            case ']':
               depth--;
               break;
            case ',':
               expectKey = depth == 1;
               awaitingAnswerValue = false;
               break;
            case ':':
               if (depth == 1) {
                  awaitingAnswerValue = "answer".equals(lastKey) && !answerDone;
               }
               break;
            case '"':
               inString = true;
               stringIsKey = depth == 1 && expectKey;
               stringIsAnswer = !stringIsKey && depth == 1 && awaitingAnswerValue;
               if (stringIsKey) {
                  key.setLength(0);
               }
               expectKey = false;
               awaitingAnswerValue = false;
               break;
            default:
               if (!Character.isWhitespace(c)) {
                  awaitingAnswerValue = false; // answer isn't a string (e.g. null)
               }
               break;
         }
      }
      return out.toString();
   }

   private void scanStringChar(final char c, final StringBuilder out) {
      if (inUnicode) {
         unicode.append(c);
         if (unicode.length() == 4) {
            inUnicode = false;
            try {
               emit((char) Integer.parseInt(unicode.toString(), 16), out);
            } catch (final NumberFormatException ignored) {
               // malformed escape: drop it
            }
            unicode.setLength(0);
         }
         return;
      }
      if (escape) {
         escape = false;
         switch (c) {
            case 'n':
               emit('\n', out);
               break;
            case 't':
               emit('\t', out);
               break;
            case 'r':
               break;
            case 'b':
            case 'f':
               break;
            case 'u':
               inUnicode = true;
               unicode.setLength(0);
               break;
            default:
               emit(c, out); // \" \\ \/
               break;
         }
         return;
      }
      if (c == '\\') {
         escape = true;
         return;
      }
      if (c == '"') {
         inString = false;
         if (stringIsKey) {
            lastKey = key.toString();
         }
         if (stringIsAnswer) {
            answerDone = true;
         }
         stringIsKey = false;
         stringIsAnswer = false;
         return;
      }
      emit(c, out);
   }

   private void emit(final char c, final StringBuilder out) {
      if (stringIsKey) {
         key.append(c);
      } else if (stringIsAnswer) {
         out.append(c);
      }
   }
}
