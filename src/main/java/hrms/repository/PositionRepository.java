package hrms.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import hrms.exception.BusinessException;
import hrms.model.Position;

/**
 * JDBC access for the {@code position} table (soft-deleted rows excluded).
 *
 * <p>A position is referenced by {@code staff.position_id}, so soft-delete is
 * blocked while a staff record still points at it. The table has no unique
 * key on {@code name}, so duplicate names are enforced here in code.</p>
 */
public final class PositionRepository {

  private PositionRepository() {
  }

  public static List<Object[]> findAllAsTableRows() {
    return searchAsTableRows(null, null, null);
  }

  /**
   * Table rows for the Position list:
   * ID, Name, Department, Role, Remark, Created By, Created Date.
   *
   * @param keyword      free-text match on position name or remark
   * @param departmentId exact department id, null for no filter
   * @param roleId       exact business role id, null for no filter
   */
  public static List<Object[]> searchAsTableRows(String keyword, Long departmentId, Long roleId) {
    return toTableRows(search(keyword, departmentId, roleId));
  }

  public static List<Position> listAll() {
    return search(null, null, null);
  }

  public static List<Position> search(String keyword, Long departmentId, Long roleId) {
    String term = keyword == null ? "" : keyword.trim();
    StringBuilder sql = new StringBuilder(SELECT_SQL);
    List<Object> params = new ArrayList<>();
    if (!term.isEmpty()) {
      String like = "%" + term.toLowerCase(Locale.ROOT) + "%";
      sql.append(" AND (LOWER(p.name) LIKE ? OR LOWER(p.remark) LIKE ?)");
      params.add(like);
      params.add(like);
    }
    if (departmentId != null) {
      sql.append(" AND p.department_id = ?");
      params.add(departmentId);
    }
    if (roleId != null) {
      sql.append(" AND p.role_id = ?");
      params.add(roleId);
    }
    sql.append(" ORDER BY p.id DESC");
    return Sql.query(sql.toString(), PositionRepository::mapRow, params.toArray());
  }

  public static Optional<Position> findById(long id) {
    return Sql.queryOne(SELECT_SQL + " AND p.id = ?", PositionRepository::mapRow, id);
  }

  /** True when another non-deleted row already uses {@code name}. */
  public static boolean existsName(String name, Long excludeId) {
    String trimmed = name == null ? "" : name.trim();
    if (excludeId == null) {
      return Sql.queryOne(
              "SELECT 1 FROM `position` WHERE name = ? AND is_deleted = FALSE",
              rs -> 1L,
              trimmed).isPresent();
    }
    return Sql.queryOne(
            "SELECT 1 FROM `position` WHERE name = ? AND id <> ? AND is_deleted = FALSE",
            rs -> 1L,
            trimmed, excludeId).isPresent();
  }

  /**
   * Inserts a position and returns the generated id.
   *
   * @throws BusinessException when required fields are invalid or the name is
   *                           already used
   */
  public static long insert(Position position) {
    if (position == null) {
      throw new IllegalArgumentException("position is required");
    }
    requireScope(position);
    String name = requireName(position);
    requireRemark(position);
    if (existsName(name, null)) {
      throw duplicateName(name);
    }
    return Sql.insert("""
            INSERT INTO `position`
              (department_id, role_id, name, remark,
               created_by, created_date, is_deleted)
            VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, FALSE)
            """,
            position.getDepartmentId(),
            position.getRoleId(),
            name,
            blankToNull(position.getRemark()),
            position.getCreatedBy());
  }

  /**
   * Updates editable fields; returns true when a row was changed.
   *
   * @throws BusinessException when required fields are invalid or the name is
   *                           already used by another row
   */
  public static boolean update(Position position) {
    if (position == null || position.getId() <= 0) {
      throw new IllegalArgumentException("position id is required");
    }
    requireScope(position);
    String name = requireName(position);
    requireRemark(position);
    if (existsName(name, position.getId())) {
      throw duplicateName(name);
    }
    int updated = Sql.update("""
            UPDATE `position`
               SET department_id = ?,
                   role_id = ?,
                   name = ?,
                   remark = ?,
                   modified_by = ?,
                   modified_date = CURRENT_TIMESTAMP
             WHERE id = ? AND is_deleted = FALSE
            """,
            position.getDepartmentId(),
            position.getRoleId(),
            name,
            blankToNull(position.getRemark()),
            position.getModifiedBy(),
            position.getId());
    return updated > 0;
  }

  /**
   * Soft-deletes one row; returns true when a row was marked deleted.
   *
   * @throws BusinessException when a staff record still references the position
   */
  public static boolean softDelete(long id, Long deletedBy) {
    if (id <= 0) {
      throw new IllegalArgumentException("id is required");
    }
    if (isInUse(id)) {
      throw new BusinessException(
              "Position is in use by a staff record and cannot be deleted.");
    }
    int updated = Sql.update("""
            UPDATE `position`
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
          SELECT p.id, p.department_id, p.role_id, p.name, p.remark,
                 p.created_by, p.created_date,
                 d.name AS department_name,
                 r.name AS role_name,
                 (SELECT COALESCE(st.name, u.username)
                    FROM `user` u
                    LEFT JOIN staff st ON st.id = u.staff_id
                   WHERE u.id = p.created_by) AS creator_name
            FROM `position` p
            LEFT JOIN department d ON d.id = p.department_id
            LEFT JOIN `role` r ON r.id = p.role_id
           WHERE p.is_deleted = FALSE
          """;

  /** True while a staff record still references the position. */
  private static boolean isInUse(long id) {
    return Sql.queryOne(
            "SELECT 1 FROM staff WHERE position_id = ? LIMIT 1",
            rs -> 1L,
            id).isPresent();
  }

  private static Position mapRow(ResultSet rs) throws SQLException {
    long departmentId = rs.getLong("department_id");
    boolean departmentNull = rs.wasNull();
    long roleId = rs.getLong("role_id");
    boolean roleNull = rs.wasNull();
    long createdBy = rs.getLong("created_by");
    boolean createdByNull = rs.wasNull();
    Position position = new Position(
            rs.getLong("id"),
            departmentNull ? null : departmentId,
            roleNull ? null : roleId,
            rs.getString("name"),
            rs.getString("remark"),
            createdByNull ? null : createdBy,
            rs.getTimestamp("created_date") == null
                    ? null : rs.getTimestamp("created_date").toLocalDateTime());
    position.setDepartmentName(rs.getString("department_name"));
    position.setRoleName(rs.getString("role_name"));
    position.setCreatorName(rs.getString("creator_name"));
    return position;
  }

  private static List<Object[]> toTableRows(List<Position> positions) {
    List<Object[]> tableRows = new ArrayList<>(positions.size());
    for (Position position : positions) {
      tableRows.add(position.toTableRow());
    }
    return tableRows;
  }

  private static void requireScope(Position position) {
    if (position.getDepartmentId() == null || position.getDepartmentId() <= 0) {
      throw new BusinessException("Department is required.");
    }
    if (position.getRoleId() == null || position.getRoleId() <= 0) {
      throw new BusinessException("Role is required.");
    }
  }

  private static String requireName(Position position) {
    String name = position.getName() == null ? "" : position.getName().trim();
    if (name.isEmpty()) {
      throw new BusinessException("Position name is required.");
    }
    if (name.length() > 100) {
      throw new BusinessException("Position name must be 100 characters or fewer.");
    }
    return name;
  }

  private static void requireRemark(Position position) {
    String remark = position.getRemark();
    if (remark != null && remark.trim().length() > 500) {
      throw new BusinessException("Remark must be 500 characters or fewer.");
    }
  }

  private static BusinessException duplicateName(String name) {
    return new BusinessException("Position '" + name + "' already exists.");
  }

  private static String blankToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
