package hrms.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import hrms.exception.BusinessException;
import hrms.model.SalaryRate;

/**
 * JDBC access for the salary_rate table (soft-deleted rows excluded). The role
 * name is resolved via LEFT JOIN so a deleted role still renders.
 */
public final class SalaryRateRepository {

  private SalaryRateRepository() {
  }

  public static List<Object[]> findAllAsTableRows() {
    return toTableRows(listAll());
  }

  public static List<SalaryRate> listAll() {
    return Sql.query(SELECT_SQL + " ORDER BY s.id DESC", SalaryRateRepository::mapRow);
  }

  public static Optional<SalaryRate> findById(long id) {
    return Sql.queryOne(SELECT_SQL + " AND s.id = ?", SalaryRateRepository::mapRow, id);
  }

  /**
   * Inserts a salary rate and returns the generated id.
   *
   * @throws BusinessException when base salary exceeds ceil salary (mirrors
   *                           the DB CHECK constraint)
   */
  public static long insert(SalaryRate salaryRate) {
    if (salaryRate == null) {
      throw new IllegalArgumentException("salary rate is required");
    }
    requireRange(salaryRate);
    return Sql.insert("""
            INSERT INTO salary_rate
              (role_id, base_salary, ceil_salary, currency, pay_frequency, remark,
               created_by, created_date, is_deleted)
            VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, FALSE)
            """,
            salaryRate.getRoleId(),
            salaryRate.getBaseSalary(),
            salaryRate.getCeilSalary(),
            salaryRate.getCurrency(),
            salaryRate.getPayFrequency(),
            salaryRate.getRemark(),
            salaryRate.getCreatedBy());
  }

  /** Updates editable fields; returns true when a row was changed. */
  public static boolean update(SalaryRate salaryRate) {
    if (salaryRate == null || salaryRate.getId() <= 0) {
      throw new IllegalArgumentException("salary rate id is required");
    }
    requireRange(salaryRate);
    int updated = Sql.update("""
            UPDATE salary_rate
               SET role_id = ?,
                   base_salary = ?,
                   ceil_salary = ?,
                   currency = ?,
                   pay_frequency = ?,
                   remark = ?,
                   modified_by = ?,
                   modified_date = CURRENT_TIMESTAMP
             WHERE id = ? AND is_deleted = FALSE
            """,
            salaryRate.getRoleId(),
            salaryRate.getBaseSalary(),
            salaryRate.getCeilSalary(),
            salaryRate.getCurrency(),
            salaryRate.getPayFrequency(),
            salaryRate.getRemark(),
            salaryRate.getModifiedBy(),
            salaryRate.getId());
    return updated > 0;
  }

  /** Soft-deletes one row; returns true when a row was marked deleted. */
  public static boolean softDelete(long id, Long deletedBy) {
    if (id <= 0) {
      throw new IllegalArgumentException("id is required");
    }
    int updated = Sql.update("""
            UPDATE salary_rate
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
          SELECT s.id, s.role_id, s.base_salary, s.ceil_salary, s.currency,
                 s.pay_frequency, s.remark, s.created_by, s.created_date,
                 (SELECT COALESCE(st.name, u.username)
                    FROM `user` u
                    LEFT JOIN staff st ON st.id = u.staff_id
                   WHERE u.id = s.created_by) AS creator_name,
                 r.name AS role_name
            FROM salary_rate s
            LEFT JOIN role r ON r.id = s.role_id
           WHERE s.is_deleted = FALSE
          """;

  private static SalaryRate mapRow(ResultSet rs) throws SQLException {
    SalaryRate salaryRate = new SalaryRate(
            rs.getLong("id"),
            rs.getLong("role_id"),
            rs.getBigDecimal("base_salary"),
            rs.getBigDecimal("ceil_salary"),
            rs.getString("currency"),
            rs.getString("pay_frequency"),
            rs.getString("remark"),
            nullableLong(rs, "created_by"),
            rs.getTimestamp("created_date") == null
                    ? null : rs.getTimestamp("created_date").toLocalDateTime());
    salaryRate.setCreatorName(rs.getString("creator_name"));
    salaryRate.setRoleName(rs.getString("role_name"));
    return salaryRate;
  }

  private static Long nullableLong(ResultSet rs, String column) throws SQLException {
    long value = rs.getLong(column);
    return rs.wasNull() ? null : value;
  }

  private static void requireRange(SalaryRate salaryRate) {
    if (salaryRate.getBaseSalary() == null || salaryRate.getCeilSalary() == null) {
      throw new BusinessException("Base salary and ceil salary are required.");
    }
    if (salaryRate.getCeilSalary().compareTo(salaryRate.getBaseSalary()) < 0) {
      throw new BusinessException("Ceil salary cannot be less than base salary.");
    }
  }

  private static List<Object[]> toTableRows(List<SalaryRate> salaryRates) {
    List<Object[]> tableRows = new ArrayList<>(salaryRates.size());
    for (SalaryRate salaryRate : salaryRates) {
      tableRows.add(salaryRate.toTableRow());
    }
    return tableRows;
  }
}
