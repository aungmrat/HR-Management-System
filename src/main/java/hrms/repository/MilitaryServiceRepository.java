package hrms.repository;

import hrms.config.DatabaseConfig;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Repository for {@link hrms.model.MilitaryService} entity.
 */
public class MilitaryServiceRepository {

    private MilitaryServiceRepository() {
    }

    /** Minimal staff entry for the dialog's staff combo. */
    public record StaffOption(long id, String code, String name) {

        /** Label shown in the combo ({@code CODE — Name}). */
        public String label() {
            return code == null || code.isBlank() ? name : code + " — " + name;
        }
    }

    /**
     * Table rows for the Military Service list:
     * ID, Staff, Batch No, Service No, Status.
     *
     * @param keyword free-text match on staff name/code, batch or service no
     */
    public static List<Object[]> searchAsTableRows(String keyword) {
        String term = keyword == null ? "" : keyword.trim();
        String sql = """
            SELECT ms.id,
                   CONCAT(COALESCE(s.code, ''), ' \u2014 ', s.name) AS staff_label,
                   ms.batch_no, ms.service_no, ms.status
            FROM military_service ms
            JOIN staff s ON s.id = ms.staff_id
            WHERE ms.is_deleted = 0
            """;
        List<String> params = new ArrayList<>();
        if (!term.isEmpty()) {
            sql += """
                 AND (LOWER(COALESCE(s.name, '')) LIKE ?
                      OR LOWER(COALESCE(s.code, '')) LIKE ?
                      OR LOWER(COALESCE(ms.batch_no, '')) LIKE ?
                      OR LOWER(COALESCE(ms.service_no, '')) LIKE ?)
                """;
            String like = "%" + term.toLowerCase(Locale.ROOT) + "%";
            params.add(like);
            params.add(like);
            params.add(like);
            params.add(like);
        }
        sql += " ORDER BY s.name, ms.id";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) {
                ps.setString(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<Object[]> rows = new ArrayList<>();
                while (rs.next()) {
                    rows.add(new Object[]{
                        rs.getLong("id"),
                        rs.getString("staff_label"),
                        rs.getString("batch_no"),
                        rs.getString("service_no"),
                        rs.getString("status")
                    });
                }
                return rows;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load military service rows", e);
        }
    }

    /** Active staff entries for the dialog's staff combo, ordered by name. */
    public static List<StaffOption> listStaffOptions() {
        String sql = """
            SELECT id, code, name FROM staff
            WHERE is_deleted = 0
            ORDER BY name
            """;
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<StaffOption> list = new ArrayList<>();
            while (rs.next()) {
                list.add(new StaffOption(rs.getLong("id"), rs.getString("code"), rs.getString("name")));
            }
            return list;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load staff options", e);
        }
    }

    /**
     * Finds a military service record by ID (non-deleted).
     */
    public static Optional<hrms.model.MilitaryService> findById(long id) {
        String sql = """
            SELECT id, staff_id, batch_no, service_no, status,
                   created_by, created_date, modified_by, modified_date,
                   is_deleted, deleted_by, deleted_date
            FROM military_service
            WHERE id = ? AND is_deleted = 0
            """;
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find military service " + id, e);
        }
    }

    /**
     * Inserts a new military service record and returns the generated ID.
     */
    public static long insert(hrms.model.MilitaryService ms) {
        String sql = """
            INSERT INTO military_service
                (staff_id, batch_no, service_no, status,
                 created_by, created_date, modified_by, modified_date, is_deleted)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0)
            """;
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, ms.getStaffId());
            ps.setString(2, ms.getBatchNo());
            ps.setString(3, ms.getServiceNo());
            ps.setString(4, ms.getStatus());
            ps.setObject(5, ms.getCreatedBy());
            ps.setTimestamp(6, Timestamp.valueOf(LocalDateTime.now()));
            ps.setObject(7, ms.getCreatedBy());
            ps.setTimestamp(8, Timestamp.valueOf(LocalDateTime.now()));
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
                throw new RuntimeException("Failed to get generated ID for military service");
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to insert military service", e);
        }
    }

    /**
     * Updates an existing military service record.
     */
    public static void update(hrms.model.MilitaryService ms, long modifiedBy) {
        String sql = """
            UPDATE military_service
            SET staff_id = ?, batch_no = ?, service_no = ?, status = ?,
                modified_by = ?, modified_date = ?
            WHERE id = ? AND is_deleted = 0
            """;
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, ms.getStaffId());
            ps.setString(2, ms.getBatchNo());
            ps.setString(3, ms.getServiceNo());
            ps.setString(4, ms.getStatus());
            ps.setLong(5, modifiedBy);
            ps.setTimestamp(6, Timestamp.valueOf(LocalDateTime.now()));
            ps.setLong(7, ms.getId());
            int affected = ps.executeUpdate();
            if (affected == 0) {
                throw new RuntimeException("Military service not found or already deleted: " + ms.getId());
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update military service " + ms.getId(), e);
        }
    }

    /**
     * Soft-deletes a military service record.
     */
    public static void softDelete(long id, long deletedBy) {
        String sql = """
            UPDATE military_service
            SET is_deleted = 1, deleted_by = ?, deleted_date = ?
            WHERE id = ? AND is_deleted = 0
            """;
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, deletedBy);
            ps.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now()));
            ps.setLong(3, id);
            int affected = ps.executeUpdate();
            if (affected == 0) {
                throw new RuntimeException("Military service not found or already deleted: " + id);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete military service " + id, e);
        }
    }

    private static hrms.model.MilitaryService mapRow(ResultSet rs) throws SQLException {
        hrms.model.MilitaryService ms = new hrms.model.MilitaryService();
        ms.setId(rs.getLong("id"));
        ms.setStaffId(rs.getLong("staff_id"));
        ms.setBatchNo(rs.getString("batch_no"));
        ms.setServiceNo(rs.getString("service_no"));
        ms.setStatus(rs.getString("status"));
        ms.setCreatedBy(rs.getObject("created_by", Long.class));
        Timestamp cd = rs.getTimestamp("created_date");
        if (cd != null) ms.setCreatedDate(cd.toLocalDateTime());
        ms.setModifiedBy(rs.getObject("modified_by", Long.class));
        Timestamp md = rs.getTimestamp("modified_date");
        if (md != null) ms.setModifiedDate(md.toLocalDateTime());
        ms.setDeleted(rs.getBoolean("is_deleted"));
        ms.setDeletedBy(rs.getObject("deleted_by", Long.class));
        Timestamp dd = rs.getTimestamp("deleted_date");
        if (dd != null) ms.setDeletedDate(dd.toLocalDateTime());
        return ms;
    }
}
