/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/GUIForms/JPanel.java to edit this template
 */
package hrms.ui.leavemanagement;


import hrms.component.ErrorPanel;
import hrms.component.HrmsTable;
import hrms.component.SearchField;
import hrms.exception.AuthorizationException;
import hrms.exception.ErrorHandler;
import hrms.repository.LeaveApplicationRepository;
import hrms.repository.LeaveEntitlementRepository;
import hrms.security.Permissions;
import hrms.security.SecurityService;
import hrms.security.SessionContext;
import hrms.util.Dialogs;
import hrms.util.UiThread;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author aungmrat
 */
public class LeaveApplication extends javax.swing.JPanel {

  private static final Logger LOG = LoggerFactory.getLogger(LeaveApplication.class);

  /**
   * HrmsTable lives outside the GEN block so Form Designer saves cannot wipe it.
   */
  private HrmsTable table;
  private HrmsTable balanceTable;
  /** Non-null when the signed-in user may only see their own rows. */
  private final Long selfStaffId;
  private final boolean canPickAnyStaff;

  /**
   * Creates new form LeaveApplication
   */
  public LeaveApplication() {
    initComponents();
    canPickAnyStaff = SecurityService.can(Permissions.STAFF_VIEW);
    selfStaffId = canPickAnyStaff ? null : SessionContext.currentEmployeeId();
    comboStatus.setModel(new javax.swing.DefaultComboBoxModel<>(
            new String[]{"All Status", "PENDING", "APPROVED", "REJECTED", "CANCELLED"}));
    spinnerYear.setValue(java.time.LocalDate.now().getYear());
    setupTables();
    setupActions();
    refreshTable();
  }

  private void setupActions() {
    btnNewLeaveRequest.addActionListener(e -> runGuarded(this::openNewApplicationDialog));
    if (!SecurityService.can(Permissions.LEAVE_APPLICATION_CREATE)) {
      btnNewLeaveRequest.setEnabled(false);
      btnNewLeaveRequest.setToolTipText("Requires permission: LEAVE_APPLICATION_CREATE");
    }
    if (textSearch instanceof SearchField sf) {
      sf.onTextChanged(keyword -> loadApplications());
    }
    comboStatus.addActionListener(e -> loadApplications());
    if (textbSearch instanceof SearchField sf) {
      sf.onTextChanged(keyword -> loadBalances());
    }
    spinnerYear.addChangeListener(e -> loadBalances());
    tabs.addChangeListener(e -> {
      if (tabs.getSelectedComponent() == balancePanel) {
        loadBalances();
      }
    });
  }

  /**
   * Runs an action, surfacing a permission failure as an Access Denied dialog
   * instead of an uncaught exception on the event thread.
   */
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

  private void openNewApplicationDialog() {
    SecurityService.require(Permissions.LEAVE_APPLICATION_CREATE);
    LeaveApplicationDialog dialog = new LeaveApplicationDialog(ownerFrame(), true);
    dialog.setLocationRelativeTo(this);
    dialog.setVisible(true);
    refreshTable();
  }

  private void openEditApplicationDialog(long id) {
    UiThread.executeAsync("Load leave application",
        () -> LeaveApplicationRepository.findById(id),
        optional -> optional.ifPresent((hrms.model.LeaveApplication application) -> {
          if (!application.isPending()) {
            Dialogs.info(this, "Leave Application",
                    "Only PENDING applications can be edited.");
            return;
          }
          if (!canPickAnyStaff && application.getStaffId() != selfStaffId) {
            Dialogs.info(this, "Leave Application",
                    "You can only edit your own applications.");
            return;
          }
          LeaveApplicationDialog dialog =
                  new LeaveApplicationDialog(ownerFrame(), true, application);
          dialog.setLocationRelativeTo(this);
          dialog.setVisible(true);
          refreshTable();
        }));
  }

  private void setupTables() {
    table = HrmsTable.builder(
        "ID", "Staff", "Leave Type", "From Date", "To Date", "Total Days",
        "Business Days", "Half Day", "Status", "Requested Date", "Approver",
        "Approved Date", "Remark")
        .hiddenColumn(0)
        .columnType(5, java.math.BigDecimal.class)
        .columnType(6, java.math.BigDecimal.class)
        .fixedColumn(1, 180)
        .fixedColumn(2, 150)
        .fixedColumn(3, 100)
        .fixedColumn(4, 100)
        .fixedColumn(5, 90)
        .fixedColumn(6, 100)
        .fixedColumn(7, 80)
        .fixedColumn(8, 95)
        .fixedColumn(9, 110)
        .fixedColumn(10, 150)
        .fixedColumn(11, 110)
        .fixedColumn(12, 130)
        .onDoubleClick((viewRow, modelRow) -> runGuarded(() -> {
          long id = ((Number) table.getModel().getValueAt(modelRow, 0)).longValue();
          openEditApplicationDialog(id);
        }))
        .build();
    jScrollPane1.setViewportView(table);

    boolean includeStaff = canPickAnyStaff;
    String[] balanceHeaders = includeStaff
            ? new String[]{"Staff", "Leave Type", "Entitled", "Carried", "Used",
              "Remaining", "Effective Date", "Expiry Date"}
            : new String[]{"Leave Type", "Entitled", "Carried", "Used",
              "Remaining", "Effective Date", "Expiry Date"};
    balanceTable = HrmsTable.builder(balanceHeaders)
        .columnType(includeStaff ? 2 : 1, Integer.class)
        .columnType(includeStaff ? 3 : 2, Integer.class)
        .columnType(includeStaff ? 4 : 3, Integer.class)
        .columnType(includeStaff ? 5 : 4, Integer.class)
        .fixedColumn(0, includeStaff ? 180 : 150)
        .fixedColumn(1, 150)
        .fixedColumn(2, 90)
        .fixedColumn(3, 90)
        .fixedColumn(4, 90)
        .fixedColumn(5, 95)
        .fixedColumn(6, 110)
        .fixedColumn(7, 110)
        .build();
    jScrollPane3.setViewportView(balanceTable);
  }

  /** Entry point wired into MainFrame's refresher map. */
  public void refreshTable() {
    loadApplications();
    loadBalances();
  }

  private void loadApplications() {
    jScrollPane1.setViewportView(table);
    String keyword = textSearch.getText() == null ? "" : textSearch.getText().trim();
    Object selected = comboStatus.getSelectedItem();
    String status = selected == null || "All Status".equals(selected.toString())
            ? null : selected.toString();
    UiThread.executeAsync("Load leave applications",
        () -> LeaveApplicationRepository.findAllAsTableRows(keyword, status, selfStaffId),
        rows -> table.setRows(rows),
        error -> {
          LOG.warn("Cannot load leave application list", error);
          jScrollPane1.setViewportView(new ErrorPanel(
                  "Cannot load the leave application list. Check that the database is "
                  + "running, then try again.", this::loadApplications));
        });
  }

  private void loadBalances() {
    jScrollPane3.setViewportView(balanceTable);
    int year = (int) spinnerYear.getValue();
    String keyword = textbSearch.getText() == null ? "" : textbSearch.getText().trim();
    boolean includeStaff = canPickAnyStaff;
    UiThread.executeAsync("Load leave balances",
        () -> LeaveEntitlementRepository.listBalanceRows(keyword, year, selfStaffId),
        rows -> {
          java.util.List<Object[]> tableRows = new java.util.ArrayList<>(rows.size());
          for (LeaveEntitlementRepository.BalanceRow row : rows) {
            tableRows.add(row.toTableRow(includeStaff));
          }
          balanceTable.setRows(tableRows);
        },
        error -> {
          LOG.warn("Cannot load leave balance list", error);
          jScrollPane3.setViewportView(new ErrorPanel(
                  "Cannot load the leave balance list. Check that the database is "
                  + "running, then try again.", this::loadBalances));
        });
  }

  /**
   * This method is called from within the constructor to initialize the form.
   * WARNING: Do NOT modify this code. The content of this method is always
   * regenerated by the Form Editor.
   */
  @SuppressWarnings("unchecked")
  // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
  private void initComponents() {

    tabs = new javax.swing.JTabbedPane();
    leaveApplicationPanel = new javax.swing.JPanel();
    header = new javax.swing.JPanel();
    btnNewLeaveRequest = new javax.swing.JButton();
    comboStatus = new javax.swing.JComboBox<>();
    textSearch =  new SearchField("Search by name or code...");
    footer = new javax.swing.JPanel();
    body = new javax.swing.JPanel();
    jScrollPane1 = new javax.swing.JScrollPane();
    tableLeaveApplication = new javax.swing.JTable();
    balancePanel = new javax.swing.JPanel();
    bHeader = new javax.swing.JPanel();
    textbSearch =  new SearchField("Search by staff...");
    labelYear = new javax.swing.JLabel();
    spinnerYear = new javax.swing.JSpinner();
    bFooter = new javax.swing.JPanel();
    bBody = new javax.swing.JPanel();
    jScrollPane3 = new javax.swing.JScrollPane();
    tableBalance = new javax.swing.JTable();

    leaveApplicationPanel.setLayout(new java.awt.BorderLayout());

    header.setPreferredSize(new java.awt.Dimension(1237, 60));

    btnNewLeaveRequest.setText("+ New Leave Request ");

    comboStatus.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "All Status" }));

    javax.swing.GroupLayout headerLayout = new javax.swing.GroupLayout(header);
    header.setLayout(headerLayout);
    headerLayout.setHorizontalGroup(
      headerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, headerLayout.createSequentialGroup()
        .addGap(60, 60, 60)
        .addComponent(textSearch, javax.swing.GroupLayout.PREFERRED_SIZE, 220, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 619, Short.MAX_VALUE)
        .addComponent(comboStatus, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(40, 40, 40)
        .addComponent(btnNewLeaveRequest)
        .addGap(60, 60, 60))
    );
    headerLayout.setVerticalGroup(
      headerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, headerLayout.createSequentialGroup()
        .addContainerGap(18, Short.MAX_VALUE)
        .addGroup(headerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
          .addComponent(btnNewLeaveRequest)
          .addComponent(comboStatus, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
          .addComponent(textSearch, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
        .addGap(15, 15, 15))
    );

    leaveApplicationPanel.add(header, java.awt.BorderLayout.PAGE_START);

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

    leaveApplicationPanel.add(footer, java.awt.BorderLayout.PAGE_END);

    tableLeaveApplication.setModel(new javax.swing.table.DefaultTableModel(
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
    jScrollPane1.setViewportView(tableLeaveApplication);

    javax.swing.GroupLayout bodyLayout = new javax.swing.GroupLayout(body);
    body.setLayout(bodyLayout);
    bodyLayout.setHorizontalGroup(
      bodyLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addComponent(jScrollPane1, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 1237, Short.MAX_VALUE)
    );
    bodyLayout.setVerticalGroup(
      bodyLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 829, Short.MAX_VALUE)
    );

    leaveApplicationPanel.add(body, java.awt.BorderLayout.CENTER);

    tabs.addTab("Leave Application", leaveApplicationPanel);

    balancePanel.setLayout(new java.awt.BorderLayout());

    bHeader.setPreferredSize(new java.awt.Dimension(1237, 60));

    labelYear.setText("Year:");

    javax.swing.GroupLayout bHeaderLayout = new javax.swing.GroupLayout(bHeader);
    bHeader.setLayout(bHeaderLayout);
    bHeaderLayout.setHorizontalGroup(
      bHeaderLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGroup(bHeaderLayout.createSequentialGroup()
        .addGap(60, 60, 60)
        .addComponent(textbSearch, javax.swing.GroupLayout.PREFERRED_SIZE, 220, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(40, 40, 40)
        .addComponent(labelYear)
        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
        .addComponent(spinnerYear, javax.swing.GroupLayout.PREFERRED_SIZE, 90, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addContainerGap(796, Short.MAX_VALUE))
    );
    bHeaderLayout.setVerticalGroup(
      bHeaderLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGroup(bHeaderLayout.createSequentialGroup()
        .addGap(17, 17, 17)
        .addGroup(bHeaderLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
          .addComponent(textbSearch, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
          .addComponent(labelYear)
          .addComponent(spinnerYear, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
        .addContainerGap(17, Short.MAX_VALUE))
    );

    balancePanel.add(bHeader, java.awt.BorderLayout.PAGE_START);

    bFooter.setPreferredSize(new java.awt.Dimension(1237, 40));

    javax.swing.GroupLayout bFooterLayout = new javax.swing.GroupLayout(bFooter);
    bFooter.setLayout(bFooterLayout);
    bFooterLayout.setHorizontalGroup(
      bFooterLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGap(0, 1237, Short.MAX_VALUE)
    );
    bFooterLayout.setVerticalGroup(
      bFooterLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGap(0, 40, Short.MAX_VALUE)
    );

    balancePanel.add(bFooter, java.awt.BorderLayout.PAGE_END);

    tableBalance.setModel(new javax.swing.table.DefaultTableModel(
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
    jScrollPane3.setViewportView(tableBalance);

    javax.swing.GroupLayout bBodyLayout = new javax.swing.GroupLayout(bBody);
    bBody.setLayout(bBodyLayout);
    bBodyLayout.setHorizontalGroup(
      bBodyLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addComponent(jScrollPane3, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 1237, Short.MAX_VALUE)
    );
    bBodyLayout.setVerticalGroup(
      bBodyLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addComponent(jScrollPane3, javax.swing.GroupLayout.DEFAULT_SIZE, 829, Short.MAX_VALUE)
    );

    balancePanel.add(bBody, java.awt.BorderLayout.CENTER);

    tabs.addTab("Balance", balancePanel);

    javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
    this.setLayout(layout);
    layout.setHorizontalGroup(
      layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addComponent(tabs, javax.swing.GroupLayout.Alignment.TRAILING)
    );
    layout.setVerticalGroup(
      layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addComponent(tabs)
    );
  }// </editor-fold>//GEN-END:initComponents


  // Variables declaration - do not modify//GEN-BEGIN:variables
  private javax.swing.JPanel bBody;
  private javax.swing.JPanel bFooter;
  private javax.swing.JPanel bHeader;
  private javax.swing.JPanel balancePanel;
  private javax.swing.JPanel body;
  private javax.swing.JButton btnNewLeaveRequest;
  private javax.swing.JComboBox<String> comboStatus;
  private javax.swing.JPanel footer;
  private javax.swing.JPanel header;
  private javax.swing.JScrollPane jScrollPane1;
  private javax.swing.JScrollPane jScrollPane3;
  private javax.swing.JLabel labelYear;
  private javax.swing.JPanel leaveApplicationPanel;
  private javax.swing.JSpinner spinnerYear;
  private javax.swing.JTable tableBalance;
  private javax.swing.JTable tableLeaveApplication;
  private javax.swing.JTabbedPane tabs;
  private javax.swing.JTextField textSearch;
  private javax.swing.JTextField textbSearch;
  // End of variables declaration//GEN-END:variables
}
