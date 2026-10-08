/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/GUIForms/JDialog.java to edit this template
 */
package hrms.ui.staff_management;

import hrms.component.AutoCompleteCombo;
import hrms.exception.AuthorizationException;
import hrms.exception.ErrorHandler;
import hrms.exception.ValidationException;
import hrms.model.FamilyMember;
import hrms.repository.FamilyMemberRepository;
import hrms.security.Permissions;
import hrms.security.SecurityService;
import hrms.security.SessionContext;
import hrms.util.UiThread;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author aungmrat
 */
public class FamilyMemberDialog extends javax.swing.JDialog {
  
  private static final java.util.logging.Logger logger = java.util.logging.Logger.getLogger(FamilyMemberDialog.class.getName());

  /** null = create mode. */
  private final FamilyMember editing;

  /**
   * Label (CODE — Name) → staff id for the generated {@code JComboBox<String>}
   * staff combo; the generated type parameters stay untouched.
   */
  private final Map<String, Long> staffIdByLabel = new HashMap<>();

  /**
   * Creates new form FamilyMemberDialog
   */
  public FamilyMemberDialog(java.awt.Frame parent, boolean modal) {
    this(parent, modal, null);
  }

  /** Create when {@code existing} is null; edit when non-null. */
  public FamilyMemberDialog(java.awt.Frame parent, boolean modal, FamilyMember existing) {
    super(parent, modal);
    initComponents();
    this.editing = existing;
    setTitle(existing == null ? "New Family Member" : "Edit Family Member");
    if (existing != null) {
      textName.setText(nullToEmpty(existing.getName()));
      textRelationship.setText(nullToEmpty(existing.getRelationship()));
      setDate(dcDob, existing.getDob());
      textAddress.setText(nullToEmpty(existing.getAddress()));
      textPhone.setText(nullToEmpty(existing.getPhone()));
    }
    setupActions();
    loadCombos();
  }

  /**
   * The form's GEN block wires no event handlers, so Save/Cancel are
   * connected here — same pattern as TeamMemberDialog.
   */
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

  /** Loads staff async; fills the combo, then makes it type-to-search. */
  private void loadCombos() {
    UiThread.executeAsync("Load family member staff combo",
        FamilyMemberRepository::listStaffOptions,
        options -> {
          List<String> labels = new ArrayList<>();
          comboStaff.removeAllItems();
          staffIdByLabel.clear();
          for (FamilyMemberRepository.StaffOption option : options) {
            String label = option.label();
            labels.add(label);
            staffIdByLabel.put(label, option.id());
            comboStaff.addItem(label);
          }
          if (editing != null) {
            long staffId = editing.getStaffId();
            options.stream()
                .filter(o -> o.id() == staffId)
                .findFirst()
                .ifPresent(o -> comboStaff.setSelectedItem(o.label()));
            comboStaff.setEnabled(false);
          }
          AutoCompleteCombo.attach(comboStaff, labels);
        });
  }

  /**
   * Typed text → staff id via exact case-insensitive label match; null when
   * the text is blank or not a known staff label.
   */
  private Long selectedStaffId() {
    Object item = comboStaff.getSelectedItem();
    return AutoCompleteCombo.idForLabel(staffIdByLabel,
        item == null ? null : item.toString());
  }

  private void saveAndClose() {
    try {
      SecurityService.require(Permissions.STAFF_UPDATE);

      String staffText = String.valueOf(comboStaff.getSelectedItem()).trim();
      Long staffId = selectedStaffId();
      String name = textName.getText() == null ? "" : textName.getText().trim();
      String relationship = textRelationship.getText() == null ? "" : textRelationship.getText().trim();
      String address = textAddress.getText() == null ? "" : textAddress.getText().trim();
      String phone = textPhone.getText() == null ? "" : textPhone.getText().trim();
      LocalDate dob = toLocalDate(dcDob.getDate());

      List<String> errors = new ArrayList<>();
      if (staffText.isEmpty()) {
        errors.add("Staff is required.");
      } else if (staffId == null) {
        errors.add("Select staff from the list.");
      }
      if (name.isEmpty()) {
        errors.add("Name is required.");
      } else if (name.length() > 100) {
        errors.add("Name must be at most 100 characters.");
      }
      if (relationship.isEmpty()) {
        errors.add("Relationship is required.");
      } else if (relationship.length() > 50) {
        errors.add("Relationship must be at most 50 characters.");
      }
      if (dob != null && dob.isAfter(LocalDate.now())) {
        errors.add("DOB cannot be in the future.");
      }
      if (address.length() > 500) {
        errors.add("Address must be at most 500 characters.");
      }
      if (phone.length() > 20) {
        errors.add("Phone must be at most 20 characters.");
      }
      if (!errors.isEmpty()) {
        throw new ValidationException(errors);
      }

      Long userId = SessionContext.isAuthenticated() ? SessionContext.currentUserId() : null;
      boolean isEdit = editing != null;
      FamilyMember record;
      if (isEdit) {
        record = editing;
        record.setName(name);
        record.setRelationship(relationship);
        record.setDob(dob);
        record.setAddress(emptyToNull(address));
        record.setPhone(emptyToNull(phone));
      } else {
        record = FamilyMember.create(staffId, name, relationship, dob,
            emptyToNull(address), emptyToNull(phone), userId == null ? 0L : userId);
      }

      btnSave.setEnabled(false);
      UiThread.executeAsync(isEdit ? "Update family member" : "Save family member",
          () -> {
            if (isEdit) {
              FamilyMemberRepository.update(record, userId == null ? 0L : userId);
            } else {
              record.setId(FamilyMemberRepository.insert(record));
            }
            return record;
          },
          saved -> {
            logger.info("Family member saved with id " + saved.getId()
                + " for staff " + saved.getStaffId());
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

  private static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }

  private static String emptyToNull(String value) {
    return value == null || value.isEmpty() ? null : value;
  }

  private static LocalDate toLocalDate(java.util.Date date) {
    return date == null ? null : new java.sql.Date(date.getTime()).toLocalDate();
  }

  private static void setDate(com.toedter.calendar.JDateChooser chooser, LocalDate value) {
    chooser.setDate(value == null ? null : java.sql.Date.valueOf(value));
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
    labelName = new javax.swing.JLabel();
    textName = new javax.swing.JTextField();
    labelRelationship = new javax.swing.JLabel();
    textRelationship = new javax.swing.JTextField();
    labelDob = new javax.swing.JLabel();
    dcDob = new com.toedter.calendar.JDateChooser();
    labelAddress = new javax.swing.JLabel();
    jScrollPane1 = new javax.swing.JScrollPane();
    textAddress = new javax.swing.JTextArea();
    labelPhone = new javax.swing.JLabel();
    textPhone = new javax.swing.JTextField();

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

    labelStaff.setText("Staff");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(8, 0, 3, 15);
    body.add(labelStaff, gridBagConstraints);

    comboStaff.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(8, 0, 3, 0);
    body.add(comboStaff, gridBagConstraints);

    labelName.setText("Name");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(8, 0, 3, 15);
    body.add(labelName, gridBagConstraints);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(8, 0, 3, 0);
    body.add(textName, gridBagConstraints);

    labelRelationship.setText("Relationship");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(8, 0, 3, 15);
    body.add(labelRelationship, gridBagConstraints);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(8, 0, 3, 0);
    body.add(textRelationship, gridBagConstraints);

    labelDob.setText("DOB");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(8, 0, 3, 15);
    body.add(labelDob, gridBagConstraints);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(8, 0, 3, 0);
    body.add(dcDob, gridBagConstraints);

    labelAddress.setText("Address");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHEAST;
    gridBagConstraints.insets = new java.awt.Insets(8, 0, 3, 15);
    body.add(labelAddress, gridBagConstraints);

    textAddress.setColumns(20);
    textAddress.setRows(5);
    jScrollPane1.setViewportView(textAddress);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(8, 0, 3, 0);
    body.add(jScrollPane1, gridBagConstraints);

    labelPhone.setText("Phone");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 5;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(8, 0, 3, 15);
    body.add(labelPhone, gridBagConstraints);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 5;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(8, 0, 3, 0);
    body.add(textPhone, gridBagConstraints);

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
      logger.log(java.util.logging.Level.SEVERE, null, ex);
    }
    //</editor-fold>

    /* Create and display the dialog */
    java.awt.EventQueue.invokeLater(new Runnable() {
      @Override
      public void run() {
        FamilyMemberDialog dialog = new FamilyMemberDialog(new javax.swing.JFrame(), true);
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
  private javax.swing.JComboBox<String> comboStaff;
  private com.toedter.calendar.JDateChooser dcDob;
  private javax.swing.JPanel footer;
  private javax.swing.JScrollPane jScrollPane1;
  private javax.swing.JLabel labelAddress;
  private javax.swing.JLabel labelDob;
  private javax.swing.JLabel labelName;
  private javax.swing.JLabel labelPhone;
  private javax.swing.JLabel labelRelationship;
  private javax.swing.JLabel labelStaff;
  private javax.swing.JTextArea textAddress;
  private javax.swing.JTextField textName;
  private javax.swing.JTextField textPhone;
  private javax.swing.JTextField textRelationship;
  // End of variables declaration//GEN-END:variables
}
