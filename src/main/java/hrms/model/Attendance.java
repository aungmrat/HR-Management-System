package hrms.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Attendance entity mapped from the {@code attendance} table.
 */
public class Attendance {

  private long id;
  private long staffId;
  private LocalDate date;
  private long shiftId;
  private LocalTime timeIn;
  private LocalTime timeOut;
  private BigDecimal totalHours;
  private String status;
  private String remark;
  private Long createdBy;
  private Long modifiedBy;

  // Display-only fields filled by the repository JOIN (not persisted columns).
  private String staffName;
  private String shiftLabel;

  public Attendance() {
  }

  /** New unsaved attendance for insert (id = 0 until persisted). */
  public static Attendance create(long staffId, LocalDate date, long shiftId,
          LocalTime timeIn, LocalTime timeOut, BigDecimal totalHours,
          String status, String remark, Long createdBy) {
    Attendance attendance = new Attendance();
    attendance.staffId = staffId;
    attendance.date = date;
    attendance.shiftId = shiftId;
    attendance.timeIn = timeIn;
    attendance.timeOut = timeOut;
    attendance.totalHours = totalHours;
    attendance.status = status;
    attendance.remark = remark;
    attendance.createdBy = createdBy;
    return attendance;
  }

  /**
   * Row shape expected by {@code HrmsTable}:
   * ID, Staff, Date, Shift, Time In, Time Out, Total Hours, Status, Remark.
   */
  public Object[] toTableRow() {
    return new Object[]{
      id,
      staffName == null ? "" : staffName,
      date == null ? "" : date.toString(),
      shiftLabel == null ? "" : shiftLabel,
      timeIn == null ? "" : timeIn.toString(),
      timeOut == null ? "" : timeOut.toString(),
      totalHours == null ? BigDecimal.ZERO : totalHours,
      status == null ? "" : status,
      remark == null ? "" : remark
    };
  }

  public long getId() {
    return id;
  }

  public void setId(long id) {
    this.id = id;
  }

  public long getStaffId() {
    return staffId;
  }

  public void setStaffId(long staffId) {
    this.staffId = staffId;
  }

  public LocalDate getDate() {
    return date;
  }

  public void setDate(LocalDate date) {
    this.date = date;
  }

  public long getShiftId() {
    return shiftId;
  }

  public void setShiftId(long shiftId) {
    this.shiftId = shiftId;
  }

  public LocalTime getTimeIn() {
    return timeIn;
  }

  public void setTimeIn(LocalTime timeIn) {
    this.timeIn = timeIn;
  }

  public LocalTime getTimeOut() {
    return timeOut;
  }

  public void setTimeOut(LocalTime timeOut) {
    this.timeOut = timeOut;
  }

  public BigDecimal getTotalHours() {
    return totalHours;
  }

  public void setTotalHours(BigDecimal totalHours) {
    this.totalHours = totalHours;
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
}
