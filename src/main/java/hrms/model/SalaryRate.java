package hrms.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * SalaryRate entity mapped from the {@code salary_rate} table.
 */
public class SalaryRate {

  private long id;
  private long roleId;
  private BigDecimal baseSalary;
  private BigDecimal ceilSalary;
  private String currency;
  private String payFrequency;
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
  /** Role name, resolved by the repository via the role join. */
  private String roleName;

  public SalaryRate() {
  }

  public SalaryRate(long id, long roleId, BigDecimal baseSalary, BigDecimal ceilSalary,
          String currency, String payFrequency, String remark,
          Long createdBy, LocalDateTime createdDate) {
    this.id = id;
    this.roleId = roleId;
    this.baseSalary = baseSalary;
    this.ceilSalary = ceilSalary;
    this.currency = currency;
    this.payFrequency = payFrequency;
    this.remark = remark;
    this.createdBy = createdBy;
    this.createdDate = createdDate;
  }

  /** New unsaved salary rate for insert (id = 0 until persisted). */
  public static SalaryRate create(long roleId, BigDecimal baseSalary,
          BigDecimal ceilSalary, String currency, String payFrequency,
          String remark, Long createdBy) {
    SalaryRate salaryRate = new SalaryRate();
    salaryRate.roleId = roleId;
    salaryRate.baseSalary = baseSalary;
    salaryRate.ceilSalary = ceilSalary;
    salaryRate.currency = currency;
    salaryRate.payFrequency = payFrequency;
    salaryRate.remark = remark;
    salaryRate.createdBy = createdBy;
    return salaryRate;
  }

  /**
   * Row shape expected by {@code HrmsTable}:
   * ID, Role, Base Salary, Ceil Salary, Currency, Pay Frequency, Remark,
   * Created By, Created Date.
   */
  public Object[] toTableRow() {
    return new Object[]{
      id,
      roleName != null ? roleName : String.valueOf(roleId),
      baseSalary,
      ceilSalary,
      currency == null ? "" : currency,
      payFrequency == null ? "" : payFrequency,
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

  public long getRoleId() {
    return roleId;
  }

  public void setRoleId(long roleId) {
    this.roleId = roleId;
  }

  public BigDecimal getBaseSalary() {
    return baseSalary;
  }

  public void setBaseSalary(BigDecimal baseSalary) {
    this.baseSalary = baseSalary;
  }

  public BigDecimal getCeilSalary() {
    return ceilSalary;
  }

  public void setCeilSalary(BigDecimal ceilSalary) {
    this.ceilSalary = ceilSalary;
  }

  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String currency) {
    this.currency = currency;
  }

  public String getPayFrequency() {
    return payFrequency;
  }

  public void setPayFrequency(String payFrequency) {
    this.payFrequency = payFrequency;
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

  public String getRoleName() {
    return roleName;
  }

  public void setRoleName(String roleName) {
    this.roleName = roleName;
  }
}
