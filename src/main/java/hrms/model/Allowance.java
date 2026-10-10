package hrms.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Allowance entity mapped from the {@code allowance} table.
 */
public class Allowance {

  private long id;
  private String name;
  private BigDecimal rate;
  private Integer minDistance;
  private Integer maxDistance;
  private String condition;
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

  public Allowance() {
  }

  public Allowance(long id, String name, BigDecimal rate, Integer minDistance,
          Integer maxDistance, String condition, String remark,
          Long createdBy, LocalDateTime createdDate) {
    this.id = id;
    this.name = name;
    this.rate = rate;
    this.minDistance = minDistance;
    this.maxDistance = maxDistance;
    this.condition = condition;
    this.remark = remark;
    this.createdBy = createdBy;
    this.createdDate = createdDate;
  }

  /** New unsaved allowance for insert (id = 0 until persisted). */
  public static Allowance create(String name, BigDecimal rate, Integer minDistance,
          Integer maxDistance, String condition, String remark, Long createdBy) {
    Allowance allowance = new Allowance();
    allowance.name = name;
    allowance.rate = rate;
    allowance.minDistance = minDistance;
    allowance.maxDistance = maxDistance;
    allowance.condition = condition;
    allowance.remark = remark;
    allowance.createdBy = createdBy;
    return allowance;
  }

  /**
   * Row shape expected by {@code HrmsTable}:
   * ID, Name, Rate, Min Distance, Max Distance, Condition, Remark,
   * Created By, Created Date.
   */
  public Object[] toTableRow() {
    return new Object[]{
      id,
      name == null ? "" : name,
      rate,
      minDistance,
      maxDistance,
      condition == null ? "" : condition,
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

  public Integer getMinDistance() {
    return minDistance;
  }

  public void setMinDistance(Integer minDistance) {
    this.minDistance = minDistance;
  }

  public Integer getMaxDistance() {
    return maxDistance;
  }

  public void setMaxDistance(Integer maxDistance) {
    this.maxDistance = maxDistance;
  }

  public String getCondition() {
    return condition;
  }

  public void setCondition(String condition) {
    this.condition = condition;
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
}
