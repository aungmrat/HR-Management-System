/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/GUIForms/JDialog.java to edit this template
 */
package hrms.ui.user_management;

import hrms.exception.AuthorizationException;
import hrms.exception.ErrorHandler;
import hrms.exception.ValidationException;
import hrms.repository.UserManagementRepository;
import hrms.security.Permissions;
import hrms.security.SecurityService;
import hrms.security.SessionContext;
import hrms.service.AuthService;
import hrms.util.UiThread;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author aungmrat
 */
public class UserDialog extends javax.swing.JDialog {

  private static final Logger LOG = LoggerFactory.getLogger(UserDialog.class);

  /** null = create mode. */
  private final UserManagementRepository.UserRow editing;

  public UserDialog(java.awt.Frame parent, boolean modal) {
    this(parent, modal, null);
  }

  /** Create when {@code existing} is null; edit when non-null. */
  public UserDialog(java.awt.Frame parent, boolean modal,
          UserManagementRepository.UserRow existing) {
    super(parent, modal);
    initComponents();
    labelPasswordPolicyHint.setText(
            "<html><div style='width:260px'>At least 8 characters "
            + "with an uppercase letter, a lowercase letter and a digit.</div></html>");
    labelPasswordPolicyHint.setFont(labelPasswordPolicyHint.getFont()
            .deriveFont(java.awt.Font.PLAIN, 11f));
    this.editing = existing;
    if (existing == null) {
      setTitle("New User");
    } else {
      setTitle("Edit User");
      prefill(existing);
      // Passwords are never edited here; resets happen through a dedicated action.
      labelInitialPassword.setVisible(false);
      passInitialPassword.setVisible(false);
      labelPasswordPolicyHint.setVisible(false);
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

  /** Apply stored values first; combo selections are applied after loadCombos(). */
  private void prefill(UserManagementRepository.UserRow row) {
    textUserName.setText(row.username());
    textEmail.setText(row.email());
    if (row.status() != null && !row.status().isBlank()) {
      comboStatus.setSelectedItem(row.status());
    }
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
    SecurityService.require(Permissions.USER_MANAGE);
  }

  private void saveAndClose() {
    try {
      requireSavePermission(editing != null);
      boolean isEdit = editing != null;
      Long staffId = isEdit ? editing.staffId() : selectedValue(comboStaff);
      Long roleId = selectedValue(comboSystemRole);
      String username = textUserName.getText();
      String email = textEmail.getText();
      String status = comboStatus.getSelectedItem() == null
              ? "Active" : comboStatus.getSelectedItem().toString();
      char[] pwdChars = passInitialPassword.getPassword();
      String plainPassword = new String(pwdChars);
      java.util.Arrays.fill(pwdChars, '\0');

      java.util.List<String> errors = new java.util.ArrayList<>();
      if (!isEdit && staffId == null) {
        errors.add("Staff is required. A staff record without a user account must exist first.");
      }
      if (username == null || username.isBlank()) {
        errors.add("Username is required.");
      } else if (username.trim().length() > 50) {
        errors.add("Username must be at most 50 characters.");
      }
      if (email == null || email.isBlank()) {
        errors.add("Email is required.");
      } else if (!isValidEmail(email.trim())) {
        errors.add("Email is not valid.");
      }
      if (roleId == null) {
        errors.add("System role is required.");
      }
      if (!isEdit && (plainPassword == null || plainPassword.isBlank())) {
        errors.add("Initial password is required.");
      }
      if (!errors.isEmpty()) {
        throw new ValidationException(errors);
      }
      if (!isEdit) {
        AuthService.validateNewPassword(null, plainPassword);
      }

      Long actor = SessionContext.currentUserId();
      btnSave.setEnabled(false);
      UiThread.executeAsync(isEdit ? "Update user" : "Create user",
              () -> {
                if (isEdit) {
                  return UserManagementRepository.updateProfile(editing.id(), roleId,
                          username.trim(), email.trim(), status, actor);
                }
                return UserManagementRepository.insert(staffId, roleId,
                        username.trim(), email.trim(), plainPassword, status, actor);
              },
              result -> {
                if (isEdit) {
                  LOG.info("User updated id={}", editing.id());
                } else {
                  LOG.info("User created id={}", result);
                }
                dispose();
              },
              error -> {
                btnSave.setEnabled(true);
                ErrorHandler.handle(this, error);
              });
    } catch (ValidationException e) {
      btnSave.setEnabled(true);
      ErrorHandler.handle(this, e);
    }
  }

  private static boolean isValidEmail(String email) {
    return email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
  }

  /** Both combos are loaded from the database — never hardcode the items. */
  private void loadCombos() {
    UiThread.executeAsync("Load user combos",
            () -> new ComboData(UserManagementRepository.listRoles(),
                    UserManagementRepository.listStaffWithoutUser()),
            this::fillCombos);
  }

  /** Snapshot of both combo sources, loaded together in one background task. */
  private record ComboData(java.util.List<UserManagementRepository.RoleOption> roles,
          java.util.List<UserManagementRepository.StaffOption> staff) {
  }

  private void fillCombos(ComboData data) {
    comboSystemRole.removeAllItems();
    for (UserManagementRepository.RoleOption role : data.roles()) {
      comboSystemRole.addItem(new ComboItem(role.id(), role.name()));
    }

    comboStaff.removeAllItems();
    if (editing != null) {
      comboStaff.addItem(new ComboItem(editing.staffId(), editing.staffName()));
      comboStaff.setEnabled(false);
    } else {
      for (UserManagementRepository.StaffOption staff : data.staff()) {
        comboStaff.addItem(new ComboItem(staff.id(), staff.display()));
      }
    }

    if (editing == null) {
      return;
    }
    selectByValue(comboSystemRole, editing.roleId());
  }

  private void selectByValue(javax.swing.JComboBox<ComboItem> combo, Long value) {
    for (int i = 0; i < combo.getItemCount(); i++) {
      ComboItem item = combo.getItemAt(i);
      if (java.util.Objects.equals(item.value(), value)) {
        combo.setSelectedIndex(i);
        return;
      }
    }
  }

  private Long selectedValue(javax.swing.JComboBox<ComboItem> combo) {
    ComboItem item = (ComboItem) combo.getSelectedItem();
    return item == null ? null : item.value();
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
    labelUserName = new javax.swing.JLabel();
    textUserName = new javax.swing.JTextField();
    labelEmail = new javax.swing.JLabel();
    textEmail = new javax.swing.JTextField();
    labelSystemRole = new javax.swing.JLabel();
    comboSystemRole = new javax.swing.JComboBox<>();
    labelStatus = new javax.swing.JLabel();
    comboStatus = new javax.swing.JComboBox<>();
    labelInitialPassword = new javax.swing.JLabel();
    labelPasswordPolicyHint = new javax.swing.JLabel();
    passInitialPassword = new javax.swing.JPasswordField();

    setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);

    footer.setPreferredSize(new java.awt.Dimension(400, 60));
    footer.setLayout(new java.awt.GridBagLayout());

    btnSave.setText("Save");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
    gridBagConstraints.insets = new java.awt.Insets(18, 18, 15, 30);
    footer.add(btnSave, gridBagConstraints);

    btnCancel.setText("Cancel");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
    gridBagConstraints.insets = new java.awt.Insets(18, 200, 15, 0);
    footer.add(btnCancel, gridBagConstraints);

    getContentPane().add(footer, java.awt.BorderLayout.PAGE_END);

    body.setLayout(new java.awt.GridBagLayout());

    labelStaff.setText("Staff");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(14, 0, 11, 13);
    body.add(labelStaff, gridBagConstraints);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(14, 0, 11, 0);
    body.add(comboStaff, gridBagConstraints);

    labelUserName.setText("User Name");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(14, 0, 11, 13);
    body.add(labelUserName, gridBagConstraints);

    textUserName.setPreferredSize(new java.awt.Dimension(200, 26));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(14, 0, 11, 0);
    body.add(textUserName, gridBagConstraints);

    labelEmail.setText("Email");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(14, 0, 11, 13);
    body.add(labelEmail, gridBagConstraints);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(14, 0, 11, 0);
    body.add(textEmail, gridBagConstraints);

    labelSystemRole.setText("System Role");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(14, 0, 11, 13);
    body.add(labelSystemRole, gridBagConstraints);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(14, 0, 11, 0);
    body.add(comboSystemRole, gridBagConstraints);

    labelStatus.setText("Status");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(14, 0, 11, 13);
    body.add(labelStatus, gridBagConstraints);

    comboStatus.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Active", "Inactive" }));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(14, 0, 11, 0);
    body.add(comboStatus, gridBagConstraints);

    labelInitialPassword.setText("Initial Password");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 5;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(14, 0, 11, 13);
    body.add(labelInitialPassword, gridBagConstraints);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 6;
    gridBagConstraints.gridwidth = 2;
    body.add(labelPasswordPolicyHint, gridBagConstraints);

    passInitialPassword.addActionListener(this::passInitialPasswordActionPerformed);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 5;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    body.add(passInitialPassword, gridBagConstraints);

    getContentPane().add(body, java.awt.BorderLayout.CENTER);

    pack();
  }// </editor-fold>//GEN-END:initComponents

  private void passInitialPasswordActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_passInitialPasswordActionPerformed
    // TODO add your handling code here:
  }//GEN-LAST:event_passInitialPasswordActionPerformed

  // Variables declaration - do not modify//GEN-BEGIN:variables
  private javax.swing.JPanel body;
  private javax.swing.JButton btnCancel;
  private javax.swing.JButton btnSave;
  private javax.swing.JComboBox<ComboItem> comboStaff;
  private javax.swing.JComboBox<String> comboStatus;
  private javax.swing.JComboBox<ComboItem> comboSystemRole;
  private javax.swing.JPanel footer;
  private javax.swing.JLabel labelEmail;
  private javax.swing.JLabel labelInitialPassword;
  private javax.swing.JLabel labelPasswordPolicyHint;
  private javax.swing.JLabel labelStaff;
  private javax.swing.JLabel labelStatus;
  private javax.swing.JLabel labelSystemRole;
  private javax.swing.JLabel labelUserName;
  private javax.swing.JPasswordField passInitialPassword;
  private javax.swing.JTextField textEmail;
  private javax.swing.JTextField textUserName;
  // End of variables declaration//GEN-END:variables
}

