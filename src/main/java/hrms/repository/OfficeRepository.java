package hrms.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import hrms.exception.BusinessException;
import hrms.exception.DataAccessException;
import hrms.model.Office;

/**
 * JDBC access for the office table (soft-deleted rows excluded).
 * Follows the same pattern as {@link DepartmentRepository}.
 */
public final class OfficeRepository {

  private OfficeRepository() {
  }

  public static List<Object[]> findAllAsTableRows() {
    return toTableRows(listAll());
  }

  public static List<Object[]> searchAsTableRows(String keyword) {
    return toTableRows(search(keyword));
  }

  public static List<Office> listAll() {
    return Sql.query(SELECT_SQL + " ORDER BY o.id DESC", OfficeRepository::mapRow);
  }

  public static List<Office> search(String keyword) {
    String term = keyword == null ? "" : keyword.trim();
    if (term.isEmpty()) {
      return listAll();
    }
    String like = "%" + term.toLowerCase(Locale.ROOT) + "%";
    return Sql.query(
            SELECT_SQL + " AND (LOWER(o.code) LIKE ? OR LOWER(o.name) LIKE ?) ORDER BY o.id DESC",
            OfficeRepository::mapRow,
            like, like);
  }

  public static Optional<Office> findById(long id) {
    return Sql.queryOne(SELECT_SQL + " AND o.id = ?", OfficeRepository::mapRow, id);
  }

  public static boolean existsCode(String code) {
    return existsCode(code, null);
  }

  /** True when another non-deleted row already uses {@code code}. */
  public static boolean existsCode(String code, Long excludeId) {
    String trimmed = code == null ? "" : code.trim();
    if (excludeId == null) {
      return Sql.queryOne(
              "SELECT 1 FROM office WHERE code = ? AND is_deleted = FALSE",
              rs -> 1L,
              trimmed).isPresent();
    }
    return Sql.queryOne(
            "SELECT 1 FROM office WHERE code = ? AND id <> ? AND is_deleted = FALSE",
            rs -> 1L,
            trimmed, excludeId).isPresent();
  }

  /**
   * Inserts an office and returns the generated id.
   *
   * @throws BusinessException when the code is already used
   */
  public static long insert(Office office) {
    if (office == null) {
      throw new IllegalArgumentException("office is required");
    }
    if (office.getDepartmentId() == null) {
      throw new BusinessException("Department is required.");
    }
    String code = requireCode(office);
    if (existsCode(code)) {
      throw duplicateCode(code);
    }
    try {
      return Sql.insert("""
              INSERT INTO office
                (department_id, code, parent_id, name, contact_no, address, status,
                 created_by, created_date, is_deleted)
              VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, FALSE)
              """,
              office.getDepartmentId(),
              code,
              office.getParentId(),
              blankToNull(office.getName()),
              blankToNull(office.getContactNo()),
              blankToNull(office.getAddress()),
              normalizeStatus(office.getStatus()),
              office.getCreatedBy());
    } catch (DataAccessException e) {
      if (isDuplicateKey(e)) {
        throw duplicateCode(code);
      }
      throw e;
    }
  }

  /** Updates editable fields; returns true when a row was changed. */
  public static boolean update(Office office) {
    if (office == null || office.getId() <= 0) {
      throw new IllegalArgumentException("office id is required");
    }
    if (office.getDepartmentId() == null) {
      throw new BusinessException("Department is required.");
    }
    String code = requireCode(office);
    if (existsCode(code, office.getId())) {
      throw duplicateCode(code);
    }
    int updated = Sql.update("""
            UPDATE office
               SET department_id = ?,
                   code = ?,
                   parent_id = ?,
                   name = ?,
                   contact_no = ?,
                   address = ?,
                   status = ?,
                   modified_by = ?,
                   modified_date = CURRENT_TIMESTAMP
             WHERE id = ? AND is_deleted = FALSE
            """,
            office.getDepartmentId(),
            code,
            office.getParentId(),
            blankToNull(office.getName()),
            blankToNull(office.getContactNo()),
            blankToNull(office.getAddress()),
            normalizeStatus(office.getStatus()),
            office.getModifiedBy(),
            office.getId());
    return updated > 0;
  }

  /** Soft-deletes one row; returns true when a row was marked deleted. */
  public static boolean softDelete(long id, Long deletedBy) {
    if (id <= 0) {
      throw new IllegalArgumentException("id is required");
    }
    int updated = Sql.update("""
            UPDATE office
               SET is_deleted = TRUE,
                   deleted_by = ?,
                   deleted_date = CURRENT_TIMESTAMP
             WHERE id = ? AND is_deleted = FALSE
            """,
            deletedBy,
            id);
    return updated > 0;
  }

  // LEFT JOIN so the table can show readable department/parent names,
  // and a missing parent (root office) still comes back as one row.
  private static final String SELECT_SQL = """
          SELECT o.id, o.department_id, o.code, o.parent_id, o.name,
                 o.contact_no, o.address, o.status, o.created_by, o.created_date,
                 d.name AS department_name,
                 p.name AS parent_office_name
            FROM office o
            LEFT JOIN department d ON d.id = o.department_id AND d.is_deleted = FALSE
            LEFT JOIN office p ON p.id = o.parent_id AND p.is_deleted = FALSE
           WHERE o.is_deleted = FALSE
          """;

  private static Office mapRow(ResultSet rs) throws SQLException {
    long id = rs.getLong("id");
    long createdBy = rs.getLong("created_by");
    boolean createdByNull = rs.wasNull();   // check immediately after reading
    Office office = new Office();
    office.setId(id);
    office.setDepartmentId(rs.getLong("department_id"));
    if (rs.wasNull()) {
      office.setDepartmentId(null);
    }
    office.setCode(rs.getString("code"));
    office.setParentId(rs.getLong("parent_id"));
    if (rs.wasNull()) {
      office.setParentId(null);
    }
    office.setName(rs.getString("name"));
    office.setContactNo(rs.getString("contact_no"));
    office.setAddress(rs.getString("address"));
    office.setStatus(rs.getString("status"));
    office.setCreatedBy(createdByNull ? null : createdBy);
    office.setCreatedDate(rs.getTimestamp("created_date") == null
            ? null : rs.getTimestamp("created_date").toLocalDateTime());
    office.setDepartmentName(rs.getString("department_name"));
    office.setParentOfficeName(rs.getString("parent_office_name"));
    return office;
  }

  private static List<Object[]> toTableRows(List<Office> offices) {
    List<Object[]> tableRows = new ArrayList<>(offices.size());
    for (Office office : offices) {
      tableRows.add(office.toTableRow());
    }
    return tableRows;
  }

  private static String requireCode(Office office) {
    String code = office.getCode() == null ? "" : office.getCode().trim();
    if (code.isEmpty()) {
      throw new BusinessException("Office code is required.");
    }
    return code;
  }

  private static String normalizeStatus(String status) {
    return status == null || status.isBlank() ? "Active" : status.trim();
  }

  private static BusinessException duplicateCode(String code) {
    return new BusinessException("Office code '" + code + "' already exists.");
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
