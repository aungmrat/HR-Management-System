package hrms.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import hrms.exception.BusinessException;
import hrms.exception.DataAccessException;
import hrms.model.DutyRoster;

/**
 * JDBC access for the duty_roster table (soft-deleted rows excluded).
 * Follows the same pattern as {@link ShiftRepository}.
 */
public final class DutyRosterRepository {

  private DutyRosterRepository() {
  }

  /** The shift this staff is rostered on for the date, when one exists. */
  public static Optional<Long> findShiftIdFor(long staffId, LocalDate date) {
    return Sql.queryOne(
            """
            SELECT shift_id
              FROM duty_roster
             WHERE staff_id = ? AND roster_date = ? AND is_deleted = FALSE
             ORDER BY id DESC
             LIMIT 1
            """,
            rs -> rs.getLong(1),
            staffId, date);
  }

  /** Roster link for the staff/date: shift + duty_roster row id. */
  public record RosterRef(long shiftId, long rosterId) {
  }

  /** The staff's rostered shift for the date, with the roster row id. */
  public static Optional<RosterRef> findRosterFor(long staffId, LocalDate date) {
    return Sql.queryOne(
            """
            SELECT shift_id, id
              FROM duty_roster
             WHERE staff_id = ? AND roster_date = ? AND is_deleted = FALSE
             ORDER BY id DESC
             LIMIT 1
            """,
            rs -> new RosterRef(rs.getLong(1), rs.getLong(2)),
            staffId, date);
  }

  /**
   * Roster rows for the inclusive date range, joined with staff/shift labels
   * and the matching attendance status (null when attendance was not recorded).
   */
  public static List<DutyRoster> findRange(LocalDate from, LocalDate to,
          Long departmentId, String keyword) {
    StringBuilder sql = new StringBuilder(SELECT_SQL);
    List<Object> params = new ArrayList<>();
    if (from != null) {
      sql.append(" AND dr.roster_date >= ?");
      params.add(from);
    }
    if (to != null) {
      sql.append(" AND dr.roster_date <= ?");
      params.add(to);
    }
    if (departmentId != null) {
      sql.append(" AND s.department_id = ?");
      params.add(departmentId);
    }
    if (keyword != null && !keyword.isBlank()) {
      sql.append(" AND (LOWER(s.code) LIKE ? OR LOWER(s.name) LIKE ?)");
      String like = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
      params.add(like);
      params.add(like);
    }
    sql.append(" ORDER BY s.code ASC, dr.roster_date ASC");
    return Sql.query(sql.toString(), DutyRosterRepository::mapRow, params.toArray());
  }

  public static Optional<DutyRoster> findById(long id) {
    return Sql.queryOne(SELECT_SQL + " AND dr.id = ?", DutyRosterRepository::mapRow, id);
  }

  /**
   * True when the staff already has any non-deleted roster row on the date
   * (cross-shift double-booking guard; the DB unique key only covers the
   * same shift).
   */
  public static Optional<String> existingShiftLabel(long staffId, LocalDate date,
          Long excludeId) {
    String likeSql = """
            SELECT CONCAT(sh.code, ' ',
                   TIME_FORMAT(sh.start_time, '%H:%i'), '-',
                   TIME_FORMAT(sh.end_time, '%H:%i'))
              FROM duty_roster dr
              JOIN shift sh ON sh.id = dr.shift_id
             WHERE dr.staff_id = ? AND dr.roster_date = ? AND dr.is_deleted = FALSE
            """ + (excludeId == null ? "" : " AND dr.id <> ?")
            + " ORDER BY dr.id DESC LIMIT 1";
    if (excludeId == null) {
      return Sql.queryOne(likeSql, rs -> rs.getString(1), staffId, date);
    }
    return Sql.queryOne(likeSql, rs -> rs.getString(1), staffId, date, excludeId);
  }

  /**
   * Inserts a roster assignment and returns the generated id.
   *
   * @throws BusinessException when the staff is already rostered that date
   */
  public static long insert(DutyRoster roster) {
    if (roster == null) {
      throw new IllegalArgumentException("roster is required");
    }
    requireFields(roster);
    existingShiftLabel(roster.getStaffId(), roster.getRosterDate(), null)
            .ifPresent(label -> {
              throw alreadyRostered(label);
            });
    try {
      return Sql.insert("""
              INSERT INTO duty_roster
                (shift_id, staff_id, roster_date, status, remark,
                 created_by, created_date, is_deleted)
              VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, FALSE)
              """,
              roster.getShiftId(),
              roster.getStaffId(),
              roster.getRosterDate(),
              roster.getStatus() == null || roster.getStatus().isBlank()
                      ? "Scheduled" : roster.getStatus(),
              blankToNull(roster.getRemark()),
              roster.getCreatedBy());
    } catch (DataAccessException e) {
      if (isDuplicateKey(e)) {
        throw alreadyRostered("that shift");
      }
      throw e;
    }
  }

  /** Updates editable fields; returns true when a row was changed. */
  public static boolean update(DutyRoster roster) {
    if (roster == null || roster.getId() <= 0) {
      throw new IllegalArgumentException("roster id is required");
    }
    requireFields(roster);
    existingShiftLabel(roster.getStaffId(), roster.getRosterDate(), roster.getId())
            .ifPresent(label -> {
              throw alreadyRostered(label);
            });
    int updated = Sql.update("""
            UPDATE duty_roster
               SET shift_id = ?,
                   staff_id = ?,
                   roster_date = ?,
                   status = ?,
                   remark = ?,
                   modified_by = ?,
                   modified_date = CURRENT_TIMESTAMP
             WHERE id = ? AND is_deleted = FALSE
            """,
            roster.getShiftId(),
            roster.getStaffId(),
            roster.getRosterDate(),
            roster.getStatus() == null || roster.getStatus().isBlank()
                    ? "Scheduled" : roster.getStatus(),
            blankToNull(roster.getRemark()),
            roster.getModifiedBy(),
            roster.getId());
    return updated > 0;
  }

  /** Soft-deletes one row; returns true when a row was marked deleted. */
  public static boolean softDelete(long id, Long deletedBy) {
    if (id <= 0) {
      throw new IllegalArgumentException("id is required");
    }
    int updated = Sql.update("""
            UPDATE duty_roster
               SET is_deleted = TRUE,
                   deleted_by = ?,
                   deleted_date = CURRENT_TIMESTAMP
             WHERE id = ? AND is_deleted = FALSE
            """,
            deletedBy,
            id);
    return updated > 0;
  }

  /** Result of a bulk auto-fill run. */
  public record FillResult(int inserted, int skipped) {
  }

  /**
   * Assigns the shift to every active staff in the department for each day of
   * the inclusive range. Days the staff is already rostered (any shift) are
   * skipped when {@code skipExisting} is true, otherwise the insert throws.
   */
  public static FillResult fillWeek(long departmentId, long shiftId,
          LocalDate from, LocalDate to, boolean skipExisting, Long createdBy) {
    List<Long> staffIds = Sql.query("""
            SELECT id
              FROM staff
             WHERE department_id = ? AND status = 'Active' AND is_deleted = FALSE
             ORDER BY code
            """,
            rs -> rs.getLong(1),
            departmentId);
    int inserted = 0;
    int skipped = 0;
    for (long staffId : staffIds) {
      for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
        if (skipExisting && existingShiftLabel(staffId, date, null).isPresent()) {
          skipped++;
          continue;
        }
        try {
          insert(DutyRoster.create(staffId, date, shiftId, "Scheduled", null, createdBy));
          inserted++;
        } catch (BusinessException e) {
          if (skipExisting) {
            skipped++;
          } else {
            throw e;
          }
        }
      }
    }
    return new FillResult(inserted, skipped);
  }

  /** Active staff of a department, for the auto-fill preview. */
  public static List<String> listActiveStaffLabels(long departmentId) {
    return Sql.query("""
            SELECT CONCAT(code, ' - ', name)
              FROM staff
             WHERE department_id = ? AND status = 'Active' AND is_deleted = FALSE
             ORDER BY code
            """,
            rs -> rs.getString(1),
            departmentId);
  }

  /** Lightweight staff row for the week grid (even when unassigned). */
  public record StaffOption(long id, String code, String name) {
  }

  /** Active staff for the week grid, filtered by department/keyword. */
  public static List<StaffOption> listActiveStaff(Long departmentId, String keyword) {
    StringBuilder sql = new StringBuilder("""
            SELECT id, code, name
              FROM staff
             WHERE status = 'Active' AND is_deleted = FALSE
            """);
    List<Object> params = new ArrayList<>();
    if (departmentId != null) {
      sql.append(" AND department_id = ?");
      params.add(departmentId);
    }
    if (keyword != null && !keyword.isBlank()) {
      sql.append(" AND (LOWER(code) LIKE ? OR LOWER(name) LIKE ?)");
      String like = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
      params.add(like);
      params.add(like);
    }
    sql.append(" ORDER BY code");
    return Sql.query(sql.toString(),
            rs -> new StaffOption(rs.getLong(1), rs.getString(2), rs.getString(3)),
            params.toArray());
  }

  private static void requireFields(DutyRoster roster) {
    if (roster.getRosterDate() == null) {
      throw new BusinessException("Roster date is required.");
    }
    if (roster.getShiftId() <= 0) {
      throw new BusinessException("Shift is required.");
    }
    if (roster.getStaffId() <= 0) {
      throw new BusinessException("Staff is required.");
    }
  }

  private static final String SELECT_SQL = """
          SELECT dr.id, dr.shift_id, dr.staff_id, dr.roster_date, dr.status,
                 dr.remark, dr.created_by,
                 CONCAT(s.code, ' - ', s.name) AS staff_name,
                 CONCAT(sh.code, ' ',
                        TIME_FORMAT(sh.start_time, '%H:%i'), '-',
                        TIME_FORMAT(sh.end_time, '%H:%i'),
                        IF(sh.is_overnight, ' +1', '')) AS shift_label,
                 sh.is_overnight AS shift_overnight,
                 sh.start_time AS shift_start_time,
                 a.status AS attendance_status
            FROM duty_roster dr
            JOIN staff s ON s.id = dr.staff_id
            JOIN shift sh ON sh.id = dr.shift_id
            LEFT JOIN attendance a
                   ON a.staff_id = dr.staff_id
                  AND a.date = dr.roster_date
                  AND a.is_deleted = FALSE
           WHERE dr.is_deleted = FALSE
          """;

  private static DutyRoster mapRow(ResultSet rs) throws SQLException {
    DutyRoster roster = new DutyRoster();
    roster.setId(rs.getLong("id"));
    roster.setShiftId(rs.getLong("shift_id"));
    roster.setStaffId(rs.getLong("staff_id"));
    roster.setRosterDate(rs.getObject("roster_date", LocalDate.class));
    roster.setStatus(rs.getString("status"));
    roster.setRemark(rs.getString("remark"));
    long createdBy = rs.getLong("created_by");
    roster.setCreatedBy(rs.wasNull() ? null : createdBy);
    roster.setStaffName(rs.getString("staff_name"));
    roster.setShiftLabel(rs.getString("shift_label"));
    roster.setOvernight(rs.getBoolean("shift_overnight"));
    roster.setShiftStartTime(rs.getObject("shift_start_time", LocalTime.class));
    roster.setAttendanceStatus(rs.getString("attendance_status"));
    return roster;
  }

  private static BusinessException alreadyRostered(String shiftLabel) {
    return new BusinessException(
            "This staff is already rostered on that date.",
            "Existing shift: " + shiftLabel + ". Remove it first or pick another date.");
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
