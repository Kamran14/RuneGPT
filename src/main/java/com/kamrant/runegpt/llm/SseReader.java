package com.kamrant.runegpt.llm;

import java.io.IOException;
import okio.BufferedSource;

/**
 * Minimal Server-Sent Events reader: returns the {@code data} payload of each event in turn. Multi-line data
 * is joined with newlines; comments, {@code event:}, {@code id:} and {@code retry:} lines are ignored. The
 * OpenAI-style {@code [DONE]} terminator ends the stream.
 */
public final class SseReader {

   static final String DONE = "[DONE]";

   private final BufferedSource source;
   private boolean finished;

   public SseReader(final BufferedSource source) {
      this.source = source;
   }

   /** The next event's data, or null at the end of the stream. */
   public String next() throws IOException {
      if (finished) {
         return null;
      }
      StringBuilder data = null;
      while (true) {
         final String line = source.readUtf8Line();
         if (line == null) {
            finished = true;
            return data == null ? null : done(data.toString());
         }
         if (line.isEmpty()) {
            if (data != null) {
               return done(data.toString());
            }
            continue;
         }
         if (line.startsWith("data:")) {
            String value = line.substring(5);
            if (value.startsWith(" ")) {
               value = value.substring(1);
            }
            if (data == null) {
               data = new StringBuilder(value);
            } else {
               data.append('\n').append(value);
            }
         }
         // Other fields (event:, id:, retry:) and ":" comments carry nothing we need.
      }
   }

   private String done(final String data) {
      if (DONE.equals(data.trim())) {
         finished = true;
         return null;
      }
      return data;
   }
}
