package hrms.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import hrms.model.UserAccount;

/**
 * User persistence for the authentication flow: credential lookup (with the
 * linked staff row), role and permission loading, password writes and the
 * last-login touch. Password hashes are only ever written through
 * {@link #updatePassword}, never logged.
 *
 * <p>Schema notes: {@code user} holds the credentials, {@code staff} holds
 * the person (full name, phone). Roles come from {@code system_role} via the
 * single {@code user.system_role_id} column; permissions are reached through
 * {@code role_permission}.</p>
 */
public class UserRepository {

    private static final String ACCOUNT_COLUMNS =
            "SELECT u.id, u.username, u.password_hash, s.name AS full_name, u.email, "
                    + "s.contact_no AS phone, u.status, u.must_change_password, "
                    + "s.id AS staff_id "
                    + "FROM `user` u JOIN staff s ON s.id = u.staff_id ";

    private static final String SELECT_ACCOUNT_BY_EMAIL =
            ACCOUNT_COLUMNS + "WHERE u.email = ? AND u.is_deleted = 0";

    private static final String SELECT_ACCOUNT_BY_ID =
            ACCOUNT_COLUMNS + "WHERE u.id = ? AND u.is_deleted = 0";

    /** Role reference for the session (system_role has no separate code column). */
    public record RoleInfo(String code, String name) {
    }

    /** Persisted brute-force state; lockout survives an application restart. */
    public record LockoutState(long failedCount, LocalDateTime lockedDate) {
    }

    /** Returns the account for an email address, including the password hash. */
    public Optional<UserAccount> findAccountByEmail(String email) {
        return Sql.queryOne(SELECT_ACCOUNT_BY_EMAIL, UserRepository::mapAccount, email);
    }

    /** Returns the account for an id, including the password hash. */
    public Optional<UserAccount> findAccountById(long userId) {
        return Sql.queryOne(SELECT_ACCOUNT_BY_ID, UserRepository::mapAccount, userId);
    }

    private static UserAccount mapAccount(ResultSet rs) throws SQLException {
        Object staffId = rs.getObject("staff_id");
        return new UserAccount(
                rs.getLong("id"),
                rs.getString("username"),
                rs.getString("password_hash"),
                rs.getString("full_name"),
                rs.getString("email"),
                rs.getString("phone"),
                "Active".equalsIgnoreCase(rs.getString("status")),
                rs.getBoolean("must_change_password"),
                staffId == null ? null : rs.getLong("staff_id"));
    }

    /** The account's system role (single assignment per user). */
    public List<RoleInfo> findRoles(long userId) {
        return Sql.query(
                "SELECT sr.name AS code, sr.name AS name "
                        + "FROM `user` u JOIN system_role sr ON sr.id = u.system_role_id "
                        + "WHERE u.id = ? AND sr.is_deleted = 0",
                rs -> new RoleInfo(rs.getString("code"), rs.getString("name")),
                userId);
    }

    /** Permission codes granted to the account's role. */
    public List<String> findPermissionCodes(long userId) {
        return Sql.query(
                "SELECT DISTINCT p.code "
                        + "FROM permission p "
                        + "JOIN role_permission rp ON rp.permission_id = p.id "
                        + "WHERE rp.system_role_id = "
                        + "(SELECT system_role_id FROM `user` WHERE id = ?) "
                        + "AND p.is_deleted = 0",
                rs -> rs.getString("code"),
                userId);
    }

    /** Writes a new password hash. */
    public void updatePassword(long userId, String passwordHash) {
        Sql.update("UPDATE `user` SET password_hash = ? WHERE id = ?", passwordHash, userId);
    }

    /** Sets or clears the forced-change flag. */
    public void setMustChangePassword(long userId, boolean mustChange) {
        Sql.update("UPDATE `user` SET must_change_password = ? WHERE id = ?",
                mustChange, userId);
    }

    /** Records a successful sign-in time. */
    public void touchLastLogin(long userId) {
        Sql.update("UPDATE `user` SET last_login_date = CURRENT_TIMESTAMP WHERE id = ?", userId);
    }

    // ------------------------------------------------------------------
    // Brute-force lockout persistence
    // ------------------------------------------------------------------

    /** Current failed-attempt state for an email, empty when unknown. */
    public Optional<LockoutState> findLockoutState(String email) {
        return Sql.queryOne(
                "SELECT failed_login_count, locked_date FROM `user` "
                        + "WHERE email = ? AND is_deleted = 0",
                rs -> new LockoutState(rs.getLong("failed_login_count"),
                        rs.getObject("locked_date", LocalDateTime.class)),
                email);
    }

    /** Increments the failure counter; stamps the lock when the max is hit. */
    public void recordLoginFailure(String email, int maxAttempts) {
        // locked_date must be assigned first: MySQL evaluates SET clauses
        // left-to-right, so the CASE has to see the pre-update counter.
        Sql.update(
                "UPDATE `user` SET "
                        + "locked_date = CASE WHEN failed_login_count + 1 >= ? THEN NOW() END, "
                        + "failed_login_count = failed_login_count + 1 "
                        + "WHERE email = ? AND is_deleted = 0",
                maxAttempts, email);
    }

    /** Clears the persisted lockout (successful sign-in or expired lock). */
    public void clearLoginFailures(String email) {
        Sql.update(
                "UPDATE `user` SET failed_login_count = 0, locked_date = NULL "
                        + "WHERE email = ? AND is_deleted = 0",
                email);
    }

    // ------------------------------------------------------------------
    // login_history audit trail
    // ------------------------------------------------------------------

    /**
     * Appends a sign-in history row. Status is SUCCESS, FAILED or LOGOUT.
     * Only called for known accounts ({@code user_id} is a NOT NULL FK).
     */
    public void insertLoginHistory(long userId, String status, String ipAddress) {
        Sql.insert("INSERT INTO login_history (user_id, status, ip_address) VALUES (?, ?, ?)",
                userId, status, ipAddress);
    }
}
