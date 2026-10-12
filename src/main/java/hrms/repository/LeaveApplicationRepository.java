package hrms.repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import hrms.exception.BusinessException;
import hrms.model.LeaveApplication;

/**
 * JDBC access for the leave_application / leave_history tables
 * (soft-deleted rows excluded).
 */
public final class LeaveApplicationRepository {

  private LeaveApplicationRepository() {
  }

  private static final String SELECT_SQL = """
          SELECT la.id, la.staff_id, la.leave_type_id, la.from_date, la.to_date,
                 la.is_half_day, la.half_day_period, la.total_days, la.business_days,
                 la.requested_date, la.approver_id, la.status, la.approved_date,
                 la.reject_reason, la.remark,
                 la.created_by, la.created_date,
                 CONCAT(s.code, ' - ', s.name) AS staff_name,
                 CONCAT(lt.code, ' - ', lt.name) AS leave_type_name,
                 CONCAT(ap.code, ' - ', ap.name) AS approver_name
            FROM leave_application la
            JOIN staff s ON s.id = la.staff_id
            JOIN leave_type lt ON lt.id = la.leave_type_id
            LEFT JOIN staff ap ON ap.id = la.approver_id
           WHERE la.is_deleted = FALSE
          """;

  public static List<Object[]> findAllAsTableRows(String keyword, String status,
          Long restrictToStaffId) {
    List<LeaveApplication> applications = findAll(keyword, status, restrictToStaffId);
    List<Object[]> tableRows = new ArrayList<>(applications.size());
    for (LeaveApplication application : applications) {
      tableRows.add(application.toTableRow());
    }
    return tableRows;
  }

  public static List<LeaveApplication> findAll(String keyword, String status,
          Long restrictToStaffId) {
    StringBuilder sql = new StringBuilder(SELECT_SQL);
    List<Object> params = new ArrayList<>();
    if (restrictToStaffId != null) {
      sql.append(" AND la.staff_id = ?");
      params.add(restrictToStaffId);
    }
    if (keyword != null && !keyword.isBlank()) {
      sql.append(" AND (s.code LIKE CONCAT('%', ?, '%') "
              + "OR s.name LIKE CONCAT('%', ?, '%') "
              + "OR la.status LIKE CONCAT('%', ?, '%'))");
      String filter = keyword.trim();
      params.add(filter);
      params.add(filter);
      params.add(filter);
    }
    if (status != null && !status.isBlank()) {
      sql.append(" AND la.status = ?");
      params.add(status.trim());
    }
    sql.append(" ORDER BY la.id DESC");
    return Sql.query(sql.toString(), LeaveApplicationRepository::mapRow, params.toArray());
  }

  public static Optional<LeaveApplication> findById(long id) {
    return Sql.queryOne(SELECT_SQL + " AND la.id = ?", LeaveApplicationRepository::mapRow, id);
  }

  /** True when the same staff already has a PENDING/APPROVED range overlap. */
  public static boolean overlaps(long staffId, LocalDate from, LocalDate to, Long excludeId) {
    return Sql.queryOne(
            "SELECT 1 FROM leave_application "
                    + "WHERE staff_id = ? AND status IN ('PENDING', 'APPROVED') "
                    + "AND from_date <= ? AND to_date >= ? "
                    + "AND (? IS NULL OR id <> ?) AND is_deleted = FALSE",
            rs -> 1L, staffId, to, from, excludeId, excludeId).isPresent();
  }

  /**
   * Remaining entitlement days (entitled + carried - used) for the staff,
   * leave type and balance year; empty when no entitlement row exists.
   */
  public static Optional<BigDecimal> remainingDays(long staffId, long leaveTypeId, int year) {
    return Sql.queryOne(
            "SELECT entitled_days + COALESCE(carried_days, 0) - COALESCE(used_days, 0) "
                    + "FROM leave_entitlement "
                    + "WHERE staff_id = ? AND leave_type_id = ? "
                    + "AND YEAR(effective_date) = ? AND is_deleted = FALSE",
            rs -> rs.getBigDecimal(1), staffId, leaveTypeId, year);
  }

  public static long insert(LeaveApplication application) {
    if (application == null) {
      throw new IllegalArgumentException("application is required");
    }
    if (application.getStaffId() <= 0 || application.getLeaveTypeId() <= 0) {
      throw new BusinessException("Staff and leave type are required.");
    }
    if (application.getFromDate() == null || application.getToDate() == null
            || application.getTotalDays() == null || application.getBusinessDays() == null) {
      throw new BusinessException("Dates and days are required.");
    }
    long id = Sql.insert("""
            INSERT INTO leave_application
              (staff_id, leave_type_id, from_date, to_date,
               is_half_day, half_day_period, total_days, business_days,
               requested_date, status, remark, created_by, created_date, is_deleted)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', ?, ?, CURRENT_TIMESTAMP, FALSE)
            """,
            application.getStaffId(),
            application.getLeaveTypeId(),
            application.getFromDate(),
            application.getToDate(),
            application.isHalfDay(),
            application.getHalfDayPeriod(),
            application.getTotalDays(),
            application.getBusinessDays(),
            application.getRequestedDate(),
            application.getRemark(),
            application.getCreatedBy());
    application.setId(id);
    return id;
  }

  /** Updates editable fields of a PENDING application; false when not pending. */
  public static boolean update(LeaveApplication application) {
    if (application == null || application.getId() <= 0) {
      throw new IllegalArgumentException("application id is required");
    }
    int updated = Sql.update("""
            UPDATE leave_application
               SET from_date = ?,
                   to_date = ?,
                   is_half_day = ?,
                   half_day_period = ?,
                   total_days = ?,
                   business_days = ?,
                   remark = ?,
                   modified_by = ?,
                   modified_date = CURRENT_TIMESTAMP
             WHERE id = ? AND status = 'PENDING' AND is_deleted = FALSE
            """,
            application.getFromDate(),
            application.getToDate(),
            application.isHalfDay(),
            application.getHalfDayPeriod(),
            application.getTotalDays(),
            application.getBusinessDays(),
            application.getRemark(),
            application.getModifiedBy(),
            application.getId());
    return updated > 0;
  }

  /** Soft-deletes one row; false when nothing changed. */
  public static boolean softDelete(long id, Long deletedBy) {
    if (id <= 0) {
      throw new IllegalArgumentException("id is required");
    }
    return Sql.update("""
            UPDATE leave_application
               SET is_deleted = TRUE,
                   deleted_by = ?,
                   deleted_date = CURRENT_TIMESTAMP
             WHERE id = ? AND is_deleted = FALSE
            """, deletedBy, id) > 0;
  }

  /**
   * Approves a PENDING application: status flip, approver + date, days moved
   * into the entitlement ledger's used_days, and an APPROVE history row.
   *
   * @throws BusinessException when the row is not pending or the entitlement
   *                           ledger row is missing
   */
  public static void approve(long id, long approverStaffId, Long approverUserId,
          LeaveApplication application) {
    int updated = Sql.update("""
            UPDATE leave_application
               SET status = 'APPROVED',
                   approver_id = ?,
                   approved_date = CURDATE(),
                   modified_by = ?,
                   modified_date = CURRENT_TIMESTAMP
             WHERE id = ? AND status = 'PENDING' AND is_deleted = FALSE
            """, approverStaffId, approverUserId, id);
    if (updated == 0) {
      throw new BusinessException("Leave " + application.getStaffName()
              + " is no longer pending.", "Refresh the list and try again.");
    }
    boolean ledger = Sql.update("""
            UPDATE leave_entitlement
               SET used_days = COALESCE(used_days, 0) + ?
             WHERE staff_id = ? AND leave_type_id = ? AND YEAR(effective_date) = ?
               AND is_deleted = FALSE
            """, application.getTotalDays(), application.getStaffId(),
            application.getLeaveTypeId(), application.getFromDate().getYear()) > 0;
    if (!ledger) {
      throw new BusinessException(
              "No leave entitlement ledger row for this staff, type and year.",
              "Create the entitlement first (Leave Entitlement screen), "
              + "then approve again.");
    }
    insertHistory(id, "APPROVE", "PENDING", "APPROVED", approverStaffId, null);
  }

  /**
   * Rejects a PENDING application with a required reason; records a REJECT
   * history row. The entitlement ledger is untouched.
   */
  public static void reject(long id, long approverStaffId, Long approverUserId,
          String reason, LeaveApplication application) {
    int updated = Sql.update("""
            UPDATE leave_application
               SET status = 'REJECTED',
                   approver_id = ?,
                   reject_reason = ?,
                   approved_date = CURDATE(),
                   modified_by = ?,
                   modified_date = CURRENT_TIMESTAMP
             WHERE id = ? AND status = 'PENDING' AND is_deleted = FALSE
            """, approverStaffId, reason, approverUserId, id);
    if (updated == 0) {
      throw new BusinessException("Leave " + application.getStaffName()
              + " is no longer pending.", "Refresh the list and try again.");
    }
    insertHistory(id, "REJECT", "PENDING", "REJECTED", approverStaffId, reason);
  }

  /** Records one workflow step in leave_history. */
  public static void insertHistory(long applicationId, String action,
          String previousStatus, String status, Long actionBy, String remark) {
    Sql.update("""
            INSERT INTO leave_history
              (leave_application_id, action, previous_status, status,
               action_by, action_date, remark, created_by, created_date, is_deleted)
            VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, ?, ?, CURRENT_TIMESTAMP, FALSE)
            """,
            applicationId, action, previousStatus, status, actionBy, remark, actionBy);
  }

  /**
   * Read-only history log rows for the Leave History screen:
   * [id, staff, leaveType, action, previousStatus, status, actionBy, actionDate, remark].
   *
   * @param keyword            matches staff code, staff name or leave type (nullable)
   * @param action             leave_history.action filter (nullable)
   * @param restrictToStaffId  when set, only history of this staff's applications
   */
  public static List<Object[]> listHistoryRows(String keyword, String action,
          Long restrictToStaffId) {
    String sql = """
            SELECT h.id,
                   CONCAT(sa.code, ' - ', sa.name) AS staff_name,
                   lt.name AS leave_type_name,
                   h.action, h.previous_status, h.status,
                   CONCAT(ab.code, ' - ', ab.name) AS action_by_name,
                   h.action_date, h.remark
              FROM leave_history h
              JOIN leave_application la ON la.id = h.leave_application_id
              JOIN staff sa ON sa.id = la.staff_id
              JOIN leave_type lt ON lt.id = la.leave_type_id
              JOIN staff ab ON ab.id = h.action_by
             WHERE h.is_deleted = FALSE AND la.is_deleted = FALSE
            """;
    java.util.List<Object> params = new java.util.ArrayList<>();
    if (keyword != null && !keyword.isBlank()) {
      sql += " AND (sa.code LIKE ? OR sa.name LIKE ? OR lt.name LIKE ?)";
      String like = "%" + keyword.trim() + "%";
      params.add(like);
      params.add(like);
      params.add(like);
    }
    if (action != null && !action.isBlank()) {
      sql += " AND h.action = ?";
      params.add(action.trim());
    }
    if (restrictToStaffId != null) {
      sql += " AND la.staff_id = ?";
      params.add(restrictToStaffId);
    }
    sql += " ORDER BY h.action_date DESC, h.id DESC";
    return Sql.query(sql, rs -> new Object[]{
      rs.getLong("id"),
      rs.getString("staff_name"),
      rs.getString("leave_type_name"),
      rs.getString("action"),
      rs.getString("previous_status"),
      rs.getString("status"),
      rs.getString("action_by_name"),
      rs.getTimestamp("action_date") == null ? null
      : rs.getTimestamp("action_date").toLocalDateTime(),
      rs.getString("remark")
    }, params.toArray());
  }

  private static LeaveApplication mapRow(ResultSet rs) throws SQLException {
    LeaveApplication application = new LeaveApplication();
    application.setId(rs.getLong("id"));
    application.setStaffId(rs.getLong("staff_id"));
    application.setLeaveTypeId(rs.getLong("leave_type_id"));
    application.setFromDate(rs.getObject("from_date", LocalDate.class));
    application.setToDate(rs.getObject("to_date", LocalDate.class));
    application.setHalfDay(rs.getBoolean("is_half_day"));
    application.setHalfDayPeriod(rs.getString("half_day_period"));
    application.setTotalDays(rs.getBigDecimal("total_days"));
    application.setBusinessDays(rs.getBigDecimal("business_days"));
    application.setRequestedDate(rs.getObject("requested_date", LocalDate.class));
    application.setApproverId(nullableLong(rs, "approver_id"));
    application.setStatus(rs.getString("status"));
    application.setApprovedDate(rs.getObject("approved_date", LocalDate.class));
    application.setRejectReason(rs.getString("reject_reason"));
    application.setRemark(rs.getString("remark"));
    application.setCreatedBy(nullableLong(rs, "created_by"));
    application.setCreatedDate(rs.getTimestamp("created_date") == null
            ? null : rs.getTimestamp("created_date").toLocalDateTime());
    application.setStaffName(rs.getString("staff_name"));
    application.setLeaveTypeName(rs.getString("leave_type_name"));
    application.setApproverName(rs.getString("approver_name"));
    return application;
  }

  private static Long nullableLong(ResultSet rs, String column) throws SQLException {
    long value = rs.getLong(column);
    return rs.wasNull() ? null : value;
  }
}
