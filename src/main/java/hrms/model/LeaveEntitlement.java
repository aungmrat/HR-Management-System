package hrms.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * LeaveEntitlement entity mapped from the {@code leave_entitlement} table (one
 * row per staff, leave type and entitlement year).
 */
public class LeaveEntitlement {

  private long id;
  private long staffId;
  private long leaveTypeId;
  private int entitledDays;
  private Integer carriedDays;
  private Integer usedDays;
  private LocalDate effectiveDate;
  private LocalDate expiryDate;
  private String remark;
  private Long createdBy;
  private LocalDateTime createdDate;
  private Long modifiedBy;
  private LocalDateTime modifiedDate;
  private boolean deleted;
  private Long deletedBy;
  private LocalDateTime deletedDate;
  /** Display name of the creator, resolved by the repository via user/staff join. */
  private String creatorName;
  /** Staff display (code + name), resolved by the repository via the staff join. */
  private String staffName;
  /** Leave type display (code + name), resolved by the repository via the type join. */
  private String leaveTypeName;

  public LeaveEntitlement() {
  }

  public LeaveEntitlement(long id, long staffId, long leaveTypeId, int entitledDays,
          Integer carriedDays, Integer usedDays, LocalDate effectiveDate,
          LocalDate expiryDate, String remark, Long createdBy, LocalDateTime createdDate) {
    this.id = id;
    this.staffId = staffId;
    this.leaveTypeId = leaveTypeId;
    this.entitledDays = entitledDays;
    this.carriedDays = carriedDays;
    this.usedDays = usedDays;
    this.effectiveDate = effectiveDate;
    this.expiryDate = expiryDate;
    this.remark = remark;
    this.createdBy = createdBy;
    this.createdDate = createdDate;
  }

  /** New unsaved entitlement for insert (id = 0 until persisted). */
  public static LeaveEntitlement create(long staffId, long leaveTypeId, int entitledDays,
          Integer carriedDays, Integer usedDays, LocalDate effectiveDate,
          LocalDate expiryDate, String remark, Long createdBy) {
    LeaveEntitlement entitlement = new LeaveEntitlement();
    entitlement.staffId = staffId;
    entitlement.leaveTypeId = leaveTypeId;
    entitlement.entitledDays = entitledDays;
    entitlement.carriedDays = carriedDays;
    entitlement.usedDays = usedDays;
    entitlement.effectiveDate = effectiveDate;
    entitlement.expiryDate = expiryDate;
    entitlement.remark = remark;
    entitlement.createdBy = createdBy;
    return entitlement;
  }

  /** Days still available: entitled + carried − used (never below 0). */
  public int getRemainingDays() {
    int remaining = entitledDays
            + (carriedDays == null ? 0 : carriedDays)
            - (usedDays == null ? 0 : usedDays);
    return Math.max(remaining, 0);
  }

  /**
   * Row shape expected by {@code HrmsTable}:
   * ID, Staff, Leave Type, Entitled Days, Carried Days, Used Days, Remaining,
   * Effective Date, Expiry Date, Remark, Created By, Created Date.
   */
  public Object[] toTableRow() {
    return new Object[]{
      id,
      staffName != null ? staffName : String.valueOf(staffId),
      leaveTypeName != null ? leaveTypeName : String.valueOf(leaveTypeId),
      entitledDays,
      carriedDays == null ? 0 : carriedDays,
      usedDays == null ? 0 : usedDays,
      getRemainingDays(),
      effectiveDate == null ? "" : effectiveDate.toString(),
      expiryDate == null ? "" : expiryDate.toString(),
      remark == null ? "" : remark,
      creatorName != null ? creatorName
              : createdBy == null ? "—" : String.valueOf(createdBy),
      createdDate == null ? "" : createdDate.toString().replace('T', ' ')
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

  public int getEntitledDays() {
    return entitledDays;
  }

  public void setEntitledDays(int entitledDays) {
    this.entitledDays = entitledDays;
  }

  public Integer getCarriedDays() {
    return carriedDays;
  }

  public void setCarriedDays(Integer carriedDays) {
    this.carriedDays = carriedDays;
  }

  public Integer getUsedDays() {
    return usedDays;
  }

  public void setUsedDays(Integer usedDays) {
    this.usedDays = usedDays;
  }

  public LocalDate getEffectiveDate() {
    return effectiveDate;
  }

  public void setEffectiveDate(LocalDate effectiveDate) {
    this.effectiveDate = effectiveDate;
  }

  public LocalDate getExpiryDate() {
    return expiryDate;
  }

  public void setExpiryDate(LocalDate expiryDate) {
    this.expiryDate = expiryDate;
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

  public String getCreatorName() {
    return creatorName;
  }

  public void setCreatorName(String creatorName) {
    this.creatorName = creatorName;
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
}
