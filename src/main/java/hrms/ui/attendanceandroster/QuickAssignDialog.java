/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/GUIForms/JDialog.java to edit this template
 */
package hrms.ui.attendanceandroster;

import hrms.exception.AuthorizationException;
import hrms.exception.ErrorHandler;
import hrms.exception.ValidationException;
import hrms.model.DutyRoster;
import hrms.model.Shift;
import hrms.repository.DutyRosterRepository;
import hrms.repository.LeaveEntitlementRepository;
import hrms.repository.LeaveEntitlementRepository.StaffBasic;
import hrms.repository.ShiftRepository;
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
public class QuickAssignDialog extends javax.swing.JDialog {

  private static final Logger LOG = LoggerFactory.getLogger(QuickAssignDialog.class);

  /** null = create mode. */
  private final DutyRoster editing;

  /** Optional prefill when opened from a week-grid cell click. */
  private final Long presetStaffId;
  private final LocalDate presetDate;

  /**
   * Creates new form QuickAssignDialog
   */
  public QuickAssignDialog(java.awt.Frame parent, boolean modal) {
    this(parent, modal, null, null, null);
  }

  /** Create when {@code existing} is null; edit when non-null. */
  public QuickAssignDialog(java.awt.Frame parent, boolean modal, DutyRoster existing,
          Long presetStaffId, LocalDate presetDate) {
    super(parent, modal);
    initComponents();
    this.editing = existing;
    this.presetStaffId = presetStaffId;
    this.presetDate = presetDate;
    if (existing == null) {
      setTitle("Assign Shift");
    } else {
      setTitle("Edit Assignment");
      prefill(existing);
    }
    setupActions();
    loadCombos();
  }

  /** Apply stored values first; combo selections are applied after loadCombos(). */
  private void prefill(DutyRoster roster) {
    dcDate.setDate(java.util.Date.from(roster.getRosterDate()
            .atStartOfDay(ZoneId.systemDefault()).toInstant()));
    textRemark.setText(roster.getRemark() == null ? "" : roster.getRemark());
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
            ? Permissions.DUTY_ROSTER_UPDATE : Permissions.DUTY_ROSTER_CREATE);
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

  /** Snapshot so all combos load in one background call. */
  private record ComboData(List<StaffBasic> staff, List<Shift> shifts) {
  }

  /** All combos are loaded from the database — never hardcode the items. */
  private void loadCombos() {
    UiThread.executeAsync("Load assignment combos",
            () -> new ComboData(LeaveEntitlementRepository.listActiveStaff(),
                    ShiftRepository.listAll()),
            this::fillCombos);
  }

  private void fillCombos(ComboData data) {
    comboStaff.removeAllItems();
    for (StaffBasic staff : data.staff()) {
      comboStaff.addItem(new ComboItem(staff.id(),
              staff.code() + " - " + staff.name()));
    }

    comboShift.removeAllItems();
    for (Shift shift : data.shifts()) {
      comboShift.addItem(new ComboItem(shift.getId(),
              shift.getCode() + " "
                      + shift.getStartTime() + "-" + shift.getEndTime()
                      + (shift.isOvernight() ? " +1" : "")));
    }

    if (editing == null) {
      if (presetStaffId != null) {
        selectByValue(comboStaff, presetStaffId);
      }
      if (presetDate != null) {
        dcDate.setDate(java.util.Date.from(presetDate
                .atStartOfDay(ZoneId.systemDefault()).toInstant()));
      } else {
        dcDate.setDate(new java.util.Date());
      }
      return;
    }
    selectByValue(comboStaff, editing.getStaffId());
    selectByValue(comboShift, editing.getShiftId());
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
      Long staffId = selectedValue(comboStaff);
      Long shiftId = selectedValue(comboShift);
      LocalDate date = getDate(dcDate);
      String remark = textRemark.getText();

      List<String> errors = new ArrayList<>();
      if (staffId == null) {
        errors.add("Staff is required.");
      }
      if (shiftId == null) {
        errors.add("Shift is required.");
      }
      if (date == null) {
        errors.add("Date is required.");
      }
      if (remark != null && remark.trim().length() > 500) {
        errors.add("Remark must be at most 500 characters.");
      }
      if (!errors.isEmpty()) {
        throw new ValidationException(errors);
      }

      Long userId = SessionContext.isAuthenticated() ? SessionContext.currentUserId() : null;
      boolean isEdit = editing != null;
      DutyRoster roster;
      if (isEdit) {
        roster = editing;
        roster.setStaffId(staffId);
        roster.setShiftId(shiftId);
        roster.setRosterDate(date);
        roster.setRemark(remark);
        roster.setModifiedBy(userId);
      } else {
        roster = DutyRoster.create(staffId, date, shiftId, "Scheduled", remark, userId);
      }

      btnSave.setEnabled(false);
      UiThread.executeAsync(isEdit ? "Update assignment" : "Save assignment",
              () -> isEdit
                      ? DutyRosterRepository.update(roster)
                      : DutyRosterRepository.insert(roster),
              result -> {
                if (isEdit) {
                  LOG.info("Duty roster updated id={}", roster.getId());
                } else {
                  LOG.info("Duty roster saved with id {}", result);
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
    labelDate = new javax.swing.JLabel();
    dcDate = new com.toedter.calendar.JDateChooser();
    labelShift = new javax.swing.JLabel();
    comboShift = new javax.swing.JComboBox<>();
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

    labelStaff.setText("Staff");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(6, 0, 7, 14);
    body.add(labelStaff, gridBagConstraints);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(6, 0, 7, 0);
    body.add(comboStaff, gridBagConstraints);

    labelDate.setText("Date");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(6, 0, 7, 14);
    body.add(labelDate, gridBagConstraints);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(6, 0, 7, 0);
    body.add(dcDate, gridBagConstraints);

    labelShift.setText("Shift");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(6, 0, 7, 14);
    body.add(labelShift, gridBagConstraints);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(6, 0, 7, 0);
    body.add(comboShift, gridBagConstraints);

    labelRemark.setText("Remark");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(6, 0, 7, 14);
    body.add(labelRemark, gridBagConstraints);

    textRemark.setPreferredSize(new java.awt.Dimension(200, 26));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(6, 0, 7, 0);
    body.add(textRemark, gridBagConstraints);

    getContentPane().add(body, java.awt.BorderLayout.CENTER);

    pack();
  }// </editor-fold>//GEN-END:initComponents

  // Variables declaration - do not modify//GEN-BEGIN:variables
  private javax.swing.JPanel body;
  private javax.swing.JButton btnCancel;
  private javax.swing.JButton btnSave;
  private javax.swing.JComboBox<ComboItem> comboShift;
  private javax.swing.JComboBox<ComboItem> comboStaff;
  private com.toedter.calendar.JDateChooser dcDate;
  private javax.swing.JPanel footer;
  private javax.swing.JLabel labelDate;
  private javax.swing.JLabel labelRemark;
  private javax.swing.JLabel labelShift;
  private javax.swing.JLabel labelStaff;
  private javax.swing.JTextField textRemark;
  // End of variables declaration//GEN-END:variables
}
