package hrms.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import hrms.exception.BusinessException;
import hrms.model.Allowance;

/**
 * JDBC access for the allowance table (soft-deleted rows excluded).
 */
public final class AllowanceRepository {

  private AllowanceRepository() {
  }

  public static List<Object[]> findAllAsTableRows() {
    return toTableRows(listAll());
  }

  public static List<Allowance> listAll() {
    return Sql.query(SELECT_SQL + " ORDER BY id DESC", AllowanceRepository::mapRow);
  }

  public static Optional<Allowance> findById(long id) {
    return Sql.queryOne(SELECT_SQL + " AND id = ?", AllowanceRepository::mapRow, id);
  }

  /** True when another non-deleted row already uses {@code name}. */
  public static boolean existsName(String name, Long excludeId) {
    String trimmed = name == null ? "" : name.trim();
    if (excludeId == null) {
      return Sql.queryOne(
              "SELECT 1 FROM allowance WHERE name = ? AND is_deleted = FALSE",
              rs -> 1L,
              trimmed).isPresent();
    }
    return Sql.queryOne(
            "SELECT 1 FROM allowance WHERE name = ? AND id <> ? AND is_deleted = FALSE",
            rs -> 1L,
            trimmed, excludeId).isPresent();
  }

  /**
   * Inserts an allowance and returns the generated id.
   *
   * @throws BusinessException when the name is already used
   */
  public static long insert(Allowance allowance) {
    if (allowance == null) {
      throw new IllegalArgumentException("allowance is required");
    }
    String name = requireName(allowance);
    if (existsName(name, null)) {
      throw duplicateName(name);
    }
    return Sql.insert("""
            INSERT INTO allowance
              (name, rate, min_distance, max_distance, `condition`, remark,
               created_by, created_date, is_deleted)
            VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, FALSE)
            """,
            name,
            allowance.getRate(),
            allowance.getMinDistance(),
            allowance.getMaxDistance(),
            allowance.getCondition(),
            allowance.getRemark(),
            allowance.getCreatedBy());
  }

  /** Updates editable fields; returns true when a row was changed. */
  public static boolean update(Allowance allowance) {
    if (allowance == null || allowance.getId() <= 0) {
      throw new IllegalArgumentException("allowance id is required");
    }
    String name = requireName(allowance);
    if (existsName(name, allowance.getId())) {
      throw duplicateName(name);
    }
    int updated = Sql.update("""
            UPDATE allowance
               SET name = ?,
                   rate = ?,
                   min_distance = ?,
                   max_distance = ?,
                   `condition` = ?,
                   remark = ?,
                   modified_by = ?,
                   modified_date = CURRENT_TIMESTAMP
             WHERE id = ? AND is_deleted = FALSE
            """,
            name,
            allowance.getRate(),
            allowance.getMinDistance(),
            allowance.getMaxDistance(),
            allowance.getCondition(),
            allowance.getRemark(),
            allowance.getModifiedBy(),
            allowance.getId());
    return updated > 0;
  }

  /** Soft-deletes one row; returns true when a row was marked deleted. */
  public static boolean softDelete(long id, Long deletedBy) {
    if (id <= 0) {
      throw new IllegalArgumentException("id is required");
    }
    int updated = Sql.update("""
            UPDATE allowance
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
          SELECT id, name, rate, min_distance, max_distance, `condition`, remark,
                 created_by, created_date,
                 (SELECT COALESCE(st.name, u.username)
                    FROM `user` u
                    LEFT JOIN staff st ON st.id = u.staff_id
                   WHERE u.id = allowance.created_by) AS creator_name
            FROM allowance
           WHERE is_deleted = FALSE
          """;

  private static Allowance mapRow(ResultSet rs) throws SQLException {
    long id = rs.getLong("id");
    long createdBy = rs.getLong("created_by");
    boolean createdByNull = rs.wasNull();
    Allowance allowance = new Allowance(
            id,
            rs.getString("name"),
            rs.getBigDecimal("rate"),
            nullableInt(rs, "min_distance"),
            nullableInt(rs, "max_distance"),
            rs.getString("condition"),
            rs.getString("remark"),
            createdByNull ? null : createdBy,
            rs.getTimestamp("created_date") == null
                    ? null : rs.getTimestamp("created_date").toLocalDateTime());
    allowance.setCreatorName(rs.getString("creator_name"));
    return allowance;
  }

  private static Integer nullableInt(ResultSet rs, String column) throws SQLException {
    int value = rs.getInt(column);
    return rs.wasNull() ? null : value;
  }

  private static List<Object[]> toTableRows(List<Allowance> allowances) {
    List<Object[]> tableRows = new ArrayList<>(allowances.size());
    for (Allowance allowance : allowances) {
      tableRows.add(allowance.toTableRow());
    }
    return tableRows;
  }

  private static String requireName(Allowance allowance) {
    String name = allowance.getName() == null ? "" : allowance.getName().trim();
    if (name.isEmpty()) {
      throw new BusinessException("Allowance name is required.");
    }
    return name;
  }

  private static BusinessException duplicateName(String name) {
    return new BusinessException("Allowance '" + name + "' already exists.");
  }
}
