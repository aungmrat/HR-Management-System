/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/GUIForms/JDialog.java to edit this template
 */
package hrms.ui.compensationpolicy;

import hrms.exception.AuthorizationException;
import hrms.exception.ErrorHandler;
import hrms.exception.ValidationException;
import hrms.model.Tax;
import hrms.repository.TaxRepository;
import hrms.security.Permissions;
import hrms.security.SecurityService;
import hrms.security.SessionContext;
import hrms.util.UiThread;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author aungmrat
 */
public class TaxDialog extends javax.swing.JDialog {

  private static final Logger LOG = LoggerFactory.getLogger(TaxDialog.class);

  /** null = create mode. */
  private final Tax editing;

  public TaxDialog(java.awt.Frame parent, boolean modal) {
    this(parent, modal, null);
  }

  /** Create when {@code existing} is null; edit when non-null. */
  public TaxDialog(java.awt.Frame parent, boolean modal, Tax existing) {
    super(parent, modal);
    initComponents();
    this.editing = existing;
    setTitle(existing == null ? "New Tax Bracket" : "Edit Tax Bracket");
    if (existing != null) {
      prefill(existing);
    }
    setupActions();
  }

  private void prefill(Tax tax) {
    textBracketName.setText(tax.getBracketName());
    textMinAmount.setText(tax.getMinAmount() == null
            ? "" : tax.getMinAmount().toPlainString());
    textMaxAmount.setText(tax.getMaxAmount() == null
            ? "" : tax.getMaxAmount().toPlainString());
    // Stored as a fraction (0.1500); the field takes a percent (15).
    textTaxRate.setText(tax.getTaxRate() == null ? "" : tax.getTaxRate()
            .multiply(BigDecimal.valueOf(100)).stripTrailingZeros().toPlainString());
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
  }

  /** Permission gate for Save; static so headless tests can verify it. */
  static void requireSavePermission(boolean isEdit) {
    SecurityService.require(isEdit
            ? Permissions.TAX_UPDATE : Permissions.TAX_CREATE);
  }

  private void saveAndClose() {
    try {
      boolean isEdit = editing != null;
      requireSavePermission(isEdit);

      java.util.List<String> errors = new java.util.ArrayList<>();
      String bracketName = textBracketName.getText() == null
              ? "" : textBracketName.getText().trim();
      if (bracketName.isEmpty()) {
        errors.add("Bracket name is required.");
      } else if (bracketName.length() > 50) {
        errors.add("Bracket name must be at most 50 characters.");
      }

      BigDecimal minAmount = parseDecimal(textMinAmount.getText(),
              "Min Amount", true, errors);
      BigDecimal maxAmount = parseDecimal(textMaxAmount.getText(),
              "Max Amount", false, errors);
      if (minAmount != null && maxAmount != null && maxAmount.compareTo(minAmount) < 0) {
        errors.add("Max Amount cannot be less than Min Amount.");
      }

      // DECIMAL(5,4) stores a fraction; the field takes a percent.
      BigDecimal percent = parseDecimal(textTaxRate.getText(),
              "Tax Rate (%)", true, errors);
      if (percent != null && percent.compareTo(new BigDecimal("999.99")) > 0) {
        errors.add("Tax Rate (%) must be at most 999.99.");
      }

      if (!errors.isEmpty()) {
        throw new ValidationException(errors);
      }

      BigDecimal taxRate = percent.divide(BigDecimal.valueOf(100), 4,
              RoundingMode.HALF_UP);
      Long userId = SessionContext.isAuthenticated() ? SessionContext.currentUserId() : null;
      Tax tax;
      if (isEdit) {
        tax = editing;
        tax.setBracketName(bracketName);
        tax.setMinAmount(minAmount);
        tax.setMaxAmount(maxAmount);
        tax.setTaxRate(taxRate);
        tax.setModifiedBy(userId);
      } else {
        tax = Tax.create(bracketName, minAmount, maxAmount, taxRate, userId);
      }

      btnSave.setEnabled(false);
      UiThread.executeAsync(isEdit ? "Update tax bracket" : "Save tax bracket",
              () -> isEdit
                      ? TaxRepository.update(tax)
                      : TaxRepository.insert(tax),
              result -> {
                if (isEdit) {
                  LOG.info("Tax bracket updated id={}", tax.getId());
                } else {
                  LOG.info("Tax bracket saved with id {}", result);
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
   * Parses a money/percent field. Required when {@code required}; returns null
   * for a blank optional field. Checks DECIMAL(12,2): non-negative, at most 2
   * decimal places.
   */
  private static BigDecimal parseDecimal(String raw, String label,
          boolean required, java.util.List<String> errors) {
    String text = raw == null ? "" : raw.trim();
    if (text.isEmpty()) {
      if (required) {
        errors.add(label + " is required.");
      }
      return null;
    }
    BigDecimal value;
    try {
      value = new BigDecimal(text);
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
    labelBracketName = new javax.swing.JLabel();
    textBracketName = new javax.swing.JTextField();
    labelMinAmount = new javax.swing.JLabel();
    textMinAmount = new javax.swing.JTextField();
    labelMaxAmount = new javax.swing.JLabel();
    textMaxAmount = new javax.swing.JTextField();
    labelTaxRate = new javax.swing.JLabel();
    textTaxRate = new javax.swing.JTextField();

    setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);

    footer.setPreferredSize(new java.awt.Dimension(400, 60));
    footer.setLayout(new java.awt.GridBagLayout());

    btnSave.setText("Save");
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

    labelBracketName.setText("Bracket Name");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(6, 0, 8, 16);
    body.add(labelBracketName, gridBagConstraints);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(6, 0, 8, 0);
    body.add(textBracketName, gridBagConstraints);

    labelMinAmount.setText("Min Amount");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(6, 0, 8, 16);
    body.add(labelMinAmount, gridBagConstraints);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(6, 0, 8, 0);
    body.add(textMinAmount, gridBagConstraints);

    labelMaxAmount.setText("Max Amount");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(6, 0, 8, 16);
    body.add(labelMaxAmount, gridBagConstraints);

    textMaxAmount.setPreferredSize(new java.awt.Dimension(200, 26));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(6, 0, 8, 0);
    body.add(textMaxAmount, gridBagConstraints);

    labelTaxRate.setText("Tax Rate (%)");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(6, 0, 8, 16);
    body.add(labelTaxRate, gridBagConstraints);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(6, 0, 8, 0);
    body.add(textTaxRate, gridBagConstraints);

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
        TaxDialog dialog = new TaxDialog(new javax.swing.JFrame(), true);
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
  private javax.swing.JLabel labelBracketName;
  private javax.swing.JLabel labelMaxAmount;
  private javax.swing.JLabel labelMinAmount;
  private javax.swing.JLabel labelTaxRate;
  private javax.swing.JTextField textBracketName;
  private javax.swing.JTextField textMaxAmount;
  private javax.swing.JTextField textMinAmount;
  private javax.swing.JTextField textTaxRate;
  // End of variables declaration//GEN-END:variables
}
