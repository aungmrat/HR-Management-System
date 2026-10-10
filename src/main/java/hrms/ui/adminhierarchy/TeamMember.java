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
import hrms.model.Team;
import hrms.repository.TeamMemberRepository;
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
public class TeamMember extends javax.swing.JPanel {

    private static final Logger LOG = LoggerFactory.getLogger(TeamMember.class);

    /**
     * HrmsTable lives outside the GEN block so Form Designer saves cannot wipe it.
     */
    private HrmsTable table;

    /**
     * Combo entry holding the database id behind a readable label.
     * toString() is what Swing renders, so the dropdown shows the label
     * while getValue() gives the FK id to filter on (null = All Teams).
     */
    private record ComboItem(Long value, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    /**
     * Creates new form TeamMember
     */
    public TeamMember() {
        initComponents();
        setupTable();
        setupActions();
        loadTeamFilter();
    }

    private void setupTable() {
        // Columns must match TeamMember.toTableRow(): ID, Staff, Team, Parent,
        // Remark, Created By, Created Date. The table has no status column,
        // so there is no badgeColumn.
        table = HrmsTable.builder(
                "ID", "Staff", "Team", "Parent", "Remark", "Created By", "Created Date")
                .hiddenColumn(0)
                .fixedColumn(1, 220)
                .fixedColumn(2, 160)
                .fixedColumn(3, 180)
                .fixedColumn(4, 170)
                .fixedColumn(5, 110)
                .fixedColumn(6, 130)
                .columnType(6, java.time.LocalDateTime.class)
                .onDoubleClick((viewRow, modelRow) -> runGuarded(
                        () -> openEditMemberDialog(rowId(modelRow))))
                .contextMenu(this::buildContextMenu)
                .build();
        jScrollPane1.setViewportView(table);
        loadMembers();
    }

    private void setupActions() {
        // SearchField debounces changes (300 ms) and fires immediately on Enter.
        if (textSearch instanceof SearchField searchField) {
            searchField.onTextChanged(t -> loadMembers());
        }
        comboTeam.addActionListener(e -> loadMembers());
        btnNewMember.addActionListener(e -> runGuarded(this::openNewMemberDialog));
        btnRemove.addActionListener(e -> runGuarded(this::removeSelectedMember));
        if (!SecurityService.can(Permissions.TEAM_MEMBER_CREATE)) {
            btnNewMember.setEnabled(false);
            btnNewMember.setToolTipText("Requires permission: TEAM_MEMBER_CREATE");
        }
        if (!SecurityService.can(Permissions.TEAM_MEMBER_UPDATE)) {
            btnRemove.setEnabled(false);
            btnRemove.setToolTipText("Requires permission: TEAM_MEMBER_UPDATE");
        }
    }

    private javax.swing.JPopupMenu buildContextMenu() {
        javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();
        javax.swing.JMenuItem editItem = new javax.swing.JMenuItem("Edit");
        editItem.addActionListener(e -> runGuarded(() -> {
            int row = table.selectedModelRow();
            if (row >= 0) {
                openEditMemberDialog(rowId(row));
            }
        }));
        javax.swing.JMenuItem removeItem = new javax.swing.JMenuItem("Remove");
        removeItem.addActionListener(e -> runGuarded(this::removeSelectedMember));
        menu.add(editItem);
        menu.add(removeItem);
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

    /** Fills the team filter combo; the first entry means "All Teams". */
    private void loadTeamFilter() {
        UiThread.executeAsync("Load team filter",
                TeamRepository::listAll,
                teams -> {
                    comboTeam.removeAllItems();
                    comboTeam.addItem(new ComboItem(null, "All Teams"));
                    for (Team team : teams) {
                        comboTeam.addItem(new ComboItem(team.getId(), team.getName()));
                    }
                });
    }

    /** Selected team filter; null = All Teams. */
    private Long selectedTeamFilter() {
        ComboItem item = (ComboItem) comboTeam.getSelectedItem();
        return item == null ? null : item.value();
    }

    private void openNewMemberDialog() {
        SecurityService.require(Permissions.TEAM_MEMBER_CREATE);
        TeamMemberDialog dialog = new TeamMemberDialog(ownerFrame(), true);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
        loadMembers();
    }

    private void openEditMemberDialog(long id) {
        SecurityService.require(Permissions.TEAM_MEMBER_UPDATE);
        UiThread.executeAsync("Load team member",
                () -> TeamMemberRepository.findById(id),
                optional -> optional.ifPresent(member -> {
                    TeamMemberDialog dialog = new TeamMemberDialog(ownerFrame(), true, member);
                    dialog.setLocationRelativeTo(this);
                    dialog.setVisible(true);
                    loadMembers();
                }));
    }

    private void removeSelectedMember() {
        SecurityService.require(Permissions.TEAM_MEMBER_UPDATE);
        int modelRow = table.selectedModelRow();
        if (modelRow < 0) {
            Dialogs.info(this, "Remove Team Member", "Select a member row first.");
            return;
        }
        long id = rowId(modelRow);
        String staff = String.valueOf(table.getModel().getValueAt(modelRow, 1));
        String team = String.valueOf(table.getModel().getValueAt(modelRow, 2));
        if (!Dialogs.confirm(this, "Remove Team Member",
                "Remove '" + staff + "' from team '" + team + "'?")) {
            return;
        }
        Long userId = SessionContext.isAuthenticated() ? SessionContext.currentUserId() : null;
        UiThread.executeAsync("Remove team member",
                () -> TeamMemberRepository.softDelete(id, userId),
                removed -> loadMembers());
    }

    private void loadMembers() {
        String keyword = textSearch.getText();
        Long teamId = selectedTeamFilter();
        UiThread.executeAsync("Load team members",
                () -> TeamMemberRepository.searchAsTableRows(keyword, teamId),
                rows -> {
                    // Restore the table in case a previous load failed and showed ErrorPanel.
                    jScrollPane1.setViewportView(table);
                    table.setRows(rows);
                },
                ex -> showLoadError(ex, this::loadMembers, "Cannot load the team member list."));
    }

    /**
     * Swaps the table area for an inline error state with retry (details go to the
     * log).
     */
    private void showLoadError(Exception ex, Runnable retry, String message) {
        LOG.warn("Team members panel load failed", ex);
        jScrollPane1.setViewportView(new ErrorPanel(
                message + " Check that the database is running, then try again.", retry));
    }

    public void refreshTable() {
        loadMembers();
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
    btnNewMember = new javax.swing.JButton();
    textSearch =  new SearchField("Search by name...");
    btnRemove = new javax.swing.JButton();
    comboTeam = new javax.swing.JComboBox<>();
    footer = new javax.swing.JPanel();
    body = new javax.swing.JPanel();
    jScrollPane1 = new javax.swing.JScrollPane();
    tableTeamMember = new javax.swing.JTable();

    setLayout(new java.awt.BorderLayout());

    header.setPreferredSize(new java.awt.Dimension(1237, 60));

    btnNewMember.setText("+ New Team Member");

    btnRemove.setText("Remove");

    javax.swing.GroupLayout headerLayout = new javax.swing.GroupLayout(header);
    header.setLayout(headerLayout);
    headerLayout.setHorizontalGroup(
      headerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, headerLayout.createSequentialGroup()
        .addGap(60, 60, 60)
        .addComponent(textSearch, javax.swing.GroupLayout.PREFERRED_SIZE, 220, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(40, 40, 40)
        .addComponent(comboTeam, javax.swing.GroupLayout.PREFERRED_SIZE, 150, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 448, Short.MAX_VALUE)
        .addComponent(btnRemove)
        .addGap(40, 40, 40)
        .addComponent(btnNewMember)
        .addGap(60, 60, 60))
    );
    headerLayout.setVerticalGroup(
      headerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGroup(headerLayout.createSequentialGroup()
        .addGap(18, 18, 18)
        .addGroup(headerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
          .addComponent(btnNewMember)
          .addComponent(textSearch, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
          .addComponent(btnRemove)
          .addComponent(comboTeam, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
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

    tableTeamMember.setModel(new javax.swing.table.DefaultTableModel(
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
    jScrollPane1.setViewportView(tableTeamMember);

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
  private javax.swing.JButton btnNewMember;
  private javax.swing.JButton btnRemove;
  private javax.swing.JComboBox<ComboItem> comboTeam;
  private javax.swing.JPanel footer;
  private javax.swing.JPanel header;
  private javax.swing.JScrollPane jScrollPane1;
  private javax.swing.JTable tableTeamMember;
  private javax.swing.JTextField textSearch;
  // End of variables declaration//GEN-END:variables
}
