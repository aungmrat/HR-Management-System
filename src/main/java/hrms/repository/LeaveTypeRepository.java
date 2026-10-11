package hrms.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import hrms.exception.BusinessException;
import hrms.model.LeaveType;

/**
 * JDBC access for the leave_type table (soft-deleted rows excluded).
 */
public final class LeaveTypeRepository {

  private LeaveTypeRepository() {
  }

  public static List<Object[]> findAllAsTableRows() {
    return toTableRows(listAll());
  }

  public static List<LeaveType> listAll() {
    return Sql.query(SELECT_SQL + " ORDER BY lt.id DESC", LeaveTypeRepository::mapRow);
  }

  public static Optional<LeaveType> findById(long id) {
    return Sql.queryOne(SELECT_SQL + " AND lt.id = ?", LeaveTypeRepository::mapRow, id);
  }

  /** True when another non-deleted row already uses {@code code} (case-insensitive). */
  public static boolean existsCode(String code, Long excludeId) {
    String trimmed = code == null ? "" : code.trim();
    if (excludeId == null) {
      return Sql.queryOne(
              "SELECT 1 FROM leave_type WHERE code = ? AND is_deleted = FALSE",
              rs -> 1L,
              trimmed).isPresent();
    }
    return Sql.queryOne(
            "SELECT 1 FROM leave_type WHERE code = ? AND id <> ? AND is_deleted = FALSE",
            rs -> 1L,
            trimmed, excludeId).isPresent();
  }

  /**
   * Inserts a leave type and returns the generated id.
   *
   * @throws BusinessException when the code is already used
   */
  public static long insert(LeaveType leaveType) {
    if (leaveType == null) {
      throw new IllegalArgumentException("leave type is required");
    }
    String code = requireCode(leaveType);
    if (existsCode(code, null)) {
      throw duplicateCode(code);
    }
    return Sql.insert("""
            INSERT INTO leave_type
              (code, name, description, standard_days_per_year, max_days_per_year,
               half_day_allowed, is_carry_forward, carry_max_days,
               requires_pre_approval, gender_restriction, is_active,
               created_by, created_date, is_deleted)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, FALSE)
            """,
            code,
            leaveType.getName(),
            leaveType.getDescription(),
            leaveType.getStandardDaysPerYear(),
            leaveType.getMaxDaysPerYear(),
            leaveType.isHalfDayAllowed(),
            leaveType.isCarryForward(),
            leaveType.getCarryMaxDays(),
            leaveType.isRequiresPreApproval(),
            leaveType.getGenderRestriction(),
            leaveType.isActive(),
            leaveType.getCreatedBy());
  }

  /** Updates editable fields; returns true when a row was changed. */
  public static boolean update(LeaveType leaveType) {
    if (leaveType == null || leaveType.getId() <= 0) {
      throw new IllegalArgumentException("leave type id is required");
    }
    String code = requireCode(leaveType);
    if (existsCode(code, leaveType.getId())) {
      throw duplicateCode(code);
    }
    int updated = Sql.update("""
            UPDATE leave_type
               SET code = ?,
                   name = ?,
                   description = ?,
                   standard_days_per_year = ?,
                   max_days_per_year = ?,
                   half_day_allowed = ?,
                   is_carry_forward = ?,
                   carry_max_days = ?,
                   requires_pre_approval = ?,
                   gender_restriction = ?,
                   is_active = ?,
                   modified_by = ?,
                   modified_date = CURRENT_TIMESTAMP
             WHERE id = ? AND is_deleted = FALSE
            """,
            code,
            leaveType.getName(),
            leaveType.getDescription(),
            leaveType.getStandardDaysPerYear(),
            leaveType.getMaxDaysPerYear(),
            leaveType.isHalfDayAllowed(),
            leaveType.isCarryForward(),
            leaveType.getCarryMaxDays(),
            leaveType.isRequiresPreApproval(),
            leaveType.getGenderRestriction(),
            leaveType.isActive(),
            leaveType.getModifiedBy(),
            leaveType.getId());
    return updated > 0;
  }

  /** Soft-deletes one row; returns true when a row was marked deleted. */
  public static boolean softDelete(long id, Long deletedBy) {
    if (id <= 0) {
      throw new IllegalArgumentException("id is required");
    }
    int updated = Sql.update("""
            UPDATE leave_type
               SET is_deleted = TRUE,
                   deleted_by = ?,
                   deleted_date = CURRENT_TIMESTAMP
             WHERE id = ? AND is_deleted = FALSE
            """,
            deletedBy,
            id);
    return updated > 0;
  }

  private static final String SELECT_SQL = """
          SELECT lt.id, lt.code, lt.name, lt.description,
                 lt.standard_days_per_year, lt.max_days_per_year,
                 lt.half_day_allowed, lt.is_carry_forward, lt.carry_max_days,
                 lt.requires_pre_approval, lt.gender_restriction, lt.is_active,
                 lt.created_by, lt.created_date,
                 (SELECT COALESCE(st.name, u.username)
                    FROM `user` u
                    LEFT JOIN staff st ON st.id = u.staff_id
                   WHERE u.id = lt.created_by) AS creator_name
            FROM leave_type lt
           WHERE lt.is_deleted = FALSE
          """;

  private static LeaveType mapRow(ResultSet rs) throws SQLException {
    LeaveType leaveType = new LeaveType(
            rs.getLong("id"),
            rs.getString("code"),
            rs.getString("name"),
            rs.getString("description"),
            rs.getInt("standard_days_per_year"),
            rs.getInt("max_days_per_year"),
            rs.getBoolean("half_day_allowed"),
            rs.getBoolean("is_carry_forward"),
            nullableInt(rs, "carry_max_days"),
            rs.getBoolean("requires_pre_approval"),
            rs.getBoolean("is_active"),
            nullableLong(rs, "created_by"),
            rs.getTimestamp("created_date") == null
                    ? null : rs.getTimestamp("created_date").toLocalDateTime());
    leaveType.setCreatorName(rs.getString("creator_name"));
    leaveType.setGenderRestriction(rs.getString("gender_restriction"));
    return leaveType;
  }

  private static Long nullableLong(ResultSet rs, String column) throws SQLException {
    long value = rs.getLong(column);
    return rs.wasNull() ? null : value;
  }

  private static Integer nullableInt(ResultSet rs, String column) throws SQLException {
    int value = rs.getInt(column);
    return rs.wasNull() ? null : value;
  }

  private static String requireCode(LeaveType leaveType) {
    String code = leaveType.getCode() == null ? "" : leaveType.getCode().trim();
    if (code.isEmpty()) {
      throw new BusinessException("Leave type code is required.");
    }
    return code;
  }

  private static BusinessException duplicateCode(String code) {
    return new BusinessException("Leave type code '" + code + "' already exists.");
  }

  private static List<Object[]> toTableRows(List<LeaveType> leaveTypes) {
    List<Object[]> tableRows = new ArrayList<>(leaveTypes.size());
    for (LeaveType leaveType : leaveTypes) {
      tableRows.add(leaveType.toTableRow());
    }
    return tableRows;
  }
}
