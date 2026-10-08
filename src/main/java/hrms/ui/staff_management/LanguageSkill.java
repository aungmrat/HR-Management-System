/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/GUIForms/JPanel.java to edit this template
 */
package hrms.ui.staff_management;

import hrms.component.ErrorPanel;
import hrms.component.HrmsTable;
import hrms.component.SearchField;
import hrms.exception.AuthorizationException;
import hrms.exception.ErrorHandler;
import hrms.repository.LanguageSkillRepository;
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
public class LanguageSkill extends javax.swing.JPanel {

  private static final Logger LOG = LoggerFactory.getLogger(LanguageSkill.class);

  /**
   * HrmsTable lives outside the GEN block so Form Designer saves cannot wipe it.
   */
  private HrmsTable table;

  /**
   * Creates new form LanguageSkill
   */
  public LanguageSkill() {
    initComponents();
    setupTable();
    setupActions();
  }

  private void setupTable() {
    // Columns must match LanguageSkillRepository.searchAsTableRows():
    // ID, Staff, Language, Speaking, Listening, Writing, Reading.
    table = HrmsTable.builder(
            "ID", "Staff", "Language", "Speaking", "Listening", "Writing", "Reading")
        .hiddenColumn(0)
        .fixedColumn(1, 240)
        .fixedColumn(2, 150)
        .fixedColumn(3, 110)
        .fixedColumn(4, 110)
        .fixedColumn(5, 110)
        .fixedColumn(6, 110)
        .onDoubleClick((viewRow, modelRow) -> runGuarded(
            () -> openEditDialog(rowId(modelRow))))
        .contextMenu(this::buildContextMenu)
        .build();
    jScrollPane1.setViewportView(table);
    loadSkills();
  }

  private void setupActions() {
    // SearchField debounces changes (300 ms) and fires immediately on Enter.
    if (textSearch instanceof SearchField searchField) {
      searchField.onTextChanged(t -> loadSkills());
    }
    btnNewLanguage.addActionListener(e -> runGuarded(this::openNewDialog));
    btnDelete.addActionListener(e -> runGuarded(this::removeSelected));
    if (!SecurityService.can(Permissions.STAFF_UPDATE)) {
      btnNewLanguage.setEnabled(false);
      btnNewLanguage.setToolTipText("Requires permission: STAFF_UPDATE");
      btnDelete.setEnabled(false);
      btnDelete.setToolTipText("Requires permission: STAFF_UPDATE");
    }
  }

  private javax.swing.JPopupMenu buildContextMenu() {
    javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();
    javax.swing.JMenuItem editItem = new javax.swing.JMenuItem("Edit");
    editItem.addActionListener(e -> runGuarded(() -> {
      int row = table.selectedModelRow();
      if (row >= 0) {
        openEditDialog(rowId(row));
      }
    }));
    javax.swing.JMenuItem deleteItem = new javax.swing.JMenuItem("Delete");
    deleteItem.addActionListener(e -> runGuarded(this::removeSelected));
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

  private void openNewDialog() {
    SecurityService.require(Permissions.STAFF_UPDATE);
    LanguageSkillDialog dialog = new LanguageSkillDialog(ownerFrame(), true);
    dialog.setLocationRelativeTo(this);
    dialog.setVisible(true);
    loadSkills();
  }

  private void openEditDialog(long id) {
    SecurityService.require(Permissions.STAFF_UPDATE);
    UiThread.executeAsync("Load language skill",
        () -> LanguageSkillRepository.findById(id),
        optional -> optional.ifPresent(skill -> {
          LanguageSkillDialog dialog = new LanguageSkillDialog(ownerFrame(), true, skill);
          dialog.setLocationRelativeTo(this);
          dialog.setVisible(true);
          loadSkills();
        }));
  }

  private void removeSelected() {
    SecurityService.require(Permissions.STAFF_UPDATE);
    int modelRow = table.selectedModelRow();
    if (modelRow < 0) {
      Dialogs.info(this, "Delete Language Skill", "Select a language skill row first.");
      return;
    }
    long id = rowId(modelRow);
    String staff = String.valueOf(table.getModel().getValueAt(modelRow, 1));
    String language = String.valueOf(table.getModel().getValueAt(modelRow, 2));
    if (!Dialogs.confirm(this, "Delete Language Skill",
        "Delete language '" + language + "' for staff '" + staff + "'?")) {
      return;
    }
    long userId = SessionContext.isAuthenticated() ? SessionContext.currentUserId() : 0;
    UiThread.executeAsync("Delete language skill",
        () -> {
          LanguageSkillRepository.softDelete(id, userId);
          return true;
        },
        removed -> loadSkills());
  }

  private void loadSkills() {
    String keyword = textSearch.getText();
    UiThread.executeAsync("Load language skills",
        () -> LanguageSkillRepository.searchAsTableRows(keyword),
        rows -> {
          // Restore the table in case a previous load failed and showed ErrorPanel.
          jScrollPane1.setViewportView(table);
          table.setRows(rows);
        },
        ex -> showLoadError(ex, this::loadSkills, "Cannot load the language skill list."));
  }

  /**
   * Swaps the table area for an inline error state with retry (details go to the
   * log).
   */
  private void showLoadError(Exception ex, Runnable retry, String message) {
    LOG.warn("Language skill panel load failed", ex);
    jScrollPane1.setViewportView(new ErrorPanel(
        message + " Check that the database is running, then try again.", retry));
  }

  public void refreshTable() {
    loadSkills();
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
    btnNewLanguage = new javax.swing.JButton();
    btnDelete = new javax.swing.JButton();
    textSearch =  new SearchField("Search by name or code...");
    footer = new javax.swing.JPanel();
    body = new javax.swing.JPanel();
    jScrollPane1 = new javax.swing.JScrollPane();
    tableLanguage = new javax.swing.JTable();

    setLayout(new java.awt.BorderLayout());

    header.setPreferredSize(new java.awt.Dimension(1237, 60));

    btnNewLanguage.setText("+ New Language");

    btnDelete.setText("Delete");

    javax.swing.GroupLayout headerLayout = new javax.swing.GroupLayout(header);
    header.setLayout(headerLayout);
    headerLayout.setHorizontalGroup(
      headerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, headerLayout.createSequentialGroup()
        .addGap(60, 60, 60)
        .addComponent(textSearch, javax.swing.GroupLayout.PREFERRED_SIZE, 220, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 658, Short.MAX_VALUE)
        .addComponent(btnDelete)
        .addGap(40, 40, 40)
        .addComponent(btnNewLanguage)
        .addGap(60, 60, 60))
    );
    headerLayout.setVerticalGroup(
      headerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGroup(headerLayout.createSequentialGroup()
        .addGap(16, 16, 16)
        .addGroup(headerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
          .addComponent(btnNewLanguage)
          .addComponent(btnDelete)
          .addComponent(textSearch, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
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

    tableLanguage.setModel(new javax.swing.table.DefaultTableModel(
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
    jScrollPane1.setViewportView(tableLanguage);

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
  private javax.swing.JButton btnNewLanguage;
  private javax.swing.JPanel footer;
  private javax.swing.JPanel header;
  private javax.swing.JScrollPane jScrollPane1;
  private javax.swing.JTable tableLanguage;
  private javax.swing.JTextField textSearch;
  // End of variables declaration//GEN-END:variables
}
