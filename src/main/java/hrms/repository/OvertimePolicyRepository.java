package hrms.repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import hrms.model.OvertimePolicy;

/**
 * JDBC access for the overtime_policy table (soft-deleted rows excluded).
 */
public final class OvertimePolicyRepository {

  private OvertimePolicyRepository() {
  }

  public static List<Object[]> findAllAsTableRows() {
    return toTableRows(listAll());
  }

  public static List<Object[]> searchAsTableRows(String keyword) {
    return toTableRows(search(keyword));
  }

  public static List<OvertimePolicy> listAll() {
    return Sql.query(SELECT_SQL + " ORDER BY id DESC", OvertimePolicyRepository::mapRow);
  }

  /**
   * Keyword filter over the numeric columns and the creator name. The table is
   * tiny (one row per policy band), so filtering in Java keeps the SQL simple
   * and avoids CAST gymnastics on INT/DECIMAL columns.
   */
  public static List<OvertimePolicy> search(String keyword) {
    String term = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
    List<OvertimePolicy> all = listAll();
    if (term.isEmpty()) {
      return all;
    }
    List<OvertimePolicy> matched = new ArrayList<>(all.size());
    for (OvertimePolicy policy : all) {
      if (String.valueOf(policy.getMinOtHour()).contains(term)
              || String.valueOf(policy.getMaxOtHourPerMonth()).contains(term)
              || containsTerm(policy.getOtMultiplier(), term)
              || containsTerm(policy.getWeekendOtMultiplier(), term)
              || containsTerm(policy.getHolidayOtMultiplier(), term)
              || (policy.getCreatorName() != null
                  && policy.getCreatorName().toLowerCase(Locale.ROOT).contains(term))) {
        matched.add(policy);
      }
    }
    return matched;
  }

  public static Optional<OvertimePolicy> findById(long id) {
    return Sql.queryOne(SELECT_SQL + " AND id = ?", OvertimePolicyRepository::mapRow, id);
  }

  /**
   * Inserts an overtime policy and returns the generated id.
   */
  public static long insert(OvertimePolicy policy) {
    if (policy == null) {
      throw new IllegalArgumentException("policy is required");
    }
    return Sql.insert("""
            INSERT INTO overtime_policy
              (min_ot_hour, max_ot_hour_per_month, ot_multiplier,
               weekend_ot_multiplier, holiday_ot_multiplier,
               created_by, created_date, is_deleted)
            VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, FALSE)
            """,
            policy.getMinOtHour(),
            policy.getMaxOtHourPerMonth(),
            policy.getOtMultiplier(),
            policy.getWeekendOtMultiplier(),
            policy.getHolidayOtMultiplier(),
            policy.getCreatedBy());
  }

  /** Updates editable fields; returns true when a row was changed. */
  public static boolean update(OvertimePolicy policy) {
    if (policy == null || policy.getId() <= 0) {
      throw new IllegalArgumentException("policy id is required");
    }
    int updated = Sql.update("""
            UPDATE overtime_policy
               SET min_ot_hour = ?,
                   max_ot_hour_per_month = ?,
                   ot_multiplier = ?,
                   weekend_ot_multiplier = ?,
                   holiday_ot_multiplier = ?,
                   modified_by = ?,
                   modified_date = CURRENT_TIMESTAMP
             WHERE id = ? AND is_deleted = FALSE
            """,
            policy.getMinOtHour(),
            policy.getMaxOtHourPerMonth(),
            policy.getOtMultiplier(),
            policy.getWeekendOtMultiplier(),
            policy.getHolidayOtMultiplier(),
            policy.getModifiedBy(),
            policy.getId());
    return updated > 0;
  }

  /** Soft-deletes one row; returns true when a row was marked deleted. */
  public static boolean softDelete(long id, Long deletedBy) {
    if (id <= 0) {
      throw new IllegalArgumentException("id is required");
    }
    int updated = Sql.update("""
            UPDATE overtime_policy
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
          SELECT id, min_ot_hour, max_ot_hour_per_month, ot_multiplier,
                 weekend_ot_multiplier, holiday_ot_multiplier,
                 created_by, created_date,
                 (SELECT COALESCE(st.name, u.username)
                    FROM `user` u
                    LEFT JOIN staff st ON st.id = u.staff_id
                   WHERE u.id = overtime_policy.created_by) AS creator_name
            FROM overtime_policy
           WHERE is_deleted = FALSE
          """;

  private static OvertimePolicy mapRow(ResultSet rs) throws SQLException {
    long id = rs.getLong("id");
    long createdBy = rs.getLong("created_by");
    boolean createdByNull = rs.wasNull();
    OvertimePolicy policy = new OvertimePolicy(
            id,
            rs.getInt("min_ot_hour"),
            rs.getInt("max_ot_hour_per_month"),
            rs.getBigDecimal("ot_multiplier"),
            rs.getBigDecimal("weekend_ot_multiplier"),
            rs.getBigDecimal("holiday_ot_multiplier"),
            createdByNull ? null : createdBy,
            rs.getTimestamp("created_date") == null
                    ? null : rs.getTimestamp("created_date").toLocalDateTime());
    policy.setCreatorName(rs.getString("creator_name"));
    return policy;
  }

  private static List<Object[]> toTableRows(List<OvertimePolicy> policies) {
    List<Object[]> tableRows = new ArrayList<>(policies.size());
    for (OvertimePolicy policy : policies) {
      tableRows.add(policy.toTableRow());
    }
    return tableRows;
  }

  private static boolean containsTerm(BigDecimal value, String term) {
    return value != null && value.toPlainString().contains(term);
  }
}
