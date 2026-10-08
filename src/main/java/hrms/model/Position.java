package hrms.model;

import java.time.LocalDateTime;

/**
 * Position entity mapped from the {@code position} table (a job title scoped
 * to one department and one business role - not the system/permission role).
 *
 * <p>Like the table, this entity has no {@code code} and no {@code status}
 * column. Both {@code department_id} and {@code role_id} are NOT NULL.</p>
 */
public class Position {

  private long id;
  private Long departmentId;
  private Long roleId;
  private String name;
  private String remark;
  private Long createdBy;
  private LocalDateTime createdDate;
  private Long modifiedBy;
  private LocalDateTime modifiedDate;
  private boolean deleted;
  private Long deletedBy;
  private LocalDateTime deletedDate;
  /** Display name of the department, resolved by the repository via join. */
  private String departmentName;
  /** Display name of the business role, resolved by the repository via join. */
  private String roleName;
  /** Display name of the creator, resolved by the repository via user/staff join. */
  private String creatorName;

  public Position() {
  }

  public Position(long id, Long departmentId, Long roleId, String name, String remark,
          Long createdBy, LocalDateTime createdDate) {
    this.id = id;
    this.departmentId = departmentId;
    this.roleId = roleId;
    this.name = name;
    this.remark = remark;
    this.createdBy = createdBy;
    this.createdDate = createdDate;
  }

  /** New unsaved position for insert (id = 0 until persisted). */
  public static Position create(Long departmentId, Long roleId, String name, String remark,
          Long createdBy) {
    Position position = new Position();
    position.departmentId = departmentId;
    position.roleId = roleId;
    position.name = name;
    position.remark = remark;
    position.createdBy = createdBy;
    return position;
  }

  /**
   * Row shape expected by {@code HrmsTable}:
   * ID, Name, Department, Role, Remark, Created By, Created Date.
   */
  public Object[] toTableRow() {
    return new Object[]{
      id,
      name == null ? "" : name,
      departmentName != null ? departmentName
              : departmentId == null ? "—" : String.valueOf(departmentId),
      roleName != null ? roleName
              : roleId == null ? "—" : String.valueOf(roleId),
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

  public Long getDepartmentId() {
    return departmentId;
  }

  public void setDepartmentId(Long departmentId) {
    this.departmentId = departmentId;
  }

  public Long getRoleId() {
    return roleId;
  }

  public void setRoleId(Long roleId) {
    this.roleId = roleId;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
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

  public String getDepartmentName() {
    return departmentName;
  }

  public void setDepartmentName(String departmentName) {
    this.departmentName = departmentName;
  }

  public String getRoleName() {
    return roleName;
  }

  public void setRoleName(String roleName) {
    this.roleName = roleName;
  }

  public String getCreatorName() {
    return creatorName;
  }

  public void setCreatorName(String creatorName) {
    this.creatorName = creatorName;
  }
}
