package hrms.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import hrms.exception.BusinessException;
import hrms.exception.DataAccessException;
import hrms.security.PasswordHasher;

/**
 * JDBC access for the user management screens: listing accounts with their
 * staff person and system role, creating and updating accounts, password
 * resets and lockout maintenance. Password hashes are only written through
 * {@link #insert} / {@link #resetPassword} and are never logged or selected
 * into table rows.
 */
public final class UserManagementRepository {

  private UserManagementRepository() {
  }

  private static final DateTimeFormatter LAST_LOGIN_FMT =
          DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

  /** Role row for the System Role combo. */
  public record RoleOption(long id, String name) {
    @Override
    public String toString() {
      return name;
    }
  }

  /** Staff row for the Staff combo; {@code display} is "CODE - Name". */
  public record StaffOption(long id, String display) {
    @Override
    public String toString() {
      return display;
    }
  }

  /** Account row used by the edit dialog (no password data). */
  public record UserRow(long id, long staffId, String staffName, long roleId,
          String username, String email, String status) {
  }

  // ------------------------------------------------------------------
  // Reads
  // ------------------------------------------------------------------

  /**
   * Table rows for the User list:
   * ID, Username, Full Name, Email, Role, Status, Locked, Last Login.
   *
   * @param keyword   free-text match on username, email or staff name
   * @param roleFilter exact system role name, blank or "All Roles" for no filter
   */
  public static List<Object[]> searchAsTableRows(String keyword, String roleFilter) {
    String term = keyword == null ? "" : keyword.trim();
    String role = roleFilter == null ? "" : roleFilter.trim();
    boolean hasRole = !role.isEmpty() && !"All Roles".equals(role);
    StringBuilder sql = new StringBuilder("""
            SELECT u.id, u.username, s.name AS full_name, u.email, sr.name AS role_name,
                   u.status, u.locked_date, u.last_login_date
              FROM `user` u
              JOIN staff s ON s.id = u.staff_id
              JOIN system_role sr ON sr.id = u.system_role_id
             WHERE u.is_deleted = 0
            """);
    List<Object> params = new ArrayList<>();
    if (!term.isEmpty()) {
      String like = "%" + term.toLowerCase(Locale.ROOT) + "%";
      sql.append(" AND (LOWER(u.username) LIKE ? OR LOWER(u.email) LIKE ? OR LOWER(s.name) LIKE ?)");
      params.add(like);
      params.add(like);
      params.add(like);
    }
    if (hasRole) {
      sql.append(" AND sr.name = ?");
      params.add(role);
    }
    sql.append(" ORDER BY u.id DESC");
    return Sql.query(sql.toString(), UserManagementRepository::mapTableRow, params.toArray());
  }

  private static Object[] mapTableRow(ResultSet rs) throws SQLException {
    LocalDateTime lockedDate = rs.getObject("locked_date", LocalDateTime.class);
    LocalDateTime lastLogin = rs.getObject("last_login_date", LocalDateTime.class);
    return new Object[]{
      rs.getLong("id"),
      rs.getString("username"),
      rs.getString("full_name"),
      rs.getString("email"),
      rs.getString("role_name"),
      rs.getString("status"),
      lockedDate == null ? "" : "Locked",
      lastLogin == null ? "" : lastLogin.format(LAST_LOGIN_FMT)
    };
  }

  /** Account details for the edit dialog, empty when unknown or deleted. */
  public static Optional<UserRow> findById(long id) {
    return Sql.queryOne("""
            SELECT u.id, u.staff_id, s.name AS full_name, u.system_role_id,
                   u.username, u.email, u.status
              FROM `user` u
              JOIN staff s ON s.id = u.staff_id
             WHERE u.id = ? AND u.is_deleted = 0
            """,
            rs -> new UserRow(
                    rs.getLong("id"),
                    rs.getLong("staff_id"),
                    rs.getString("full_name"),
                    rs.getLong("system_role_id"),
                    rs.getString("username"),
                    rs.getString("email"),
                    rs.getString("status")),
            id);
  }

  /** Active system roles for the System Role combo. */
  public static List<RoleOption> listRoles() {
    return Sql.query(
            "SELECT id, name FROM system_role WHERE is_deleted = 0 ORDER BY name",
            rs -> new RoleOption(rs.getLong("id"), rs.getString("name")));
  }

  /** Staff members that do not have a user account yet (create mode). */
  public static List<StaffOption> listStaffWithoutUser() {
    return Sql.query("""
            SELECT s.id, s.code, s.name
              FROM staff s
             WHERE s.is_deleted = 0
               AND NOT EXISTS (SELECT 1 FROM `user` u WHERE u.staff_id = s.id)
             ORDER BY s.name
            """,
            rs -> new StaffOption(rs.getLong("id"),
                    rs.getString("code") + " - " + rs.getString("name")));
  }

  public static boolean existsUsername(String username, Long excludeId) {
    return existsBy("username", username, excludeId);
  }

  public static boolean existsEmail(String email, Long excludeId) {
    return existsBy("email", email, excludeId);
  }

  private static boolean existsBy(String column, String value, Long excludeId) {
    String trimmed = value == null ? "" : value.trim();
    if (trimmed.isEmpty()) {
      return false;
    }
    if (excludeId == null) {
      return Sql.queryOne(
              "SELECT 1 FROM `user` WHERE " + column + " = ? AND is_deleted = 0",
              rs -> 1L, trimmed).isPresent();
    }
    return Sql.queryOne(
            "SELECT 1 FROM `user` WHERE " + column + " = ? AND id <> ? AND is_deleted = 0",
            rs -> 1L, trimmed, excludeId).isPresent();
  }

  /** Active HR_ADMIN accounts; used to guard against locking everyone out. */
  public static long countActiveAdmins() {
    return Sql.count("""
            SELECT COUNT(*)
              FROM `user` u
              JOIN system_role sr ON sr.id = u.system_role_id
             WHERE sr.name = 'HR_ADMIN' AND sr.is_deleted = 0
               AND u.status = 'Active' AND u.is_deleted = 0
            """);
  }

  // ------------------------------------------------------------------
  // Writes
  // ------------------------------------------------------------------

  /**
   * Creates an account with a forced password change on first sign-in.
   *
   * @return the generated user id
   * @throws BusinessException when the username or email is already taken
   */
  public static long insert(long staffId, long roleId, String username, String email,
          String plainPassword, String status, Long createdBy) {
    String user = requireText(username, "Username is required.");
    String mail = requireText(email, "Email is required.");
    String password = requireText(plainPassword, "Password is required.");
    if (existsUsername(user, null)) {
      throw new BusinessException("Username already taken.");
    }
    if (existsEmail(mail, null)) {
      throw new BusinessException("Email already taken.");
    }
    String normalizedStatus = status == null || status.isBlank() ? "Active" : status.trim();
    try {
      return Sql.insert("""
              INSERT INTO `user`
                (staff_id, system_role_id, username, email, password_hash,
                 status, must_change_password, created_by, created_date, is_deleted)
              VALUES (?, ?, ?, ?, ?, ?, 1, ?, CURRENT_TIMESTAMP, 0)
              """,
              staffId,
              roleId,
              user,
              mail,
              PasswordHasher.hash(password),
              normalizedStatus,
              createdBy);
    } catch (DataAccessException e) {
      if (isDuplicateKey(e)) {
        throw new BusinessException(
                "That staff member or email already has a user account.");
      }
      throw e;
    }
  }

  /** Updates profile fields; the staff link and password are never touched here. */
  public static boolean updateProfile(long id, long roleId, String username, String email,
          String status, Long modifiedBy) {
    String user = requireText(username, "Username is required.");
    String mail = requireText(email, "Email is required.");
    if (existsUsername(user, id)) {
      throw new BusinessException("Username already taken.");
    }
    if (existsEmail(mail, id)) {
      throw new BusinessException("Email already taken.");
    }
    String normalizedStatus = status == null || status.isBlank() ? "Active" : status.trim();
    int updated;
    try {
      updated = Sql.update("""
              UPDATE `user`
                 SET system_role_id = ?,
                     username = ?,
                     email = ?,
                     status = ?,
                     modified_by = ?,
                     modified_date = CURRENT_TIMESTAMP
               WHERE id = ? AND is_deleted = 0
              """,
              roleId, user, mail, normalizedStatus, modifiedBy, id);
    } catch (DataAccessException e) {
      if (isDuplicateKey(e)) {
        throw new BusinessException("Email already taken.");
      }
      throw e;
    }
    return updated > 0;
  }

  /** Sets a new password hash and forces a change on the next sign-in. */
  public static void resetPassword(long id, String plainPassword, Long modifiedBy) {
    String password = requireText(plainPassword, "Password is required.");
    Sql.update("""
            UPDATE `user`
               SET password_hash = ?,
                   must_change_password = 1,
                   modified_by = ?,
                   modified_date = CURRENT_TIMESTAMP
             WHERE id = ? AND is_deleted = 0
            """,
            PasswordHasher.hash(password), modifiedBy, id);
  }

  /** Clears the brute-force lockout for one account. */
  public static boolean unlock(long id, Long modifiedBy) {
    int updated = Sql.update("""
            UPDATE `user`
               SET failed_login_count = 0,
                   locked_date = NULL,
                   modified_by = ?,
                   modified_date = CURRENT_TIMESTAMP
             WHERE id = ? AND is_deleted = 0
            """,
            modifiedBy, id);
    return updated > 0;
  }

  /** Activates or deactivates an account (soft status change, never a delete). */
  public static boolean setStatus(long id, String status, Long modifiedBy) {
    String normalized = status == null || status.isBlank() ? "Active" : status.trim();
    int updated = Sql.update("""
            UPDATE `user`
               SET status = ?,
                   modified_by = ?,
                   modified_date = CURRENT_TIMESTAMP
             WHERE id = ? AND is_deleted = 0
            """,
            normalized, modifiedBy, id);
    return updated > 0;
  }

  // ------------------------------------------------------------------
  // Helpers
  // ------------------------------------------------------------------

  private static String requireText(String value, String message) {
    if (value == null || value.isBlank()) {
      throw new BusinessException(message);
    }
    return value.trim();
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
