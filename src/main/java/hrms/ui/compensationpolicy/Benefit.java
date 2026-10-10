/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/GUIForms/JPanel.java to edit this template
 */
package hrms.ui.compensationpolicy;

import hrms.component.ErrorPanel;
import hrms.component.HrmsTable;
import hrms.exception.AuthorizationException;
import hrms.exception.ErrorHandler;
import hrms.repository.BenefitRepository;
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
public class Benefit extends javax.swing.JPanel {

  private static final Logger LOG = LoggerFactory.getLogger(Benefit.class);

  /**
   * HrmsTable lives outside the GEN block so Form Designer saves cannot wipe it.
   */
  private HrmsTable table;

  /**
   * Creates new form Benefit
   */
  public Benefit() {
    initComponents();
    setupTable();
    setupActions();
  }

  private void setupActions() {
    btnNewBenefit.addActionListener(e -> runGuarded(this::openNewBenefitDialog));
    btnDelete.addActionListener(e -> runGuarded(this::deleteSelectedBenefit));
    if (!SecurityService.can(Permissions.BENEFIT_CREATE)) {
      btnNewBenefit.setEnabled(false);
      btnNewBenefit.setToolTipText("Requires permission: BENEFIT_CREATE");
    }
    if (!SecurityService.can(Permissions.BENEFIT_UPDATE)) {
      btnDelete.setEnabled(false);
      btnDelete.setToolTipText("Requires permission: BENEFIT_UPDATE");
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

  private void openNewBenefitDialog() {
    SecurityService.require(Permissions.BENEFIT_CREATE);
    BenefitDialog dialog = new BenefitDialog(ownerFrame(), true, null);
    dialog.setLocationRelativeTo(this);
    dialog.setVisible(true);
    loadBenefits();
  }

  private void openEditBenefitDialog(long id) {
    SecurityService.require(Permissions.BENEFIT_UPDATE);
    UiThread.executeAsync("Load benefit",
        () -> BenefitRepository.findById(id),
        optional -> optional.ifPresent(benefit -> {
          BenefitDialog dialog = new BenefitDialog(ownerFrame(), true, benefit);
          dialog.setLocationRelativeTo(this);
          dialog.setVisible(true);
          loadBenefits();
        }));
  }

  private void deleteSelectedBenefit() {
    SecurityService.require(Permissions.BENEFIT_UPDATE);
    int modelRow = table.selectedModelRow();
    if (modelRow < 0) {
      Dialogs.info(this, "Delete Benefit", "Select a benefit row first.");
      return;
    }
    long id = ((Number) table.getModel().getValueAt(modelRow, 0)).longValue();
    String name = String.valueOf(table.getModel().getValueAt(modelRow, 1));
    if (!Dialogs.confirm(this, "Delete Benefit",
        "Delete benefit '" + name + "'? This is a soft delete.")) {
      return;
    }
    Long userId = SessionContext.isAuthenticated() ? SessionContext.currentUserId() : null;
    UiThread.executeAsync("Delete benefit",
        () -> BenefitRepository.softDelete(id, userId),
        deleted -> loadBenefits());
  }

  private void setupTable() {
    table = HrmsTable.builder(
        "ID", "Name", "Rate", "Frequency", "Unit", "Min Annual Income",
        "Max Annual Income", "Min Age", "Max Age", "Has Family", "Has Children",
        "Active", "Created By", "Created Date")
        .hiddenColumn(0)
        .columnType(2, java.math.BigDecimal.class)
        .columnType(5, java.math.BigDecimal.class)
        .columnType(6, java.math.BigDecimal.class)
        .columnType(7, Integer.class)
        .columnType(8, Integer.class)
        .fixedColumn(1, 150)
        .fixedColumn(2, 80)
        .fixedColumn(3, 90)
        .fixedColumn(4, 90)
        .fixedColumn(5, 120)
        .fixedColumn(6, 120)
        .fixedColumn(7, 70)
        .fixedColumn(8, 70)
        .fixedColumn(9, 80)
        .fixedColumn(10, 90)
        .fixedColumn(11, 85)
        .fixedColumn(12, 110)
        .fixedColumn(13, 130)
        .badgeColumn(11)
        .onDoubleClick((viewRow, modelRow) -> runGuarded(() -> {
          long id = ((Number) table.getModel().getValueAt(modelRow, 0)).longValue();
          openEditBenefitDialog(id);
        }))
        .contextMenu(() -> {
          javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();
          javax.swing.JMenuItem editItem = new javax.swing.JMenuItem("Edit");
          editItem.addActionListener(e -> runGuarded(() -> {
            int row = table.selectedModelRow();
            if (row >= 0) {
              long id = ((Number) table.getModel().getValueAt(row, 0)).longValue();
              openEditBenefitDialog(id);
            }
          }));
          javax.swing.JMenuItem deleteItem = new javax.swing.JMenuItem("Delete");
          deleteItem.addActionListener(e -> runGuarded(this::deleteSelectedBenefit));
          menu.add(editItem);
          menu.add(deleteItem);
          return menu;
        })
        .build();
    jScrollPane1.setViewportView(table);

    loadBenefits();
  }

  private void loadBenefits() {
    UiThread.executeAsync("Load benefits",
        () -> BenefitRepository.findAllAsTableRows(),
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
    LOG.warn("Cannot load benefit list", ex);
    jScrollPane1.setViewportView(new ErrorPanel(
        "Cannot load the benefit list. Check that the database is running, then try again.",
        this::loadBenefits));
  }

  public void refreshTable() {
    loadBenefits();
  }

  /**
   * This method is called from within the constructor to initialize the form.
   * WARNING: Do NOT modify this code. The content of this method is always
   * regenerated by the Form Editor.
   */
  @SuppressWarnings("unchecked")
  // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
  private void initComponents() {

    jPanel1 = new javax.swing.JPanel();
    btnNewBenefit = new javax.swing.JButton();
    btnDelete = new javax.swing.JButton();
    jPanel2 = new javax.swing.JPanel();
    jPanel3 = new javax.swing.JPanel();
    jScrollPane1 = new javax.swing.JScrollPane();
    tableBenefit = new javax.swing.JTable();

    setLayout(new java.awt.BorderLayout());

    jPanel1.setPreferredSize(new java.awt.Dimension(1237, 60));

    btnNewBenefit.setText("+ New Benefit");

    btnDelete.setText("Delete");

    javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
    jPanel1.setLayout(jPanel1Layout);
    jPanel1Layout.setHorizontalGroup(
      jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel1Layout.createSequentialGroup()
        .addContainerGap(953, Short.MAX_VALUE)
        .addComponent(btnDelete)
        .addGap(40, 40, 40)
        .addComponent(btnNewBenefit)
        .addGap(60, 60, 60))
    );
    jPanel1Layout.setVerticalGroup(
      jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGroup(jPanel1Layout.createSequentialGroup()
        .addGap(16, 16, 16)
        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
          .addComponent(btnNewBenefit)
          .addComponent(btnDelete))
        .addContainerGap(17, Short.MAX_VALUE))
    );

    add(jPanel1, java.awt.BorderLayout.PAGE_START);

    jPanel2.setPreferredSize(new java.awt.Dimension(1237, 40));

    javax.swing.GroupLayout jPanel2Layout = new javax.swing.GroupLayout(jPanel2);
    jPanel2.setLayout(jPanel2Layout);
    jPanel2Layout.setHorizontalGroup(
      jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGap(0, 1237, Short.MAX_VALUE)
    );
    jPanel2Layout.setVerticalGroup(
      jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGap(0, 40, Short.MAX_VALUE)
    );

    add(jPanel2, java.awt.BorderLayout.PAGE_END);

    tableBenefit.setModel(new javax.swing.table.DefaultTableModel(
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
    jScrollPane1.setViewportView(tableBenefit);

    javax.swing.GroupLayout jPanel3Layout = new javax.swing.GroupLayout(jPanel3);
    jPanel3.setLayout(jPanel3Layout);
    jPanel3Layout.setHorizontalGroup(
      jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addComponent(jScrollPane1, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 1237, Short.MAX_VALUE)
    );
    jPanel3Layout.setVerticalGroup(
      jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 740, Short.MAX_VALUE)
    );

    add(jPanel3, java.awt.BorderLayout.CENTER);
  }// </editor-fold>//GEN-END:initComponents


  // Variables declaration - do not modify//GEN-BEGIN:variables
  private javax.swing.JButton btnDelete;
  private javax.swing.JButton btnNewBenefit;
  private javax.swing.JPanel jPanel1;
  private javax.swing.JPanel jPanel2;
  private javax.swing.JPanel jPanel3;
  private javax.swing.JScrollPane jScrollPane1;
  private javax.swing.JTable tableBenefit;
  // End of variables declaration//GEN-END:variables
}
