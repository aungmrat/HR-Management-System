-- ============================================================================
-- HRMS auth bootstrap seed
-- Target: hr_management (MySQL 8)
-- Safe to re-run: every insert is guarded with NOT EXISTS or INSERT IGNORE.
--
-- Creates the minimum data chain required by NOT NULL foreign keys:
--   role -> position -> team -> staff -> user
-- then seeds permissions (matches hrms.security.Permissions enum exactly)
-- and role_permission mappings.
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1) Schema fix: user.email was varchar(20), staff.email is varchar(100)
-- ---------------------------------------------------------------------------
ALTER TABLE `user` MODIFY `email` VARCHAR(100) NOT NULL;

-- ---------------------------------------------------------------------------
-- 2) Org role (FK target for position.role_id)
-- ---------------------------------------------------------------------------
INSERT INTO `role` (department_id, name, remark)
SELECT d.id, 'System Administrator', 'Seed: auth bootstrap'
FROM department d
WHERE d.id = 1
  AND NOT EXISTS (SELECT 1 FROM `role` WHERE name = 'System Administrator');

-- ---------------------------------------------------------------------------
-- 3) Position (FK target for staff.position_id)
-- ---------------------------------------------------------------------------
INSERT INTO position (department_id, role_id, name)
SELECT r.department_id, r.id, 'System Administrator'
FROM `role` r
WHERE r.name = 'System Administrator'
  AND NOT EXISTS (SELECT 1 FROM position WHERE name = 'System Administrator');

-- ---------------------------------------------------------------------------
-- 4) Team (FK target for staff.team_id)
-- ---------------------------------------------------------------------------
INSERT INTO team (department_id, office_id, name)
SELECT d.id, o.id, 'Core Team'
FROM department d, office o
WHERE d.id = 1
  AND o.id = 1
  AND NOT EXISTS (SELECT 1 FROM team WHERE name = 'Core Team');

-- ---------------------------------------------------------------------------
-- 5) Admin staff record (FK target for user.staff_id)
-- ---------------------------------------------------------------------------
INSERT INTO staff (code, name, dob, sex, contact_no,
                   department_id, position_id, team_id, office_id,
                   salary, email, address, status)
SELECT 'EMP-001', 'System Administrator', '1990-01-01', 'Male', '0999000000',
       d.id, p.id, t.id, o.id,
       0.00, 'admin@ams.local', 'Yangon', 'Active'
FROM department d, position p, team t, office o
WHERE d.id = 1
  AND o.id = 1
  AND p.name = 'System Administrator'
  AND t.name = 'Core Team'
  AND NOT EXISTS (SELECT 1 FROM staff WHERE code = 'EMP-001');

-- ---------------------------------------------------------------------------
-- 6) Admin user account
--     password_hash = BCrypt cost 12 of 'Admin@123'
--     (generated with hrms.security.PasswordHasher-compatible algorithm)
--     must_change_password = 1 -> first login forces ChangePasswordDialog
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO `user` (staff_id, system_role_id, username, email,
                           password_hash, status, must_change_password)
SELECT s.id, sr.id, 'admin', 'admin@ams.local',
       '$2a$12$l8OX4M6lMJfWp2q5ZzUbdueIgqDtJu0llyqMA7yuxfcjhr4KYSyB2',
       'Active', 1
FROM staff s
JOIN system_role sr ON sr.name = 'HR_ADMIN' AND sr.is_deleted = 0
WHERE s.code = 'EMP-001';

-- ---------------------------------------------------------------------------
-- 7) Permissions (codes must match hrms.security.Permissions enum exactly;
--    module = side-menu group name)
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO permission (code, module, description) VALUES
  -- Organization
  ('DEPARTMENT_VIEW',             'organization',       'View departments'),
  ('DEPARTMENT_CREATE',           'organization',       'Create departments'),
  ('DEPARTMENT_UPDATE',           'organization',       'Edit departments'),
  ('OFFICE_VIEW',                 'organization',       'View offices'),
  ('OFFICE_CREATE',               'organization',       'Create offices'),
  ('OFFICE_UPDATE',               'organization',       'Edit offices'),
  ('ORG_TREE_VIEW',               'organization',       'View organization tree'),
  -- Admin Hierarchy
  ('ROLE_VIEW',                   'admin_hierarchy',    'View roles'),
  ('ROLE_CREATE',                 'admin_hierarchy',    'Create roles'),
  ('ROLE_UPDATE',                 'admin_hierarchy',    'Edit roles'),
  ('POSITION_VIEW',               'admin_hierarchy',    'View positions'),
  ('POSITION_CREATE',             'admin_hierarchy',    'Create positions'),
  ('POSITION_UPDATE',             'admin_hierarchy',    'Edit positions'),
  ('TEAM_VIEW',                   'admin_hierarchy',    'View teams'),
  ('TEAM_CREATE',                 'admin_hierarchy',    'Create teams'),
  ('TEAM_UPDATE',                 'admin_hierarchy',    'Edit teams'),
  ('TEAM_MEMBER_VIEW',            'admin_hierarchy',    'View team members'),
  ('TEAM_MEMBER_CREATE',          'admin_hierarchy',    'Add team members'),
  ('TEAM_MEMBER_UPDATE',          'admin_hierarchy',    'Edit team members'),
  -- Staff Management
  ('STAFF_VIEW',                  'staff_management',   'View staff list'),
  ('STAFF_CREATE',                'staff_management',   'Create staff records'),
  ('STAFF_UPDATE',                'staff_management',   'Edit staff records'),
  ('LANGUAGE_SKILL_VIEW',         'staff_management',   'View language skills'),
  ('MILITARY_SERVICE_VIEW',       'staff_management',   'View military service records'),
  ('FAMILY_MEMBER_VIEW',          'staff_management',   'View family members'),
  -- Leave Management
  ('LEAVE_TYPE_VIEW',             'leave_management',   'View leave types'),
  ('LEAVE_TYPE_CREATE',           'leave_management',   'Create leave types'),
  ('LEAVE_TYPE_UPDATE',           'leave_management',   'Edit or delete leave types'),
  ('LEAVE_ENTITLEMENT_VIEW',      'leave_management',   'View leave entitlements'),
  ('LEAVE_ENTITLEMENT_CREATE',    'leave_management',   'Create leave entitlements'),
  ('LEAVE_ENTITLEMENT_UPDATE',    'leave_management',   'Edit or delete leave entitlements'),
  ('LEAVE_APPLICATION_VIEW',      'leave_management',   'View leave applications'),
  ('LEAVE_APPLICATION_CREATE',    'leave_management',   'Submit leave applications'),
  ('LEAVE_APPROVAL_VIEW',         'leave_management',   'View leave approval queue'),
  ('LEAVE_APPROVE',               'leave_management',   'Approve or reject leave applications'),
  ('LEAVE_HISTORY_VIEW',          'leave_management',   'View leave history'),
  ('GAZETTED_HOLIDAY_VIEW',       'leave_management',   'View gazetted holidays'),
  ('HOLIDAY_CALENDAR_VIEW',       'leave_management',   'View holiday calendar'),
  -- Compensation Policy
  ('OVERTIME_POLICY_VIEW',        'compensation_policy', 'View overtime policies'),
  ('OVERTIME_POLICY_CREATE',      'compensation_policy', 'Create overtime policies'),
  ('OVERTIME_POLICY_UPDATE',      'compensation_policy', 'Edit or delete overtime policies'),
  ('BENEFIT_VIEW',                'compensation_policy', 'View benefits'),
  ('BENEFIT_CREATE',              'compensation_policy', 'Create benefits'),
  ('BENEFIT_UPDATE',              'compensation_policy', 'Edit or delete benefits'),
  ('ALLOWANCE_VIEW',              'compensation_policy', 'View allowances'),
  ('ALLOWANCE_CREATE',            'compensation_policy', 'Create allowances'),
  ('ALLOWANCE_UPDATE',            'compensation_policy', 'Edit or delete allowances'),
  ('SALARY_RATE_VIEW',            'compensation_policy', 'View salary rates'),
  ('SALARY_RATE_CREATE',          'compensation_policy', 'Create salary rates'),
  ('SALARY_RATE_UPDATE',          'compensation_policy', 'Edit or delete salary rates'),
  ('TAX_VIEW',                    'compensation_policy', 'View taxes'),
  ('TAX_CREATE',                  'compensation_policy', 'Create tax brackets'),
  ('TAX_UPDATE',                  'compensation_policy', 'Edit or delete tax brackets'),
  -- Payroll & Compensation
  ('STAFF_SALARY_VIEW',           'payroll_compensation', 'View staff salaries'),
  ('STAFF_BENEFIT_VIEW',          'payroll_compensation', 'View staff benefits'),
  ('STAFF_BENEFIT_ASSIGN',        'payroll_compensation', 'Assign or end staff benefits'),
  ('STAFF_BENEFIT_UPDATE',        'payroll_compensation', 'Edit staff benefit dates'),
  ('STAFF_ALLOWANCE_VIEW',        'payroll_compensation', 'View staff allowances'),
  ('PAYROLL_VIEW',                'payroll_compensation', 'View payroll runs'),
  ('PAYROLL_DETAILS_VIEW',        'payroll_compensation', 'View payroll details'),
  ('PAYSLIP_VIEW',                'payroll_compensation', 'View payslips'),
  -- Attendance & Roster
  ('ATTENDANCE_VIEW',             'attendance_roster',  'View attendance records'),
  ('ATTENDANCE_CREATE',           'attendance_roster',  'Create attendance records'),
  ('ATTENDANCE_UPDATE',           'attendance_roster',  'Edit or delete attendance records'),
  ('SHIFT_VIEW',                  'attendance_roster',  'View shifts'),
  ('SHIFT_CREATE',                'attendance_roster',  'Create shifts'),
  ('SHIFT_UPDATE',                'attendance_roster',  'Edit or delete shifts'),
  ('DUTY_ROSTER_VIEW',            'attendance_roster',  'View duty rosters'),
  ('DUTY_ROSTER_CREATE',          'attendance_roster',  'Assign duty rosters'),
  ('DUTY_ROSTER_UPDATE',          'attendance_roster',  'Edit or remove duty rosters'),
  -- Report
  ('REPORT_VIEW',                 'report',             'View reports'),
  -- System
  ('USER_MANAGE',                 'system',             'Manage user accounts');

-- ---------------------------------------------------------------------------
-- 8a) HR_ADMIN -> every permission
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO role_permission (system_role_id, permission_id)
SELECT sr.id, p.id
FROM system_role sr
JOIN permission p
WHERE sr.name = 'HR_ADMIN'
  AND sr.is_deleted = 0;

-- ---------------------------------------------------------------------------
-- 8b) MANAGER (Team Leader) -> view-only org/staff/attendance + team leave
--     approval. No user admin, no payroll/compensation, no create/update
--     on departments/offices/staff.
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO role_permission (system_role_id, permission_id)
SELECT sr.id, p.id
FROM system_role sr
JOIN permission p
WHERE sr.name = 'MANAGER'
  AND sr.is_deleted = 0
  AND p.code IN (
    'DEPARTMENT_VIEW', 'OFFICE_VIEW', 'ORG_TREE_VIEW',
    'ROLE_VIEW', 'POSITION_VIEW', 'TEAM_VIEW', 'TEAM_MEMBER_VIEW',
    'STAFF_VIEW', 'LANGUAGE_SKILL_VIEW', 'MILITARY_SERVICE_VIEW', 'FAMILY_MEMBER_VIEW',
    'LEAVE_TYPE_VIEW', 'LEAVE_ENTITLEMENT_VIEW',
    'LEAVE_APPLICATION_VIEW', 'LEAVE_APPLICATION_CREATE',
    'LEAVE_APPROVAL_VIEW', 'LEAVE_APPROVE',
    'LEAVE_HISTORY_VIEW', 'GAZETTED_HOLIDAY_VIEW', 'HOLIDAY_CALENDAR_VIEW',
    'ATTENDANCE_VIEW', 'SHIFT_VIEW', 'DUTY_ROSTER_VIEW'
  );

-- ---------------------------------------------------------------------------
-- 8c) STAFF -> own leave applications + limited staff view
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO role_permission (system_role_id, permission_id)
SELECT sr.id, p.id
FROM system_role sr
JOIN permission p
WHERE sr.name = 'STAFF'
  AND sr.is_deleted = 0
  AND p.code IN (
    'STAFF_VIEW',
    'LEAVE_APPLICATION_VIEW', 'LEAVE_APPLICATION_CREATE'
  );
