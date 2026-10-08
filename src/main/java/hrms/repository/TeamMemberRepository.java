package hrms.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import hrms.exception.BusinessException;
import hrms.model.TeamMember;

/**
 * JDBC access for the {@code team_member} table (soft-deleted rows excluded).
 *
 * <p>One row = one staff record's membership in one team. The table has no
 * unique key, so the "same staff in the same team" rule and the "parent must
 * belong to the same team" rule are enforced here in code.</p>
 */
public final class TeamMemberRepository {

  private TeamMemberRepository() {
  }

  /** Minimal staff entry for the dialog's staff combo. */
  public record StaffOption(long id, String code, String name) {

    /** Label shown in the combo ({@code CODE — Name}). */
    public String label() {
      return code == null || code.isBlank() ? name : code + " — " + name;
    }
  }

  public static List<Object[]> findAllAsTableRows() {
    return searchAsTableRows(null, null);
  }

  /**
   * Table rows for the Team Member list:
   * ID, Staff, Team, Parent, Remark, Created By, Created Date.
   *
   * @param keyword free-text match on staff name or remark
   * @param teamId  exact team id, null for no filter
   */
  public static List<Object[]> searchAsTableRows(String keyword, Long teamId) {
    return toTableRows(search(keyword, teamId));
  }

  public static List<TeamMember> listAll() {
    return search(null, null);
  }

  public static List<TeamMember> search(String keyword, Long teamId) {
    String term = keyword == null ? "" : keyword.trim();
    StringBuilder sql = new StringBuilder(SELECT_SQL);
    List<Object> params = new ArrayList<>();
    if (!term.isEmpty()) {
      String like = "%" + term.toLowerCase(Locale.ROOT) + "%";
      sql.append(" AND (LOWER(COALESCE(s.name, '')) LIKE ? OR LOWER(COALESCE(tm.remark, '')) LIKE ?)");
      params.add(like);
      params.add(like);
    }
    if (teamId != null) {
      sql.append(" AND tm.team_id = ?");
      params.add(teamId);
    }
    sql.append(" ORDER BY tm.id DESC");
    return Sql.query(sql.toString(), TeamMemberRepository::mapRow, params.toArray());
  }

  public static Optional<TeamMember> findById(long id) {
    return Sql.queryOne(SELECT_SQL + " AND tm.id = ?", TeamMemberRepository::mapRow, id);
  }

  /** Active memberships of one team (parent choices + staff filtering). */
  public static List<TeamMember> listByTeam(long teamId) {
    return Sql.query(SELECT_SQL + " AND tm.team_id = ? ORDER BY tm.id",
            TeamMemberRepository::mapRow, teamId);
  }

  /**
   * Staff that can still join {@code teamId}: everyone except those already
   * active members. In edit mode {@code keepStaffId} keeps the membership's
   * current staff selectable.
   */
  public static List<StaffOption> listStaffOptions(Long teamId, Long keepStaffId) {
    String sql = """
            SELECT s.id, s.code, s.name
              FROM staff s
             WHERE s.is_deleted = FALSE
               AND (s.id = ?
                    OR s.id NOT IN (SELECT tm.staff_id
                                      FROM team_member tm
                                     WHERE tm.team_id = ? AND tm.is_deleted = FALSE))
             ORDER BY s.name
            """;
    return Sql.query(sql,
            rs -> new StaffOption(rs.getLong("id"), rs.getString("code"), rs.getString("name")),
            keepStaffId == null ? -1L : keepStaffId,
            teamId == null ? -1L : teamId);
  }

  /** True when {@code staffId} already has an active membership in {@code teamId}. */
  public static boolean existsMembership(Long staffId, Long teamId, Long excludeId) {
    if (staffId == null || teamId == null) {
      return false;
    }
    if (excludeId == null) {
      return Sql.queryOne(
              "SELECT 1 FROM team_member WHERE staff_id = ? AND team_id = ? AND is_deleted = FALSE",
              rs -> 1L,
              staffId, teamId).isPresent();
    }
    return Sql.queryOne(
            "SELECT 1 FROM team_member WHERE staff_id = ? AND team_id = ? AND id <> ?"
                    + " AND is_deleted = FALSE",
            rs -> 1L,
            staffId, teamId, excludeId).isPresent();
  }

  /**
   * Inserts a membership and returns the generated id.
   *
   * @throws BusinessException when required fields are invalid, the staff is
   *                           already in the team, or the parent is invalid
   */
  public static long insert(TeamMember member) {
    if (member == null) {
      throw new IllegalArgumentException("member is required");
    }
    requireScope(member);
    requireRemark(member);
    if (existsMembership(member.getStaffId(), member.getTeamId(), null)) {
      throw duplicateMembership();
    }
    requireValidParent(null, member.getTeamId(), member.getParentId());
    return Sql.insert("""
            INSERT INTO team_member
              (staff_id, team_id, parent_id, remark,
               created_by, created_date, is_deleted)
            VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, FALSE)
            """,
            member.getStaffId(),
            member.getTeamId(),
            member.getParentId(),
            blankToNull(member.getRemark()),
            member.getCreatedBy());
  }

  /**
   * Updates editable fields; returns true when a row was changed.
   *
   * @throws BusinessException when required fields are invalid, the staff is
   *                           already in the team, the parent is invalid, or
   *                           the team changed while sub-members exist
   */
  public static boolean update(TeamMember member) {
    if (member == null || member.getId() <= 0) {
      throw new IllegalArgumentException("member id is required");
    }
    requireScope(member);
    requireRemark(member);
    if (existsMembership(member.getStaffId(), member.getTeamId(), member.getId())) {
      throw duplicateMembership();
    }
    TeamMember existing = findById(member.getId()).orElseThrow(
            () -> new BusinessException("Team member not found."));
    if (!Objects.equals(existing.getTeamId(), member.getTeamId()) && hasChildren(member.getId())) {
      throw new BusinessException(
              "Remove or move the sub-members before changing the team.");
    }
    requireValidParent(member.getId(), member.getTeamId(), member.getParentId());
    int updated = Sql.update("""
            UPDATE team_member
               SET staff_id = ?,
                   team_id = ?,
                   parent_id = ?,
                   remark = ?,
                   modified_by = ?,
                   modified_date = CURRENT_TIMESTAMP
             WHERE id = ? AND is_deleted = FALSE
            """,
            member.getStaffId(),
            member.getTeamId(),
            member.getParentId(),
            blankToNull(member.getRemark()),
            member.getModifiedBy(),
            member.getId());
    return updated > 0;
  }

  /**
   * Removes one membership (soft delete); returns true when a row was changed.
   *
   * @throws BusinessException when active sub-members still point at it
   */
  public static boolean softDelete(long id, Long deletedBy) {
    if (id <= 0) {
      throw new IllegalArgumentException("id is required");
    }
    if (hasChildren(id)) {
      throw new BusinessException(
              "Remove the member's sub-members first.");
    }
    int updated = Sql.update("""
            UPDATE team_member
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
          SELECT tm.id, tm.staff_id, tm.team_id, tm.parent_id, tm.remark,
                 tm.created_by, tm.created_date,
                 s.code AS staff_code,
                 s.name AS staff_name,
                 t.name AS team_name,
                 ps.name AS parent_name,
                 (SELECT COALESCE(st.name, u.username)
                    FROM `user` u
                    LEFT JOIN staff st ON st.id = u.staff_id
                   WHERE u.id = tm.created_by) AS creator_name
            FROM team_member tm
            LEFT JOIN staff s ON s.id = tm.staff_id
            LEFT JOIN `team` t ON t.id = tm.team_id
            LEFT JOIN team_member pm ON pm.id = tm.parent_id
            LEFT JOIN staff ps ON ps.id = pm.staff_id
           WHERE tm.is_deleted = FALSE
          """;

  /** True while an active sub-member still points at the membership. */
  private static boolean hasChildren(long id) {
    return Sql.queryOne(
            "SELECT 1 FROM team_member WHERE parent_id = ? AND is_deleted = FALSE LIMIT 1",
            rs -> 1L,
            id).isPresent();
  }

  /**
   * The parent must be an active membership of the same team and not the
   * membership itself.
   */
  private static void requireValidParent(Long memberId, Long teamId, Long parentId) {
    if (parentId == null) {
      return;
    }
    if (memberId != null && parentId.equals(memberId)) {
      throw new BusinessException("A member cannot be its own parent.");
    }
    Long parentTeamId = Sql.queryOne(
            "SELECT team_id FROM team_member WHERE id = ? AND is_deleted = FALSE",
            rs -> rs.getLong("team_id"),
            parentId).orElse(null);
    if (parentTeamId == null) {
      throw new BusinessException("Parent member not found.");
    }
    if (!parentTeamId.equals(teamId)) {
      throw new BusinessException("Parent member must belong to the same team.");
    }
  }

  private static void requireScope(TeamMember member) {
    if (member.getStaffId() == null || member.getStaffId() <= 0) {
      throw new BusinessException("Staff is required.");
    }
    if (member.getTeamId() == null || member.getTeamId() <= 0) {
      throw new BusinessException("Team is required.");
    }
  }

  private static void requireRemark(TeamMember member) {
    String remark = member.getRemark();
    if (remark != null && remark.trim().length() > 500) {
      throw new BusinessException("Remark must be 500 characters or fewer.");
    }
  }

  private static TeamMember mapRow(ResultSet rs) throws SQLException {
    long staffId = rs.getLong("staff_id");
    boolean staffNull = rs.wasNull();
    long teamId = rs.getLong("team_id");
    boolean teamNull = rs.wasNull();
    long parentId = rs.getLong("parent_id");
    boolean parentNull = rs.wasNull();
    long createdBy = rs.getLong("created_by");
    boolean createdByNull = rs.wasNull();
    TeamMember member = new TeamMember(
            rs.getLong("id"),
            staffNull ? null : staffId,
            teamNull ? null : teamId,
            parentNull ? null : parentId,
            rs.getString("remark"),
            createdByNull ? null : createdBy,
            rs.getTimestamp("created_date") == null
                    ? null : rs.getTimestamp("created_date").toLocalDateTime());
    member.setStaffCode(rs.getString("staff_code"));
    member.setStaffName(rs.getString("staff_name"));
    member.setTeamName(rs.getString("team_name"));
    member.setParentName(rs.getString("parent_name"));
    member.setCreatorName(rs.getString("creator_name"));
    return member;
  }

  private static List<Object[]> toTableRows(List<TeamMember> members) {
    List<Object[]> tableRows = new ArrayList<>(members.size());
    for (TeamMember member : members) {
      tableRows.add(member.toTableRow());
    }
    return tableRows;
  }

  private static BusinessException duplicateMembership() {
    return new BusinessException("That staff record is already a member of this team.");
  }

  private static String blankToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
