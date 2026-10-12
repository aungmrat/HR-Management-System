package hrms.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import hrms.exception.BusinessException;
import hrms.exception.DataAccessException;
import hrms.model.Shift;

/**
 * JDBC access for the shift table (soft-deleted rows excluded).
 * Follows the same pattern as {@link OfficeRepository}.
 */
public final class ShiftRepository {

  private ShiftRepository() {
  }

  public static List<Object[]> findAllAsTableRows() {
    return toTableRows(listAll());
  }

  public static List<Object[]> searchAsTableRows(String keyword) {
    return toTableRows(search(keyword));
  }

  public static List<Shift> listAll() {
    return Sql.query(SELECT_SQL + " ORDER BY s.id DESC", ShiftRepository::mapRow);
  }

  public static List<Shift> search(String keyword) {
    String term = keyword == null ? "" : keyword.trim();
    if (term.isEmpty()) {
      return listAll();
    }
    String like = "%" + term.toLowerCase(Locale.ROOT) + "%";
    return Sql.query(
            SELECT_SQL + " AND (LOWER(s.code) LIKE ? OR LOWER(d.name) LIKE ? "
                    + "OR LOWER(t.name) LIKE ?) ORDER BY s.id DESC",
            ShiftRepository::mapRow,
            like, like, like);
  }

  public static Optional<Shift> findById(long id) {
    return Sql.queryOne(SELECT_SQL + " AND s.id = ?", ShiftRepository::mapRow, id);
  }

  /** True when another non-deleted row already uses {@code code}. */
  public static boolean existsCode(String code, Long excludeId) {
    String trimmed = code == null ? "" : code.trim();
    if (excludeId == null) {
      return Sql.queryOne(
              "SELECT 1 FROM shift WHERE code = ? AND is_deleted = FALSE",
              rs -> 1L,
              trimmed).isPresent();
    }
    return Sql.queryOne(
            "SELECT 1 FROM shift WHERE code = ? AND id <> ? AND is_deleted = FALSE",
            rs -> 1L,
            trimmed, excludeId).isPresent();
  }

  /**
   * Inserts a shift and returns the generated id.
   *
   * @throws BusinessException when the code is already used
   */
  public static long insert(Shift shift) {
    if (shift == null) {
      throw new IllegalArgumentException("shift is required");
    }
    String code = requireCode(shift);
    if (existsCode(code, null)) {
      throw duplicateCode(code);
    }
    if (shift.getStartTime() == null || shift.getEndTime() == null) {
      throw new BusinessException("Start time and end time are required.");
    }
    try {
      return Sql.insert("""
              INSERT INTO shift
                (department_id, team_id, code, start_time, end_time, is_overnight,
                 remark, created_by, created_date, is_deleted)
              VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, FALSE)
              """,
              shift.getDepartmentId(),
              shift.getTeamId(),
              code,
              shift.getStartTime(),
              shift.getEndTime(),
              shift.isOvernight(),
              blankToNull(shift.getRemark()),
              shift.getCreatedBy());
    } catch (DataAccessException e) {
      if (isDuplicateKey(e)) {
        throw duplicateCode(code);
      }
      throw e;
    }
  }

  /** Updates editable fields; returns true when a row was changed. */
  public static boolean update(Shift shift) {
    if (shift == null || shift.getId() <= 0) {
      throw new IllegalArgumentException("shift id is required");
    }
    String code = requireCode(shift);
    if (existsCode(code, shift.getId())) {
      throw duplicateCode(code);
    }
    if (shift.getStartTime() == null || shift.getEndTime() == null) {
      throw new BusinessException("Start time and end time are required.");
    }
    int updated = Sql.update("""
            UPDATE shift
               SET department_id = ?,
                   team_id = ?,
                   code = ?,
                   start_time = ?,
                   end_time = ?,
                   is_overnight = ?,
                   remark = ?,
                   modified_by = ?,
                   modified_date = CURRENT_TIMESTAMP
             WHERE id = ? AND is_deleted = FALSE
            """,
            shift.getDepartmentId(),
            shift.getTeamId(),
            code,
            shift.getStartTime(),
            shift.getEndTime(),
            shift.isOvernight(),
            blankToNull(shift.getRemark()),
            shift.getModifiedBy(),
            shift.getId());
    return updated > 0;
  }

  /** Soft-deletes one row; returns true when a row was marked deleted. */
  public static boolean softDelete(long id, Long deletedBy) {
    if (id <= 0) {
      throw new IllegalArgumentException("id is required");
    }
    int updated = Sql.update("""
            UPDATE shift
               SET is_deleted = TRUE,
                   deleted_by = ?,
                   deleted_date = CURRENT_TIMESTAMP
             WHERE id = ? AND is_deleted = FALSE
            """,
            deletedBy,
            id);
    return updated > 0;
  }

  // LEFT JOIN so the table can show readable department/team names,
  // and a shift without a team still comes back as one row.
  private static final String SELECT_SQL = """
          SELECT s.id, s.department_id, s.team_id, s.code, s.start_time, s.end_time,
                 s.is_overnight, s.remark, s.created_by,
                 d.name AS department_name,
                 t.name AS team_name
            FROM shift s
            LEFT JOIN department d ON d.id = s.department_id AND d.is_deleted = FALSE
            LEFT JOIN team t ON t.id = s.team_id AND t.is_deleted = FALSE
           WHERE s.is_deleted = FALSE
          """;

  private static Shift mapRow(ResultSet rs) throws SQLException {
    Shift shift = new Shift();
    shift.setId(rs.getLong("id"));
    shift.setDepartmentId(rs.getLong("department_id"));
    if (rs.wasNull()) {
      shift.setDepartmentId(0);
    }
    shift.setTeamId(rs.getLong("team_id"));
    if (rs.wasNull()) {
      shift.setTeamId(null);
    }
    shift.setCode(rs.getString("code"));
    shift.setStartTime(rs.getObject("start_time", LocalTime.class));
    shift.setEndTime(rs.getObject("end_time", LocalTime.class));
    shift.setOvernight(rs.getBoolean("is_overnight"));
    shift.setRemark(rs.getString("remark"));
    long createdBy = rs.getLong("created_by");
    shift.setCreatedBy(rs.wasNull() ? null : createdBy);
    shift.setDepartmentName(rs.getString("department_name"));
    shift.setTeamName(rs.getString("team_name"));
    return shift;
  }

  private static List<Object[]> toTableRows(List<Shift> shifts) {
    List<Object[]> tableRows = new ArrayList<>(shifts.size());
    for (Shift shift : shifts) {
      tableRows.add(shift.toTableRow());
    }
    return tableRows;
  }

  private static String requireCode(Shift shift) {
    String code = shift.getCode() == null ? "" : shift.getCode().trim();
    if (code.isEmpty()) {
      throw new BusinessException("Shift code is required.");
    }
    return code;
  }

  private static BusinessException duplicateCode(String code) {
    return new BusinessException("Shift code '" + code + "' already exists.");
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
