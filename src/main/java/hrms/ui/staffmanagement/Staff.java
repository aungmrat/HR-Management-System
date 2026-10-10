/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/GUIForms/JPanel.java to edit this template
 */
package hrms.ui.staffmanagement;


import hrms.component.ErrorPanel;
import hrms.component.HrmsTable;
import hrms.component.SearchField;
import hrms.exception.AuthorizationException;
import hrms.exception.BusinessException;
import hrms.exception.ErrorHandler;
import hrms.model.Department;
import hrms.model.Office;
import hrms.model.Position;
import hrms.model.StoredFile;
import hrms.repository.DepartmentRepository;
import hrms.repository.FileRepository;
import hrms.repository.OfficeRepository;
import hrms.repository.PositionRepository;
import hrms.repository.StaffRepository;
import hrms.security.Permissions;
import hrms.security.SecurityService;
import hrms.security.SessionContext;
import hrms.util.Dialogs;
import hrms.util.FileStorage;
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
public class Staff extends javax.swing.JPanel {

  private static final Logger LOG = LoggerFactory.getLogger(Staff.class);

  /** Guards combo events fired while the filter options are still loading. */
  private boolean filterOptionsReady;

  /**
   * HrmsTable lives outside the GEN block so Form Designer saves cannot wipe it.
   */
  private HrmsTable table;

  /** Files-tab table also lives outside the GEN block. */
  private HrmsTable filesTable;

  /** Combo display name to id; "All ..." items resolve to a null filter. */
  private final Map<String, Long> departmentIds = new HashMap<>();
  private final Map<String, Long> officeIds = new HashMap<>();
  private final Map<String, Long> positionIds = new HashMap<>();

  /** Filter dropdown options loaded together off the EDT. */
  private record FilterOptions(List<Department> departments, List<Office> offices,
          List<Position> positions) {
  }

  /**
   * Creates new form Staff
   */
  public Staff() {
    initComponents();
    setupTable();
    setupFiles();
    setupActions();
  }

  private void setupTable() {
    table = HrmsTable.builder(
            "ID", "Code", "Name", "Department", "Position", "Team", "Office",
            "Phone", "Status", "Created")
        .hiddenColumn(0)
        .fixedColumn(1, 80)
        .fixedColumn(2, 180)
        .fixedColumn(3, 170)
        .fixedColumn(4, 140)
        .fixedColumn(5, 110)
        .fixedColumn(6, 110)
        .fixedColumn(7, 120)
        .fixedColumn(8, 85)
        .badgeColumn(8)
        .onDoubleClick((viewRow, modelRow) -> openEditStaffDialog(rowId(modelRow)))
        .contextMenu(this::buildContextMenu)
        .build();
    jScrollPane1.setViewportView(table);
  }

  private void setupFiles() {
    filesTable = HrmsTable.builder(
            "ID", "Name", "Type", "Source", "Uploaded By", "Uploaded")
        .hiddenColumn(0)
        .fixedColumn(1, 240)
        .fixedColumn(2, 60)
        .fixedColumn(3, 160)
        .fixedColumn(4, 140)
        .onDoubleClick((viewRow, modelRow) -> runGuarded(() -> openFile(fileRowId(modelRow))))
        .contextMenu(this::buildFilesContextMenu)
        .build();
    jScrollPane2.setViewportView(filesTable);
  }

  private javax.swing.JPopupMenu buildFilesContextMenu() {
    javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();
    javax.swing.JMenuItem openItem = new javax.swing.JMenuItem("Open");
    openItem.addActionListener(e -> runGuarded(() -> {
      int row = filesTable.selectedModelRow();
      if (row >= 0) {
        openFile(fileRowId(row));
      }
    }));
    javax.swing.JMenuItem downloadItem = new javax.swing.JMenuItem("Download");
    downloadItem.addActionListener(e -> runGuarded(() -> {
      int row = filesTable.selectedModelRow();
      if (row >= 0) {
        downloadFile(fileRowId(row));
      }
    }));
    javax.swing.JMenuItem deleteItem = new javax.swing.JMenuItem("Delete");
    deleteItem.addActionListener(e -> runGuarded(this::deleteSelectedFile));
    menu.add(openItem);
    menu.add(downloadItem);
    menu.add(deleteItem);
    return menu;
  }

  private void downloadFile(long fileId) {
    UiThread.executeAsync("Download file",
        () -> {
          StoredFile file = FileRepository.findById(fileId)
              .orElseThrow(() -> new BusinessException(
                  "File not found.", "This file no longer exists."));
          java.nio.file.Path sourcePath = FileStorage.resolve(file.getFilePath());
          if (sourcePath == null || !java.nio.file.Files.isRegularFile(sourcePath)) {
            throw new BusinessException("File not found on disk.",
                "The stored file could not be found on disk.");
          }
          javax.swing.JFileChooser chooser = new javax.swing.JFileChooser();
          chooser.setSelectedFile(new java.io.File(file.getFileName()));
          if (chooser.showSaveDialog(this) != javax.swing.JFileChooser.APPROVE_OPTION) {
            return null;
          }
          java.nio.file.Path dest = chooser.getSelectedFile().toPath();
          if (java.nio.file.Files.exists(dest)) {
            int opt = javax.swing.JOptionPane.showConfirmDialog(this,
                "File already exists. Overwrite?", "Confirm Overwrite",
                javax.swing.JOptionPane.YES_NO_OPTION);
            if (opt != javax.swing.JOptionPane.YES_OPTION) {
              return null;
            }
          }
          try {
            java.nio.file.Files.copy(sourcePath, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
          } catch (java.io.IOException ex) {
            throw new BusinessException("Cannot save file.", ex.getMessage());
          }
          return file.getFileName();
        },
        name -> {
          if (name != null) {
            Dialogs.info(this, "Download Complete", "File saved as '" + name + "'");
          }
        });
  }

  private long fileRowId(int modelRow) {
    return ((Number) filesTable.getModel().getValueAt(modelRow, 0)).longValue();
  }

  private void loadFiles() {
    String keyword = textFSearch.getText();
    UiThread.executeAsync("Load files",
        () -> FileRepository.searchAsTableRows(keyword, null),
        rows -> {
          // Restore the table in case a previous load failed and showed ErrorPanel.
          jScrollPane2.setViewportView(filesTable);
          filesTable.setRows(rows);
          LOG.info("Files list refreshed: {} rows (keyword='{}')", rows.size(), keyword);
        },
        ex -> {
          LOG.warn("Cannot load file list", ex);
          jScrollPane2.setViewportView(new ErrorPanel(
              "Cannot load the file list. Check that the database is running, then try again.",
              this::loadFiles));
        });
  }

  private void openFile(long fileId) {
    UiThread.executeAsync("Open file",
        () -> {
          StoredFile file = FileRepository.findById(fileId)
              .orElseThrow(() -> new BusinessException(
                  "File not found.", "This file no longer exists."));
          java.nio.file.Path path = FileStorage.resolve(file.getFilePath());
          if (path == null || !java.nio.file.Files.isRegularFile(path)) {
            throw new BusinessException("File not found on disk.",
                "The stored file could not be found on disk.");
          }
          try {
            java.awt.Desktop.getDesktop().open(path.toFile());
          } catch (java.io.IOException | java.awt.HeadlessException ex) {
            throw new BusinessException("Cannot open file.",
                "No application is available to open this file type.");
          }
          return file.getFileName();
        },
        name -> {
        });
  }

  private void deleteSelectedFile() {
    SecurityService.require(Permissions.STAFF_UPDATE);
    int modelRow = filesTable.selectedModelRow();
    if (modelRow < 0) {
      Dialogs.info(this, "Delete File", "Select a file row first.");
      return;
    }
    long id = fileRowId(modelRow);
    String name = String.valueOf(filesTable.getModel().getValueAt(modelRow, 1));
    if (!Dialogs.confirm(this, "Delete File", "Delete file '" + name + "'?")) {
      return;
    }
    Long userId = SessionContext.isAuthenticated() ? SessionContext.currentUserId() : null;
    UiThread.executeAsync("Delete file",
        () -> FileRepository.softDelete(id, userId),
        deleted -> loadFiles(),
        ex -> ErrorHandler.handle(this, ex));
  }

  private void setupActions() {
    // SearchField debounces changes (300 ms) and fires immediately on Enter.
    if (textSearch instanceof SearchField searchField) {
      searchField.onTextChanged(t -> loadStaff());
    }
    comboDepartment.addActionListener(e -> {
      if (filterOptionsReady) {
        loadStaff();
      }
    });
    comboOffice.addActionListener(e -> {
      if (filterOptionsReady) {
        loadStaff();
      }
    });
    comboPosition.addActionListener(e -> {
      if (filterOptionsReady) {
        loadStaff();
      }
    });
    comboStatus.addActionListener(e -> {
      if (filterOptionsReady) {
        loadStaff();
      }
    });
    // btnNewStaff keeps its designer-generated listener; see
    // btnNewStaffActionPerformed below.
    btnDelete.addActionListener(e -> runGuarded(this::deleteSelected));
    // Files tab
    if (textFSearch instanceof SearchField fileSearch) {
      fileSearch.onTextChanged(t -> loadFiles());
    }
    btnFDelete.addActionListener(e -> runGuarded(this::deleteSelectedFile));
    if (!SecurityService.can(Permissions.STAFF_CREATE)) {
      btnNewStaff.setEnabled(false);
      btnNewStaff.setToolTipText("Requires permission: STAFF_CREATE");
    }
    if (!SecurityService.can(Permissions.STAFF_UPDATE)) {
      btnDelete.setEnabled(false);
      btnDelete.setToolTipText("Requires permission: STAFF_UPDATE");
      btnFDelete.setEnabled(false);
      btnFDelete.setToolTipText("Requires permission: STAFF_UPDATE");
    }
    rebuildStatusFilter();
    loadFilterOptions();
    loadFiles();
    // Files are uploaded from the Staff dialog, so reload whichever tab is
    // being shown instead of trusting the data loaded at construction time.
    jTabbedPane1.addChangeListener(e -> {
      java.awt.Component shown = jTabbedPane1.getSelectedComponent();
      if (shown == filePanel) {
        loadFiles();
      } else if (shown == staffPanel) {
        loadStaff();
      }
    });
  }

  /** Static status options; data-driven choices load with loadFilterOptions(). */
  private void rebuildStatusFilter() {
    comboStatus.removeAllItems();
    comboStatus.addItem("All Statuses");
    comboStatus.addItem("Active");
    comboStatus.addItem("Probation");
    comboStatus.addItem("Resigned");
  }

  private void loadFilterOptions() {
    UiThread.executeAsync("Load staff filter options",
        () -> new FilterOptions(
            DepartmentRepository.listAll(),
            OfficeRepository.listAll(),
            PositionRepository.listAll()),
        opts -> {
          departmentIds.clear();
          comboDepartment.removeAllItems();
          comboDepartment.addItem("All Departments");
          for (Department d : opts.departments()) {
            departmentIds.put(d.getName(), d.getId());
            comboDepartment.addItem(d.getName());
          }
          officeIds.clear();
          comboOffice.removeAllItems();
          comboOffice.addItem("All Offices");
          for (Office o : opts.offices()) {
            officeIds.put(o.getName(), o.getId());
            comboOffice.addItem(o.getName());
          }
          positionIds.clear();
          comboPosition.removeAllItems();
          comboPosition.addItem("All Positions");
          for (Position p : opts.positions()) {
            positionIds.put(p.getName(), p.getId());
            comboPosition.addItem(p.getName());
          }
          filterOptionsReady = true;
          loadStaff();
        },
        ex -> showLoadError(ex, this::loadFilterOptions));
  }

  private javax.swing.JPopupMenu buildContextMenu() {
    javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();
    javax.swing.JMenuItem editItem = new javax.swing.JMenuItem("Edit");
    editItem.addActionListener(e -> runGuarded(() -> {
      int row = table.selectedModelRow();
      if (row >= 0) {
        openEditStaffDialog(rowId(row));
      }
    }));
    javax.swing.JMenuItem deleteItem = new javax.swing.JMenuItem("Delete");
    deleteItem.addActionListener(e -> runGuarded(this::deleteSelected));
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

  private void openNewStaffDialog() {
    SecurityService.require(Permissions.STAFF_CREATE);
    StaffDialog dialog = new StaffDialog(ownerFrame(), true);
    dialog.onSaved(this::refreshAfterDialog);
    dialog.setLocationRelativeTo(this);
    try {
      dialog.setVisible(true);
    } finally {
      // Refresh even if closing the modal dialog misbehaves.
      refreshAfterDialog();
    }
  }

  private void openEditStaffDialog(long id) {
    SecurityService.require(Permissions.STAFF_UPDATE);
    UiThread.executeAsync("Load staff",
        () -> StaffRepository.findById(id),
        optional -> optional.ifPresent(staff -> {
          StaffDialog dialog = new StaffDialog(ownerFrame(), true, staff);
          dialog.onSaved(this::refreshAfterDialog);
          dialog.setLocationRelativeTo(this);
          try {
            dialog.setVisible(true);
          } finally {
            refreshAfterDialog();
          }
        }));
  }

  /**
   * The dialog hosts file uploads, so closing it must reload the Files tab too,
   * not just the staff list.
   */
  private void refreshAfterDialog() {
    loadStaff();
    loadFiles();
  }

  private void deleteSelected() {
    SecurityService.require(Permissions.STAFF_UPDATE);
    int modelRow = table.selectedModelRow();
    if (modelRow < 0) {
      Dialogs.info(this, "Delete Staff", "Select a staff row first.");
      return;
    }
    long id = rowId(modelRow);
    String name = String.valueOf(table.getModel().getValueAt(modelRow, 2));
    if (!Dialogs.confirm(this, "Delete Staff",
        "Delete staff '" + name + "'? This is a soft delete.")) {
      return;
    }
    Long userId = SessionContext.isAuthenticated() ? SessionContext.currentUserId() : null;
    UiThread.executeAsync("Delete staff",
        () -> StaffRepository.softDelete(id, userId),
        deleted -> loadStaff(),
        ex -> {
          if (ex instanceof BusinessException) {
            Dialogs.info(this, "Delete Staff", ex.getMessage());
          } else {
            ErrorHandler.handle(this, ex);
          }
        });
  }

  private Long selectedId(Map<String, Long> ids, javax.swing.JComboBox<String> combo) {
    String selected = combo.getSelectedItem() == null
        ? "" : combo.getSelectedItem().toString();
    if (selected.startsWith("All ")) {
      return null;
    }
    return ids.get(selected);
  }

  private String selectedStatus() {
    String selected = comboStatus.getSelectedItem() == null
        ? "" : comboStatus.getSelectedItem().toString();
    return selected.startsWith("All ") ? null : selected;
  }

  private void loadStaff() {
    String keyword = textSearch.getText();
    Long departmentId = selectedId(departmentIds, comboDepartment);
    Long officeId = selectedId(officeIds, comboOffice);
    Long positionId = selectedId(positionIds, comboPosition);
    String status = selectedStatus();
    UiThread.executeAsync("Load staff",
        () -> StaffRepository.searchAsTableRows(keyword, departmentId, officeId,
            positionId, status),
        rows -> {
          // Restore the table in case a previous load failed and showed ErrorPanel.
          jScrollPane1.setViewportView(table);
          table.setRows(rows);
          LOG.info("Staff list refreshed: {} rows (keyword='{}')", rows.size(), keyword);
        },
        ex -> showLoadError(ex, this::loadStaff));
  }

  /**
   * Swaps the table area for an inline error state with retry (details go to the
   * log).
   */
  private void showLoadError(Exception ex, Runnable retry) {
    LOG.warn("Cannot load staff list", ex);
    jScrollPane1.setViewportView(new ErrorPanel(
        "Cannot load the staff list. Check that the database is running, then try again.",
        retry));
  }

  public void refreshTable() {
    loadStaff();
    loadFiles();
  }

  /**
   * This method is called from within the constructor to initialize the form.
   * WARNING: Do NOT modify this code. The content of this method is always
   * regenerated by the Form Editor.
   */
  @SuppressWarnings("unchecked")
  // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
  private void initComponents() {

    jTabbedPane1 = new javax.swing.JTabbedPane();
    staffPanel = new javax.swing.JPanel();
    sheader = new javax.swing.JPanel();
    btnNewStaff = new javax.swing.JButton();
    btnDelete = new javax.swing.JButton();
    textSearch =  new SearchField("Search by name/code/phone...");
    comboDepartment = new javax.swing.JComboBox<>();
    comboOffice = new javax.swing.JComboBox<>();
    comboPosition = new javax.swing.JComboBox<>();
    comboStatus = new javax.swing.JComboBox<>();
    sFooter = new javax.swing.JPanel();
    sBody = new javax.swing.JPanel();
    jScrollPane1 = new javax.swing.JScrollPane();
    tableStaff = new javax.swing.JTable();
    filePanel = new javax.swing.JPanel();
    fHeader = new javax.swing.JPanel();
    textFSearch =  new SearchField("Search by name...");
    btnFDelete = new javax.swing.JButton();
    fFooter = new javax.swing.JPanel();
    fBody = new javax.swing.JPanel();
    jScrollPane2 = new javax.swing.JScrollPane();
    tableFile = new javax.swing.JTable();

    staffPanel.setLayout(new java.awt.BorderLayout());

    sheader.setPreferredSize(new java.awt.Dimension(1237, 80));

    btnNewStaff.setText("+ New Staff");
    btnNewStaff.addActionListener(this::btnNewStaffActionPerformed);

    btnDelete.setText("Delete");

    comboDepartment.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Department" }));

    comboOffice.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Office" }));

    comboPosition.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Position" }));

    comboStatus.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Status" }));

    javax.swing.GroupLayout sheaderLayout = new javax.swing.GroupLayout(sheader);
    sheader.setLayout(sheaderLayout);
    sheaderLayout.setHorizontalGroup(
      sheaderLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGroup(sheaderLayout.createSequentialGroup()
        .addGap(20, 20, 20)
        .addGroup(sheaderLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
          .addGroup(sheaderLayout.createSequentialGroup()
            .addComponent(textSearch, javax.swing.GroupLayout.PREFERRED_SIZE, 220, javax.swing.GroupLayout.PREFERRED_SIZE)
            .addGap(20, 20, 20)
            .addComponent(comboDepartment, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
          .addComponent(comboOffice, javax.swing.GroupLayout.PREFERRED_SIZE, 101, javax.swing.GroupLayout.PREFERRED_SIZE))
        .addGap(18, 18, 18)
        .addGroup(sheaderLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
          .addComponent(comboStatus, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
          .addComponent(comboPosition, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 481, Short.MAX_VALUE)
        .addComponent(btnDelete)
        .addGap(40, 40, 40)
        .addComponent(btnNewStaff, javax.swing.GroupLayout.PREFERRED_SIZE, 120, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(60, 60, 60))
    );
    sheaderLayout.setVerticalGroup(
      sheaderLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, sheaderLayout.createSequentialGroup()
        .addGap(13, 13, 13)
        .addGroup(sheaderLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
          .addComponent(btnNewStaff)
          .addComponent(btnDelete)
          .addComponent(textSearch, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
          .addComponent(comboDepartment, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
          .addComponent(comboPosition, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
        .addGap(4, 4, 4)
        .addGroup(sheaderLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
          .addComponent(comboOffice, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
          .addComponent(comboStatus, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
        .addContainerGap())
    );

    staffPanel.add(sheader, java.awt.BorderLayout.PAGE_START);

    sFooter.setPreferredSize(new java.awt.Dimension(1237, 40));
    sFooter.setRequestFocusEnabled(false);

    javax.swing.GroupLayout sFooterLayout = new javax.swing.GroupLayout(sFooter);
    sFooter.setLayout(sFooterLayout);
    sFooterLayout.setHorizontalGroup(
      sFooterLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGap(0, 1237, Short.MAX_VALUE)
    );
    sFooterLayout.setVerticalGroup(
      sFooterLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGap(0, 40, Short.MAX_VALUE)
    );

    staffPanel.add(sFooter, java.awt.BorderLayout.PAGE_END);

    tableStaff.setModel(new javax.swing.table.DefaultTableModel(
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
    jScrollPane1.setViewportView(tableStaff);

    javax.swing.GroupLayout sBodyLayout = new javax.swing.GroupLayout(sBody);
    sBody.setLayout(sBodyLayout);
    sBodyLayout.setHorizontalGroup(
      sBodyLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addComponent(jScrollPane1, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 1237, Short.MAX_VALUE)
    );
    sBodyLayout.setVerticalGroup(
      sBodyLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 685, Short.MAX_VALUE)
    );

    staffPanel.add(sBody, java.awt.BorderLayout.CENTER);

    jTabbedPane1.addTab("Staff", staffPanel);

    filePanel.setLayout(new java.awt.BorderLayout());

    fHeader.setPreferredSize(new java.awt.Dimension(1237, 60));

    btnFDelete.setText("Delete");

    javax.swing.GroupLayout fHeaderLayout = new javax.swing.GroupLayout(fHeader);
    fHeader.setLayout(fHeaderLayout);
    fHeaderLayout.setHorizontalGroup(
      fHeaderLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, fHeaderLayout.createSequentialGroup()
        .addGap(60, 60, 60)
        .addComponent(textFSearch, javax.swing.GroupLayout.PREFERRED_SIZE, 220, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 821, Short.MAX_VALUE)
        .addComponent(btnFDelete)
        .addGap(60, 60, 60))
    );
    fHeaderLayout.setVerticalGroup(
      fHeaderLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGroup(fHeaderLayout.createSequentialGroup()
        .addGap(17, 17, 17)
        .addGroup(fHeaderLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
          .addComponent(textFSearch, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
          .addComponent(btnFDelete))
        .addContainerGap(16, Short.MAX_VALUE))
    );

    filePanel.add(fHeader, java.awt.BorderLayout.PAGE_START);

    fFooter.setPreferredSize(new java.awt.Dimension(1237, 40));

    javax.swing.GroupLayout fFooterLayout = new javax.swing.GroupLayout(fFooter);
    fFooter.setLayout(fFooterLayout);
    fFooterLayout.setHorizontalGroup(
      fFooterLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGap(0, 1237, Short.MAX_VALUE)
    );
    fFooterLayout.setVerticalGroup(
      fFooterLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGap(0, 40, Short.MAX_VALUE)
    );

    filePanel.add(fFooter, java.awt.BorderLayout.PAGE_END);

    tableFile.setModel(new javax.swing.table.DefaultTableModel(
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
    jScrollPane2.setViewportView(tableFile);

    javax.swing.GroupLayout fBodyLayout = new javax.swing.GroupLayout(fBody);
    fBody.setLayout(fBodyLayout);
    fBodyLayout.setHorizontalGroup(
      fBodyLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addComponent(jScrollPane2, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 1237, Short.MAX_VALUE)
    );
    fBodyLayout.setVerticalGroup(
      fBodyLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 705, Short.MAX_VALUE)
    );

    filePanel.add(fBody, java.awt.BorderLayout.CENTER);

    jTabbedPane1.addTab("Files", filePanel);

    javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
    this.setLayout(layout);
    layout.setHorizontalGroup(
      layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addComponent(jTabbedPane1)
    );
    layout.setVerticalGroup(
      layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addComponent(jTabbedPane1)
    );
  }// </editor-fold>//GEN-END:initComponents

  private void btnNewStaffActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnNewStaffActionPerformed
    runGuarded(this::openNewStaffDialog);
  }//GEN-LAST:event_btnNewStaffActionPerformed


  // Variables declaration - do not modify//GEN-BEGIN:variables
  private javax.swing.JButton btnDelete;
  private javax.swing.JButton btnFDelete;
  private javax.swing.JButton btnNewStaff;
  private javax.swing.JComboBox<String> comboDepartment;
  private javax.swing.JComboBox<String> comboOffice;
  private javax.swing.JComboBox<String> comboPosition;
  private javax.swing.JComboBox<String> comboStatus;
  private javax.swing.JPanel fBody;
  private javax.swing.JPanel fFooter;
  private javax.swing.JPanel fHeader;
  private javax.swing.JPanel filePanel;
  private javax.swing.JScrollPane jScrollPane1;
  private javax.swing.JScrollPane jScrollPane2;
  private javax.swing.JTabbedPane jTabbedPane1;
  private javax.swing.JPanel sBody;
  private javax.swing.JPanel sFooter;
  private javax.swing.JPanel sheader;
  private javax.swing.JPanel staffPanel;
  private javax.swing.JTable tableFile;
  private javax.swing.JTable tableStaff;
  private javax.swing.JTextField textFSearch;
  private javax.swing.JTextField textSearch;
  // End of variables declaration//GEN-END:variables
}
