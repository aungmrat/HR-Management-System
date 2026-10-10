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
import hrms.repository.PositionRepository;
import hrms.security.Permissions;
import hrms.security.SecurityService;
import hrms.security.SessionContext;
import hrms.util.Dialogs;
import hrms.util.UiThread;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author user
 */
public class Position extends javax.swing.JPanel {

    private static final Logger LOG = LoggerFactory.getLogger(Position.class);

    /**
     * HrmsTable lives outside the GEN block so Form Designer saves cannot wipe it.
     */
    private HrmsTable table;

    /**
     * Creates new form Position
     */
    public Position() {
        initComponents();
        setupTable();
        setupActions();
    }

    private void setupTable() {
        // Columns must match Position.toTableRow(): ID, Name, Department, Role,
        // Remark, Created By, Created Date. The table has no status column,
        // so there is no badgeColumn.
        table = HrmsTable.builder(
                "ID", "Name", "Department", "Role", "Remark", "Created By", "Created Date")
                .hiddenColumn(0)
                .fixedColumn(1, 180)
                .fixedColumn(2, 160)
                .fixedColumn(3, 160)
                .fixedColumn(4, 200)
                .fixedColumn(5, 110)
                .fixedColumn(6, 130)
                .columnType(6, java.time.LocalDateTime.class)
                .onDoubleClick((viewRow, modelRow) -> runGuarded(
                        () -> openEditPositionDialog(rowId(modelRow))))
                .contextMenu(this::buildContextMenu)
                .build();
        jScrollPane1.setViewportView(table);
        loadPositions();
    }

    private void setupActions() {
        // SearchField debounces changes (300 ms) and fires immediately on Enter.
        if (textSearch instanceof SearchField searchField) {
            searchField.onTextChanged(t -> loadPositions());
        }
        btnNewPosition.addActionListener(e -> runGuarded(this::openNewPositionDialog));
        btnDelete.addActionListener(e -> runGuarded(this::deleteSelectedPosition));
        if (!SecurityService.can(Permissions.POSITION_CREATE)) {
            btnNewPosition.setEnabled(false);
            btnNewPosition.setToolTipText("Requires permission: POSITION_CREATE");
        }
        if (!SecurityService.can(Permissions.POSITION_UPDATE)) {
            btnDelete.setEnabled(false);
            btnDelete.setToolTipText("Requires permission: POSITION_UPDATE");
        }
    }

    private javax.swing.JPopupMenu buildContextMenu() {
        javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();
        javax.swing.JMenuItem editItem = new javax.swing.JMenuItem("Edit");
        editItem.addActionListener(e -> runGuarded(() -> {
            int row = table.selectedModelRow();
            if (row >= 0) {
                openEditPositionDialog(rowId(row));
            }
        }));
        javax.swing.JMenuItem deleteItem = new javax.swing.JMenuItem("Delete");
        deleteItem.addActionListener(e -> runGuarded(this::deleteSelectedPosition));
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

    private void openNewPositionDialog() {
        SecurityService.require(Permissions.POSITION_CREATE);
        PositionDialog dialog = new PositionDialog(ownerFrame(), true);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
        loadPositions();
    }

    private void openEditPositionDialog(long id) {
        SecurityService.require(Permissions.POSITION_UPDATE);
        UiThread.executeAsync("Load position",
                () -> PositionRepository.findById(id),
                optional -> optional.ifPresent(position -> {
                    PositionDialog dialog = new PositionDialog(ownerFrame(), true, position);
                    dialog.setLocationRelativeTo(this);
                    dialog.setVisible(true);
                    loadPositions();
                }));
    }

    private void deleteSelectedPosition() {
        SecurityService.require(Permissions.POSITION_UPDATE);
        int modelRow = table.selectedModelRow();
        if (modelRow < 0) {
            Dialogs.info(this, "Delete Position", "Select a position row first.");
            return;
        }
        long id = rowId(modelRow);
        String name = String.valueOf(table.getModel().getValueAt(modelRow, 1));
        if (!Dialogs.confirm(this, "Delete Position",
                "Delete position '" + name + "'? This is a soft delete.")) {
            return;
        }
        Long userId = SessionContext.isAuthenticated() ? SessionContext.currentUserId() : null;
        UiThread.executeAsync("Delete position",
                () -> PositionRepository.softDelete(id, userId),
                deleted -> loadPositions());
    }

    private void loadPositions() {
        String keyword = textSearch.getText();
        UiThread.executeAsync("Load positions",
                () -> PositionRepository.searchAsTableRows(keyword, null, null),
                rows -> {
                    // Restore the table in case a previous load failed and showed ErrorPanel.
                    jScrollPane1.setViewportView(table);
                    table.setRows(rows);
                },
                ex -> showLoadError(ex, this::loadPositions, "Cannot load the position list."));
    }

    /**
     * Swaps the table area for an inline error state with retry (details go to the
     * log).
     */
    private void showLoadError(Exception ex, Runnable retry, String message) {
        LOG.warn("Positions panel load failed", ex);
        jScrollPane1.setViewportView(new ErrorPanel(
                message + " Check that the database is running, then try again.", retry));
    }

    public void refreshTable() {
        loadPositions();
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
    btnNewPosition = new javax.swing.JButton();
    btnDelete = new javax.swing.JButton();
    textSearch =  new SearchField("Search by name...");
    footer = new javax.swing.JPanel();
    body = new javax.swing.JPanel();
    jScrollPane1 = new javax.swing.JScrollPane();
    tablePosition = new javax.swing.JTable();

    setLayout(new java.awt.BorderLayout());

    header.setPreferredSize(new java.awt.Dimension(1237, 60));

    btnNewPosition.setText("+ New Position");

    btnDelete.setText("Delete");

    javax.swing.GroupLayout headerLayout = new javax.swing.GroupLayout(header);
    header.setLayout(headerLayout);
    headerLayout.setHorizontalGroup(
      headerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, headerLayout.createSequentialGroup()
        .addGap(60, 60, 60)
        .addComponent(textSearch, javax.swing.GroupLayout.PREFERRED_SIZE, 220, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 674, Short.MAX_VALUE)
        .addComponent(btnDelete)
        .addGap(40, 40, 40)
        .addComponent(btnNewPosition)
        .addGap(60, 60, 60))
    );
    headerLayout.setVerticalGroup(
      headerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, headerLayout.createSequentialGroup()
        .addContainerGap(21, Short.MAX_VALUE)
        .addGroup(headerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
          .addComponent(btnNewPosition)
          .addComponent(btnDelete)
          .addComponent(textSearch, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
        .addGap(16, 16, 16))
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

    tablePosition.setModel(new javax.swing.table.DefaultTableModel(
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
    jScrollPane1.setViewportView(tablePosition);

    javax.swing.GroupLayout bodyLayout = new javax.swing.GroupLayout(body);
    body.setLayout(bodyLayout);
    bodyLayout.setHorizontalGroup(
      bodyLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addComponent(jScrollPane1, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 1237, Short.MAX_VALUE)
    );
    bodyLayout.setVerticalGroup(
      bodyLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 640, Short.MAX_VALUE)
    );

    add(body, java.awt.BorderLayout.CENTER);
  }// </editor-fold>//GEN-END:initComponents


  // Variables declaration - do not modify//GEN-BEGIN:variables
  private javax.swing.JPanel body;
  private javax.swing.JButton btnDelete;
  private javax.swing.JButton btnNewPosition;
  private javax.swing.JPanel footer;
  private javax.swing.JPanel header;
  private javax.swing.JScrollPane jScrollPane1;
  private javax.swing.JTable tablePosition;
  private javax.swing.JTextField textSearch;
  // End of variables declaration//GEN-END:variables
}
