package com.kamrant.runegpt.ui;

import com.kamrant.runegpt.guides.GuideOption;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * Optional single-choice chips: "Attack style  [Melee] [Ranged] [Magic]".
 * Clicking the selected chip again clears it (no preference).
 */
public class ChipGroup extends JPanel {

   private static final Color CHIP_BG = ColorScheme.DARK_GRAY_COLOR;
   private static final Color CHIP_HOVER = ColorScheme.DARK_GRAY_HOVER_COLOR;
   private static final Color CHIP_SELECTED = new Color(110, 72, 14);

   private final List<RoundedPanel> chips = new ArrayList<>();
   private final List<String> choices;
   private String selected;

   public ChipGroup(final GuideOption option, final String initial, final Consumer<String> onChange) {
      super(new BorderLayout(0, 3));
      setOpaque(false);
      this.choices = option.getChoices();
      this.selected = initial;

      final JLabel label = new JLabel(option.getLabel() + "  (optional)");
      label.setFont(FontManager.getRunescapeSmallFont());
      label.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
      add(label, BorderLayout.NORTH);

      final JPanel row = new JPanel(new GridLayout(1, choices.size(), 4, 0));
      row.setOpaque(false);
      for (final String choice : choices) {
         final RoundedPanel chip = new RoundedPanel(new BorderLayout(), CHIP_BG, 10);
         chip.setBorder(new EmptyBorder(3, 2, 3, 2));
         chip.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
         final JLabel text = new JLabel(choice, SwingConstants.CENTER);
         text.setFont(FontManager.getRunescapeSmallFont());
         chip.add(text, BorderLayout.CENTER);
         chip.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(final MouseEvent e) {
               selected = choice.equals(selected) ? null : choice;
               restyle();
               onChange.accept(selected);
            }

            @Override
            public void mouseEntered(final MouseEvent e) {
               if (!choice.equals(selected)) {
                  chip.setBackground(CHIP_HOVER);
               }
            }

            @Override
            public void mouseExited(final MouseEvent e) {
               restyle();
            }
         });
         chips.add(chip);
         row.add(chip);
      }
      add(row, BorderLayout.CENTER);
      restyle();
   }

   private void restyle() {
      for (int i = 0; i < chips.size(); i++) {
         final boolean on = choices.get(i).equals(selected);
         final RoundedPanel chip = chips.get(i);
         chip.setBackground(on ? CHIP_SELECTED : CHIP_BG);
         chip.setOutline(on ? ColorScheme.BRAND_ORANGE : ColorScheme.MEDIUM_GRAY_COLOR.darker());
         ((JLabel) chip.getComponent(0)).setForeground(on ? Color.WHITE : ColorScheme.LIGHT_GRAY_COLOR);
      }
   }
}
