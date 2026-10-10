/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/GUIForms/JDialog.java to edit this template
 */
package hrms.ui.compensationpolicy;

import hrms.exception.AuthorizationException;
import hrms.exception.ErrorHandler;
import hrms.exception.ValidationException;
import hrms.model.OvertimePolicy;
import hrms.repository.OvertimePolicyRepository;
import hrms.security.Permissions;
import hrms.security.SecurityService;
import hrms.security.SessionContext;
import hrms.util.UiThread;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author aungmrat
 */
public class OvertimePolicyDialog extends javax.swing.JDialog {

  private static final Logger LOG = LoggerFactory.getLogger(OvertimePolicyDialog.class);

  /** DECIMAL(3,1) upper bound and one decimal place allowed by the schema. */
  private static final java.math.BigDecimal MAX_MULTIPLIER = new java.math.BigDecimal("99.9");

  /** null = create mode. */
  private final OvertimePolicy editing;

  public OvertimePolicyDialog(java.awt.Frame parent, boolean modal) {
    this(parent, modal, null);
  }

  /** Create when {@code existing} is null; edit when non-null. */
  public OvertimePolicyDialog(java.awt.Frame parent, boolean modal, OvertimePolicy existing) {
    super(parent, modal);
    initComponents();
    this.editing = existing;
    setTitle(existing == null ? "New Overtime Policy" : "Edit Overtime Policy");
    if (existing != null) {
      prefill(existing);
    }
    setupActions();
  }

  private void prefill(OvertimePolicy policy) {
    textMinOtHour.setText(String.valueOf(policy.getMinOtHour()));
    textMaxOtHour.setText(String.valueOf(policy.getMaxOtHourPerMonth()));
    if (policy.getOtMultiplier() != null) {
      textOtMultiplier.setText(policy.getOtMultiplier().toPlainString());
    }
    if (policy.getWeekendOtMultiplier() != null) {
      textWeekendOtMultiplier.setText(policy.getWeekendOtMultiplier().toPlainString());
    }
    if (policy.getHolidayOtMultiplier() != null) {
      textHolidayOtMultiplier.setText(policy.getHolidayOtMultiplier().toPlainString());
    }
  }

  private void setupActions() {
    btnCancel.addActionListener(e -> dispose());
  }

  /** Permission gate for Save; static so headless tests can verify it. */
  static void requireSavePermission(boolean isEdit) {
    SecurityService.require(isEdit
            ? Permissions.OVERTIME_POLICY_UPDATE : Permissions.OVERTIME_POLICY_CREATE);
  }

  private void saveAndClose() {
    try {
      requireSavePermission(editing != null);

      java.util.List<String> errors = new java.util.ArrayList<>();
      Integer minOtHour = parseIntField(textMinOtHour.getText(),
              "Min OT Hour", 0, null, errors);
      Integer maxOtHour = parseIntField(textMaxOtHour.getText(),
              "Max OT Hour / Month", 0, minOtHour, errors);
      java.math.BigDecimal otMultiplier = parseMultiplier(
              textOtMultiplier.getText(), "OT Multiplier", errors);
      java.math.BigDecimal weekendMultiplier = parseMultiplier(
              textWeekendOtMultiplier.getText(), "Weekend OT Multiplier", errors);
      java.math.BigDecimal holidayMultiplier = parseMultiplier(
              textHolidayOtMultiplier.getText(), "Holiday OT Multiplier", errors);
      if (!errors.isEmpty()) {
        throw new ValidationException(errors);
      }

      Long userId = SessionContext.isAuthenticated() ? SessionContext.currentUserId() : null;
      boolean isEdit = editing != null;
      OvertimePolicy policy;
      if (isEdit) {
        policy = editing;
        policy.setMinOtHour(minOtHour);
        policy.setMaxOtHourPerMonth(maxOtHour);
        policy.setOtMultiplier(otMultiplier);
        policy.setWeekendOtMultiplier(weekendMultiplier);
        policy.setHolidayOtMultiplier(holidayMultiplier);
        policy.setModifiedBy(userId);
      } else {
        policy = OvertimePolicy.create(minOtHour, maxOtHour,
                otMultiplier, weekendMultiplier, holidayMultiplier, userId);
      }

      btnSave.setEnabled(false);
      UiThread.executeAsync(isEdit ? "Update overtime policy" : "Save overtime policy",
              () -> isEdit
                      ? OvertimePolicyRepository.update(policy)
                      : OvertimePolicyRepository.insert(policy),
              result -> {
                if (isEdit) {
                  LOG.info("Overtime policy updated id={}", policy.getId());
                } else {
                  LOG.info("Overtime policy saved with id {}", result);
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

  /** Parses a whole-number field, appending any problem to {@code errors}. */
  private static Integer parseIntField(String raw, String label, int minimum,
          Integer lowerBoundField, java.util.List<String> errors) {
    String text = raw == null ? "" : raw.trim();
    if (text.isEmpty()) {
      errors.add(label + " is required.");
      return null;
    }
    int value;
    try {
      value = Integer.parseInt(text);
    } catch (NumberFormatException e) {
      errors.add(label + " must be a whole number.");
      return null;
    }
    if (value < minimum) {
      errors.add(label + " cannot be less than " + minimum + ".");
      return null;
    }
    if (lowerBoundField != null && value < lowerBoundField) {
      errors.add(label + " cannot be less than Min OT Hour (" + lowerBoundField + ").");
    }
    return value;
  }

  /** Parses a multiplier within the DECIMAL(3,1) range (1.0 - 99.9). */
  private static java.math.BigDecimal parseMultiplier(String raw, String label,
          java.util.List<String> errors) {
    String text = raw == null ? "" : raw.trim();
    if (text.isEmpty()) {
      errors.add(label + " is required.");
      return null;
    }
    java.math.BigDecimal value;
    try {
      value = new java.math.BigDecimal(text);
    } catch (NumberFormatException e) {
      errors.add(label + " must be a number.");
      return null;
    }
    if (value.scale() > 1) {
      errors.add(label + " supports at most one decimal place.");
      return null;
    }
    if (value.compareTo(java.math.BigDecimal.ONE) < 0) {
      errors.add(label + " must be at least 1.0.");
      return null;
    }
    if (value.compareTo(MAX_MULTIPLIER) > 0) {
      errors.add(label + " cannot exceed 99.9.");
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
    labelMinOtHour = new javax.swing.JLabel();
    textMinOtHour = new javax.swing.JTextField();
    labelMaxOtHour = new javax.swing.JLabel();
    textMaxOtHour = new javax.swing.JTextField();
    labelOtMultiplier = new javax.swing.JLabel();
    textOtMultiplier = new javax.swing.JTextField();
    labelWeekendOtMultiplier = new javax.swing.JLabel();
    textWeekendOtMultiplier = new javax.swing.JTextField();
    jLabel1 = new javax.swing.JLabel();
    textHolidayOtMultiplier = new javax.swing.JTextField();

    setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);

    footer.setPreferredSize(new java.awt.Dimension(400, 60));
    footer.setLayout(new java.awt.GridBagLayout());

    btnSave.setText("Save");
    btnSave.addActionListener(this::btnSaveActionPerformed);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
    gridBagConstraints.insets = new java.awt.Insets(18, 18, 15, 40);
    footer.add(btnSave, gridBagConstraints);

    btnCancel.setText("Cancel");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
    gridBagConstraints.insets = new java.awt.Insets(18, 190, 15, 0);
    footer.add(btnCancel, gridBagConstraints);

    getContentPane().add(footer, java.awt.BorderLayout.PAGE_END);

    body.setLayout(new java.awt.GridBagLayout());

    labelMinOtHour.setText("Min OT Hour / Month");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(14, 0, 8, 16);
    body.add(labelMinOtHour, gridBagConstraints);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(14, 0, 8, 0);
    body.add(textMinOtHour, gridBagConstraints);

    labelMaxOtHour.setText("Max OT Hour / Month");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(14, 0, 8, 16);
    body.add(labelMaxOtHour, gridBagConstraints);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(14, 0, 8, 0);
    body.add(textMaxOtHour, gridBagConstraints);

    labelOtMultiplier.setText("OT Multiplier");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(14, 0, 8, 16);
    body.add(labelOtMultiplier, gridBagConstraints);

    textOtMultiplier.setPreferredSize(new java.awt.Dimension(200, 26));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(14, 0, 8, 0);
    body.add(textOtMultiplier, gridBagConstraints);

    labelWeekendOtMultiplier.setText("Weekend OT Multiplier");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(14, 0, 8, 16);
    body.add(labelWeekendOtMultiplier, gridBagConstraints);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(14, 0, 8, 0);
    body.add(textWeekendOtMultiplier, gridBagConstraints);

    jLabel1.setText("Holiday OT Multiplier");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(14, 0, 8, 16);
    body.add(jLabel1, gridBagConstraints);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(14, 0, 8, 0);
    body.add(textHolidayOtMultiplier, gridBagConstraints);

    getContentPane().add(body, java.awt.BorderLayout.CENTER);

    pack();
  }// </editor-fold>//GEN-END:initComponents

  private void btnSaveActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnSaveActionPerformed
    saveAndClose();
  }//GEN-LAST:event_btnSaveActionPerformed

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
        OvertimePolicyDialog dialog = new OvertimePolicyDialog(new javax.swing.JFrame(), true);
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
  private javax.swing.JPanel footer;
  private javax.swing.JLabel jLabel1;
  private javax.swing.JLabel labelMaxOtHour;
  private javax.swing.JLabel labelMinOtHour;
  private javax.swing.JLabel labelOtMultiplier;
  private javax.swing.JLabel labelWeekendOtMultiplier;
  private javax.swing.JTextField textHolidayOtMultiplier;
  private javax.swing.JTextField textMaxOtHour;
  private javax.swing.JTextField textMinOtHour;
  private javax.swing.JTextField textOtMultiplier;
  private javax.swing.JTextField textWeekendOtMultiplier;
  // End of variables declaration//GEN-END:variables
}
