package hrms.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Attendance entity mapped from the {@code attendance} table.
 */
public class Attendance {

  private long id;
  private long staffId;
  private Long rosterId;
  private LocalDate date;
  private long shiftId;
  private LocalTime timeIn;
  private LocalTime timeOut;
  private BigDecimal totalHours = BigDecimal.ZERO;
  private BigDecimal overtimeHours = BigDecimal.ZERO;
  private String status;
  private String remark;
  private Long createdBy;
  private Long modifiedBy;

  // Display-only fields filled by the repository JOIN (not persisted columns).
  private String staffName;
  private String shiftLabel;
  private long punchCount;
  private LocalTime firstIn;
  private LocalTime lastOut;

  /** Ordered IN/OUT punches backing timeIn/timeOut/totalHours. */
  private List<LocalTime> punches = new ArrayList<>();

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
   * ID, Staff, Date, Shift, Punches, First In, Last Out, Hours, OT, Status, Remark.
   * First In / Last Out show an em dash when no punch exists (ABSENT/LEAVE).
   */
  public Object[] toTableRow() {
    return new Object[]{
      id,
      staffName == null ? "" : staffName,
      date == null ? "" : date.toString(),
      shiftLabel == null || shiftLabel.isBlank() ? "—" : shiftLabel,
      punchCount,
      firstIn == null ? "—" : firstIn.toString(),
      lastOut == null ? "—" : lastOut.toString(),
      totalHours == null ? BigDecimal.ZERO : totalHours,
      overtimeHours == null ? BigDecimal.ZERO : overtimeHours,
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

  public Long getRosterId() {
    return rosterId;
  }

  public void setRosterId(Long rosterId) {
    this.rosterId = rosterId;
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

  public BigDecimal getOvertimeHours() {
    return overtimeHours;
  }

  public void setOvertimeHours(BigDecimal overtimeHours) {
    this.overtimeHours = overtimeHours;
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

  public long getPunchCount() {
    return punchCount;
  }

  public void setPunchCount(long punchCount) {
    this.punchCount = punchCount;
  }

  public LocalTime getFirstIn() {
    return firstIn;
  }

  public void setFirstIn(LocalTime firstIn) {
    this.firstIn = firstIn;
  }

  public LocalTime getLastOut() {
    return lastOut;
  }

  public void setLastOut(LocalTime lastOut) {
    this.lastOut = lastOut;
  }

  public List<LocalTime> getPunches() {
    return punches;
  }

  public void setPunches(List<LocalTime> punches) {
    this.punches = punches == null ? new ArrayList<>() : new ArrayList<>(punches);
  }

  /**
   * Recomputes timeIn, timeOut and totalHours from the punch list: pairs are
   * summed (an overnight pair adds 24h), an unpaired final punch is ignored,
   * and an empty list forces 00:00-00:00 with 0 hours (ABSENT/LEAVE rows).
   */
  public void recomputeFromPunches() {
    if (punches == null || punches.isEmpty()) {
      timeIn = LocalTime.MIDNIGHT;
      timeOut = LocalTime.MIDNIGHT;
      totalHours = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
      return;
    }
    timeIn = punches.get(0);
    timeOut = punches.get(punches.size() - 1);
    BigDecimal hours = BigDecimal.ZERO;
    for (int i = 0; i + 1 < punches.size(); i += 2) {
      hours = hours.add(hoursBetween(punches.get(i), punches.get(i + 1)));
    }
    totalHours = hours.setScale(2, RoundingMode.HALF_UP);
  }

  private static BigDecimal hoursBetween(LocalTime in, LocalTime out) {
    LocalDateTime inDt = LocalDateTime.of(LocalDate.EPOCH, in);
    LocalDateTime outDt = LocalDateTime.of(LocalDate.EPOCH, out);
    if (!outDt.isAfter(inDt)) {
      outDt = outDt.plusDays(1); // overnight pair
    }
    long minutes = Duration.between(inDt, outDt).toMinutes();
    return BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(60), 4,
            RoundingMode.HALF_UP);
  }
}
