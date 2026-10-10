package hrms.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Tax entity mapped from the {@code tax} table (a personal-income-tax bracket).
 */
public class Tax {

  private long id;
  private String bracketName;
  private BigDecimal minAmount;
  /** Upper bound of the bracket; null means open-ended (top bracket). */
  private BigDecimal maxAmount;
  /** Tax rate as a fraction (0.1500 = 15%); column is DECIMAL(5,4). */
  private BigDecimal taxRate;
  private Long createdBy;
  private LocalDateTime createdDate;
  private Long modifiedBy;
  private LocalDateTime modifiedDate;
  private boolean deleted;
  private Long deletedBy;
  private LocalDateTime deletedDate;
  /** Display name of the creator, resolved by the repository via user/staff join. */
  private String creatorName;

  public Tax() {
  }

  public Tax(long id, String bracketName, BigDecimal minAmount, BigDecimal maxAmount,
          BigDecimal taxRate, Long createdBy, LocalDateTime createdDate) {
    this.id = id;
    this.bracketName = bracketName;
    this.minAmount = minAmount;
    this.maxAmount = maxAmount;
    this.taxRate = taxRate;
    this.createdBy = createdBy;
    this.createdDate = createdDate;
  }

  /** New unsaved tax bracket for insert (id = 0 until persisted). */
  public static Tax create(String bracketName, BigDecimal minAmount,
          BigDecimal maxAmount, BigDecimal taxRate, Long createdBy) {
    Tax tax = new Tax();
    tax.bracketName = bracketName;
    tax.minAmount = minAmount;
    tax.maxAmount = maxAmount;
    tax.taxRate = taxRate;
    tax.createdBy = createdBy;
    return tax;
  }

  /**
   * Row shape expected by {@code HrmsTable}:
   * ID, Bracket Name, Min Amount, Max Amount, Tax Rate, Created By, Created Date.
   * The rate is displayed as a percent (stored fraction × 100).
   */
  public Object[] toTableRow() {
    return new Object[]{
      id,
      bracketName == null ? "" : bracketName,
      minAmount,
      maxAmount,
      taxRate == null ? "" : taxRate.multiply(BigDecimal.valueOf(100))
              .stripTrailingZeros().toPlainString() + "%",
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

  public String getBracketName() {
    return bracketName;
  }

  public void setBracketName(String bracketName) {
    this.bracketName = bracketName;
  }

  public BigDecimal getMinAmount() {
    return minAmount;
  }

  public void setMinAmount(BigDecimal minAmount) {
    this.minAmount = minAmount;
  }

  public BigDecimal getMaxAmount() {
    return maxAmount;
  }

  public void setMaxAmount(BigDecimal maxAmount) {
    this.maxAmount = maxAmount;
  }

  public BigDecimal getTaxRate() {
    return taxRate;
  }

  public void setTaxRate(BigDecimal taxRate) {
    this.taxRate = taxRate;
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
