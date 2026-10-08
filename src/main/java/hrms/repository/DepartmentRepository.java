package hrms.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import hrms.exception.BusinessException;
import hrms.exception.DataAccessException;
import hrms.model.Department;

/**
 * JDBC access for the department table (soft-deleted rows excluded).
 */
public final class DepartmentRepository {

  private DepartmentRepository() {
  }

  public static List<Object[]> findAllAsTableRows() {
    return toTableRows(listAll());
  }

  public static List<Object[]> searchAsTableRows(String keyword) {
    return toTableRows(search(keyword));
  }

  public static List<Department> listAll() {
    return Sql.query(SELECT_SQL + " ORDER BY id DESC", DepartmentRepository::mapRow);
  }

  public static List<Department> search(String keyword) {
    String term = keyword == null ? "" : keyword.trim();
    if (term.isEmpty()) {
      return listAll();
    }
    String like = "%" + term.toLowerCase(Locale.ROOT) + "%";
    return Sql.query(
            SELECT_SQL + " AND (LOWER(code) LIKE ? OR LOWER(name) LIKE ?) ORDER BY id DESC",
            DepartmentRepository::mapRow,
            like, like);
  }

  public static Optional<Department> findById(long id) {
    return Sql.queryOne(SELECT_SQL + " AND id = ?", DepartmentRepository::mapRow, id);
  }

  public static boolean existsCode(String code) {
    return existsCode(code, null);
  }

  /** True when another non-deleted row already uses {@code code}. */
  public static boolean existsCode(String code, Long excludeId) {
    String trimmed = code == null ? "" : code.trim();
    if (excludeId == null) {
      return Sql.queryOne(
              "SELECT 1 FROM department WHERE code = ? AND is_deleted = FALSE",
              rs -> 1L,
              trimmed).isPresent();
    }
    return Sql.queryOne(
            "SELECT 1 FROM department WHERE code = ? AND id <> ? AND is_deleted = FALSE",
            rs -> 1L,
            trimmed, excludeId).isPresent();
  }

  /**
   * Inserts a department and returns the generated id.
   *
   * @throws BusinessException when the code is already used
   */
  public static long insert(Department department) {
    if (department == null) {
      throw new IllegalArgumentException("department is required");
    }
    String code = requireCode(department);
    if (existsCode(code)) {
      throw duplicateCode(code);
    }
    try {
      return Sql.insert("""
              INSERT INTO department
                (code, name, address, status, remark, created_by, created_date, is_deleted)
              VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, FALSE)
              """,
              code,
              blankToNull(department.getName()),
              blankToNull(department.getAddress()),
              normalizeStatus(department.getStatus()),
              blankToNull(department.getRemark()),
              department.getCreatedBy());
    } catch (DataAccessException e) {
      if (isDuplicateKey(e)) {
        throw duplicateCode(code);
      }
      throw e;
    }
  }

  /** Updates editable fields; returns true when a row was changed. */
  public static boolean update(Department department) {
    if (department == null || department.getId() <= 0) {
      throw new IllegalArgumentException("department id is required");
    }
    String code = requireCode(department);
    if (existsCode(code, department.getId())) {
      throw duplicateCode(code);
    }
    int updated = Sql.update("""
            UPDATE department
               SET code = ?,
                   name = ?,
                   address = ?,
                   status = ?,
                   remark = ?,
                   modified_by = ?,
                   modified_date = CURRENT_TIMESTAMP
             WHERE id = ? AND is_deleted = FALSE
            """,
            code,
            blankToNull(department.getName()),
            blankToNull(department.getAddress()),
            normalizeStatus(department.getStatus()),
            blankToNull(department.getRemark()),
            department.getModifiedBy(),
            department.getId());
    return updated > 0;
  }

  /** Soft-deletes one row; returns true when a row was marked deleted. */
  public static boolean softDelete(long id, Long deletedBy) {
    if (id <= 0) {
      throw new IllegalArgumentException("id is required");
    }
    int updated = Sql.update("""
            UPDATE department
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
          SELECT id, code, name, address, status, remark, created_by, created_date,
                 (SELECT COALESCE(st.name, u.username)
                    FROM `user` u
                    LEFT JOIN staff st ON st.id = u.staff_id
                   WHERE u.id = department.created_by) AS creator_name
            FROM department
           WHERE is_deleted = FALSE
          """;

  private static Department mapRow(ResultSet rs) throws SQLException {
    long id = rs.getLong("id");
    long createdBy = rs.getLong("created_by");
    boolean createdByNull = rs.wasNull();
    Department department = new Department(
            id,
            rs.getString("code"),
            rs.getString("name"),
            rs.getString("address"),
            rs.getString("status"),
            rs.getString("remark"),
            createdByNull ? null : createdBy,
            rs.getTimestamp("created_date") == null
                    ? null : rs.getTimestamp("created_date").toLocalDateTime());
    department.setCreatorName(rs.getString("creator_name"));
    return department;
  }

  private static List<Object[]> toTableRows(List<Department> departments) {
    List<Object[]> tableRows = new ArrayList<>(departments.size());
    for (Department department : departments) {
      tableRows.add(department.toTableRow());
    }
    return tableRows;
  }

  private static String requireCode(Department department) {
    String code = department.getCode() == null ? "" : department.getCode().trim();
    if (code.isEmpty()) {
      throw new BusinessException("Department code is required.");
    }
    return code;
  }

  private static String normalizeStatus(String status) {
    return status == null || status.isBlank() ? "Active" : status.trim();
  }

  private static BusinessException duplicateCode(String code) {
    return new BusinessException("Department code '" + code + "' already exists.");
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
