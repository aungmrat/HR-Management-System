package hrms.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Benefit entity mapped from the {@code benefit} table.
 */
public class Benefit {

  private long id;
  private String name;
  private BigDecimal rate;
  private String frequency;
  private String unit;
  private BigDecimal minAnnualIncome;
  private BigDecimal maxAnnualIncome;
  private Integer minAge;
  private Integer maxAge;
  private boolean hasFamily;
  private boolean hasChildren;
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

  public Benefit() {
  }

  public Benefit(long id, String name, BigDecimal rate, String frequency,
          String unit, BigDecimal minAnnualIncome, BigDecimal maxAnnualIncome,
          Integer minAge, Integer maxAge, boolean hasFamily, boolean hasChildren,
          boolean active, Long createdBy, LocalDateTime createdDate) {
    this.id = id;
    this.name = name;
    this.rate = rate;
    this.frequency = frequency;
    this.unit = unit;
    this.minAnnualIncome = minAnnualIncome;
    this.maxAnnualIncome = maxAnnualIncome;
    this.minAge = minAge;
    this.maxAge = maxAge;
    this.hasFamily = hasFamily;
    this.hasChildren = hasChildren;
    this.active = active;
    this.createdBy = createdBy;
    this.createdDate = createdDate;
  }

  /** New unsaved benefit for insert (id = 0 until persisted). */
  public static Benefit create(String name, BigDecimal rate, String frequency,
          String unit, BigDecimal minAnnualIncome, BigDecimal maxAnnualIncome,
          Integer minAge, Integer maxAge, boolean hasFamily, boolean hasChildren,
          boolean active, Long createdBy) {
    Benefit benefit = new Benefit();
    benefit.name = name;
    benefit.rate = rate;
    benefit.frequency = frequency;
    benefit.unit = unit;
    benefit.minAnnualIncome = minAnnualIncome;
    benefit.maxAnnualIncome = maxAnnualIncome;
    benefit.minAge = minAge;
    benefit.maxAge = maxAge;
    benefit.hasFamily = hasFamily;
    benefit.hasChildren = hasChildren;
    benefit.active = active;
    benefit.createdBy = createdBy;
    return benefit;
  }

  /**
   * Row shape expected by {@code HrmsTable}:
   * ID, Name, Rate, Frequency, Unit, Min Annual Income, Max Annual Income,
   * Min Age, Max Age, Has Family, Has Children, Active, Created By,
   * Created Date.
   */
  public Object[] toTableRow() {
    return new Object[]{
      id,
      name == null ? "" : name,
      rate,
      frequency == null ? "" : frequency,
      unit == null ? "" : unit,
      minAnnualIncome,
      maxAnnualIncome,
      minAge,
      maxAge,
      hasFamily ? "Yes" : "No",
      hasChildren ? "Yes" : "No",
      active ? "Active" : "Inactive",
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

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public BigDecimal getRate() {
    return rate;
  }

  public void setRate(BigDecimal rate) {
    this.rate = rate;
  }

  public String getFrequency() {
    return frequency;
  }

  public void setFrequency(String frequency) {
    this.frequency = frequency;
  }

  public String getUnit() {
    return unit;
  }

  public void setUnit(String unit) {
    this.unit = unit;
  }

  public BigDecimal getMinAnnualIncome() {
    return minAnnualIncome;
  }

  public void setMinAnnualIncome(BigDecimal minAnnualIncome) {
    this.minAnnualIncome = minAnnualIncome;
  }

  public BigDecimal getMaxAnnualIncome() {
    return maxAnnualIncome;
  }

  public void setMaxAnnualIncome(BigDecimal maxAnnualIncome) {
    this.maxAnnualIncome = maxAnnualIncome;
  }

  public Integer getMinAge() {
    return minAge;
  }

  public void setMinAge(Integer minAge) {
    this.minAge = minAge;
  }

  public Integer getMaxAge() {
    return maxAge;
  }

  public void setMaxAge(Integer maxAge) {
    this.maxAge = maxAge;
  }

  public boolean isHasFamily() {
    return hasFamily;
  }

  public void setHasFamily(boolean hasFamily) {
    this.hasFamily = hasFamily;
  }

  public boolean isHasChildren() {
    return hasChildren;
  }

  public void setHasChildren(boolean hasChildren) {
    this.hasChildren = hasChildren;
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
