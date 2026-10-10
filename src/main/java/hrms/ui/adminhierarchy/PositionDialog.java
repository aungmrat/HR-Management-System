/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/GUIForms/JDialog.java to edit this template
 */
package hrms.ui.adminhierarchy;

import hrms.exception.AuthorizationException;
import hrms.exception.ErrorHandler;
import hrms.exception.ValidationException;
import hrms.model.Department;
import hrms.model.Position;
import hrms.model.Role;
import hrms.repository.DepartmentRepository;
import hrms.repository.PositionRepository;
import hrms.repository.RoleRepository;
import hrms.security.Permissions;
import hrms.security.SecurityService;
import hrms.security.SessionContext;
import hrms.util.UiThread;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author user
 */
public class PositionDialog extends javax.swing.JDialog {

    private static final Logger LOG = LoggerFactory.getLogger(PositionDialog.class);

    /** null = create mode. */
    private final Position editing;

    /**
     * Creates new form PositionDialog
     */
    public PositionDialog(java.awt.Frame parent, boolean modal) {
        this(parent, modal, null);
    }

    /** Create when {@code existing} is null; edit when non-null. */
    public PositionDialog(java.awt.Frame parent, boolean modal, Position existing) {
        super(parent, modal);
        initComponents();
        this.editing = existing;
        if (existing == null) {
            setTitle("New Position");
        } else {
            setTitle("Edit Position");
            prefill(existing);
        }
        setupActions();
        loadCombos();
    }

    /** Apply stored values first; combo selections are applied after loadCombos(). */
    private void prefill(Position position) {
        textName.setText(position.getName() == null ? "" : position.getName());
        textRemark.setText(position.getRemark() == null ? "" : position.getRemark());
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
                ? Permissions.POSITION_UPDATE : Permissions.POSITION_CREATE);
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

    /** Snapshot so both combos load in one background call. */
    private record ComboData(java.util.List<Department> departments,
            java.util.List<Role> roles) {
    }

    /** Both combos are loaded from the database — never hardcode the items. */
    private void loadCombos() {
        UiThread.executeAsync("Load position combos",
                () -> new ComboData(DepartmentRepository.listAll(),
                        RoleRepository.listAll()),
                this::fillCombos);
    }

    private void fillCombos(ComboData data) {
        comboDepartment.removeAllItems();
        for (Department department : data.departments()) {
            comboDepartment.addItem(new ComboItem(department.getId(),
                    department.getCode() + " — " + department.getName()));
        }
        comboRole.removeAllItems();
        for (Role role : data.roles()) {
            comboRole.addItem(new ComboItem(role.getId(), role.getName()));
        }

        if (editing == null) {
            return;
        }
        selectByValue(comboDepartment, editing.getDepartmentId());
        selectByValue(comboRole, editing.getRoleId());
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
            Long roleId = selectedValue(comboRole);

            java.util.List<String> errors = new java.util.ArrayList<>();
            if (departmentId == null) {
                errors.add("Department is required.");
            }
            if (roleId == null) {
                errors.add("Role is required.");
            }
            if (name == null || name.isBlank()) {
                errors.add("Position name is required.");
            } else if (name.trim().length() > 100) {
                errors.add("Position name must be at most 100 characters.");
            }
            if (remark != null && remark.trim().length() > 500) {
                errors.add("Remark must be at most 500 characters.");
            }
            if (!errors.isEmpty()) {
                throw new ValidationException(errors);
            }

            Long userId = SessionContext.isAuthenticated() ? SessionContext.currentUserId() : null;
            boolean isEdit = editing != null;
            Position position;
            if (isEdit) {
                position = editing;
                position.setDepartmentId(departmentId);
                position.setRoleId(roleId);
                position.setName(name.trim());
                position.setRemark(remark);
                position.setModifiedBy(userId);
            } else {
                position = Position.create(departmentId, roleId, name.trim(), remark, userId);
            }

            btnSave.setEnabled(false);
            UiThread.executeAsync(isEdit ? "Update position" : "Save position",
                    () -> isEdit
                            ? PositionRepository.update(position)
                            : PositionRepository.insert(position),
                    result -> {
                        if (isEdit) {
                            LOG.info("Position updated id={}", position.getId());
                        } else {
                            LOG.info("Position saved with id {}", result);
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
    labelRole = new javax.swing.JLabel();
    comboRole = new javax.swing.JComboBox<>();
    labelRemark = new javax.swing.JLabel();
    jScrollPane1 = new javax.swing.JScrollPane();
    textRemark = new javax.swing.JTextArea();

    setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);

    footer.setPreferredSize(new java.awt.Dimension(400, 60));
    footer.setLayout(new java.awt.GridBagLayout());

    btnSave.setText("Save");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
    gridBagConstraints.insets = new java.awt.Insets(22, 18, 15, 40);
    footer.add(btnSave, gridBagConstraints);

    btnCancel.setText("Cancel");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
    gridBagConstraints.insets = new java.awt.Insets(22, 198, 15, 0);
    footer.add(btnCancel, gridBagConstraints);

    getContentPane().add(footer, java.awt.BorderLayout.PAGE_END);

    body.setLayout(new java.awt.GridBagLayout());

    labelName.setText("Name");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(11, 0, 9, 18);
    body.add(labelName, gridBagConstraints);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(11, 0, 9, 0);
    body.add(textName, gridBagConstraints);

    labelDepartment.setText("Department");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(11, 0, 9, 18);
    body.add(labelDepartment, gridBagConstraints);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(11, 0, 9, 0);
    body.add(comboDepartment, gridBagConstraints);

    labelRole.setText("Role");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(11, 0, 9, 18);
    body.add(labelRole, gridBagConstraints);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(11, 0, 9, 0);
    body.add(comboRole, gridBagConstraints);

    labelRemark.setText("Remark");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHEAST;
    gridBagConstraints.insets = new java.awt.Insets(11, 0, 9, 18);
    body.add(labelRemark, gridBagConstraints);

    textRemark.setColumns(20);
    textRemark.setRows(5);
    jScrollPane1.setViewportView(textRemark);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(11, 0, 9, 0);
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
                PositionDialog dialog = new PositionDialog(new javax.swing.JFrame(), true);
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
  private javax.swing.JComboBox<ComboItem> comboRole;
  private javax.swing.JPanel footer;
  private javax.swing.JScrollPane jScrollPane1;
  private javax.swing.JLabel labelDepartment;
  private javax.swing.JLabel labelName;
  private javax.swing.JLabel labelRemark;
  private javax.swing.JLabel labelRole;
  private javax.swing.JTextField textName;
  private javax.swing.JTextArea textRemark;
  // End of variables declaration//GEN-END:variables
}
