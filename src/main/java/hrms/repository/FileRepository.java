package hrms.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import hrms.exception.BusinessException;
import hrms.model.StoredFile;

/**
 * JDBC access for the polymorphic {@code file} table (soft-deleted rows
 * excluded). The bytes live on disk via {@link hrms.util.FileStorage}; this
 * repository only manages metadata.
 */
public final class FileRepository {

  private FileRepository() {
  }

  public static List<StoredFile> listBySource(long sourceId, String sourceType) {
    String type = sourceType == null ? "" : sourceType.trim();
    return Sql.query(SELECT_SQL + " AND f.source_id = ? AND f.source_type = ?"
            + " ORDER BY f.id DESC",
        FileRepository::mapRow, sourceId, type);
  }

  /** Optional free-text match on file name; optional exact source type. */
  public static List<StoredFile> search(String keyword, String sourceType) {
    return Sql.query(buildWhere(keyword, sourceType) + " ORDER BY f.id DESC",
        FileRepository::mapRow, params(keyword, sourceType).toArray());
  }

  /**
   * Table rows for the Files list:
   * ID, Name, Type, Source, Uploaded By, Uploaded.
   */
  public static List<Object[]> searchAsTableRows(String keyword, String sourceType) {
    List<Object[]> rows = new ArrayList<>();
    for (StoredFile file : search(keyword, sourceType)) {
      rows.add(file.toTableRow());
    }
    return rows;
  }

  public static Optional<StoredFile> findById(long id) {
    return Sql.queryOne(SELECT_SQL + " AND f.id = ?", FileRepository::mapRow, id);
  }

  /**
   * Inserts file metadata and returns the generated id.
   *
   * @throws BusinessException when required fields are missing or too long
   */
  public static long insert(StoredFile file) {
    if (file == null) {
      throw new IllegalArgumentException("file is required");
    }
    if (file.getSourceId() <= 0) {
      throw new BusinessException("File source id is required.");
    }
    String sourceType = requireText(file.getSourceType(), 50, "Source type");
    String fileName = requireText(file.getFileName(), 255, "File name");
    String filePath = requireText(file.getFilePath(), 500, "File path");
    String fileExt = normalizeOptional(file.getFileExt(), 10, "File extension");
    return Sql.insert("""
            INSERT INTO file
              (source_id, source_type, file_name, file_path, file_ext,
               created_by, created_date, is_deleted)
            VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, FALSE)
            """,
        file.getSourceId(),
        sourceType,
        fileName,
        filePath,
        fileExt,
        file.getCreatedBy());
  }

  /**
   * Soft-deletes one metadata row; returns true when a row was changed. The
   * stored bytes stay on disk so an accidental delete can be recovered by a
   * DBA.
   */
  public static boolean softDelete(long id, Long deletedBy) {
    if (id <= 0) {
      throw new IllegalArgumentException("id is required");
    }
    int updated = Sql.update("""
            UPDATE file
               SET is_deleted = TRUE,
                   deleted_by = ?,
                   deleted_date = CURRENT_TIMESTAMP
             WHERE id = ? AND is_deleted = FALSE
            """,
        deletedBy,
        id);
    return updated > 0;
  }

  private static String buildWhere(String keyword, String sourceType) {
    StringBuilder sql = new StringBuilder(SELECT_SQL);
    String term = keyword == null ? "" : keyword.trim();
    if (!term.isEmpty()) {
      sql.append(" AND LOWER(f.file_name) LIKE ?");
    }
    String type = sourceType == null ? "" : sourceType.trim();
    if (!type.isEmpty()) {
      sql.append(" AND f.source_type = ?");
    }
    return sql.toString();
  }

  private static List<Object> params(String keyword, String sourceType) {
    List<Object> params = new ArrayList<>();
    String term = keyword == null ? "" : keyword.trim();
    if (!term.isEmpty()) {
      params.add("%" + term.toLowerCase(Locale.ROOT) + "%");
    }
    String type = sourceType == null ? "" : sourceType.trim();
    if (!type.isEmpty()) {
      params.add(type);
    }
    return params;
  }

  private static String requireText(String value, int max, String label) {
    String trimmed = normalizeOptional(value, max, label);
    if (trimmed == null) {
      throw new BusinessException(label + " is required.");
    }
    return trimmed;
  }

  private static String normalizeOptional(String value, int max, String label) {
    if (value == null || value.trim().isEmpty()) {
      return null;
    }
    String trimmed = value.trim();
    if (trimmed.length() > max) {
      throw new BusinessException(label + " must be " + max + " characters or fewer.");
    }
    return trimmed;
  }

  private static final String SELECT_SQL = """
          SELECT f.id, f.source_id, f.source_type, f.file_name, f.file_path,
                 f.file_ext, f.created_by, f.created_date,
                 s.name AS staff_name,
                 (SELECT COALESCE(st.name, u.username)
                    FROM `user` u
                    LEFT JOIN staff st ON st.id = u.staff_id
                   WHERE u.id = f.created_by) AS creator_name
            FROM file f
            LEFT JOIN staff s ON f.source_type = 'STAFF' AND s.id = f.source_id
           WHERE f.is_deleted = FALSE
          """;

  private static StoredFile mapRow(ResultSet rs) throws SQLException {
    StoredFile file = new StoredFile();
    file.setId(rs.getLong("id"));
    file.setSourceId(rs.getLong("source_id"));
    file.setSourceType(rs.getString("source_type"));
    file.setFileName(rs.getString("file_name"));
    file.setFilePath(rs.getString("file_path"));
    file.setFileExt(rs.getString("file_ext"));
    long createdBy = rs.getLong("created_by");
    file.setCreatedBy(rs.wasNull() ? null : createdBy);
    file.setCreatedDate(rs.getTimestamp("created_date") == null
            ? null : rs.getTimestamp("created_date").toLocalDateTime());
    file.setStaffName(rs.getString("staff_name"));
    file.setCreatorName(rs.getString("creator_name"));
    return file;
  }
}
