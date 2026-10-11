package hrms.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import hrms.exception.BusinessException;
import hrms.model.LeaveEntitlement;

/**
 * JDBC access for the leave_entitlement table (soft-deleted rows excluded),
 * plus the yearly bulk-generation helpers (active staff, existing rows,
 * proration).
 */
public final class LeaveEntitlementRepository {

  private LeaveEntitlementRepository() {
  }

  public static List<Object[]> findAllAsTableRows() {
    return toTableRows(listAll());
  }

  public static List<Object[]> searchAsTableRows(String keyword) {
    return toTableRows(search(keyword));
  }

  public static List<LeaveEntitlement> listAll() {
    return Sql.query(SELECT_SQL + " ORDER BY e.id DESC", LeaveEntitlementRepository::mapRow);
  }

  /**
   * Keyword filter over staff name/code, leave type name/code and the creator
   * name. Filtering in Java keeps the SQL simple (same approach as
   * OvertimePolicyRepository).
   */
  public static List<LeaveEntitlement> search(String keyword) {
    String term = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
    List<LeaveEntitlement> all = listAll();
    if (term.isEmpty()) {
      return all;
    }
    List<LeaveEntitlement> matched = new ArrayList<>(all.size());
    for (LeaveEntitlement entitlement : all) {
      if (containsTerm(entitlement.getStaffName(), term)
              || containsTerm(entitlement.getLeaveTypeName(), term)
              || containsTerm(entitlement.getRemark(), term)
              || containsTerm(entitlement.getCreatorName(), term)) {
        matched.add(entitlement);
      }
    }
    return matched;
  }

  public static Optional<LeaveEntitlement> findById(long id) {
    return Sql.queryOne(SELECT_SQL + " AND e.id = ?", LeaveEntitlementRepository::mapRow, id);
  }

  /** True when another non-deleted row already covers this staff/type/year start. */
  public static boolean existsFor(long staffId, long leaveTypeId,
          LocalDate effectiveDate, Long excludeId) {
    if (excludeId == null) {
      return Sql.queryOne(
              """
              SELECT 1 FROM leave_entitlement
               WHERE staff_id = ? AND leave_type_id = ? AND effective_date = ?
                 AND is_deleted = FALSE
              """,
              rs -> 1L, staffId, leaveTypeId, java.sql.Date.valueOf(effectiveDate)).isPresent();
    }
    return Sql.queryOne(
            """
            SELECT 1 FROM leave_entitlement
             WHERE staff_id = ? AND leave_type_id = ? AND effective_date = ?
               AND id <> ? AND is_deleted = FALSE
            """,
            rs -> 1L, staffId, leaveTypeId, java.sql.Date.valueOf(effectiveDate), excludeId)
            .isPresent();
  }

  /**
   * Inserts an entitlement and returns the generated id.
   *
   * @throws BusinessException when the staff/type/year row already exists
   */
  public static long insert(LeaveEntitlement entitlement) {
    if (entitlement == null) {
      throw new IllegalArgumentException("entitlement is required");
    }
    if (entitlement.getEffectiveDate() == null) {
      throw new BusinessException("Effective date is required.");
    }
    if (existsFor(entitlement.getStaffId(), entitlement.getLeaveTypeId(),
            entitlement.getEffectiveDate(), null)) {
      throw duplicateRow(entitlement);
    }
    return Sql.insert(INSERT_SQL, values(entitlement));
  }

  /** Updates editable fields; returns true when a row was changed. */
  public static boolean update(LeaveEntitlement entitlement) {
    if (entitlement == null || entitlement.getId() <= 0) {
      throw new IllegalArgumentException("entitlement id is required");
    }
    if (entitlement.getEffectiveDate() == null) {
      throw new BusinessException("Effective date is required.");
    }
    if (existsFor(entitlement.getStaffId(), entitlement.getLeaveTypeId(),
            entitlement.getEffectiveDate(), entitlement.getId())) {
      throw duplicateRow(entitlement);
    }
    int updated = Sql.update("""
            UPDATE leave_entitlement
               SET staff_id = ?,
                   leave_type_id = ?,
                   entitled_days = ?,
                   carried_days = ?,
                   used_days = ?,
                   effective_date = ?,
                   expiry_date = ?,
                   remark = ?,
                   modified_by = ?,
                   modified_date = CURRENT_TIMESTAMP
             WHERE id = ? AND is_deleted = FALSE
            """,
            entitlement.getStaffId(),
            entitlement.getLeaveTypeId(),
            entitlement.getEntitledDays(),
            entitlement.getCarriedDays(),
            entitlement.getUsedDays(),
            java.sql.Date.valueOf(entitlement.getEffectiveDate()),
            java.sql.Date.valueOf(entitlement.getExpiryDate()),
            entitlement.getRemark(),
            entitlement.getModifiedBy(),
            entitlement.getId());
    return updated > 0;
  }

  /** Soft-deletes one row; returns true when a row was marked deleted. */
  public static boolean softDelete(long id, Long deletedBy) {
    if (id <= 0) {
      throw new IllegalArgumentException("id is required");
    }
    int updated = Sql.update("""
            UPDATE leave_entitlement
               SET is_deleted = TRUE,
                   deleted_by = ?,
                   deleted_date = CURRENT_TIMESTAMP
             WHERE id = ? AND is_deleted = FALSE
            """,
            deletedBy,
            id);
    return updated > 0;
  }

  // ------------------------------------------------------------------
  // Yearly bulk generation helpers
  // ------------------------------------------------------------------

  /** Lightweight staff row for the staff combo and bulk generation. */
  public record StaffBasic(long id, String code, String name, LocalDate commencementDate,
          String sex) {
  }

  /** Active, non-deleted staff ordered by code. */
  public static List<StaffBasic> listActiveStaff() {
    return Sql.query("""
            SELECT id, code, name, commencement_date, sex
              FROM staff
             WHERE status = 'Active' AND is_deleted = FALSE
             ORDER BY code
            """,
            rs -> {
              java.sql.Date commencement = rs.getDate("commencement_date");
              return new StaffBasic(
                      rs.getLong("id"),
                      rs.getString("code"),
                      rs.getString("name"),
                      commencement == null ? null : commencement.toLocalDate(),
                      rs.getString("sex"));
            });
  }

  /** staffId:typeId pairs that already exist for the given entitlement start. */
  public static Set<String> existingKeys(LocalDate effectiveDate) {
    List<Object[]> pairs = Sql.query(
            """
            SELECT staff_id, leave_type_id
              FROM leave_entitlement
             WHERE effective_date = ? AND is_deleted = FALSE
            """,
            rs -> new Object[]{rs.getLong(1), rs.getLong(2)},
            java.sql.Date.valueOf(effectiveDate));
    Set<String> keys = new HashSet<>(pairs.size());
    for (Object[] pair : pairs) {
      keys.add(pair[0] + ":" + pair[1]);
    }
    return keys;
  }

  /** The previous-year row for one staff/type, when present. */
  public static Optional<LeaveEntitlement> findByStaffTypeAndEffective(
          long staffId, long leaveTypeId, LocalDate effectiveDate) {
    return Sql.queryOne(SELECT_SQL
            + " AND e.staff_id = ? AND e.leave_type_id = ? AND e.effective_date = ?",
            LeaveEntitlementRepository::mapRow,
            staffId, leaveTypeId, java.sql.Date.valueOf(effectiveDate));
  }

  /**
   * Pro-rated maximum entitled days for a staff member who joined mid-period
   * (month-based, rounded down). Full entitlement when the commencement date
   * is null or on/before the period start.
   *
   * <p>Example: annual quota 12, period 2026-01-01 → 2026-12-31, joined
   * 2026-08-01 → 12 × 5/12 = 5 days.
   */
  public static int maxEntitledDays(LocalDate commencementDate, LocalDate effectiveDate,
          LocalDate expiryDate, int standardDays) {
    if (commencementDate == null || effectiveDate == null || expiryDate == null) {
      return standardDays;
    }
    if (!commencementDate.isAfter(effectiveDate)) {
      return standardDays;
    }
    int periodMonths = monthsBetween(effectiveDate, expiryDate);
    if (periodMonths <= 0) {
      return standardDays;
    }
    int remainingMonths = monthsBetween(commencementDate, expiryDate);
    if (remainingMonths <= 0) {
      return 0;
    }
    int capped = (int) Math.floor(standardDays * (double) remainingMonths / periodMonths);
    return Math.max(0, Math.min(capped, standardDays));
  }

  /** Inclusive month count between the two dates' year-month parts. */
  private static int monthsBetween(LocalDate from, LocalDate to) {
    return (to.getYear() - from.getYear()) * 12 + (to.getMonthValue() - from.getMonthValue()) + 1;
  }

  private static final String INSERT_SQL = """
          INSERT INTO leave_entitlement
            (staff_id, leave_type_id, entitled_days, carried_days, used_days,
             effective_date, expiry_date, remark, created_by, created_date, is_deleted)
          VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, FALSE)
          """;

  private static Object[] values(LeaveEntitlement entitlement) {
    return new Object[]{
      entitlement.getStaffId(),
      entitlement.getLeaveTypeId(),
      entitlement.getEntitledDays(),
      entitlement.getCarriedDays(),
      entitlement.getUsedDays(),
      java.sql.Date.valueOf(entitlement.getEffectiveDate()),
      java.sql.Date.valueOf(entitlement.getExpiryDate()),
      entitlement.getRemark(),
      entitlement.getCreatedBy()
    };
  }

  private static final String SELECT_SQL = """
          SELECT e.id, e.staff_id, e.leave_type_id, e.entitled_days, e.carried_days,
                 e.used_days, e.effective_date, e.expiry_date, e.remark,
                 e.created_by, e.created_date,
                 (SELECT COALESCE(st.name, u.username)
                    FROM `user` u
                    LEFT JOIN staff st ON st.id = u.staff_id
                   WHERE u.id = e.created_by) AS creator_name,
                 CONCAT(s.code, ' - ', s.name) AS staff_name,
                 CONCAT(lt.code, ' - ', lt.name) AS leave_type_name
            FROM leave_entitlement e
            LEFT JOIN staff s ON s.id = e.staff_id
            LEFT JOIN leave_type lt ON lt.id = e.leave_type_id
           WHERE e.is_deleted = FALSE
          """;

  private static LeaveEntitlement mapRow(ResultSet rs) throws SQLException {
    LeaveEntitlement entitlement = new LeaveEntitlement(
            rs.getLong("id"),
            rs.getLong("staff_id"),
            rs.getLong("leave_type_id"),
            rs.getInt("entitled_days"),
            nullableInt(rs, "carried_days"),
            nullableInt(rs, "used_days"),
            rs.getDate("effective_date") == null
                    ? null : rs.getDate("effective_date").toLocalDate(),
            rs.getDate("expiry_date") == null
                    ? null : rs.getDate("expiry_date").toLocalDate(),
            rs.getString("remark"),
            nullableLong(rs, "created_by"),
            rs.getTimestamp("created_date") == null
                    ? null : rs.getTimestamp("created_date").toLocalDateTime());
    entitlement.setCreatorName(rs.getString("creator_name"));
    entitlement.setStaffName(rs.getString("staff_name"));
    entitlement.setLeaveTypeName(rs.getString("leave_type_name"));
    return entitlement;
  }

  private static Long nullableLong(ResultSet rs, String column) throws SQLException {
    long value = rs.getLong(column);
    return rs.wasNull() ? null : value;
  }

  private static Integer nullableInt(ResultSet rs, String column) throws SQLException {
    int value = rs.getInt(column);
    return rs.wasNull() ? null : value;
  }

  private static boolean containsTerm(String value, String term) {
    return value != null && value.toLowerCase(Locale.ROOT).contains(term);
  }

  private static BusinessException duplicateRow(LeaveEntitlement entitlement) {
    return new BusinessException("An entitlement for this staff, leave type and year "
            + "already exists.");
  }

  private static List<Object[]> toTableRows(List<LeaveEntitlement> entitlements) {
    List<Object[]> tableRows = new ArrayList<>(entitlements.size());
    for (LeaveEntitlement entitlement : entitlements) {
      tableRows.add(entitlement.toTableRow());
    }
    return tableRows;
  }
}
