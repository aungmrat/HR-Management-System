package hrms.repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import hrms.model.Staff;
import hrms.model.StaffSalary;

/**
 * JDBC access for the {@code staff_salary} ledger (soft-deleted rows excluded).
 *
 * <p>Reads serve the Staff Salary screen and its history dialog. The
 * package-private write helpers run on the transaction-bound {@link Sql}
 * supplied by {@link TransactionManager}, so a staff insert/update and its
 * ledger rows commit or roll back atomically (called from
 * {@code StaffRepository}).</p>
 *
 * <p>Ledger rules: the open row ({@code end_date IS NULL}) is the current
 * salary; a change closes it the day before the new row takes effect; a
 * same-day/future-dated row is amended in place because
 * {@code UNIQUE(staff_id, effective_date)} forbids a second row on the same
 * date.</p>
 */
public final class StaffSalaryRepository {

  private StaffSalaryRepository() {
  }

  /** One current row per active staff: the newest ledger row, falling back to {@code staff.salary}. */
  public static List<StaffSalary> listCurrent() {
    return Sql.query(CURRENT_SQL, StaffSalaryRepository::mapRow);
  }

  /** Full ledger history for one staff member, newest first. */
  public static List<StaffSalary> listHistory(long staffId) {
    return Sql.query(HISTORY_SQL + " ORDER BY ss.effective_date DESC, ss.id DESC",
            StaffSalaryRepository::mapRow, staffId);
  }

  /**
   * Creates the first ledger row for a new staff member; runs inside the
   * staff insert transaction. Effective date: commencement, else application
   * date, else today.
   */
  static void seedInitial(Sql sql, long staffId, Staff staff, BigDecimal salary) {
    LocalDate effective = staff.getCommencementDate() != null
            ? staff.getCommencementDate()
            : staff.getApplicationDate() != null ? staff.getApplicationDate() : LocalDate.now();
    insertRow(sql, staffId, salary, effective,
            findBandId(sql, staff.getPositionId(), salary),
            "Initial salary at registration", staff.getCreatedBy());
  }

  /**
   * Applies a salary change to the ledger; runs inside the staff update
   * transaction. Closes the open row and opens a new one effective today,
   * or amends the open row in place when it takes effect today or later.
   */
  static void syncChange(Sql sql, Staff staff, BigDecimal newSalary) {
    LocalDate today = LocalDate.now();
    Long band = findBandId(sql, staff.getPositionId(), newSalary);
    Optional<OpenRow> open = findOpenRow(sql, staff.getId());
    if (open.isEmpty()) {
      insertRow(sql, staff.getId(), newSalary, today, band, null, staff.getModifiedBy());
      return;
    }
    OpenRow row = open.get();
    if (!row.effectiveDate().isBefore(today)) {
      updateOpenRow(sql, row.id(), newSalary, band, staff.getModifiedBy());
      return;
    }
    closeOpenRow(sql, row.id(), today.minusDays(1), staff.getModifiedBy());
    insertRow(sql, staff.getId(), newSalary, today, band, null, staff.getModifiedBy());
  }

  /**
   * Re-evaluates the rate band on the open row after a position change with
   * an unchanged salary; runs inside the staff update transaction. Amends in
   * place - a position change alone must not create a history row.
   */
  static void refreshBand(Sql sql, Staff staff, BigDecimal salary) {
    findOpenRow(sql, staff.getId()).ifPresent(row -> updateOpenRow(sql, row.id(),
            salary, findBandId(sql, staff.getPositionId(), salary), staff.getModifiedBy()));
  }

  /** Salary rate whose band contains {@code salary} for the position's role, null when none. */
  private static Long findBandId(Sql sql, Long positionId, BigDecimal salary) {
    if (positionId == null || salary == null) {
      return null;
    }
    return sql.first("""
            SELECT sr.id
              FROM position p
              JOIN salary_rate sr ON sr.role_id = p.role_id
             WHERE p.id = ? AND ? BETWEEN sr.base_salary AND sr.ceil_salary
             LIMIT 1
            """, rs -> rs.getLong(1), positionId, salary).orElse(null);
  }

  private record OpenRow(long id, LocalDate effectiveDate) {
  }

  private static Optional<OpenRow> findOpenRow(Sql sql, long staffId) {
    return sql.first("""
            SELECT id, effective_date
              FROM staff_salary
             WHERE staff_id = ? AND end_date IS NULL AND is_deleted = FALSE
             ORDER BY effective_date DESC, id DESC
             LIMIT 1
            """, rs -> {
      java.sql.Date effective = rs.getDate("effective_date");
      return new OpenRow(rs.getLong("id"),
              effective == null ? null : effective.toLocalDate());
    }, staffId);
  }

  private static void insertRow(Sql sql, long staffId, BigDecimal salary,
          LocalDate effective, Long salaryRateId, String remark, Long userId) {
    sql.executeInsert("""
            INSERT INTO staff_salary
              (staff_id, salary_rate_id, base_salary, effective_date, remark,
               created_by, created_date, is_deleted)
            VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, FALSE)
            """,
            staffId, salaryRateId, salary, effective, remark, userId);
  }

  private static void closeOpenRow(Sql sql, long rowId, LocalDate endDate, Long userId) {
    sql.executeUpdate("""
            UPDATE staff_salary
               SET end_date = ?,
                   modified_by = ?,
                   modified_date = CURRENT_TIMESTAMP
             WHERE id = ? AND end_date IS NULL AND is_deleted = FALSE
            """,
            endDate, userId, rowId);
  }

  private static void updateOpenRow(Sql sql, long rowId, BigDecimal salary,
          Long salaryRateId, Long userId) {
    sql.executeUpdate("""
            UPDATE staff_salary
               SET base_salary = ?,
                   salary_rate_id = ?,
                   modified_by = ?,
                   modified_date = CURRENT_TIMESTAMP
             WHERE id = ? AND is_deleted = FALSE
            """,
            salary, salaryRateId, userId, rowId);
  }

  /** Columns shared by both reads so one mapper serves the screen and the dialog. */
  private static final String COMMON_COLUMNS = """
          ss.id, ss.staff_id, ss.salary_rate_id, ss.effective_date,
          ss.end_date, ss.remark, ss.created_by, ss.created_date,
          s.code AS staff_code, s.name AS staff_name,
          p.name AS position_name,
          sr.base_salary AS rate_base, sr.ceil_salary AS rate_ceil,
          (SELECT COALESCE(st.name, u.username)
             FROM `user` u
             LEFT JOIN staff st ON st.id = u.staff_id
            WHERE u.id = ss.created_by) AS creator_name
        """;

  /**
   * One row per active staff: the newest ledger row, joined for display;
   * base salary falls back to the registration snapshot when no ledger row
   * exists yet.
   */
  private static final String CURRENT_SQL = """
          SELECT %s,
                 COALESCE(ss.base_salary, s.salary) AS base_salary
            FROM staff s
            LEFT JOIN position p ON p.id = s.position_id
            LEFT JOIN staff_salary ss ON ss.id = (
                  SELECT s2.id
                    FROM staff_salary s2
                   WHERE s2.staff_id = s.id AND s2.is_deleted = FALSE
                   ORDER BY s2.effective_date DESC, s2.id DESC
                   LIMIT 1)
            LEFT JOIN salary_rate sr ON sr.id = ss.salary_rate_id
           WHERE s.is_deleted = FALSE
           ORDER BY s.code ASC
          """.formatted(COMMON_COLUMNS);

  private static final String HISTORY_SQL = """
          SELECT %s,
                 ss.base_salary AS base_salary
            FROM staff_salary ss
            JOIN staff s ON s.id = ss.staff_id
            LEFT JOIN position p ON p.id = s.position_id
            LEFT JOIN salary_rate sr ON sr.id = ss.salary_rate_id
           WHERE ss.staff_id = ? AND ss.is_deleted = FALSE
          """.formatted(COMMON_COLUMNS);

  private static StaffSalary mapRow(ResultSet rs) throws SQLException {
    StaffSalary row = new StaffSalary();
    row.setId(rs.getLong("id"));
    row.setStaffId(rs.getLong("staff_id"));
    long salaryRateId = rs.getLong("salary_rate_id");
    row.setSalaryRateId(rs.wasNull() ? null : salaryRateId);
    row.setBaseSalary(rs.getBigDecimal("base_salary"));
    java.sql.Date effective = rs.getDate("effective_date");
    row.setEffectiveDate(effective == null ? null : effective.toLocalDate());
    java.sql.Date end = rs.getDate("end_date");
    row.setEndDate(end == null ? null : end.toLocalDate());
    row.setRemark(rs.getString("remark"));
    long createdBy = rs.getLong("created_by");
    row.setCreatedBy(rs.wasNull() ? null : createdBy);
    java.sql.Timestamp created = rs.getTimestamp("created_date");
    row.setCreatedDate(created == null ? null : created.toLocalDateTime());
    row.setStaffCode(rs.getString("staff_code"));
    row.setStaffName(rs.getString("staff_name"));
    row.setPositionName(rs.getString("position_name"));
    row.setRateBase(rs.getBigDecimal("rate_base"));
    row.setRateCeil(rs.getBigDecimal("rate_ceil"));
    row.setCreatorName(rs.getString("creator_name"));
    return row;
  }
}
