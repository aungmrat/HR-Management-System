package hrms.repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import hrms.model.StaffBenefit;

/**
 * JDBC access for the {@code staff_benefit} assignment ledger (soft-deleted
 * rows excluded). Assignments are managed only from the Staff Benefit
 * screen: assign inserts an open row, End closes it, Edit Dates amends it.
 * The rate comes from the referenced {@code benefit} rule - this table has
 * no amount column.
 */
public final class StaffBenefitRepository {

  private StaffBenefitRepository() {
  }

  /** Lightweight staff entry for the assign dialog's picker. */
  public record StaffOption(long id, String code, String name) {
    @Override
    public String toString() {
      return code + " \u2014 " + name;
    }
  }

  /** Facts needed to evaluate a benefit rule's eligibility conditions. */
  public record EligibilityData(LocalDate dob, BigDecimal salary,
          String maritalStatus, int familyCount, int childCount) {
  }

  /** All open + ended assignments, staff name ascending. */
  public static List<StaffBenefit> listAll() {
    return Sql.query(LIST_SQL, StaffBenefitRepository::mapRow);
  }

  /** Every assignment for one staff member, newest first (history dialog). */
  public static List<StaffBenefit> listHistory(long staffId) {
    return Sql.query(HISTORY_SQL + " ORDER BY sb.effective_date DESC, sb.id DESC",
            StaffBenefitRepository::mapRow, staffId);
  }

  /** One assignment for the edit dialog; empty when deleted or unknown. */
  public static Optional<StaffBenefit> findById(long id) {
    return Sql.queryOne(LIST_SQL + " AND sb.id = ?", StaffBenefitRepository::mapRow, id);
  }

  /** Active staff for the assign dialog's picker. */
  public static List<StaffOption> listStaffOptions() {
    return Sql.query("""
            SELECT id, code, name
              FROM staff
             WHERE is_deleted = FALSE AND status = 'Active'
             ORDER BY code ASC
            """, rs -> new StaffOption(rs.getLong("id"),
            rs.getString("code"), rs.getString("name")));
  }

  /** True when an open assignment already exists for this staff + benefit. */
  public static boolean openRowExists(long staffId, long benefitId, long excludeId) {
    return Sql.queryOne("""
            SELECT COUNT(*)
              FROM staff_benefit
             WHERE staff_id = ? AND benefit_id = ? AND end_date IS NULL
               AND is_deleted = FALSE AND id <> ?
            """, rs -> rs.getInt(1) > 0, staffId, benefitId, excludeId).orElse(false);
  }

  /** Inserts an assignment row; effective date required, end optional. */
  public static long insert(StaffBenefit row) {
    if (row == null) {
      throw new IllegalArgumentException("staff benefit is required");
    }
    if (row.getEffectiveDate() == null) {
      throw new IllegalArgumentException("effective date is required");
    }
    if (row.getEndDate() != null && row.getEndDate().isBefore(row.getEffectiveDate())) {
      throw new IllegalArgumentException("end date cannot be before effective date");
    }
    return Sql.insert("""
            INSERT INTO staff_benefit
              (staff_id, benefit_id, effective_date, end_date,
               created_by, created_date, is_deleted)
            VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, FALSE)
            """,
            row.getStaffId(), row.getBenefitId(), row.getEffectiveDate(),
            row.getEndDate(), row.getCreatedBy());
  }

  /** Amends an existing assignment's dates; returns true when a row changed. */
  public static boolean updateDates(long id, LocalDate effectiveDate, LocalDate endDate,
          Long modifiedBy) {
    if (id <= 0) {
      throw new IllegalArgumentException("staff benefit id is required");
    }
    if (effectiveDate == null) {
      throw new IllegalArgumentException("effective date is required");
    }
    if (endDate != null && endDate.isBefore(effectiveDate)) {
      throw new IllegalArgumentException("end date cannot be before effective date");
    }
    return Sql.update("""
            UPDATE staff_benefit
               SET effective_date = ?,
                   end_date = ?,
                   modified_by = ?,
                   modified_date = CURRENT_TIMESTAMP
             WHERE id = ? AND is_deleted = FALSE
            """,
            effectiveDate, endDate, modifiedBy, id) > 0;
  }

  /**
   * Closes an open assignment on {@code endDate}; returns false when the row
   * is already closed or gone.
   */
  public static boolean endAssignment(long id, LocalDate endDate, Long modifiedBy) {
    if (id <= 0 || endDate == null) {
      throw new IllegalArgumentException("id and end date are required");
    }
    return Sql.update("""
            UPDATE staff_benefit
               SET end_date = ?,
                   modified_by = ?,
                   modified_date = CURRENT_TIMESTAMP
             WHERE id = ? AND end_date IS NULL AND is_deleted = FALSE
            """,
            endDate, modifiedBy, id) > 0;
  }

  /** Staff facts for the assign dialog's eligibility hints. */
  public static Optional<EligibilityData> findEligibilityData(long staffId) {
    return Sql.queryOne("""
            SELECT s.dob, s.salary, s.marital_status,
                   (SELECT COUNT(*)
                      FROM family_member fm
                     WHERE fm.staff_id = s.id AND fm.is_deleted = FALSE) AS family_count,
                   (SELECT COUNT(*)
                      FROM family_member fm
                     WHERE fm.staff_id = s.id AND fm.is_deleted = FALSE
                       AND LOWER(fm.relationship) IN ('son', 'daughter', 'child')) AS child_count
              FROM staff s
             WHERE s.id = ? AND s.is_deleted = FALSE
            """, rs -> {
      java.sql.Date dob = rs.getDate("dob");
      return new EligibilityData(
              dob == null ? null : dob.toLocalDate(),
              rs.getBigDecimal("salary"),
              rs.getString("marital_status"),
              rs.getInt("family_count"),
              rs.getInt("child_count"));
    }, staffId);
  }

  /** Columns shared by both reads so one mapper serves screen and history. */
  private static final String COMMON_COLUMNS = """
          sb.id, sb.staff_id, sb.benefit_id, sb.effective_date, sb.end_date,
          sb.created_by, sb.created_date,
          s.code AS staff_code, s.name AS staff_name, s.department_id,
          p.name AS position_name,
          b.name AS benefit_name, b.rate AS benefit_rate,
          b.frequency AS benefit_frequency, b.unit AS benefit_unit,
          (SELECT COALESCE(st.name, u.username)
             FROM `user` u
             LEFT JOIN staff st ON st.id = u.staff_id
            WHERE u.id = sb.created_by) AS creator_name
        """;

  private static final String LIST_SQL = """
          SELECT %s
            FROM staff_benefit sb
            JOIN staff s ON s.id = sb.staff_id
            JOIN benefit b ON b.id = sb.benefit_id
            LEFT JOIN position p ON p.id = s.position_id
           WHERE sb.is_deleted = FALSE AND s.is_deleted = FALSE
          """.formatted(COMMON_COLUMNS);

  private static final String HISTORY_SQL = """
          SELECT %s
            FROM staff_benefit sb
            JOIN staff s ON s.id = sb.staff_id
            JOIN benefit b ON b.id = sb.benefit_id
            LEFT JOIN position p ON p.id = s.position_id
           WHERE sb.staff_id = ? AND sb.is_deleted = FALSE
          """.formatted(COMMON_COLUMNS);

  private static StaffBenefit mapRow(ResultSet rs) throws SQLException {
    StaffBenefit row = new StaffBenefit();
    row.setId(rs.getLong("id"));
    row.setStaffId(rs.getLong("staff_id"));
    row.setBenefitId(rs.getLong("benefit_id"));
    java.sql.Date effective = rs.getDate("effective_date");
    row.setEffectiveDate(effective == null ? null : effective.toLocalDate());
    java.sql.Date end = rs.getDate("end_date");
    row.setEndDate(end == null ? null : end.toLocalDate());
    long createdBy = rs.getLong("created_by");
    row.setCreatedBy(rs.wasNull() ? null : createdBy);
    java.sql.Timestamp created = rs.getTimestamp("created_date");
    row.setCreatedDate(created == null ? null : created.toLocalDateTime());
    row.setStaffCode(rs.getString("staff_code"));
    row.setStaffName(rs.getString("staff_name"));
    long departmentId = rs.getLong("department_id");
    row.setDepartmentId(rs.wasNull() ? null : departmentId);
    row.setPositionName(rs.getString("position_name"));
    row.setBenefitName(rs.getString("benefit_name"));
    row.setBenefitRate(rs.getBigDecimal("benefit_rate"));
    row.setBenefitFrequency(rs.getString("benefit_frequency"));
    row.setBenefitUnit(rs.getString("benefit_unit"));
    row.setCreatorName(rs.getString("creator_name"));
    return row;
  }
}
