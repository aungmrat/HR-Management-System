package hrms.repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import hrms.exception.BusinessException;
import hrms.exception.DataAccessException;
import hrms.model.Staff;

/**
 * JDBC access for the {@code staff} table (soft-deleted rows excluded).
 *
 * <p>A staff record is referenced by {@code user.staff_id} and by 12 history
 * tables (attendance, leave, payroll, ...). History rows keep pointing at the
 * soft-deleted staff row, so only a linked user account blocks deletion;
 * current-state rows (team memberships, stored documents) are soft-deleted
 * together with the staff record.</p>
 */
public final class StaffRepository {

  private static final BigDecimal MAX_SALARY = new BigDecimal("9999999999.99");

  private StaffRepository() {
  }

  public static List<Object[]> findAllAsTableRows() {
    return searchAsTableRows(null, null, null, null, null);
  }

  /**
   * Table rows for the Staff list:
   * ID, Code, Name, Department, Position, Team, Office, Phone, Status,
   * Created Date.
   *
   * @param keyword      free-text match on code, name, email, phone or NID
   * @param departmentId exact department id, null for no filter
   * @param status       exact status, null/blank for no filter
   */
  public static List<Object[]> searchAsTableRows(String keyword, Long departmentId, String status) {
    return searchAsTableRows(keyword, departmentId, null, null, status);
  }

  public static List<Object[]> searchAsTableRows(String keyword, Long departmentId,
          Long officeId, Long positionId, String status) {
    return toTableRows(search(keyword, departmentId, officeId, positionId, status));
  }

  public static List<Staff> listAll() {
    return search(null, null, null, null, null);
  }

  public static List<Staff> search(String keyword, Long departmentId, String status) {
    return search(keyword, departmentId, null, null, status);
  }

  /**
   * @param keyword      free-text match on code, name, email, phone or NID
   * @param departmentId exact department id, null for no filter
   * @param officeId     exact office id, null for no filter
   * @param positionId   exact position id, null for no filter
   * @param status       exact status, null/blank for no filter
   */
  public static List<Staff> search(String keyword, Long departmentId, Long officeId,
          Long positionId, String status) {
    String term = keyword == null ? "" : keyword.trim();
    StringBuilder sql = new StringBuilder(SELECT_SQL);
    List<Object> params = new ArrayList<>();
    if (!term.isEmpty()) {
      String like = "%" + term.toLowerCase(Locale.ROOT) + "%";
      sql.append(" AND (LOWER(s.code) LIKE ? OR LOWER(s.name) LIKE ?"
              + " OR LOWER(s.email) LIKE ? OR LOWER(s.contact_no) LIKE ?"
              + " OR LOWER(s.nid) LIKE ?)");
      params.add(like);
      params.add(like);
      params.add(like);
      params.add(like);
      params.add(like);
    }
    if (departmentId != null) {
      sql.append(" AND s.department_id = ?");
      params.add(departmentId);
    }
    if (officeId != null) {
      sql.append(" AND s.office_id = ?");
      params.add(officeId);
    }
    if (positionId != null) {
      sql.append(" AND s.position_id = ?");
      params.add(positionId);
    }
    if (status != null && !status.isBlank()) {
      sql.append(" AND s.status = ?");
      params.add(status.trim());
    }
    sql.append(" ORDER BY s.id DESC");
    return Sql.query(sql.toString(), StaffRepository::mapRow, params.toArray());
  }

  public static Optional<Staff> findById(long id) {
    return Sql.queryOne(SELECT_SQL + " AND s.id = ?", StaffRepository::mapRow, id);
  }

  public static boolean existsCode(String code, Long excludeId) {
    return existsBy("code", code, excludeId);
  }

  public static boolean existsEmail(String email, Long excludeId) {
    return existsBy("email", email, excludeId);
  }

  public static boolean existsNid(String nid, Long excludeId) {
    return existsBy("nid", nid, excludeId);
  }

  /**
   * Inserts a staff record and returns the generated id.
   *
   * @throws BusinessException when required fields are invalid or the code,
   *                           email or NID is already used
   */
  public static long insert(Staff staff) {
    if (staff == null) {
      throw new IllegalArgumentException("staff is required");
    }
    String code = requireCode(staff);
    String name = requireName(staff);
    requireDob(staff);
    requireSex(staff);
    requireContactNo(staff);
    requireScope(staff);
    BigDecimal salary = requireSalary(staff);
    String email = normalizeOptional(staff.getEmail(), 100, "Email");
    String nid = normalizeOptional(staff.getNid(), 50, "NID");
    String status = normalizeStatus(staff.getStatus());
    requireOptionalLengths(staff);
    requireUnique(code, email, nid, null);
    try {
      return TransactionManager.execute(tx -> {
        long id = tx.executeInsert("""
                INSERT INTO staff
                  (code, name, dob, sex, blood, contact_no, department_id, email, address,
                   position_id, team_id, office_id, salary, application_date,
                   commencement_date, contract_type, marital_status, next_of_kin,
                   emergency_contact, health_status, status, nid, education_level,
                   other_skills, hobby, has_driving_licence, created_by, created_date,
                   is_deleted)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                        ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, FALSE)
                """,
                code,
                name,
                staff.getDob(),
                staff.getSex(),
                blankToNull(staff.getBlood()),
                staff.getContactNo(),
                staff.getDepartmentId(),
                email,
                blankToNull(staff.getAddress()),
                staff.getPositionId(),
                staff.getTeamId(),
                staff.getOfficeId(),
                salary,
                staff.getApplicationDate(),
                staff.getCommencementDate(),
                blankToNull(staff.getContractType()),
                blankToNull(staff.getMaritalStatus()),
                blankToNull(staff.getNextOfKin()),
                blankToNull(staff.getEmergencyContact()),
                blankToNull(staff.getHealthStatus()),
                status,
                nid,
                blankToNull(staff.getEducationLevel()),
                blankToNull(staff.getOtherSkills()),
                blankToNull(staff.getHobby()),
                staff.isHasDrivingLicence(),
                staff.getCreatedBy());
        StaffSalaryRepository.seedInitial(tx, id, staff, salary);
        return id;
      });
    } catch (DataAccessException e) {
      if (isDuplicateKey(e)) {
        throw duplicateFallback();
      }
      throw e;
    }
  }

  /**
   * Updates editable fields; returns true when a row was changed.
   *
   * @throws BusinessException when required fields are invalid or the code,
   *                           email or NID is already used by another row
   */
  public static boolean update(Staff staff) {
    if (staff == null || staff.getId() <= 0) {
      throw new IllegalArgumentException("staff id is required");
    }
    String code = requireCode(staff);
    String name = requireName(staff);
    requireDob(staff);
    requireSex(staff);
    requireContactNo(staff);
    requireScope(staff);
    BigDecimal salary = requireSalary(staff);
    String email = normalizeOptional(staff.getEmail(), 100, "Email");
    String nid = normalizeOptional(staff.getNid(), 50, "NID");
    String status = normalizeStatus(staff.getStatus());
    requireOptionalLengths(staff);
    requireUnique(code, email, nid, staff.getId());
    Staff previous = findById(staff.getId()).orElse(null);
    try {
      return TransactionManager.execute(tx -> {
        int updated = tx.executeUpdate("""
                UPDATE staff
                   SET code = ?,
                       name = ?,
                       dob = ?,
                       sex = ?,
                       blood = ?,
                       contact_no = ?,
                       department_id = ?,
                       email = ?,
                       address = ?,
                       position_id = ?,
                       team_id = ?,
                       office_id = ?,
                       salary = ?,
                       application_date = ?,
                       commencement_date = ?,
                       contract_type = ?,
                       marital_status = ?,
                       next_of_kin = ?,
                       emergency_contact = ?,
                       health_status = ?,
                       status = ?,
                       nid = ?,
                       education_level = ?,
                       other_skills = ?,
                       hobby = ?,
                       has_driving_licence = ?,
                       modified_by = ?,
                       modified_date = CURRENT_TIMESTAMP
                 WHERE id = ? AND is_deleted = FALSE
                """,
                code,
                name,
                staff.getDob(),
                staff.getSex(),
                blankToNull(staff.getBlood()),
                staff.getContactNo(),
                staff.getDepartmentId(),
                email,
                blankToNull(staff.getAddress()),
                staff.getPositionId(),
                staff.getTeamId(),
                staff.getOfficeId(),
                salary,
                staff.getApplicationDate(),
                staff.getCommencementDate(),
                blankToNull(staff.getContractType()),
                blankToNull(staff.getMaritalStatus()),
                blankToNull(staff.getNextOfKin()),
                blankToNull(staff.getEmergencyContact()),
                blankToNull(staff.getHealthStatus()),
                status,
                nid,
                blankToNull(staff.getEducationLevel()),
                blankToNull(staff.getOtherSkills()),
                blankToNull(staff.getHobby()),
                staff.isHasDrivingLicence(),
                staff.getModifiedBy(),
                staff.getId());
        if (updated > 0 && previous != null) {
          if (previous.getSalary() == null
                  || previous.getSalary().compareTo(salary) != 0) {
            StaffSalaryRepository.syncChange(tx, staff, salary);
          } else if (!Objects.equals(previous.getPositionId(), staff.getPositionId())) {
            StaffSalaryRepository.refreshBand(tx, staff, salary);
          }
        }
        return updated > 0;
      });
    } catch (DataAccessException e) {
      if (isDuplicateKey(e)) {
        throw duplicateFallback();
      }
      throw e;
    }
  }

  /**
   * Soft-deletes one staff record; returns true when a row was marked
   * deleted. Team memberships and stored documents are soft-deleted together
   * with the staff record; history rows (attendance, payroll, ...) stay.
   *
   * @throws BusinessException when a user account is linked to the staff
   */
  public static boolean softDelete(long id, Long deletedBy) {
    if (id <= 0) {
      throw new IllegalArgumentException("id is required");
    }
    if (Sql.queryOne(
            "SELECT 1 FROM `user` WHERE staff_id = ? LIMIT 1",
            rs -> 1L,
            id).isPresent()) {
      throw new BusinessException(
              "Staff has a linked user account and cannot be deleted. "
              + "Set the status to Resigned instead.");
    }
    int updated = Sql.update("""
            UPDATE staff
               SET is_deleted = TRUE,
                   deleted_by = ?,
                   deleted_date = CURRENT_TIMESTAMP
             WHERE id = ? AND is_deleted = FALSE
            """,
            deletedBy,
            id);
    if (updated > 0) {
      Sql.update("""
              UPDATE team_member
                 SET is_deleted = TRUE,
                     deleted_by = ?,
                     deleted_date = CURRENT_TIMESTAMP
               WHERE staff_id = ? AND is_deleted = FALSE
              """,
              deletedBy,
              id);
      Sql.update("""
              UPDATE file
                 SET is_deleted = TRUE,
                     deleted_by = ?,
                     deleted_date = CURRENT_TIMESTAMP
               WHERE source_type = 'STAFF' AND source_id = ? AND is_deleted = FALSE
              """,
              deletedBy,
              id);
    }
    return updated > 0;
  }

  private static final String SELECT_SQL = """
          SELECT s.id, s.code, s.name, s.dob, s.sex, s.blood, s.contact_no,
                 s.department_id, s.email, s.address, s.position_id, s.team_id,
                 s.office_id, s.salary, s.application_date, s.commencement_date,
                 s.contract_type, s.marital_status, s.next_of_kin,
                 s.emergency_contact, s.health_status, s.status, s.nid,
                 s.education_level, s.other_skills, s.hobby, s.has_driving_licence,
                 s.created_by, s.created_date,
                 d.name AS department_name,
                 p.name AS position_name,
                 t.name AS team_name,
                 o.name AS office_name,
                 (SELECT COALESCE(st.name, u.username)
                    FROM `user` u
                    LEFT JOIN staff st ON st.id = u.staff_id
                   WHERE u.id = s.created_by) AS creator_name
            FROM staff s
            LEFT JOIN department d ON d.id = s.department_id
            LEFT JOIN `position` p ON p.id = s.position_id
            LEFT JOIN `team` t ON t.id = s.team_id
            LEFT JOIN office o ON o.id = s.office_id
           WHERE s.is_deleted = FALSE
          """;

  private static Staff mapRow(ResultSet rs) throws SQLException {
    Staff staff = new Staff();
    staff.setId(rs.getLong("id"));
    staff.setCode(rs.getString("code"));
    staff.setName(rs.getString("name"));
    java.sql.Date dob = rs.getDate("dob");
    staff.setDob(dob == null ? null : dob.toLocalDate());
    staff.setSex(rs.getString("sex"));
    staff.setBlood(rs.getString("blood"));
    staff.setContactNo(rs.getString("contact_no"));
    long departmentId = rs.getLong("department_id");
    staff.setDepartmentId(rs.wasNull() ? null : departmentId);
    staff.setEmail(rs.getString("email"));
    staff.setAddress(rs.getString("address"));
    long positionId = rs.getLong("position_id");
    staff.setPositionId(rs.wasNull() ? null : positionId);
    long teamId = rs.getLong("team_id");
    staff.setTeamId(rs.wasNull() ? null : teamId);
    long officeId = rs.getLong("office_id");
    staff.setOfficeId(rs.wasNull() ? null : officeId);
    staff.setSalary(rs.getBigDecimal("salary"));
    java.sql.Date applicationDate = rs.getDate("application_date");
    staff.setApplicationDate(applicationDate == null ? null : applicationDate.toLocalDate());
    java.sql.Date commencementDate = rs.getDate("commencement_date");
    staff.setCommencementDate(commencementDate == null ? null : commencementDate.toLocalDate());
    staff.setContractType(rs.getString("contract_type"));
    staff.setMaritalStatus(rs.getString("marital_status"));
    staff.setNextOfKin(rs.getString("next_of_kin"));
    staff.setEmergencyContact(rs.getString("emergency_contact"));
    staff.setHealthStatus(rs.getString("health_status"));
    staff.setStatus(rs.getString("status"));
    staff.setNid(rs.getString("nid"));
    staff.setEducationLevel(rs.getString("education_level"));
    staff.setOtherSkills(rs.getString("other_skills"));
    staff.setHobby(rs.getString("hobby"));
    staff.setHasDrivingLicence(rs.getBoolean("has_driving_licence"));
    long createdBy = rs.getLong("created_by");
    staff.setCreatedBy(rs.wasNull() ? null : createdBy);
    staff.setCreatedDate(rs.getTimestamp("created_date") == null
            ? null : rs.getTimestamp("created_date").toLocalDateTime());
    staff.setDepartmentName(rs.getString("department_name"));
    staff.setPositionName(rs.getString("position_name"));
    staff.setTeamName(rs.getString("team_name"));
    staff.setOfficeName(rs.getString("office_name"));
    staff.setCreatorName(rs.getString("creator_name"));
    return staff;
  }

  private static List<Object[]> toTableRows(List<Staff> staffs) {
    List<Object[]> tableRows = new ArrayList<>(staffs.size());
    for (Staff staff : staffs) {
      tableRows.add(staff.toTableRow());
    }
    return tableRows;
  }

  private static boolean existsBy(String column, String value, Long excludeId) {
    String trimmed = value == null ? "" : value.trim();
    if (trimmed.isEmpty()) {
      return false;
    }
    if (excludeId == null) {
      return Sql.queryOne(
              "SELECT 1 FROM staff WHERE " + column + " = ? AND is_deleted = FALSE",
              rs -> 1L,
              trimmed).isPresent();
    }
    return Sql.queryOne(
            "SELECT 1 FROM staff WHERE " + column + " = ? AND id <> ? AND is_deleted = FALSE",
            rs -> 1L,
            trimmed, excludeId).isPresent();
  }

  private static void requireUnique(String code, String email, String nid, Long excludeId) {
    if (existsCode(code, excludeId)) {
      throw new BusinessException("Staff code '" + code + "' already exists.");
    }
    if (email != null && existsEmail(email, excludeId)) {
      throw new BusinessException("Staff email '" + email + "' already exists.");
    }
    if (nid != null && existsNid(nid, excludeId)) {
      throw new BusinessException("Staff NID '" + nid + "' already exists.");
    }
  }

  private static String requireCode(Staff staff) {
    String code = staff.getCode() == null ? "" : staff.getCode().trim();
    if (code.isEmpty()) {
      throw new BusinessException("Staff code is required.");
    }
    if (code.length() > 10) {
      throw new BusinessException("Staff code must be 10 characters or fewer.");
    }
    return code;
  }

  private static String requireName(Staff staff) {
    String name = staff.getName() == null ? "" : staff.getName().trim();
    if (name.isEmpty()) {
      throw new BusinessException("Staff name is required.");
    }
    if (name.length() > 100) {
      throw new BusinessException("Staff name must be 100 characters or fewer.");
    }
    return name;
  }

  private static void requireDob(Staff staff) {
    if (staff.getDob() == null) {
      throw new BusinessException("Date of birth is required.");
    }
  }

  private static void requireSex(Staff staff) {
    String sex = staff.getSex() == null ? "" : staff.getSex().trim();
    if (sex.isEmpty()) {
      throw new BusinessException("Sex is required.");
    }
    if (sex.length() > 10) {
      throw new BusinessException("Sex must be 10 characters or fewer.");
    }
  }

  private static void requireContactNo(Staff staff) {
    String contactNo = staff.getContactNo() == null ? "" : staff.getContactNo().trim();
    if (contactNo.isEmpty()) {
      throw new BusinessException("Contact number is required.");
    }
    if (contactNo.length() > 20) {
      throw new BusinessException("Contact number must be 20 characters or fewer.");
    }
  }

  private static void requireScope(Staff staff) {
    if (staff.getDepartmentId() == null || staff.getDepartmentId() <= 0) {
      throw new BusinessException("Department is required.");
    }
    if (staff.getPositionId() == null || staff.getPositionId() <= 0) {
      throw new BusinessException("Position is required.");
    }
    if (staff.getTeamId() == null || staff.getTeamId() <= 0) {
      throw new BusinessException("Team is required.");
    }
    if (staff.getOfficeId() == null || staff.getOfficeId() <= 0) {
      throw new BusinessException("Office is required.");
    }
  }

  private static BigDecimal requireSalary(Staff staff) {
    BigDecimal salary = staff.getSalary();
    if (salary == null) {
      throw new BusinessException("Salary is required.");
    }
    if (salary.signum() < 0) {
      throw new BusinessException("Salary cannot be negative.");
    }
    if (salary.compareTo(MAX_SALARY) > 0) {
      throw new BusinessException("Salary must be 9999999999.99 or fewer.");
    }
    return salary;
  }

  private static void requireOptionalLengths(Staff staff) {
    requireLength(staff.getBlood(), 10, "Blood group");
    requireLength(staff.getContractType(), 20, "Contract type");
    requireLength(staff.getMaritalStatus(), 20, "Marital status");
    requireLength(staff.getEmergencyContact(), 20, "Emergency contact");
    requireLength(staff.getHealthStatus(), 20, "Health status");
    requireLength(staff.getNextOfKin(), 100, "Next of kin");
    requireLength(staff.getEducationLevel(), 200, "Education level");
    requireLength(staff.getAddress(), 500, "Address");
    requireLength(staff.getOtherSkills(), 500, "Other skills");
    requireLength(staff.getHobby(), 500, "Hobby");
  }

  private static void requireLength(String value, int max, String label) {
    if (value != null && value.trim().length() > max) {
      throw new BusinessException(label + " must be " + max + " characters or fewer.");
    }
  }

  private static String normalizeOptional(String value, int max, String label) {
    String trimmed = blankToNull(value);
    if (trimmed != null && trimmed.length() > max) {
      throw new BusinessException(label + " must be " + max + " characters or fewer.");
    }
    return trimmed;
  }

  private static String normalizeStatus(String status) {
    String trimmed = status == null || status.isBlank() ? "Active" : status.trim();
    if (trimmed.length() > 20) {
      throw new BusinessException("Status must be 20 characters or fewer.");
    }
    return trimmed;
  }

  private static BusinessException duplicateFallback() {
    return new BusinessException("Staff code, email or NID already exists.");
  }

  private static String blankToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private static boolean isDuplicateKey(DataAccessException e) {
    Throwable cause = e.getCause();
    while (cause != null) {
      if (cause instanceof java.sql.SQLIntegrityConstraintViolationException) {
        return true;
      }
      String message = cause.getMessage();
      if (message != null && message.toLowerCase(Locale.ROOT).contains("duplicate")) {
        return true;
      }
      cause = cause.getCause();
    }
    String message = e.getMessage();
    return message != null && message.toLowerCase(Locale.ROOT).contains("duplicate");
  }
}
