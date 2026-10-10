package hrms.repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import hrms.exception.BusinessException;
import hrms.model.Tax;

/**
 * JDBC access for the tax table (soft-deleted rows excluded).
 */
public final class TaxRepository {

  private TaxRepository() {
  }

  public static List<Object[]> findAllAsTableRows() {
    return toTableRows(listAll());
  }

  public static List<Tax> listAll() {
    return Sql.query(SELECT_SQL + " ORDER BY t.min_amount ASC", TaxRepository::mapRow);
  }

  public static Optional<Tax> findById(long id) {
    return Sql.queryOne(SELECT_SQL + " AND t.id = ?", TaxRepository::mapRow, id);
  }

  /**
   * Inserts a tax bracket and returns the generated id.
   *
   * @throws BusinessException when the bracket range or rate is invalid
   */
  public static long insert(Tax tax) {
    if (tax == null) {
      throw new IllegalArgumentException("tax is required");
    }
    requireValid(tax);
    return Sql.insert("""
            INSERT INTO tax
              (bracket_name, min_amount, max_amount, tax_rate,
               created_by, created_date, is_deleted)
            VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, FALSE)
            """,
            tax.getBracketName(),
            tax.getMinAmount(),
            tax.getMaxAmount(),
            tax.getTaxRate(),
            tax.getCreatedBy());
  }

  /** Updates editable fields; returns true when a row was changed. */
  public static boolean update(Tax tax) {
    if (tax == null || tax.getId() <= 0) {
      throw new IllegalArgumentException("tax id is required");
    }
    requireValid(tax);
    int updated = Sql.update("""
            UPDATE tax
               SET bracket_name = ?,
                   min_amount = ?,
                   max_amount = ?,
                   tax_rate = ?,
                   modified_by = ?,
                   modified_date = CURRENT_TIMESTAMP
             WHERE id = ? AND is_deleted = FALSE
            """,
            tax.getBracketName(),
            tax.getMinAmount(),
            tax.getMaxAmount(),
            tax.getTaxRate(),
            tax.getModifiedBy(),
            tax.getId());
    return updated > 0;
  }

  /** Soft-deletes one row; returns true when a row was marked deleted. */
  public static boolean softDelete(long id, Long deletedBy) {
    if (id <= 0) {
      throw new IllegalArgumentException("id is required");
    }
    int updated = Sql.update("""
            UPDATE tax
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
          SELECT t.id, t.bracket_name, t.min_amount, t.max_amount, t.tax_rate,
                 t.created_by, t.created_date,
                 (SELECT COALESCE(st.name, u.username)
                    FROM `user` u
                    LEFT JOIN staff st ON st.id = u.staff_id
                   WHERE u.id = t.created_by) AS creator_name
            FROM tax t
           WHERE t.is_deleted = FALSE
          """;

  private static Tax mapRow(ResultSet rs) throws SQLException {
    Tax tax = new Tax(
            rs.getLong("id"),
            rs.getString("bracket_name"),
            rs.getBigDecimal("min_amount"),
            rs.getBigDecimal("max_amount"),
            rs.getBigDecimal("tax_rate"),
            nullableLong(rs, "created_by"),
            rs.getTimestamp("created_date") == null
                    ? null : rs.getTimestamp("created_date").toLocalDateTime());
    tax.setCreatorName(rs.getString("creator_name"));
    return tax;
  }

  private static Long nullableLong(ResultSet rs, String column) throws SQLException {
    long value = rs.getLong(column);
    return rs.wasNull() ? null : value;
  }

  /** App-level mirror of the column constraints (bracket range + rate scale). */
  private static void requireValid(Tax tax) {
    if (tax.getMinAmount() == null) {
      throw new BusinessException("Min amount is required.");
    }
    if (tax.getMaxAmount() != null
            && tax.getMaxAmount().compareTo(tax.getMinAmount()) < 0) {
      throw new BusinessException("Max amount cannot be less than min amount.");
    }
    if (tax.getTaxRate() == null) {
      throw new BusinessException("Tax rate is required.");
    }
    if (tax.getTaxRate().compareTo(BigDecimal.ZERO) < 0) {
      throw new BusinessException("Tax rate cannot be negative.");
    }
    if (tax.getTaxRate().compareTo(new BigDecimal("9.9999")) > 0) {
      throw new BusinessException("Tax rate is too large (max 999.99%).");
    }
  }

  private static List<Object[]> toTableRows(List<Tax> taxes) {
    List<Object[]> tableRows = new ArrayList<>(taxes.size());
    for (Tax tax : taxes) {
      tableRows.add(tax.toTableRow());
    }
    return tableRows;
  }
}
