/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/GUIForms/JDialog.java to edit this template
 */
package hrms.ui.compensationpolicy;

import hrms.exception.AuthorizationException;
import hrms.exception.ErrorHandler;
import hrms.exception.ValidationException;
import hrms.model.Benefit;
import hrms.repository.BenefitRepository;
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
public class BenefitDialog extends javax.swing.JDialog {

  private static final Logger LOG = LoggerFactory.getLogger(BenefitDialog.class);

  /** null = create mode. */
  private final Benefit editing;

  public BenefitDialog(java.awt.Frame parent, boolean modal) {
    this(parent, modal, null);
  }

  /** Create when {@code existing} is null; edit when non-null. */
  public BenefitDialog(java.awt.Frame parent, boolean modal, Benefit existing) {
    super(parent, modal);
    initComponents();
    this.editing = existing;
    setTitle(existing == null ? "New Benefit" : "Edit Benefit");
    if (existing == null) {
      applyCreateDefaults();
    } else {
      prefill(existing);
    }
    setupActions();
  }

  /** DB defaults: has_family = 0, has_children = 0, is_active = 1. */
  private void applyCreateDefaults() {
    comboFrequency.setSelectedItem("Monthly");
    comboUnit.setSelectedItem("Flat");
    comboHasFamily.setSelectedItem("No");
    comboHasChildren.setSelectedItem("No");
    comboIsActive.setSelectedItem("Yes");
  }

  private void prefill(Benefit benefit) {
    textName.setText(benefit.getName());
    textRate.setText(benefit.getRate() == null ? "" : benefit.getRate().toPlainString());
    if (benefit.getFrequency() != null) {
      comboFrequency.setSelectedItem(benefit.getFrequency());
    }
    if (benefit.getUnit() != null) {
      comboUnit.setSelectedItem(benefit.getUnit());
    }
    textMinAnnualIncome.setText(benefit.getMinAnnualIncome() == null
            ? "" : benefit.getMinAnnualIncome().toPlainString());
    textMaxAnnualIncome.setText(benefit.getMaxAnnualIncome() == null
            ? "" : benefit.getMaxAnnualIncome().toPlainString());
    textMinAge.setText(benefit.getMinAge() == null ? "" : String.valueOf(benefit.getMinAge()));
    textMaxAge.setText(benefit.getMaxAge() == null ? "" : String.valueOf(benefit.getMaxAge()));
    comboHasFamily.setSelectedItem(benefit.isHasFamily() ? "Yes" : "No");
    comboHasChildren.setSelectedItem(benefit.isHasChildren() ? "Yes" : "No");
    comboIsActive.setSelectedItem(benefit.isActive() ? "Yes" : "No");
  }

  private void setupActions() {
    btnCancel.addActionListener(e -> dispose());
  }

  /** Permission gate for Save; static so headless tests can verify it. */
  static void requireSavePermission(boolean isEdit) {
    SecurityService.require(isEdit
            ? Permissions.BENEFIT_UPDATE : Permissions.BENEFIT_CREATE);
  }

  private void saveAndClose() {
    try {
      requireSavePermission(editing != null);

      java.util.List<String> errors = new java.util.ArrayList<>();
      String name = textName.getText() == null ? "" : textName.getText().trim();
      if (name.isEmpty()) {
        errors.add("Benefit name is required.");
      } else if (name.length() > 100) {
        errors.add("Benefit name must be at most 100 characters.");
      }

      java.math.BigDecimal rate = parseDecimal(textRate.getText(), "Rate", true, errors);
      String frequency = comboFrequency.getSelectedItem() == null
              ? "" : comboFrequency.getSelectedItem().toString().trim();
      if (frequency.isEmpty()) {
        errors.add("Frequency is required.");
      }
      String unit = comboUnit.getSelectedItem() == null
              ? "" : comboUnit.getSelectedItem().toString().trim();
      if (unit.isEmpty()) {
        errors.add("Unit is required.");
      }

      java.math.BigDecimal minIncome = parseDecimal(
              textMinAnnualIncome.getText(), "Min Annual Income", false, errors);
      java.math.BigDecimal maxIncome = parseDecimal(
              textMaxAnnualIncome.getText(), "Max Annual Income", false, errors);
      if (minIncome != null && maxIncome != null && minIncome.compareTo(maxIncome) > 0) {
        errors.add("Max Annual Income cannot be less than Min Annual Income.");
      }

      Integer minAge = parseAge(textMinAge.getText(), "Min Age", errors);
      Integer maxAge = parseAge(textMaxAge.getText(), "Max Age", errors);
      if (minAge != null && maxAge != null && maxAge < minAge) {
        errors.add("Max Age cannot be less than Min Age.");
      }

      boolean hasFamily = "Yes".equals(comboHasFamily.getSelectedItem());
      boolean hasChildren = "Yes".equals(comboHasChildren.getSelectedItem());
      boolean active = "Yes".equals(comboIsActive.getSelectedItem());

      if (!errors.isEmpty()) {
        throw new ValidationException(errors);
      }

      Long userId = SessionContext.isAuthenticated() ? SessionContext.currentUserId() : null;
      boolean isEdit = editing != null;
      Benefit benefit;
      if (isEdit) {
        benefit = editing;
        benefit.setName(name);
        benefit.setRate(rate);
        benefit.setFrequency(frequency);
        benefit.setUnit(unit);
        benefit.setMinAnnualIncome(minIncome);
        benefit.setMaxAnnualIncome(maxIncome);
        benefit.setMinAge(minAge);
        benefit.setMaxAge(maxAge);
        benefit.setHasFamily(hasFamily);
        benefit.setHasChildren(hasChildren);
        benefit.setActive(active);
        benefit.setModifiedBy(userId);
      } else {
        benefit = Benefit.create(name, rate, frequency, unit, minIncome, maxIncome,
                minAge, maxAge, hasFamily, hasChildren, active, userId);
      }

      btnSave.setEnabled(false);
      UiThread.executeAsync(isEdit ? "Update benefit" : "Save benefit",
              () -> isEdit
                      ? BenefitRepository.update(benefit)
                      : BenefitRepository.insert(benefit),
              result -> {
                if (isEdit) {
                  LOG.info("Benefit updated id={}", benefit.getId());
                } else {
                  LOG.info("Benefit saved with id {}", result);
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
   * Parses a money field. Required when {@code required}; returns null for a
   * blank optional field. Checks DECIMAL(12,2): non-negative, at most 2
   * decimal places.
   */
  private static java.math.BigDecimal parseDecimal(String raw, String label,
          boolean required, java.util.List<String> errors) {
    String text = raw == null ? "" : raw.trim();
    if (text.isEmpty()) {
      if (required) {
        errors.add(label + " is required.");
      }
      return null;
    }
    java.math.BigDecimal value;
    try {
      value = new java.math.BigDecimal(text);
    } catch (NumberFormatException e) {
      errors.add(label + " must be a number.");
      return null;
    }
    if (value.signum() < 0) {
      errors.add(label + " cannot be negative.");
      return null;
    }
    if (value.scale() > 2) {
      errors.add(label + " supports at most 2 decimal places.");
      return null;
    }
    return value;
  }

  /** Parses an optional age; returns null when blank. */
  private static Integer parseAge(String raw, String label, java.util.List<String> errors) {
    String text = raw == null ? "" : raw.trim();
    if (text.isEmpty()) {
      return null;
    }
    int value;
    try {
      value = Integer.parseInt(text);
    } catch (NumberFormatException e) {
      errors.add(label + " must be a whole number.");
      return null;
    }
    if (value < 0 || value > 120) {
      errors.add(label + " must be between 0 and 120.");
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
    labelName = new javax.swing.JLabel();
    textName = new javax.swing.JTextField();
    labelRate = new javax.swing.JLabel();
    textRate = new javax.swing.JTextField();
    labelFrequency = new javax.swing.JLabel();
    labelMinAnnualIncome = new javax.swing.JLabel();
    textMinAnnualIncome = new javax.swing.JTextField();
    labelMaxAnnualIncome = new javax.swing.JLabel();
    textMaxAnnualIncome = new javax.swing.JTextField();
    comboFrequency = new javax.swing.JComboBox<>();
    labelMinAge = new javax.swing.JLabel();
    textMinAge = new javax.swing.JTextField();
    labelMaxAge = new javax.swing.JLabel();
    textMaxAge = new javax.swing.JTextField();
    labelHasFamily = new javax.swing.JLabel();
    comboHasFamily = new javax.swing.JComboBox<>();
    labelHasChildren = new javax.swing.JLabel();
    comboHasChildren = new javax.swing.JComboBox<>();
    labelIsActive = new javax.swing.JLabel();
    comboIsActive = new javax.swing.JComboBox<>();
    labelUnit = new javax.swing.JLabel();
    comboUnit = new javax.swing.JComboBox<>();

    setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);

    footer.setPreferredSize(new java.awt.Dimension(400, 60));
    footer.setLayout(new java.awt.GridBagLayout());

    btnSave.setText("Save");
    btnSave.addActionListener(this::btnSaveActionPerformed);
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

    labelName.setText("Benefit Name");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(6, 0, 4, 14);
    body.add(labelName, gridBagConstraints);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(6, 0, 4, 0);
    body.add(textName, gridBagConstraints);

    labelRate.setText("Rate");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(6, 0, 4, 14);
    body.add(labelRate, gridBagConstraints);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(6, 0, 4, 0);
    body.add(textRate, gridBagConstraints);

    labelFrequency.setText("Frequency");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(6, 0, 4, 14);
    body.add(labelFrequency, gridBagConstraints);

    labelMinAnnualIncome.setText("Min Annual Income");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(6, 0, 4, 14);
    body.add(labelMinAnnualIncome, gridBagConstraints);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(6, 0, 4, 0);
    body.add(textMinAnnualIncome, gridBagConstraints);

    labelMaxAnnualIncome.setText("Max Annual Income");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 5;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(6, 0, 4, 14);
    body.add(labelMaxAnnualIncome, gridBagConstraints);

    textMaxAnnualIncome.setPreferredSize(new java.awt.Dimension(200, 26));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 5;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(6, 0, 4, 0);
    body.add(textMaxAnnualIncome, gridBagConstraints);

    comboFrequency.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Monthly", "Yearly" }));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(6, 0, 4, 0);
    body.add(comboFrequency, gridBagConstraints);

    labelMinAge.setText("Min Age");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 6;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(6, 0, 4, 14);
    body.add(labelMinAge, gridBagConstraints);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 6;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(6, 0, 4, 0);
    body.add(textMinAge, gridBagConstraints);

    labelMaxAge.setText("Max Age");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 7;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(6, 0, 4, 14);
    body.add(labelMaxAge, gridBagConstraints);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 7;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(6, 0, 4, 0);
    body.add(textMaxAge, gridBagConstraints);

    labelHasFamily.setText("Has Family");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 8;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(6, 0, 4, 14);
    body.add(labelHasFamily, gridBagConstraints);

    comboHasFamily.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Yes", "No" }));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 8;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(6, 0, 4, 0);
    body.add(comboHasFamily, gridBagConstraints);

    labelHasChildren.setText("Has Children");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 9;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(6, 0, 4, 14);
    body.add(labelHasChildren, gridBagConstraints);

    comboHasChildren.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Yes", "No" }));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 9;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(6, 0, 4, 0);
    body.add(comboHasChildren, gridBagConstraints);

    labelIsActive.setText("Is Active");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 10;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(6, 0, 4, 14);
    body.add(labelIsActive, gridBagConstraints);

    comboIsActive.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Yes", "No" }));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 10;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(6, 0, 4, 0);
    body.add(comboIsActive, gridBagConstraints);

    labelUnit.setText("Unit");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(5, 0, 7, 14);
    body.add(labelUnit, gridBagConstraints);

    comboUnit.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Flat", "Per Child" }));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(5, 0, 7, 0);
    body.add(comboUnit, gridBagConstraints);

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
        BenefitDialog dialog = new BenefitDialog(new javax.swing.JFrame(), true);
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
  private javax.swing.JComboBox<String> comboFrequency;
  private javax.swing.JComboBox<String> comboHasChildren;
  private javax.swing.JComboBox<String> comboHasFamily;
  private javax.swing.JComboBox<String> comboIsActive;
  private javax.swing.JComboBox<String> comboUnit;
  private javax.swing.JPanel footer;
  private javax.swing.JLabel labelFrequency;
  private javax.swing.JLabel labelHasChildren;
  private javax.swing.JLabel labelHasFamily;
  private javax.swing.JLabel labelIsActive;
  private javax.swing.JLabel labelMaxAge;
  private javax.swing.JLabel labelMaxAnnualIncome;
  private javax.swing.JLabel labelMinAge;
  private javax.swing.JLabel labelMinAnnualIncome;
  private javax.swing.JLabel labelName;
  private javax.swing.JLabel labelRate;
  private javax.swing.JLabel labelUnit;
  private javax.swing.JTextField textMaxAge;
  private javax.swing.JTextField textMaxAnnualIncome;
  private javax.swing.JTextField textMinAge;
  private javax.swing.JTextField textMinAnnualIncome;
  private javax.swing.JTextField textName;
  private javax.swing.JTextField textRate;
  // End of variables declaration//GEN-END:variables
}
