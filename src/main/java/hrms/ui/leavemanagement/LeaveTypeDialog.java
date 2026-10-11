/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/GUIForms/JDialog.java to edit this template
 */
package hrms.ui.leavemanagement;

import hrms.exception.AuthorizationException;
import hrms.exception.ErrorHandler;
import hrms.exception.ValidationException;
import hrms.model.LeaveType;
import hrms.repository.LeaveTypeRepository;
import hrms.security.Permissions;
import hrms.security.SecurityService;
import hrms.security.SessionContext;
import hrms.util.UiThread;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author aungmrat
 */
public class LeaveTypeDialog extends javax.swing.JDialog {

  private static final Logger LOG = LoggerFactory.getLogger(LeaveTypeDialog.class);

  /** null = create mode. */
  private final LeaveType editing;

  public LeaveTypeDialog(java.awt.Frame parent, boolean modal) {
    this(parent, modal, null);
  }

  /** Create when {@code existing} is null; edit when non-null. */
  public LeaveTypeDialog(java.awt.Frame parent, boolean modal, LeaveType existing) {
    super(parent, modal);
    initComponents();
    this.editing = existing;
    setTitle(existing == null ? "New Leave Type" : "Edit Leave Type");
    if (existing == null) {
      applyCreateDefaults();
    } else {
      prefill(existing);
    }
    setupActions();
  }

  /** DB defaults: half_day_allowed = 0, is_carry_forward = 0, is_active = 1. */
  private void applyCreateDefaults() {
    comboHalfDayAllowed.setSelectedItem("No");
    comboIsCarryForward.setSelectedItem("No");
    comboRequirePreApproval.setSelectedItem("No");
    comboIsActive.setSelectedItem("Yes");
    comboForGender.setSelectedItem("Both");
  }

  private void prefill(LeaveType leaveType) {
    textCode.setText(leaveType.getCode());
    textName.setText(leaveType.getName());
    textDescription.setText(leaveType.getDescription() == null
            ? "" : leaveType.getDescription());
    textStandardDaysPerYear.setText(String.valueOf(leaveType.getStandardDaysPerYear()));
    textMaxDaysPerYear.setText(String.valueOf(leaveType.getMaxDaysPerYear()));
    comboHalfDayAllowed.setSelectedItem(leaveType.isHalfDayAllowed() ? "Yes" : "No");
    comboIsCarryForward.setSelectedItem(leaveType.isCarryForward() ? "Yes" : "No");
    textCarryMaxDays.setText(leaveType.getCarryMaxDays() == null
            ? "" : String.valueOf(leaveType.getCarryMaxDays()));
    comboRequirePreApproval.setSelectedItem(leaveType.isRequiresPreApproval() ? "Yes" : "No");
    comboIsActive.setSelectedItem(leaveType.isActive() ? "Yes" : "No");
    comboForGender.setSelectedItem(leaveType.getGenderRestriction());
  }

  private void setupActions() {
    btnCancel.addActionListener(e -> dispose());
    btnSave.addActionListener(e -> {
      try {
        saveAndClose();
      } catch (AuthorizationException ex) {
        ErrorHandler.handle(this, ex);
      }
    });
    // Carry Max Days only applies when the type carries leave forward.
    comboIsCarryForward.addActionListener(e -> updateCarryMaxDaysEnabled());
    updateCarryMaxDaysEnabled();
  }

  private void updateCarryMaxDaysEnabled() {
    textCarryMaxDays.setEnabled("Yes".equals(comboIsCarryForward.getSelectedItem()));
  }

  /** Permission gate for Save; static so headless tests can verify it. */
  static void requireSavePermission(boolean isEdit) {
    SecurityService.require(isEdit
            ? Permissions.LEAVE_TYPE_UPDATE : Permissions.LEAVE_TYPE_CREATE);
  }

  private void saveAndClose() {
    try {
      boolean isEdit = editing != null;
      requireSavePermission(isEdit);

      List<String> errors = new ArrayList<>();
      String code = textCode.getText() == null ? "" : textCode.getText().trim();
      if (code.isEmpty()) {
        errors.add("Code is required.");
      } else if (code.length() > 10) {
        errors.add("Code must be at most 10 characters.");
      }

      String name = textName.getText() == null ? "" : textName.getText().trim();
      if (name.isEmpty()) {
        errors.add("Name is required.");
      } else if (name.length() > 100) {
        errors.add("Name must be at most 100 characters.");
      }

      String description = textDescription.getText() == null
              ? "" : textDescription.getText().trim();
      if (description.length() > 500) {
        errors.add("Description must be at most 500 characters.");
      }

      Integer standardDays = parseInteger(textStandardDaysPerYear.getText(),
              "Standard Days Per Year", true, errors);
      Integer maxDays = parseInteger(textMaxDaysPerYear.getText(),
              "Max Days Per Year", true, errors);
      if (standardDays != null && maxDays != null && maxDays < standardDays) {
        errors.add("Max Days Per Year cannot be less than Standard Days Per Year.");
      }

      boolean carryForward = "Yes".equals(comboIsCarryForward.getSelectedItem());
      Integer carryMaxDays;
      if (carryForward) {
        carryMaxDays = parseInteger(textCarryMaxDays.getText(),
                "Carry Max Days", true, errors);
      } else {
        carryMaxDays = null;
      }

      boolean halfDayAllowed = "Yes".equals(comboHalfDayAllowed.getSelectedItem());
      boolean requiresPreApproval = "Yes".equals(comboRequirePreApproval.getSelectedItem());
      boolean active = "Yes".equals(comboIsActive.getSelectedItem());
      String gender = comboForGender.getSelectedItem() == null
              ? "Both" : comboForGender.getSelectedItem().toString();

      if (!errors.isEmpty()) {
        throw new ValidationException(errors);
      }

      Long userId = SessionContext.isAuthenticated() ? SessionContext.currentUserId() : null;
      LeaveType leaveType;
      if (isEdit) {
        leaveType = editing;
        leaveType.setCode(code);
        leaveType.setName(name);
        leaveType.setDescription(description.isEmpty() ? null : description);
        leaveType.setStandardDaysPerYear(standardDays);
        leaveType.setMaxDaysPerYear(maxDays);
        leaveType.setHalfDayAllowed(halfDayAllowed);
        leaveType.setCarryForward(carryForward);
        leaveType.setCarryMaxDays(carryMaxDays);
        leaveType.setRequiresPreApproval(requiresPreApproval);
        leaveType.setGenderRestriction(gender);
        leaveType.setActive(active);
        leaveType.setModifiedBy(userId);
      } else {
        leaveType = LeaveType.create(code, name,
                description.isEmpty() ? null : description,
                standardDays, maxDays, halfDayAllowed, carryForward, carryMaxDays,
                requiresPreApproval, gender, active, userId);
      }

      btnSave.setEnabled(false);
      UiThread.executeAsync(isEdit ? "Update leave type" : "Save leave type",
              () -> isEdit
                      ? LeaveTypeRepository.update(leaveType)
                      : LeaveTypeRepository.insert(leaveType),
              result -> {
                if (isEdit) {
                  LOG.info("Leave type updated id={}", leaveType.getId());
                } else {
                  LOG.info("Leave type saved with id {}", result);
                }
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
   * Parses a whole-day field. Required when {@code required}; returns null for
   * a blank optional field. Must be a non-negative whole number.
   */
  private static Integer parseInteger(String raw, String label,
          boolean required, List<String> errors) {
    String text = raw == null ? "" : raw.trim();
    if (text.isEmpty()) {
      if (required) {
        errors.add(label + " is required.");
      }
      return null;
    }
    int value;
    try {
      value = Integer.parseInt(text);
    } catch (NumberFormatException e) {
      errors.add(label + " must be a whole number.");
      return null;
    }
    if (value < 0) {
      errors.add(label + " cannot be negative.");
      return null;
    }
    return value;
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
    labelCode = new javax.swing.JLabel();
    textCode = new javax.swing.JTextField();
    labelName = new javax.swing.JLabel();
    textName = new javax.swing.JTextField();
    labelDescription = new javax.swing.JLabel();
    textDescription = new javax.swing.JTextField();
    labelStandardDaysPerYear = new javax.swing.JLabel();
    textStandardDaysPerYear = new javax.swing.JTextField();
    labelMaxDaysPerYear = new javax.swing.JLabel();
    textMaxDaysPerYear = new javax.swing.JTextField();
    labelHalfDayAllowed = new javax.swing.JLabel();
    comboHalfDayAllowed = new javax.swing.JComboBox<>();
    labelIsCarryForward = new javax.swing.JLabel();
    comboIsCarryForward = new javax.swing.JComboBox<>();
    labelCarryMaxDays = new javax.swing.JLabel();
    textCarryMaxDays = new javax.swing.JTextField();
    labelRequirePreApproval = new javax.swing.JLabel();
    comboRequirePreApproval = new javax.swing.JComboBox<>();
    labelIsActive = new javax.swing.JLabel();
    comboIsActive = new javax.swing.JComboBox<>();
    labelForGender = new javax.swing.JLabel();
    comboForGender = new javax.swing.JComboBox<>();

    setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);

    footer.setPreferredSize(new java.awt.Dimension(400, 60));
    footer.setLayout(new java.awt.GridBagLayout());

    btnSave.setText("Save");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
    gridBagConstraints.insets = new java.awt.Insets(17, 18, 16, 40);
    footer.add(btnSave, gridBagConstraints);

    btnCancel.setText("Cancel");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
    gridBagConstraints.insets = new java.awt.Insets(17, 190, 16, 0);
    footer.add(btnCancel, gridBagConstraints);

    getContentPane().add(footer, java.awt.BorderLayout.PAGE_END);

    body.setLayout(new java.awt.GridBagLayout());

    labelCode.setText("Code");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(5, 0, 5, 17);
    body.add(labelCode, gridBagConstraints);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(5, 0, 5, 0);
    body.add(textCode, gridBagConstraints);

    labelName.setText("Name");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(5, 0, 5, 17);
    body.add(labelName, gridBagConstraints);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(5, 0, 5, 0);
    body.add(textName, gridBagConstraints);

    labelDescription.setText("Description");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(5, 0, 5, 17);
    body.add(labelDescription, gridBagConstraints);

    textDescription.setPreferredSize(new java.awt.Dimension(200, 26));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(5, 0, 5, 0);
    body.add(textDescription, gridBagConstraints);

    labelStandardDaysPerYear.setText("Standard Days Per Year");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(5, 0, 5, 17);
    body.add(labelStandardDaysPerYear, gridBagConstraints);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(5, 0, 5, 0);
    body.add(textStandardDaysPerYear, gridBagConstraints);

    labelMaxDaysPerYear.setText("Max Days Per Year");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(5, 0, 5, 17);
    body.add(labelMaxDaysPerYear, gridBagConstraints);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(5, 0, 5, 0);
    body.add(textMaxDaysPerYear, gridBagConstraints);

    labelHalfDayAllowed.setText("Half Day Allowed");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 5;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(5, 0, 5, 17);
    body.add(labelHalfDayAllowed, gridBagConstraints);

    comboHalfDayAllowed.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Yes", "No" }));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 5;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(5, 0, 5, 0);
    body.add(comboHalfDayAllowed, gridBagConstraints);

    labelIsCarryForward.setText("Is Carry Forward");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 6;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(5, 0, 5, 17);
    body.add(labelIsCarryForward, gridBagConstraints);

    comboIsCarryForward.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Yes", "No" }));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 6;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(5, 0, 5, 0);
    body.add(comboIsCarryForward, gridBagConstraints);

    labelCarryMaxDays.setText("Carry Max Days");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 7;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(5, 0, 5, 17);
    body.add(labelCarryMaxDays, gridBagConstraints);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 7;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(5, 0, 5, 0);
    body.add(textCarryMaxDays, gridBagConstraints);

    labelRequirePreApproval.setText("Require Pre-approval");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 8;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(5, 0, 5, 17);
    body.add(labelRequirePreApproval, gridBagConstraints);

    comboRequirePreApproval.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Yes", "No" }));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 8;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(5, 0, 5, 0);
    body.add(comboRequirePreApproval, gridBagConstraints);

    labelIsActive.setText("Is Active");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 9;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(5, 0, 5, 17);
    body.add(labelIsActive, gridBagConstraints);

    comboIsActive.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Yes", "No" }));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 9;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(5, 0, 5, 0);
    body.add(comboIsActive, gridBagConstraints);

    labelForGender.setText("For Gender");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 10;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(6, 0, 0, 16);
    body.add(labelForGender, gridBagConstraints);

    comboForGender.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Male", "Female", "Both" }));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 10;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(6, 0, 0, 0);
    body.add(comboForGender, gridBagConstraints);

    getContentPane().add(body, java.awt.BorderLayout.CENTER);

    pack();
  }// </editor-fold>//GEN-END:initComponents

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
      LOG.error("Cannot set look and feel", ex);
    }
    //</editor-fold>

    /* Create and display the dialog */
    java.awt.EventQueue.invokeLater(new Runnable() {
      @Override
      public void run() {
        LeaveTypeDialog dialog = new LeaveTypeDialog(new javax.swing.JFrame(), true);
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
  private javax.swing.JComboBox<String> comboForGender;
  private javax.swing.JComboBox<String> comboHalfDayAllowed;
  private javax.swing.JComboBox<String> comboIsActive;
  private javax.swing.JComboBox<String> comboIsCarryForward;
  private javax.swing.JComboBox<String> comboRequirePreApproval;
  private javax.swing.JPanel footer;
  private javax.swing.JLabel labelCarryMaxDays;
  private javax.swing.JLabel labelCode;
  private javax.swing.JLabel labelDescription;
  private javax.swing.JLabel labelForGender;
  private javax.swing.JLabel labelHalfDayAllowed;
  private javax.swing.JLabel labelIsActive;
  private javax.swing.JLabel labelIsCarryForward;
  private javax.swing.JLabel labelMaxDaysPerYear;
  private javax.swing.JLabel labelName;
  private javax.swing.JLabel labelRequirePreApproval;
  private javax.swing.JLabel labelStandardDaysPerYear;
  private javax.swing.JTextField textCarryMaxDays;
  private javax.swing.JTextField textCode;
  private javax.swing.JTextField textDescription;
  private javax.swing.JTextField textMaxDaysPerYear;
  private javax.swing.JTextField textName;
  private javax.swing.JTextField textStandardDaysPerYear;
  // End of variables declaration//GEN-END:variables
}
