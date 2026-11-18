package com.kamrant.runegpt.ui;

import com.kamrant.runegpt.model.AccountMode;

import java.awt.Color;
import net.runelite.client.ui.ColorScheme;

/** Shared palette and UI label constants for the sidebar panel and in-game overlays. */
public final class UiTheme {

   private UiTheme() {
   }

   // Shared status & accent colours
   public static final Color ACCENT_CYAN = new Color(80, 220, 255);
   public static final Color ACCENT_CYAN_FILL = new Color(80, 220, 255, 55);
   public static final Color ACCENT_CYAN_OUTLINE = new Color(80, 220, 255, 120);

   public static final Color READY_GREEN = new Color(90, 200, 90);
   public static final Color EQUIP_YELLOW = new Color(240, 200, 60);
   public static final Color MISSING_RED = new Color(225, 85, 70);
   public static final Color BANK_ORANGE = new Color(235, 145, 70);

   public static final Color ACTIVE_STEP_ORANGE = new Color(255, 115, 50);
   public static final Color ACTIVE_STEP_BG = new Color(68, 36, 22);
   public static final Color ACTIVE_STEP_OUTLINE = new Color(255, 115, 50, 110);
   public static final Color ACTIVE_STEP_FILL = new Color(255, 145, 55, 45);
   public static final Color ACTIVE_STEP_BORDER = new Color(255, 145, 55, 200);

   public static final Color UPCOMING_GREY = new Color(165, 165, 165);
   public static final Color APPROX_GREY = new Color(185, 185, 185);
   public static final Color OVERLAY_MUTED = new Color(190, 190, 190);
   public static final Color SHADOW_OUTLINE = new Color(0, 0, 0, 180);

   public static final Color TEXT_PRIMARY = new Color(220, 220, 220);
   public static final Color CARD_BG = ColorScheme.DARK_GRAY_COLOR;
   public static final Color CARD_HOVER = ColorScheme.DARK_GRAY_HOVER_COLOR;
   public static final Color SUBCARD_BG = new Color(36, 36, 36);
   public static final Color TRACKED_BG = new Color(28, 40, 46);
   public static final Color USER_BUBBLE_BG = new Color(74, 54, 22);
   public static final Color ERROR_BUBBLE_BG = new Color(84, 32, 32);
   public static final Color NOTICE_BUBBLE_BG = new Color(44, 40, 28);
   public static final Color CHIP_SELECTED_BG = new Color(110, 72, 14);

   // Shared UI labels & tooltips
   public static final String BTN_ASK = "Ask";
   public static final String BTN_GO = "Go";
   public static final String BTN_STOP = "Stop";
   public static final String BTN_EDIT_GUIDE = "Edit guide";
   public static final String BTN_TRACK_GUIDE = "Track guide";
   public static final String BTN_START_TASK = "▶ Start task";
   public static final String BTN_DONE_NEXT = "Done, next step";
   public static final String BTN_WIKI_GUIDE = "Wiki guide";
   public static final String BTN_OPEN_WIKI = "Open on wiki";
   public static final String BTN_MONSTER_WIKI = "Monster wiki";
   public static final String BTN_PREV_STEP = "◀ Prev";
   public static final String BTN_NEXT_STEP = "Done · Next ▶";
   public static final String BTN_ALL_DONE = "All done ✓";

   public static final String TIP_TRACK_GUIDE = "Live checklist, in-game overlay, map marker and highlights";
   public static final String TIP_STOP_TRACKING = "Stop tracking this guide and remove markers";

   public static final String LBL_WEAR = "Wear";
   public static final String LBL_BRING = "Bring";
   public static final String LBL_STEPS = "Steps";
   public static final String LBL_GO_TO = "Go to";
   public static final String LBL_FIND = "Find";
   public static final String LBL_READY = "Ready";
   public static final String LBL_THINKING = "Thinking";
   public static final String LBL_CHECKING_WIKI = "Checking the OSRS Wiki";

   public static final String HDR_QUEST_WIKI = "Quest helper · OSRS Wiki";
   public static final String HDR_QUEST = "Quest helper";
   public static final String HDR_TASK_WIKI = "Task helper · OSRS Wiki";
   public static final String HDR_TASK = "Task helper";
   public static final String HDR_GEAR_WIKI = "Gear optimizer · OSRS Wiki";
   public static final String HDR_GEAR = "Gear optimizer";

   public static final String NOTE_OPEN_BANK_ITEMS = "Open your bank once so I can check it too.";
   public static final String NOTE_OPEN_BANK_GEAR = "Open your bank once so I can include banked gear.";
   public static final String NOTE_LOGIN_SUGGESTIONS = "Log in to get suggestions based on your stats.";
   public static final String NOTE_ALL_QUESTS_DONE = "All quests completed! Ask about any quest below.";
   /** Added to answers about deadly content for Hardcore accounts when the AI didn't include its own warning. */
   public static final String NOTE_HARDCORE_RISK = "**Hardcore risk:** this involves combat that can kill you. "
      + "Bring food and an emergency teleport, and leave before your supplies run out.";

   /** Plain-text version for the task helper's tip line. */
   public static final String TIP_HARDCORE_RISK = "Hardcore risk: bring food and an emergency teleport, and leave "
      + "early if supplies run low.";

   /** One-line note above the guide sections for Ironman accounts; null for a normal account. */
   public static String accountNote(final AccountMode mode) {
      switch (mode) {
         case IRONMAN:
            return "Ironman: advice skips the GE and trading.";
         case ULTIMATE_IRONMAN:
            return "Ultimate Ironman: advice skips the GE, trading and banking.";
         case HARDCORE_IRONMAN:
            return "Hardcore Ironman: advice skips the GE and trading, and warns about deadly content.";
         case GROUP_IRONMAN:
         case UNRANKED_GROUP_IRONMAN:
            return mode.getDisplayName() + ": advice skips the GE; trades stay in your group.";
         case HARDCORE_GROUP_IRONMAN:
            return "Hardcore Group Ironman: advice skips the GE and warns about deadly content.";
         default:
            return null;
      }
   }

   public static final String NOTE_ALL_SKILLS_MAXED = "All skills are 99! Ask about post-99 XP or pet hunting below.";
}
