/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/GUIForms/JDialog.java to edit this template
 */
package hrms.ui.compensation_policy;

import hrms.exception.ErrorHandler;
import hrms.exception.ValidationException;
import hrms.model.Allowance;
import hrms.repository.AllowanceRepository;
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
public class AllowanceDialog extends javax.swing.JDialog {

  private static final Logger LOG = LoggerFactory.getLogger(AllowanceDialog.class);

  /** null = create mode. */
  private final Allowance editing;

  public AllowanceDialog(java.awt.Frame parent, boolean modal) {
    this(parent, modal, null);
  }

  /** Create when {@code existing} is null; edit when non-null. */
  public AllowanceDialog(java.awt.Frame parent, boolean modal, Allowance existing) {
    super(parent, modal);
    initComponents();
    this.editing = existing;
    setTitle(existing == null ? "New Allowance" : "Edit Allowance");
    if (existing != null) {
      prefill(existing);
    }
    setupActions();
  }

  private void prefill(Allowance allowance) {
    textName.setText(allowance.getName());
    textRate.setText(allowance.getRate() == null ? "" : allowance.getRate().toPlainString());
    textMinDistance.setText(allowance.getMinDistance() == null
            ? "" : String.valueOf(allowance.getMinDistance()));
    textMaxDistance.setText(allowance.getMaxDistance() == null
            ? "" : String.valueOf(allowance.getMaxDistance()));
    textCondition.setText(allowance.getCondition() == null ? "" : allowance.getCondition());
    textRemark.setText(allowance.getRemark() == null ? "" : allowance.getRemark());
  }

  private void setupActions() {
    btnCancel.addActionListener(e -> dispose());
  }

  /** Permission gate for Save; static so headless tests can verify it. */
  static void requireSavePermission(boolean isEdit) {
    SecurityService.require(isEdit
            ? Permissions.ALLOWANCE_UPDATE : Permissions.ALLOWANCE_CREATE);
  }

  private void saveAndClose() {
    try {
      requireSavePermission(editing != null);

      java.util.List<String> errors = new java.util.ArrayList<>();
      String name = textName.getText() == null ? "" : textName.getText().trim();
      if (name.isEmpty()) {
        errors.add("Allowance name is required.");
      } else if (name.length() > 100) {
        errors.add("Allowance name must be at most 100 characters.");
      }

      java.math.BigDecimal rate = parseDecimal(textRate.getText(), "Rate", true, errors);
      Integer minDistance = parseDistance(textMinDistance.getText(), "Min Distance", errors);
      Integer maxDistance = parseDistance(textMaxDistance.getText(), "Max Distance", errors);
      if (minDistance != null && maxDistance != null && maxDistance < minDistance) {
        errors.add("Max Distance cannot be less than Min Distance.");
      }

      String condition = optionalText(textCondition.getText(), "Condition", 500, errors);
      String remark = optionalText(textRemark.getText(), "Remark", 500, errors);

      if (!errors.isEmpty()) {
        throw new ValidationException(errors);
      }

      Long userId = SessionContext.isAuthenticated() ? SessionContext.currentUserId() : null;
      boolean isEdit = editing != null;
      Allowance allowance;
      if (isEdit) {
        allowance = editing;
        allowance.setName(name);
        allowance.setRate(rate);
        allowance.setMinDistance(minDistance);
        allowance.setMaxDistance(maxDistance);
        allowance.setCondition(condition);
        allowance.setRemark(remark);
        allowance.setModifiedBy(userId);
      } else {
        allowance = Allowance.create(name, rate, minDistance, maxDistance,
                condition, remark, userId);
      }

      btnSave.setEnabled(false);
      UiThread.executeAsync(isEdit ? "Update allowance" : "Save allowance",
              () -> isEdit
                      ? AllowanceRepository.update(allowance)
                      : AllowanceRepository.insert(allowance),
              result -> {
                if (isEdit) {
                  LOG.info("Allowance updated id={}", allowance.getId());
                } else {
                  LOG.info("Allowance saved with id {}", result);
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

  /** Parses an optional distance (km); returns null when blank. */
  private static Integer parseDistance(String raw, String label, java.util.List<String> errors) {
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
    if (value < 0) {
      errors.add(label + " cannot be negative.");
      return null;
    }
    return value;
  }

  /** Trimmed optional text; null when blank. Enforces {@code maxLength}. */
  private static String optionalText(String raw, String label, int maxLength,
          java.util.List<String> errors) {
    String text = raw == null ? "" : raw.trim();
    if (text.isEmpty()) {
      return null;
    }
    if (text.length() > maxLength) {
      errors.add(label + " must be at most " + maxLength + " characters.");
      return null;
    }
    return text;
  }

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
        AllowanceDialog dialog = new AllowanceDialog(new javax.swing.JFrame(), true);
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
    labelMinDistance = new javax.swing.JLabel();
    textMinDistance = new javax.swing.JTextField();
    labelMaxDistance = new javax.swing.JLabel();
    textMaxDistance = new javax.swing.JTextField();
    labelCondition = new javax.swing.JLabel();
    textCondition = new javax.swing.JTextField();
    labelRemark = new javax.swing.JLabel();
    textRemark = new javax.swing.JTextField();

    setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);

    footer.setPreferredSize(new java.awt.Dimension(400, 60));
    footer.setLayout(new java.awt.GridBagLayout());

    btnSave.setText("Save");
    btnSave.addActionListener(this::btnSaveActionPerformed);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
    gridBagConstraints.insets = new java.awt.Insets(18, 18, 15, 60);
    footer.add(btnSave, gridBagConstraints);

    btnCancel.setText("Cancel");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
    gridBagConstraints.insets = new java.awt.Insets(18, 170, 15, 0);
    footer.add(btnCancel, gridBagConstraints);

    getContentPane().add(footer, java.awt.BorderLayout.PAGE_END);

    body.setLayout(new java.awt.GridBagLayout());

    labelName.setText("Name");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(8, 0, 7, 16);
    body.add(labelName, gridBagConstraints);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(8, 0, 7, 0);
    body.add(textName, gridBagConstraints);

    labelRate.setText("Rate");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(8, 0, 7, 16);
    body.add(labelRate, gridBagConstraints);

    textRate.setPreferredSize(new java.awt.Dimension(200, 26));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(8, 0, 7, 0);
    body.add(textRate, gridBagConstraints);

    labelMinDistance.setText("Min Distance");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(8, 0, 7, 16);
    body.add(labelMinDistance, gridBagConstraints);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(8, 0, 7, 0);
    body.add(textMinDistance, gridBagConstraints);

    labelMaxDistance.setText("Max Distance");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(8, 0, 7, 16);
    body.add(labelMaxDistance, gridBagConstraints);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(8, 0, 7, 0);
    body.add(textMaxDistance, gridBagConstraints);

    labelCondition.setText("Condition");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(8, 0, 7, 16);
    body.add(labelCondition, gridBagConstraints);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(8, 0, 7, 0);
    body.add(textCondition, gridBagConstraints);

    labelRemark.setText("Remark");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 5;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(8, 0, 7, 16);
    body.add(labelRemark, gridBagConstraints);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 5;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(8, 0, 7, 0);
    body.add(textRemark, gridBagConstraints);

    getContentPane().add(body, java.awt.BorderLayout.CENTER);

    pack();
  }// </editor-fold>//GEN-END:initComponents

  private void btnSaveActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnSaveActionPerformed
    saveAndClose();
  }//GEN-LAST:event_btnSaveActionPerformed

  // Variables declaration - do not modify//GEN-BEGIN:variables
  private javax.swing.JPanel body;
  private javax.swing.JButton btnCancel;
  private javax.swing.JButton btnSave;
  private javax.swing.JPanel footer;
  private javax.swing.JLabel labelCondition;
  private javax.swing.JLabel labelMaxDistance;
  private javax.swing.JLabel labelMinDistance;
  private javax.swing.JLabel labelName;
  private javax.swing.JLabel labelRate;
  private javax.swing.JLabel labelRemark;
  private javax.swing.JTextField textCondition;
  private javax.swing.JTextField textMaxDistance;
  private javax.swing.JTextField textMinDistance;
  private javax.swing.JTextField textName;
  private javax.swing.JTextField textRate;
  private javax.swing.JTextField textRemark;
  // End of variables declaration//GEN-END:variables
}
