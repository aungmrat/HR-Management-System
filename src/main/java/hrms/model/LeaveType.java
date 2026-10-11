package hrms.model;

import java.time.LocalDateTime;

/**
 * LeaveType entity mapped from the {@code leave_type} table.
 */
public class LeaveType {

  private long id;
  private String code;
  private String name;
  private String description;
  private int standardDaysPerYear;
  private int maxDaysPerYear;
  private boolean halfDayAllowed;
  private boolean carryForward;
  /** Maximum days that may be carried into the next year; null when not carried. */
  private Integer carryMaxDays;
  private boolean requiresPreApproval;
  /** Gender restriction: 'Male', 'Female' or 'Both'. */
  private String genderRestriction = "Both";
  private boolean active;
  private Long createdBy;
  private LocalDateTime createdDate;
  private Long modifiedBy;
  private LocalDateTime modifiedDate;
  private boolean deleted;
  private Long deletedBy;
  private LocalDateTime deletedDate;
  /** Display name of the creator, resolved by the repository via user/staff join. */
  private String creatorName;

  public LeaveType() {
  }

  public LeaveType(long id, String code, String name, String description,
          int standardDaysPerYear, int maxDaysPerYear, boolean halfDayAllowed,
          boolean carryForward, Integer carryMaxDays, boolean requiresPreApproval,
          boolean active, Long createdBy, LocalDateTime createdDate) {
    this.id = id;
    this.code = code;
    this.name = name;
    this.description = description;
    this.standardDaysPerYear = standardDaysPerYear;
    this.maxDaysPerYear = maxDaysPerYear;
    this.halfDayAllowed = halfDayAllowed;
    this.carryForward = carryForward;
    this.carryMaxDays = carryMaxDays;
    this.requiresPreApproval = requiresPreApproval;
    this.active = active;
    this.createdBy = createdBy;
    this.createdDate = createdDate;
  }

  /** New unsaved leave type for insert (id = 0 until persisted). */
  public static LeaveType create(String code, String name, String description,
          int standardDaysPerYear, int maxDaysPerYear, boolean halfDayAllowed,
          boolean carryForward, Integer carryMaxDays, boolean requiresPreApproval,
          String genderRestriction, boolean active, Long createdBy) {
    LeaveType leaveType = new LeaveType();
    leaveType.code = code;
    leaveType.name = name;
    leaveType.description = description;
    leaveType.standardDaysPerYear = standardDaysPerYear;
    leaveType.maxDaysPerYear = maxDaysPerYear;
    leaveType.halfDayAllowed = halfDayAllowed;
    leaveType.carryForward = carryForward;
    leaveType.carryMaxDays = carryMaxDays;
    leaveType.requiresPreApproval = requiresPreApproval;
    leaveType.genderRestriction = genderRestriction == null || genderRestriction.isBlank()
            ? "Both" : genderRestriction;
    leaveType.active = active;
    leaveType.createdBy = createdBy;
    return leaveType;
  }

  /**
   * Row shape expected by {@code HrmsTable}:
   * ID, Code, Name, Standard Days, Max Days, Half Day, Carry Forward,
   * Carry Max Days, Pre-approval, Gender, Active, Created By, Created Date.
   * Description is omitted (up to 500 characters).
   */
  public Object[] toTableRow() {
    return new Object[]{
      id,
      code == null ? "" : code,
      name == null ? "" : name,
      standardDaysPerYear,
      maxDaysPerYear,
      halfDayAllowed ? "Yes" : "No",
      carryForward ? "Yes" : "No",
      carryMaxDays == null ? "" : carryMaxDays,
      requiresPreApproval ? "Yes" : "No",
      genderRestriction == null || genderRestriction.isBlank() ? "Both" : genderRestriction,
      active ? "Yes" : "No",
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

  public String getCode() {
    return code;
  }

  public void setCode(String code) {
    this.code = code;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public int getStandardDaysPerYear() {
    return standardDaysPerYear;
  }

  public void setStandardDaysPerYear(int standardDaysPerYear) {
    this.standardDaysPerYear = standardDaysPerYear;
  }

  public int getMaxDaysPerYear() {
    return maxDaysPerYear;
  }

  public void setMaxDaysPerYear(int maxDaysPerYear) {
    this.maxDaysPerYear = maxDaysPerYear;
  }

  public boolean isHalfDayAllowed() {
    return halfDayAllowed;
  }

  public void setHalfDayAllowed(boolean halfDayAllowed) {
    this.halfDayAllowed = halfDayAllowed;
  }

  public boolean isCarryForward() {
    return carryForward;
  }

  public void setCarryForward(boolean carryForward) {
    this.carryForward = carryForward;
  }

  public Integer getCarryMaxDays() {
    return carryMaxDays;
  }

  public void setCarryMaxDays(Integer carryMaxDays) {
    this.carryMaxDays = carryMaxDays;
  }

  public boolean isRequiresPreApproval() {
    return requiresPreApproval;
  }

  public void setRequiresPreApproval(boolean requiresPreApproval) {
    this.requiresPreApproval = requiresPreApproval;
  }

  public String getGenderRestriction() {
    return genderRestriction == null || genderRestriction.isBlank()
            ? "Both" : genderRestriction;
  }

  public void setGenderRestriction(String genderRestriction) {
    this.genderRestriction = genderRestriction == null || genderRestriction.isBlank()
            ? "Both" : genderRestriction;
  }

  /** True when this type may be granted to an employee of the given sex. */
  public boolean allowsSex(String sex) {
    String restriction = getGenderRestriction();
    if ("Both".equalsIgnoreCase(restriction)) {
      return true;
    }
    return restriction.equalsIgnoreCase(sex);
  }

  public boolean isActive() {
    return active;
  }

  public void setActive(boolean active) {
    this.active = active;
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
}
