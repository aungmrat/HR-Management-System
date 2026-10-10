/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/GUIForms/JPanel.java to edit this template
 */
package hrms.ui.compensationpolicy;

import hrms.component.ErrorPanel;
import hrms.component.HrmsTable;
import hrms.exception.AuthorizationException;
import hrms.exception.ErrorHandler;
import hrms.repository.OvertimePolicyRepository;
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
public class OvertimePolicy extends javax.swing.JPanel {

  private static final Logger LOG = LoggerFactory.getLogger(OvertimePolicy.class);

  /**
   * HrmsTable lives outside the GEN block so Form Designer saves cannot wipe it.
   */
  private HrmsTable table;

  /**
   * Creates new form OvertimePolicy
   */
  public OvertimePolicy() {
    initComponents();
    setupTable();
    setupActions();
  }

  private void setupActions() {
    btnNewOvertimePolicy.addActionListener(e -> runGuarded(this::openNewPolicyDialog));
    btnDelete.addActionListener(e -> runGuarded(this::deleteSelectedPolicy));
    if (!SecurityService.can(Permissions.OVERTIME_POLICY_CREATE)) {
      btnNewOvertimePolicy.setEnabled(false);
      btnNewOvertimePolicy.setToolTipText("Requires permission: OVERTIME_POLICY_CREATE");
    }
    if (!SecurityService.can(Permissions.OVERTIME_POLICY_UPDATE)) {
      btnDelete.setEnabled(false);
      btnDelete.setToolTipText("Requires permission: OVERTIME_POLICY_UPDATE");
    }
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

  private void openNewPolicyDialog() {
    SecurityService.require(Permissions.OVERTIME_POLICY_CREATE);
    OvertimePolicyDialog dialog = new OvertimePolicyDialog(ownerFrame(), true, null);
    dialog.setLocationRelativeTo(this);
    dialog.setVisible(true);
    loadPolicies();
  }

  private void openEditPolicyDialog(long id) {
    SecurityService.require(Permissions.OVERTIME_POLICY_UPDATE);
    UiThread.executeAsync("Load overtime policy",
        () -> OvertimePolicyRepository.findById(id),
        optional -> optional.ifPresent(policy -> {
          OvertimePolicyDialog dialog = new OvertimePolicyDialog(ownerFrame(), true, policy);
          dialog.setLocationRelativeTo(this);
          dialog.setVisible(true);
          loadPolicies();
        }));
  }

  private void deleteSelectedPolicy() {
    SecurityService.require(Permissions.OVERTIME_POLICY_UPDATE);
    int modelRow = table.selectedModelRow();
    if (modelRow < 0) {
      Dialogs.info(this, "Delete Overtime Policy", "Select an overtime policy row first.");
      return;
    }
    long id = ((Number) table.getModel().getValueAt(modelRow, 0)).longValue();
    String minHour = String.valueOf(table.getModel().getValueAt(modelRow, 1));
    String maxHour = String.valueOf(table.getModel().getValueAt(modelRow, 2));
    if (!Dialogs.confirm(this, "Delete Overtime Policy",
        "Delete overtime policy (" + minHour + " - " + maxHour + " hrs)? This is a soft delete.")) {
      return;
    }
    Long userId = SessionContext.isAuthenticated() ? SessionContext.currentUserId() : null;
    UiThread.executeAsync("Delete overtime policy",
        () -> OvertimePolicyRepository.softDelete(id, userId),
        deleted -> loadPolicies());
  }

  private void setupTable() {
    table = HrmsTable.builder(
        "ID", "Min OT Hour", "Max OT Hour / Month", "OT Multiplier",
        "Weekend Multiplier", "Holiday Multiplier", "Created By", "Created Date")
        .hiddenColumn(0)
        .columnType(1, Integer.class)
        .columnType(2, Integer.class)
        .columnType(3, java.math.BigDecimal.class)
        .columnType(4, java.math.BigDecimal.class)
        .columnType(5, java.math.BigDecimal.class)
        .fixedColumn(1, 110)
        .fixedColumn(2, 150)
        .fixedColumn(3, 120)
        .fixedColumn(4, 150)
        .fixedColumn(5, 150)
        .fixedColumn(6, 120)
        .fixedColumn(7, 130)
        .onDoubleClick((viewRow, modelRow) -> runGuarded(() -> {
          long id = ((Number) table.getModel().getValueAt(modelRow, 0)).longValue();
          openEditPolicyDialog(id);
        }))
        .contextMenu(() -> {
          javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();
          javax.swing.JMenuItem editItem = new javax.swing.JMenuItem("Edit");
          editItem.addActionListener(e -> runGuarded(() -> {
            int row = table.selectedModelRow();
            if (row >= 0) {
              long id = ((Number) table.getModel().getValueAt(row, 0)).longValue();
              openEditPolicyDialog(id);
            }
          }));
          javax.swing.JMenuItem deleteItem = new javax.swing.JMenuItem("Delete");
          deleteItem.addActionListener(e -> runGuarded(this::deleteSelectedPolicy));
          menu.add(editItem);
          menu.add(deleteItem);
          return menu;
        })
        .build();
    jScrollPane1.setViewportView(table);

    loadPolicies();
  }

  private void loadPolicies() {
    UiThread.executeAsync("Load overtime policies",
        () -> OvertimePolicyRepository.findAllAsTableRows(),
        rows -> {
          // Restore the table in case a previous load failed and showed ErrorPanel.
          jScrollPane1.setViewportView(table);
          table.setRows(rows);
        },
        ex -> showLoadError(ex));
  }

  /**
   * Swaps the table area for an inline error state with retry (details go to the
   * log).
   */
  private void showLoadError(Exception ex) {
    LOG.warn("Cannot load overtime policy list", ex);
    jScrollPane1.setViewportView(new ErrorPanel(
        "Cannot load the overtime policy list. Check that the database is running, then try again.",
        this::loadPolicies));
  }

  public void refreshTable() {
    loadPolicies();
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
    btnNewOvertimePolicy = new javax.swing.JButton();
    btnDelete = new javax.swing.JButton();
    footer = new javax.swing.JPanel();
    body = new javax.swing.JPanel();
    jScrollPane1 = new javax.swing.JScrollPane();
    tableOvertimePolicy = new javax.swing.JTable();

    setLayout(new java.awt.BorderLayout());

    header.setPreferredSize(new java.awt.Dimension(1237, 60));

    btnNewOvertimePolicy.setText("+ New Overtime Policy");

    btnDelete.setText("Delete");

    javax.swing.GroupLayout headerLayout = new javax.swing.GroupLayout(header);
    header.setLayout(headerLayout);
    headerLayout.setHorizontalGroup(
      headerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, headerLayout.createSequentialGroup()
        .addContainerGap(69, Short.MAX_VALUE)
        .addComponent(btnDelete)
        .addGap(40, 40, 40)
        .addComponent(btnNewOvertimePolicy)
        .addGap(60, 60, 60))
    );
    headerLayout.setVerticalGroup(
      headerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGroup(headerLayout.createSequentialGroup()
        .addGap(17, 17, 17)
        .addGroup(headerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
          .addComponent(btnNewOvertimePolicy)
          .addComponent(btnDelete))
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

    tableOvertimePolicy.setModel(new javax.swing.table.DefaultTableModel(
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
    jScrollPane1.setViewportView(tableOvertimePolicy);

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
  private javax.swing.JButton btnDelete;
  private javax.swing.JButton btnNewOvertimePolicy;
  private javax.swing.JPanel footer;
  private javax.swing.JPanel header;
  private javax.swing.JScrollPane jScrollPane1;
  private javax.swing.JTable tableOvertimePolicy;
  // End of variables declaration//GEN-END:variables
}
