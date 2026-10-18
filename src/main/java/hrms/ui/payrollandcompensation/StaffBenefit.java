/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/GUIForms/JPanel.java to edit this template
 */
package hrms.ui.payrollandcompensation;

import hrms.component.ErrorPanel;
import hrms.component.HrmsTable;
import hrms.component.SearchField;
import hrms.exception.AuthorizationException;
import hrms.exception.ErrorHandler;
import hrms.model.Department;
import hrms.repository.DepartmentRepository;
import hrms.repository.StaffBenefitRepository;
import hrms.security.Permissions;
import hrms.security.SecurityService;
import hrms.security.SessionContext;
import hrms.util.Dialogs;
import hrms.util.UiThread;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Staff Benefit assignments: one row per effective-dated assignment (open
 * and ended). Assign inserts an open row, End closes it, Edit Dates amends
 * it, View History shows one staff member's assignments.
 *
 * @author aungmrat
 */
public class StaffBenefit extends javax.swing.JPanel {

  private static final Logger LOG = LoggerFactory.getLogger(StaffBenefit.class);

  /**
   * HrmsTable lives outside the GEN block so Form Designer saves cannot wipe it.
   */
  private HrmsTable table;

  /** Footer count line, added to the GEN footer panel at runtime. */
  private javax.swing.JLabel labelFooter;

  /** Last loaded rows (all departments); filters render from this. */
  private java.util.List<hrms.model.StaffBenefit> allRows = java.util.List.of();

  /** Departments for the header filter; combo index 0 is "All". */
  private java.util.List<Department> departments = java.util.List.of();

  /** Selected department id, null = all departments. */
  private Long currentDepartmentId;

  /**
   * Creates new form StaffBenefit
   */
  public StaffBenefit() {
    initComponents();
    setupTable();
    setupActions();
    loadDepartments();
    loadTable();
  }

  private void setupActions() {
    if (textSearch instanceof SearchField sf) {
      sf.onTextChanged(keyword -> applyFilter());
    }
    comboDepartment.addActionListener(e -> onDepartmentChanged());
    btnAssign.addActionListener(e -> runGuarded(this::openAssignDialog));
    if (!SecurityService.can(Permissions.STAFF_BENEFIT_ASSIGN)) {
      btnAssign.setEnabled(false);
      btnAssign.setToolTipText("Requires permission: STAFF_BENEFIT_ASSIGN");
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

  private void setupTable() {
    table = HrmsTable.builder(
        "ID", "Staff ID", "Name", "Position", "Benefit", "Rate",
        "Frequency", "Unit", "Effective From", "End")
        .hiddenColumn(0)
        .hiddenColumn(1)
        .fixedColumn(2, 170)
        .fixedColumn(3, 150)
        .fixedColumn(4, 180)
        .fixedColumn(5, 100)
        .fixedColumn(6, 90)
        .fixedColumn(7, 85)
        .fixedColumn(8, 115)
        .fixedColumn(9, 110)
        .onDoubleClick((viewRow, modelRow) -> runGuarded(() -> openEditDialog(modelRow)))
        .contextMenu(() -> {
          javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();
          javax.swing.JMenuItem editItem = new javax.swing.JMenuItem("Edit Dates");
          editItem.addActionListener(e -> runGuarded(() -> {
            int row = table.selectedModelRow();
            if (row >= 0) {
              openEditDialog(row);
            }
          }));
          javax.swing.JMenuItem endItem = new javax.swing.JMenuItem("End Benefit");
          endItem.addActionListener(e -> runGuarded(() -> {
            int row = table.selectedModelRow();
            if (row >= 0) {
              endBenefit(row);
            }
          }));
          javax.swing.JMenuItem historyItem = new javax.swing.JMenuItem("View History");
          historyItem.addActionListener(e -> runGuarded(() -> {
            int row = table.selectedModelRow();
            if (row >= 0) {
              openHistory(row);
            }
          }));
          menu.add(editItem);
          menu.add(endItem);
          menu.addSeparator();
          menu.add(historyItem);
          return menu;
        })
        .build();
    jScrollPane1.setViewportView(table);

    labelFooter = new javax.swing.JLabel(" ");
    labelFooter.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 60, 0, 60));
    footer.setLayout(new java.awt.BorderLayout());
    footer.add(labelFooter, java.awt.BorderLayout.CENTER);
  }

  private void openAssignDialog() {
    SecurityService.require(Permissions.STAFF_BENEFIT_ASSIGN);
    StaffBenefitDialog dialog = new StaffBenefitDialog(ownerFrame(), true, null);
    dialog.setLocationRelativeTo(this);
    dialog.setVisible(true);
    loadTable();
  }

  private void openEditDialog(int modelRow) {
    SecurityService.require(Permissions.STAFF_BENEFIT_UPDATE);
    long id = ((Number) table.getModel().getValueAt(modelRow, 0)).longValue();
    UiThread.executeAsync("Load benefit assignment",
        () -> StaffBenefitRepository.findById(id),
        optional -> optional.ifPresent(row -> {
          StaffBenefitDialog dialog =
                  new StaffBenefitDialog(ownerFrame(), true, row);
          dialog.setLocationRelativeTo(this);
          dialog.setVisible(true);
          loadTable();
        }),
        error -> ErrorHandler.handle(this, error));
  }

  private void endBenefit(int modelRow) {
    SecurityService.require(Permissions.STAFF_BENEFIT_ASSIGN);
    long id = ((Number) table.getModel().getValueAt(modelRow, 0)).longValue();
    String name = String.valueOf(table.getModel().getValueAt(modelRow, 2));
    String benefit = String.valueOf(table.getModel().getValueAt(modelRow, 4));
    String endValue = String.valueOf(table.getModel().getValueAt(modelRow, 9));
    if (!"—".equals(endValue)) {
      Dialogs.info(this, "End Benefit",
          "This assignment already ended on " + endValue + ".");
      return;
    }
    String effectiveValue = String.valueOf(table.getModel().getValueAt(modelRow, 8));
    if (!"—".equals(effectiveValue)
            && java.time.LocalDate.parse(effectiveValue).isAfter(java.time.LocalDate.now())) {
      Dialogs.info(this, "End Benefit",
          "This assignment starts on " + effectiveValue
          + ". Use Edit Dates to give it an end date.");
      return;
    }
    if (!Dialogs.confirm(this, "End Benefit",
        "End '" + benefit + "' for " + name + "? It stays active through today.")) {
      return;
    }
    Long userId = SessionContext.isAuthenticated() ? SessionContext.currentUserId() : null;
    UiThread.executeAsync("End benefit assignment",
        () -> StaffBenefitRepository.endAssignment(id, java.time.LocalDate.now(), userId),
        ok -> loadTable(),
        error -> ErrorHandler.handle(this, error));
  }

  private void openHistory(int modelRow) {
    long staffId = ((Number) table.getModel().getValueAt(modelRow, 1)).longValue();
    String name = String.valueOf(table.getModel().getValueAt(modelRow, 2));
    StaffBenefitHistoryDialog dialog =
            new StaffBenefitHistoryDialog(ownerFrame(), true, staffId, name);
    dialog.setLocationRelativeTo(this);
    dialog.setVisible(true);
  }

  private void loadDepartments() {
    UiThread.executeAsync("Load departments",
        DepartmentRepository::listAll,
        loaded -> {
          departments = loaded;
          comboDepartment.removeAllItems();
          comboDepartment.addItem("Department: All");
          for (Department department : loaded) {
            comboDepartment.addItem(department.getName());
          }
        },
        ex -> LOG.warn("Cannot load the department filter", ex));
  }

  private void onDepartmentChanged() {
    int index = comboDepartment.getSelectedIndex();
    currentDepartmentId = index <= 0 || index > departments.size()
            ? null : departments.get(index - 1).getId();
    applyFilter();
  }

  private void loadTable() {
    UiThread.executeAsync("Load staff benefits",
        StaffBenefitRepository::listAll,
        rows -> {
          // Restore the table in case a previous load failed and showed ErrorPanel.
          jScrollPane1.setViewportView(table);
          allRows = rows;
          applyFilter();
        },
        ex -> showLoadError(ex));
  }

  /** Renders {@link #allRows} through the department + search filters. */
  private void applyFilter() {
    String keyword = textSearch.getText() == null ? ""
            : textSearch.getText().trim().toLowerCase(java.util.Locale.ROOT);
    java.util.List<Object[]> rows = new java.util.ArrayList<>(allRows.size());
    for (hrms.model.StaffBenefit row : allRows) {
      if (currentDepartmentId != null && !currentDepartmentId.equals(row.getDepartmentId())) {
        continue;
      }
      if (!matches(row, keyword)) {
        continue;
      }
      rows.add(row.toTableRow());
    }
    table.setRows(rows);
    labelFooter.setText(rows.size() == 1
            ? "1 assignment" : rows.size() + " assignments");
  }

  private static boolean matches(hrms.model.StaffBenefit row, String keyword) {
    return contains(row.getStaffCode(), keyword)
            || contains(row.getStaffName(), keyword)
            || contains(row.getPositionName(), keyword)
            || contains(row.getBenefitName(), keyword);
  }

  private static boolean contains(String value, String keyword) {
    return value != null && value.toLowerCase(java.util.Locale.ROOT).contains(keyword);
  }

  /**
   * Swaps the table area for an inline error state with retry (details go to the
   * log).
   */
  private void showLoadError(Exception ex) {
    LOG.warn("Cannot load staff benefit list", ex);
    jScrollPane1.setViewportView(new ErrorPanel(
        "Cannot load the staff benefit list. Check that the database is running, then try again.",
        this::loadTable));
  }

  /** Refresh hook for MainFrame navigation. */
  public void refreshTable() {
    loadTable();
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
    btnAssign = new javax.swing.JButton();
    textSearch = new SearchField("Search by code or name...");
    comboDepartment = new javax.swing.JComboBox<>();
    footer = new javax.swing.JPanel();
    body = new javax.swing.JPanel();
    jScrollPane1 = new javax.swing.JScrollPane();
    tableStaffBenefit = new javax.swing.JTable();

    setLayout(new java.awt.BorderLayout());

    header.setPreferredSize(new java.awt.Dimension(1237, 60));

    btnAssign.setText("+ Assign");

    comboDepartment.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Department: All" }));

    javax.swing.GroupLayout headerLayout = new javax.swing.GroupLayout(header);
    header.setLayout(headerLayout);
    headerLayout.setHorizontalGroup(
      headerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, headerLayout.createSequentialGroup()
        .addGap(60, 60, 60)
        .addComponent(textSearch, javax.swing.GroupLayout.PREFERRED_SIZE, 200, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(30, 30, 30)
        .addComponent(comboDepartment, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 656, Short.MAX_VALUE)
        .addComponent(btnAssign, javax.swing.GroupLayout.PREFERRED_SIZE, 110, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(60, 60, 60))
    );
    headerLayout.setVerticalGroup(
      headerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGroup(headerLayout.createSequentialGroup()
        .addGap(16, 16, 16)
        .addGroup(headerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
          .addComponent(btnAssign)
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

    tableStaffBenefit.setModel(new javax.swing.table.DefaultTableModel(
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
    jScrollPane1.setViewportView(tableStaffBenefit);

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
  private javax.swing.JButton btnAssign;
  private javax.swing.JComboBox<String> comboDepartment;
  private javax.swing.JPanel footer;
  private javax.swing.JPanel header;
  private javax.swing.JScrollPane jScrollPane1;
  private javax.swing.JTable tableStaffBenefit;
  private javax.swing.JTextField textSearch;
  // End of variables declaration//GEN-END:variables
}
