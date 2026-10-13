package hrms.model;

import java.time.LocalTime;

/**
 * Shift entity mapped from the {@code shift} table.
 */
public class Shift {

  private long id;
  private long departmentId;
  private Long teamId;
  private String code;
  private LocalTime startTime;
  private LocalTime endTime;
  private boolean overnight;
  private String remark;
  private Long createdBy;
  private Long modifiedBy;

  // Display-only fields filled by the repository JOIN (not persisted columns).
  private String departmentName;
  private String teamName;

  public Shift() {
  }

  /** New unsaved shift for insert (id = 0 until persisted). */
  public static Shift create(long departmentId, Long teamId, String code,
          LocalTime startTime, LocalTime endTime, boolean overnight, String remark,
          Long createdBy) {
    Shift shift = new Shift();
    shift.departmentId = departmentId;
    shift.teamId = teamId;
    shift.code = code;
    shift.startTime = startTime;
    shift.endTime = endTime;
    shift.overnight = overnight;
    shift.remark = remark;
    shift.createdBy = createdBy;
    return shift;
  }

  /**
   * Row shape expected by {@code HrmsTable}:
   * ID, Code, Department, Team, Start Time, End Time, Overnight, Remark.
   */
  public Object[] toTableRow() {
    return new Object[]{
      id,
      code == null ? "" : code,
      departmentName == null ? "" : departmentName,
      teamName == null ? "" : teamName,
      startTime == null ? "" : startTime.toString(),
      endTime == null ? "" : endTime.toString(),
      overnight ? "Yes" : "No",
      remark == null ? "" : remark
    };
  }

  public long getId() {
    return id;
  }

  public void setId(long id) {
    this.id = id;
  }

  public long getDepartmentId() {
    return departmentId;
  }

  public void setDepartmentId(long departmentId) {
    this.departmentId = departmentId;
  }

  public Long getTeamId() {
    return teamId;
  }

  public void setTeamId(Long teamId) {
    this.teamId = teamId;
  }

  public String getCode() {
    return code;
  }

  public void setCode(String code) {
    this.code = code;
  }

  public LocalTime getStartTime() {
    return startTime;
  }

  public void setStartTime(LocalTime startTime) {
    this.startTime = startTime;
  }

  public LocalTime getEndTime() {
    return endTime;
  }

  public void setEndTime(LocalTime endTime) {
    this.endTime = endTime;
  }

  public boolean isOvernight() {
    return overnight;
  }

  public void setOvernight(boolean overnight) {
    this.overnight = overnight;
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

  public Long getModifiedBy() {
    return modifiedBy;
  }

  public void setModifiedBy(Long modifiedBy) {
    this.modifiedBy = modifiedBy;
  }

  public String getDepartmentName() {
    return departmentName;
  }

  public void setDepartmentName(String departmentName) {
    this.departmentName = departmentName;
  }

  public String getTeamName() {
    return teamName;
  }

  public void setTeamName(String teamName) {
    this.teamName = teamName;
  }
}
