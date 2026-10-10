/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/GUIForms/JPanel.java to edit this template
 */
package hrms.ui.usermanagement;


import hrms.component.ErrorPanel;
import hrms.component.HrmsTable;
import hrms.component.SearchField;
import hrms.exception.AuthorizationException;
import hrms.exception.ErrorHandler;
import hrms.repository.UserManagementRepository;
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
public class User extends javax.swing.JPanel {

  private static final Logger LOG = LoggerFactory.getLogger(User.class);

  /** Guards combo events fired while the role filter is still loading. */
  private boolean roleFilterReady;

  /**
   * HrmsTable lives outside the GEN block so Form Designer saves cannot wipe it.
   */
  private HrmsTable table;

  /**
   * Creates new form User
   */
  public User() {
    initComponents();
    setupTable();
    setupActions();
  }

  private void setupTable() {
    table = HrmsTable.builder(
            "ID", "Username", "Full Name", "Email", "Role", "Status", "Locked", "Last Login")
        .hiddenColumn(0)
        .fixedColumn(1, 120)
        .fixedColumn(2, 180)
        .fixedColumn(3, 220)
        .fixedColumn(4, 110)
        .fixedColumn(5, 80)
        .badgeColumn(5)
        .fixedColumn(6, 80)
        .badgeColumn(6)
        .fixedColumn(7, 130)
        .onDoubleClick((viewRow, modelRow) -> openEditUserDialog(rowId(modelRow)))
        .contextMenu(this::buildContextMenu)
        .build();
    jScrollPane1.setViewportView(table);
  }

  private void setupActions() {
    // SearchField debounces changes (300 ms) and fires immediately on Enter.
    if (textSearch instanceof SearchField searchField) {
      searchField.onTextChanged(t -> loadUsers());
    }
    comboRole.addActionListener(e -> {
      if (roleFilterReady) {
        loadUsers();
      }
    });
    btnNewUser.addActionListener(e -> runGuarded(this::openNewUserDialog));
    btnDelete.addActionListener(e -> runGuarded(this::deactivateSelected));
    btnUnlock.addActionListener(e -> runGuarded(this::unlockSelected));
    if (!SecurityService.can(Permissions.USER_MANAGE)) {
      btnNewUser.setEnabled(false);
      btnDelete.setEnabled(false);
      btnUnlock.setEnabled(false);
    }
    UiThread.executeAsync("Load role filter",
        () -> UserManagementRepository.listRoles(),
        roles -> {
          comboRole.removeAllItems();
          comboRole.addItem("All Roles");
          for (UserManagementRepository.RoleOption role : roles) {
            comboRole.addItem(role.name());
          }
          roleFilterReady = true;
          loadUsers();
        },
        ex -> showLoadError(ex));
  }

  private javax.swing.JPopupMenu buildContextMenu() {
    javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();
    javax.swing.JMenuItem editItem = new javax.swing.JMenuItem("Edit");
    editItem.addActionListener(e -> runGuarded(() -> {
      int row = table.selectedModelRow();
      if (row >= 0) {
        openEditUserDialog(rowId(row));
      }
    }));
    javax.swing.JMenuItem deactivateItem = new javax.swing.JMenuItem("Deactivate");
    deactivateItem.addActionListener(e -> runGuarded(this::deactivateSelected));
    menu.add(editItem);
    menu.add(deactivateItem);
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

  private void openNewUserDialog() {
    SecurityService.require(Permissions.USER_MANAGE);
    UserDialog dialog = new UserDialog(ownerFrame(), true);
    dialog.setLocationRelativeTo(this);
    dialog.setVisible(true);
    loadUsers();
  }

  private void openEditUserDialog(long id) {
    SecurityService.require(Permissions.USER_MANAGE);
    UiThread.executeAsync("Load user",
        () -> UserManagementRepository.findById(id),
        optional -> optional.ifPresent(row -> {
          UserDialog dialog = new UserDialog(ownerFrame(), true, row);
          dialog.setLocationRelativeTo(this);
          dialog.setVisible(true);
          loadUsers();
        }));
  }

  private void deactivateSelected() {
    SecurityService.require(Permissions.USER_MANAGE);
    int modelRow = table.selectedModelRow();
    if (modelRow < 0) {
      Dialogs.info(this, "Deactivate User", "Select a user row first.");
      return;
    }
    String status = String.valueOf(table.getModel().getValueAt(modelRow, 5));
    if ("Inactive".equalsIgnoreCase(status)) {
      Dialogs.info(this, "Deactivate User", "This account is already inactive.");
      return;
    }
    long id = rowId(modelRow);
    Long currentId = SessionContext.currentUserId();
    if (currentId != null && id == currentId) {
      Dialogs.info(this, "Deactivate User", "You cannot deactivate your own account.");
      return;
    }
    String role = String.valueOf(table.getModel().getValueAt(modelRow, 4));
    if ("HR_ADMIN".equals(role) && UserManagementRepository.countActiveAdmins() <= 1) {
      Dialogs.info(this, "Deactivate User",
          "Cannot deactivate the last active administrator.");
      return;
    }
    String username = String.valueOf(table.getModel().getValueAt(modelRow, 1));
    if (!Dialogs.confirm(this, "Deactivate User",
        "Deactivate user '" + username + "'? They will no longer be able to sign in.")) {
      return;
    }
    UiThread.executeAsync("Deactivate user",
        () -> UserManagementRepository.setStatus(id, "Inactive",
            SessionContext.currentUserId()),
        updated -> loadUsers());
  }

  private void unlockSelected() {
    SecurityService.require(Permissions.USER_MANAGE);
    int modelRow = table.selectedModelRow();
    if (modelRow < 0) {
      Dialogs.info(this, "Unlock User", "Select a user row first.");
      return;
    }
    String locked = String.valueOf(table.getModel().getValueAt(modelRow, 6));
    if (!"Locked".equals(locked)) {
      Dialogs.info(this, "Unlock User", "This account is not locked.");
      return;
    }
    long id = rowId(modelRow);
    String username = String.valueOf(table.getModel().getValueAt(modelRow, 1));
    if (!Dialogs.confirm(this, "Unlock User",
        "Clear the failed-attempt lock for '" + username + "'?")) {
      return;
    }
    UiThread.executeAsync("Unlock user",
        () -> UserManagementRepository.unlock(id, SessionContext.currentUserId()),
        updated -> loadUsers());
  }

  private void loadUsers() {
    String keyword = textSearch.getText();
    String role = comboRole.getSelectedItem() == null
        ? "" : comboRole.getSelectedItem().toString();
    UiThread.executeAsync("Load users",
        () -> UserManagementRepository.searchAsTableRows(keyword, role),
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
    LOG.warn("Cannot load user list", ex);
    jScrollPane1.setViewportView(new ErrorPanel(
        "Cannot load the user list. Check that the database is running, then try again.",
        this::loadUsers));
  }

  public void refreshTable() {
    loadUsers();
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
    btnNewUser = new javax.swing.JButton();
    btnDelete = new javax.swing.JButton();
    btnUnlock = new javax.swing.JButton();
    comboRole = new javax.swing.JComboBox<>();
    textSearch =  new SearchField("Search by name/email/username");
    footer = new javax.swing.JPanel();
    body = new javax.swing.JPanel();
    jScrollPane1 = new javax.swing.JScrollPane();
    tableUser = new javax.swing.JTable();

    setLayout(new java.awt.BorderLayout());

    header.setPreferredSize(new java.awt.Dimension(1237, 60));

    btnNewUser.setText("+ New User");

    btnDelete.setText("Delete");

    btnUnlock.setText("Unlock");

    comboRole.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Role" }));

    javax.swing.GroupLayout headerLayout = new javax.swing.GroupLayout(header);
    header.setLayout(headerLayout);
    headerLayout.setHorizontalGroup(
      headerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, headerLayout.createSequentialGroup()
        .addGap(60, 60, 60)
        .addComponent(textSearch, javax.swing.GroupLayout.PREFERRED_SIZE, 200, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 473, Short.MAX_VALUE)
        .addComponent(comboRole, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(30, 30, 30)
        .addComponent(btnUnlock)
        .addGap(30, 30, 30)
        .addComponent(btnDelete)
        .addGap(40, 40, 40)
        .addComponent(btnNewUser, javax.swing.GroupLayout.PREFERRED_SIZE, 116, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(60, 60, 60))
    );
    headerLayout.setVerticalGroup(
      headerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGroup(headerLayout.createSequentialGroup()
        .addGap(16, 16, 16)
        .addGroup(headerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
          .addComponent(btnNewUser)
          .addComponent(btnDelete)
          .addComponent(btnUnlock)
          .addComponent(comboRole, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
          .addComponent(textSearch, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
        .addContainerGap(17, Short.MAX_VALUE))
    );

    textSearch.getAccessibleContext().setAccessibleDescription("");

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

    tableUser.setModel(new javax.swing.table.DefaultTableModel(
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
    jScrollPane1.setViewportView(tableUser);

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
  private javax.swing.JButton btnNewUser;
  private javax.swing.JButton btnUnlock;
  private javax.swing.JComboBox<String> comboRole;
  private javax.swing.JPanel footer;
  private javax.swing.JPanel header;
  private javax.swing.JScrollPane jScrollPane1;
  private javax.swing.JTable tableUser;
  private javax.swing.JTextField textSearch;
  // End of variables declaration//GEN-END:variables
}
