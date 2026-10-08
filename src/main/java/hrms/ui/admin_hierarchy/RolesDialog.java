/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/GUIForms/JDialog.java to edit this template
 */
package hrms.ui.admin_hierarchy;

import hrms.exception.AuthorizationException;
import hrms.exception.ErrorHandler;
import hrms.exception.ValidationException;
import hrms.model.Department;
import hrms.model.Role;
import hrms.repository.DepartmentRepository;
import hrms.repository.RoleRepository;
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
public class RolesDialog extends javax.swing.JDialog {

  private static final Logger LOG = LoggerFactory.getLogger(RolesDialog.class);

  /** null = create mode. */
  private final Role editing;

  /**
   * Creates new form RolesDialog
   */
  public RolesDialog(java.awt.Frame parent, boolean modal) {
    this(parent, modal, null);
  }

  /** Create when {@code existing} is null; edit when non-null. */
  public RolesDialog(java.awt.Frame parent, boolean modal, Role existing) {
    super(parent, modal);
    initComponents();
    this.editing = existing;
    if (existing == null) {
      setTitle("New Role");
    } else {
      setTitle("Edit Role");
      prefill(existing);
    }
    setupActions();
    loadCombos();
  }

  /** Apply stored values first; combo selections are applied after loadCombos(). */
  private void prefill(Role role) {
    textName.setText(role.getName() == null ? "" : role.getName());
    textRemark.setText(role.getRemark() == null ? "" : role.getRemark());
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
            ? Permissions.ROLE_UPDATE : Permissions.ROLE_CREATE);
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

  /** The department combo is loaded from the database — never hardcode the items. */
  private void loadCombos() {
    UiThread.executeAsync("Load role combos",
            DepartmentRepository::listAll,
            this::fillCombos);
  }

  private void fillCombos(java.util.List<Department> data) {
    comboDepartment.removeAllItems();
    for (Department department : data) {
      comboDepartment.addItem(new ComboItem(department.getId(),
              department.getCode() + " — " + department.getName()));
    }

    if (editing == null) {
      return;
    }
    selectByValue(comboDepartment, editing.getDepartmentId());
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

  private void saveAndClose() {
    try {
      requireSavePermission(editing != null);
      String name = textName.getText();
      String remark = textRemark.getText();
      Long departmentId = selectedValue(comboDepartment);

      java.util.List<String> errors = new java.util.ArrayList<>();
      if (departmentId == null) {
        errors.add("Department is required.");
      }
      if (name == null || name.isBlank()) {
        errors.add("Role name is required.");
      } else if (name.trim().length() > 100) {
        errors.add("Role name must be at most 100 characters.");
      }
      if (remark != null && remark.trim().length() > 500) {
        errors.add("Remark must be at most 500 characters.");
      }
      if (!errors.isEmpty()) {
        throw new ValidationException(errors);
      }

      Long userId = SessionContext.isAuthenticated() ? SessionContext.currentUserId() : null;
      boolean isEdit = editing != null;
      Role role;
      if (isEdit) {
        role = editing;
        role.setDepartmentId(departmentId);
        role.setName(name.trim());
        role.setRemark(remark);
        role.setModifiedBy(userId);
      } else {
        role = Role.create(departmentId, name.trim(), remark, userId);
      }

      btnSave.setEnabled(false);
      UiThread.executeAsync(isEdit ? "Update role" : "Save role",
              () -> isEdit
                      ? RoleRepository.update(role)
                      : RoleRepository.insert(role),
              result -> {
                if (isEdit) {
                  LOG.info("Role updated id={}", role.getId());
                } else {
                  LOG.info("Role saved with id {}", result);
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
    labelDepartment = new javax.swing.JLabel();
    comboDepartment = new javax.swing.JComboBox<>();
    labelRemark = new javax.swing.JLabel();
    jScrollPane1 = new javax.swing.JScrollPane();
    textRemark = new javax.swing.JTextArea();

    setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
    setPreferredSize(new java.awt.Dimension(400, 380));

    footer.setPreferredSize(new java.awt.Dimension(350, 60));
    footer.setLayout(new java.awt.GridBagLayout());

    btnSave.setText("Save");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
    gridBagConstraints.insets = new java.awt.Insets(16, 18, 17, 40);
    footer.add(btnSave, gridBagConstraints);

    btnCancel.setText("Cancel");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
    gridBagConstraints.insets = new java.awt.Insets(16, 140, 17, 0);
    footer.add(btnCancel, gridBagConstraints);

    getContentPane().add(footer, java.awt.BorderLayout.PAGE_END);

    body.setPreferredSize(new java.awt.Dimension(400, 350));
    body.setLayout(new java.awt.GridBagLayout());

    labelName.setText("Name");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(14, 0, 4, 18);
    body.add(labelName, gridBagConstraints);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(14, 0, 4, 0);
    body.add(textName, gridBagConstraints);

    labelDepartment.setText("Department");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(14, 0, 4, 18);
    body.add(labelDepartment, gridBagConstraints);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(14, 0, 4, 0);
    body.add(comboDepartment, gridBagConstraints);

    labelRemark.setText("Remark");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHEAST;
    gridBagConstraints.insets = new java.awt.Insets(14, 0, 4, 18);
    body.add(labelRemark, gridBagConstraints);

    textRemark.setColumns(20);
    textRemark.setRows(5);
    jScrollPane1.setViewportView(textRemark);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(14, 0, 4, 0);
    body.add(jScrollPane1, gridBagConstraints);

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
        RolesDialog dialog = new RolesDialog(new javax.swing.JFrame(), true);
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
  private javax.swing.JComboBox<ComboItem> comboDepartment;
  private javax.swing.JPanel footer;
  private javax.swing.JScrollPane jScrollPane1;
  private javax.swing.JLabel labelDepartment;
  private javax.swing.JLabel labelName;
  private javax.swing.JLabel labelRemark;
  private javax.swing.JTextField textName;
  private javax.swing.JTextArea textRemark;
  // End of variables declaration//GEN-END:variables
}
