package com.kamrant.runegpt.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * The player's account type, read from {@code VarbitID.IRONMAN} (varbit 1777: 0 normal, 1 Ironman, 2 Ultimate,
 * 3 Hardcore, 4 Group, 5 Hardcore Group, 6 Unranked Group). Decides whether advice may use the Grand Exchange,
 * trading or a bank, and whether deadly content gets a Hardcore warning. The prompt text for each mode lives in
 * {@code Prompts.accountContext}.
 */
@Getter
@RequiredArgsConstructor
public enum AccountMode {

   MAIN(0, "Normal account", false, true, false, false),
   IRONMAN(1, "Ironman", true, true, false, false),
   ULTIMATE_IRONMAN(2, "Ultimate Ironman", true, false, false, false),
   HARDCORE_IRONMAN(3, "Hardcore Ironman", true, true, true, false),
   GROUP_IRONMAN(4, "Group Ironman", true, true, false, true),
   HARDCORE_GROUP_IRONMAN(5, "Hardcore Group Ironman", true, true, true, true),
   UNRANKED_GROUP_IRONMAN(6, "Unranked Group Ironman", true, true, false, true);

   private final int varbitValue;
   private final String displayName;
   /** True for every Ironman variant: no Grand Exchange, no trading outside a group. */
   private final boolean ironman;
   /** False only for Ultimate Ironman, who can't use a bank. */
   private final boolean bankAllowed;
   /** True for Hardcore Ironman and Hardcore Group Ironman (a dangerous death loses Hardcore status). */
   private final boolean hardcore;
   /** True for the group Ironman modes (they may trade within their group). */
   private final boolean group;

   /** The mode for a raw varbit value; unknown values count as a normal account. */
   public static AccountMode fromVarbit(final int value) {
      for (final AccountMode mode : values()) {
         if (mode.varbitValue == value) {
            return mode;
         }
      }
      return MAIN;
   }
}
