package hrms.model;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;

/**
 * StaffBenefit entity mapped from the {@code staff_benefit} table: one
 * effective-dated benefit assignment per row, linking a staff member to a
 * {@code benefit} rule (the rule owns the rate; this table has no amount).
 * The open row ({@code endDate == null}) is the current assignment.
 */
public class StaffBenefit {

  private long id;
  private long staffId;
  private long benefitId;
  private LocalDate effectiveDate;
  private LocalDate endDate;
  private Long createdBy;
  private LocalDateTime createdDate;
  private Long modifiedBy;
  private LocalDateTime modifiedDate;
  private boolean deleted;
  private Long deletedBy;
  private LocalDateTime deletedDate;
  /** Staff code, resolved by the repository via the staff join. */
  private String staffCode;
  /** Staff name, resolved by the repository via the staff join. */
  private String staffName;
  /** Position name, resolved by the repository via the staff join. */
  private String positionName;
  /** Department id, resolved via the staff join (screen filter). */
  private Long departmentId;
  /** Benefit rule fields, resolved via the benefit join. */
  private String benefitName;
  private BigDecimal benefitRate;
  private String benefitFrequency;
  private String benefitUnit;
  /** Display name of the assigner, resolved via the user/staff join. */
  private String creatorName;

  public StaffBenefit() {
  }

  /**
   * Row shape for the Staff Benefit screen's table:
   * Staff ID, ID, Name, Position, Benefit, Rate, Frequency, Unit,
   * Effective From, End.
   */
  public Object[] toTableRow() {
    return new Object[]{
      staffId,
      id,
      staffName == null ? "" : staffName,
      positionName == null ? "—" : positionName,
      benefitName == null ? "" : benefitName,
      money(benefitRate),
      benefitFrequency == null ? "—" : benefitFrequency,
      benefitUnit == null ? "—" : benefitUnit,
      effectiveDate == null ? "—" : effectiveDate.toString(),
      endDate == null ? "—" : endDate.toString()
    };
  }

  /**
   * Row shape for the history dialog's table:
   * ID, Benefit, Rate, Frequency, Effective, End, Status, Assigned By.
   */
  public Object[] toHistoryTableRow() {
    return new Object[]{
      id,
      benefitName == null ? "" : benefitName,
      money(benefitRate),
      benefitFrequency == null ? "—" : benefitFrequency,
      effectiveDate == null ? "—" : effectiveDate.toString(),
      endDate == null ? "Current" : endDate.toString(),
      endDate == null ? "Active" : "Ended",
      creatorName != null ? creatorName
              : createdBy == null ? "—" : String.valueOf(createdBy)
    };
  }

  /** "100,000.00" formatted for display; "—" when the rule has no rate. */
  public String money(BigDecimal value) {
    if (value == null) {
      return "—";
    }
    return new DecimalFormat("#,##0.00",
            new DecimalFormatSymbols(Locale.ROOT)).format(value);
  }

  /** True when the assignment is still open (no end date). */
  public boolean isOpen() {
    return endDate == null;
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

  public long getBenefitId() {
    return benefitId;
  }

  public void setBenefitId(long benefitId) {
    this.benefitId = benefitId;
  }

  public LocalDate getEffectiveDate() {
    return effectiveDate;
  }

  public void setEffectiveDate(LocalDate effectiveDate) {
    this.effectiveDate = effectiveDate;
  }

  public LocalDate getEndDate() {
    return endDate;
  }

  public void setEndDate(LocalDate endDate) {
    this.endDate = endDate;
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

  public String getStaffCode() {
    return staffCode;
  }

  public void setStaffCode(String staffCode) {
    this.staffCode = staffCode;
  }

  public String getStaffName() {
    return staffName;
  }

  public void setStaffName(String staffName) {
    this.staffName = staffName;
  }

  public String getPositionName() {
    return positionName;
  }

  public void setPositionName(String positionName) {
    this.positionName = positionName;
  }

  public Long getDepartmentId() {
    return departmentId;
  }

  public void setDepartmentId(Long departmentId) {
    this.departmentId = departmentId;
  }

  public String getBenefitName() {
    return benefitName;
  }

  public void setBenefitName(String benefitName) {
    this.benefitName = benefitName;
  }

  public BigDecimal getBenefitRate() {
    return benefitRate;
  }

  public void setBenefitRate(BigDecimal benefitRate) {
    this.benefitRate = benefitRate;
  }

  public String getBenefitFrequency() {
    return benefitFrequency;
  }

  public void setBenefitFrequency(String benefitFrequency) {
    this.benefitFrequency = benefitFrequency;
  }

  public String getBenefitUnit() {
    return benefitUnit;
  }

  public void setBenefitUnit(String benefitUnit) {
    this.benefitUnit = benefitUnit;
  }

  public String getCreatorName() {
    return creatorName;
  }

  public void setCreatorName(String creatorName) {
    this.creatorName = creatorName;
  }
}
