package hrms.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Staff entity mapped from the {@code staff} table (one employee record).
 */
public class Staff {

  private long id;
  private String code;
  private String name;
  private LocalDate dob;
  private String sex;
  private String blood;
  private String contactNo;
  private Long departmentId;
  private String email;
  private String address;
  private Long positionId;
  private Long teamId;
  private Long officeId;
  private BigDecimal salary;
  private LocalDate applicationDate;
  private LocalDate commencementDate;
  private String contractType;
  private String maritalStatus;
  private String nextOfKin;
  private String emergencyContact;
  private String healthStatus;
  private String status;
  private String nid;
  private String educationLevel;
  private String otherSkills;
  private String hobby;
  private boolean hasDrivingLicence;
  private Long createdBy;
  private LocalDateTime createdDate;
  private Long modifiedBy;
  private LocalDateTime modifiedDate;
  private boolean deleted;
  private Long deletedBy;
  private LocalDateTime deletedDate;
  /** Display names resolved by the repository via join. */
  private String departmentName;
  private String positionName;
  private String teamName;
  private String officeName;
  private String creatorName;

  public Staff() {
  }

  /**
   * Row shape expected by {@code HrmsTable}:
   * ID, Code, Name, Department, Position, Team, Office, Phone, Status,
   * Created Date.
   */
  public Object[] toTableRow() {
    return new Object[]{
      id,
      code == null ? "" : code,
      name == null ? "" : name,
      departmentName != null ? departmentName
              : departmentId == null ? "—" : String.valueOf(departmentId),
      positionName != null ? positionName
              : positionId == null ? "—" : String.valueOf(positionId),
      teamName != null ? teamName
              : teamId == null ? "—" : String.valueOf(teamId),
      officeName != null ? officeName
              : officeId == null ? "—" : String.valueOf(officeId),
      contactNo == null ? "" : contactNo,
      status == null ? "" : status,
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

  public LocalDate getDob() {
    return dob;
  }

  public void setDob(LocalDate dob) {
    this.dob = dob;
  }

  public String getSex() {
    return sex;
  }

  public void setSex(String sex) {
    this.sex = sex;
  }

  public String getBlood() {
    return blood;
  }

  public void setBlood(String blood) {
    this.blood = blood;
  }

  public String getContactNo() {
    return contactNo;
  }

  public void setContactNo(String contactNo) {
    this.contactNo = contactNo;
  }

  public Long getDepartmentId() {
    return departmentId;
  }

  public void setDepartmentId(Long departmentId) {
    this.departmentId = departmentId;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getAddress() {
    return address;
  }

  public void setAddress(String address) {
    this.address = address;
  }

  public Long getPositionId() {
    return positionId;
  }

  public void setPositionId(Long positionId) {
    this.positionId = positionId;
  }

  public Long getTeamId() {
    return teamId;
  }

  public void setTeamId(Long teamId) {
    this.teamId = teamId;
  }

  public Long getOfficeId() {
    return officeId;
  }

  public void setOfficeId(Long officeId) {
    this.officeId = officeId;
  }

  public BigDecimal getSalary() {
    return salary;
  }

  public void setSalary(BigDecimal salary) {
    this.salary = salary;
  }

  public LocalDate getApplicationDate() {
    return applicationDate;
  }

  public void setApplicationDate(LocalDate applicationDate) {
    this.applicationDate = applicationDate;
  }

  public LocalDate getCommencementDate() {
    return commencementDate;
  }

  public void setCommencementDate(LocalDate commencementDate) {
    this.commencementDate = commencementDate;
  }

  public String getContractType() {
    return contractType;
  }

  public void setContractType(String contractType) {
    this.contractType = contractType;
  }

  public String getMaritalStatus() {
    return maritalStatus;
  }

  public void setMaritalStatus(String maritalStatus) {
    this.maritalStatus = maritalStatus;
  }

  public String getNextOfKin() {
    return nextOfKin;
  }

  public void setNextOfKin(String nextOfKin) {
    this.nextOfKin = nextOfKin;
  }

  public String getEmergencyContact() {
    return emergencyContact;
  }

  public void setEmergencyContact(String emergencyContact) {
    this.emergencyContact = emergencyContact;
  }

  public String getHealthStatus() {
    return healthStatus;
  }

  public void setHealthStatus(String healthStatus) {
    this.healthStatus = healthStatus;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getNid() {
    return nid;
  }

  public void setNid(String nid) {
    this.nid = nid;
  }

  public String getEducationLevel() {
    return educationLevel;
  }

  public void setEducationLevel(String educationLevel) {
    this.educationLevel = educationLevel;
  }

  public String getOtherSkills() {
    return otherSkills;
  }

  public void setOtherSkills(String otherSkills) {
    this.otherSkills = otherSkills;
  }

  public String getHobby() {
    return hobby;
  }

  public void setHobby(String hobby) {
    this.hobby = hobby;
  }

  public boolean isHasDrivingLicence() {
    return hasDrivingLicence;
  }

  public void setHasDrivingLicence(boolean hasDrivingLicence) {
    this.hasDrivingLicence = hasDrivingLicence;
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

  public String getPositionName() {
    return positionName;
  }

  public void setPositionName(String positionName) {
    this.positionName = positionName;
  }

  public String getTeamName() {
    return teamName;
  }

  public void setTeamName(String teamName) {
    this.teamName = teamName;
  }

  public String getOfficeName() {
    return officeName;
  }

  public void setOfficeName(String officeName) {
    this.officeName = officeName;
  }

  public String getCreatorName() {
    return creatorName;
  }

  public void setCreatorName(String creatorName) {
    this.creatorName = creatorName;
  }
}
