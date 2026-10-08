/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/GUIForms/JDialog.java to edit this template
 */
package hrms.ui.staff_management;

import hrms.component.AutoCompleteCombo;
import hrms.exception.AuthorizationException;
import hrms.exception.ErrorHandler;
import hrms.exception.ValidationException;
import hrms.model.LanguageProficiency;
import hrms.model.LanguageSkill;
import hrms.repository.LanguageSkillRepository;
import hrms.security.Permissions;
import hrms.security.SecurityService;
import hrms.security.SessionContext;
import hrms.util.UiThread;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author aungmrat
 */
public class LanguageSkillDialog extends javax.swing.JDialog {
  
  private static final java.util.logging.Logger logger = java.util.logging.Logger.getLogger(LanguageSkillDialog.class.getName());

  /** null = create mode. */
  private final LanguageSkill editing;

  /**
   * Label (CODE — Name) → staff id for the generated {@code JComboBox<String>}
   * staff combo; the generated type parameters stay untouched.
   */
  private final Map<String, Long> staffIdByLabel = new HashMap<>();

  /**
   * Creates new form LanguageSkillDialog
   */
  public LanguageSkillDialog(java.awt.Frame parent, boolean modal) {
    this(parent, modal, null);
  }

  /** Create when {@code existing} is null; edit when non-null. */
  public LanguageSkillDialog(java.awt.Frame parent, boolean modal, LanguageSkill existing) {
    super(parent, modal);
    initComponents();
    this.editing = existing;
    setTitle(existing == null ? "New Language Skill" : "Edit Language Skill");
    if (existing != null) {
      textLanguage.setText(existing.getLanguageName());
    }
    setupActions();
    loadCombos();
  }

  /**
   * The form's GEN block wires only comboStaff, so Save/Cancel are connected
   * here — same pattern as TeamMemberDialog.
   */
  private void setupActions() {
    btnCancel.addActionListener(e -> dispose());
    btnSave.addActionListener(e -> {
      try {
        saveAndClose();
      } catch (AuthorizationException ex) {
        ErrorHandler.handle(this, ex);
      }
    });
  }

  /** Fills the proficiency models synchronously, then loads staff async. */
  private void loadCombos() {
    String[] levels = levelLabels();
    comboSpeaking.setModel(new javax.swing.DefaultComboBoxModel<>(levels));
    comboListening.setModel(new javax.swing.DefaultComboBoxModel<>(levels));
    comboWriting.setModel(new javax.swing.DefaultComboBoxModel<>(levels));
    comboReading.setModel(new javax.swing.DefaultComboBoxModel<>(levels));
    if (editing == null) {
      selectLevel(comboSpeaking, LanguageProficiency.NONE);
      selectLevel(comboListening, LanguageProficiency.NONE);
      selectLevel(comboWriting, LanguageProficiency.NONE);
      selectLevel(comboReading, LanguageProficiency.NONE);
    } else {
      selectLevel(comboSpeaking, editing.getSpeaking());
      selectLevel(comboListening, editing.getListening());
      selectLevel(comboWriting, editing.getWriting());
      selectLevel(comboReading, editing.getReading());
    }

    UiThread.executeAsync("Load language skill combos",
        LanguageSkillRepository::listStaffOptions,
        options -> {
          List<String> labels = new ArrayList<>();
          comboStaff.removeAllItems();
          staffIdByLabel.clear();
          for (LanguageSkillRepository.StaffOption option : options) {
            String label = option.label();
            labels.add(label);
            staffIdByLabel.put(label, option.id());
            comboStaff.addItem(label);
          }
          if (editing != null) {
            long staffId = editing.getStaffId();
            options.stream()
                .filter(o -> o.id() == staffId)
                .findFirst()
                .ifPresent(o -> comboStaff.setSelectedItem(o.label()));
            comboStaff.setEnabled(false);
          }
          AutoCompleteCombo.attach(comboStaff, labels);
        });
  }

  private static String[] levelLabels() {
    LanguageProficiency[] values = LanguageProficiency.values();
    String[] labels = new String[values.length];
    for (int i = 0; i < values.length; i++) {
      labels[i] = values[i].getLabel();
    }
    return labels;
  }

  private static void selectLevel(javax.swing.JComboBox<String> combo, LanguageProficiency value) {
    LanguageProficiency level = value == null ? LanguageProficiency.NONE : value;
    combo.setSelectedItem(level.getLabel());
  }

  private LanguageProficiency selectedLevel(javax.swing.JComboBox<String> combo) {
    return LanguageProficiency.fromLabel(String.valueOf(combo.getSelectedItem()));
  }

  /** Staff id behind the selected/typed label; null when no known label. */
  private Long selectedStaffId() {
    Object selected = comboStaff.getSelectedItem();
    return AutoCompleteCombo.idForLabel(staffIdByLabel,
        selected == null ? null : selected.toString());
  }

  private void saveAndClose() {
    try {
      SecurityService.require(Permissions.STAFF_UPDATE);

      Long staffId = editing != null ? editing.getStaffId() : selectedStaffId();
      String language = textLanguage.getText() == null ? "" : textLanguage.getText().trim();
      LanguageProficiency speaking = selectedLevel(comboSpeaking);
      LanguageProficiency listening = selectedLevel(comboListening);
      LanguageProficiency writing = selectedLevel(comboWriting);
      LanguageProficiency reading = selectedLevel(comboReading);

      List<String> errors = new ArrayList<>();
      String staffText = String.valueOf(comboStaff.getSelectedItem()).trim();
      if (staffText.isEmpty()) {
        errors.add("Staff is required.");
      } else if (staffId == null) {
        errors.add("Select staff from the list.");
      }
      if (language.isEmpty()) {
        errors.add("Language is required.");
      } else if (language.length() > 50) {
        errors.add("Language must be at most 50 characters.");
      }
      if (speaking == LanguageProficiency.NONE && listening == LanguageProficiency.NONE
          && writing == LanguageProficiency.NONE && reading == LanguageProficiency.NONE) {
        errors.add("At least one level must be above None.");
      }
      if (!errors.isEmpty()) {
        throw new ValidationException(errors);
      }

      Long userId = SessionContext.isAuthenticated() ? SessionContext.currentUserId() : null;
      boolean isEdit = editing != null;
      LanguageSkill skill = isEdit ? editing : new LanguageSkill();
      if (!isEdit) {
        skill.setStaffId(staffId);
        skill.setCreatedBy(userId == null ? 0L : userId);
      }
      skill.setLanguageName(language);
      skill.setSpeaking(speaking);
      skill.setListening(listening);
      skill.setWriting(writing);
      skill.setReading(reading);

      btnSave.setEnabled(false);
      UiThread.executeAsync(isEdit ? "Update language skill" : "Save language skill",
          () -> {
            if (LanguageSkillRepository.existsForStaff(
                staffId, language, isEdit ? skill.getId() : null)) {
              throw new ValidationException(
                  "Language '" + language + "' already exists for this staff.");
            }
            if (isEdit) {
              LanguageSkillRepository.update(skill, userId == null ? 0L : userId);
            } else {
              skill.setId(LanguageSkillRepository.insert(skill));
            }
            return skill;
          },
          saved -> {
            logger.info("Language skill saved with id " + saved.getId()
                + " for staff " + saved.getStaffId());
            dispose();
          },
          error -> {
            btnSave.setEnabled(true);
            ErrorHandler.handle(this, error);
          });
    } catch (ValidationException e) {
      ErrorHandler.handle(this, e);
    }
  }

  /**
   * This method is called from within the constructor to initialize the form.
   * WARNING: Do NOT modify this code. The content of this method is always
   * regenerated by the Form Editor.
   */
  @SuppressWarnings("unchecked")
  // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
  private void initComponents() {
    java.awt.GridBagConstraints gridBagConstraints;

    footer = new javax.swing.JPanel();
    btnSave = new javax.swing.JButton();
    btnCancel = new javax.swing.JButton();
    body = new javax.swing.JPanel();
    labelStaff = new javax.swing.JLabel();
    comboStaff = new javax.swing.JComboBox<>();
    labelLanguage = new javax.swing.JLabel();
    textLanguage = new javax.swing.JTextField();
    labelSpeaking = new javax.swing.JLabel();
    comboSpeaking = new javax.swing.JComboBox<>();
    labelListening = new javax.swing.JLabel();
    comboListening = new javax.swing.JComboBox<>();
    labelWriting = new javax.swing.JLabel();
    comboWriting = new javax.swing.JComboBox<>();
    jLabel1 = new javax.swing.JLabel();
    comboReading = new javax.swing.JComboBox<>();

    setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);

    footer.setPreferredSize(new java.awt.Dimension(400, 60));
    footer.setLayout(new java.awt.GridBagLayout());

    btnSave.setText("Save");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
    gridBagConstraints.insets = new java.awt.Insets(19, 18, 14, 40);
    footer.add(btnSave, gridBagConstraints);

    btnCancel.setText("Cancel");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
    gridBagConstraints.insets = new java.awt.Insets(19, 190, 14, 0);
    footer.add(btnCancel, gridBagConstraints);

    getContentPane().add(footer, java.awt.BorderLayout.PAGE_END);

    body.setLayout(new java.awt.GridBagLayout());

    labelStaff.setText("Staff");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(8, 0, 7, 15);
    body.add(labelStaff, gridBagConstraints);

    comboStaff.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
    comboStaff.addActionListener(this::comboStaffActionPerformed);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(8, 0, 7, 0);
    body.add(comboStaff, gridBagConstraints);

    labelLanguage.setText("Language");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(8, 0, 7, 15);
    body.add(labelLanguage, gridBagConstraints);

    textLanguage.setPreferredSize(new java.awt.Dimension(200, 26));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(8, 0, 7, 0);
    body.add(textLanguage, gridBagConstraints);

    labelSpeaking.setText("Speaking");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(8, 0, 7, 15);
    body.add(labelSpeaking, gridBagConstraints);

    comboSpeaking.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(8, 0, 7, 0);
    body.add(comboSpeaking, gridBagConstraints);

    labelListening.setText("Listening");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(8, 0, 7, 15);
    body.add(labelListening, gridBagConstraints);

    comboListening.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(8, 0, 7, 0);
    body.add(comboListening, gridBagConstraints);

    labelWriting.setText("Writing");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(8, 0, 7, 15);
    body.add(labelWriting, gridBagConstraints);

    comboWriting.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(8, 0, 7, 0);
    body.add(comboWriting, gridBagConstraints);

    jLabel1.setText("Reading");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 5;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(8, 0, 7, 15);
    body.add(jLabel1, gridBagConstraints);

    comboReading.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 5;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(8, 0, 7, 0);
    body.add(comboReading, gridBagConstraints);

    getContentPane().add(body, java.awt.BorderLayout.CENTER);

    pack();
  }// </editor-fold>//GEN-END:initComponents

  private void comboStaffActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_comboStaffActionPerformed
    // TODO add your handling code here:
  }//GEN-LAST:event_comboStaffActionPerformed

  /**
   * @param args the command line arguments
   */
  public static void main(String args[]) {
    /* Set the Nimbus look and feel */
    //<editor-fold defaultstate="collapsed" desc=" Look and feel setting code (optional) ">
    /* If Nimbus (introduced in Java SE 6) is not available, stay with the default look and feel.
         * For details see http://download.oracle.com/javase/tutorial/uiswing/lookandfeel/plaf.html 
     */
    try {
      for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
        if ("Nimbus".equals(info.getName())) {
          javax.swing.UIManager.setLookAndFeel(info.getClassName());
          break;
        }
      }
    } catch (ReflectiveOperationException | javax.swing.UnsupportedLookAndFeelException ex) {
      logger.log(java.util.logging.Level.SEVERE, null, ex);
    }
    //</editor-fold>

    /* Create and display the dialog */
    java.awt.EventQueue.invokeLater(new Runnable() {
      @Override
      public void run() {
        LanguageSkillDialog dialog = new LanguageSkillDialog(new javax.swing.JFrame(), true);
        dialog.addWindowListener(new java.awt.event.WindowAdapter() {
          @Override
          public void windowClosing(java.awt.event.WindowEvent e) {
            System.exit(0);
          }
        });
        dialog.setVisible(true);
      }
    });
  }

  // Variables declaration - do not modify//GEN-BEGIN:variables
  private javax.swing.JPanel body;
  private javax.swing.JButton btnCancel;
  private javax.swing.JButton btnSave;
  private javax.swing.JComboBox<String> comboListening;
  private javax.swing.JComboBox<String> comboReading;
  private javax.swing.JComboBox<String> comboSpeaking;
  private javax.swing.JComboBox<String> comboStaff;
  private javax.swing.JComboBox<String> comboWriting;
  private javax.swing.JPanel footer;
  private javax.swing.JLabel jLabel1;
  private javax.swing.JLabel labelLanguage;
  private javax.swing.JLabel labelListening;
  private javax.swing.JLabel labelSpeaking;
  private javax.swing.JLabel labelStaff;
  private javax.swing.JLabel labelWriting;
  private javax.swing.JTextField textLanguage;
  // End of variables declaration//GEN-END:variables
}
