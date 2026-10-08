package hrms.repository;

import hrms.config.DatabaseConfig;
import hrms.model.LanguageProficiency;
import hrms.model.LanguageSkill;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link LanguageSkill} entity.
 */
public class LanguageSkillRepository {

    private LanguageSkillRepository() {
    }

    /**
     * Finds all language skills for a staff member (non-deleted).
     */
    public static List<LanguageSkill> findByStaffId(long staffId) {
        String sql = """
            SELECT id, staff_id, language_name, speaking, listening, writing, reading,
                   created_by, created_date, modified_by, modified_date,
                   is_deleted, deleted_by, deleted_date
            FROM language_skill
            WHERE staff_id = ? AND is_deleted = 0
            ORDER BY id
            """;
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, staffId);
            try (ResultSet rs = ps.executeQuery()) {
                List<LanguageSkill> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
                return list;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load language skills for staff " + staffId, e);
        }
    }

    /** Minimal staff entry for the dialog's staff combo. */
    public record StaffOption(long id, String code, String name) {

        /** Label shown in the combo ({@code CODE — Name}). */
        public String label() {
            return code == null || code.isBlank() ? name : code + " — " + name;
        }
    }

    /**
     * Table rows for the Language Skill list:
     * ID, Staff, Language, Speaking, Listening, Writing, Reading.
     *
     * @param keyword free-text match on staff name/code or language name
     */
    public static List<Object[]> searchAsTableRows(String keyword) {
        String term = keyword == null ? "" : keyword.trim();
        String sql = """
            SELECT ls.id,
                   CONCAT(COALESCE(s.code, ''), ' \u2014 ', s.name) AS staff_label,
                   ls.language_name, ls.speaking, ls.listening, ls.writing, ls.reading
            FROM language_skill ls
            JOIN staff s ON s.id = ls.staff_id
            WHERE ls.is_deleted = 0
            """;
        List<Object> params = new ArrayList<>();
        if (!term.isEmpty()) {
            sql += """
                 AND (LOWER(COALESCE(s.name, '')) LIKE ?
                      OR LOWER(COALESCE(s.code, '')) LIKE ?
                      OR LOWER(ls.language_name) LIKE ?)
                """;
            String like = "%" + term.toLowerCase(java.util.Locale.ROOT) + "%";
            params.add(like);
            params.add(like);
            params.add(like);
        }
        sql += " ORDER BY s.name, ls.language_name";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) {
                ps.setString(i + 1, (String) params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<Object[]> rows = new ArrayList<>();
                while (rs.next()) {
                    rows.add(new Object[]{
                        rs.getLong("id"),
                        rs.getString("staff_label"),
                        rs.getString("language_name"),
                        proficiencyLabel(rs.getString("speaking")),
                        proficiencyLabel(rs.getString("listening")),
                        proficiencyLabel(rs.getString("writing")),
                        proficiencyLabel(rs.getString("reading"))
                    });
                }
                return rows;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load language skill rows", e);
        }
    }

    /** Display label for a stored proficiency code; blank when unset. */
    private static String proficiencyLabel(String code) {
        if (code == null || code.isBlank()) {
            return "";
        }
        try {
            return LanguageProficiency.valueOf(code).getLabel();
        } catch (IllegalArgumentException e) {
            return code;
        }
    }

    /** True when {@code staffId} already has a skill with this language name. */
    public static boolean existsForStaff(long staffId, String languageName, Long excludeId) {
        String sql = """
            SELECT COUNT(*) FROM language_skill
            WHERE staff_id = ? AND LOWER(language_name) = LOWER(?)
              AND is_deleted = 0
            """;
        List<Object> params = new ArrayList<>();
        params.add(staffId);
        params.add(languageName);
        if (excludeId != null) {
            sql += " AND id <> ?";
            params.add(excludeId);
        }
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, params.get(0));
            ps.setObject(2, params.get(1));
            if (params.size() > 2) {
                ps.setObject(3, params.get(2));
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getLong(1) > 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to check language skill duplicate", e);
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
     * Finds a language skill by ID (non-deleted).
     */
    public static Optional<LanguageSkill> findById(long id) {
        String sql = """
            SELECT id, staff_id, language_name, speaking, listening, writing, reading,
                   created_by, created_date, modified_by, modified_date,
                   is_deleted, deleted_by, deleted_date
            FROM language_skill
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
            throw new RuntimeException("Failed to find language skill " + id, e);
        }
    }

    /**
     * Inserts a new language skill and returns the generated ID.
     */
    public static long insert(LanguageSkill ls) {
        String sql = """
            INSERT INTO language_skill
                (staff_id, language_name, speaking, listening, writing, reading,
                 created_by, created_date, modified_by, modified_date, is_deleted)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
            """;
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, ls.getStaffId());
            ps.setString(2, ls.getLanguageName());
            ps.setString(3, ls.getSpeaking().name());
            ps.setString(4, ls.getListening().name());
            ps.setString(5, ls.getWriting().name());
            ps.setString(6, ls.getReading().name());
            ps.setLong(7, ls.getCreatedBy());
            ps.setTimestamp(8, Timestamp.valueOf(LocalDateTime.now()));
            ps.setLong(9, ls.getCreatedBy());
            ps.setTimestamp(10, Timestamp.valueOf(LocalDateTime.now()));
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
                throw new RuntimeException("Failed to get generated ID for language skill");
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to insert language skill", e);
        }
    }

    /**
     * Updates an existing language skill.
     */
    public static void update(LanguageSkill ls, long modifiedBy) {
        String sql = """
            UPDATE language_skill
            SET language_name = ?, speaking = ?, listening = ?, writing = ?, reading = ?,
                modified_by = ?, modified_date = ?
            WHERE id = ? AND is_deleted = 0
            """;
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, ls.getLanguageName());
            ps.setString(2, ls.getSpeaking().name());
            ps.setString(3, ls.getListening().name());
            ps.setString(4, ls.getWriting().name());
            ps.setString(5, ls.getReading().name());
            ps.setLong(6, modifiedBy);
            ps.setTimestamp(7, Timestamp.valueOf(LocalDateTime.now()));
            ps.setLong(8, ls.getId());
            int affected = ps.executeUpdate();
            if (affected == 0) {
                throw new RuntimeException("Language skill not found or already deleted: " + ls.getId());
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update language skill " + ls.getId(), e);
        }
    }

    /**
     * Soft-deletes a language skill.
     */
    public static void softDelete(long id, long deletedBy) {
        String sql = """
            UPDATE language_skill
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
                throw new RuntimeException("Language skill not found or already deleted: " + id);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete language skill " + id, e);
        }
    }

    /**
     * Synchronizes language skills for a staff member.
     * Deletes removed skills, updates changed ones, inserts new ones.
     */
    public static void sync(long staffId, List<LanguageSkill> skills, long userId) {
        List<LanguageSkill> existing = findByStaffId(staffId);
        List<Long> keptIds = new ArrayList<>();

        for (LanguageSkill skill : skills) {
            if (skill.getId() > 0) {
                // Existing skill - update if changed
                Optional<LanguageSkill> existingOpt = existing.stream()
                        .filter(e -> e.getId() == skill.getId())
                        .findFirst();
                if (existingOpt.isPresent()) {
                    LanguageSkill old = existingOpt.get();
                    if (!old.equalsData(skill)) {
                        skill.setModifiedBy(userId);
                        update(skill, userId);
                    }
                    keptIds.add(skill.getId());
                }
            } else {
                // New skill - insert
                skill.setCreatedBy(userId);
                long newId = insert(skill);
                skill.setId(newId);
                keptIds.add(newId);
            }
        }

        // Soft-delete removed skills
        for (LanguageSkill old : existing) {
            if (!keptIds.contains(old.getId())) {
                softDelete(old.getId(), userId);
            }
        }
    }

    private static LanguageSkill mapRow(ResultSet rs) throws SQLException {
        LanguageSkill ls = new LanguageSkill();
        ls.setId(rs.getLong("id"));
        ls.setStaffId(rs.getLong("staff_id"));
        ls.setLanguageName(rs.getString("language_name"));
        ls.setSpeaking(LanguageProficiency.valueOf(rs.getString("speaking")));
        ls.setListening(LanguageProficiency.valueOf(rs.getString("listening")));
        ls.setWriting(LanguageProficiency.valueOf(rs.getString("writing")));
        ls.setReading(LanguageProficiency.valueOf(rs.getString("reading")));
        ls.setCreatedBy(rs.getObject("created_by", Long.class));
        Timestamp cd = rs.getTimestamp("created_date");
        if (cd != null) ls.setCreatedDate(cd.toLocalDateTime());
        ls.setModifiedBy(rs.getObject("modified_by", Long.class));
        Timestamp md = rs.getTimestamp("modified_date");
        if (md != null) ls.setModifiedDate(md.toLocalDateTime());
        ls.setDeleted(rs.getBoolean("is_deleted"));
        ls.setDeletedBy(rs.getObject("deleted_by", Long.class));
        Timestamp dd = rs.getTimestamp("deleted_date");
        if (dd != null) ls.setDeletedDate(dd.toLocalDateTime());
        return ls;
    }
}