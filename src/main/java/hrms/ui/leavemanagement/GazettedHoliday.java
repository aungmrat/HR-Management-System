/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/GUIForms/JPanel.java to edit this template
 */
package hrms.ui.leavemanagement;

import java.time.LocalDate;

import javax.swing.SpinnerNumberModel;

import hrms.component.SearchField;

import hrms.component.ErrorPanel;
import hrms.component.HrmsTable;
import hrms.exception.AuthorizationException;
import hrms.exception.ErrorHandler;
import hrms.repository.GazettedHolidayRepository;
import hrms.util.Dialogs;
import hrms.util.UiThread;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manage gazetted holidays as a table-only screen (create/edit/delete). The
 * month calendar lives on the Holiday Calendar screen; recurring is
 * metadata-free by design (Myanmar holidays move yearly).
 *
 * @author aungmrat
 */
public class GazettedHoliday extends javax.swing.JPanel {

  private static final Logger LOG = LoggerFactory.getLogger(GazettedHoliday.class);

  /**
   * HrmsTable lives outside the GEN block so Form Designer saves cannot wipe it.
   */
  private HrmsTable table;

  /** Footer count line, added to the GEN footer panel at runtime. */
  private javax.swing.JLabel labelFooter;

  /**
   * Creates new form GazettedHoliday
   */
  public GazettedHoliday() {
    initComponents();
    setupTable();
    setupActions();
    refreshTable();
  }

  private void setupActions() {
    jButton1.addActionListener(e -> runGuarded(this::openNewDialog));
    spinnerYear.setModel(new SpinnerNumberModel(LocalDate.now().getYear(), 2020, 2100, 1));
    spinnerYear.addChangeListener(e -> loadTable());
    if (textSearch instanceof SearchField sf) {
      sf.onTextChanged(keyword -> loadTable());
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

  private void openNewDialog() {
    GazettedHolidayDialog dialog = new GazettedHolidayDialog(ownerFrame(), true);
    dialog.setLocationRelativeTo(this);
    dialog.setVisible(true);
    refreshTable();
  }

  private void openEditDialog(long id) {
    UiThread.executeAsync("Load gazetted holiday",
        () -> GazettedHolidayRepository.findById(id),
        optional -> optional.ifPresent(holiday -> {
          GazettedHolidayDialog dialog =
                  new GazettedHolidayDialog(ownerFrame(), true, holiday);
          dialog.setLocationRelativeTo(this);
          dialog.setVisible(true);
          refreshTable();
        }),
        error -> ErrorHandler.handle(this, error));
  }

  private void deleteSelected() {
    int modelRow = table.selectedModelRow();
    if (modelRow < 0) {
      Dialogs.info(this, "Delete Gazetted Holiday", "Select a holiday row first.");
      return;
    }
    long id = ((Number) table.getModel().getValueAt(modelRow, 0)).longValue();
    String title = String.valueOf(table.getModel().getValueAt(modelRow, 1));
    String from = String.valueOf(table.getModel().getValueAt(modelRow, 2));
    String to = String.valueOf(table.getModel().getValueAt(modelRow, 3));
    String range = from.equals(to) ? from : from + " \u00b7 " + to;
    if (!Dialogs.confirm(this, "Delete Gazetted Holiday",
        "Delete '" + title + "' (" + range + ")? This is a soft delete.")) {
      return;
    }
    Long userId = hrms.security.SessionContext.isAuthenticated()
            ? hrms.security.SessionContext.currentUserId() : null;
    UiThread.executeAsync("Delete gazetted holiday",
        () -> GazettedHolidayRepository.softDelete(id, userId),
        deleted -> refreshTable());
  }

  private void setupTable() {
    table = HrmsTable.builder(
        "ID", "Title", "From", "To", "Days", "Recurring",
        "Status", "Created By", "Created Date")
        .hiddenColumn(0)
        .fixedColumn(1, 200)
        .fixedColumn(2, 95)
        .fixedColumn(3, 95)
        .fixedColumn(4, 60)
        .fixedColumn(5, 85)
        .fixedColumn(6, 80)
        .fixedColumn(7, 120)
        .fixedColumn(8, 130)
        .onDoubleClick((viewRow, modelRow) -> runGuarded(() ->
            openEditDialog(((Number) table.getModel().getValueAt(modelRow, 0)).longValue())))
        .contextMenu(() -> {
          javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();
          javax.swing.JMenuItem editItem = new javax.swing.JMenuItem("Edit");
          editItem.addActionListener(e -> runGuarded(() -> {
            int row = table.selectedModelRow();
            if (row >= 0) {
              openEditDialog(((Number) table.getModel().getValueAt(row, 0)).longValue());
            }
          }));
          javax.swing.JMenuItem deleteItem = new javax.swing.JMenuItem("Delete");
          deleteItem.addActionListener(e -> runGuarded(this::deleteSelected));
          menu.add(editItem);
          menu.add(deleteItem);
          return menu;
        })
        .build();
    jScrollPane1.setViewportView(table);

    labelFooter = new javax.swing.JLabel(" ");
    labelFooter.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 60, 0, 60));
    footer.setLayout(new java.awt.BorderLayout());
    footer.add(labelFooter, java.awt.BorderLayout.CENTER);
  }

  private void loadTable() {
    String keyword = textSearch.getText() == null ? ""
            : textSearch.getText().trim().toLowerCase(java.util.Locale.ROOT);
    int year = spinnerYear.getValue() instanceof Number number
            ? number.intValue() : LocalDate.now().getYear();
    UiThread.executeAsync("Load gazetted holidays",
        () -> GazettedHolidayRepository.listAll(),
        holidays -> {
          // Restore the table in case a previous load failed and showed ErrorPanel.
          jScrollPane1.setViewportView(table);
          java.util.List<Object[]> rows = new java.util.ArrayList<>(holidays.size());
          for (hrms.model.GazettedHoliday holiday : holidays) {
            if (holiday.getStartDate() == null
                    || holiday.getStartDate().getYear() != year) {
              continue;
            }
            if (!keyword.isEmpty() && !matches(holiday, keyword)) {
              continue;
            }
            rows.add(holiday.toTableRow());
          }
          table.setRows(rows);
          labelFooter.setText(rows.size() == 1 ? "1 holiday" : rows.size() + " holidays");
        },
        ex -> showLoadError(ex));
  }

  private static boolean matches(hrms.model.GazettedHoliday holiday, String keyword) {
    return contains(holiday.getTitle(), keyword) || contains(holiday.getRemark(), keyword);
  }

  private static boolean contains(String value, String keyword) {
    return value != null && value.toLowerCase(java.util.Locale.ROOT).contains(keyword);
  }

  /**
   * Swaps the table area for an inline error state with retry (details go to the
   * log).
   */
  private void showLoadError(Exception ex) {
    LOG.warn("Cannot load gazetted holiday list", ex);
    jScrollPane1.setViewportView(new ErrorPanel(
        "Cannot load the gazetted holiday list. Check that the database is running, then try again.",
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
    jButton1 = new javax.swing.JButton();
    textSearch =  new SearchField("Search by title...");
    labelYear = new javax.swing.JLabel();
    spinnerYear = new javax.swing.JSpinner();
    footer = new javax.swing.JPanel();
    body = new javax.swing.JPanel();
    jPanel2 = new javax.swing.JPanel();
    jScrollPane1 = new javax.swing.JScrollPane();
    tableGazettedHoliday = new javax.swing.JTable();

    setLayout(new java.awt.BorderLayout());

    header.setPreferredSize(new java.awt.Dimension(1237, 60));

    jButton1.setText("+ New Gazetted Holiday");

    labelYear.setText("Year:");

    javax.swing.GroupLayout headerLayout = new javax.swing.GroupLayout(header);
    header.setLayout(headerLayout);
    headerLayout.setHorizontalGroup(
      headerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, headerLayout.createSequentialGroup()
        .addGap(40, 40, 40)
        .addComponent(textSearch, javax.swing.GroupLayout.PREFERRED_SIZE, 200, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(39, 39, 39)
        .addComponent(labelYear)
        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
        .addComponent(spinnerYear, javax.swing.GroupLayout.PREFERRED_SIZE, 86, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 618, Short.MAX_VALUE)
        .addComponent(jButton1)
        .addGap(60, 60, 60))
    );
    headerLayout.setVerticalGroup(
      headerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGroup(headerLayout.createSequentialGroup()
        .addGap(16, 16, 16)
        .addGroup(headerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
          .addComponent(jButton1)
          .addComponent(textSearch, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
          .addComponent(labelYear)
          .addComponent(spinnerYear, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
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

    body.setLayout(new java.awt.BorderLayout());

    tableGazettedHoliday.setModel(new javax.swing.table.DefaultTableModel(
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
    jScrollPane1.setViewportView(tableGazettedHoliday);

    javax.swing.GroupLayout jPanel2Layout = new javax.swing.GroupLayout(jPanel2);
    jPanel2.setLayout(jPanel2Layout);
    jPanel2Layout.setHorizontalGroup(
      jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 1237, Short.MAX_VALUE)
    );
    jPanel2Layout.setVerticalGroup(
      jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 740, Short.MAX_VALUE)
    );

    body.add(jPanel2, java.awt.BorderLayout.CENTER);

    add(body, java.awt.BorderLayout.CENTER);
  }// </editor-fold>//GEN-END:initComponents


  // Variables declaration - do not modify//GEN-BEGIN:variables
  private javax.swing.JPanel body;
  private javax.swing.JPanel footer;
  private javax.swing.JPanel header;
  private javax.swing.JButton jButton1;
  private javax.swing.JPanel jPanel2;
  private javax.swing.JScrollPane jScrollPane1;
  private javax.swing.JLabel labelYear;
  private javax.swing.JSpinner spinnerYear;
  private javax.swing.JTable tableGazettedHoliday;
  private javax.swing.JTextField textSearch;
  // End of variables declaration//GEN-END:variables
}
