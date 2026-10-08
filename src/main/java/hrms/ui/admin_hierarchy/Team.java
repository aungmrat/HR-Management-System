/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/GUIForms/JPanel.java to edit this template
 */
package hrms.ui.admin_hierarchy;

import hrms.component.ErrorPanel;
import hrms.component.HrmsTable;
import hrms.component.SearchField;
import hrms.exception.AuthorizationException;
import hrms.exception.ErrorHandler;
import hrms.repository.TeamRepository;
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
public class Team extends javax.swing.JPanel {

    private static final Logger LOG = LoggerFactory.getLogger(Team.class);

    /**
     * HrmsTable lives outside the GEN block so Form Designer saves cannot wipe it.
     */
    private HrmsTable table;

    /**
     * Creates new form Team
     */
    public Team() {
        initComponents();
        setupTable();
        setupActions();
    }

    private void setupTable() {
        // Columns must match Team.toTableRow(): ID, Name, Department, Office,
        // Parent Team, Members, Remark, Created By, Created Date. The table has
        // no status column, so there is no badgeColumn.
        table = HrmsTable.builder(
                "ID", "Name", "Department", "Office", "Parent Team", "Members",
                "Remark", "Created By", "Created Date")
                .hiddenColumn(0)
                .fixedColumn(1, 180)
                .fixedColumn(2, 150)
                .fixedColumn(3, 150)
                .fixedColumn(4, 130)
                .fixedColumn(5, 80)
                .fixedColumn(6, 180)
                .fixedColumn(7, 110)
                .fixedColumn(8, 130)
                .columnType(8, java.time.LocalDateTime.class)
                .onDoubleClick((viewRow, modelRow) -> runGuarded(
                        () -> openEditTeamDialog(rowId(modelRow))))
                .contextMenu(this::buildContextMenu)
                .build();
        jScrollPane1.setViewportView(table);
        loadTeams();
    }

    private void setupActions() {
        // SearchField debounces changes (300 ms) and fires immediately on Enter.
        if (textSearch instanceof SearchField searchField) {
            searchField.onTextChanged(t -> loadTeams());
        }
        btnNewTeam.addActionListener(e -> runGuarded(this::openNewTeamDialog));
        btnDelete.addActionListener(e -> runGuarded(this::deleteSelectedTeam));
        if (!SecurityService.can(Permissions.TEAM_CREATE)) {
            btnNewTeam.setEnabled(false);
            btnNewTeam.setToolTipText("Requires permission: TEAM_CREATE");
        }
        if (!SecurityService.can(Permissions.TEAM_UPDATE)) {
            btnDelete.setEnabled(false);
            btnDelete.setToolTipText("Requires permission: TEAM_UPDATE");
        }
    }

    private javax.swing.JPopupMenu buildContextMenu() {
        javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();
        if (SecurityService.can(Permissions.TEAM_MEMBER_VIEW)) {
            // Non-destructive action first; omit the item without permission
            // rather than showing a dead one.
            javax.swing.JMenuItem viewItem = new javax.swing.JMenuItem(viewMembersLabel());
            viewItem.addActionListener(e -> runGuarded(this::openTeamMembersView));
            menu.add(viewItem);
            menu.addSeparator();
        }
        javax.swing.JMenuItem editItem = new javax.swing.JMenuItem("Edit");
        editItem.addActionListener(e -> runGuarded(() -> {
            int row = table.selectedModelRow();
            if (row >= 0) {
                openEditTeamDialog(rowId(row));
            }
        }));
        javax.swing.JMenuItem deleteItem = new javax.swing.JMenuItem("Delete");
        deleteItem.addActionListener(e -> runGuarded(this::deleteSelectedTeam));
        menu.add(editItem);
        menu.add(deleteItem);
        return menu;
    }

    /** Menu label including the row's member count, e.g. "View Team Members (3)". */
    private String viewMembersLabel() {
        // The right-clicked row is already selected when the menu is built.
        int row = table.selectedModelRow();
        if (row < 0) {
            return "View Team Members";
        }
        return "View Team Members (" + table.getModel().getValueAt(row, 5) + ")";
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

    private void openNewTeamDialog() {
        SecurityService.require(Permissions.TEAM_CREATE);
        TeamDialog dialog = new TeamDialog(ownerFrame(), true);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
        loadTeams();
    }

    private void openEditTeamDialog(long id) {
        SecurityService.require(Permissions.TEAM_UPDATE);
        UiThread.executeAsync("Load team",
                () -> TeamRepository.findById(id),
                optional -> optional.ifPresent(team -> {
                    TeamDialog dialog = new TeamDialog(ownerFrame(), true, team);
                    dialog.setLocationRelativeTo(this);
                    dialog.setVisible(true);
                    loadTeams();
                }));
    }

    /** Opens the read-only member list for the right-clicked team row. */
    private void openTeamMembersView() {
        SecurityService.require(Permissions.TEAM_MEMBER_VIEW);
        int modelRow = table.selectedModelRow();
        if (modelRow < 0) {
            Dialogs.info(this, "View Team Members", "Select a team row first.");
            return;
        }
        long id = rowId(modelRow);
        String name = String.valueOf(table.getModel().getValueAt(modelRow, 1));
        TeamMemberView dialog = new TeamMemberView(ownerFrame(), true, id, name);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private void deleteSelectedTeam() {
        SecurityService.require(Permissions.TEAM_UPDATE);
        int modelRow = table.selectedModelRow();
        if (modelRow < 0) {
            Dialogs.info(this, "Delete Team", "Select a team row first.");
            return;
        }
        long id = rowId(modelRow);
        String name = String.valueOf(table.getModel().getValueAt(modelRow, 1));
        if (!Dialogs.confirm(this, "Delete Team",
                "Delete team '" + name + "'? This is a soft delete.")) {
            return;
        }
        Long userId = SessionContext.isAuthenticated() ? SessionContext.currentUserId() : null;
        UiThread.executeAsync("Delete team",
                () -> TeamRepository.softDelete(id, userId),
                deleted -> loadTeams());
    }

    private void loadTeams() {
        String keyword = textSearch.getText();
        UiThread.executeAsync("Load teams",
                () -> TeamRepository.searchAsTableRows(keyword, null, null),
                rows -> {
                    // Restore the table in case a previous load failed and showed ErrorPanel.
                    jScrollPane1.setViewportView(table);
                    table.setRows(rows);
                },
                ex -> showLoadError(ex, this::loadTeams, "Cannot load the team list."));
    }

    /**
     * Swaps the table area for an inline error state with retry (details go to the
     * log).
     */
    private void showLoadError(Exception ex, Runnable retry, String message) {
        LOG.warn("Teams panel load failed", ex);
        jScrollPane1.setViewportView(new ErrorPanel(
                message + " Check that the database is running, then try again.", retry));
    }

    public void refreshTable() {
        loadTeams();
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
        btnNewTeam = new javax.swing.JButton();
        btnDelete = new javax.swing.JButton();
        textSearch =  new SearchField("Search by name...");
        footer = new javax.swing.JPanel();
        body = new javax.swing.JPanel();
        jScrollPane1 = new javax.swing.JScrollPane();
        tableTeam = new javax.swing.JTable();

        setLayout(new java.awt.BorderLayout());

        header.setPreferredSize(new java.awt.Dimension(1237, 60));

        btnNewTeam.setText("+ New Team");

        btnDelete.setText("Delete");

        javax.swing.GroupLayout headerLayout = new javax.swing.GroupLayout(header);
        header.setLayout(headerLayout);
        headerLayout.setHorizontalGroup(
            headerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, headerLayout.createSequentialGroup()
                .addGap(60, 60, 60)
                .addComponent(textSearch, javax.swing.GroupLayout.PREFERRED_SIZE, 220, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 701, Short.MAX_VALUE)
                .addComponent(btnDelete)
                .addGap(40, 40, 40)
                .addComponent(btnNewTeam)
                .addGap(60, 60, 60))
        );
        headerLayout.setVerticalGroup(
            headerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, headerLayout.createSequentialGroup()
                .addContainerGap(20, Short.MAX_VALUE)
                .addGroup(headerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(btnNewTeam)
                    .addComponent(btnDelete)
                    .addComponent(textSearch, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(17, 17, 17))
        );

        add(header, java.awt.BorderLayout.PAGE_START);

        footer.setPreferredSize(new java.awt.Dimension(1237, 40));

        javax.swing.GroupLayout footerLayout = new javax.swing.GroupLayout(footer);
        footer.setLayout(footerLayout);
        footerLayout.setHorizontalGroup(
            footerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 1251, Short.MAX_VALUE)
        );
        footerLayout.setVerticalGroup(
            footerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 40, Short.MAX_VALUE)
        );

        add(footer, java.awt.BorderLayout.PAGE_END);

        tableTeam.setModel(new javax.swing.table.DefaultTableModel(
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
        jScrollPane1.setViewportView(tableTeam);

        javax.swing.GroupLayout bodyLayout = new javax.swing.GroupLayout(body);
        body.setLayout(bodyLayout);
        bodyLayout.setHorizontalGroup(
            bodyLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane1, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 1251, Short.MAX_VALUE)
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
    private javax.swing.JButton btnNewTeam;
    private javax.swing.JPanel footer;
    private javax.swing.JPanel header;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JTable tableTeam;
    private javax.swing.JTextField textSearch;
    // End of variables declaration//GEN-END:variables
}
