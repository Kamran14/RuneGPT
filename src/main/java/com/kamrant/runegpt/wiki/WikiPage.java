package com.kamrant.runegpt.wiki;

/** A wiki page after following redirects: its real title and raw wikitext. */
final class WikiPage {

   private final String title;
   private final String text;

   WikiPage(final String title, final String text) {
      this.title = title;
      this.text = text;
   }

   String getTitle() {
      return title;
   }

   String getText() {
      return text;
   }
}
