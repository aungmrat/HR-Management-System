package hrms.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * LeaveApplication entity mapped from the {@code leave_application} table.
 */
public class LeaveApplication {

  private long id;
  private long staffId;
  private long leaveTypeId;
  private LocalDate fromDate;
  private LocalDate toDate;
  private boolean halfDay;
  /** Half-day period: 'AM' or 'PM'; null when not a half day. */
  private String halfDayPeriod;
  private BigDecimal totalDays;
  private BigDecimal businessDays;
  private LocalDate requestedDate;
  private Long approverId;
  private String status = "PENDING";
  private LocalDate approvedDate;
  private String rejectReason;
  private String remark;
  private Long createdBy;
  private LocalDateTime createdDate;
  private Long modifiedBy;
  private LocalDateTime modifiedDate;
  private boolean deleted;
  private Long deletedBy;
  private LocalDateTime deletedDate;
  /** Display names resolved by the repository via joins. */
  private String staffName;
  private String leaveTypeName;
  private String approverName;

  public LeaveApplication() {
  }

  public static LeaveApplication create(long staffId, long leaveTypeId,
          LocalDate fromDate, LocalDate toDate, boolean halfDay, String halfDayPeriod,
          BigDecimal totalDays, BigDecimal businessDays, LocalDate requestedDate,
          String remark, Long createdBy) {
    LeaveApplication application = new LeaveApplication();
    application.staffId = staffId;
    application.leaveTypeId = leaveTypeId;
    application.fromDate = fromDate;
    application.toDate = toDate;
    application.halfDay = halfDay;
    application.halfDayPeriod = halfDay ? halfDayPeriod : null;
    application.totalDays = totalDays;
    application.businessDays = businessDays;
    application.requestedDate = requestedDate;
    application.remark = remark;
    application.createdBy = createdBy;
    return application;
  }

  public boolean isPending() {
    return "PENDING".equalsIgnoreCase(status);
  }

  /**
   * Row shape expected by {@code HrmsTable}:
   * ID, Staff, Leave Type, From Date, To Date, Total Days, Business Days,
   * Half Day, Status, Requested Date, Approver, Approved Date, Remark.
   */
  public Object[] toTableRow() {
    return new Object[]{
      id,
      staffName == null ? "" : staffName,
      leaveTypeName == null ? "" : leaveTypeName,
      fromDate == null ? "" : fromDate,
      toDate == null ? "" : toDate,
      totalDays == null ? "" : totalDays,
      businessDays == null ? "" : businessDays,
      halfDay ? (halfDayPeriod == null ? "Yes" : halfDayPeriod) : "No",
      status == null ? "" : status,
      requestedDate == null ? "" : requestedDate,
      approverName == null ? "—" : approverName,
      approvedDate == null ? "" : approvedDate,
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

  public long getLeaveTypeId() {
    return leaveTypeId;
  }

  public void setLeaveTypeId(long leaveTypeId) {
    this.leaveTypeId = leaveTypeId;
  }

  public LocalDate getFromDate() {
    return fromDate;
  }

  public void setFromDate(LocalDate fromDate) {
    this.fromDate = fromDate;
  }

  public LocalDate getToDate() {
    return toDate;
  }

  public void setToDate(LocalDate toDate) {
    this.toDate = toDate;
  }

  public boolean isHalfDay() {
    return halfDay;
  }

  public void setHalfDay(boolean halfDay) {
    this.halfDay = halfDay;
  }

  public String getHalfDayPeriod() {
    return halfDayPeriod;
  }

  public void setHalfDayPeriod(String halfDayPeriod) {
    this.halfDayPeriod = halfDayPeriod;
  }

  public BigDecimal getTotalDays() {
    return totalDays;
  }

  public void setTotalDays(BigDecimal totalDays) {
    this.totalDays = totalDays;
  }

  public BigDecimal getBusinessDays() {
    return businessDays;
  }

  public void setBusinessDays(BigDecimal businessDays) {
    this.businessDays = businessDays;
  }

  public LocalDate getRequestedDate() {
    return requestedDate;
  }

  public void setRequestedDate(LocalDate requestedDate) {
    this.requestedDate = requestedDate;
  }

  public Long getApproverId() {
    return approverId;
  }

  public void setApproverId(Long approverId) {
    this.approverId = approverId;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public LocalDate getApprovedDate() {
    return approvedDate;
  }

  public void setApprovedDate(LocalDate approvedDate) {
    this.approvedDate = approvedDate;
  }

  public String getRejectReason() {
    return rejectReason;
  }

  public void setRejectReason(String rejectReason) {
    this.rejectReason = rejectReason;
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

  public LocalDateTime getCreatedDate() {
    return createdDate;
  }

  public void setCreatedDate(LocalDateTime createdDate) {
    this.createdDate = createdDate;
  }

  public Long getModifiedBy() {
    return modifiedBy;
  }

  public void setModifiedBy(Long modifiedBy) {
    this.modifiedBy = modifiedBy;
  }

  public LocalDateTime getModifiedDate() {
    return modifiedDate;
  }

  public void setModifiedDate(LocalDateTime modifiedDate) {
    this.modifiedDate = modifiedDate;
  }

  public boolean isDeleted() {
    return deleted;
  }

  public void setDeleted(boolean deleted) {
    this.deleted = deleted;
  }

  public Long getDeletedBy() {
    return deletedBy;
  }

  public void setDeletedBy(Long deletedBy) {
    this.deletedBy = deletedBy;
  }

  public LocalDateTime getDeletedDate() {
    return deletedDate;
  }

  public void setDeletedDate(LocalDateTime deletedDate) {
    this.deletedDate = deletedDate;
  }

  public String getStaffName() {
    return staffName;
  }

  public void setStaffName(String staffName) {
    this.staffName = staffName;
  }

  public String getLeaveTypeName() {
    return leaveTypeName;
  }

  public void setLeaveTypeName(String leaveTypeName) {
    this.leaveTypeName = leaveTypeName;
  }

  public String getApproverName() {
    return approverName;
  }

  public void setApproverName(String approverName) {
    this.approverName = approverName;
  }
}
