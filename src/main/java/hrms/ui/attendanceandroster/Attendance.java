/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/GUIForms/JPanel.java to edit this template
 */
package hrms.ui.attendanceandroster;

import hrms.component.ErrorPanel;
import hrms.component.HrmsTable;
import hrms.component.KpiChip;
import hrms.component.SearchField;
import hrms.exception.AuthorizationException;
import hrms.exception.ErrorHandler;
import hrms.model.Department;
import hrms.repository.AttendanceRepository;
import hrms.repository.DepartmentRepository;
import hrms.security.Permissions;
import hrms.security.SecurityService;
import hrms.security.SessionContext;
import hrms.ui.theme.Palette;
import hrms.ui.theme.Palette.Role;
import hrms.util.Dialogs;
import hrms.util.UiThread;
import java.awt.FlowLayout;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author aungmrat
 */
public class Attendance extends javax.swing.JPanel {

  private static final Logger LOG = LoggerFactory.getLogger(Attendance.class);

  private static final DateTimeFormatter DATE_FORMAT =
          DateTimeFormatter.ofPattern("dd MMM yyyy");

  /** KPI chips kept so filters and reloads can update them. */
  private KpiChip chipPresent;
  private KpiChip chipAbsent;
  private KpiChip chipLeave;
  private KpiChip chipHalfDay;
  private KpiChip chipHours;

  /** Chip label -> status filter value (null = no chip filter). */
  private static final Map<String, String> CHIP_STATUS = Map.of(
      "PRESENT", "PRESENT",
      "ABSENT", "ABSENT",
      "LEAVE", "LEAVE",
      "HALF DAY", "HALF_DAY");

  /**
   * HrmsTable lives outside the GEN block so Form Designer saves cannot wipe it.
   */
  private HrmsTable table;

  /** Last loaded models — the context menu maps model rows back to staff/date. */
  private List<hrms.model.Attendance> lastRows;

  /**
   * Creates new form Attendance
   */
  public Attendance() {
    initComponents();
    setupKpiChips();
    setupTable();
    setupActions();
    loadDepartments();
    dcAttendance.setDate(java.sql.Date.valueOf(LocalDate.now()));
    refreshTable();
  }

  // ------------------------------------------------------------------
  // Permissions & actions
  // ------------------------------------------------------------------

  private void setupActions() {
    btnCheckIn.addActionListener(e -> openPunchDialog(AttendanceDialog.Mode.IN));
    btnCheckOut.addActionListener(e -> openPunchDialog(AttendanceDialog.Mode.OUT));
    jButton1.addActionListener(e -> runGuarded(this::markAbsentees));
    for (String status : AttendanceRepository.STATUSES) {
      comboStatus.addItem(status);
    }
    comboStatus.addActionListener(e -> runGuarded(this::refreshTable));
    comboDept.addActionListener(e -> runGuarded(this::refreshTable));
    dcAttendance.addPropertyChangeListener("date", evt -> {
      if (evt.getNewValue() != null || evt.getOldValue() != null) {
        runGuarded(this::refreshTable);
      }
    });
    if (textSearch instanceof SearchField searchField) {
      searchField.onTextChanged(keyword -> runGuarded(this::refreshTable));
    }
    if (!SecurityService.can(Permissions.ATTENDANCE_CREATE)) {
      jButton1.setEnabled(false);
      jButton1.setToolTipText("Requires permission: ATTENDANCE_CREATE");
    }
  }

  private void runGuarded(Runnable action) {
    try {
      action.run();
    } catch (AuthorizationException e) {
      ErrorHandler.handle(this, e);
    }
  }

  private java.awt.Frame ownerFrame() {
    java.awt.Window owner = javax.swing.SwingUtilities.getWindowAncestor(this);
    if (owner instanceof java.awt.Frame f) {
      return f;
    }
    if (owner instanceof java.awt.Dialog d && d.getOwner() instanceof java.awt.Frame fo) {
      return fo;
    }
    return null;
  }

  /** Self-punch needs no permission; the dialog gates other-staff picks. */
  private void openPunchDialog(AttendanceDialog.Mode mode) {
    AttendanceDialog dialog = new AttendanceDialog(ownerFrame(), true, mode);
    dialog.setLocationRelativeTo(this);
    dialog.setVisible(true);
    refreshTable();
  }

  private LocalDate selectedDate() {
    java.util.Date picked = dcAttendance.getDate();
    return picked == null ? null : new java.sql.Date(picked.getTime()).toLocalDate();
  }

  /**
   * One-click batch ABSENT: rostered staff for the picked date without an
   * attendance row and without an approved leave covering it.
   */
  private void markAbsentees() {
    SecurityService.require(Permissions.ATTENDANCE_CREATE);
    LocalDate day = selectedDate();
    if (day == null) {
      Dialogs.info(this, "Mark Absentees", "Pick a date first.");
      return;
    }
    if (!Dialogs.confirm(this, "Mark Absentees",
        "Mark rostered staff with no punch on " + DATE_FORMAT.format(day)
            + " as ABSENT? Staff with approved leave are skipped.")) {
      return;
    }
    Long userId = SessionContext.isAuthenticated() ? SessionContext.currentUserId() : null;
    UiThread.executeAsync("Mark absentees",
        () -> AttendanceRepository.markAbsentees(day, userId),
        count -> {
          Dialogs.info(this, "Mark Absentees", count == 0
              ? "No staff needed marking on " + DATE_FORMAT.format(day) + "."
              : count + " staff marked absent.");
          refreshTable();
        },
        ex -> ErrorHandler.handle(this, ex));
  }

  private void openMonthlyViewForSelected() {
    int modelRow = table.selectedModelRow();
    if (modelRow < 0) {
      Dialogs.info(this, "Monthly View", "Select a row first.");
      return;
    }
    if (lastRows == null || modelRow >= lastRows.size()) {
      Dialogs.info(this, "Monthly View", "Refresh the list and try again.");
      return;
    }
    hrms.model.Attendance attendance = lastRows.get(modelRow);
    AttendanceMonthlyView dialog = new AttendanceMonthlyView(ownerFrame(), true,
        attendance.getStaffId(), attendance.getStaffName(), attendance.getDate());
    dialog.setLocationRelativeTo(this);
    dialog.setVisible(true);
  }

  private void deleteSelected() {
    SecurityService.require(Permissions.ATTENDANCE_UPDATE);
    int modelRow = table.selectedModelRow();
    if (modelRow < 0) {
      Dialogs.info(this, "Delete Attendance", "Select a row first.");
      return;
    }
    long id = ((Number) table.getModel().getValueAt(modelRow, 0)).longValue();
    String staff = String.valueOf(table.getModel().getValueAt(modelRow, 1));
    String date = String.valueOf(table.getModel().getValueAt(modelRow, 2));
    if (!Dialogs.confirm(this, "Delete Attendance",
        "Delete " + staff + " on " + date + "? This is a soft delete.")) {
      return;
    }
    Long userId = SessionContext.isAuthenticated() ? SessionContext.currentUserId() : null;
    UiThread.executeAsync("Delete attendance",
        () -> AttendanceRepository.softDelete(id, userId),
        deleted -> refreshTable());
  }

  // ------------------------------------------------------------------
  // KPI chips
  // ------------------------------------------------------------------

  private void setupKpiChips() {
    kpiPanel.setLayout(new FlowLayout(FlowLayout.LEFT, 12, 22));
    chipPresent = new KpiChip("Present", Role.SUCCESS, () -> toggleChipFilter("PRESENT"));
    chipAbsent = new KpiChip("Absent", Role.DANGER, () -> toggleChipFilter("ABSENT"));
    chipLeave = new KpiChip("Leave", Role.INFO, () -> toggleChipFilter("LEAVE"));
    chipHalfDay = new KpiChip("Half Day", Role.WARNING, () -> toggleChipFilter("HALF_DAY"));
    chipHours = new KpiChip("Total Hours", Role.ACCENT, null);
    kpiPanel.add(chipPresent);
    kpiPanel.add(chipAbsent);
    kpiPanel.add(chipLeave);
    kpiPanel.add(chipHalfDay);
    kpiPanel.add(chipHours);
  }

  /**
   * Clicking a chip toggles the status filter — the two views stay in sync
   * whichever one the user drives.
   */
  private void toggleChipFilter(String status) {
    String current = selectedStatusFilter();
    if (status.equals(current)) {
      selectStatusFilter(null);
    } else {
      selectStatusFilter(status);
    }
    refreshTable();
  }

  private String selectedStatusFilter() {
    Object item = comboStatus.getSelectedItem();
    if (item == null || String.valueOf(item).startsWith("Status:")) {
      return null;
    }
    return String.valueOf(item);
  }

  private void selectStatusFilter(String status) {
    comboStatus.setSelectedItem(status == null ? "Status: All" : status);
  }

  private void refreshChips(Map<String, BigDecimal> summary) {
    chipPresent.setCount(countOf(summary, "PRESENT"));
    chipAbsent.setCount(countOf(summary, "ABSENT"));
    chipLeave.setCount(countOf(summary, "LEAVE"));
    BigDecimal halfDays = countValue(summary, "HALF_DAY_AM")
        .add(countValue(summary, "HALF_DAY_PM"));
    chipHalfDay.setCount(halfDays.toPlainString());
    BigDecimal hours = summary.getOrDefault("TOTAL_HOURS", BigDecimal.ZERO);
    chipHours.setCount(hours.setScale(1, RoundingMode.HALF_UP).toPlainString() + "h");

    String active = selectedStatusFilter();
    chipPresent.setSelectedChip("PRESENT".equals(active));
    chipAbsent.setSelectedChip("ABSENT".equals(active));
    chipLeave.setSelectedChip("LEAVE".equals(active));
    chipHalfDay.setSelectedChip("HALF_DAY".equals(active));
    chipHours.setSelectedChip(false);
  }

  private static String countOf(Map<String, BigDecimal> summary, String key) {
    return summary.getOrDefault(key, BigDecimal.ZERO).toPlainString();
  }

  private static BigDecimal countValue(Map<String, BigDecimal> summary, String key) {
    return summary.getOrDefault(key, BigDecimal.ZERO);
  }

  // ------------------------------------------------------------------
  // Department filter
  // ------------------------------------------------------------------

  private record ComboItem(Long value, String label) {
    @Override
    public String toString() {
      return label;
    }
  }

  private void loadDepartments() {
    UiThread.executeAsync("Load departments",
        DepartmentRepository::listAll,
        this::fillDepartments);
  }

  private void fillDepartments(List<Department> departments) {
    comboDept.removeAllItems();
    comboDept.addItem(new ComboItem(null, "Dept: All"));
    for (Department department : departments) {
      comboDept.addItem(new ComboItem(department.getId(),
          department.getCode() + " — " + department.getName()));
    }
  }

  private Long selectedDepartmentId() {
    ComboItem item = (ComboItem) comboDept.getSelectedItem();
    return item == null ? null : item.value();
  }

  // ------------------------------------------------------------------
  // Table
  // ------------------------------------------------------------------

  private void setupTable() {
    table = HrmsTable.builder(
        "ID", "Staff", "Date", "Shift", "Punches", "In", "Out", "Hours", "OT",
        "Status", "Remark")
        .hiddenColumn(0)
        .fixedColumn(1, 200)
        .fixedColumn(2, 110)
        .fixedColumn(3, 150)
        .fixedColumn(4, 70)
        .fixedColumn(5, 70)
        .fixedColumn(6, 70)
        .fixedColumn(7, 70)
        .fixedColumn(8, 55)
        .badgeColumn(9)
        .fixedColumn(10, 180)
        .contextMenu(() -> {
          javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();
          javax.swing.JMenuItem monthlyItem = new javax.swing.JMenuItem("Monthly View");
          monthlyItem.addActionListener(e -> runGuarded(this::openMonthlyViewForSelected));
          javax.swing.JMenuItem deleteItem = new javax.swing.JMenuItem("Delete");
          deleteItem.addActionListener(e -> runGuarded(this::deleteSelected));
          menu.add(monthlyItem);
          menu.addSeparator();
          menu.add(deleteItem);
          return menu;
        })
        .build();
    table.setRowHeight(30);
    jScrollPane1.setViewportView(table);
  }

  public void refreshTable() {
    jScrollPane1.setViewportView(table);
    String keyword = textSearch.getText() == null ? "" : textSearch.getText().trim();
    String status = selectedStatusFilter();
    // The repo filters one exact status; HALF_DAY covers AM+PM so filter client-side.
    boolean halfDayFilter = "HALF_DAY".equals(status);
    String statusFilter = halfDayFilter ? null : status;
    Long departmentId = selectedDepartmentId();
    LocalDate day = selectedDate();
    UiThread.executeAsync("Load attendance",
        () -> {
          List<hrms.model.Attendance> rows =
              AttendanceRepository.findAll(keyword, statusFilter, day, day, departmentId);
          if (halfDayFilter) {
            rows = rows.stream()
                .filter(a -> a.getStatus() != null && a.getStatus().startsWith("HALF_DAY"))
                .toList();
          }
          Map<String, BigDecimal> summary = day == null
              ? Map.of() : AttendanceRepository.summary(day);
          return new Object[]{rows, summary};
        },
        payload -> {
          @SuppressWarnings("unchecked")
          List<hrms.model.Attendance> rows =
              (List<hrms.model.Attendance>) payload[0];
          @SuppressWarnings("unchecked")
          Map<String, BigDecimal> summary = (Map<String, BigDecimal>) payload[1];
          List<Object[]> tableRows = new java.util.ArrayList<>();
          for (hrms.model.Attendance attendance : rows) {
            tableRows.add(attendance.toTableRow());
          }
          table.setRows(tableRows);
          lastRows = rows;
          refreshChips(summary);
        },
        ex -> showLoadError(ex));
  }

  private void showLoadError(Exception ex) {
    LOG.warn("Cannot load attendance list", ex);
    jScrollPane1.setViewportView(new ErrorPanel(
        "Cannot load the attendance list. Check that the database is running, then try again.",
        this::refreshTable));
  }

  /**
   * This method is called from within the constructor to initialize the form.
   * WARNING: Do NOT modify this code. The content of this method is always
   * regenerated by the Form Editor.
   */
  @SuppressWarnings("unchecked")
  // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
  private void initComponents() {

    kpiPanel = new javax.swing.JPanel();
    mainPanel = new javax.swing.JPanel();
    header = new javax.swing.JPanel();
    dcAttendance = new com.toedter.calendar.JDateChooser();
    comboStatus = new javax.swing.JComboBox<>();
    comboDept = new javax.swing.JComboBox<>();
    textSearch =  new SearchField("Search by name or code...");
    btnCheckIn = new javax.swing.JButton();
    btnCheckOut = new javax.swing.JButton();
    jButton1 = new javax.swing.JButton();
    footer = new javax.swing.JPanel();
    body = new javax.swing.JPanel();
    jScrollPane1 = new javax.swing.JScrollPane();
    tableAttendance = new javax.swing.JTable();

    setLayout(new java.awt.BorderLayout());

    javax.swing.GroupLayout kpiPanelLayout = new javax.swing.GroupLayout(kpiPanel);
    kpiPanel.setLayout(kpiPanelLayout);
    kpiPanelLayout.setHorizontalGroup(
      kpiPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGap(0, 1237, Short.MAX_VALUE)
    );
    kpiPanelLayout.setVerticalGroup(
      kpiPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGap(0, 100, Short.MAX_VALUE)
    );

    add(kpiPanel, java.awt.BorderLayout.PAGE_START);

    mainPanel.setLayout(new java.awt.BorderLayout());

    header.setPreferredSize(new java.awt.Dimension(1237, 80));

    comboStatus.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Status: All" }));

    btnCheckIn.setText("Check In");
    btnCheckIn.setPreferredSize(new java.awt.Dimension(90, 27));

    btnCheckOut.setText("Check Out");

    jButton1.setText("Mark Absentees");

    javax.swing.GroupLayout headerLayout = new javax.swing.GroupLayout(header);
    header.setLayout(headerLayout);
    headerLayout.setHorizontalGroup(
      headerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, headerLayout.createSequentialGroup()
        .addGap(20, 20, 20)
        .addGroup(headerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
          .addComponent(dcAttendance, javax.swing.GroupLayout.PREFERRED_SIZE, 130, javax.swing.GroupLayout.PREFERRED_SIZE)
          .addGroup(headerLayout.createSequentialGroup()
            .addComponent(comboStatus, javax.swing.GroupLayout.PREFERRED_SIZE, 124, javax.swing.GroupLayout.PREFERRED_SIZE)
            .addGap(18, 18, 18)
            .addComponent(comboDept, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
            .addGap(18, 18, 18)
            .addComponent(textSearch, javax.swing.GroupLayout.PREFERRED_SIZE, 190, javax.swing.GroupLayout.PREFERRED_SIZE)))
        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 366, Short.MAX_VALUE)
        .addComponent(jButton1)
        .addGap(18, 18, 18)
        .addComponent(btnCheckIn, javax.swing.GroupLayout.PREFERRED_SIZE, 119, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
        .addComponent(btnCheckOut, javax.swing.GroupLayout.PREFERRED_SIZE, 106, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(51, 51, 51))
    );
    headerLayout.setVerticalGroup(
      headerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, headerLayout.createSequentialGroup()
        .addContainerGap()
        .addGroup(headerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
          .addGroup(headerLayout.createSequentialGroup()
            .addGroup(headerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
              .addComponent(btnCheckIn, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
              .addComponent(btnCheckOut)
              .addComponent(jButton1))
            .addGap(0, 0, Short.MAX_VALUE))
          .addGroup(headerLayout.createSequentialGroup()
            .addGap(0, 4, Short.MAX_VALUE)
            .addGroup(headerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
              .addComponent(comboStatus, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
              .addComponent(comboDept, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
              .addComponent(textSearch, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
            .addComponent(dcAttendance, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
        .addContainerGap())
    );

    mainPanel.add(header, java.awt.BorderLayout.PAGE_START);

    footer.setPreferredSize(new java.awt.Dimension(1237, 40));

    javax.swing.GroupLayout footerLayout = new javax.swing.GroupLayout(footer);
    footer.setLayout(footerLayout);
    footerLayout.setHorizontalGroup(
      footerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGap(0, 1237, Short.MAX_VALUE)
    );
    footerLayout.setVerticalGroup(
      footerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGap(0, 40, Short.MAX_VALUE)
    );

    mainPanel.add(footer, java.awt.BorderLayout.PAGE_END);

    tableAttendance.setModel(new javax.swing.table.DefaultTableModel(
      new Object [][] {
        {null, null, null, null},
        {null, null, null, null},
        {null, null, null, null},
        {null, null, null, null}
      },
      new String [] {
        "Title 1", "Title 2", "Title 3", "Title 4"
      }
    ));
    jScrollPane1.setViewportView(tableAttendance);

    javax.swing.GroupLayout bodyLayout = new javax.swing.GroupLayout(body);
    body.setLayout(bodyLayout);
    bodyLayout.setHorizontalGroup(
      bodyLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addComponent(jScrollPane1, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 1237, Short.MAX_VALUE)
    );
    bodyLayout.setVerticalGroup(
      bodyLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 620, Short.MAX_VALUE)
    );

    mainPanel.add(body, java.awt.BorderLayout.CENTER);

    add(mainPanel, java.awt.BorderLayout.CENTER);
  }// </editor-fold>//GEN-END:initComponents


  // Variables declaration - do not modify//GEN-BEGIN:variables
  private javax.swing.JPanel body;
  private javax.swing.JButton btnCheckIn;
  private javax.swing.JButton btnCheckOut;
  private javax.swing.JComboBox<ComboItem> comboDept;
  private javax.swing.JComboBox<String> comboStatus;
  private com.toedter.calendar.JDateChooser dcAttendance;
  private javax.swing.JPanel footer;
  private javax.swing.JPanel header;
  private javax.swing.JButton jButton1;
  private javax.swing.JScrollPane jScrollPane1;
  private javax.swing.JPanel kpiPanel;
  private javax.swing.JPanel mainPanel;
  private javax.swing.JTable tableAttendance;
  private javax.swing.JTextField textSearch;
  // End of variables declaration//GEN-END:variables
}
