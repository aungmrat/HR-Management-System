/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/GUIForms/JPanel.java to edit this template
 */
package hrms.ui.payrollandcompensation;

import hrms.component.SearchField;

import hrms.component.ErrorPanel;
import hrms.component.HrmsTable;
import hrms.repository.StaffSalaryRepository;
import hrms.util.UiThread;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 *
 * @author aungmrat
 */
public class StaffSalary extends javax.swing.JPanel {

  private static final Logger LOG = LoggerFactory.getLogger(StaffSalary.class);

  /**
   * HrmsTable lives outside the GEN block so Form Designer saves cannot wipe it.
   */
  private HrmsTable table;

  /** Footer count line, added to the GEN footer panel at runtime. */
  private javax.swing.JLabel labelFooter;

  /**
   * Creates new form StaffSalary
   */
  public StaffSalary() {
    initComponents();
    setupTable();
    setupActions();
    refreshTable();
  }

  private void setupActions() {
    if (textSearch instanceof SearchField sf) {
      sf.onTextChanged(keyword -> loadTable());
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
        "ID", "Code", "Name", "Position", "Salary", "Effective From", "Rate Band")
        .hiddenColumn(0)
        .fixedColumn(1, 90)
        .fixedColumn(2, 200)
        .fixedColumn(3, 180)
        .fixedColumn(4, 115)
        .fixedColumn(5, 115)
        .fixedColumn(6, 170)
        .onDoubleClick((viewRow, modelRow) -> openHistory(modelRow))
        .contextMenu(() -> {
          javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();
          javax.swing.JMenuItem viewItem = new javax.swing.JMenuItem("View History");
          viewItem.addActionListener(e -> {
            int row = table.selectedModelRow();
            if (row >= 0) {
              openHistory(row);
            }
          });
          menu.add(viewItem);
          return menu;
        })
        .build();
    jScrollPane1.setViewportView(table);

    labelFooter = new javax.swing.JLabel(" ");
    labelFooter.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 60, 0, 60));
    footer.setLayout(new java.awt.BorderLayout());
    footer.add(labelFooter, java.awt.BorderLayout.CENTER);
  }

  private void openHistory(int modelRow) {
    long staffId = ((Number) table.getModel().getValueAt(modelRow, 0)).longValue();
    String code = String.valueOf(table.getModel().getValueAt(modelRow, 1));
    String name = String.valueOf(table.getModel().getValueAt(modelRow, 2));
    StaffSalaryHistoryDialog dialog =
            new StaffSalaryHistoryDialog(ownerFrame(), true, staffId, code, name);
    dialog.setLocationRelativeTo(this);
    dialog.setVisible(true);
  }

  private void loadTable() {
    String keyword = textSearch.getText() == null ? ""
            : textSearch.getText().trim().toLowerCase(java.util.Locale.ROOT);
    UiThread.executeAsync("Load staff salaries",
        () -> StaffSalaryRepository.listCurrent(),
        salaries -> {
          // Restore the table in case a previous load failed and showed ErrorPanel.
          jScrollPane1.setViewportView(table);
          java.util.List<Object[]> rows = new java.util.ArrayList<>(salaries.size());
          for (hrms.model.StaffSalary salary : salaries) {
            if (!matches(salary, keyword)) {
              continue;
            }
            rows.add(salary.toCurrentTableRow());
          }
          table.setRows(rows);
          labelFooter.setText(rows.size() == 1 ? "1 staff" : rows.size() + " staff");
        },
        ex -> showLoadError(ex));
  }

  private static boolean matches(hrms.model.StaffSalary salary, String keyword) {
    return contains(salary.getStaffCode(), keyword)
            || contains(salary.getStaffName(), keyword)
            || contains(salary.getPositionName(), keyword);
  }

  private static boolean contains(String value, String keyword) {
    return value != null && value.toLowerCase(java.util.Locale.ROOT).contains(keyword);
  }

  /**
   * Swaps the table area for an inline error state with retry (details go to the
   * log).
   */
  private void showLoadError(Exception ex) {
    LOG.warn("Cannot load staff salary list", ex);
    jScrollPane1.setViewportView(new ErrorPanel(
        "Cannot load the staff salary list. Check that the database is running, then try again.",
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
    textSearch =  new SearchField("Search by name...");
    footer = new javax.swing.JPanel();
    labelNoOfStaff = new javax.swing.JLabel();
    body = new javax.swing.JPanel();
    jScrollPane1 = new javax.swing.JScrollPane();
    tableStaffSalary = new javax.swing.JTable();

    setLayout(new java.awt.BorderLayout());

    header.setPreferredSize(new java.awt.Dimension(1237, 60));

    javax.swing.GroupLayout headerLayout = new javax.swing.GroupLayout(header);
    header.setLayout(headerLayout);
    headerLayout.setHorizontalGroup(
      headerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGroup(headerLayout.createSequentialGroup()
        .addGap(60, 60, 60)
        .addComponent(textSearch, javax.swing.GroupLayout.PREFERRED_SIZE, 200, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addContainerGap(977, Short.MAX_VALUE))
    );
    headerLayout.setVerticalGroup(
      headerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, headerLayout.createSequentialGroup()
        .addContainerGap(18, Short.MAX_VALUE)
        .addComponent(textSearch, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(16, 16, 16))
    );

    add(header, java.awt.BorderLayout.PAGE_START);

    footer.setPreferredSize(new java.awt.Dimension(1237, 40));

    labelNoOfStaff.setText("No of Staff");

    javax.swing.GroupLayout footerLayout = new javax.swing.GroupLayout(footer);
    footer.setLayout(footerLayout);
    footerLayout.setHorizontalGroup(
      footerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGroup(footerLayout.createSequentialGroup()
        .addGap(60, 60, 60)
        .addComponent(labelNoOfStaff, javax.swing.GroupLayout.PREFERRED_SIZE, 78, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addContainerGap(1099, Short.MAX_VALUE))
    );
    footerLayout.setVerticalGroup(
      footerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, footerLayout.createSequentialGroup()
        .addContainerGap(18, Short.MAX_VALUE)
        .addComponent(labelNoOfStaff)
        .addContainerGap())
    );

    add(footer, java.awt.BorderLayout.PAGE_END);

    tableStaffSalary.setModel(new javax.swing.table.DefaultTableModel(
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
    jScrollPane1.setViewportView(tableStaffSalary);

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
  private javax.swing.JPanel footer;
  private javax.swing.JPanel header;
  private javax.swing.JScrollPane jScrollPane1;
  private javax.swing.JLabel labelNoOfStaff;
  private javax.swing.JTable tableStaffSalary;
  private javax.swing.JTextField textSearch;
  // End of variables declaration//GEN-END:variables
}
