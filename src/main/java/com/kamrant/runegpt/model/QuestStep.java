package com.kamrant.runegpt.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Result of a quest step lookup, ready for the panel to show. */
public final class QuestStep {

   private QuestStepKind kind;
   private String headerLabel = "";
   private String quest;
   private String input;
   private String wikiUrl;
   private String message;
   private String progress = "";
   private String confidence = "";
   private List<String> completedSteps = Collections.emptyList();
   private String nextStep = "";
   private WorldTarget location;
   private WorldTarget npc;
   private boolean npcNearby;
   private List<GuideItem> items = Collections.emptyList();
   private List<String> then = Collections.emptyList();
   private String tip = "";
   private int removedUnverified;
   private boolean bankKnown;
   private List<String> suggestions = Collections.emptyList();
   private List<String> wikiMatches = Collections.emptyList();

   public static QuestStep of(final QuestStepKind kind) {
      final QuestStep s = new QuestStep();
      s.kind = kind;
      return s;
   }

   public static QuestStep error(final String quest, final String message) {
      final QuestStep s = of(QuestStepKind.ERROR);
      s.quest = quest;
      s.message = message;
      return s;
   }

   /**
    * The step as a trackable guide: completed steps (green), then the current step (orange/red), then
    * upcoming steps (muted), plus items, location and NPC.
    */
   public GuidePlan toGuidePlan() {
      final List<String> steps = new ArrayList<>(completedSteps);
      final int activeIdx = steps.size();
      if (!nextStep.isEmpty()) {
         steps.add(nextStep);
      }
      steps.addAll(then);
      return new GuidePlan(quest, steps, activeIdx, items, location, npc, removedUnverified);
   }

   public QuestStepKind getKind() {
      return kind;
   }

   public String getHeaderLabel() {
      return headerLabel;
   }

   public QuestStep setHeaderLabel(final String headerLabel) {
      this.headerLabel = headerLabel == null ? "" : headerLabel;
      return this;
   }

   public String getQuest() {
      return quest;
   }

   public QuestStep setQuest(final String quest) {
      this.quest = quest;
      return this;
   }

   /** What the player asked about (may not be a real quest name). */
   public String getInput() {
      return input;
   }

   public QuestStep setInput(final String input) {
      this.input = input;
      return this;
   }

   public String getWikiUrl() {
      return wikiUrl;
   }

   public QuestStep setWikiUrl(final String wikiUrl) {
      this.wikiUrl = wikiUrl;
      return this;
   }

   public String getMessage() {
      return message;
   }

   public String getProgress() {
      return progress;
   }

   public QuestStep setProgress(final String progress) {
      this.progress = progress;
      return this;
   }

   public String getConfidence() {
      return confidence;
   }

   public QuestStep setConfidence(final String confidence) {
      this.confidence = confidence;
      return this;
   }

   public List<String> getCompletedSteps() {
      return completedSteps;
   }

   public QuestStep setCompletedSteps(final List<String> completedSteps) {
      this.completedSteps = completedSteps;
      return this;
   }

   public String getNextStep() {
      return nextStep;
   }

   public QuestStep setNextStep(final String nextStep) {
      this.nextStep = nextStep;
      return this;
   }

   /** Where to go, or null. */
   public WorldTarget getLocation() {
      return location;
   }

   public QuestStep setLocation(final WorldTarget location) {
      this.location = location;
      return this;
   }

   /** NPC to talk to next, verified against the wiki; null if none. */
   public WorldTarget getNpc() {
      return npc;
   }

   public QuestStep setNpc(final WorldTarget npc) {
      this.npc = npc;
      return this;
   }

   public boolean isNpcNearby() {
      return npcNearby;
   }

   public QuestStep setNpcNearby(final boolean npcNearby) {
      this.npcNearby = npcNearby;
      return this;
   }

   public List<GuideItem> getItems() {
      return items;
   }

   public QuestStep setItems(final List<GuideItem> items) {
      this.items = items;
      return this;
   }

   public List<String> getThen() {
      return then;
   }

   public QuestStep setThen(final List<String> then) {
      this.then = then;
      return this;
   }

   public String getTip() {
      return tip;
   }

   public QuestStep setTip(final String tip) {
      this.tip = tip;
      return this;
   }

   /** How many AI-suggested items/NPCs were dropped because the wiki doesn't have them. */
   public int getRemovedUnverified() {
      return removedUnverified;
   }

   public QuestStep setRemovedUnverified(final int removedUnverified) {
      this.removedUnverified = removedUnverified;
      return this;
   }

   public boolean isBankKnown() {
      return bankKnown;
   }

   public QuestStep setBankKnown(final boolean bankKnown) {
      this.bankKnown = bankKnown;
      return this;
   }

   public List<String> getSuggestions() {
      return suggestions;
   }

   public QuestStep setSuggestions(final List<String> suggestions) {
      this.suggestions = suggestions;
      return this;
   }

   public List<String> getWikiMatches() {
      return wikiMatches;
   }

   public QuestStep setWikiMatches(final List<String> wikiMatches) {
      this.wikiMatches = wikiMatches;
      return this;
   }
}
