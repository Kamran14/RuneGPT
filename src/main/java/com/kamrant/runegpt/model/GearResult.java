package com.kamrant.runegpt.model;

import java.util.Collections;
import java.util.List;

/**
 * Result of a gear optimisation against one monster: the best setup per combat style (best first), or an
 * error message when there's nothing to compute (unknown monster, not logged in...). Immutable.
 */
public final class GearResult {

   private final String input;
   private final MonsterStats monster;
   private final List<GearSetup> setups;
   private final CombatStyle preferred;
   private final boolean onTask;
   private final boolean bankKnown;
   private final List<String> hidden;
   private final WorldTarget target;
   private final String error;
   private final String wikiUrl;

   private GearResult(final String input, final MonsterStats monster, final List<GearSetup> setups,
      final CombatStyle preferred, final boolean onTask, final boolean bankKnown, final List<String> hidden,
      final WorldTarget target, final String error, final String wikiUrl) {
      this.input = input;
      this.monster = monster;
      this.setups = Collections.unmodifiableList(setups);
      this.preferred = preferred;
      this.onTask = onTask;
      this.bankKnown = bankKnown;
      this.hidden = Collections.unmodifiableList(hidden);
      this.target = target;
      this.error = error;
      this.wikiUrl = wikiUrl;
   }

   public static GearResult of(final String input, final MonsterStats monster, final List<GearSetup> setups,
      final CombatStyle preferred, final boolean onTask, final boolean bankKnown, final List<String> hidden,
      final WorldTarget target, final String wikiUrl) {
      return new GearResult(input, monster, setups, preferred, onTask, bankKnown, hidden, target, null, wikiUrl);
   }

   public static GearResult error(final String input, final String message) {
      return new GearResult(input, null, Collections.emptyList(), null, false, false, Collections.emptyList(), null,
         message, null);
   }

   public boolean isSuccess() {
      return error == null;
   }

   /** What the player typed as the target. */
   public String getInput() {
      return input;
   }

   public MonsterStats getMonster() {
      return monster;
   }

   /** Best setup per style, best first. The preferred style (if any) comes first regardless. */
   public List<GearSetup> getSetups() {
      return setups;
   }

   /** Style the player asked for, or null for "best overall". */
   public CombatStyle getPreferred() {
      return preferred;
   }

   public boolean isOnTask() {
      return onTask;
   }

   public boolean isBankKnown() {
      return bankKnown;
   }

   /** Items left out because the wiki says the player's levels are too low to wear them. */
   public List<String> getHidden() {
      return hidden;
   }

   /** The monster as a navigation target (wiki coordinates when known), or null. */
   public WorldTarget getTarget() {
      return target;
   }

   public String getError() {
      return error;
   }

   public String getWikiUrl() {
      return wikiUrl;
   }
}
