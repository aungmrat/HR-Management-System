package hrms.model;

import java.time.LocalDateTime;

/**
 * Office entity mapped from the {@code office} table.
 */
public class Office {

  private long id;
  private Long departmentId;
  private String code;
  private Long parentId;
  private String name;
  private String contactNo;
  private String address;
  private String status;
  private Long createdBy;
  private LocalDateTime createdDate;
  private Long modifiedBy;
  private LocalDateTime modifiedDate;
  private boolean deleted;
  private Long deletedBy;
  private LocalDateTime deletedDate;

  // Display-only fields filled by the repository JOIN (not persisted columns).
  private String departmentName;
  private String parentOfficeName;

  public Office() {
  }

  /** New unsaved office for insert (id = 0 until persisted). */
  public static Office create(Long departmentId, String code, Long parentId, String name,
          String contactNo, String address, String status, Long createdBy) {
    Office office = new Office();
    office.departmentId = departmentId;
    office.code = code;
    office.parentId = parentId;
    office.name = name;
    office.contactNo = contactNo;
    office.address = address;
    office.status = status == null || status.isBlank() ? "Active" : status;
    office.createdBy = createdBy;
    return office;
  }

  /**
   * Row shape expected by {@code HrmsTable}:
   * ID, Code, Name, Department, Parent Office, Contact No, Address, Status.
   * The hidden ID column keeps the primary key for edit/delete actions while
   * readable display names (from the repository JOIN) are shown.
   */
  public Object[] toTableRow() {
    return new Object[]{
      id,
      code == null ? "" : code,
      name == null ? "" : name,
      departmentName == null ? "" : departmentName,
      parentOfficeName == null ? "" : parentOfficeName,
      contactNo == null ? "" : contactNo,
      address == null ? "" : address,
      status == null ? "" : status
    };
  }

  public long getId() {
    return id;
  }

  public void setId(long id) {
    this.id = id;
  }

  public Long getDepartmentId() {
    return departmentId;
  }

  public void setDepartmentId(Long departmentId) {
    this.departmentId = departmentId;
  }

  public String getCode() {
    return code;
  }

  public void setCode(String code) {
    this.code = code;
  }

  public Long getParentId() {
    return parentId;
  }

  public void setParentId(Long parentId) {
    this.parentId = parentId;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getContactNo() {
    return contactNo;
  }

  public void setContactNo(String contactNo) {
    this.contactNo = contactNo;
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

  public String getDepartmentName() {
    return departmentName;
  }

  public void setDepartmentName(String departmentName) {
    this.departmentName = departmentName;
  }

  public String getParentOfficeName() {
    return parentOfficeName;
  }

  public void setParentOfficeName(String parentOfficeName) {
    this.parentOfficeName = parentOfficeName;
  }
}
