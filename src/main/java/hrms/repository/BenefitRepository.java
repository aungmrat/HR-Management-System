package hrms.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import hrms.exception.BusinessException;
import hrms.model.Benefit;

/**
 * JDBC access for the benefit table (soft-deleted rows excluded).
 */
public final class BenefitRepository {

  private BenefitRepository() {
  }

  public static List<Object[]> findAllAsTableRows() {
    return toTableRows(listAll());
  }

  public static List<Benefit> listAll() {
    return Sql.query(SELECT_SQL + " ORDER BY id DESC", BenefitRepository::mapRow);
  }

  public static Optional<Benefit> findById(long id) {
    return Sql.queryOne(SELECT_SQL + " AND id = ?", BenefitRepository::mapRow, id);
  }

  /** True when another non-deleted row already uses {@code name}. */
  public static boolean existsName(String name, Long excludeId) {
    String trimmed = name == null ? "" : name.trim();
    if (excludeId == null) {
      return Sql.queryOne(
              "SELECT 1 FROM benefit WHERE name = ? AND is_deleted = FALSE",
              rs -> 1L,
              trimmed).isPresent();
    }
    return Sql.queryOne(
            "SELECT 1 FROM benefit WHERE name = ? AND id <> ? AND is_deleted = FALSE",
            rs -> 1L,
            trimmed, excludeId).isPresent();
  }

  /**
   * Inserts a benefit and returns the generated id.
   *
   * @throws BusinessException when the name is already used
   */
  public static long insert(Benefit benefit) {
    if (benefit == null) {
      throw new IllegalArgumentException("benefit is required");
    }
    String name = requireName(benefit);
    if (existsName(name, null)) {
      throw duplicateName(name);
    }
    return Sql.insert("""
            INSERT INTO benefit
              (name, rate, frequency, unit, min_annual_income, max_annual_income,
               min_age, max_age, has_family, has_children, is_active,
               created_by, created_date, is_deleted)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, FALSE)
            """,
            name,
            benefit.getRate(),
            benefit.getFrequency(),
            benefit.getUnit(),
            benefit.getMinAnnualIncome(),
            benefit.getMaxAnnualIncome(),
            benefit.getMinAge(),
            benefit.getMaxAge(),
            benefit.isHasFamily(),
            benefit.isHasChildren(),
            benefit.isActive(),
            benefit.getCreatedBy());
  }

  /** Updates editable fields; returns true when a row was changed. */
  public static boolean update(Benefit benefit) {
    if (benefit == null || benefit.getId() <= 0) {
      throw new IllegalArgumentException("benefit id is required");
    }
    String name = requireName(benefit);
    if (existsName(name, benefit.getId())) {
      throw duplicateName(name);
    }
    int updated = Sql.update("""
            UPDATE benefit
               SET name = ?,
                   rate = ?,
                   frequency = ?,
                   unit = ?,
                   min_annual_income = ?,
                   max_annual_income = ?,
                   min_age = ?,
                   max_age = ?,
                   has_family = ?,
                   has_children = ?,
                   is_active = ?,
                   modified_by = ?,
                   modified_date = CURRENT_TIMESTAMP
             WHERE id = ? AND is_deleted = FALSE
            """,
            name,
            benefit.getRate(),
            benefit.getFrequency(),
            benefit.getUnit(),
            benefit.getMinAnnualIncome(),
            benefit.getMaxAnnualIncome(),
            benefit.getMinAge(),
            benefit.getMaxAge(),
            benefit.isHasFamily(),
            benefit.isHasChildren(),
            benefit.isActive(),
            benefit.getModifiedBy(),
            benefit.getId());
    return updated > 0;
  }

  /** Soft-deletes one row; returns true when a row was marked deleted. */
  public static boolean softDelete(long id, Long deletedBy) {
    if (id <= 0) {
      throw new IllegalArgumentException("id is required");
    }
    int updated = Sql.update("""
            UPDATE benefit
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
          SELECT id, name, rate, frequency, unit,
                 min_annual_income, max_annual_income,
                 min_age, max_age, has_family, has_children, is_active,
                 created_by, created_date,
                 (SELECT COALESCE(st.name, u.username)
                    FROM `user` u
                    LEFT JOIN staff st ON st.id = u.staff_id
                   WHERE u.id = benefit.created_by) AS creator_name
            FROM benefit
           WHERE is_deleted = FALSE
          """;

  private static Benefit mapRow(ResultSet rs) throws SQLException {
    long id = rs.getLong("id");
    long createdBy = rs.getLong("created_by");
    boolean createdByNull = rs.wasNull();
    Benefit benefit = new Benefit(
            id,
            rs.getString("name"),
            rs.getBigDecimal("rate"),
            rs.getString("frequency"),
            rs.getString("unit"),
            rs.getBigDecimal("min_annual_income"),
            rs.getBigDecimal("max_annual_income"),
            nullableInt(rs, "min_age"),
            nullableInt(rs, "max_age"),
            rs.getBoolean("has_family"),
            rs.getBoolean("has_children"),
            rs.getBoolean("is_active"),
            createdByNull ? null : createdBy,
            rs.getTimestamp("created_date") == null
                    ? null : rs.getTimestamp("created_date").toLocalDateTime());
    benefit.setCreatorName(rs.getString("creator_name"));
    return benefit;
  }

  private static Integer nullableInt(ResultSet rs, String column) throws SQLException {
    int value = rs.getInt(column);
    return rs.wasNull() ? null : value;
  }

  private static List<Object[]> toTableRows(List<Benefit> benefits) {
    List<Object[]> tableRows = new ArrayList<>(benefits.size());
    for (Benefit benefit : benefits) {
      tableRows.add(benefit.toTableRow());
    }
    return tableRows;
  }

  private static String requireName(Benefit benefit) {
    String name = benefit.getName() == null ? "" : benefit.getName().trim();
    if (name.isEmpty()) {
      throw new BusinessException("Benefit name is required.");
    }
    return name;
  }

  private static BusinessException duplicateName(String name) {
    return new BusinessException("Benefit '" + name + "' already exists.");
  }
}
