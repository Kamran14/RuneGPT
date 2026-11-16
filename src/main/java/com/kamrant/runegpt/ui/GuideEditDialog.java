package com.kamrant.runegpt.ui;

import com.kamrant.runegpt.guides.Guide;
import com.kamrant.runegpt.service.TextUtils;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * Modal editor for a guide. The simple part is the fields (e.g. Monster: blue dragons); below them the
 * player can rewrite the title and the full prompt sent to the LLM. A live preview shows the result.
 */
public final class GuideEditDialog {

   private GuideEditDialog() {
   }

   /** Shows the dialog. On SAVE / SAVE_AND_ASK the guide has been updated in place. */
   public static GuideEditAction show(final Component parent, final Guide guide) {
      final Window owner = SwingUtilities.getWindowAncestor(parent);
      final JDialog dialog = new JDialog(owner, "Edit guide", JDialog.ModalityType.APPLICATION_MODAL);
      final GuideEditAction[] result = { GuideEditAction.CANCEL };

      final JPanel form = new JPanel(new GridBagLayout());
      form.setBorder(BorderFactory.createEmptyBorder(12, 12, 6, 12));
      final GridBagConstraints c = new GridBagConstraints();
      c.gridx = 0;
      c.gridy = 0;
      c.weightx = 1;
      c.fill = GridBagConstraints.HORIZONTAL;
      c.insets = new Insets(0, 0, 4, 0);

      final JLabel preview = new JLabel();
      preview.setFont(FontManager.getRunescapeBoldFont());
      preview.setForeground(ColorScheme.BRAND_ORANGE);
      form.add(preview, c);
      c.gridy++;
      c.insets = new Insets(0, 0, 12, 0);
      form.add(hint("This is what the guide will be called."), c);

      // Fields
      final Map<String, JTextField> fieldInputs = new LinkedHashMap<>();
      if (!guide.getFields().isEmpty()) {
         c.gridy++;
         c.insets = new Insets(0, 0, 6, 0);
         form.add(section("Details"), c);
         for (final Map.Entry<String, String> e : guide.getFields().entrySet()) {
            final JTextField input = new JTextField(e.getValue());
            fieldInputs.put(e.getKey(), input);
            c.gridy++;
            c.insets = new Insets(0, 0, 2, 0);
            form.add(label(TextUtils.capitalize(e.getKey())), c);
            c.gridy++;
            c.insets = new Insets(0, 0, 8, 0);
            form.add(input, c);
         }
      }

      // Advanced: templates
      c.gridy++;
      c.insets = new Insets(8, 0, 2, 0);
      form.add(section("Customize the question"), c);
      c.gridy++;
      c.insets = new Insets(0, 0, 8, 0);
      form.add(hint(fieldInputs.isEmpty()
         ? "Change the wording to ask exactly what you want."
         : "Words in {braces} are replaced by the details above, e.g. {" + fieldInputs.keySet().iterator().next() + "}."), c);

      final JTextField titleInput = new JTextField(guide.getTitleTemplate());
      c.gridy++;
      c.insets = new Insets(0, 0, 2, 0);
      form.add(label("Title"), c);
      c.gridy++;
      c.insets = new Insets(0, 0, 8, 0);
      form.add(titleInput, c);

      final JTextArea promptInput = new JTextArea(guide.getPromptTemplate(), 6, 32);
      promptInput.setLineWrap(true);
      promptInput.setWrapStyleWord(true);
      c.gridy++;
      c.insets = new Insets(0, 0, 2, 0);
      form.add(label("Question sent to the AI"), c);
      c.gridy++;
      c.insets = new Insets(0, 0, 8, 0);
      form.add(new JScrollPane(promptInput), c);

      final Runnable refreshPreview = () -> preview.setText(Guide.fill(titleInput.getText(), values(fieldInputs)));
      refreshPreview.run();
      listen(titleInput, refreshPreview);
      fieldInputs.values().forEach(f -> listen(f, refreshPreview));

      // Buttons
      final JButton reset = new JButton("Reset to default");
      reset.setEnabled(guide.isCustomized());
      final JButton cancel = new JButton("Cancel");
      final JButton save = new JButton("Save");
      final JButton saveAsk = new JButton("Save & ask");

      final Runnable applyEdits = () -> guide.apply(titleInput.getText().trim(), promptInput.getText().trim(),
         values(fieldInputs));
      reset.addActionListener(e -> {
         result[0] = GuideEditAction.RESET;
         dialog.dispose();
      });
      cancel.addActionListener(e -> dialog.dispose());
      save.addActionListener(e -> {
         applyEdits.run();
         result[0] = GuideEditAction.SAVE;
         dialog.dispose();
      });
      saveAsk.addActionListener(e -> {
         applyEdits.run();
         result[0] = GuideEditAction.SAVE_AND_ASK;
         dialog.dispose();
      });

      final JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
      left.add(reset);
      final JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
      right.add(cancel);
      right.add(save);
      right.add(saveAsk);
      final JPanel buttons = new JPanel(new BorderLayout());
      buttons.setBorder(BorderFactory.createEmptyBorder(6, 12, 12, 12));
      buttons.add(left, BorderLayout.WEST);
      buttons.add(right, BorderLayout.EAST);

      dialog.getContentPane().setLayout(new BorderLayout());
      dialog.getContentPane().add(form, BorderLayout.CENTER);
      dialog.getContentPane().add(buttons, BorderLayout.SOUTH);
      dialog.getRootPane().setDefaultButton(saveAsk);
      dialog.pack();
      dialog.setMinimumSize(new Dimension(420, dialog.getHeight()));
      dialog.setLocationRelativeTo(owner);
      dialog.setVisible(true); // blocks until closed
      return result[0];
   }

   private static Map<String, String> values(final Map<String, JTextField> inputs) {
      final Map<String, String> m = new LinkedHashMap<>();
      inputs.forEach((k, v) -> m.put(k, v.getText().trim()));
      return m;
   }

   private static void listen(final JTextComponent field, final Runnable onChange) {
      field.getDocument().addDocumentListener(new DocumentListener() {
         @Override
         public void insertUpdate(final DocumentEvent e) {
            onChange.run();
         }

         @Override
         public void removeUpdate(final DocumentEvent e) {
            onChange.run();
         }

         @Override
         public void changedUpdate(final DocumentEvent e) {
            onChange.run();
         }
      });
   }

   private static JLabel section(final String text) {
      final JLabel l = new JLabel(text);
      l.setFont(FontManager.getRunescapeBoldFont());
      l.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
      return l;
   }

   private static JLabel label(final String text) {
      final JLabel l = new JLabel(text);
      l.setFont(FontManager.getRunescapeSmallFont());
      l.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
      return l;
   }

   private static JLabel hint(final String text) {
      final JLabel l = new JLabel("<html><body style='width:300px'>" + MarkdownRenderer.escape(text) + "</body></html>");
      l.setFont(FontManager.getRunescapeSmallFont());
      l.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
      return l;
   }
}
