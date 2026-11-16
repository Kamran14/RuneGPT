package com.kamrant.runegpt.ui;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Small Markdown to HTML converter for LLM replies, targeting Swing's HTML 3.2 renderer.
 * Supports headings, bold/italic, inline code, code blocks, bullet/numbered lists (nested),
 * blockquotes, horizontal rules, tables and http(s) links. Everything else is escaped text.
 */
public final class MarkdownRenderer {

   private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(.*?)\\s*#*\\s*$");
   private static final Pattern BULLET = Pattern.compile("^(\\s*)[-*+]\\s+(.*)$");
   private static final Pattern NUMBERED = Pattern.compile("^(\\s*)\\d+[.)]\\s+(.*)$");
   private static final Pattern HR = Pattern.compile("^\\s*([-*_])(\\s*\\1){2,}\\s*$");
   private static final Pattern TABLE_SEPARATOR = Pattern.compile("^\\s*\\|?\\s*:?-{2,}:?\\s*(\\|\\s*:?-{2,}:?\\s*)*\\|?\\s*$");

   private static final Pattern INLINE_CODE = Pattern.compile("`([^`]+)`");
   private static final Pattern LINK = Pattern.compile("\\[([^\\]]+)]\\((https?://[^)\\s]+)\\)");
   private static final Pattern BOLD = Pattern.compile("(\\*\\*|__)(?=\\S)(.+?)(?<=\\S)\\1");
   private static final Pattern ITALIC_STAR = Pattern.compile("(?<![*\\w])\\*(?=\\S)(.+?)(?<=\\S)\\*(?![*\\w])");
   private static final Pattern ITALIC_UNDERSCORE = Pattern.compile("(?<![\\w_])_(?=\\S)(.+?)(?<=\\S)_(?![\\w_])");

   private MarkdownRenderer() {
   }

   /** Returns an HTML fragment (no html/body tags). */
   public static String toHtml(final String markdown) {
      final String[] lines = markdown.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
      final StringBuilder out = new StringBuilder();
      final List<String> paragraph = new ArrayList<>();
      // Open list tags as a stack of {indent, tag}.
      final Deque<Object[]> lists = new ArrayDeque<>();

      int i = 0;
      while (i < lines.length) {
         final String line = lines[i];
         final String trimmed = line.trim();

         // Fenced code block
         if (trimmed.startsWith("```")) {
            flushParagraph(out, paragraph);
            closeLists(out, lists, -1);
            final StringBuilder code = new StringBuilder();
            i++;
            while (i < lines.length && !lines[i].trim().startsWith("```")) {
               code.append(escape(lines[i])).append("\n");
               i++;
            }
            i++; // skip closing fence
            out.append("<pre>").append(code).append("</pre>");
            continue;
         }

         if (trimmed.isEmpty()) {
            flushParagraph(out, paragraph);
            // A blank line inside a list doesn't end it if the next line continues the list.
            if (!lists.isEmpty() && !(i + 1 < lines.length && isListItem(lines[i + 1]))) {
               closeLists(out, lists, -1);
            }
            i++;
            continue;
         }

         // Table: header row followed by a separator row
         if (trimmed.contains("|") && i + 1 < lines.length && TABLE_SEPARATOR.matcher(lines[i + 1]).matches()) {
            flushParagraph(out, paragraph);
            closeLists(out, lists, -1);
            out.append("<table cellspacing=\"0\" cellpadding=\"3\">");
            appendRow(out, trimmed, "th");
            i += 2;
            while (i < lines.length && lines[i].trim().contains("|") && !lines[i].trim().isEmpty()) {
               appendRow(out, lines[i].trim(), "td");
               i++;
            }
            out.append("</table>");
            continue;
         }

         final Matcher heading = HEADING.matcher(trimmed);
         if (heading.matches()) {
            flushParagraph(out, paragraph);
            closeLists(out, lists, -1);
            // Map h1/h2 -> h2, h3+ -> h3 so headings stay small in the sidebar.
            final String tag = heading.group(1).length() <= 2 ? "h2" : "h3";
            out.append('<').append(tag).append('>').append(inline(heading.group(2))).append("</").append(tag).append('>');
            i++;
            continue;
         }

         if (HR.matcher(trimmed).matches()) {
            flushParagraph(out, paragraph);
            closeLists(out, lists, -1);
            out.append("<hr>");
            i++;
            continue;
         }

         final Matcher bullet = BULLET.matcher(line);
         final Matcher numbered = NUMBERED.matcher(line);
         if (bullet.matches() || numbered.matches()) {
            flushParagraph(out, paragraph);
            final boolean isBullet = bullet.matches();
            final Matcher m = isBullet ? bullet : numbered;
            final int indent = m.group(1).replace("\t", "    ").length();
            final String tag = isBullet ? "ul" : "ol";

            // Close deeper lists, or a same-level list of the other type.
            closeLists(out, lists, indent);
            if (!lists.isEmpty() && (int) lists.peek()[0] == indent && !lists.peek()[1].equals(tag)) {
               out.append("</").append(lists.pop()[1]).append('>');
            }
            if (lists.isEmpty() || (int) lists.peek()[0] < indent) {
               out.append('<').append(tag).append('>');
               lists.push(new Object[] { indent, tag });
            }
            out.append("<li>").append(inline(m.group(2))).append("</li>");
            i++;
            continue;
         }

         if (trimmed.startsWith(">")) {
            flushParagraph(out, paragraph);
            closeLists(out, lists, -1);
            final StringBuilder quote = new StringBuilder();
            while (i < lines.length && lines[i].trim().startsWith(">")) {
               if (quote.length() > 0) {
                  quote.append(' ');
               }
               quote.append(lines[i].trim().substring(1).trim());
               i++;
            }
            out.append("<div class=\"quote\">").append(inline(quote.toString())).append("</div>");
            continue;
         }

         if (!lists.isEmpty()) {
            final boolean indented = line.startsWith(" ") || line.startsWith("\t");
            final int liEnd = out.length() - "</li>".length();
            if (indented && liEnd >= 0 && out.indexOf("</li>", liEnd) == liEnd) {
               // Continuation of the previous list item.
               out.insert(liEnd, " " + inline(trimmed));
               i++;
               continue;
            }
            closeLists(out, lists, -1);
         }
         paragraph.add(trimmed);
         i++;
      }

      flushParagraph(out, paragraph);
      closeLists(out, lists, -1);
      return out.toString();
   }

   public static String escape(final String text) {
      return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
   }

   private static boolean isListItem(final String line) {
      return BULLET.matcher(line).matches() || NUMBERED.matcher(line).matches();
   }

   private static void flushParagraph(final StringBuilder out, final List<String> paragraph) {
      if (paragraph.isEmpty()) {
         return;
      }
      out.append("<p>").append(inline(String.join(" ", paragraph))).append("</p>");
      paragraph.clear();
   }

   /** Closes lists nested deeper than {@code indent}; -1 closes all. */
   private static void closeLists(final StringBuilder out, final Deque<Object[]> lists, final int indent) {
      while (!lists.isEmpty() && (indent < 0 || (int) lists.peek()[0] > indent)) {
         out.append("</").append(lists.pop()[1]).append('>');
      }
   }

   private static void appendRow(final StringBuilder out, final String row, final String cellTag) {
      String r = row;
      if (r.startsWith("|")) {
         r = r.substring(1);
      }
      if (r.endsWith("|")) {
         r = r.substring(0, r.length() - 1);
      }
      out.append("<tr>");
      for (final String cell : r.split("\\|", -1)) {
         out.append('<').append(cellTag).append('>').append(inline(cell.trim())).append("</").append(cellTag).append('>');
      }
      out.append("</tr>");
   }

   /** Inline formatting. Code spans are pulled out first so nothing inside them is formatted. */
   static String inline(final String text) {
      final List<String> codeSpans = new ArrayList<>();
      final Matcher code = INLINE_CODE.matcher(text);
      final StringBuffer withTokens = new StringBuffer();
      while (code.find()) {
         codeSpans.add(code.group(1));
         code.appendReplacement(withTokens, "\u0000" + (codeSpans.size() - 1) + "\u0000");
      }
      code.appendTail(withTokens);

      String html = escape(withTokens.toString());
      html = LINK.matcher(html).replaceAll("<a href=\"$2\">$1</a>");
      html = BOLD.matcher(html).replaceAll("<b>$2</b>");
      html = ITALIC_STAR.matcher(html).replaceAll("<i>$1</i>");
      html = ITALIC_UNDERSCORE.matcher(html).replaceAll("<i>$1</i>");

      for (int c = 0; c < codeSpans.size(); c++) {
         html = html.replace("\u0000" + c + "\u0000", "<code>" + escape(codeSpans.get(c)) + "</code>");
      }
      return html;
   }
}
