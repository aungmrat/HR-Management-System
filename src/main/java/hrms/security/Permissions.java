package hrms.security;

import java.util.Arrays;
import java.util.Optional;

/**
 * Application permission codes. Names match the {@code permissions} table
 * seed exactly; unknown database codes are ignored at login (logged) so new
 * permissions can be added via migration without breaking older builds.
 *
 * <p>One code per side-menu leaf ({@code *_VIEW} controls menu visibility);
 * action codes ({@code *_CREATE}, {@code *_UPDATE}, {@code LEAVE_APPROVE},
 * {@code USER_MANAGE}) gate the operations inside a panel. Dashboard needs
 * no code — every signed-in user sees it.</p>
 */
public enum Permissions {

    // Organization
    DEPARTMENT_VIEW, DEPARTMENT_CREATE, DEPARTMENT_UPDATE,
    OFFICE_VIEW, OFFICE_CREATE, OFFICE_UPDATE,
    ORG_TREE_VIEW,

    // Admin Hierarchy
    ROLE_VIEW, ROLE_CREATE, ROLE_UPDATE,
    POSITION_VIEW, POSITION_CREATE, POSITION_UPDATE,
    TEAM_VIEW, TEAM_CREATE, TEAM_UPDATE,
    TEAM_MEMBER_VIEW, TEAM_MEMBER_CREATE, TEAM_MEMBER_UPDATE,

    // Staff Management
    STAFF_VIEW, STAFF_CREATE, STAFF_UPDATE,
    LANGUAGE_SKILL_VIEW, MILITARY_SERVICE_VIEW, FAMILY_MEMBER_VIEW,

    // Leave Management
    LEAVE_TYPE_VIEW, LEAVE_TYPE_CREATE, LEAVE_TYPE_UPDATE,
    LEAVE_ENTITLEMENT_VIEW, LEAVE_ENTITLEMENT_CREATE, LEAVE_ENTITLEMENT_UPDATE,
    LEAVE_APPLICATION_VIEW, LEAVE_APPLICATION_CREATE,
    LEAVE_APPROVAL_VIEW, LEAVE_APPROVE,
    LEAVE_HISTORY_VIEW, GAZETTED_HOLIDAY_VIEW, HOLIDAY_CALENDAR_VIEW,

    // Compensation Policy
    OVERTIME_POLICY_VIEW, OVERTIME_POLICY_CREATE, OVERTIME_POLICY_UPDATE,
    BENEFIT_VIEW, BENEFIT_CREATE, BENEFIT_UPDATE,
    ALLOWANCE_VIEW, ALLOWANCE_CREATE, ALLOWANCE_UPDATE,
    SALARY_RATE_VIEW, SALARY_RATE_CREATE, SALARY_RATE_UPDATE, TAX_VIEW,
    TAX_CREATE, TAX_UPDATE,

    // Payroll & Compensation
    STAFF_SALARY_VIEW, STAFF_BENEFIT_VIEW, STAFF_ALLOWANCE_VIEW,
    PAYROLL_VIEW, PAYROLL_DETAILS_VIEW, PAYSLIP_VIEW,

    // Attendance & Roster
    ATTENDANCE_VIEW, SHIFT_VIEW, SHIFT_CREATE, SHIFT_UPDATE, DUTY_ROSTER_VIEW,
    
    // Report
    REPORT_VIEW,

    // System
    USER_MANAGE;

    /**
     * Maps a database permission code to the enum; empty when the code is
     * unknown to this build.
     */
    public static Optional<Permissions> fromCode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(permission -> permission.name().equals(code.trim()))
                .findFirst();
    }
}
