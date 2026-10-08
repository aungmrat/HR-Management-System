package hrms.repository;

import hrms.config.DatabaseConfig;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Repository for {@link hrms.model.FamilyMember} entity.
 */
public class FamilyMemberRepository {

    private FamilyMemberRepository() {
    }

    /** Minimal staff entry for the dialog's staff combo. */
    public record StaffOption(long id, String code, String name) {

        /** Label shown in the combo ({@code CODE — Name}). */
        public String label() {
            return code == null || code.isBlank() ? name : code + " — " + name;
        }
    }

    /**
     * Table rows for the Family Member list:
     * ID, Staff, Name, Relationship, DOB, Phone.
     *
     * @param keyword free-text match on staff name/code, member name,
     *                relationship or phone
     */
    public static List<Object[]> searchAsTableRows(String keyword) {
        String term = keyword == null ? "" : keyword.trim();
        String sql = """
            SELECT fm.id,
                   CONCAT(COALESCE(s.code, ''), ' \u2014 ', s.name) AS staff_label,
                   fm.name, fm.relationship, fm.dob, fm.phone
            FROM family_member fm
            JOIN staff s ON s.id = fm.staff_id
            WHERE fm.is_deleted = 0
            """;
        List<String> params = new ArrayList<>();
        if (!term.isEmpty()) {
            sql += """
                 AND (LOWER(COALESCE(s.name, '')) LIKE ?
                      OR LOWER(COALESCE(s.code, '')) LIKE ?
                      OR LOWER(COALESCE(fm.name, '')) LIKE ?
                      OR LOWER(COALESCE(fm.relationship, '')) LIKE ?
                      OR LOWER(COALESCE(fm.phone, '')) LIKE ?)
                """;
            String like = "%" + term.toLowerCase(Locale.ROOT) + "%";
            params.add(like);
            params.add(like);
            params.add(like);
            params.add(like);
            params.add(like);
        }
        sql += " ORDER BY s.name, fm.id";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) {
                ps.setString(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<Object[]> rows = new ArrayList<>();
                while (rs.next()) {
                    Date dob = rs.getDate("dob");
                    rows.add(new Object[]{
                        rs.getLong("id"),
                        rs.getString("staff_label"),
                        rs.getString("name"),
                        rs.getString("relationship"),
                        dob == null ? null : dob.toLocalDate(),
                        rs.getString("phone")
                    });
                }
                return rows;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load family member rows", e);
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
     * Finds a family member by ID (non-deleted).
     */
    public static Optional<hrms.model.FamilyMember> findById(long id) {
        String sql = """
            SELECT id, staff_id, name, relationship, dob, address, phone,
                   created_by, created_date, modified_by, modified_date,
                   is_deleted, deleted_by, deleted_date
            FROM family_member
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
            throw new RuntimeException("Failed to find family member " + id, e);
        }
    }

    /**
     * Inserts a new family member and returns the generated ID.
     */
    public static long insert(hrms.model.FamilyMember member) {
        String sql = """
            INSERT INTO family_member
                (staff_id, name, relationship, dob, address, phone,
                 created_by, created_date, modified_by, modified_date, is_deleted)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
            """;
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, member.getStaffId());
            ps.setString(2, member.getName());
            ps.setString(3, member.getRelationship());
            if (member.getDob() == null) {
                ps.setNull(4, Types.DATE);
            } else {
                ps.setDate(4, Date.valueOf(member.getDob()));
            }
            ps.setString(5, member.getAddress());
            ps.setString(6, member.getPhone());
            ps.setObject(7, member.getCreatedBy());
            ps.setTimestamp(8, Timestamp.valueOf(LocalDateTime.now()));
            ps.setObject(9, member.getCreatedBy());
            ps.setTimestamp(10, Timestamp.valueOf(LocalDateTime.now()));
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
                throw new RuntimeException("Failed to get generated ID for family member");
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to insert family member", e);
        }
    }

    /**
     * Updates an existing family member.
     */
    public static void update(hrms.model.FamilyMember member, long modifiedBy) {
        String sql = """
            UPDATE family_member
            SET staff_id = ?, name = ?, relationship = ?, dob = ?, address = ?, phone = ?,
                modified_by = ?, modified_date = ?
            WHERE id = ? AND is_deleted = 0
            """;
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, member.getStaffId());
            ps.setString(2, member.getName());
            ps.setString(3, member.getRelationship());
            if (member.getDob() == null) {
                ps.setNull(4, Types.DATE);
            } else {
                ps.setDate(4, Date.valueOf(member.getDob()));
            }
            ps.setString(5, member.getAddress());
            ps.setString(6, member.getPhone());
            ps.setLong(7, modifiedBy);
            ps.setTimestamp(8, Timestamp.valueOf(LocalDateTime.now()));
            ps.setLong(9, member.getId());
            int affected = ps.executeUpdate();
            if (affected == 0) {
                throw new RuntimeException("Family member not found or already deleted: " + member.getId());
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update family member " + member.getId(), e);
        }
    }

    /**
     * Soft-deletes a family member.
     */
    public static void softDelete(long id, long deletedBy) {
        String sql = """
            UPDATE family_member
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
                throw new RuntimeException("Family member not found or already deleted: " + id);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete family member " + id, e);
        }
    }

    private static hrms.model.FamilyMember mapRow(ResultSet rs) throws SQLException {
        hrms.model.FamilyMember member = new hrms.model.FamilyMember();
        member.setId(rs.getLong("id"));
        member.setStaffId(rs.getLong("staff_id"));
        member.setName(rs.getString("name"));
        member.setRelationship(rs.getString("relationship"));
        Date dob = rs.getDate("dob");
        if (dob != null) member.setDob(dob.toLocalDate());
        member.setAddress(rs.getString("address"));
        member.setPhone(rs.getString("phone"));
        member.setCreatedBy(rs.getObject("created_by", Long.class));
        Timestamp cd = rs.getTimestamp("created_date");
        if (cd != null) member.setCreatedDate(cd.toLocalDateTime());
        member.setModifiedBy(rs.getObject("modified_by", Long.class));
        Timestamp md = rs.getTimestamp("modified_date");
        if (md != null) member.setModifiedDate(md.toLocalDateTime());
        member.setDeleted(rs.getBoolean("is_deleted"));
        member.setDeletedBy(rs.getObject("deleted_by", Long.class));
        Timestamp dd = rs.getTimestamp("deleted_date");
        if (dd != null) member.setDeletedDate(dd.toLocalDateTime());
        return member;
    }
}
