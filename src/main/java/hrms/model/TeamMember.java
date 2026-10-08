package hrms.model;

import java.time.LocalDateTime;

/**
 * TeamMember entity mapped from the {@code team_member} table: one staff
 * record's membership in one team, optionally nested under another member of
 * the same team ({@code parent_id} self reference).
 *
 * <p>The table has no status column — membership rows are soft-deleted, and
 * duplicates (same staff in the same team) are enforced in the repository.</p>
 */
public class TeamMember {

  private long id;
  private Long staffId;
  private Long teamId;
  private Long parentId;
  private String remark;
  private Long createdBy;
  private LocalDateTime createdDate;
  private Long modifiedBy;
  private LocalDateTime modifiedDate;
  private boolean deleted;
  private Long deletedBy;
  private LocalDateTime deletedDate;
  /** Staff name ({@code staff.name}), resolved by the repository via join. */
  private String staffName;
  /** Staff code ({@code staff.code}), resolved by the repository via join. */
  private String staffCode;
  /** Display name of the team, resolved by the repository via join. */
  private String teamName;
  /** Staff name of the parent member, resolved by the repository via self join. */
  private String parentName;
  /** Display name of the creator, resolved by the repository via user/staff join. */
  private String creatorName;

  public TeamMember() {
  }

  public TeamMember(long id, Long staffId, Long teamId, Long parentId, String remark,
          Long createdBy, LocalDateTime createdDate) {
    this.id = id;
    this.staffId = staffId;
    this.teamId = teamId;
    this.parentId = parentId;
    this.remark = remark;
    this.createdBy = createdBy;
    this.createdDate = createdDate;
  }

  /** New unsaved membership for insert (id = 0 until persisted). */
  public static TeamMember create(Long staffId, Long teamId, Long parentId, String remark,
          Long createdBy) {
    TeamMember member = new TeamMember();
    member.staffId = staffId;
    member.teamId = teamId;
    member.parentId = parentId;
    member.remark = remark;
    member.createdBy = createdBy;
    return member;
  }

  /**
   * Row shape expected by {@code HrmsTable}:
   * ID, Staff, Team, Parent, Remark, Created By, Created Date.
   */
  public Object[] toTableRow() {
    String staff = staffCode != null && !staffCode.isBlank()
            ? staffCode + " — " + (staffName == null ? "" : staffName)
            : staffName != null ? staffName
            : staffId == null ? "—" : String.valueOf(staffId);
    return new Object[]{
      id,
      staff,
      teamName != null ? teamName
              : teamId == null ? "—" : String.valueOf(teamId),
      parentId == null ? ""
              : parentName != null ? parentName : String.valueOf(parentId),
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

  public Long getStaffId() {
    return staffId;
  }

  public void setStaffId(Long staffId) {
    this.staffId = staffId;
  }

  public Long getTeamId() {
    return teamId;
  }

  public void setTeamId(Long teamId) {
    this.teamId = teamId;
  }

  public Long getParentId() {
    return parentId;
  }

  public void setParentId(Long parentId) {
    this.parentId = parentId;
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

  public String getStaffName() {
    return staffName;
  }

  public void setStaffName(String staffName) {
    this.staffName = staffName;
  }

  public String getStaffCode() {
    return staffCode;
  }

  public void setStaffCode(String staffCode) {
    this.staffCode = staffCode;
  }

  public String getTeamName() {
    return teamName;
  }

  public void setTeamName(String teamName) {
    this.teamName = teamName;
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
}
