/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/GUIForms/JPanel.java to edit this template
 */
package hrms.ui.office;

import hrms.component.ErrorPanel;
import hrms.component.HrmsTable;
import hrms.component.SearchField;
import hrms.exception.AuthorizationException;
import hrms.exception.ErrorHandler;
import hrms.repository.OfficeRepository;
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
public class Office extends javax.swing.JPanel {

  private static final Logger LOG = LoggerFactory.getLogger(Office.class);

  /** HrmsTable lives outside the GEN block so Form Designer saves cannot wipe it. */
  private HrmsTable table;

  /**
   * Creates new form Office
   */
  public Office() {
    initComponents();
    setupTable();
    setupActions();
  }

  private void setupActions() {
    // SearchField debounces changes (300 ms) and fires immediately on Enter.
    if (textSearch instanceof SearchField searchField) {
      searchField.onTextChanged(t -> loadOffices());
    }
    if (!SecurityService.can(Permissions.OFFICE_CREATE)) {
      btnOffice.setEnabled(false);
      btnOffice.setToolTipText("Requires permission: OFFICE_CREATE");
    }
    if (!SecurityService.can(Permissions.OFFICE_UPDATE)) {
      btnDelete.setEnabled(false);
      btnDelete.setToolTipText("Requires permission: OFFICE_UPDATE");
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

  private void openNewOfficeDialog() {
    SecurityService.require(Permissions.OFFICE_CREATE);
    OfficeDialog dialog = new OfficeDialog(ownerFrame(), true, null);
    dialog.setLocationRelativeTo(this);
    dialog.setVisible(true);
    loadOffices();
  }

  private void openEditOfficeDialog(long id) {
    SecurityService.require(Permissions.OFFICE_UPDATE);
    UiThread.executeAsync("Load office",
            () -> OfficeRepository.findById(id),
            optional -> {
              optional.ifPresent(office -> {
                OfficeDialog dialog = new OfficeDialog(ownerFrame(), true, office);
                dialog.setLocationRelativeTo(this);
                dialog.setVisible(true);
                loadOffices();
              });
            });
  }

  private void deleteSelectedOffice() {
    SecurityService.require(Permissions.OFFICE_UPDATE);
    int modelRow = table.selectedModelRow();
    if (modelRow < 0) {
      Dialogs.info(this, "Delete Office", "Select an office row first.");
      return;
    }
    long id = ((Number) table.getModel().getValueAt(modelRow, 0)).longValue();
    String name = String.valueOf(table.getModel().getValueAt(modelRow, 2));
    if (!Dialogs.confirm(this, "Delete Office",
            "Delete office '" + name + "'? This is a soft delete.")) {
      return;
    }
    Long userId = SessionContext.isAuthenticated() ? SessionContext.currentUserId() : null;
    UiThread.executeAsync("Delete office",
            () -> OfficeRepository.softDelete(id, userId),
            deleted -> loadOffices());
  }

  private void setupTable() {
    table = HrmsTable.builder(
            "ID", "Code", "Name", "Department", "Parent Office",
            "Contact No", "Address", "Status")
        .hiddenColumn(0)
        .fixedColumn(1, 80)
        .fixedColumn(2, 180)
        .fixedColumn(3, 160)
        .fixedColumn(4, 160)
        .fixedColumn(5, 120)
        .fixedColumn(6, 160)
        .fixedColumn(7, 90)
        .badgeColumn(7)
        .onDoubleClick((viewRow, modelRow) -> runGuarded(() -> {
          long id = ((Number) table.getModel().getValueAt(modelRow, 0)).longValue();
          openEditOfficeDialog(id);
        }))
        .contextMenu(() -> {
          javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();
          javax.swing.JMenuItem editItem = new javax.swing.JMenuItem("Edit");
          editItem.addActionListener(e -> runGuarded(() -> {
            int row = table.selectedModelRow();
            if (row >= 0) {
              long id = ((Number) table.getModel().getValueAt(row, 0)).longValue();
              openEditOfficeDialog(id);
            }
          }));
          javax.swing.JMenuItem deleteItem = new javax.swing.JMenuItem("Delete");
          deleteItem.addActionListener(e -> runGuarded(this::deleteSelectedOffice));
          menu.add(editItem);
          menu.add(deleteItem);
          return menu;
        })
        .build();
    jScrollPane1.setViewportView(table);

    loadOffices();
  }

  private void loadOffices() {
    String keyword = textSearch.getText();
    UiThread.executeAsync("Load offices",
            () -> OfficeRepository.searchAsTableRows(keyword),
            rows -> {
              // Restore the table in case a previous load failed and showed ErrorPanel.
              jScrollPane1.setViewportView(table);
              table.setRows(rows);
            },
            ex -> showLoadError(ex));
  }

  /** Swaps the table area for an inline error state with retry (details go to the log). */
  private void showLoadError(Exception ex) {
    LOG.warn("Cannot load office list", ex);
    jScrollPane1.setViewportView(new ErrorPanel(
            "Cannot load the office list. Check that the database is running, then try again.",
            this::loadOffices));
  }

  public void refreshTable() {
    loadOffices();
  }

  /**
   * This method is called from within the constructor to initialize the form.
   * WARNING: Do NOT modify this code. The content of this method is always
   * regenerated by the Form Editor.
   */
  @SuppressWarnings("unchecked")
  // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
  private void initComponents() {

    head = new javax.swing.JPanel();
    btnOffice = new javax.swing.JButton();
    textSearch =  new SearchField("Search by name or code...");
    btnDelete = new javax.swing.JButton();
    jPanel2 = new javax.swing.JPanel();
    foot = new javax.swing.JPanel();
    body = new javax.swing.JPanel();
    jScrollPane1 = new javax.swing.JScrollPane();
    jTable1 = new javax.swing.JTable();

    setLayout(new java.awt.BorderLayout());

    head.setPreferredSize(new java.awt.Dimension(1237, 60));

    btnOffice.setText("+ New Office");
    btnOffice.addActionListener(this::btnOfficeActionPerformed);

    btnDelete.setText("Delete");
    btnDelete.addActionListener(this::btnDeleteActionPerformed);

    javax.swing.GroupLayout headLayout = new javax.swing.GroupLayout(head);
    head.setLayout(headLayout);
    headLayout.setHorizontalGroup(
      headLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, headLayout.createSequentialGroup()
        .addGap(42, 42, 42)
        .addComponent(textSearch, javax.swing.GroupLayout.PREFERRED_SIZE, 198, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 683, Short.MAX_VALUE)
        .addComponent(btnDelete, javax.swing.GroupLayout.PREFERRED_SIZE, 85, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(55, 55, 55)
        .addComponent(btnOffice, javax.swing.GroupLayout.PREFERRED_SIZE, 121, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(53, 53, 53))
    );
    headLayout.setVerticalGroup(
      headLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGroup(headLayout.createSequentialGroup()
        .addGap(17, 17, 17)
        .addGroup(headLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
          .addComponent(btnOffice)
          .addComponent(textSearch, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
          .addComponent(btnDelete))
        .addContainerGap(16, Short.MAX_VALUE))
    );

    add(head, java.awt.BorderLayout.PAGE_START);

    jPanel2.setLayout(new java.awt.BorderLayout());

    foot.setPreferredSize(new java.awt.Dimension(1237, 40));

    javax.swing.GroupLayout footLayout = new javax.swing.GroupLayout(foot);
    foot.setLayout(footLayout);
    footLayout.setHorizontalGroup(
      footLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGap(0, 1237, Short.MAX_VALUE)
    );
    footLayout.setVerticalGroup(
      footLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGap(0, 40, Short.MAX_VALUE)
    );

    jPanel2.add(foot, java.awt.BorderLayout.PAGE_END);

    jTable1.setModel(new javax.swing.table.DefaultTableModel(
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
    jScrollPane1.setViewportView(jTable1);

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

    jPanel2.add(body, java.awt.BorderLayout.CENTER);

    add(jPanel2, java.awt.BorderLayout.CENTER);
  }// </editor-fold>//GEN-END:initComponents

  private void btnOfficeActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnOfficeActionPerformed
    openNewOfficeDialog();
  }//GEN-LAST:event_btnOfficeActionPerformed

  private void btnDeleteActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnDeleteActionPerformed
    deleteSelectedOffice();
  }//GEN-LAST:event_btnDeleteActionPerformed


  // Variables declaration - do not modify//GEN-BEGIN:variables
  private javax.swing.JPanel body;
  private javax.swing.JButton btnDelete;
  private javax.swing.JButton btnOffice;
  private javax.swing.JPanel foot;
  private javax.swing.JPanel head;
  private javax.swing.JPanel jPanel2;
  private javax.swing.JScrollPane jScrollPane1;
  private javax.swing.JTable jTable1;
  private javax.swing.JTextField textSearch;
  // End of variables declaration//GEN-END:variables
}
