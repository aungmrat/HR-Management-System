/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/GUIForms/JDialog.java to edit this template
 */
package hrms.ui.staff_management;

import hrms.component.AutoCompleteCombo;
import hrms.exception.AuthorizationException;
import hrms.exception.ErrorHandler;
import hrms.exception.ValidationException;
import hrms.model.MilitaryService;
import hrms.repository.MilitaryServiceRepository;
import hrms.security.Permissions;
import hrms.security.SecurityService;
import hrms.security.SessionContext;
import hrms.util.UiThread;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author aungmrat
 */
public class MilitaryServiceDialog extends javax.swing.JDialog {
  
  private static final java.util.logging.Logger logger = java.util.logging.Logger.getLogger(MilitaryServiceDialog.class.getName());

  /** Stored status values (badge-friendly, uppercase/underscore) in combo order. */
  private static final String[] STATUS_VALUES = {
      "COMPLETED", "IN_PROGRESS", "EXEMPTED", "POSTPONED"};

  /** null = create mode. */
  private final MilitaryService editing;

  /**
   * Label (CODE — Name) → staff id for the generated {@code JComboBox<String>}
   * staff combo; the generated type parameters stay untouched.
   */
  private final Map<String, Long> staffIdByLabel = new HashMap<>();

  /**
   * Creates new form MilitaryServiceDialog
   */
  public MilitaryServiceDialog(java.awt.Frame parent, boolean modal) {
    this(parent, modal, null);
  }

  /** Create when {@code existing} is null; edit when non-null. */
  public MilitaryServiceDialog(java.awt.Frame parent, boolean modal, MilitaryService existing) {
    super(parent, modal);
    initComponents();
    this.editing = existing;
    setTitle(existing == null ? "New Military Service Record" : "Edit Military Service Record");
    if (existing != null) {
      textBatchNo.setText(existing.getBatchNo() == null ? "" : existing.getBatchNo());
      textServiceNo.setText(existing.getServiceNo() == null ? "" : existing.getServiceNo());
    }
    setupActions();
    loadCombos();
  }

  /**
   * The form's GEN block wires no event handlers, so Save/Cancel are connected
   * here — same pattern as TeamMemberDialog.
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

  /** Fills the status model synchronously, then loads staff async. */
  private void loadCombos() {
    comboStatus.setModel(new javax.swing.DefaultComboBoxModel<>(statusLabels()));
    if (editing == null) {
      comboStatus.setSelectedItem(statusLabel("IN_PROGRESS"));
    } else {
      comboStatus.setSelectedItem(statusLabel(editing.getStatus()));
    }

    UiThread.executeAsync("Load military service combos",
        MilitaryServiceRepository::listStaffOptions,
        options -> {
          java.util.List<String> labels = new java.util.ArrayList<>();
          comboStaff.removeAllItems();
          staffIdByLabel.clear();
          for (MilitaryServiceRepository.StaffOption option : options) {
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

  private static String[] statusLabels() {
    String[] labels = new String[STATUS_VALUES.length];
    for (int i = 0; i < STATUS_VALUES.length; i++) {
      labels[i] = statusLabel(STATUS_VALUES[i]);
    }
    return labels;
  }

  /** User-facing label for a stored status ("IN_PROGRESS" → "In Progress"). */
  private static String statusLabel(String stored) {
    if (stored == null || stored.isBlank()) {
      return statusLabel("IN_PROGRESS");
    }
    String[] parts = stored.toLowerCase(java.util.Locale.ROOT).split("_");
    StringBuilder label = new StringBuilder();
    for (String part : parts) {
      if (!part.isEmpty()) {
        if (label.length() > 0) {
          label.append(' ');
        }
        label.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
      }
    }
    return label.toString();
  }

  /** Stored status value for the selected label ("In Progress" → "IN_PROGRESS"). */
  private String selectedStatus() {
    String label = String.valueOf(comboStatus.getSelectedItem());
    for (String value : STATUS_VALUES) {
      if (statusLabel(value).equals(label)) {
        return value;
      }
    }
    return null;
  }

  /** Staff id behind the selected/typed label; null when no known label. */
  private Long selectedStaffId() {
    Object selected = comboStaff.getSelectedItem();
    return AutoCompleteCombo.idForLabel(staffIdByLabel,
        selected == null ? null : selected.toString());
  }

  private void saveAndClose() {
    try {
      SecurityService.require(Permissions.STAFF_UPDATE);

      Long staffId = editing != null ? editing.getStaffId() : selectedStaffId();
      String batchNo = textBatchNo.getText() == null ? "" : textBatchNo.getText().trim();
      String serviceNo = textServiceNo.getText() == null ? "" : textServiceNo.getText().trim();
      String status = selectedStatus();

      List<String> errors = new ArrayList<>();
      String staffText = String.valueOf(comboStaff.getSelectedItem()).trim();
      if (staffText.isEmpty()) {
        errors.add("Staff is required.");
      } else if (staffId == null) {
        errors.add("Select staff from the list.");
      }
      if (batchNo.length() > 20) {
        errors.add("Batch No must be at most 20 characters.");
      }
      if (serviceNo.length() > 50) {
        errors.add("Service No must be at most 50 characters.");
      }
      if (status == null) {
        errors.add("Status is required.");
      }
      if (!errors.isEmpty()) {
        throw new ValidationException(errors);
      }

      Long userId = SessionContext.isAuthenticated() ? SessionContext.currentUserId() : null;
      boolean isEdit = editing != null;
      MilitaryService record;
      if (isEdit) {
        record = editing;
        record.setBatchNo(batchNo.isEmpty() ? null : batchNo);
        record.setServiceNo(serviceNo.isEmpty() ? null : serviceNo);
        record.setStatus(status);
      } else {
        record = MilitaryService.create(staffId, batchNo.isEmpty() ? null : batchNo,
            serviceNo.isEmpty() ? null : serviceNo, status,
            userId == null ? 0L : userId);
      }

      btnSave.setEnabled(false);
      UiThread.executeAsync(isEdit ? "Update military service" : "Save military service",
          () -> {
            if (isEdit) {
              MilitaryServiceRepository.update(record, userId == null ? 0L : userId);
            } else {
              record.setId(MilitaryServiceRepository.insert(record));
            }
            return record;
          },
          saved -> {
            logger.info("Military service saved with id " + saved.getId()
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
    labelBatchNo = new javax.swing.JLabel();
    textBatchNo = new javax.swing.JTextField();
    labelServiceNo = new javax.swing.JLabel();
    textServiceNo = new javax.swing.JTextField();
    labelStatus = new javax.swing.JLabel();
    comboStatus = new javax.swing.JComboBox<>();

    setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);

    footer.setPreferredSize(new java.awt.Dimension(380, 60));
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
    gridBagConstraints.insets = new java.awt.Insets(17, 170, 16, 0);
    footer.add(btnCancel, gridBagConstraints);

    getContentPane().add(footer, java.awt.BorderLayout.PAGE_END);

    body.setLayout(new java.awt.GridBagLayout());

    labelStaff.setText("Staff");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(8, 0, 7, 16);
    body.add(labelStaff, gridBagConstraints);

    comboStaff.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(8, 0, 7, 0);
    body.add(comboStaff, gridBagConstraints);

    labelBatchNo.setText("Batch No");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(8, 0, 7, 16);
    body.add(labelBatchNo, gridBagConstraints);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(8, 0, 7, 0);
    body.add(textBatchNo, gridBagConstraints);

    labelServiceNo.setText("Service No");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(8, 0, 7, 16);
    body.add(labelServiceNo, gridBagConstraints);

    textServiceNo.setPreferredSize(new java.awt.Dimension(200, 26));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(8, 0, 7, 0);
    body.add(textServiceNo, gridBagConstraints);

    labelStatus.setText("Status");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(8, 0, 7, 16);
    body.add(labelStatus, gridBagConstraints);

    comboStatus.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(8, 0, 7, 0);
    body.add(comboStatus, gridBagConstraints);

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
        MilitaryServiceDialog dialog = new MilitaryServiceDialog(new javax.swing.JFrame(), true);
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
  private javax.swing.JComboBox<String> comboStatus;
  private javax.swing.JPanel footer;
  private javax.swing.JLabel labelBatchNo;
  private javax.swing.JLabel labelServiceNo;
  private javax.swing.JLabel labelStaff;
  private javax.swing.JLabel labelStatus;
  private javax.swing.JTextField textBatchNo;
  private javax.swing.JTextField textServiceNo;
  // End of variables declaration//GEN-END:variables
}
