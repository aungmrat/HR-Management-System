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
import hrms.model.LeaveApplication;
import hrms.repository.LeaveApplicationRepository;
import hrms.security.Permissions;
import hrms.security.SecurityService;
import hrms.security.SessionContext;
import hrms.util.Dialogs;
import hrms.util.UiThread;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author aungmrat
 */
public class LeaveApproval extends javax.swing.JPanel {

  private static final Logger LOG = LoggerFactory.getLogger(LeaveApproval.class);

  /**
   * HrmsTable lives outside the GEN block so Form Designer saves cannot wipe it.
   */
  private HrmsTable table;

  /**
   * Creates new form LeaveApproval
   */
  public LeaveApproval() {
    initComponents();
    setupTable();
    setupActions();
    loadQueue();
  }

  private void setupTable() {
    table = HrmsTable.builder(
        "ID", "Staff", "Leave Type", "From Date", "To Date", "Total Days",
        "Status", "Requested Date", "Remark")
        .hiddenColumn(0)
        .badgeColumn(6)
        .columnType(5, java.math.BigDecimal.class)
        .fixedColumn(1, 180)
        .fixedColumn(2, 150)
        .fixedColumn(3, 100)
        .fixedColumn(4, 100)
        .fixedColumn(5, 90)
        .fixedColumn(6, 95)
        .fixedColumn(7, 110)
        .fixedColumn(8, 150)
        .build();
    jScrollPane1.setViewportView(table);
  }

  private void setupActions() {
    btnApprove.addActionListener(e -> runGuarded(this::approveSelected));
    btnReject.addActionListener(e -> runGuarded(this::rejectSelected));
    if (!SecurityService.can(Permissions.LEAVE_APPROVE)) {
      btnApprove.setEnabled(false);
      btnApprove.setToolTipText("Requires permission: LEAVE_APPROVE");
      btnReject.setEnabled(false);
      btnReject.setToolTipText("Requires permission: LEAVE_APPROVE");
    }
    if (textSearch instanceof SearchField sf) {
      sf.onTextChanged(keyword -> loadQueue());
    }
    comboStatus.addActionListener(e -> loadQueue());
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

  /** Entry point wired into MainFrame's refresher map. */
  public void refreshTable() {
    loadQueue();
  }

  /**
   * Maps the filter combo value to the DB status literal.
   * "All Status" and blank values map to null (no status filter).
   */
  private String selectedStatusFilter() {
    Object selected = comboStatus.getSelectedItem();
    if (selected == null) {
      return null;
    }
    String value = selected.toString().trim();
    if (value.isEmpty() || value.equalsIgnoreCase("All Status")) {
      return null;
    }
    return value.toUpperCase();
  }

  private void loadQueue() {
    jScrollPane1.setViewportView(table);
    String keyword = textSearch.getText() == null ? "" : textSearch.getText().trim();
    String status = selectedStatusFilter();
    UiThread.executeAsync("Load leave approval queue",
        () -> {
          var rows = LeaveApplicationRepository.findAllAsTableRows(keyword, status, null);
          java.util.List<Object[]> trimmed = new java.util.ArrayList<>(rows.size());
          for (Object[] row : rows) {
            // Queue view: ID, Staff, Type, From, To, Total, Status, Requested, Remark
            trimmed.add(new Object[]{row[0], row[1], row[2], row[3], row[4],
              row[5], row[8], row[9], row[12]});
          }
          return trimmed;
        },
        table::setRows,
        error -> {
          LOG.warn("Cannot load leave approval queue", error);
          jScrollPane1.setViewportView(new ErrorPanel(
                  "Cannot load the leave approval queue. Check that the database is "
                  + "running, then try again.", this::loadQueue));
        });
  }

  private void approveSelected() {
    SecurityService.require(Permissions.LEAVE_APPROVE);
    int modelRow = table.selectedModelRow();
    if (modelRow < 0) {
      Dialogs.info(this, "Leave Approval", "Select a leave application first.");
      return;
    }
    long id = ((Number) table.getModel().getValueAt(modelRow, 0)).longValue();
    String staff = String.valueOf(table.getModel().getValueAt(modelRow, 1));
    if (!Dialogs.confirm(this, "Approve Leave",
            "Approve leave for '" + staff + "'? Used days are deducted from the balance.")) {
      return;
    }
    Long approverStaffId = SessionContext.currentEmployeeId();
    if (approverStaffId == null) {
      Dialogs.info(this, "Approve Leave",
              "Your account has no linked staff profile, so it cannot be recorded "
              + "as the approver.");
      return;
    }
    Long userId = SessionContext.isAuthenticated() ? SessionContext.currentUserId() : null;
    UiThread.executeAsync("Load leave application",
        () -> LeaveApplicationRepository.findById(id),
        optional -> optional.ifPresent(application -> {
          if (!application.isPending()) {
            Dialogs.info(this, "Approve Leave",
                    "This application is no longer pending.");
            loadQueue();
            return;
          }
          if (approverStaffId != null && application.getStaffId() == approverStaffId) {
            Dialogs.info(this, "Approve Leave",
                    "You cannot approve your own leave application.");
            return;
          }
          UiThread.executeAsync("Approve leave",
              () -> {
                LeaveApplicationRepository.approve(id, approverStaffId, userId, application);
                return null;
              },
              result -> {
                LOG.info("Leave application {} approved by staff #{}", id, approverStaffId);
                refreshTable();
              },
              error -> ErrorHandler.handle(this, error));
        }),
        error -> ErrorHandler.handle(this, error));
  }

  private void rejectSelected() {
    SecurityService.require(Permissions.LEAVE_APPROVE);
    int modelRow = table.selectedModelRow();
    if (modelRow < 0) {
      Dialogs.info(this, "Leave Approval", "Select a leave application first.");
      return;
    }
    long id = ((Number) table.getModel().getValueAt(modelRow, 0)).longValue();
    String staff = String.valueOf(table.getModel().getValueAt(modelRow, 1));
    String input = Dialogs.prompt(this, "Reject Leave",
            "Reason for rejecting leave for '" + staff + "':");
    if (input == null) {
      return;
    }
    String reason = input.trim();
    if (reason.isEmpty()) {
      Dialogs.info(this, "Reject Leave", "A rejection reason is required.");
      return;
    }
    Long approverStaffId = SessionContext.currentEmployeeId();
    if (approverStaffId == null) {
      Dialogs.info(this, "Reject Leave",
              "Your account has no linked staff profile, so it cannot be recorded "
              + "as the approver.");
      return;
    }
    Long userId = SessionContext.isAuthenticated() ? SessionContext.currentUserId() : null;
    UiThread.executeAsync("Load leave application",
        () -> LeaveApplicationRepository.findById(id),
        optional -> optional.ifPresent(application -> {
          if (!application.isPending()) {
            Dialogs.info(this, "Reject Leave",
                    "This application is no longer pending.");
            loadQueue();
            return;
          }
          if (approverStaffId != null && application.getStaffId() == approverStaffId) {
            Dialogs.info(this, "Reject Leave",
                    "You cannot reject your own leave application.");
            return;
          }
          UiThread.executeAsync("Reject leave",
              () -> {
                LeaveApplicationRepository.reject(id, approverStaffId, userId,
                        reason, application);
                return null;
              },
              result -> {
                LOG.info("Leave application {} rejected by staff #{}", id, approverStaffId);
                refreshTable();
              },
              error -> ErrorHandler.handle(this, error));
        }),
        error -> ErrorHandler.handle(this, error));
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
    textSearch =  new SearchField("Search by name or code...");
    btnApprove = new javax.swing.JButton();
    btnReject = new javax.swing.JButton();
    comboStatus = new javax.swing.JComboBox<>();
    footer = new javax.swing.JPanel();
    body = new javax.swing.JPanel();
    jScrollPane1 = new javax.swing.JScrollPane();
    tableLeaveApproval = new javax.swing.JTable();

    setLayout(new java.awt.BorderLayout());

    header.setPreferredSize(new java.awt.Dimension(1237, 60));

    btnApprove.setText("Approve");

    btnReject.setText("Reject");

    comboStatus.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "All Status", "PENDING", "APPROVED", "REJECTED" }));

    javax.swing.GroupLayout headerLayout = new javax.swing.GroupLayout(header);
    header.setLayout(headerLayout);
    headerLayout.setHorizontalGroup(
      headerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGroup(headerLayout.createSequentialGroup()
        .addGap(60, 60, 60)
        .addComponent(textSearch, javax.swing.GroupLayout.PREFERRED_SIZE, 220, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 570, Short.MAX_VALUE)
        .addComponent(comboStatus, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(40, 40, 40)
        .addComponent(btnReject)
        .addGap(40, 40, 40)
        .addComponent(btnApprove)
        .addGap(60, 60, 60))
    );
    headerLayout.setVerticalGroup(
      headerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGroup(headerLayout.createSequentialGroup()
        .addGap(17, 17, 17)
        .addGroup(headerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
          .addComponent(textSearch, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
          .addComponent(btnApprove)
          .addComponent(btnReject)
          .addComponent(comboStatus, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
        .addContainerGap(16, Short.MAX_VALUE))
    );

    add(header, java.awt.BorderLayout.PAGE_START);

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

    add(footer, java.awt.BorderLayout.PAGE_END);

    tableLeaveApproval.setModel(new javax.swing.table.DefaultTableModel(
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
    jScrollPane1.setViewportView(tableLeaveApproval);

    javax.swing.GroupLayout bodyLayout = new javax.swing.GroupLayout(body);
    body.setLayout(bodyLayout);
    bodyLayout.setHorizontalGroup(
      bodyLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addComponent(jScrollPane1, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 1237, Short.MAX_VALUE)
    );
    bodyLayout.setVerticalGroup(
      bodyLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 740, Short.MAX_VALUE)
    );

    add(body, java.awt.BorderLayout.CENTER);
  }// </editor-fold>//GEN-END:initComponents


  // Variables declaration - do not modify//GEN-BEGIN:variables
  private javax.swing.JPanel body;
  private javax.swing.JButton btnApprove;
  private javax.swing.JButton btnReject;
  private javax.swing.JComboBox<String> comboStatus;
  private javax.swing.JPanel footer;
  private javax.swing.JPanel header;
  private javax.swing.JScrollPane jScrollPane1;
  private javax.swing.JTable tableLeaveApproval;
  private javax.swing.JTextField textSearch;
  // End of variables declaration//GEN-END:variables
}
