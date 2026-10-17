/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/GUIForms/JDialog.java to edit this template
 */
package hrms.ui.attendanceandroster;

import hrms.component.ErrorPanel;
import hrms.component.HrmsTable;
import hrms.repository.AttendanceRepository;
import hrms.security.Permissions;
import hrms.security.SecurityService;
import hrms.util.UiThread;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import javax.swing.SpinnerNumberModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One staff member's monthly attendance as a day-by-month table (day of
 * month, status pill, in, out, worked, overtime — every day of the month)
 * with a totals line in the footer.
 *
 * @author aungmrat
 */
public class AttendanceMonthlyView extends javax.swing.JDialog {

  private static final Logger LOG = LoggerFactory.getLogger(AttendanceMonthlyView.class);

  private static final String[] MONTHS = {
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December"
  };

  private static final java.util.logging.Logger logger = java.util.logging.Logger.getLogger(AttendanceMonthlyView.class.getName());

  private final long staffId;
  private final String staffName;
  private final LocalDate monthAnchor;

  /**
   * HrmsTable lives outside the GEN block so Form Designer saves cannot wipe it.
   */
  private HrmsTable table;

  /** Footer totals line, added to the GEN footer panel at runtime. */
  private javax.swing.JLabel labelSummary;

  /**
   * Creates new form AttendanceMonthlyView for one staff member; the month
   * opens on {@code monthAnchor}'s month (usually the picked row's date).
   */
  public AttendanceMonthlyView(java.awt.Frame parent, boolean modal,
          long staffId, String staffName, LocalDate monthAnchor) {
    super(parent, modal);
    SecurityService.require(Permissions.ATTENDANCE_VIEW);
    this.staffId = staffId;
    this.staffName = staffName == null ? "" : staffName;
    this.monthAnchor = monthAnchor == null ? LocalDate.now() : monthAnchor;
    initComponents();
    setTitle("Monthly Attendance - " + this.staffName);
    setupTable();
    fillMonths();
    setupActions();
    reload();
  }

  private void setupTable() {
    table = HrmsTable.builder(
        "Day", "Status", "In", "Out", "Worked", "OT")
        .columnType(0, Integer.class)
        .fixedColumn(0, 55)
        .fixedColumn(1, 120)
        .badgeColumn(1)
        .fixedColumn(2, 75)
        .fixedColumn(3, 75)
        .fixedColumn(4, 90)
        .fixedColumn(5, 70)
        .build();
    table.setRowHeight(48);
    jScrollPane1.setViewportView(table);
  }

  private void fillMonths() {
    comboMonth.removeAllItems();
    for (String month : MONTHS) {
      comboMonth.addItem(month);
    }
    comboMonth.setSelectedIndex(monthAnchor.getMonthValue() - 1);
  }

  private void setupActions() {
    spinnerYear.setModel(new SpinnerNumberModel(
        monthAnchor.getYear(), 2020, 2100, 1));
    spinnerYear.addChangeListener(e -> reload());
    comboMonth.addActionListener(e -> reload());
    labelSummary = new javax.swing.JLabel(" ");
    footer.setLayout(new java.awt.BorderLayout());
    footer.add(labelSummary, java.awt.BorderLayout.CENTER);
  }

  private void reload() {
    jScrollPane1.setViewportView(table);
    int month = comboMonth.getSelectedIndex() + 1;
    if (month < 1) {
      return;
    }
    int year = ((Number) spinnerYear.getValue()).intValue();
    LocalDate from = LocalDate.of(year, month, 1);
    LocalDate to = from.withDayOfMonth(from.lengthOfMonth());
    UiThread.executeAsync("Load monthly view",
        () -> AttendanceRepository.findAllForStaff(staffId, from, to),
        rows -> showRows(year, month, rows),
        this::showLoadError);
  }

  private void showRows(int year, int month, List<hrms.model.Attendance> rows) {
    java.util.Map<LocalDate, hrms.model.Attendance> byDate = new java.util.HashMap<>();
    for (hrms.model.Attendance attendance : rows) {
      if (attendance.getDate() != null) {
        byDate.put(attendance.getDate(), attendance);
      }
    }
    int monthLength = LocalDate.of(year, month, 1).lengthOfMonth();
    List<Object[]> tableRows = new java.util.ArrayList<>(monthLength);
    long present = 0;
    long halfDay = 0;
    long absent = 0;
    long leave = 0;
    BigDecimal hours = BigDecimal.ZERO;
    BigDecimal overtime = BigDecimal.ZERO;
    for (int day = 1; day <= monthLength; day++) {
      hrms.model.Attendance attendance = byDate.get(LocalDate.of(year, month, day));
      tableRows.add(toMonthlyRow(day, attendance));
      if (attendance == null) {
        continue;
      }
      String status = attendance.getStatus();
      if ("PRESENT".equals(status) || "OVERTIME".equals(status)) {
        present++;
      } else if ("HALF_DAY_AM".equals(status) || "HALF_DAY_PM".equals(status)) {
        halfDay++;
      } else if ("ABSENT".equals(status)) {
        absent++;
      } else if ("LEAVE".equals(status)) {
        leave++;
      }
      if (attendance.getTotalHours() != null) {
        hours = hours.add(attendance.getTotalHours());
      }
      if (attendance.getOvertimeHours() != null) {
        overtime = overtime.add(attendance.getOvertimeHours());
      }
    }
    table.setRows(tableRows);
    labelSummary.setText(String.format(
        "Present %d \u00b7 Half-day %d \u00b7 Absent %d \u00b7 Leave %d"
        + " \u00b7 Worked %s h \u00b7 Overtime %s h",
        present, halfDay, absent, leave,
        hours.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString(),
        overtime.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()));
  }

  private static Object[] toMonthlyRow(int day, hrms.model.Attendance attendance) {
    if (attendance == null) {
      return new Object[]{day, "-", "-", "-", "-", "-"};
    }
    String status = attendance.getStatus() == null || attendance.getStatus().isBlank()
        ? "-" : attendance.getStatus();
    return new Object[]{
      day,
      status,
      attendance.getFirstIn() == null ? "-" : attendance.getFirstIn().toString(),
      attendance.getLastOut() == null ? "-" : attendance.getLastOut().toString(),
      hoursText(attendance.getTotalHours()),
      hoursText(attendance.getOvertimeHours())
    };
  }

  private static String hoursText(BigDecimal hours) {
    return hours == null || hours.signum() <= 0 ? "-"
        : hours.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
  }

  private void showLoadError(Exception ex) {
    LOG.warn("Cannot load the monthly view", ex);
    jScrollPane1.setViewportView(new ErrorPanel(
        "Cannot load this staff's monthly view. Check that the database is running, then try again.",
        this::reload));
  }

  /**
   * This method is called from within the constructor to initialize the form.
   * WARNING: Do NOT modify this code. The content of this method is always
   * regenerated by the Form Editor.
   */
  @SuppressWarnings("unchecked")
  // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
  private void initComponents() {

    header = new javax.swing.JPanel();
    labelMonth = new javax.swing.JLabel();
    comboMonth = new javax.swing.JComboBox<>();
    jLabel1 = new javax.swing.JLabel();
    spinnerYear = new javax.swing.JSpinner();
    footer = new javax.swing.JPanel();
    body = new javax.swing.JPanel();
    jScrollPane1 = new javax.swing.JScrollPane();
    tableMonthlyView = new javax.swing.JTable();

    setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);

    header.setPreferredSize(new java.awt.Dimension(700, 60));

    labelMonth.setText("Month:");

    comboMonth.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));

    jLabel1.setText("Year:");

    javax.swing.GroupLayout headerLayout = new javax.swing.GroupLayout(header);
    header.setLayout(headerLayout);
    headerLayout.setHorizontalGroup(
      headerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGroup(headerLayout.createSequentialGroup()
        .addGap(20, 20, 20)
        .addComponent(labelMonth)
        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
        .addComponent(comboMonth, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(40, 40, 40)
        .addComponent(jLabel1)
        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
        .addComponent(spinnerYear, javax.swing.GroupLayout.PREFERRED_SIZE, 82, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addContainerGap(407, Short.MAX_VALUE))
    );
    headerLayout.setVerticalGroup(
      headerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGroup(headerLayout.createSequentialGroup()
        .addGap(16, 16, 16)
        .addGroup(headerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
          .addComponent(labelMonth)
          .addComponent(comboMonth, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
          .addComponent(jLabel1)
          .addComponent(spinnerYear, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
        .addContainerGap(18, Short.MAX_VALUE))
    );

    getContentPane().add(header, java.awt.BorderLayout.PAGE_START);

    footer.setPreferredSize(new java.awt.Dimension(700, 40));

    javax.swing.GroupLayout footerLayout = new javax.swing.GroupLayout(footer);
    footer.setLayout(footerLayout);
    footerLayout.setHorizontalGroup(
      footerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGap(0, 700, Short.MAX_VALUE)
    );
    footerLayout.setVerticalGroup(
      footerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGap(0, 40, Short.MAX_VALUE)
    );

    getContentPane().add(footer, java.awt.BorderLayout.PAGE_END);

    tableMonthlyView.setModel(new javax.swing.table.DefaultTableModel(
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
    jScrollPane1.setViewportView(tableMonthlyView);

    javax.swing.GroupLayout bodyLayout = new javax.swing.GroupLayout(body);
    body.setLayout(bodyLayout);
    bodyLayout.setHorizontalGroup(
      bodyLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 700, Short.MAX_VALUE)
    );
    bodyLayout.setVerticalGroup(
      bodyLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGroup(bodyLayout.createSequentialGroup()
        .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 401, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 0, Short.MAX_VALUE))
    );

    getContentPane().add(body, java.awt.BorderLayout.CENTER);

    pack();
  }// </editor-fold>//GEN-END:initComponents

  /**
   * @param args the command line arguments
   */
  public static void main(String args[]) {
    /* Set the Nimbus look and feel */
    //<editor-fold defaultstate="collapsed" desc=" Look and feel setting code (optional) ">
    /* If Nimbus (introduced in Java SE 6) is not available, stay with the default look and feel.
         * For details see http://download.oracle.com/javase/tutorial/uiswing/lookandfeel/plaf.html 
     */
    try {
      for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
        if ("Nimbus".equals(info.getName())) {
          javax.swing.UIManager.setLookAndFeel(info.getClassName());
          break;
        }
      }
    } catch (ReflectiveOperationException | javax.swing.UnsupportedLookAndFeelException ex) {
      logger.log(java.util.logging.Level.SEVERE, null, ex);
    }
    //</editor-fold>

    /* Create and display the dialog */
    java.awt.EventQueue.invokeLater(new Runnable() {
      @Override
      public void run() {
        AttendanceMonthlyView dialog = new AttendanceMonthlyView(
            new javax.swing.JFrame(), true, 1L, "Sample Staff", LocalDate.now());
        dialog.addWindowListener(new java.awt.event.WindowAdapter() {
          @Override
          public void windowClosing(java.awt.event.WindowEvent e) {
            System.exit(0);
          }
        });
        dialog.setVisible(true);
      }
    });
  }

  // Variables declaration - do not modify//GEN-BEGIN:variables
  private javax.swing.JPanel body;
  private javax.swing.JComboBox<String> comboMonth;
  private javax.swing.JPanel footer;
  private javax.swing.JPanel header;
  private javax.swing.JLabel jLabel1;
  private javax.swing.JScrollPane jScrollPane1;
  private javax.swing.JLabel labelMonth;
  private javax.swing.JSpinner spinnerYear;
  private javax.swing.JTable tableMonthlyView;
  // End of variables declaration//GEN-END:variables
}
