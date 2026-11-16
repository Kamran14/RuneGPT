package com.kamrant.runegpt.ui;

import com.kamrant.runegpt.guides.Guide;
import com.kamrant.runegpt.guides.GuideCatalog;
import com.kamrant.runegpt.guides.GuideCategory;
import com.kamrant.runegpt.guides.GuideOption;
import com.kamrant.runegpt.guides.GuideStore;
import com.kamrant.runegpt.service.PlayerSnapshot;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * Accordion of guide categories (Quest Helper, Combat & Slayer, ...). One section is open at a time.
 * Each guide card asks in one click; expanding it shows optional quick choices (attack style, budget...)
 * and an Edit button. Each section ends with an "Other" box for the player's own interest.
 */
public class GuidesView extends JPanel {

   private static final Color CARD_BG = ColorScheme.DARK_GRAY_COLOR;
   private static final Color CARD_HOVER = ColorScheme.DARK_GRAY_HOVER_COLOR;

   private final GuideStore store;
   /** (text shown in chat, prompt sent to the LLM) */
   private final BiConsumer<String, String> onAsk;
   /** Quest name for "Continue quest" guides, which open the quest step assistant instead of plain chat. */
   private final BiConsumer<String, Map<String, String>> onQuestStep;
   /** (quest name, prompt) for "Requirements for quest", which checks the quest exists and adds wiki data. */
   private final BiConsumer<String, String> onQuestRequirements;
   /** Monster/boss name for "Best gear I own for ...", which runs the gear optimizer from owned items. */
   private final BiConsumer<String, Map<String, String>> onGearOptimize;

   private final Map<GuideCategory, CollapsibleSection> sections = new EnumMap<>(GuideCategory.class);
   // Session-only UI state: quick choices per guide and which cards are expanded.
   private final Map<String, Map<String, String>> picks = new HashMap<>();
   private final Set<String> expandedCards = new HashSet<>();

   private PlayerSnapshot snapshot = PlayerSnapshot.LOGGED_OUT;

   public GuidesView(final GuideStore store, final BiConsumer<String, String> onAsk,
      final BiConsumer<String, Map<String, String>> onQuestStep, final BiConsumer<String, String> onQuestRequirements,
      final BiConsumer<String, Map<String, String>> onGearOptimize) {
      this.store = store;
      this.onAsk = onAsk;
      this.onQuestStep = onQuestStep;
      this.onQuestRequirements = onQuestRequirements;
      this.onGearOptimize = onGearOptimize;
      setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
      setOpaque(false);

      for (final GuideCategory category : GuideCategory.values()) {
         final CollapsibleSection section = new CollapsibleSection(category.getSectionName(), () -> toggle(category));
         section.setAlignmentX(Component.LEFT_ALIGNMENT);
         sections.put(category, section);
         add(section);
         add(Box.createVerticalStrut(6));
      }
      render();
   }

   public void setSnapshot(final PlayerSnapshot snapshot) {
      this.snapshot = snapshot;
      render();
   }

   private void toggle(final GuideCategory category) {
      final CollapsibleSection section = sections.get(category);
      final boolean open = !section.isExpanded();
      sections.forEach((c, s) -> s.setExpanded(open && c == category));
      render();
      if (open) {
         // Bring the opened section's header to the top of the visible area.
         javax.swing.SwingUtilities.invokeLater(() -> section.scrollRectToVisible(
            new java.awt.Rectangle(0, 0, section.getWidth(), Math.max(section.getHeight(), 1))));
      }
   }

   private void render() {
      for (final GuideCategory category : GuideCategory.values()) {
         final CollapsibleSection section = sections.get(category);
         if (category == GuideCategory.QUESTS && !snapshot.getQuestsInProgress().isEmpty()) {
            section.setBadge(snapshot.getQuestsInProgress().size() + " active");
         } else {
            section.setBadge(null);
         }
         // Only build the open section's cards.
         if (section.isExpanded()) {
            section.setBody(sectionBody(category));
         }
      }
      revalidate();
      repaint();
   }

   private JComponent sectionBody(final GuideCategory category) {
      final JPanel list = new JPanel();
      list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
      list.setOpaque(false);
      if (!snapshot.isLoggedIn()) {
         list.add(note("Log in to get suggestions based on your stats."));
      }
      for (final Guide guide : store.guides(category, snapshot)) {
         list.add(guideCard(guide));
         list.add(Box.createVerticalStrut(4));
      }
      list.add(otherCard(category));
      return list;
   }

   private JComponent guideCard(final Guide guide) {
      final boolean expanded = expandedCards.contains(guide.getId());
      final Map<String, String> guidePicks = picks.computeIfAbsent(guide.getId(), k -> new HashMap<>());
      final boolean isQuestStep = guide.getId().startsWith(GuideCatalog.QUEST_STEP_PREFIX);
      final boolean isGearOptimize = guide.getId().startsWith(GuideCatalog.GEAR_OPTIMIZER_PREFIX);
      final boolean isTool = isQuestStep || isGearOptimize;

      final RoundedPanel card = new RoundedPanel(new BorderLayout(0, 6), CARD_BG, 10);
      card.setBorder(new EmptyBorder(7, 8, 7, 7));
      card.setAlignmentX(Component.LEFT_ALIGNMENT);
      if (guide.isCustomized()) {
         card.setOutline(ColorScheme.PROGRESS_COMPLETE_COLOR.darker());
      }

      // Top row: title (click to expand) + one-click Ask / Go.
      final HtmlPane title = new HtmlPane(Color.WHITE, FontManager.getRunescapeBoldFont());
      title.setPlainText((isTool ? "▶ " : "") + guide.title() + (guide.isCustomized() ? "  ✎" : ""));
      title.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      title.setToolTipText(expanded ? "Hide options" : "Show options");

      final JButton ask = pillButton(isTool ? "Go" : "Ask");
      final Runnable doAsk = () -> runGuide(guide, guidePicks);
      ask.addActionListener(e -> doAsk.run());

      final JPanel top = new JPanel(new BorderLayout(6, 0));
      top.setOpaque(false);
      top.add(title, BorderLayout.CENTER);
      final JPanel askWrap = new JPanel(new BorderLayout());
      askWrap.setOpaque(false);
      askWrap.add(ask, BorderLayout.NORTH);
      top.add(askWrap, BorderLayout.EAST);
      card.add(top, BorderLayout.NORTH);

      final MouseAdapter expandToggle = new MouseAdapter() {
         @Override
         public void mouseClicked(final MouseEvent e) {
            if (!expandedCards.remove(guide.getId())) {
               expandedCards.add(guide.getId());
            }
            render();
         }

         @Override
         public void mouseEntered(final MouseEvent e) {
            card.setBackground(CARD_HOVER);
            card.repaint();
         }

         @Override
         public void mouseExited(final MouseEvent e) {
            card.setBackground(CARD_BG);
            card.repaint();
         }
      };
      title.addMouseListener(expandToggle);

      if (expanded) {
         final JPanel details = new JPanel();
         details.setLayout(new BoxLayout(details, BoxLayout.Y_AXIS));
         details.setOpaque(false);

         final HtmlPane preview = new HtmlPane(ColorScheme.LIGHT_GRAY_COLOR, FontManager.getRunescapeSmallFont());
         preview.setPlainText(guide.prompt());
         preview.setAlignmentX(Component.LEFT_ALIGNMENT);
         details.add(preview);

         for (final GuideOption option : guide.getOptions()) {
            details.add(Box.createVerticalStrut(6));
            final ChipGroup chips = new ChipGroup(option, guidePicks.get(option.getLabel()), choice -> {
               if (choice == null) {
                  guidePicks.remove(option.getLabel());
               } else {
                  guidePicks.put(option.getLabel(), choice);
               }
            });
            chips.setAlignmentX(Component.LEFT_ALIGNMENT);
            details.add(chips);
         }

         details.add(Box.createVerticalStrut(8));
         final JButton edit = pillButton("Edit guide");
         edit.addActionListener(e -> edit(guide));
         final String bottomLabel = isQuestStep ? "Find my next step"
            : isGearOptimize ? "Find best gear" : "Ask";
         final JButton askBottom = pillButton(bottomLabel);
         askBottom.addActionListener(e -> doAsk.run());
         final JPanel buttons = new JPanel(new GridLayout(1, 2, 4, 0));
         buttons.setOpaque(false);
         buttons.setAlignmentX(Component.LEFT_ALIGNMENT);
         buttons.add(edit);
         buttons.add(askBottom);
         details.add(buttons);
         card.add(details, BorderLayout.CENTER);
      }
      return card;
   }

   private void runGuide(final Guide guide, final Map<String, String> guidePicks) {
      if (guide.getId().startsWith(GuideCatalog.QUEST_STEP_PREFIX)) {
         onQuestStep.accept(guide.getFields().get("quest"), guidePicks);
      } else if (guide.getId().startsWith(GuideCatalog.GEAR_OPTIMIZER_PREFIX)) {
         final String target = guide.getFields().containsKey("monster")
            ? guide.getFields().get("monster")
            : guide.getFields().get("boss");
         onGearOptimize.accept(target, guidePicks);
      } else if (guide.getId().equals(GuideCatalog.QUEST_REQUIREMENTS_ID)) {
         onQuestRequirements.accept(guide.getFields().get("quest"), guide.prompt(guidePicks));
      } else {
         onAsk.accept(guide.title(), guide.prompt(guidePicks));
      }
   }

   private JComponent otherCard(final GuideCategory category) {
      final RoundedPanel card = new RoundedPanel(new BorderLayout(0, 4), CARD_BG, 10);
      card.setBorder(new EmptyBorder(7, 8, 7, 7));
      card.setAlignmentX(Component.LEFT_ALIGNMENT);

      final JLabel title = new JLabel("Something else?");
      title.setFont(FontManager.getRunescapeBoldFont());
      title.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

      final JTextField field = new JTextField();
      field.setToolTipText("e.g. " + exampleInterest(category));
      field.setBackground(ColorScheme.DARKER_GRAY_COLOR);
      field.setForeground(Color.WHITE);
      field.setCaretColor(Color.WHITE);
      field.setBorder(BorderFactory.createCompoundBorder(
         BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR.darker()), new EmptyBorder(3, 4, 3, 4)));
      final JButton ask = pillButton("Ask");
      final Runnable submit = () -> {
         final String interest = field.getText().trim();
         if (!interest.isEmpty()) {
            field.setText("");
            onAsk.accept(interest, GuideCatalog.otherPrompt(category, interest));
         }
      };
      ask.addActionListener(e -> submit.run());
      field.addActionListener(e -> submit.run());

      final JLabel hint = new JLabel("e.g. " + exampleInterest(category));
      hint.setFont(FontManager.getRunescapeSmallFont());
      hint.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);

      final JPanel inputRow = new JPanel(new BorderLayout(4, 0));
      inputRow.setOpaque(false);
      inputRow.add(field, BorderLayout.CENTER);
      inputRow.add(ask, BorderLayout.EAST);

      card.add(title, BorderLayout.NORTH);
      card.add(inputRow, BorderLayout.CENTER);
      card.add(hint, BorderLayout.SOUTH);
      return card;
   }

   private void edit(final Guide original) {
      final Guide working = original.copy();
      switch (GuideEditDialog.show(this, working)) {
         case SAVE:
            store.save(working);
            render();
            break;
         case SAVE_AND_ASK:
            store.save(working);
            render();
            runGuide(working, picks.getOrDefault(working.getId(), new HashMap<>()));
            break;
         case RESET:
            store.reset(original);
            render();
            break;
         default:
            break;
      }
   }

   private static String exampleInterest(final GuideCategory category) {
      switch (category) {
         case QUESTS:
            return "unlock Fairy rings";
         case MONSTERS:
            return "best place for Slayer XP";
         case BOSSES:
            return "first raid tips";
         case SKILLING:
            return "train Herblore cheaply";
         case MONEY:
            return "flipping on the GE";
         default:
            return "Tempoross rewards";
      }
   }

   private static JComponent note(final String text) {
      final HtmlPane l = new HtmlPane(ColorScheme.MEDIUM_GRAY_COLOR, FontManager.getRunescapeSmallFont());
      l.setPlainText(text);
      l.setBorder(new EmptyBorder(0, 2, 6, 2));
      l.setAlignmentX(Component.LEFT_ALIGNMENT);
      return l;
   }

   static JButton pillButton(final String text) {
      final JButton b = new JButton(text);
      b.setFont(FontManager.getRunescapeSmallFont());
      b.setFocusable(false);
      b.setMargin(new Insets(2, 8, 2, 8));
      return b;
   }
}
