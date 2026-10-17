package hrms.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import hrms.exception.BusinessException;
import hrms.model.GazettedHoliday;

/**
 * JDBC access for the gazetted_holiday table (soft-deleted rows excluded).
 */
public final class GazettedHolidayRepository {

  private GazettedHolidayRepository() {
  }

  public static List<Object[]> findAllAsTableRows() {
    List<GazettedHoliday> holidays = listAll();
    List<Object[]> rows = new ArrayList<>(holidays.size());
    for (GazettedHoliday holiday : holidays) {
      rows.add(holiday.toTableRow());
    }
    return rows;
  }

  public static List<GazettedHoliday> listAll() {
    return Sql.query(SELECT_SQL + " ORDER BY h.start_date ASC, h.id ASC",
            GazettedHolidayRepository::mapRow);
  }

  /**
   * Active holidays overlapping {@code from}..{@code to} (inclusive) for the
   * calendar grid.
   */
  public static List<GazettedHoliday> findRange(LocalDate from, LocalDate to) {
    return Sql.query(SELECT_SQL
            + " AND h.start_date <= ? AND h.end_date >= ? AND h.status = 'Active'"
            + " ORDER BY h.start_date ASC, h.id ASC",
            GazettedHolidayRepository::mapRow, to, from);
  }

  public static Optional<GazettedHoliday> findById(long id) {
    return Sql.queryOne(SELECT_SQL + " AND h.id = ?",
            GazettedHolidayRepository::mapRow, id);
  }

  /**
   * Inserts a holiday and returns the generated id.
   *
   * @throws BusinessException when the required fields are missing
   */
  public static long insert(GazettedHoliday holiday) {
    requireRow(holiday);
    return Sql.insert("""
            INSERT INTO gazetted_holiday
              (start_date, end_date, title, remark, is_recurring, status,
               created_by, created_date, is_deleted)
            VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, FALSE)
            """,
            holiday.getStartDate(),
            holiday.getEndDate(),
            holiday.getTitle().trim(),
            emptyToNull(holiday.getRemark()),
            holiday.isRecurring(),
            normalizeStatus(holiday.getStatus()),
            holiday.getCreatedBy());
  }

  /** Updates editable fields; returns true when a row was changed. */
  public static boolean update(GazettedHoliday holiday) {
    if (holiday == null || holiday.getId() <= 0) {
      throw new IllegalArgumentException("gazetted holiday id is required");
    }
    requireRow(holiday);
    int updated = Sql.update("""
            UPDATE gazetted_holiday
               SET start_date = ?,
                   end_date = ?,
                   title = ?,
                   remark = ?,
                   is_recurring = ?,
                   status = ?,
                   modified_by = ?,
                   modified_date = CURRENT_TIMESTAMP
             WHERE id = ? AND is_deleted = FALSE
            """,
            holiday.getStartDate(),
            holiday.getEndDate(),
            holiday.getTitle().trim(),
            emptyToNull(holiday.getRemark()),
            holiday.isRecurring(),
            normalizeStatus(holiday.getStatus()),
            holiday.getModifiedBy(),
            holiday.getId());
    return updated > 0;
  }

  /** Soft-deletes one row; returns true when a row was marked deleted. */
  public static boolean softDelete(long id, Long deletedBy) {
    if (id <= 0) {
      throw new IllegalArgumentException("id is required");
    }
    int updated = Sql.update("""
            UPDATE gazetted_holiday
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
          SELECT h.id, h.start_date, h.end_date, h.title, h.remark,
                 h.is_recurring, h.status, h.created_by, h.created_date,
                 (SELECT COALESCE(st.name, u.username)
                    FROM `user` u
                    LEFT JOIN staff st ON st.id = u.staff_id
                   WHERE u.id = h.created_by) AS creator_name
            FROM gazetted_holiday h
           WHERE h.is_deleted = FALSE
          """;

  private static GazettedHoliday mapRow(ResultSet rs) throws SQLException {
    GazettedHoliday holiday = new GazettedHoliday();
    holiday.setId(rs.getLong("id"));
    java.sql.Date startDate = rs.getDate("start_date");
    holiday.setStartDate(startDate == null ? null : startDate.toLocalDate());
    java.sql.Date endDate = rs.getDate("end_date");
    holiday.setEndDate(endDate == null ? null : endDate.toLocalDate());
    holiday.setTitle(rs.getString("title"));
    holiday.setRemark(rs.getString("remark"));
    holiday.setRecurring(rs.getBoolean("is_recurring"));
    holiday.setStatus(rs.getString("status"));
    long createdBy = rs.getLong("created_by");
    holiday.setCreatedBy(rs.wasNull() ? null : createdBy);
    java.sql.Timestamp created = rs.getTimestamp("created_date");
    holiday.setCreatedDate(created == null ? null : created.toLocalDateTime());
    holiday.setCreatorName(rs.getString("creator_name"));
    return holiday;
  }

  private static void requireRow(GazettedHoliday holiday) {
    if (holiday == null) {
      throw new IllegalArgumentException("gazetted holiday is required");
    }
    if (holiday.getStartDate() == null) {
      throw new BusinessException("Start date is required.");
    }
    if (holiday.getEndDate() == null) {
      throw new BusinessException("End date is required.");
    }
    if (holiday.getEndDate().isBefore(holiday.getStartDate())) {
      throw new BusinessException("End date cannot be before start date.");
    }
    String title = holiday.getTitle() == null ? "" : holiday.getTitle().trim();
    if (title.isEmpty()) {
      throw new BusinessException("Title is required.");
    }
    if (title.length() > 200) {
      throw new BusinessException("Title must be at most 200 characters.");
    }
  }

  private static String normalizeStatus(String status) {
    if (status == null || status.isBlank()) {
      return "Active";
    }
    return "Inactive".equalsIgnoreCase(status.trim()) ? "Inactive" : "Active";
  }

  private static String emptyToNull(String value) {
    return value == null || value.trim().isEmpty() ? null : value.trim();
  }
}
