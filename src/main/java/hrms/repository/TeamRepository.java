package hrms.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import hrms.exception.BusinessException;
import hrms.model.Team;

/**
 * JDBC access for the {@code team} table (soft-deleted rows excluded).
 *
 * <p>A team is referenced by {@code staff.team_id}, by {@code team_member.team_id}
 * and by child teams ({@code team.parent_id}), so soft-delete is blocked while
 * any of those still point at it. The table has no unique key on {@code name},
 * so duplicate names are enforced here in code.</p>
 */
public final class TeamRepository {

  private TeamRepository() {
  }

  public static List<Object[]> findAllAsTableRows() {
    return searchAsTableRows(null, null, null);
  }

  /**
   * Table rows for the Team list:
   * ID, Name, Department, Office, Parent Team, Members, Remark, Created By,
   * Created Date.
   *
   * @param keyword      free-text match on team name or remark
   * @param departmentId exact department id, null for no filter
   * @param officeId     exact office id, null for no filter
   */
  public static List<Object[]> searchAsTableRows(String keyword, Long departmentId, Long officeId) {
    return toTableRows(search(keyword, departmentId, officeId));
  }

  public static List<Team> listAll() {
    return search(null, null, null);
  }

  public static List<Team> search(String keyword, Long departmentId, Long officeId) {
    String term = keyword == null ? "" : keyword.trim();
    StringBuilder sql = new StringBuilder(SELECT_SQL);
    List<Object> params = new ArrayList<>();
    if (!term.isEmpty()) {
      String like = "%" + term.toLowerCase(Locale.ROOT) + "%";
      sql.append(" AND (LOWER(t.name) LIKE ? OR LOWER(t.remark) LIKE ?)");
      params.add(like);
      params.add(like);
    }
    if (departmentId != null) {
      sql.append(" AND t.department_id = ?");
      params.add(departmentId);
    }
    if (officeId != null) {
      sql.append(" AND t.office_id = ?");
      params.add(officeId);
    }
    sql.append(" ORDER BY t.id DESC");
    return Sql.query(sql.toString(), TeamRepository::mapRow, params.toArray());
  }

  public static Optional<Team> findById(long id) {
    return Sql.queryOne(SELECT_SQL + " AND t.id = ?", TeamRepository::mapRow, id);
  }

  /** True when another non-deleted row already uses {@code name}. */
  public static boolean existsName(String name, Long excludeId) {
    String trimmed = name == null ? "" : name.trim();
    if (excludeId == null) {
      return Sql.queryOne(
              "SELECT 1 FROM `team` WHERE name = ? AND is_deleted = FALSE",
              rs -> 1L,
              trimmed).isPresent();
    }
    return Sql.queryOne(
            "SELECT 1 FROM `team` WHERE name = ? AND id <> ? AND is_deleted = FALSE",
            rs -> 1L,
            trimmed, excludeId).isPresent();
  }

  /**
   * Inserts a team and returns the generated id.
   *
   * @throws BusinessException when required fields are invalid or the name is
   *                           already used
   */
  public static long insert(Team team) {
    if (team == null) {
      throw new IllegalArgumentException("team is required");
    }
    requireScope(team);
    String name = requireName(team);
    requireRemark(team);
    if (existsName(name, null)) {
      throw duplicateName(name);
    }
    return Sql.insert("""
            INSERT INTO `team`
              (department_id, office_id, parent_id, name, remark,
               created_by, created_date, is_deleted)
            VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, FALSE)
            """,
            team.getDepartmentId(),
            team.getOfficeId(),
            team.getParentId(),
            name,
            blankToNull(team.getRemark()),
            team.getCreatedBy());
  }

  /**
   * Updates editable fields; returns true when a row was changed.
   *
   * @throws BusinessException when required fields are invalid, the name is
   *                           already used by another row, or the new parent
   *                           would create a cycle
   */
  public static boolean update(Team team) {
    if (team == null || team.getId() <= 0) {
      throw new IllegalArgumentException("team id is required");
    }
    requireScope(team);
    String name = requireName(team);
    requireRemark(team);
    if (existsName(name, team.getId())) {
      throw duplicateName(name);
    }
    requireNoParentCycle(team.getId(), team.getParentId());
    int updated = Sql.update("""
            UPDATE `team`
               SET department_id = ?,
                   office_id = ?,
                   parent_id = ?,
                   name = ?,
                   remark = ?,
                   modified_by = ?,
                   modified_date = CURRENT_TIMESTAMP
             WHERE id = ? AND is_deleted = FALSE
            """,
            team.getDepartmentId(),
            team.getOfficeId(),
            team.getParentId(),
            name,
            blankToNull(team.getRemark()),
            team.getModifiedBy(),
            team.getId());
    return updated > 0;
  }

  /**
   * Soft-deletes one row; returns true when a row was marked deleted.
   *
   * @throws BusinessException when staff, a team member or a sub-team still
   *                           references the team
   */
  public static boolean softDelete(long id, Long deletedBy) {
    if (id <= 0) {
      throw new IllegalArgumentException("id is required");
    }
    if (isInUse(id)) {
      throw new BusinessException(
              "Team is in use by staff, team members or sub-teams and cannot be deleted.");
    }
    int updated = Sql.update("""
            UPDATE `team`
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
          SELECT t.id, t.department_id, t.office_id, t.parent_id, t.name, t.remark,
                 t.created_by, t.created_date,
                 d.name AS department_name,
                 o.name AS office_name,
                 pt.name AS parent_name,
                 (SELECT COALESCE(st.name, u.username)
                    FROM `user` u
                    LEFT JOIN staff st ON st.id = u.staff_id
                   WHERE u.id = t.created_by) AS creator_name,
                 (SELECT COUNT(*)
                    FROM team_member tm
                   WHERE tm.team_id = t.id AND tm.is_deleted = FALSE) AS member_count
            FROM `team` t
            LEFT JOIN department d ON d.id = t.department_id
            LEFT JOIN office o ON o.id = t.office_id
            LEFT JOIN `team` pt ON pt.id = t.parent_id
           WHERE t.is_deleted = FALSE
          """;

  /**
   * True while a staff record, an active membership row or an active child
   * team still references the team.
   */
  private static boolean isInUse(long id) {
    return Sql.queryOne(
            "SELECT 1 FROM staff WHERE team_id = ? LIMIT 1",
            rs -> 1L,
            id).isPresent()
            || Sql.queryOne(
            "SELECT 1 FROM team_member WHERE team_id = ? AND is_deleted = FALSE LIMIT 1",
            rs -> 1L,
            id).isPresent()
            || Sql.queryOne(
            "SELECT 1 FROM `team` WHERE parent_id = ? AND is_deleted = FALSE LIMIT 1",
            rs -> 1L,
            id).isPresent();
  }

  /**
   * Blocks a parent choice that would make the team its own ancestor
   * (self or any team below it in the hierarchy).
   */
  private static void requireNoParentCycle(long teamId, Long parentId) {
    if (parentId == null) {
      return;
    }
    if (parentId == teamId) {
      throw new BusinessException("A team cannot be its own parent.");
    }
    Long cursor = parentId;
    int guard = 0;
    while (cursor != null && guard++ < 100) {
      if (cursor == teamId) {
        throw new BusinessException("Parent team would create a hierarchy cycle.");
      }
      final Long current = cursor;
      cursor = Sql.queryOne(
              "SELECT parent_id FROM `team` WHERE id = ? AND is_deleted = FALSE",
              rs -> {
                long value = rs.getLong("parent_id");
                return rs.wasNull() ? null : value;
              },
              current).orElse(null);
    }
  }

  private static Team mapRow(ResultSet rs) throws SQLException {
    long departmentId = rs.getLong("department_id");
    boolean departmentNull = rs.wasNull();
    long officeId = rs.getLong("office_id");
    boolean officeNull = rs.wasNull();
    long parentId = rs.getLong("parent_id");
    boolean parentNull = rs.wasNull();
    long createdBy = rs.getLong("created_by");
    boolean createdByNull = rs.wasNull();
    Team team = new Team(
            rs.getLong("id"),
            departmentNull ? null : departmentId,
            officeNull ? null : officeId,
            parentNull ? null : parentId,
            rs.getString("name"),
            rs.getString("remark"),
            createdByNull ? null : createdBy,
            rs.getTimestamp("created_date") == null
                    ? null : rs.getTimestamp("created_date").toLocalDateTime());
    team.setDepartmentName(rs.getString("department_name"));
    team.setOfficeName(rs.getString("office_name"));
    team.setParentName(rs.getString("parent_name"));
    team.setCreatorName(rs.getString("creator_name"));
    team.setMemberCount(rs.getLong("member_count"));
    return team;
  }

  private static List<Object[]> toTableRows(List<Team> teams) {
    List<Object[]> tableRows = new ArrayList<>(teams.size());
    for (Team team : teams) {
      tableRows.add(team.toTableRow());
    }
    return tableRows;
  }

  private static void requireScope(Team team) {
    if (team.getDepartmentId() == null || team.getDepartmentId() <= 0) {
      throw new BusinessException("Department is required.");
    }
    if (team.getOfficeId() == null || team.getOfficeId() <= 0) {
      throw new BusinessException("Office is required.");
    }
  }

  private static String requireName(Team team) {
    String name = team.getName() == null ? "" : team.getName().trim();
    if (name.isEmpty()) {
      throw new BusinessException("Team name is required.");
    }
    if (name.length() > 100) {
      throw new BusinessException("Team name must be 100 characters or fewer.");
    }
    return name;
  }

  private static void requireRemark(Team team) {
    String remark = team.getRemark();
    if (remark != null && remark.trim().length() > 500) {
      throw new BusinessException("Remark must be 500 characters or fewer.");
    }
  }

  private static BusinessException duplicateName(String name) {
    return new BusinessException("Team '" + name + "' already exists.");
  }

  private static String blankToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
