package hrms.model;

import java.time.LocalDateTime;

/**
 * Team entity mapped from the {@code team} table. A team belongs to one
 * department and one office (both NOT NULL) and may nest under a parent team
 * ({@code parent_id} self reference).
 *
 * <p>Like the table, this entity has no {@code code} and no {@code status}
 * column. Duplicate names are enforced in the repository (the table has no
 * unique key).</p>
 */
public class Team {

  private long id;
  private Long departmentId;
  private Long officeId;
  private Long parentId;
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
  /** Display name of the office, resolved by the repository via join. */
  private String officeName;
  /** Display name of the parent team, resolved by the repository via self join. */
  private String parentName;
  /** Display name of the creator, resolved by the repository via user/staff join. */
  private String creatorName;
  /** Active member count (team_member rows), resolved by the repository. */
  private long memberCount;

  public Team() {
  }

  public Team(long id, Long departmentId, Long officeId, Long parentId, String name, String remark,
          Long createdBy, LocalDateTime createdDate) {
    this.id = id;
    this.departmentId = departmentId;
    this.officeId = officeId;
    this.parentId = parentId;
    this.name = name;
    this.remark = remark;
    this.createdBy = createdBy;
    this.createdDate = createdDate;
  }

  /** New unsaved team for insert (id = 0 until persisted). */
  public static Team create(Long departmentId, Long officeId, Long parentId, String name,
          String remark, Long createdBy) {
    Team team = new Team();
    team.departmentId = departmentId;
    team.officeId = officeId;
    team.parentId = parentId;
    team.name = name;
    team.remark = remark;
    team.createdBy = createdBy;
    return team;
  }

  /**
   * Row shape expected by {@code HrmsTable}:
   * ID, Name, Department, Office, Parent Team, Members, Remark, Created By,
   * Created Date.
   */
  public Object[] toTableRow() {
    return new Object[]{
      id,
      name == null ? "" : name,
      departmentName != null ? departmentName
              : departmentId == null ? "—" : String.valueOf(departmentId),
      officeName != null ? officeName
              : officeId == null ? "—" : String.valueOf(officeId),
      parentId == null ? ""
              : parentName != null ? parentName : String.valueOf(parentId),
      memberCount,
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

  public Long getOfficeId() {
    return officeId;
  }

  public void setOfficeId(Long officeId) {
    this.officeId = officeId;
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

  public String getOfficeName() {
    return officeName;
  }

  public void setOfficeName(String officeName) {
    this.officeName = officeName;
  }

  public String getParentName() {
    return parentName;
  }

  public void setParentName(String parentName) {
    this.parentName = parentName;
  }

  public String getCreatorName() {
    return creatorName;
  }

  public void setCreatorName(String creatorName) {
    this.creatorName = creatorName;
  }

  public long getMemberCount() {
    return memberCount;
  }

  public void setMemberCount(long memberCount) {
    this.memberCount = memberCount;
  }
}
