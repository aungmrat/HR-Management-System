package hrms.component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Locale;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * Turns a {@code JComboBox<String>} into a type-to-search (autocomplete) combo:
 * while the user types, the dropdown is re-filtered with a case-insensitive
 * <em>contains</em> match. Pure Swing — no extra dependency.
 *
 * <p>Usage: fill the combo first (or later, from an async load), then call
 * {@link #attach(JComboBox, List)}. The helper stores a snapshot of all labels;
 * selection resolution stays with the caller (see
 * {@link #idForLabel(Map, String)}).</p>
 */
public final class AutoCompleteCombo {

    /** Marks a combo as attached so {@link #attach} is idempotent. */
    private static final String ATTACHED_KEY = "hrms.autocomplete";

    private AutoCompleteCombo() {
    }

    /**
     * Makes the combo editable and filters its items while typing.
     *
     * @param combo     combo to enhance (already populated)
     * @param allLabels the complete label list the combo was filled with
     */
    public static void attach(JComboBox<String> combo, List<String> allLabels) {
        if (combo.getClientProperty(ATTACHED_KEY) != null) {
            return;
        }
        combo.putClientProperty(ATTACHED_KEY, Boolean.TRUE);
        combo.setEditable(true);

        List<String> labels = List.copyOf(allLabels);
        pinStableSize(combo, labels);
        JTextField editor = editor(combo);
        if (editor == null) {
            return;
        }

        // Guards re-entrant document events fired while the model/editor is
        // being rewritten (setModel / setText) so typing is not filtered twice.
        final boolean[] guard = {false};
        // Coalesces bursts of document events into one deferred refilter.
        final boolean[] pending = {false};

        DocumentListener filterOnType = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                schedule();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                schedule();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                schedule();
            }

            private void schedule() {
                if (guard[0] || pending[0]) {
                    return;
                }
                pending[0] = true;
                // The model must never be mutated from inside a document
                // notification (AbstractDocument rejects it — "Attempt to
                // mutate in notification"), so refilter after the edit lands.
                javax.swing.SwingUtilities.invokeLater(() -> {
                    pending[0] = false;
                    refilter();
                });
            }

            private void refilter() {
                if (guard[0]) {
                    return;
                }
                String typed = editor.getText();
                // Selection made via popup/arrow keys echoes into the editor;
                // that is not typing — leave the combo alone.
                Object selected = combo.getSelectedItem();
                if (selected != null && typed.equals(selected.toString())) {
                    return;
                }
                guard[0] = true;
                try {
                    List<String> matches = filter(labels, typed);
                    // Replacing the model triggers a revalidate that re-measures
                    // the whole dialog — skip it when the matches did not change.
                    if (!sameItems(combo, matches)) {
                        combo.setModel(new DefaultComboBoxModel<>(matches.toArray(String[]::new)));
                        if (!typed.equals(editor.getText())) {
                            editor.setText(typed);
                        }
                    }
                    combo.setSelectedItem(typed);
                    if (!typed.isEmpty() && !matches.isEmpty()) {
                        combo.showPopup();
                    } else {
                        combo.hidePopup();
                    }
                } finally {
                    guard[0] = false;
                }
            }
        };
        editor.getDocument().addDocumentListener(filterOnType);
    }

    /**
     * Locks the combo's preferred width so filtering/typing can never resize
     * the field in the dialog's layout:
     * <ul>
     *   <li>{@code setPrototypeDisplayValue(widest)} — {@code getDisplaySize()}
     *       then measures only the prototype instead of the (changing) model
     *       items ({@code BasicComboBoxUI:1425-1431});</li>
     *   <li>{@code editor.setColumns(k)} — {@code JTextField} preferred width
     *       becomes {@code columns x charWidth} ({@code JTextField:440-443}),
     *       independent of the typed text (with columns == 0 it follows the
     *       text length — {@code BasicComboBoxUI:1473-1477}).</li>
     * </ul>
     * On every revalidate (our per-keystroke {@code setModel} causes one)
     * GridBag re-queries these sizes, so they must be constant.
     */
    private static void pinStableSize(JComboBox<String> combo, List<String> labels) {
        if (labels.isEmpty()) {
            return;
        }
        String widest = null;
        int widestPx = -1;
        for (String label : labels) {
            int px = measureLabel(combo, label);
            if (px > widestPx) {
                widestPx = px;
                widest = label;
            }
        }
        combo.setPrototypeDisplayValue(widest);
        JTextField editor = editor(combo);
        if (editor != null && widestPx > 0) {
            int charWidth = Math.max(1, editor.getFontMetrics(editor.getFont()).charWidth('M'));
            editor.setColumns((widestPx + charWidth - 1) / charWidth + 2);
        }
    }

    /** Pixel width of a label as the combo would render it. */
    private static int measureLabel(JComboBox<String> combo, String label) {
        javax.swing.JList<String> list = new javax.swing.JList<>();
        list.setFont(combo.getFont());
        java.awt.Component c = combo.getRenderer()
                .getListCellRendererComponent(list, label, -1, false, false);
        return c.getPreferredSize().width;
    }

    /** Whether the combo's current items are exactly the given matches. */
    private static boolean sameItems(JComboBox<String> combo, List<String> matches) {
        javax.swing.ComboBoxModel<String> model = combo.getModel();
        if (model.getSize() != matches.size()) {
            return false;
        }
        for (int i = 0; i < matches.size(); i++) {
            if (!matches.get(i).equals(model.getElementAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Case-insensitive contains filter; empty/blank text returns every label.
     */
    public static List<String> filter(List<String> labels, String text) {
        String term = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
        if (term.isEmpty()) {
            return new ArrayList<>(labels);
        }
        List<String> matches = new ArrayList<>();
        for (String label : labels) {
            if (label != null && label.toLowerCase(Locale.ROOT).contains(term)) {
                matches.add(label);
            }
        }
        return matches;
    }

    /**
     * Resolves the id behind a (possibly typed) label with an exact,
     * case-insensitive match; {@code null} when the text is not a known label.
     * With an editable combo the typed free text must go through this — never
     * trust {@code getSelectedItem()} directly.
     */
    public static Long idForLabel(Map<String, Long> idByLabel, String text) {
        if (text == null || idByLabel == null || idByLabel.isEmpty()) {
            return null;
        }
        String wanted = text.trim();
        if (wanted.isEmpty()) {
            return null;
        }
        for (Map.Entry<String, Long> entry : idByLabel.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(wanted)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static JTextField editor(JComboBox<String> combo) {
        Object component = combo.getEditor().getEditorComponent();
        return component instanceof JTextField field ? field : null;
    }
}
