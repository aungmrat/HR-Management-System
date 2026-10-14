package hrms.repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import hrms.exception.BusinessException;
import hrms.exception.DataAccessException;
import hrms.model.Attendance;

/**
 * JDBC access for the attendance table (soft-deleted rows excluded).
 * Follows the same pattern as {@link OfficeRepository}.
 */
public final class AttendanceRepository {

  private AttendanceRepository() {
  }

  /** Values allowed by {@code chk_attendance_status}. */
  public static final String[] STATUSES = {
    "PRESENT", "ABSENT", "LEAVE", "OVERTIME", "HALF_DAY_AM", "HALF_DAY_PM"
  };

  public static List<Object[]> findAllAsTableRows(String keyword, String status,
          LocalDate from, LocalDate to) {
    List<Object[]> tableRows = new ArrayList<>();
    for (Attendance attendance : findAll(keyword, status, from, to)) {
      tableRows.add(attendance.toTableRow());
    }
    return tableRows;
  }

  public static List<Attendance> findAll(String keyword, String status,
          LocalDate from, LocalDate to) {
    StringBuilder sql = new StringBuilder(SELECT_SQL);
    List<Object> params = new ArrayList<>();
    if (keyword != null && !keyword.isBlank()) {
      sql.append(" AND (s.code LIKE CONCAT('%', ?, '%') "
              + "OR s.name LIKE CONCAT('%', ?, '%'))");
      String filter = keyword.trim();
      params.add(filter);
      params.add(filter);
    }
    if (status != null && !status.isBlank()) {
      sql.append(" AND a.status = ?");
      params.add(status.trim());
    }
    if (from != null) {
      sql.append(" AND a.date >= ?");
      params.add(from);
    }
    if (to != null) {
      sql.append(" AND a.date <= ?");
      params.add(to);
    }
    sql.append(" ORDER BY a.date DESC, s.code ASC");
    return Sql.query(sql.toString(), AttendanceRepository::mapRow, params.toArray());
  }

  public static Optional<Attendance> findById(long id) {
    return Sql.queryOne(SELECT_SQL + " AND a.id = ?", AttendanceRepository::mapRow, id);
  }

  /** True when another non-deleted row already records this staff on this date. */
  public static boolean exists(long staffId, LocalDate date, Long excludeId) {
    if (excludeId == null) {
      return Sql.queryOne(
              "SELECT 1 FROM attendance WHERE staff_id = ? AND date = ? AND is_deleted = FALSE",
              rs -> 1L,
              staffId, date).isPresent();
    }
    return Sql.queryOne(
            "SELECT 1 FROM attendance WHERE staff_id = ? AND date = ? "
                    + "AND id <> ? AND is_deleted = FALSE",
            rs -> 1L,
            staffId, date, excludeId).isPresent();
  }

  /**
   * KPI counts for a date: status -> row count, plus TOTAL_HOURS key.
   * Returns an empty map when the date has no rows.
   */
  public static Map<String, BigDecimal> summary(LocalDate date) {
    Map<String, BigDecimal> summary = new LinkedHashMap<>();
    List<Object[]> rows = Sql.query("""
            SELECT status, COUNT(*), COALESCE(SUM(total_hours), 0)
              FROM attendance
             WHERE date = ? AND is_deleted = FALSE
             GROUP BY status
            """,
            rs -> new Object[]{
              rs.getString(1),
              BigDecimal.valueOf(rs.getLong(2)),
              rs.getBigDecimal(3)
            },
            date);
    BigDecimal total = BigDecimal.ZERO;
    for (Object[] row : rows) {
      summary.put((String) row[0], (BigDecimal) row[1]);
      total = total.add((BigDecimal) row[2]);
    }
    if (!rows.isEmpty()) {
      summary.put("TOTAL_HOURS", total);
    }
    return summary;
  }

  /**
   * Inserts an attendance record and returns the generated id.
   *
   * @throws BusinessException when the staff already has a row for the date
   */
  public static long insert(Attendance attendance) {
    if (attendance == null) {
      throw new IllegalArgumentException("attendance is required");
    }
    if (attendance.getDate() == null) {
      throw new BusinessException("Date is required.");
    }
    if (exists(attendance.getStaffId(), attendance.getDate(), null)) {
      throw duplicateDate();
    }
    return insertRow(attendance);
  }

  /** Updates editable fields; returns true when a row was changed. */
  public static boolean update(Attendance attendance) {
    if (attendance == null || attendance.getId() <= 0) {
      throw new IllegalArgumentException("attendance id is required");
    }
    if (attendance.getDate() == null) {
      throw new BusinessException("Date is required.");
    }
    if (exists(attendance.getStaffId(), attendance.getDate(), attendance.getId())) {
      throw duplicateDate();
    }
    int updated = Sql.update("""
            UPDATE attendance
               SET staff_id = ?,
                   date = ?,
                   shift_id = ?,
                   time_in = ?,
                   time_out = ?,
                   total_hours = ?,
                   status = ?,
                   remark = ?,
                   modified_by = ?,
                   modified_date = CURRENT_TIMESTAMP
             WHERE id = ? AND is_deleted = FALSE
            """,
            attendance.getStaffId(),
            attendance.getDate(),
            attendance.getShiftId(),
            attendance.getTimeIn(),
            attendance.getTimeOut(),
            attendance.getTotalHours(),
            attendance.getStatus(),
            blankToNull(attendance.getRemark()),
            attendance.getModifiedBy(),
            attendance.getId());
    return updated > 0;
  }

  /** Soft-deletes one row; returns true when a row was marked deleted. */
  public static boolean softDelete(long id, Long deletedBy) {
    if (id <= 0) {
      throw new IllegalArgumentException("id is required");
    }
    int updated = Sql.update("""
            UPDATE attendance
               SET is_deleted = TRUE,
                   deleted_by = ?,
                   deleted_date = CURRENT_TIMESTAMP
             WHERE id = ? AND is_deleted = FALSE
            """,
            deletedBy,
            id);
    return updated > 0;
  }

  private static long insertRow(Attendance attendance) {
    try {
      return Sql.insert("""
              INSERT INTO attendance
                (staff_id, date, shift_id, time_in, time_out, total_hours,
                 status, remark, created_by, created_date, is_deleted)
              VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, FALSE)
              """,
              attendance.getStaffId(),
              attendance.getDate(),
              attendance.getShiftId(),
              attendance.getTimeIn(),
              attendance.getTimeOut(),
              attendance.getTotalHours(),
              attendance.getStatus(),
              blankToNull(attendance.getRemark()),
              attendance.getCreatedBy());
    } catch (DataAccessException e) {
      if (isDuplicateKey(e)) {
        throw duplicateDate();
      }
      throw e;
    }
  }

  private static final String SELECT_SQL = """
          SELECT a.id, a.staff_id, a.date, a.shift_id, a.time_in, a.time_out,
                 a.total_hours, a.status, a.remark, a.created_by,
                 CONCAT(s.code, ' - ', s.name) AS staff_name,
                 CONCAT(sh.code, ' ',
                        TIME_FORMAT(sh.start_time, '%H:%i'), '-',
                        TIME_FORMAT(sh.end_time, '%H:%i'),
                        IF(sh.is_overnight, ' +1', '')) AS shift_label
            FROM attendance a
            JOIN staff s ON s.id = a.staff_id
            JOIN shift sh ON sh.id = a.shift_id
           WHERE a.is_deleted = FALSE
          """;

  private static Attendance mapRow(ResultSet rs) throws SQLException {
    Attendance attendance = new Attendance();
    attendance.setId(rs.getLong("id"));
    attendance.setStaffId(rs.getLong("staff_id"));
    attendance.setDate(rs.getObject("date", LocalDate.class));
    attendance.setShiftId(rs.getLong("shift_id"));
    attendance.setTimeIn(rs.getObject("time_in", LocalTime.class));
    attendance.setTimeOut(rs.getObject("time_out", LocalTime.class));
    attendance.setTotalHours(rs.getBigDecimal("total_hours"));
    attendance.setStatus(rs.getString("status"));
    attendance.setRemark(rs.getString("remark"));
    long createdBy = rs.getLong("created_by");
    attendance.setCreatedBy(rs.wasNull() ? null : createdBy);
    attendance.setStaffName(rs.getString("staff_name"));
    attendance.setShiftLabel(rs.getString("shift_label"));
    return attendance;
  }

  private static BusinessException duplicateDate() {
    return new BusinessException(
            "This staff already has an attendance record on that date.",
            "Pick another date or edit the existing record.");
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
