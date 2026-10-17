/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/GUIForms/JDialog.java to edit this template
 */
package hrms.ui.attendanceandroster;

import hrms.exception.AuthorizationException;
import hrms.exception.ErrorHandler;
import hrms.exception.ValidationException;
import hrms.model.Attendance;
import hrms.model.Shift;
import hrms.model.Staff;
import hrms.repository.AttendanceRepository;
import hrms.repository.DutyRosterRepository;
import hrms.repository.ShiftRepository;
import hrms.repository.StaffRepository;
import hrms.security.Permissions;
import hrms.security.SecurityService;
import hrms.security.SessionContext;
import hrms.util.UiThread;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Card-reader popup: check in (IN punch) or check out (OUT punch) for today.
 * The staff picks who punches; date, shift and status are system-derived.
 *
 * @author aungmrat
 */
public class AttendanceDialog extends javax.swing.JDialog {

  private static final Logger LOG = LoggerFactory.getLogger(AttendanceDialog.class);

  /** Punch direction the Save button performs. */
  public enum Mode {

    IN, OUT
  }

  private final Mode mode;

  /**
   * Creates new form AttendanceDialog
   */
  public AttendanceDialog(java.awt.Frame parent, boolean modal) {
    this(parent, modal, Mode.IN);
  }

  /** Opens for today; {@code mode} decides check-in vs check-out save rules. */
  public AttendanceDialog(java.awt.Frame parent, boolean modal, Mode mode) {
    super(parent, modal);
    initComponents();
    this.mode = mode;
    setTitle(mode == Mode.IN ? "Check In" : "Check Out");
    btnSave.setText(mode == Mode.IN ? "Check In" : "Check Out");
    setupActions();
    loadStaff();
  }

  // ------------------------------------------------------------------
  // Actions & permissions
  // ------------------------------------------------------------------

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

  /**
   * Self-punch needs no permission; punching another staff is gated by
   * ATTENDANCE_CREATE (in) / ATTENDANCE_UPDATE (out).
   */
  static void requireSavePermission(Mode mode, boolean pickingOtherStaff) {
    if (!pickingOtherStaff) {
      return;
    }
    SecurityService.require(mode == Mode.IN
            ? Permissions.ATTENDANCE_CREATE : Permissions.ATTENDANCE_UPDATE);
  }

  private record ComboItem(Long value, String label) {
    @Override
    public String toString() {
      return label;
    }
  }

  private static Long selfStaffId() {
    return SessionContext.currentEmployeeId();
  }

  // ------------------------------------------------------------------
  // Staff combo
  // ------------------------------------------------------------------

  private void loadStaff() {
    UiThread.executeAsync("Load staff",
        StaffRepository::listAll,
        this::fillStaff);
  }

  private void fillStaff(List<Staff> staff) {
    Long self = selfStaffId();
    long selfId = self == null ? -1L : self;
    boolean pickingOthers = SecurityService.can(mode == Mode.IN
            ? Permissions.ATTENDANCE_CREATE : Permissions.ATTENDANCE_UPDATE);
    comboStaffId.removeAllItems();
    if (!pickingOthers) {
      comboStaffId.setEnabled(false);
    }
    for (Staff member : staff) {
      if (pickingOthers || member.getId() == selfId) {
        comboStaffId.addItem(new ComboItem(member.getId(),
            member.getCode() + " - " + member.getName()));
      }
    }
    selectSelf(selfId);
  }

  private void selectSelf(long staffId) {
    for (int i = 0; i < comboStaffId.getItemCount(); i++) {
      ComboItem item = comboStaffId.getItemAt(i);
      if (item != null && item.value() != null && item.value() == staffId) {
        comboStaffId.setSelectedIndex(i);
        return;
      }
    }
  }

  // ------------------------------------------------------------------
  // Save
  // ------------------------------------------------------------------

  private void saveAndClose() {
    try {
      ComboItem item = (ComboItem) comboStaffId.getSelectedItem();
      if (item == null || item.value() == null) {
        throw new ValidationException("Staff is required.");
      }
      long staffId = item.value();
      Long self = selfStaffId();
      requireSavePermission(mode, self == null || staffId != self);

      LocalDate date = LocalDate.now();
      LocalTime now = LocalTime.now().withNano(0);
      Long userId = SessionContext.isAuthenticated() ? SessionContext.currentUserId() : null;

      btnSave.setEnabled(false);
      UiThread.executeAsync(mode == Mode.IN ? "Check in" : "Check out",
          () -> mode == Mode.IN
              ? checkIn(staffId, date, now, userId)
              : checkOut(staffId, date, now, userId),
          id -> {
            LOG.info("Attendance check-{} saved for staff {} (attendance id={})",
                mode, staffId, id);
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
   * Worker: IN punch. Creates the day's row from the duty roster, or appends
   * to an existing row; an existing punch list always wins over LEAVE/ABSENT.
   */
  private long checkIn(long staffId, LocalDate date, LocalTime now, Long userId) {
    Attendance existing = AttendanceRepository.findByStaffDate(staffId, date).orElse(null);
    if (existing != null) {
      if (existing.getPunches().size() % 2 == 1) {
        throw new ValidationException("Already checked in.");
      }
      List<LocalTime> punches = new ArrayList<>(existing.getPunches());
      punches.add(now);
      existing.setPunches(punches);
      existing.setStatus("PRESENT");
      existing.setModifiedBy(userId);
      AttendanceRepository.update(existing);
      return existing.getId();
    }
    DutyRosterRepository.RosterRef roster = DutyRosterRepository
            .findRosterFor(staffId, date)
            .orElseThrow(() -> new ValidationException("No duty roster today."));
    Attendance created = Attendance.create(staffId, date, roster.shiftId(),
            now, now, BigDecimal.ZERO, "PRESENT", null, userId);
    created.setRosterId(roster.rosterId());
    created.setPunches(new ArrayList<>(List.of(now)));
    return AttendanceRepository.insert(created);
  }

  /**
   * Worker: OUT punch. Appends to an open (odd) punch list, then derives
   * total hours, OT = floor((worked - scheduled) / 60) and status.
   */
  private long checkOut(long staffId, LocalDate date, LocalTime now, Long userId) {
    Attendance existing = AttendanceRepository.findByStaffDate(staffId, date)
            .orElseThrow(() -> new ValidationException("Check in first."));
    if (existing.getPunches().isEmpty()
            || existing.getPunches().size() % 2 == 0) {
      throw new ValidationException("Check in first.");
    }
    List<LocalTime> punches = new ArrayList<>(existing.getPunches());
    punches.add(now);
    existing.setPunches(punches);
    existing.recomputeFromPunches();
    applyOvertime(existing, punches);
    existing.setModifiedBy(userId);
    AttendanceRepository.update(existing);
    return existing.getId();
  }

  /** OT hours = floor(excess minutes / 60); no shift means no OT. */
  private static void applyOvertime(Attendance attendance, List<LocalTime> punches) {
    long overtimeHours = 0;
    Shift shift = attendance.getShiftId() > 0
            ? ShiftRepository.findById(attendance.getShiftId()).orElse(null) : null;
    if (shift != null) {
      long workedMin = 0;
      for (int i = 0; i + 1 < punches.size(); i += 2) {
        long minutes = Duration.between(punches.get(i), punches.get(i + 1)).toMinutes();
        if (minutes <= 0) {
          minutes += 24 * 60; // overnight pair
        }
        workedMin += minutes;
      }
      long scheduledMin = Duration.between(shift.getStartTime(), shift.getEndTime())
              .toMinutes();
      if (scheduledMin <= 0 && shift.isOvernight()) {
        scheduledMin += 24 * 60;
      }
      overtimeHours = Math.max(0, workedMin - scheduledMin) / 60;
    }
    attendance.setOvertimeHours(BigDecimal.valueOf(overtimeHours));
    attendance.setStatus(overtimeHours > 0 ? "OVERTIME" : "PRESENT");
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
    labelStaffId = new javax.swing.JLabel();
    comboStaffId = new javax.swing.JComboBox<>();

    setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);

    footer.setPreferredSize(new java.awt.Dimension(400, 60));
    footer.setLayout(new java.awt.GridBagLayout());

    btnSave.setText("Save");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
    gridBagConstraints.insets = new java.awt.Insets(18, 12, 15, 40);
    footer.add(btnSave, gridBagConstraints);

    btnCancel.setText("Cancel");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
    gridBagConstraints.insets = new java.awt.Insets(18, 196, 15, 0);
    footer.add(btnCancel, gridBagConstraints);

    getContentPane().add(footer, java.awt.BorderLayout.PAGE_END);

    body.setLayout(new java.awt.GridBagLayout());

    labelStaffId.setText("Staff");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(7, 0, 7, 18);
    body.add(labelStaffId, gridBagConstraints);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(4, 0, 4, 0);
    body.add(comboStaffId, gridBagConstraints);

    getContentPane().add(body, java.awt.BorderLayout.CENTER);

    pack();
  }// </editor-fold>//GEN-END:initComponents

  // Variables declaration - do not modify//GEN-BEGIN:variables
  private javax.swing.JPanel body;
  private javax.swing.JButton btnCancel;
  private javax.swing.JButton btnSave;
  private javax.swing.JComboBox<ComboItem> comboStaffId;
  private javax.swing.JPanel footer;
  private javax.swing.JLabel labelStaffId;
  // End of variables declaration//GEN-END:variables
}
