/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/GUIForms/JPanel.java to edit this template
 */
package hrms.ui.staffmanagement;

import hrms.component.ErrorPanel;
import hrms.component.HrmsTable;
import hrms.component.SearchField;
import hrms.exception.AuthorizationException;
import hrms.exception.ErrorHandler;
import hrms.repository.MilitaryServiceRepository;
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
public class MilitaryService extends javax.swing.JPanel {

  private static final Logger LOG = LoggerFactory.getLogger(MilitaryService.class);

  /**
   * HrmsTable lives outside the GEN block so Form Designer saves cannot wipe it.
   */
  private HrmsTable table;

  /**
   * Creates new form MilitaryService
   */
  public MilitaryService() {
    initComponents();
    setupTable();
    setupActions();
  }

  private void setupTable() {
    // Columns must match MilitaryServiceRepository.searchAsTableRows():
    // ID, Staff, Batch No, Service No, Status.
    table = HrmsTable.builder(
            "ID", "Staff", "Batch No", "Service No", "Status")
        .hiddenColumn(0)
        .fixedColumn(1, 280)
        .fixedColumn(2, 140)
        .fixedColumn(3, 160)
        .fixedColumn(4, 130)
        .badgeColumn(4)
        .onDoubleClick((viewRow, modelRow) -> runGuarded(
            () -> openEditDialog(rowId(modelRow))))
        .contextMenu(this::buildContextMenu)
        .build();
    jScrollPane1.setViewportView(table);
    loadRecords();
  }

  private void setupActions() {
    // SearchField debounces changes (300 ms) and fires immediately on Enter.
    if (textSearch instanceof SearchField searchField) {
      searchField.onTextChanged(t -> loadRecords());
    }
    btnNewMilitaryServiceRecord.addActionListener(e -> runGuarded(this::openNewDialog));
    btnDelete.addActionListener(e -> runGuarded(this::removeSelected));
    if (!SecurityService.can(Permissions.STAFF_UPDATE)) {
      btnNewMilitaryServiceRecord.setEnabled(false);
      btnNewMilitaryServiceRecord.setToolTipText("Requires permission: STAFF_UPDATE");
      btnDelete.setEnabled(false);
      btnDelete.setToolTipText("Requires permission: STAFF_UPDATE");
    }
  }

  private javax.swing.JPopupMenu buildContextMenu() {
    javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();
    javax.swing.JMenuItem editItem = new javax.swing.JMenuItem("Edit");
    editItem.addActionListener(e -> runGuarded(() -> {
      int row = table.selectedModelRow();
      if (row >= 0) {
        openEditDialog(rowId(row));
      }
    }));
    javax.swing.JMenuItem deleteItem = new javax.swing.JMenuItem("Delete");
    deleteItem.addActionListener(e -> runGuarded(this::removeSelected));
    menu.add(editItem);
    menu.add(deleteItem);
    return menu;
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

  private long rowId(int modelRow) {
    return ((Number) table.getModel().getValueAt(modelRow, 0)).longValue();
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

  private void openNewDialog() {
    SecurityService.require(Permissions.STAFF_UPDATE);
    MilitaryServiceDialog dialog = new MilitaryServiceDialog(ownerFrame(), true);
    dialog.setLocationRelativeTo(this);
    dialog.setVisible(true);
    loadRecords();
  }

  private void openEditDialog(long id) {
    SecurityService.require(Permissions.STAFF_UPDATE);
    UiThread.executeAsync("Load military service",
        () -> MilitaryServiceRepository.findById(id),
        optional -> optional.ifPresent(record -> {
          MilitaryServiceDialog dialog = new MilitaryServiceDialog(ownerFrame(), true, record);
          dialog.setLocationRelativeTo(this);
          dialog.setVisible(true);
          loadRecords();
        }));
  }

  private void removeSelected() {
    SecurityService.require(Permissions.STAFF_UPDATE);
    int modelRow = table.selectedModelRow();
    if (modelRow < 0) {
      Dialogs.info(this, "Delete Military Service Record", "Select a military service row first.");
      return;
    }
    long id = rowId(modelRow);
    String staff = String.valueOf(table.getModel().getValueAt(modelRow, 1));
    String batchNo = String.valueOf(table.getModel().getValueAt(modelRow, 2));
    if (!Dialogs.confirm(this, "Delete Military Service Record",
        "Delete record '" + batchNo + "' for staff '" + staff + "'?")) {
      return;
    }
    long userId = SessionContext.isAuthenticated() ? SessionContext.currentUserId() : 0;
    UiThread.executeAsync("Delete military service",
        () -> {
          MilitaryServiceRepository.softDelete(id, userId);
          return true;
        },
        removed -> loadRecords());
  }

  private void loadRecords() {
    String keyword = textSearch.getText();
    UiThread.executeAsync("Load military service",
        () -> MilitaryServiceRepository.searchAsTableRows(keyword),
        rows -> {
          // Restore the table in case a previous load failed and showed ErrorPanel.
          jScrollPane1.setViewportView(table);
          table.setRows(rows);
        },
        ex -> showLoadError(ex, this::loadRecords, "Cannot load the military service list."));
  }

  /**
   * Swaps the table area for an inline error state with retry (details go to the
   * log).
   */
  private void showLoadError(Exception ex, Runnable retry, String message) {
    LOG.warn("Military service panel load failed", ex);
    jScrollPane1.setViewportView(new ErrorPanel(
        message + " Check that the database is running, then try again.", retry));
  }

  public void refreshTable() {
    loadRecords();
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
    btnNewMilitaryServiceRecord = new javax.swing.JButton();
    btnDelete = new javax.swing.JButton();
    textSearch =  new SearchField("Search by name or code...");
    footer = new javax.swing.JPanel();
    body = new javax.swing.JPanel();
    jScrollPane1 = new javax.swing.JScrollPane();
    tableMilitaryService = new javax.swing.JTable();

    setLayout(new java.awt.BorderLayout());

    header.setPreferredSize(new java.awt.Dimension(1237, 60));

    btnNewMilitaryServiceRecord.setText("+ New Military Service Record ");

    btnDelete.setText("Delete");

    javax.swing.GroupLayout headerLayout = new javax.swing.GroupLayout(header);
    header.setLayout(headerLayout);
    headerLayout.setHorizontalGroup(
      headerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, headerLayout.createSequentialGroup()
        .addGap(60, 60, 60)
        .addComponent(textSearch, javax.swing.GroupLayout.PREFERRED_SIZE, 220, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 586, Short.MAX_VALUE)
        .addComponent(btnDelete)
        .addGap(40, 40, 40)
        .addComponent(btnNewMilitaryServiceRecord)
        .addGap(60, 60, 60))
    );
    headerLayout.setVerticalGroup(
      headerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGroup(headerLayout.createSequentialGroup()
        .addGap(14, 14, 14)
        .addGroup(headerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
          .addComponent(btnNewMilitaryServiceRecord)
          .addComponent(btnDelete)
          .addComponent(textSearch, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
        .addContainerGap(19, Short.MAX_VALUE))
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

    tableMilitaryService.setModel(new javax.swing.table.DefaultTableModel(
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
    jScrollPane1.setViewportView(tableMilitaryService);

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
  private javax.swing.JButton btnNewMilitaryServiceRecord;
  private javax.swing.JPanel footer;
  private javax.swing.JPanel header;
  private javax.swing.JScrollPane jScrollPane1;
  private javax.swing.JTable tableMilitaryService;
  private javax.swing.JTextField textSearch;
  // End of variables declaration//GEN-END:variables
}
