package hrms.model;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;

/**
 * StaffSalary entity mapped from the {@code staff_salary} table: one
 * effective-dated salary row per staff member. The newest open row
 * ({@code endDate == null}) is the current salary; {@code staff.salary} stays
 * the registration snapshot, kept in sync by {@code StaffRepository}.
 */
public class StaffSalary {

  private long id;
  private long staffId;
  private Long salaryRateId;
  private BigDecimal baseSalary;
  private LocalDate effectiveDate;
  private LocalDate endDate;
  private String remark;
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
  /** Display name of the row creator, resolved via the user/staff join. */
  private String creatorName;
  /** Band bounds of the referenced salary rate, when one is set. */
  private BigDecimal rateBase;
  private BigDecimal rateCeil;

  public StaffSalary() {
  }

  /**
   * Row shape for the Staff Salary screen's current-salary table:
   * Staff ID, Code, Name, Position, Salary, Effective From, Rate Band.
   */
  public Object[] toCurrentTableRow() {
    return new Object[]{
      staffId,
      staffCode == null ? "" : staffCode,
      staffName == null ? "" : staffName,
      positionName == null ? "—" : positionName,
      money(baseSalary),
      effectiveDate == null ? "—" : effectiveDate.toString(),
      rateLabel()
    };
  }

  /**
   * Row shape for the history dialog's table:
   * ID, Effective, End, Amount, Rate Band, Remark, Created By, Created Date.
   */
  public Object[] toHistoryTableRow() {
    return new Object[]{
      id,
      effectiveDate == null ? "—" : effectiveDate.toString(),
      endDate == null ? "Current" : endDate.toString(),
      money(baseSalary),
      rateLabel(),
      remark == null ? "" : remark,
      creatorName != null ? creatorName
              : createdBy == null ? "—" : String.valueOf(createdBy),
      createdDate == null ? "" : createdDate.toString().replace('T', ' ')
    };
  }

  /** "500,000.00 – 600,000.00" when a band is set, "—" otherwise. */
  public String rateLabel() {
    if (rateBase == null || rateCeil == null) {
      return "—";
    }
    return money(rateBase) + " – " + money(rateCeil);
  }

  private static String money(BigDecimal value) {
    if (value == null) {
      return "—";
    }
    return new DecimalFormat("#,##0.00",
            new DecimalFormatSymbols(Locale.ROOT)).format(value);
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

  public Long getSalaryRateId() {
    return salaryRateId;
  }

  public void setSalaryRateId(Long salaryRateId) {
    this.salaryRateId = salaryRateId;
  }

  public BigDecimal getBaseSalary() {
    return baseSalary;
  }

  public void setBaseSalary(BigDecimal baseSalary) {
    this.baseSalary = baseSalary;
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

  public String getCreatorName() {
    return creatorName;
  }

  public void setCreatorName(String creatorName) {
    this.creatorName = creatorName;
  }

  public BigDecimal getRateBase() {
    return rateBase;
  }

  public void setRateBase(BigDecimal rateBase) {
    this.rateBase = rateBase;
  }

  public BigDecimal getRateCeil() {
    return rateCeil;
  }

  public void setRateCeil(BigDecimal rateCeil) {
    this.rateCeil = rateCeil;
  }
}
