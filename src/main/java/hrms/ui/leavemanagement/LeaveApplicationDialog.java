/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/GUIForms/JDialog.java to edit this template
 */
package hrms.ui.leavemanagement;

import hrms.exception.AuthorizationException;
import hrms.exception.ErrorHandler;
import hrms.exception.ValidationException;
import hrms.model.LeaveApplication;
import hrms.model.LeaveType;
import hrms.repository.LeaveApplicationRepository;
import hrms.repository.LeaveEntitlementRepository;
import hrms.repository.LeaveTypeRepository;
import hrms.security.Permissions;
import hrms.security.SecurityService;
import hrms.security.SessionContext;
import hrms.util.UiThread;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author aungmrat
 */
public class LeaveApplicationDialog extends javax.swing.JDialog {

  private static final Logger LOG = LoggerFactory.getLogger(LeaveApplicationDialog.class);

  private LeaveApplication editing;
  private final List<LeaveType> leaveTypes = new ArrayList<>();
  private final Map<Long, String> staffSexById = new HashMap<>();
  private BigDecimal availableDays;

  /**
   * Creates new form LeaveApplicationDialog
   */
  public LeaveApplicationDialog(java.awt.Frame parent, boolean modal) {
    this(parent, modal, null);
  }

  /** Create when {@code existing} is null; edit a PENDING row when non-null. */
  public LeaveApplicationDialog(java.awt.Frame parent, boolean modal,
          LeaveApplication existing) {
    super(parent, modal);
    initComponents();
    this.editing = existing;
    setTitle(existing == null ? "New Leave Application" : "Edit Leave Application");
    if (existing == null) {
      applyCreateDefaults();
    } else {
      prefill(existing);
    }
    loadCombos();
    setupActions();
  }

  private record ComboItem(Long value, String label) {

    @Override
    public String toString() {
      return label;
    }
  }

  private void applyCreateDefaults() {
    LocalDate today = LocalDate.now();
    dcFromDate.setDate(utilDate(today));
    dcToDate.setDate(utilDate(today));
    comboHalfDay.setSelectedItem("NO");
    textTotalDays.setText("1");
    textBusinessDays.setText(String.valueOf(businessDays(today, today)));
  }

  private void prefill(LeaveApplication application) {
    selectStaff(application.getStaffId());
    selectType(application.getLeaveTypeId());
    dcFromDate.setDate(utilDate(application.getFromDate()));
    dcToDate.setDate(utilDate(application.getToDate()));
    comboHalfDay.setSelectedItem(application.isHalfDay()
            ? (application.getHalfDayPeriod() == null ? "AM" : application.getHalfDayPeriod())
            : "NO");
    textRemark.setText(application.getRemark() == null ? "" : application.getRemark());
    if (!application.isPending()) {
      setFieldsEditable(false);
      btnSave.setEnabled(false);
    }
  }

  private void setFieldsEditable(boolean editable) {
    comboStaff.setEnabled(editable);
    comboLeaveType.setEnabled(editable);
    dcFromDate.setEnabled(editable);
    dcToDate.setEnabled(editable);
    comboHalfDay.setEnabled(editable);
    textRemark.setEnabled(editable);
  }

  private static java.util.Date utilDate(LocalDate date) {
    return java.util.Date.from(date.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant());
  }

  private static LocalDate localDate(java.util.Date date) {
    if (date == null) {
      return null;
    }
    return date.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
  }

  private void loadCombos() {
    boolean canPickAnyStaff = SecurityService.can(Permissions.STAFF_VIEW);
    Long selfId = SessionContext.currentEmployeeId();
    UiThread.executeAsync("Load leave application combos",
        () -> {
          List<LeaveEntitlementRepository.StaffBasic> basics =
                  LeaveEntitlementRepository.listActiveStaff();
          if (!canPickAnyStaff && selfId != null) {
            basics = basics.stream().filter(s -> s.id() == selfId).toList();
          }
          Map<Long, String> sexById = new HashMap<>();
          for (LeaveEntitlementRepository.StaffBasic s : basics) {
            sexById.put(s.id(), s.sex());
          }
          List<LeaveType> types = LeaveTypeRepository.listAll().stream()
                  .filter(LeaveType::isActive)
                  .toList();
          return new Object[]{basics, types, sexById};
        },
        result -> {
          @SuppressWarnings("unchecked")
          List<LeaveEntitlementRepository.StaffBasic> basics =
                  (List<LeaveEntitlementRepository.StaffBasic>) result[0];
          @SuppressWarnings("unchecked")
          List<LeaveType> types = (List<LeaveType>) result[1];
          @SuppressWarnings("unchecked")
          Map<Long, String> sexById = (Map<Long, String>) result[2];
          leaveTypes.clear();
          leaveTypes.addAll(types);
          staffSexById.clear();
          staffSexById.putAll(sexById);
          comboStaff.removeAllItems();
          for (LeaveEntitlementRepository.StaffBasic s : basics) {
            comboStaff.addItem(new ComboItem(s.id(), s.code() + " - " + s.name()));
          }
          refreshTypeCombo();
          if (editing != null) {
            selectStaff(editing.getStaffId());
            selectType(editing.getLeaveTypeId());
          }
          recomputeDays();
          refreshAvailable();
        },
        error -> ErrorHandler.handle(this, error));
  }

  /** Rebuilds the leave-type combo for the selected staff's gender. */
  private void refreshTypeCombo() {
    ComboItem staff = selectedValue(comboStaff);
    String sex = staff == null ? null : staffSexById.get(staff.value());
    Long previous = selectedTypeValue();
    comboLeaveType.removeAllItems();
    for (LeaveType type : leaveTypes) {
      if (type.allowsSex(sex)) {
        comboLeaveType.addItem(new ComboItem(type.getId(),
                type.getCode() + " - " + type.getName()));
      }
    }
    if (previous != null) {
      selectType(previous);
    }
  }

  private Long selectedTypeValue() {
    ComboItem item = selectedValue(comboLeaveType);
    return item == null ? null : item.value();
  }

  private void selectStaff(long staffId) {
    selectByValue(comboStaff, staffId);
    refreshTypeCombo();
  }

  private void selectType(long typeId) {
    selectByValue(comboLeaveType, typeId);
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
    java.beans.PropertyChangeListener dateChanged = evt -> {
      recomputeDays();
      refreshAvailable();
    };
    dcFromDate.addPropertyChangeListener("date", dateChanged);
    dcToDate.addPropertyChangeListener("date", dateChanged);
    comboHalfDay.addActionListener(e -> recomputeDays());
    comboStaff.addActionListener(e -> {
      refreshTypeCombo();
      refreshAvailable();
    });
    comboLeaveType.addActionListener(e -> refreshAvailable());
  }

  /** Auto-computes Total Days (inclusive, half-day -0.5) and Business Days (Mon-Fri). */
  private void recomputeDays() {
    LocalDate from = localDate(dcFromDate.getDate());
    LocalDate to = localDate(dcToDate.getDate());
    if (from == null || to == null || to.isBefore(from)) {
      textTotalDays.setText("");
      textBusinessDays.setText("");
      return;
    }
    BigDecimal total = BigDecimal.valueOf(ChronoUnit.DAYS.between(from, to) + 1);
    BigDecimal business = BigDecimal.valueOf(businessDays(from, to));
    if (!"NO".equals(comboHalfDay.getSelectedItem())) {
      total = total.subtract(BigDecimal.valueOf(0.5)).max(BigDecimal.valueOf(0.5));
      business = business.subtract(BigDecimal.valueOf(0.5)).max(BigDecimal.valueOf(0.5));
    }
    textTotalDays.setText(total.stripTrailingZeros().toPlainString());
    textBusinessDays.setText(business.stripTrailingZeros().toPlainString());
  }

  private static long businessDays(LocalDate from, LocalDate to) {
    long count = 0;
    for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
      DayOfWeek dow = day.getDayOfWeek();
      if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY) {
        count++;
      }
    }
    return count;
  }

  private void refreshAvailable() {
    ComboItem staff = selectedValue(comboStaff);
    ComboItem type = selectedValue(comboLeaveType);
    LocalDate from = localDate(dcFromDate.getDate());
    if (staff == null || staff.value() == null || type == null || type.value() == null) {
      availableDays = null;
      textAvailable.setText("");
      return;
    }
    int year = from == null ? LocalDate.now().getYear() : from.getYear();
    UiThread.executeAsync("Check available days",
        () -> LeaveApplicationRepository.remainingDays(staff.value(), type.value(), year),
        result -> {
          availableDays = result.orElse(null);
          textAvailable.setText(result.map(r -> r.stripTrailingZeros().toPlainString())
                  .orElse("— no entitlement —"));
        },
        error -> ErrorHandler.handle(this, error));
  }

  /** Permission gate for Submit; static so headless tests can verify it. */
  static void requireSavePermission(boolean isEdit) {
    SecurityService.require(isEdit
            ? Permissions.LEAVE_APPLICATION_CREATE : Permissions.LEAVE_APPLICATION_CREATE);
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
      LocalDate from = localDate(dcFromDate.getDate());
      if (from == null) {
        errors.add("From Date is required.");
      }
      LocalDate to = localDate(dcToDate.getDate());
      if (to == null) {
        errors.add("To Date is required.");
      }
      if (from != null && to != null && to.isBefore(from)) {
        errors.add("To Date cannot be before From Date.");
      }

      String halfSelection = comboHalfDay.getSelectedItem() == null
              ? "NO" : comboHalfDay.getSelectedItem().toString();
      boolean halfDay = !"NO".equals(halfSelection);

      BigDecimal total = null;
      BigDecimal business = null;
      if (from != null && to != null && !to.isBefore(from)) {
        total = BigDecimal.valueOf(ChronoUnit.DAYS.between(from, to) + 1);
        business = BigDecimal.valueOf(businessDays(from, to));
        if (halfDay) {
          total = total.subtract(BigDecimal.valueOf(0.5)).max(BigDecimal.valueOf(0.5));
          business = business.subtract(BigDecimal.valueOf(0.5)).max(BigDecimal.valueOf(0.5));
        }
        if (total.compareTo(BigDecimal.valueOf(366)) > 0) {
          errors.add("Leave cannot exceed 366 days.");
        }
      }

      if (staff != null && staff.value() != null && type != null && total != null) {
        if (LeaveApplicationRepository.overlaps(staff.value(), from, to,
                isEdit ? editing.getId() : null)) {
          errors.add("These dates overlap an existing pending or approved leave.");
        }
        if (availableDays == null) {
          errors.add("No leave entitlement exists for the selected staff, type and year.");
        } else if (total.compareTo(availableDays) > 0) {
          errors.add("Insufficient balance: " + availableDays.stripTrailingZeros().toPlainString()
                  + " day(s) available, but " + total.stripTrailingZeros().toPlainString()
                  + " requested.");
        }
      }

      String remark = textRemark.getText() == null ? "" : textRemark.getText().trim();
      if (remark.length() > 500) {
        errors.add("Remark must be at most 500 characters.");
      }

      if (!errors.isEmpty()) {
        throw new ValidationException(errors);
      }

      Long userId = SessionContext.isAuthenticated() ? SessionContext.currentUserId() : null;
      Long creatorStaffId = SessionContext.currentEmployeeId();
      LeaveApplication application;
      if (isEdit) {
        application = editing;
        application.setFromDate(from);
        application.setToDate(to);
        application.setHalfDay(halfDay);
        application.setHalfDayPeriod(halfDay ? halfSelection : null);
        application.setTotalDays(total);
        application.setBusinessDays(business);
        application.setRemark(remark.isEmpty() ? null : remark);
        application.setModifiedBy(userId);
      } else {
        application = LeaveApplication.create(staff.value(), type.value(), from, to,
                halfDay, halfDay ? halfSelection : null, total, business,
                LocalDate.now(), remark.isEmpty() ? null : remark, userId);
      }

      btnSave.setEnabled(false);
      UiThread.executeAsync(isEdit ? "Update leave application" : "Submit leave application",
          () -> {
            if (isEdit) {
              LeaveApplicationRepository.update(application);
            } else {
              LeaveApplicationRepository.insert(application);
            }
            LeaveApplicationRepository.insertHistory(application.getId(),
                    isEdit ? "UPDATE" : "SUBMIT",
                    isEdit ? "PENDING" : null,
                    "PENDING",
                    creatorStaffId != null ? creatorStaffId : application.getStaffId(),
                    remark.isEmpty() ? null : remark);
            return null;
          },
          result -> {
            LOG.info("Leave application {} id={}",
                    isEdit ? "updated" : "submitted", application.getId());
            dispose();
          },
          error -> {
            btnSave.setEnabled(true);
            ErrorHandler.handle(this, error);
          });
    } catch (ValidationException ex) {
      ErrorHandler.handle(this, ex);
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
    labelFromDate = new javax.swing.JLabel();
    dcFromDate = new com.toedter.calendar.JDateChooser();
    labelToDate = new javax.swing.JLabel();
    dcToDate = new com.toedter.calendar.JDateChooser();
    labelTotalDays = new javax.swing.JLabel();
    textTotalDays = new javax.swing.JTextField();
    labelBusinessDays = new javax.swing.JLabel();
    textBusinessDays = new javax.swing.JTextField();
    labelHalfDay = new javax.swing.JLabel();
    comboHalfDay = new javax.swing.JComboBox<>();
    labelAvailable = new javax.swing.JLabel();
    textAvailable = new javax.swing.JTextField();
    labelRemark = new javax.swing.JLabel();
    textRemark = new javax.swing.JTextField();

    setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);

    footer.setPreferredSize(new java.awt.Dimension(400, 60));
    footer.setLayout(new java.awt.GridBagLayout());

    btnSave.setText("Submit");
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
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(6, 0, 5, 13);
    body.add(labelStaff, gridBagConstraints);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(6, 0, 5, 0);
    body.add(comboStaff, gridBagConstraints);

    labelLeaveType.setText("Leave Type");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(6, 0, 5, 13);
    body.add(labelLeaveType, gridBagConstraints);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(6, 0, 5, 0);
    body.add(comboLeaveType, gridBagConstraints);

    labelFromDate.setText("From Date");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(6, 0, 5, 13);
    body.add(labelFromDate, gridBagConstraints);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(6, 0, 5, 0);
    body.add(dcFromDate, gridBagConstraints);

    labelToDate.setText("To Date");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(6, 0, 5, 13);
    body.add(labelToDate, gridBagConstraints);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(6, 0, 5, 0);
    body.add(dcToDate, gridBagConstraints);

    labelTotalDays.setText("Total Days");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(6, 0, 5, 13);
    body.add(labelTotalDays, gridBagConstraints);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(6, 0, 5, 0);
    body.add(textTotalDays, gridBagConstraints);

    labelBusinessDays.setText("Business Days");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 5;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(6, 0, 5, 13);
    body.add(labelBusinessDays, gridBagConstraints);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 5;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(6, 0, 5, 0);
    body.add(textBusinessDays, gridBagConstraints);

    labelHalfDay.setText("Half Day");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 6;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(6, 0, 5, 13);
    body.add(labelHalfDay, gridBagConstraints);

    comboHalfDay.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "NO", "AM", "PM" }));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 6;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(6, 0, 5, 0);
    body.add(comboHalfDay, gridBagConstraints);

    labelAvailable.setText("Available");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 7;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(6, 0, 5, 13);
    body.add(labelAvailable, gridBagConstraints);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 7;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(6, 0, 5, 0);
    body.add(textAvailable, gridBagConstraints);

    labelRemark.setText("Remark");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 8;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(6, 0, 5, 13);
    body.add(labelRemark, gridBagConstraints);

    textRemark.setPreferredSize(new java.awt.Dimension(200, 26));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 8;
    gridBagConstraints.insets = new java.awt.Insets(6, 0, 5, 0);
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
        LeaveApplicationDialog dialog = new LeaveApplicationDialog(new javax.swing.JFrame(), true);
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
  private javax.swing.JComboBox<String> comboHalfDay;
  private javax.swing.JComboBox<ComboItem> comboLeaveType;
  private javax.swing.JComboBox<ComboItem> comboStaff;
  private com.toedter.calendar.JDateChooser dcFromDate;
  private com.toedter.calendar.JDateChooser dcToDate;
  private javax.swing.JPanel footer;
  private javax.swing.JLabel labelAvailable;
  private javax.swing.JLabel labelBusinessDays;
  private javax.swing.JLabel labelFromDate;
  private javax.swing.JLabel labelHalfDay;
  private javax.swing.JLabel labelLeaveType;
  private javax.swing.JLabel labelRemark;
  private javax.swing.JLabel labelStaff;
  private javax.swing.JLabel labelToDate;
  private javax.swing.JLabel labelTotalDays;
  private javax.swing.JTextField textAvailable;
  private javax.swing.JTextField textBusinessDays;
  private javax.swing.JTextField textRemark;
  private javax.swing.JTextField textTotalDays;
  // End of variables declaration//GEN-END:variables
}
