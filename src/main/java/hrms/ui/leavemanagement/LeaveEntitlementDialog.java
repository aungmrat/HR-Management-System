/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/GUIForms/JDialog.java to edit this template
 */
package hrms.ui.leavemanagement;

import hrms.exception.AuthorizationException;
import hrms.exception.ErrorHandler;
import hrms.exception.ValidationException;
import hrms.model.LeaveEntitlement;
import hrms.model.LeaveType;
import hrms.repository.LeaveEntitlementRepository;
import hrms.repository.LeaveTypeRepository;
import hrms.security.Permissions;
import hrms.security.SecurityService;
import hrms.security.SessionContext;
import hrms.util.UiThread;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author aungmrat
 */
public class LeaveEntitlementDialog extends javax.swing.JDialog {

  private static final Logger LOG = LoggerFactory.getLogger(LeaveEntitlementDialog.class);

  /** null = create mode. */
  private final LeaveEntitlement editing;

  /** Leave types by combo index, filled together with the combos. */
  private final List<LeaveType> leaveTypes = new ArrayList<>();

  public LeaveEntitlementDialog(java.awt.Frame parent, boolean modal) {
    this(parent, modal, null);
  }

  /** Create when {@code existing} is null; edit when non-null. */
  public LeaveEntitlementDialog(java.awt.Frame parent, boolean modal, LeaveEntitlement existing) {
    super(parent, modal);
    initComponents();
    this.editing = existing;
    setTitle(existing == null ? "New Leave Entitlement" : "Edit Leave Entitlement");
    if (existing == null) {
      applyCreateDefaults();
    }
    setupActions();
    loadCombos();
  }

  /** A whole entitlement year, starting 1 January. */
  private void applyCreateDefaults() {
    LocalDate now = LocalDate.now();
    dcEffectiveDate.setDate(java.util.Date.from(now.withDayOfYear(1)
            .atStartOfDay(ZoneId.systemDefault()).toInstant()));
    dcExpiryDate.setDate(java.util.Date.from(now.withDayOfYear(now.lengthOfYear())
            .atStartOfDay(ZoneId.systemDefault()).toInstant()));
    textCarriedDays.setText("0");
    textUsedDays.setText("0");
  }

  private record ComboItem(Long value, String label) {

    @Override
    public String toString() {
      return label;
    }
  }

  /** A staff list and a leave-type list, loaded together off the EDT. */
  private record Combos(List<ComboItem> staff, List<LeaveType> types,
          java.util.Map<Long, String> staffSex) {
  }

  private final java.util.Map<Long, String> staffSexById = new java.util.HashMap<>();

  private void loadCombos() {
    UiThread.executeAsync("Load entitlement combos",
        () -> {
          var basics = LeaveEntitlementRepository.listActiveStaff();
          var sexById = new java.util.HashMap<Long, String>();
          for (var s : basics) {
            sexById.put(s.id(), s.sex());
          }
          return new Combos(
              basics.stream()
                  .map(s -> new ComboItem(s.id(), s.code() + " - " + s.name()))
                  .toList(),
              LeaveTypeRepository.listAll().stream()
                  .filter(LeaveType::isActive)
                  .toList(),
              sexById);
        },
        combos -> {
          leaveTypes.clear();
          leaveTypes.addAll(combos.types());
          staffSexById.clear();
          staffSexById.putAll(combos.staffSex());
          comboStaff.removeAllItems();
          for (ComboItem item : combos.staff()) {
            comboStaff.addItem(item);
          }
          comboLeaveType.removeAllItems();
          for (LeaveType type : combos.types()) {
            comboLeaveType.addItem(new ComboItem(type.getId(),
                    type.getCode() + " - " + type.getName()));
          }
          if (editing != null) {
            prefillSelections();
          }
        },
        error -> ErrorHandler.handle(this, error));
  }

  private void prefillSelections() {
    textEntitledDays.setText(String.valueOf(editing.getEntitledDays()));
    textCarriedDays.setText(editing.getCarriedDays() == null
            ? "0" : String.valueOf(editing.getCarriedDays()));
    textUsedDays.setText(editing.getUsedDays() == null
            ? "0" : String.valueOf(editing.getUsedDays()));
    dcEffectiveDate.setDate(java.util.Date.from(editing.getEffectiveDate()
            .atStartOfDay(ZoneId.systemDefault()).toInstant()));
    dcExpiryDate.setDate(java.util.Date.from(editing.getExpiryDate()
            .atStartOfDay(ZoneId.systemDefault()).toInstant()));
    textRemark.setText(editing.getRemark() == null ? "" : editing.getRemark());
    selectByValue(comboStaff, editing.getStaffId());
    selectByValue(comboLeaveType, editing.getLeaveTypeId());
  }

  private static void selectByValue(javax.swing.JComboBox<ComboItem> combo, long value) {
    for (int i = 0; i < combo.getItemCount(); i++) {
      ComboItem item = combo.getItemAt(i);
      if (item.value() != null && item.value() == value) {
        combo.setSelectedIndex(i);
        return;
      }
    }
  }

  private static ComboItem selectedValue(javax.swing.JComboBox<ComboItem> combo) {
    return (ComboItem) combo.getSelectedItem();
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
            ? Permissions.LEAVE_ENTITLEMENT_UPDATE : Permissions.LEAVE_ENTITLEMENT_CREATE);
  }

  private void saveAndClose() {
    try {
      boolean isEdit = editing != null;
      requireSavePermission(isEdit);

      List<String> errors = new ArrayList<>();
      ComboItem staff = selectedValue(comboStaff);
      if (staff == null || staff.value() == null) {
        errors.add("Staff is required.");
      }
      ComboItem type = selectedValue(comboLeaveType);
      if (type == null || type.value() == null) {
        errors.add("Leave Type is required.");
      }
      int typeIndex = comboLeaveType.getSelectedIndex();
      LeaveType leaveType = typeIndex >= 0 && typeIndex < leaveTypes.size()
              ? leaveTypes.get(typeIndex) : null;
      if (staff != null && staff.value() != null && leaveType != null
              && !leaveType.allowsSex(staffSexById.get(staff.value()))) {
        errors.add(leaveType.getName() + " is only available to "
                + leaveType.getGenderRestriction() + " staff.");
      }

      Integer entitled = parseInteger(textEntitledDays.getText(), "Entitled Days", true, errors);
      if (entitled != null && entitled < 0) {
        errors.add("Entitled Days cannot be negative.");
      }
      Integer carried = parseInteger(textCarriedDays.getText(), "Carried Days", false, errors);
      if (carried != null && carried < 0) {
        errors.add("Carried Days cannot be negative.");
      }
      Integer used = parseInteger(textUsedDays.getText(), "Used Days", false, errors);
      if (used != null && used < 0) {
        errors.add("Used Days cannot be negative.");
      }

      LocalDate effective = getDate(dcEffectiveDate);
      if (effective == null) {
        errors.add("Effective Date is required.");
      }
      LocalDate expiry = getDate(dcExpiryDate);
      if (expiry == null) {
        errors.add("Expiry Date is required.");
      }
      if (effective != null && expiry != null && expiry.isBefore(effective)) {
        errors.add("Expiry Date cannot be before Effective Date.");
      }

      String remark = textRemark.getText() == null ? "" : textRemark.getText().trim();
      if (remark.length() > 500) {
        errors.add("Remark must be at most 500 characters.");
      }

      // Hard cap: a mid-year joiner may not receive the full annual quota.
      int cap = Integer.MAX_VALUE;
      if (staff != null && leaveType != null && effective != null && expiry != null) {
        LeaveEntitlementRepository.StaffBasic staffBasic =
                LeaveEntitlementRepository.listActiveStaff().stream()
                    .filter(s -> s.id() == staff.value())
                    .findFirst().orElse(null);
        if (staffBasic != null) {
          cap = LeaveEntitlementRepository.maxEntitledDays(
                  staffBasic.commencementDate(), effective, expiry,
                  leaveType.getStandardDaysPerYear());
          if (entitled != null && entitled > cap) {
            errors.add("Entitled Days cannot exceed " + cap + " for this staff and period"
                    + (staffBasic.commencementDate() == null ? "."
                        : " (joined " + staffBasic.commencementDate() + ")."));
          }
        }
      }

      if (!errors.isEmpty()) {
        throw new ValidationException(errors);
      }

      Long userId = SessionContext.isAuthenticated() ? SessionContext.currentUserId() : null;
      LeaveEntitlement entitlement;
      if (isEdit) {
        entitlement = editing;
        entitlement.setStaffId(staff.value());
        entitlement.setLeaveTypeId(type.value());
        entitlement.setEntitledDays(entitled);
        entitlement.setCarriedDays(carried == null ? 0 : carried);
        entitlement.setUsedDays(used == null ? 0 : used);
        entitlement.setEffectiveDate(effective);
        entitlement.setExpiryDate(expiry);
        entitlement.setRemark(remark.isEmpty() ? null : remark);
        entitlement.setModifiedBy(userId);
      } else {
        entitlement = LeaveEntitlement.create(staff.value(), type.value(), entitled,
                carried == null ? 0 : carried, used == null ? 0 : used,
                effective, expiry, remark.isEmpty() ? null : remark, userId);
      }

      btnSave.setEnabled(false);
      UiThread.executeAsync(isEdit ? "Update leave entitlement" : "Save leave entitlement",
          () -> {
            if (isEdit) {
              LeaveEntitlementRepository.update(entitlement);
              return true;
            }
            LeaveEntitlementRepository.insert(entitlement);
            return true;
          },
          result -> {
            if (isEdit) {
              LOG.info("Leave entitlement updated id={}", entitlement.getId());
            } else {
              LOG.info("Leave entitlement saved with id {}", entitlement.getId());
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

  private static java.time.LocalDate getDate(com.toedter.calendar.JDateChooser chooser) {
    java.util.Date date = chooser.getDate();
    return date == null ? null
            : date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
  }

  /**
   * Parses a whole-day field. Required when {@code required}; returns null for
   * a blank optional field.
   */
  private static Integer parseInteger(String raw, String label,
          boolean required, List<String> errors) {
    String text = raw == null ? "" : raw.trim();
    if (text.isEmpty()) {
      if (required) {
        errors.add(label + " is required.");
      }
      return null;
    }
    try {
      return Integer.parseInt(text);
    } catch (NumberFormatException e) {
      errors.add(label + " must be a whole number.");
      return null;
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
    labelLeaveType = new javax.swing.JLabel();
    comboLeaveType = new javax.swing.JComboBox<>();
    labelEntitledDays = new javax.swing.JLabel();
    textEntitledDays = new javax.swing.JTextField();
    labelCarriedDays = new javax.swing.JLabel();
    textCarriedDays = new javax.swing.JTextField();
    labelUsedDays = new javax.swing.JLabel();
    textUsedDays = new javax.swing.JTextField();
    labelEffectiveDate = new javax.swing.JLabel();
    dcEffectiveDate = new com.toedter.calendar.JDateChooser();
    labelExpiryDate = new javax.swing.JLabel();
    dcExpiryDate = new com.toedter.calendar.JDateChooser();
    labelRemark = new javax.swing.JLabel();
    textRemark = new javax.swing.JTextField();

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

    labelStaff.setText("Staff");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(7, 0, 7, 15);
    body.add(labelStaff, gridBagConstraints);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(7, 0, 7, 0);
    body.add(comboStaff, gridBagConstraints);

    labelLeaveType.setText("Leave Type");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(7, 0, 7, 15);
    body.add(labelLeaveType, gridBagConstraints);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(7, 0, 7, 0);
    body.add(comboLeaveType, gridBagConstraints);

    labelEntitledDays.setText("Entitled Days");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(7, 0, 7, 15);
    body.add(labelEntitledDays, gridBagConstraints);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(7, 0, 7, 0);
    body.add(textEntitledDays, gridBagConstraints);

    labelCarriedDays.setText("Carried Days");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(7, 0, 7, 15);
    body.add(labelCarriedDays, gridBagConstraints);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(7, 0, 7, 0);
    body.add(textCarriedDays, gridBagConstraints);

    labelUsedDays.setText("Used Days");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(7, 0, 7, 15);
    body.add(labelUsedDays, gridBagConstraints);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(7, 0, 7, 0);
    body.add(textUsedDays, gridBagConstraints);

    labelEffectiveDate.setText("Effective Date");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 5;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(7, 0, 7, 15);
    body.add(labelEffectiveDate, gridBagConstraints);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 5;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(7, 0, 7, 0);
    body.add(dcEffectiveDate, gridBagConstraints);

    labelExpiryDate.setText("Expiry Date");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 6;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(7, 0, 7, 15);
    body.add(labelExpiryDate, gridBagConstraints);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 6;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(7, 0, 7, 0);
    body.add(dcExpiryDate, gridBagConstraints);

    labelRemark.setText("Remark");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 7;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(7, 0, 7, 15);
    body.add(labelRemark, gridBagConstraints);

    textRemark.setPreferredSize(new java.awt.Dimension(200, 26));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 7;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(7, 0, 7, 0);
    body.add(textRemark, gridBagConstraints);

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
        LeaveEntitlementDialog dialog = new LeaveEntitlementDialog(new javax.swing.JFrame(), true);
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
  private javax.swing.JComboBox<ComboItem> comboLeaveType;
  private javax.swing.JComboBox<ComboItem> comboStaff;
  private com.toedter.calendar.JDateChooser dcEffectiveDate;
  private com.toedter.calendar.JDateChooser dcExpiryDate;
  private javax.swing.JPanel footer;
  private javax.swing.JLabel labelCarriedDays;
  private javax.swing.JLabel labelEffectiveDate;
  private javax.swing.JLabel labelEntitledDays;
  private javax.swing.JLabel labelExpiryDate;
  private javax.swing.JLabel labelLeaveType;
  private javax.swing.JLabel labelRemark;
  private javax.swing.JLabel labelStaff;
  private javax.swing.JLabel labelUsedDays;
  private javax.swing.JTextField textCarriedDays;
  private javax.swing.JTextField textEntitledDays;
  private javax.swing.JTextField textRemark;
  private javax.swing.JTextField textUsedDays;
  // End of variables declaration//GEN-END:variables
}
