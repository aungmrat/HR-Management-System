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
import hrms.repository.LeaveEntitlementRepository;
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
public class LeaveEntitlement extends javax.swing.JPanel {

  private static final Logger LOG = LoggerFactory.getLogger(LeaveEntitlement.class);

  /**
   * HrmsTable lives outside the GEN block so Form Designer saves cannot wipe it.
   */
  private HrmsTable table;

  /**
   * Creates new form LeaveEntitlement
   */
  public LeaveEntitlement() {
    initComponents();
    setupTable();
    setupActions();
  }

  private void setupActions() {
    btnGenerateEntitlements.addActionListener(
            e -> runGuarded(this::openGenerateDialog));
    btnNewLeaveEntitlement.addActionListener(
            e -> runGuarded(this::openNewEntitlementDialog));
    btnDelete.addActionListener(e -> runGuarded(this::deleteSelectedEntitlement));
    if (!SecurityService.can(Permissions.LEAVE_ENTITLEMENT_CREATE)) {
      btnGenerateEntitlements.setEnabled(false);
      btnGenerateEntitlements.setToolTipText("Requires permission: LEAVE_ENTITLEMENT_CREATE");
      btnNewLeaveEntitlement.setEnabled(false);
      btnNewLeaveEntitlement.setToolTipText("Requires permission: LEAVE_ENTITLEMENT_CREATE");
    }
    if (!SecurityService.can(Permissions.LEAVE_ENTITLEMENT_UPDATE)) {
      btnDelete.setEnabled(false);
      btnDelete.setToolTipText("Requires permission: LEAVE_ENTITLEMENT_UPDATE");
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

  private void openNewEntitlementDialog() {
    SecurityService.require(Permissions.LEAVE_ENTITLEMENT_CREATE);
    LeaveEntitlementDialog dialog = new LeaveEntitlementDialog(ownerFrame(), true, null);
    dialog.setLocationRelativeTo(this);
    dialog.setVisible(true);
    loadEntitlements();
  }

  private void openEditEntitlementDialog(long id) {
    SecurityService.require(Permissions.LEAVE_ENTITLEMENT_UPDATE);
    UiThread.executeAsync("Load leave entitlement",
        () -> LeaveEntitlementRepository.findById(id),
        optional -> optional.ifPresent(entitlement -> {
          LeaveEntitlementDialog dialog =
                  new LeaveEntitlementDialog(ownerFrame(), true, entitlement);
          dialog.setLocationRelativeTo(this);
          dialog.setVisible(true);
          loadEntitlements();
        }));
  }

  private void openGenerateDialog() {
    SecurityService.require(Permissions.LEAVE_ENTITLEMENT_CREATE);
    LeaveEntitlementGenerateDialog dialog =
            new LeaveEntitlementGenerateDialog(ownerFrame(), true);
    dialog.setLocationRelativeTo(this);
    dialog.setVisible(true);
    loadEntitlements();
  }

  private void deleteSelectedEntitlement() {
    SecurityService.require(Permissions.LEAVE_ENTITLEMENT_UPDATE);
    int modelRow = table.selectedModelRow();
    if (modelRow < 0) {
      Dialogs.info(this, "Delete Leave Entitlement", "Select an entitlement row first.");
      return;
    }
    long id = ((Number) table.getModel().getValueAt(modelRow, 0)).longValue();
    String staff = String.valueOf(table.getModel().getValueAt(modelRow, 1));
    if (!Dialogs.confirm(this, "Delete Leave Entitlement",
        "Delete entitlement for '" + staff + "'? This is a soft delete.")) {
      return;
    }
    Long userId = SessionContext.isAuthenticated() ? SessionContext.currentUserId() : null;
    UiThread.executeAsync("Delete leave entitlement",
        () -> LeaveEntitlementRepository.softDelete(id, userId),
        deleted -> loadEntitlements());
  }

  private void setupTable() {
    table = HrmsTable.builder(
        "ID", "Staff", "Leave Type", "Entitled Days", "Carried Days", "Used Days",
        "Remaining", "Effective Date", "Expiry Date", "Remark", "Created By",
        "Created Date")
        .hiddenColumn(0)
        .columnType(3, Integer.class)
        .columnType(4, Integer.class)
        .columnType(5, Integer.class)
        .columnType(6, Integer.class)
        .fixedColumn(1, 180)
        .fixedColumn(2, 160)
        .fixedColumn(3, 100)
        .fixedColumn(4, 100)
        .fixedColumn(5, 90)
        .fixedColumn(6, 90)
        .fixedColumn(7, 110)
        .fixedColumn(8, 110)
        .fixedColumn(9, 130)
        .fixedColumn(10, 110)
        .fixedColumn(11, 130)
        .onDoubleClick((viewRow, modelRow) -> runGuarded(() -> {
          long id = ((Number) table.getModel().getValueAt(modelRow, 0)).longValue();
          openEditEntitlementDialog(id);
        }))
        .contextMenu(() -> {
          javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();
          javax.swing.JMenuItem editItem = new javax.swing.JMenuItem("Edit");
          editItem.addActionListener(e -> runGuarded(() -> {
            int row = table.selectedModelRow();
            if (row >= 0) {
              long id = ((Number) table.getModel().getValueAt(row, 0)).longValue();
              openEditEntitlementDialog(id);
            }
          }));
          javax.swing.JMenuItem deleteItem = new javax.swing.JMenuItem("Delete");
          deleteItem.addActionListener(e -> runGuarded(this::deleteSelectedEntitlement));
          menu.add(editItem);
          menu.add(deleteItem);
          return menu;
        })
        .build();
    jScrollPane1.setViewportView(table);

    // SearchField debounces changes (300 ms) and fires immediately on Enter.
    if (textSearch instanceof SearchField searchField) {
      searchField.onTextChanged(keyword -> loadEntitlements());
    }

    loadEntitlements();
  }

  private void loadEntitlements() {
    String keyword = textSearch.getText();
    UiThread.executeAsync("Load leave entitlements",
        () -> LeaveEntitlementRepository.searchAsTableRows(keyword),
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
    LOG.warn("Cannot load leave entitlement list", ex);
    jScrollPane1.setViewportView(new ErrorPanel(
        "Cannot load the leave entitlement list. Check that the database is running, then try again.",
        this::loadEntitlements));
  }

  public void refreshTable() {
    loadEntitlements();
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
    btnNewLeaveEntitlement = new javax.swing.JButton();
    btnDelete = new javax.swing.JButton();
    btnGenerateEntitlements = new javax.swing.JButton();
    textSearch =  new SearchField("Search by name or code...");
    footer = new javax.swing.JPanel();
    body = new javax.swing.JPanel();
    jScrollPane1 = new javax.swing.JScrollPane();
    tableLeaveEntitlement = new javax.swing.JTable();

    setLayout(new java.awt.BorderLayout());

    header.setPreferredSize(new java.awt.Dimension(1237, 60));

    btnNewLeaveEntitlement.setText("+ New Leave Entitlement");

    btnDelete.setText("Delete");

    btnGenerateEntitlements.setText("Generate Entitlements...");

    javax.swing.GroupLayout headerLayout = new javax.swing.GroupLayout(header);
    header.setLayout(headerLayout);
    headerLayout.setHorizontalGroup(
      headerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, headerLayout.createSequentialGroup()
        .addGap(60, 60, 60)
        .addComponent(textSearch, javax.swing.GroupLayout.PREFERRED_SIZE, 210, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 430, Short.MAX_VALUE)
        .addComponent(btnGenerateEntitlements)
        .addGap(40, 40, 40)
        .addComponent(btnDelete)
        .addGap(40, 40, 40)
        .addComponent(btnNewLeaveEntitlement)
        .addGap(60, 60, 60))
    );
    headerLayout.setVerticalGroup(
      headerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGroup(headerLayout.createSequentialGroup()
        .addGap(16, 16, 16)
        .addGroup(headerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
          .addComponent(btnNewLeaveEntitlement)
          .addComponent(btnDelete)
          .addComponent(btnGenerateEntitlements)
          .addComponent(textSearch, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
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

    tableLeaveEntitlement.setModel(new javax.swing.table.DefaultTableModel(
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
    jScrollPane1.setViewportView(tableLeaveEntitlement);

    javax.swing.GroupLayout bodyLayout = new javax.swing.GroupLayout(body);
    body.setLayout(bodyLayout);
    bodyLayout.setHorizontalGroup(
      bodyLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 1237, Short.MAX_VALUE)
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
  private javax.swing.JButton btnGenerateEntitlements;
  private javax.swing.JButton btnNewLeaveEntitlement;
  private javax.swing.JPanel footer;
  private javax.swing.JPanel header;
  private javax.swing.JScrollPane jScrollPane1;
  private javax.swing.JTable tableLeaveEntitlement;
  private javax.swing.JTextField textSearch;
  // End of variables declaration//GEN-END:variables
}
