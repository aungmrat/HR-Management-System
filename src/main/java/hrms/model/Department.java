package hrms.model;

import java.time.LocalDateTime;

/**
 * Department entity mapped from the {@code department} table.
 */
public class Department {

  private long id;
  private String code;
  private String name;
  private String address;
  private String status;
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

  public Department() {
  }

  public Department(long id, String code, String name, String address, String status,
          String remark, Long createdBy, LocalDateTime createdDate) {
    this.id = id;
    this.code = code;
    this.name = name;
    this.address = address;
    this.status = status;
    this.remark = remark;
    this.createdBy = createdBy;
    this.createdDate = createdDate;
  }

  /** New unsaved department for insert (id = 0 until persisted). */
  public static Department create(String code, String name, String address, String status,
          String remark, Long createdBy) {
    Department department = new Department();
    department.code = code;
    department.name = name;
    department.address = address;
    department.status = status == null || status.isBlank() ? "Active" : status;
    department.remark = remark;
    department.createdBy = createdBy;
    return department;
  }

  /**
   * Row shape expected by {@code HrmsTable}:
   * ID, Code, Name, Address, Status, Remark, Created By, Created Date.
   */
  public Object[] toTableRow() {
    return new Object[]{
      id,
      code == null ? "" : code,
      name == null ? "" : name,
      address == null ? "" : address,
      status == null ? "" : status,
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

  public String getAddress() {
    return address;
  }

  public void setAddress(String address) {
    this.address = address;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
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
