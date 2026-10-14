package hrms.model;

import java.time.LocalDate;

/**
 * DutyRoster entity mapped from the {@code duty_roster} table (one staff
 * assigned to one shift on one date).
 */
public class DutyRoster {

  private long id;
  private long shiftId;
  private long staffId;
  private LocalDate rosterDate;
  private String status;
  private String remark;
  private Long createdBy;
  private Long modifiedBy;

  // Display-only fields filled by the repository JOIN (not persisted columns).
  private String staffName;
  private String shiftLabel;
  private boolean shiftOvernight;
  private java.time.LocalTime shiftStartTime;
  /** Attendance status for the same staff+date, when a record exists. */
  private String attendanceStatus;

  public DutyRoster() {
  }

  /** New unsaved duty roster for insert (id = 0 until persisted). */
  public static DutyRoster create(long staffId, LocalDate rosterDate, long shiftId,
          String status, String remark, Long createdBy) {
    DutyRoster roster = new DutyRoster();
    roster.staffId = staffId;
    roster.rosterDate = rosterDate;
    roster.shiftId = shiftId;
    roster.status = status;
    roster.remark = remark;
    roster.createdBy = createdBy;
    return roster;
  }

  /**
   * Derived display state shown in the week grid and list badge:
   * Completed / Absent / Leave come from the attendance record, Missed when
   * the date passed without one, otherwise the stored roster status
   * (Scheduled).
   */
  public String displayState() {
    if (attendanceStatus != null) {
      return switch (attendanceStatus) {
        case "ABSENT" -> "Absent";
        case "LEAVE" -> "Leave";
        default -> "Completed";
      };
    }
    if (rosterDate != null && rosterDate.isBefore(LocalDate.now())) {
      return "Missed";
    }
    return status == null || status.isBlank() ? "Scheduled" : status;
  }

  /**
   * Row shape expected by the flat list table:
   * ID, Staff, Date, Shift, State, Remark.
   */
  public Object[] toTableRow() {
    return new Object[]{
      id,
      staffName == null ? "" : staffName,
      rosterDate == null ? "" : rosterDate.toString(),
      shiftLabel == null ? "" : shiftLabel,
      displayState(),
      remark == null ? "" : remark
    };
  }

  public long getId() {
    return id;
  }

  public void setId(long id) {
    this.id = id;
  }

  public long getShiftId() {
    return shiftId;
  }

  public void setShiftId(long shiftId) {
    this.shiftId = shiftId;
  }

  public long getStaffId() {
    return staffId;
  }

  public void setStaffId(long staffId) {
    this.staffId = staffId;
  }

  public LocalDate getRosterDate() {
    return rosterDate;
  }

  public void setRosterDate(LocalDate rosterDate) {
    this.rosterDate = rosterDate;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getRemark() {
    return remark;
  }

  public void setRemark(String remark) {
    this.remark = remark;
  }

  public Long getCreatedBy() {
    return createdBy;
  }

  public void setCreatedBy(Long createdBy) {
    this.createdBy = createdBy;
  }

  public Long getModifiedBy() {
    return modifiedBy;
  }

  public void setModifiedBy(Long modifiedBy) {
    this.modifiedBy = modifiedBy;
  }

  public String getStaffName() {
    return staffName;
  }

  public void setStaffName(String staffName) {
    this.staffName = staffName;
  }

  public String getShiftLabel() {
    return shiftLabel;
  }

  public void setShiftLabel(String shiftLabel) {
    this.shiftLabel = shiftLabel;
  }

  public boolean isOvernight() {
    return shiftOvernight;
  }

  public void setOvernight(boolean shiftOvernight) {
    this.shiftOvernight = shiftOvernight;
  }

  public java.time.LocalTime getShiftStartTime() {
    return shiftStartTime;
  }

  public void setShiftStartTime(java.time.LocalTime shiftStartTime) {
    this.shiftStartTime = shiftStartTime;
  }

  /**
   * Time-of-day band of the shift, used by the week grid to tint chips:
   * Night when the shift runs overnight, AM when it starts before noon,
   * otherwise PM. Derived from times, not from the shift code text.
   */
  public String shiftBand() {
    if (shiftOvernight) {
      return "NIGHT";
    }
    if (shiftStartTime != null && shiftStartTime.getHour() < 12) {
      return "AM";
    }
    return "PM";
  }

  public String getAttendanceStatus() {
    return attendanceStatus;
  }

  public void setAttendanceStatus(String attendanceStatus) {
    this.attendanceStatus = attendanceStatus;
  }
}
