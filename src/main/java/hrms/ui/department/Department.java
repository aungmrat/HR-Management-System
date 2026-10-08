package hrms.ui.department;

import hrms.component.ErrorPanel;
import hrms.component.HrmsTable;
import hrms.component.SearchField;
import hrms.exception.AuthorizationException;
import hrms.exception.ErrorHandler;
import hrms.repository.DepartmentRepository;
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
public class Department extends javax.swing.JPanel {

  private static final Logger LOG = LoggerFactory.getLogger(Department.class);

  /**
   * HrmsTable lives outside the GEN block so Form Designer saves cannot wipe it.
   */
  private HrmsTable table;

  /**
   * Creates new form Department
   */
  public Department() {
    initComponents();
    setupTable();
    setupActions();
  }

  private void setupActions() {
    addDepartment.addActionListener(e -> runGuarded(this::openNewDepartmentDialog));
    btnDelete.addActionListener(e -> runGuarded(this::deleteSelectedDepartment));
    if (!SecurityService.can(Permissions.DEPARTMENT_CREATE)) {
      addDepartment.setEnabled(false);
      addDepartment.setToolTipText("Requires permission: DEPARTMENT_CREATE");
    }
    if (!SecurityService.can(Permissions.DEPARTMENT_UPDATE)) {
      btnDelete.setEnabled(false);
      btnDelete.setToolTipText("Requires permission: DEPARTMENT_UPDATE");
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

  private void openNewDepartmentDialog() {
    SecurityService.require(Permissions.DEPARTMENT_CREATE);
    DepartmentDialog dialog = new DepartmentDialog(ownerFrame(), true, null);
    dialog.setLocationRelativeTo(this);
    dialog.setVisible(true);
    loadDepartments();
  }

  private void openEditDepartmentDialog(long id) {
    SecurityService.require(Permissions.DEPARTMENT_UPDATE);
    UiThread.executeAsync("Load department",
        () -> DepartmentRepository.findById(id),
        optional -> {
          optional.ifPresent(department -> {
            DepartmentDialog dialog = new DepartmentDialog(ownerFrame(), true, department);
            dialog.setLocationRelativeTo(this);
            dialog.setVisible(true);
            loadDepartments();
          });
        });
  }

  private void deleteSelectedDepartment() {
    SecurityService.require(Permissions.DEPARTMENT_UPDATE);
    int modelRow = table.selectedModelRow();
    if (modelRow < 0) {
      Dialogs.info(this, "Delete Department", "Select a department row first.");
      return;
    }
    long id = ((Number) table.getModel().getValueAt(modelRow, 0)).longValue();
    String name = String.valueOf(table.getModel().getValueAt(modelRow, 2));
    if (!Dialogs.confirm(this, "Delete Department",
        "Delete department '" + name + "'? This is a soft delete.")) {
      return;
    }
    Long userId = SessionContext.isAuthenticated() ? SessionContext.currentUserId() : null;
    UiThread.executeAsync("Delete department",
        () -> DepartmentRepository.softDelete(id, userId),
        deleted -> loadDepartments());
  }

  private void setupTable() {
    table = HrmsTable.builder(
        "ID", "Code", "Name", "Address", "Status", "Remark", "Created By", "Created Date")
        .hiddenColumn(0)
        .fixedColumn(1, 80)
        .fixedColumn(2, 200)
        .fixedColumn(3, 250)
        .fixedColumn(4, 90)
        .fixedColumn(5, 150)
        .fixedColumn(6, 110)
        .fixedColumn(7, 130)
        .badgeColumn(4)
        .onDoubleClick((viewRow, modelRow) -> runGuarded(() -> {
          long id = ((Number) table.getModel().getValueAt(modelRow, 0)).longValue();
          openEditDepartmentDialog(id);
        }))
        .contextMenu(() -> {
          javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();
          javax.swing.JMenuItem editItem = new javax.swing.JMenuItem("Edit");
          editItem.addActionListener(e -> runGuarded(() -> {
            int row = table.selectedModelRow();
            if (row >= 0) {
              long id = ((Number) table.getModel().getValueAt(row, 0)).longValue();
              openEditDepartmentDialog(id);
            }
          }));
          javax.swing.JMenuItem deleteItem = new javax.swing.JMenuItem("Delete");
          deleteItem.addActionListener(e -> runGuarded(this::deleteSelectedDepartment));
          menu.add(editItem);
          menu.add(deleteItem);
          return menu;
        })
        .build();
    jScrollPane1.setViewportView(table);

    if (search instanceof SearchField searchField) {
      searchField.onTextChanged(this::applyFilter);
    }

    loadDepartments();
  }

  private void applyFilter(String text) {
    loadDepartments();
  }

  private void loadDepartments() {
    String keyword = search.getText();
    UiThread.executeAsync("Load departments",
        () -> DepartmentRepository.searchAsTableRows(keyword),
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
    LOG.warn("Cannot load department list", ex);
    jScrollPane1.setViewportView(new ErrorPanel(
        "Cannot load the department list. Check that the database is running, then try again.",
        this::loadDepartments));
  }

  public void refreshTable() {
    loadDepartments();
  }

  /**
   * This method is called from within the constructor to initialize the form.
   * WARNING: Do NOT modify this code. The content of this method is always
   * regenerated by the Form Editor.
   */
  @SuppressWarnings("unchecked")
  // <editor-fold defaultstate="collapsed" desc="Generated
  // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
  private void initComponents() {

    header = new javax.swing.JPanel();
    jLabel1 = new javax.swing.JLabel();
    jPanel2 = new javax.swing.JPanel();
    jPanel3 = new javax.swing.JPanel();
    footer = new javax.swing.JPanel();
    head = new javax.swing.JPanel();
    search =  new SearchField("Search by name or code...");
    addDepartment = new javax.swing.JButton();
    btnDelete = new javax.swing.JButton();
    jPanel4 = new javax.swing.JPanel();
    jScrollPane1 = new javax.swing.JScrollPane();
    tableDepartment = new javax.swing.JTable();

    header.setPreferredSize(new java.awt.Dimension(1220, 40));

    jLabel1.setFont(new java.awt.Font("Segoe UI", 1, 18)); // NOI18N
    jLabel1.setText("Department & Office > Department");

    javax.swing.GroupLayout headerLayout = new javax.swing.GroupLayout(header);
    header.setLayout(headerLayout);
    headerLayout.setHorizontalGroup(
      headerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGroup(headerLayout.createSequentialGroup()
        .addContainerGap()
        .addComponent(jLabel1, javax.swing.GroupLayout.PREFERRED_SIZE, 373, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addContainerGap(858, Short.MAX_VALUE))
    );
    headerLayout.setVerticalGroup(
      headerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, headerLayout.createSequentialGroup()
        .addContainerGap(9, Short.MAX_VALUE)
        .addComponent(jLabel1)
        .addContainerGap())
    );

    setLayout(new java.awt.BorderLayout());

    jPanel2.setLayout(new java.awt.BorderLayout());

    jPanel3.setPreferredSize(new java.awt.Dimension(1220, 40));

    javax.swing.GroupLayout jPanel3Layout = new javax.swing.GroupLayout(jPanel3);
    jPanel3.setLayout(jPanel3Layout);
    jPanel3Layout.setHorizontalGroup(
      jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGap(0, 1237, Short.MAX_VALUE)
    );
    jPanel3Layout.setVerticalGroup(
      jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGap(0, 40, Short.MAX_VALUE)
    );

    jPanel2.add(jPanel3, java.awt.BorderLayout.PAGE_END);

    footer.setLayout(new java.awt.BorderLayout());

    head.setPreferredSize(new java.awt.Dimension(1220, 60));

    search.addActionListener(this::searchActionPerformed);

    addDepartment.setText("+ New Department");

    btnDelete.setText("Delete");

    javax.swing.GroupLayout headLayout = new javax.swing.GroupLayout(head);
    head.setLayout(headLayout);
    headLayout.setHorizontalGroup(
      headLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGroup(headLayout.createSequentialGroup()
        .addGap(60, 60, 60)
        .addComponent(search, javax.swing.GroupLayout.PREFERRED_SIZE, 220, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 653, Short.MAX_VALUE)
        .addComponent(btnDelete)
        .addGap(40, 40, 40)
        .addComponent(addDepartment)
        .addGap(60, 60, 60))
    );
    headLayout.setVerticalGroup(
      headLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGroup(headLayout.createSequentialGroup()
        .addGap(16, 16, 16)
        .addGroup(headLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
          .addComponent(search, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
          .addComponent(addDepartment)
          .addComponent(btnDelete))
        .addContainerGap(17, Short.MAX_VALUE))
    );

    footer.add(head, java.awt.BorderLayout.PAGE_START);

    tableDepartment.setModel(new javax.swing.table.DefaultTableModel(
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
    jScrollPane1.setViewportView(tableDepartment);

    javax.swing.GroupLayout jPanel4Layout = new javax.swing.GroupLayout(jPanel4);
    jPanel4.setLayout(jPanel4Layout);
    jPanel4Layout.setHorizontalGroup(
      jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 1237, Short.MAX_VALUE)
    );
    jPanel4Layout.setVerticalGroup(
      jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addComponent(jScrollPane1, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 740, Short.MAX_VALUE)
    );

    footer.add(jPanel4, java.awt.BorderLayout.CENTER);

    jPanel2.add(footer, java.awt.BorderLayout.CENTER);

    add(jPanel2, java.awt.BorderLayout.CENTER);
  }// </editor-fold>//GEN-END:initComponents

  private void searchActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_searchActionPerformed
    // TODO add your handling code here:
  }// GEN-LAST:event_searchActionPerformed

  // Variables declaration - do not modify//GEN-BEGIN:variables
  private javax.swing.JButton addDepartment;
  private javax.swing.JButton btnDelete;
  private javax.swing.JPanel footer;
  private javax.swing.JPanel head;
  private javax.swing.JPanel header;
  private javax.swing.JLabel jLabel1;
  private javax.swing.JPanel jPanel2;
  private javax.swing.JPanel jPanel3;
  private javax.swing.JPanel jPanel4;
  private javax.swing.JScrollPane jScrollPane1;
  private javax.swing.JTextField search;
  private javax.swing.JTable tableDepartment;
  // End of variables declaration//GEN-END:variables
}

