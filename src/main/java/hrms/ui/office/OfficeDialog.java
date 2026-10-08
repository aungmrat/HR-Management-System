/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/GUIForms/JDialog.java to edit this template
 */
package hrms.ui.office;

import hrms.exception.AuthorizationException;
import hrms.exception.ErrorHandler;
import hrms.exception.ValidationException;
import hrms.model.Department;
import hrms.model.Office;
import hrms.repository.DepartmentRepository;
import hrms.repository.OfficeRepository;
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
public class OfficeDialog extends javax.swing.JDialog {

  private static final Logger LOG = LoggerFactory.getLogger(OfficeDialog.class);

  /** null = create mode. */
  private final Office editing;

  public OfficeDialog(java.awt.Frame parent, boolean modal) {
    this(parent, modal, null);
  }

  /** Create when {@code existing} is null; edit when non-null. */
  public OfficeDialog(java.awt.Frame parent, boolean modal, Office existing) {
    super(parent, modal);
    initComponents();
    this.editing = existing;
    if (existing == null) {
      setTitle("New Office");
    } else {
      setTitle("Edit Office");
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

  /** Apply stored values first; combo selections are applied after loadCombos(). */
  private void prefill(Office office) {
    textCode.setText(office.getCode());
    textName.setText(office.getName());
    textContactNo.setText(office.getContactNo() == null ? "" : office.getContactNo());
    textAddress.setText(office.getAddress() == null ? "" : office.getAddress());
    if (office.getStatus() != null && !office.getStatus().isBlank()) {
      comboStatus.setSelectedItem(office.getStatus());
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
    SecurityService.require(isEdit
            ? Permissions.OFFICE_UPDATE : Permissions.OFFICE_CREATE);
  }

  /** Both combos are loaded from the database — never hardcode the items. */
  private void loadCombos() {
    UiThread.executeAsync("Load office combos",
            () -> new ComboData(DepartmentRepository.listAll(), OfficeRepository.listAll()),
            this::fillCombos);
  }

  /** Snapshot of both combo sources, loaded together in one background task. */
  private record ComboData(java.util.List<Department> departments,
                           java.util.List<Office> offices) {
  }

  private void fillCombos(ComboData data) {
    // Department combo: required, so no empty option.
    comboDep.removeAllItems();
    for (Department department : data.departments()) {
      comboDep.addItem(new ComboItem(department.getId(),
              department.getCode() + " — " + department.getName()));
    }

    // Parent office combo: optional, root offices pick "— None —" (null id).
    comboParentOffice.removeAllItems();
    comboParentOffice.addItem(new ComboItem(null, "— None —"));
    for (Office office : data.offices()) {
      // Edit mode must not offer the office itself as its own parent (cycle).
      if (editing != null && office.getId() == editing.getId()) {
        continue;
      }
      comboParentOffice.addItem(new ComboItem(office.getId(),
              office.getCode() + " — " + office.getName()));
    }

    if (editing == null) {
      return;
    }
    selectByValue(comboDep, editing.getDepartmentId());
    selectByValue(comboParentOffice, editing.getParentId());
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
      String code = textCode.getText();
      String name = textName.getText();
      String contactNo = textContactNo.getText();
      String address = textAddress.getText();
      String status = comboStatus.getSelectedItem() == null
              ? "Active" : comboStatus.getSelectedItem().toString().trim();
      Long departmentId = selectedValue(comboDep);
      Long parentId = selectedValue(comboParentOffice);

      java.util.List<String> errors = new java.util.ArrayList<>();
      if (departmentId == null) {
        errors.add("Department is required.");
      }
      if (code == null || code.isBlank()) {
        errors.add("Code is required.");
      } else if (code.trim().length() > 20) {
        errors.add("Code must be at most 20 characters.");
      }
      if (name == null || name.isBlank()) {
        errors.add("Office name is required.");
      } else if (name.trim().length() > 100) {
        errors.add("Office name must be at most 100 characters.");
      }
      if (contactNo != null && contactNo.trim().length() > 20) {
        errors.add("Contact No must be at most 20 characters.");
      }
      if (address != null && address.trim().length() > 500) {
        errors.add("Address must be at most 500 characters.");
      }
      if (!errors.isEmpty()) {
        throw new ValidationException(errors);
      }

      Long userId = SessionContext.isAuthenticated() ? SessionContext.currentUserId() : null;
      boolean isEdit = editing != null;
      Office office;
      if (isEdit) {
        office = editing;
        office.setDepartmentId(departmentId);
        office.setCode(code.trim());
        office.setParentId(parentId);
        office.setName(name.trim());
        office.setContactNo(contactNo);
        office.setAddress(address);
        office.setStatus(status);
        office.setModifiedBy(userId);
      } else {
        office = Office.create(departmentId, code.trim(), parentId, name.trim(),
                contactNo, address, status, userId);
      }

      btnSave.setEnabled(false);
      UiThread.executeAsync(isEdit ? "Update office" : "Save office",
              () -> isEdit
                      ? OfficeRepository.update(office)
                      : OfficeRepository.insert(office),
              result -> {
                if (isEdit) {
                  LOG.info("Office updated id={}", office.getId());
                } else {
                  LOG.info("Office saved with id {}", result);
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
    btnCancel = new javax.swing.JButton();
    btnSave = new javax.swing.JButton();
    form = new javax.swing.JPanel();
    labelDep = new javax.swing.JLabel();
    comboDep = new javax.swing.JComboBox<>();
    labelCode = new javax.swing.JLabel();
    textCode = new javax.swing.JTextField();
    labelName = new javax.swing.JLabel();
    textName = new javax.swing.JTextField();
    labelParentOffice = new javax.swing.JLabel();
    comboParentOffice = new javax.swing.JComboBox<>();
    labelContactNo = new javax.swing.JLabel();
    textContactNo = new javax.swing.JTextField();
    labelAddress = new javax.swing.JLabel();
    jScrollPane1 = new javax.swing.JScrollPane();
    textAddress = new javax.swing.JTextArea();
    labelStatus = new javax.swing.JLabel();
    comboStatus = new javax.swing.JComboBox<>();

    setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
    setPreferredSize(new java.awt.Dimension(470, 500));

    footer.setPreferredSize(new java.awt.Dimension(450, 60));

    btnCancel.setText("Cancel");

    btnSave.setText("Save");

    javax.swing.GroupLayout footerLayout = new javax.swing.GroupLayout(footer);
    footer.setLayout(footerLayout);
    footerLayout.setHorizontalGroup(
      footerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, footerLayout.createSequentialGroup()
        .addContainerGap(238, Short.MAX_VALUE)
        .addComponent(btnCancel)
        .addGap(26, 26, 26)
        .addComponent(btnSave)
        .addGap(34, 34, 34))
    );
    footerLayout.setVerticalGroup(
      footerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGroup(footerLayout.createSequentialGroup()
        .addGap(14, 14, 14)
        .addGroup(footerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
          .addComponent(btnCancel)
          .addComponent(btnSave))
        .addContainerGap(19, Short.MAX_VALUE))
    );

    getContentPane().add(footer, java.awt.BorderLayout.PAGE_END);

    form.setLayout(new java.awt.GridBagLayout());

    labelDep.setText("Department");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(1, 0, 13, 22);
    form.add(labelDep, gridBagConstraints);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(1, 0, 13, 0);
    form.add(comboDep, gridBagConstraints);

    labelCode.setText("Code");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(1, 0, 13, 22);
    form.add(labelCode, gridBagConstraints);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(1, 0, 13, 0);
    form.add(textCode, gridBagConstraints);

    labelName.setText("Name");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(1, 0, 13, 22);
    form.add(labelName, gridBagConstraints);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(1, 0, 13, 0);
    form.add(textName, gridBagConstraints);

    labelParentOffice.setText("Parent Office");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(1, 0, 13, 22);
    form.add(labelParentOffice, gridBagConstraints);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(1, 0, 13, 0);
    form.add(comboParentOffice, gridBagConstraints);

    labelContactNo.setText("Contact No");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(1, 0, 13, 22);
    form.add(labelContactNo, gridBagConstraints);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(1, 0, 13, 0);
    form.add(textContactNo, gridBagConstraints);

    labelAddress.setText("Address");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 5;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHEAST;
    gridBagConstraints.insets = new java.awt.Insets(1, 0, 13, 22);
    form.add(labelAddress, gridBagConstraints);

    textAddress.setColumns(20);
    textAddress.setRows(5);
    jScrollPane1.setViewportView(textAddress);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 5;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(1, 0, 13, 0);
    form.add(jScrollPane1, gridBagConstraints);

    labelStatus.setText("Status");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 6;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(1, 0, 13, 22);
    form.add(labelStatus, gridBagConstraints);

    comboStatus.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Active", "Inactive" }));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 6;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(1, 0, 13, 0);
    form.add(comboStatus, gridBagConstraints);

    getContentPane().add(form, java.awt.BorderLayout.CENTER);

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
        OfficeDialog dialog = new OfficeDialog(new javax.swing.JFrame(), true);
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
  private javax.swing.JButton btnCancel;
  private javax.swing.JButton btnSave;
  private javax.swing.JComboBox<ComboItem> comboDep;
  private javax.swing.JComboBox<ComboItem> comboParentOffice;
  private javax.swing.JComboBox<String> comboStatus;
  private javax.swing.JPanel footer;
  private javax.swing.JPanel form;
  private javax.swing.JScrollPane jScrollPane1;
  private javax.swing.JLabel labelAddress;
  private javax.swing.JLabel labelCode;
  private javax.swing.JLabel labelContactNo;
  private javax.swing.JLabel labelDep;
  private javax.swing.JLabel labelName;
  private javax.swing.JLabel labelParentOffice;
  private javax.swing.JLabel labelStatus;
  private javax.swing.JTextArea textAddress;
  private javax.swing.JTextField textCode;
  private javax.swing.JTextField textContactNo;
  private javax.swing.JTextField textName;
  // End of variables declaration//GEN-END:variables
}

