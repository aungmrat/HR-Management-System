/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/GUIForms/JPanel.java to edit this template
 */
package hrms.ui.compensationpolicy;

import hrms.component.ErrorPanel;
import hrms.component.HrmsTable;
import hrms.exception.AuthorizationException;
import hrms.exception.ErrorHandler;
import hrms.repository.SalaryRateRepository;
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
public class SalaryRate extends javax.swing.JPanel {

  private static final Logger LOG = LoggerFactory.getLogger(SalaryRate.class);

  /**
   * HrmsTable lives outside the GEN block so Form Designer saves cannot wipe it.
   */
  private HrmsTable table;

  /**
   * Creates new form SalaryRate
   */
  public SalaryRate() {
    initComponents();
    setupTable();
    setupActions();
  }

  private void setupActions() {
    btnNewSalaryRate.addActionListener(e -> runGuarded(this::openNewSalaryRateDialog));
    btnDelete.addActionListener(e -> runGuarded(this::deleteSelectedSalaryRate));
    if (!SecurityService.can(Permissions.SALARY_RATE_CREATE)) {
      btnNewSalaryRate.setEnabled(false);
      btnNewSalaryRate.setToolTipText("Requires permission: SALARY_RATE_CREATE");
    }
    if (!SecurityService.can(Permissions.SALARY_RATE_UPDATE)) {
      btnDelete.setEnabled(false);
      btnDelete.setToolTipText("Requires permission: SALARY_RATE_UPDATE");
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

  private void openNewSalaryRateDialog() {
    SecurityService.require(Permissions.SALARY_RATE_CREATE);
    SalaryRateDialog dialog = new SalaryRateDialog(ownerFrame(), true, null);
    dialog.setLocationRelativeTo(this);
    dialog.setVisible(true);
    loadSalaryRates();
  }

  private void openEditSalaryRateDialog(long id) {
    SecurityService.require(Permissions.SALARY_RATE_UPDATE);
    UiThread.executeAsync("Load salary rate",
        () -> SalaryRateRepository.findById(id),
        optional -> optional.ifPresent(salaryRate -> {
          SalaryRateDialog dialog = new SalaryRateDialog(ownerFrame(), true, salaryRate);
          dialog.setLocationRelativeTo(this);
          dialog.setVisible(true);
          loadSalaryRates();
        }));
  }

  private void deleteSelectedSalaryRate() {
    SecurityService.require(Permissions.SALARY_RATE_UPDATE);
    int modelRow = table.selectedModelRow();
    if (modelRow < 0) {
      Dialogs.info(this, "Delete Salary Rate", "Select a salary rate row first.");
      return;
    }
    long id = ((Number) table.getModel().getValueAt(modelRow, 0)).longValue();
    String role = String.valueOf(table.getModel().getValueAt(modelRow, 1));
    if (!Dialogs.confirm(this, "Delete Salary Rate",
        "Delete salary rate for '" + role + "'? This is a soft delete.")) {
      return;
    }
    Long userId = SessionContext.isAuthenticated() ? SessionContext.currentUserId() : null;
    UiThread.executeAsync("Delete salary rate",
        () -> SalaryRateRepository.softDelete(id, userId),
        deleted -> loadSalaryRates());
  }

  private void setupTable() {
    table = HrmsTable.builder(
        "ID", "Role", "Base Salary", "Ceil Salary", "Currency", "Pay Frequency",
        "Remark", "Created By", "Created Date")
        .hiddenColumn(0)
        .columnType(2, java.math.BigDecimal.class)
        .columnType(3, java.math.BigDecimal.class)
        .fixedColumn(1, 150)
        .fixedColumn(2, 110)
        .fixedColumn(3, 110)
        .fixedColumn(4, 80)
        .fixedColumn(5, 110)
        .fixedColumn(6, 150)
        .fixedColumn(7, 110)
        .fixedColumn(8, 130)
        .onDoubleClick((viewRow, modelRow) -> runGuarded(() -> {
          long id = ((Number) table.getModel().getValueAt(modelRow, 0)).longValue();
          openEditSalaryRateDialog(id);
        }))
        .contextMenu(() -> {
          javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();
          javax.swing.JMenuItem editItem = new javax.swing.JMenuItem("Edit");
          editItem.addActionListener(e -> runGuarded(() -> {
            int row = table.selectedModelRow();
            if (row >= 0) {
              long id = ((Number) table.getModel().getValueAt(row, 0)).longValue();
              openEditSalaryRateDialog(id);
            }
          }));
          javax.swing.JMenuItem deleteItem = new javax.swing.JMenuItem("Delete");
          deleteItem.addActionListener(e -> runGuarded(this::deleteSelectedSalaryRate));
          menu.add(editItem);
          menu.add(deleteItem);
          return menu;
        })
        .build();
    jScrollPane1.setViewportView(table);

    loadSalaryRates();
  }

  private void loadSalaryRates() {
    UiThread.executeAsync("Load salary rates",
        () -> SalaryRateRepository.findAllAsTableRows(),
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
    LOG.warn("Cannot load salary rate list", ex);
    jScrollPane1.setViewportView(new ErrorPanel(
        "Cannot load the salary rate list. Check that the database is running, then try again.",
        this::loadSalaryRates));
  }

  public void refreshTable() {
    loadSalaryRates();
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
    btnNewSalaryRate = new javax.swing.JButton();
    btnDelete = new javax.swing.JButton();
    footer = new javax.swing.JPanel();
    body = new javax.swing.JPanel();
    jScrollPane1 = new javax.swing.JScrollPane();
    tableSalaryRate = new javax.swing.JTable();

    setLayout(new java.awt.BorderLayout());

    header.setPreferredSize(new java.awt.Dimension(1237, 60));

    btnNewSalaryRate.setText("+ New Salary Rate");

    btnDelete.setText("Delete");

    javax.swing.GroupLayout headerLayout = new javax.swing.GroupLayout(header);
    header.setLayout(headerLayout);
    headerLayout.setHorizontalGroup(
      headerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, headerLayout.createSequentialGroup()
        .addContainerGap(931, Short.MAX_VALUE)
        .addComponent(btnDelete)
        .addGap(40, 40, 40)
        .addComponent(btnNewSalaryRate)
        .addGap(60, 60, 60))
    );
    headerLayout.setVerticalGroup(
      headerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGroup(headerLayout.createSequentialGroup()
        .addGap(16, 16, 16)
        .addGroup(headerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
          .addComponent(btnNewSalaryRate)
          .addComponent(btnDelete))
        .addContainerGap(17, Short.MAX_VALUE))
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

    tableSalaryRate.setModel(new javax.swing.table.DefaultTableModel(
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
    jScrollPane1.setViewportView(tableSalaryRate);

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
  private javax.swing.JButton btnNewSalaryRate;
  private javax.swing.JPanel footer;
  private javax.swing.JPanel header;
  private javax.swing.JScrollPane jScrollPane1;
  private javax.swing.JTable tableSalaryRate;
  // End of variables declaration//GEN-END:variables
}
