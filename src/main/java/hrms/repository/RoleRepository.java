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
import hrms.model.Role;

/**
 * JDBC access for the {@code role} table (soft-deleted rows excluded).
 *
 * <p>Business roles are referenced by {@code position.role_id} and
 * {@code salary_rate.role_id}, so soft-delete is blocked while in use.</p>
 */
public final class RoleRepository {

  private RoleRepository() {
  }

  public static List<Object[]> findAllAsTableRows() {
    return searchAsTableRows(null, null);
  }

  /**
   * Table rows for the Role list:
   * ID, Name, Department, Remark, Created By, Created Date.
   *
   * @param keyword      free-text match on role name or remark
   * @param departmentId exact department id, null for no filter
   */
  public static List<Object[]> searchAsTableRows(String keyword, Long departmentId) {
    return toTableRows(search(keyword, departmentId));
  }

  public static List<Role> listAll() {
    return search(null, null);
  }

  public static List<Role> search(String keyword, Long departmentId) {
    String term = keyword == null ? "" : keyword.trim();
    StringBuilder sql = new StringBuilder(SELECT_SQL);
    List<Object> params = new ArrayList<>();
    if (!term.isEmpty()) {
      String like = "%" + term.toLowerCase(Locale.ROOT) + "%";
      sql.append(" AND (LOWER(r.name) LIKE ? OR LOWER(r.remark) LIKE ?)");
      params.add(like);
      params.add(like);
    }
    if (departmentId != null) {
      sql.append(" AND r.department_id = ?");
      params.add(departmentId);
    }
    sql.append(" ORDER BY r.id DESC");
    return Sql.query(sql.toString(), RoleRepository::mapRow, params.toArray());
  }

  public static Optional<Role> findById(long id) {
    return Sql.queryOne(SELECT_SQL + " AND r.id = ?", RoleRepository::mapRow, id);
  }

  /** True when another non-deleted row already uses {@code name}. */
  public static boolean existsName(String name, Long excludeId) {
    String trimmed = name == null ? "" : name.trim();
    if (excludeId == null) {
      return Sql.queryOne(
              "SELECT 1 FROM `role` WHERE name = ? AND is_deleted = FALSE",
              rs -> 1L,
              trimmed).isPresent();
    }
    return Sql.queryOne(
            "SELECT 1 FROM `role` WHERE name = ? AND id <> ? AND is_deleted = FALSE",
            rs -> 1L,
            trimmed, excludeId).isPresent();
  }

  /**
   * Inserts a role and returns the generated id.
   *
   * @throws BusinessException when required fields are invalid or the name is
   *                           already used
   */
  public static long insert(Role role) {
    if (role == null) {
      throw new IllegalArgumentException("role is required");
    }
    String name = requireName(role);
    requireScope(role);
    requireRemark(role);
    if (existsName(name, null)) {
      throw duplicateName(name);
    }
    try {
      return Sql.insert("""
              INSERT INTO `role`
                (department_id, name, remark, created_by, created_date, is_deleted)
              VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, FALSE)
              """,
              role.getDepartmentId(),
              name,
              blankToNull(role.getRemark()),
              role.getCreatedBy());
    } catch (DataAccessException e) {
      if (isDuplicateKey(e)) {
        throw duplicateName(name);
      }
      throw e;
    }
  }

  /**
   * Updates editable fields; returns true when a row was changed.
   *
   * @throws BusinessException when required fields are invalid or the name is
   *                           already used by another row
   */
  public static boolean update(Role role) {
    if (role == null || role.getId() <= 0) {
      throw new IllegalArgumentException("role id is required");
    }
    String name = requireName(role);
    requireScope(role);
    requireRemark(role);
    if (existsName(name, role.getId())) {
      throw duplicateName(name);
    }
    int updated = Sql.update("""
            UPDATE `role`
               SET department_id = ?,
                   name = ?,
                   remark = ?,
                   modified_by = ?,
                   modified_date = CURRENT_TIMESTAMP
             WHERE id = ? AND is_deleted = FALSE
            """,
            role.getDepartmentId(),
            name,
            blankToNull(role.getRemark()),
            role.getModifiedBy(),
            role.getId());
    return updated > 0;
  }

  /**
   * Soft-deletes one row; returns true when a row was marked deleted.
   *
   * @throws BusinessException when the role is still referenced by a position
   *                           or salary rate
   */
  public static boolean softDelete(long id, Long deletedBy) {
    if (id <= 0) {
      throw new IllegalArgumentException("id is required");
    }
    if (isInUse(id)) {
      throw new BusinessException(
              "Role is in use by a position or salary rate and cannot be deleted.");
    }
    int updated = Sql.update("""
            UPDATE `role`
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
          SELECT r.id, r.department_id, r.name, r.remark,
                 r.created_by, r.created_date,
                 d.name AS department_name,
                 (SELECT COALESCE(st.name, u.username)
                    FROM `user` u
                    LEFT JOIN staff st ON st.id = u.staff_id
                   WHERE u.id = r.created_by) AS creator_name
            FROM `role` r
            LEFT JOIN department d ON d.id = r.department_id
           WHERE r.is_deleted = FALSE
          """;

  /** True while any position or salary rate still references the role. */
  private static boolean isInUse(long id) {
    return Sql.queryOne(
            "SELECT 1 FROM position WHERE role_id = ? LIMIT 1",
            rs -> 1L,
            id).isPresent()
            || Sql.queryOne(
                    "SELECT 1 FROM salary_rate WHERE role_id = ? LIMIT 1",
                    rs -> 1L,
                    id).isPresent();
  }

  private static Role mapRow(ResultSet rs) throws SQLException {
    long id = rs.getLong("id");
    long createdBy = rs.getLong("created_by");
    boolean createdByNull = rs.wasNull();
    Role role = new Role(
            id,
            rs.getLong("department_id"),
            rs.getString("name"),
            rs.getString("remark"),
            createdByNull ? null : createdBy,
            rs.getTimestamp("created_date") == null
                    ? null : rs.getTimestamp("created_date").toLocalDateTime());
    role.setDepartmentName(rs.getString("department_name"));
    role.setCreatorName(rs.getString("creator_name"));
    return role;
  }

  private static List<Object[]> toTableRows(List<Role> roles) {
    List<Object[]> tableRows = new ArrayList<>(roles.size());
    for (Role role : roles) {
      tableRows.add(role.toTableRow());
    }
    return tableRows;
  }

  private static String requireName(Role role) {
    String name = role.getName() == null ? "" : role.getName().trim();
    if (name.isEmpty()) {
      throw new BusinessException("Role name is required.");
    }
    if (name.length() > 100) {
      throw new BusinessException("Role name must be 100 characters or fewer.");
    }
    return name;
  }

  private static void requireScope(Role role) {
    if (role.getDepartmentId() == null || role.getDepartmentId() <= 0) {
      throw new BusinessException("Department is required.");
    }
  }

  private static void requireRemark(Role role) {
    String remark = role.getRemark();
    if (remark != null && remark.trim().length() > 500) {
      throw new BusinessException("Remark must be 500 characters or fewer.");
    }
  }

  private static BusinessException duplicateName(String name) {
    return new BusinessException("Role '" + name + "' already exists.");
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
