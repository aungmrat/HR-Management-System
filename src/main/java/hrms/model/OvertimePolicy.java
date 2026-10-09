package hrms.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Overtime policy entity mapped from the {@code overtime_policy} table.
 */
public class OvertimePolicy {

  private long id;
  private int minOtHour;
  private int maxOtHourPerMonth;
  private BigDecimal otMultiplier;
  private BigDecimal weekendOtMultiplier;
  private BigDecimal holidayOtMultiplier;
  private Long createdBy;
  private LocalDateTime createdDate;
  private Long modifiedBy;
  private LocalDateTime modifiedDate;
  private boolean deleted;
  private Long deletedBy;
  private LocalDateTime deletedDate;
  /** Display name of the creator, resolved by the repository via user/staff join. */
  private String creatorName;

  public OvertimePolicy() {
  }

  public OvertimePolicy(long id, int minOtHour, int maxOtHourPerMonth,
          BigDecimal otMultiplier, BigDecimal weekendOtMultiplier,
          BigDecimal holidayOtMultiplier, Long createdBy, LocalDateTime createdDate) {
    this.id = id;
    this.minOtHour = minOtHour;
    this.maxOtHourPerMonth = maxOtHourPerMonth;
    this.otMultiplier = otMultiplier;
    this.weekendOtMultiplier = weekendOtMultiplier;
    this.holidayOtMultiplier = holidayOtMultiplier;
    this.createdBy = createdBy;
    this.createdDate = createdDate;
  }

  /** New unsaved overtime policy for insert (id = 0 until persisted). */
  public static OvertimePolicy create(int minOtHour, int maxOtHourPerMonth,
          BigDecimal otMultiplier, BigDecimal weekendOtMultiplier,
          BigDecimal holidayOtMultiplier, Long createdBy) {
    OvertimePolicy policy = new OvertimePolicy();
    policy.minOtHour = minOtHour;
    policy.maxOtHourPerMonth = maxOtHourPerMonth;
    policy.otMultiplier = otMultiplier;
    policy.weekendOtMultiplier = weekendOtMultiplier;
    policy.holidayOtMultiplier = holidayOtMultiplier;
    policy.createdBy = createdBy;
    return policy;
  }

  /**
   * Row shape expected by {@code HrmsTable}:
   * ID, Min OT Hour, Max OT Hour / Month, OT Multiplier, Weekend Multiplier,
   * Holiday Multiplier, Created By, Created Date.
   */
  public Object[] toTableRow() {
    return new Object[]{
      id,
      minOtHour,
      maxOtHourPerMonth,
      otMultiplier,
      weekendOtMultiplier,
      holidayOtMultiplier,
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

  public int getMinOtHour() {
    return minOtHour;
  }

  public void setMinOtHour(int minOtHour) {
    this.minOtHour = minOtHour;
  }

  public int getMaxOtHourPerMonth() {
    return maxOtHourPerMonth;
  }

  public void setMaxOtHourPerMonth(int maxOtHourPerMonth) {
    this.maxOtHourPerMonth = maxOtHourPerMonth;
  }

  public BigDecimal getOtMultiplier() {
    return otMultiplier;
  }

  public void setOtMultiplier(BigDecimal otMultiplier) {
    this.otMultiplier = otMultiplier;
  }

  public BigDecimal getWeekendOtMultiplier() {
    return weekendOtMultiplier;
  }

  public void setWeekendOtMultiplier(BigDecimal weekendOtMultiplier) {
    this.weekendOtMultiplier = weekendOtMultiplier;
  }

  public BigDecimal getHolidayOtMultiplier() {
    return holidayOtMultiplier;
  }

  public void setHolidayOtMultiplier(BigDecimal holidayOtMultiplier) {
    this.holidayOtMultiplier = holidayOtMultiplier;
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
