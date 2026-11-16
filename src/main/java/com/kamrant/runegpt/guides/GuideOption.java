package com.kamrant.runegpt.guides;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * An optional quick choice on a guide, e.g. "Attack style: Melee / Ranged / Magic".
 * Shown as chips; leaving it unselected means "no preference".
 */
public final class GuideOption {

   private final String label;
   private final List<String> choices;

   public GuideOption(final String label, final String... choices) {
      this.label = label;
      this.choices = Collections.unmodifiableList(Arrays.asList(choices));
   }

   public String getLabel() {
      return label;
   }

   public List<String> getChoices() {
      return choices;
   }

   public static final GuideOption ATTACK_STYLE = new GuideOption("Attack style", "Melee", "Ranged", "Magic");
   public static final GuideOption SLAYER_TASK = new GuideOption("Slayer", "On task", "Off task");
   public static final GuideOption BUDGET = new GuideOption("Gear budget", "Budget", "Mid", "Best in slot");
   public static final GuideOption PACE = new GuideOption("Priority", "Fastest", "Cheapest", "AFK");
}
