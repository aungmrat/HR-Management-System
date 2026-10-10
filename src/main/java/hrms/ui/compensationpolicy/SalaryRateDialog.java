/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/GUIForms/JDialog.java to edit this template
 */
package hrms.ui.compensationpolicy;

import hrms.exception.AuthorizationException;
import hrms.exception.ErrorHandler;
import hrms.exception.ValidationException;
import hrms.model.Role;
import hrms.model.SalaryRate;
import hrms.repository.RoleRepository;
import hrms.repository.SalaryRateRepository;
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
public class SalaryRateDialog extends javax.swing.JDialog {

  private static final Logger LOG = LoggerFactory.getLogger(SalaryRateDialog.class);

  /** null = create mode. */
  private final SalaryRate editing;

  public SalaryRateDialog(java.awt.Frame parent, boolean modal) {
    this(parent, modal, null);
  }

  /** Create when {@code existing} is null; edit when non-null. */
  public SalaryRateDialog(java.awt.Frame parent, boolean modal, SalaryRate existing) {
    super(parent, modal);
    initComponents();
    this.editing = existing;
    setTitle(existing == null ? "New Salary Rate" : "Edit Salary Rate");
    if (existing != null) {
      prefill(existing);
    }
    setupActions();
    loadCombos();
  }

  /**
   * Combo entry holding the database id behind a readable label.
   * toString() is what Swing renders, so the dropdown shows the label
   * while getValue() gives the FK id to save.
   */
  private record ComboItem(Long value, String label) {
    @Override
    public String toString() {
      return label;
    }
  }

  /** The role combo is loaded from the database — never hardcode the items. */
  private void loadCombos() {
    UiThread.executeAsync("Load salary rate combos",
            () -> RoleRepository.listAll(),
            this::fillRoles,
            ex -> ErrorHandler.handle(this, ex));
  }

  private void fillRoles(java.util.List<Role> roles) {
    comboRole.removeAllItems();
    for (Role role : roles) {
      comboRole.addItem(new ComboItem(role.getId(), role.getName()));
    }
    if (editing == null) {
      return;
    }
    selectByValue(editing.getRoleId());
  }

  private void selectByValue(Long value) {
    for (int i = 0; i < comboRole.getItemCount(); i++) {
      ComboItem item = comboRole.getItemAt(i);
      if (java.util.Objects.equals(item.value(), value)) {
        comboRole.setSelectedIndex(i);
        return;
      }
    }
  }

  private Long selectedRoleId() {
    ComboItem item = (ComboItem) comboRole.getSelectedItem();
    return item == null ? null : item.value();
  }

  private void prefill(SalaryRate salaryRate) {
    textBaseSalary.setText(salaryRate.getBaseSalary() == null
            ? "" : salaryRate.getBaseSalary().toPlainString());
    textCeilSalary.setText(salaryRate.getCeilSalary() == null
            ? "" : salaryRate.getCeilSalary().toPlainString());
    if (salaryRate.getCurrency() != null) {
      comboCurrency.setSelectedItem(salaryRate.getCurrency());
    }
    if (salaryRate.getPayFrequency() != null) {
      comboPayFrequency.setSelectedItem(salaryRate.getPayFrequency());
    }
    textRemark.setText(salaryRate.getRemark() == null ? "" : salaryRate.getRemark());
    // Role selection is applied in fillRoles() once the combo has loaded.
  }

  private void setupActions() {
    // The form's GEN block has no event handlers, so both buttons are wired here.
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
            ? Permissions.SALARY_RATE_UPDATE : Permissions.SALARY_RATE_CREATE);
  }

  private void saveAndClose() {
    try {
      requireSavePermission(editing != null);

      java.util.List<String> errors = new java.util.ArrayList<>();
      Long roleId = selectedRoleId();
      if (roleId == null) {
        errors.add("Role is required.");
      }

      java.math.BigDecimal baseSalary = parseDecimal(
              textBaseSalary.getText(), "Base Salary", true, errors);
      java.math.BigDecimal ceilSalary = parseDecimal(
              textCeilSalary.getText(), "Ceil Salary", true, errors);
      if (baseSalary != null && ceilSalary != null && ceilSalary.compareTo(baseSalary) < 0) {
        errors.add("Ceil Salary cannot be less than Base Salary.");
      }

      String currency = comboCurrency.getSelectedItem() == null
              ? "" : comboCurrency.getSelectedItem().toString().trim();
      if (currency.isEmpty()) {
        errors.add("Currency is required.");
      } else if (currency.length() > 10) {
        errors.add("Currency must be at most 10 characters.");
      }

      String payFrequency = comboPayFrequency.getSelectedItem() == null
              ? "" : comboPayFrequency.getSelectedItem().toString().trim();
      if (payFrequency.isEmpty()) {
        errors.add("Pay Frequency is required.");
      } else if (payFrequency.length() > 20) {
        errors.add("Pay Frequency must be at most 20 characters.");
      }

      String remark = optionalText(textRemark.getText(), "Remark", 500, errors);

      if (!errors.isEmpty()) {
        throw new ValidationException(errors);
      }

      Long userId = SessionContext.isAuthenticated() ? SessionContext.currentUserId() : null;
      boolean isEdit = editing != null;
      SalaryRate salaryRate;
      if (isEdit) {
        salaryRate = editing;
        salaryRate.setRoleId(roleId);
        salaryRate.setBaseSalary(baseSalary);
        salaryRate.setCeilSalary(ceilSalary);
        salaryRate.setCurrency(currency);
        salaryRate.setPayFrequency(payFrequency);
        salaryRate.setRemark(remark);
        salaryRate.setModifiedBy(userId);
      } else {
        salaryRate = SalaryRate.create(roleId, baseSalary, ceilSalary,
                currency, payFrequency, remark, userId);
      }

      btnSave.setEnabled(false);
      UiThread.executeAsync(isEdit ? "Update salary rate" : "Save salary rate",
              () -> isEdit
                      ? SalaryRateRepository.update(salaryRate)
                      : SalaryRateRepository.insert(salaryRate),
              result -> {
                if (isEdit) {
                  LOG.info("Salary rate updated id={}", salaryRate.getId());
                } else {
                  LOG.info("Salary rate saved with id {}", result);
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
    labelRole = new javax.swing.JLabel();
    comboRole = new javax.swing.JComboBox<>();
    labelBaseSalary = new javax.swing.JLabel();
    textBaseSalary = new javax.swing.JTextField();
    labelCeilSalary = new javax.swing.JLabel();
    textCeilSalary = new javax.swing.JTextField();
    labelCurrency = new javax.swing.JLabel();
    labelPayFrequency = new javax.swing.JLabel();
    labelRemark = new javax.swing.JLabel();
    textRemark = new javax.swing.JTextField();
    comboPayFrequency = new javax.swing.JComboBox<>();
    comboCurrency = new javax.swing.JComboBox<>();

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

    labelRole.setText("Role");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(7, 0, 7, 19);
    body.add(labelRole, gridBagConstraints);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(7, 0, 7, 0);
    body.add(comboRole, gridBagConstraints);

    labelBaseSalary.setText("Base Salary");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(7, 0, 7, 19);
    body.add(labelBaseSalary, gridBagConstraints);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(7, 0, 7, 0);
    body.add(textBaseSalary, gridBagConstraints);

    labelCeilSalary.setText("Ceil Salary");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(7, 0, 7, 19);
    body.add(labelCeilSalary, gridBagConstraints);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(7, 0, 7, 0);
    body.add(textCeilSalary, gridBagConstraints);

    labelCurrency.setText("Currency");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(7, 0, 7, 19);
    body.add(labelCurrency, gridBagConstraints);

    labelPayFrequency.setText("Pay Frequency");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(7, 0, 7, 19);
    body.add(labelPayFrequency, gridBagConstraints);

    labelRemark.setText("Remark");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 5;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(7, 0, 7, 19);
    body.add(labelRemark, gridBagConstraints);

    textRemark.setPreferredSize(new java.awt.Dimension(200, 26));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 5;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(7, 0, 7, 0);
    body.add(textRemark, gridBagConstraints);

    comboPayFrequency.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Monthly", "Yearly" }));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(8, 0, 7, 0);
    body.add(comboPayFrequency, gridBagConstraints);

    comboCurrency.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "MMK", "USD" }));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(8, 0, 7, 0);
    body.add(comboCurrency, gridBagConstraints);

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
        SalaryRateDialog dialog = new SalaryRateDialog(new javax.swing.JFrame(), true);
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
  private javax.swing.JComboBox<String> comboCurrency;
  private javax.swing.JComboBox<String> comboPayFrequency;
  private javax.swing.JComboBox<ComboItem> comboRole;
  private javax.swing.JPanel footer;
  private javax.swing.JLabel labelBaseSalary;
  private javax.swing.JLabel labelCeilSalary;
  private javax.swing.JLabel labelCurrency;
  private javax.swing.JLabel labelPayFrequency;
  private javax.swing.JLabel labelRemark;
  private javax.swing.JLabel labelRole;
  private javax.swing.JTextField textBaseSalary;
  private javax.swing.JTextField textCeilSalary;
  private javax.swing.JTextField textRemark;
  // End of variables declaration//GEN-END:variables
}
