/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/GUIForms/JPanel.java to edit this template
 */
package hrms.ui.adminhierarchy;


import hrms.component.ErrorPanel;
import hrms.component.HrmsTable;
import hrms.component.SearchField;
import hrms.exception.AuthorizationException;
import hrms.exception.ErrorHandler;
import hrms.model.Department;
import hrms.repository.DepartmentRepository;
import hrms.repository.RoleRepository;
import hrms.security.Permissions;
import hrms.security.SecurityService;
import hrms.security.SessionContext;
import hrms.util.Dialogs;
import hrms.util.UiThread;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author aungmrat
 */
public class Roles extends javax.swing.JPanel {

  private static final Logger LOG = LoggerFactory.getLogger(Roles.class);

  /**
   * HrmsTable lives outside the GEN block so Form Designer saves cannot wipe it.
   */
  private HrmsTable table;

  /** Guards combo events fired while the department filter loads. */
  private boolean filterReady;

  /** Display name -&gt; id for the String department filter. */
  private final Map<String, Long> departmentIds = new HashMap<>();

  /**
   * Creates new form Roles
   */
  public Roles() {
    initComponents();
    setupTable();
    setupActions();
  }

  private void setupTable() {
    table = HrmsTable.builder(
            "ID", "Name", "Department", "Remark", "Created By", "Created Date")
        .hiddenColumn(0)
        .fixedColumn(1, 200)
        .fixedColumn(2, 160)
        .fixedColumn(3, 200)
        .fixedColumn(4, 110)
        .fixedColumn(5, 130)
        .onDoubleClick((viewRow, modelRow) -> runGuarded(() -> openEditRoleDialog(rowId(modelRow))))
        .contextMenu(this::buildContextMenu)
        .build();
    jScrollPane1.setViewportView(table);
    loadRoles();
  }

  private void setupActions() {
    // SearchField debounces changes (300 ms) and fires immediately on Enter.
    if (textSearch instanceof SearchField searchField) {
      searchField.onTextChanged(t -> loadRoles());
    }
    comboDepartment.addActionListener(e -> {
      if (filterReady) {
        loadRoles();
      }
    });
    btnNewRole.addActionListener(e -> runGuarded(this::openNewRoleDialog));
    btnDelete.addActionListener(e -> runGuarded(this::deleteSelectedRole));
    if (!SecurityService.can(Permissions.ROLE_CREATE)) {
      btnNewRole.setEnabled(false);
      btnNewRole.setToolTipText("Requires permission: ROLE_CREATE");
    }
    if (!SecurityService.can(Permissions.ROLE_UPDATE)) {
      btnDelete.setEnabled(false);
      btnDelete.setToolTipText("Requires permission: ROLE_UPDATE");
    }
    loadFilters();
  }

  private void loadFilters() {
    UiThread.executeAsync("Load role filters",
        DepartmentRepository::listAll,
        departments -> {
          departmentIds.clear();
          comboDepartment.removeAllItems();
          comboDepartment.addItem("All Departments");
          for (Department department : departments) {
            comboDepartment.addItem(department.getName());
            departmentIds.putIfAbsent(department.getName(), department.getId());
          }
          filterReady = true;
          loadRoles();
        },
        ex -> showLoadError(ex, this::loadFilters,
            "Cannot load the department filter."));
  }

  private javax.swing.JPopupMenu buildContextMenu() {
    javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();
    javax.swing.JMenuItem editItem = new javax.swing.JMenuItem("Edit");
    editItem.addActionListener(e -> runGuarded(() -> {
      int row = table.selectedModelRow();
      if (row >= 0) {
        openEditRoleDialog(rowId(row));
      }
    }));
    javax.swing.JMenuItem deleteItem = new javax.swing.JMenuItem("Delete");
    deleteItem.addActionListener(e -> runGuarded(this::deleteSelectedRole));
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

  private void openNewRoleDialog() {
    SecurityService.require(Permissions.ROLE_CREATE);
    RolesDialog dialog = new RolesDialog(ownerFrame(), true);
    dialog.setLocationRelativeTo(this);
    dialog.setVisible(true);
    loadRoles();
  }

  private void openEditRoleDialog(long id) {
    SecurityService.require(Permissions.ROLE_UPDATE);
    UiThread.executeAsync("Load role",
        () -> RoleRepository.findById(id),
        optional -> optional.ifPresent(role -> {
          RolesDialog dialog = new RolesDialog(ownerFrame(), true, role);
          dialog.setLocationRelativeTo(this);
          dialog.setVisible(true);
          loadRoles();
        }));
  }

  private void deleteSelectedRole() {
    SecurityService.require(Permissions.ROLE_UPDATE);
    int modelRow = table.selectedModelRow();
    if (modelRow < 0) {
      Dialogs.info(this, "Delete Role", "Select a role row first.");
      return;
    }
    long id = rowId(modelRow);
    String name = String.valueOf(table.getModel().getValueAt(modelRow, 1));
    if (!Dialogs.confirm(this, "Delete Role",
        "Delete role '" + name + "'? This is a soft delete.")) {
      return;
    }
    Long userId = SessionContext.isAuthenticated() ? SessionContext.currentUserId() : null;
    UiThread.executeAsync("Delete role",
        () -> RoleRepository.softDelete(id, userId),
        deleted -> loadRoles());
  }

  private void loadRoles() {
    String keyword = textSearch.getText();
    Long departmentId = selectedFilterId(comboDepartment, departmentIds, "All Departments");
    UiThread.executeAsync("Load roles",
        () -> RoleRepository.searchAsTableRows(keyword, departmentId),
        rows -> {
          // Restore the table in case a previous load failed and showed ErrorPanel.
          jScrollPane1.setViewportView(table);
          table.setRows(rows);
        },
        ex -> showLoadError(ex, this::loadRoles, "Cannot load the role list."));
  }

  private Long selectedFilterId(javax.swing.JComboBox<String> combo, Map<String, Long> ids,
          String allLabel) {
    Object selected = combo.getSelectedItem();
    if (selected == null) {
      return null;
    }
    String label = selected.toString();
    if (allLabel.equals(label)) {
      return null;
    }
    return ids.get(label);
  }

  /**
   * Swaps the table area for an inline error state with retry (details go to the
   * log).
   */
  private void showLoadError(Exception ex, Runnable retry, String message) {
    LOG.warn("Roles panel load failed", ex);
    jScrollPane1.setViewportView(new ErrorPanel(
        message + " Check that the database is running, then try again.", retry));
  }

  public void refreshTable() {
    loadRoles();
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
    btnNewRole = new javax.swing.JButton();
    btnDelete = new javax.swing.JButton();
    textSearch =  new SearchField("Search by name...");
    comboDepartment = new javax.swing.JComboBox<>();
    footer = new javax.swing.JPanel();
    body = new javax.swing.JPanel();
    jScrollPane1 = new javax.swing.JScrollPane();
    tableRoles = new javax.swing.JTable();

    setLayout(new java.awt.BorderLayout());

    header.setPreferredSize(new java.awt.Dimension(1237, 60));

    btnNewRole.setText("+ New Role");

    btnDelete.setText("Delete");

    textSearch.addActionListener(this::textSearchActionPerformed);

    comboDepartment.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Department" }));

    javax.swing.GroupLayout headerLayout = new javax.swing.GroupLayout(header);
    header.setLayout(headerLayout);
    headerLayout.setHorizontalGroup(
      headerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, headerLayout.createSequentialGroup()
        .addGap(60, 60, 60)
        .addComponent(textSearch, javax.swing.GroupLayout.PREFERRED_SIZE, 220, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 407, Short.MAX_VALUE)
        .addComponent(comboDepartment, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(40, 40, 40)
        .addComponent(btnDelete)
        .addGap(40, 40, 40)
        .addComponent(btnNewRole, javax.swing.GroupLayout.PREFERRED_SIZE, 117, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(60, 60, 60))
    );
    headerLayout.setVerticalGroup(
      headerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGroup(headerLayout.createSequentialGroup()
        .addGap(16, 16, 16)
        .addGroup(headerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
          .addComponent(btnNewRole)
          .addComponent(btnDelete)
          .addComponent(textSearch, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
          .addComponent(comboDepartment, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
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

    tableRoles.setModel(new javax.swing.table.DefaultTableModel(
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
    jScrollPane1.setViewportView(tableRoles);

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

  private void textSearchActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_textSearchActionPerformed
    // TODO add your handling code here:
  }//GEN-LAST:event_textSearchActionPerformed


  // Variables declaration - do not modify//GEN-BEGIN:variables
  private javax.swing.JPanel body;
  private javax.swing.JButton btnDelete;
  private javax.swing.JButton btnNewRole;
  private javax.swing.JComboBox<String> comboDepartment;
  private javax.swing.JPanel footer;
  private javax.swing.JPanel header;
  private javax.swing.JScrollPane jScrollPane1;
  private javax.swing.JTable tableRoles;
  private javax.swing.JTextField textSearch;
  // End of variables declaration//GEN-END:variables
}
