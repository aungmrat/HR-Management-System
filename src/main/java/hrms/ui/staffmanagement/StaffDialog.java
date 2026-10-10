/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/GUIForms/JDialog.java to edit this template
 */
package hrms.ui.staffmanagement;

import hrms.component.ErrorPanel;
import hrms.component.HrmsTable;
import hrms.exception.AuthorizationException;
import hrms.exception.BusinessException;
import hrms.exception.ErrorHandler;
import hrms.exception.ValidationException;
import hrms.model.Department;
import hrms.model.Office;
import hrms.model.Position;
import hrms.model.Staff;
import hrms.model.StoredFile;
import hrms.model.Team;
import hrms.repository.DepartmentRepository;
import hrms.repository.FileRepository;
import hrms.repository.OfficeRepository;
import hrms.repository.PositionRepository;
import hrms.repository.StaffRepository;
import hrms.repository.TeamRepository;
import hrms.security.Permissions;
import hrms.security.SecurityService;
import hrms.security.SessionContext;
import hrms.util.Dialogs;
import hrms.util.FileStorage;
import hrms.util.UiThread;

/**
 *
 * @author aungmrat
 */
public class StaffDialog extends javax.swing.JDialog {
  
  private static final java.util.logging.Logger logger = java.util.logging.Logger.getLogger(StaffDialog.class.getName());

  /** null = create mode. */
  private final hrms.model.Staff editing;

  /** Guards combo events fired while the FK options are still loading. */
  private boolean combosReady;

  /** Combo display name to id; maps are rebuilt whenever their combo is refilled. */
  private final java.util.Map<String, Long> departmentIds = new java.util.HashMap<>();
  private final java.util.Map<String, Long> positionIds = new java.util.HashMap<>();
  private final java.util.Map<String, Long> teamIds = new java.util.HashMap<>();
  private final java.util.Map<String, Long> officeIds = new java.util.HashMap<>();

  /**
   * Position options kept client-side so the department cascade in
   * comboDepartmentActionPerformed stays synchronous.
   */
  private record PositionOption(long id, String name, Long departmentId) {
  }

  private java.util.List<PositionOption> positionOptions = java.util.List.of();

  /**
   * Documents table lives outside the GEN block so Form Designer saves cannot
   * wipe it.
   */
  private HrmsTable docsTable;

  /**
   * Refresh hook for the owning list: fired the moment a save commits, before
   * the dialog closes, so the list never depends on the modal-close ordering.
   */
  private Runnable onSaved;

  private static final org.slf4j.Logger LOG =
          org.slf4j.LoggerFactory.getLogger(StaffDialog.class);

  /** FK options loaded together off the EDT. */
  private record ComboData(java.util.List<Department> departments,
          java.util.List<Position> positions,
          java.util.List<Team> teams,
          java.util.List<Office> offices) {
  }

  /**
   * Creates new form StaffDialog
   */
  public StaffDialog(java.awt.Frame parent, boolean modal) {
    this(parent, modal, null);
  }

  /** Create when {@code existing} is null; edit when non-null. */
  public StaffDialog(java.awt.Frame parent, boolean modal, hrms.model.Staff existing) {
    super(parent, modal);
    initComponents();
    this.editing = existing;
    setTitle(existing == null ? "New Staff" : "Edit Staff");
    fillStaticCombos();
    if (existing != null) {
      prefill(existing);
    }
    setupActions();
    setupDocuments();
    loadCombos();
  }

  /** Called as soon as a save commits; typically {@code listPanel::loadStaff}. */
  public void onSaved(Runnable action) {
    this.onSaved = action;
  }

  /** Never lets a refresh failure keep the dialog from closing. */
  private void notifySaved() {
    if (onSaved == null) {
      return;
    }
    try {
      onSaved.run();
    } catch (RuntimeException ex) {
      LOG.warn("Post-save list refresh failed", ex);
    }
  }

  /** Apply stored values first; FK combo selections are applied after loadCombos(). */
  private void prefill(hrms.model.Staff staff) {
    textCode.setText(nullToEmpty(staff.getCode()));
    textName.setText(nullToEmpty(staff.getName()));
    setDate(dcDob, staff.getDob());
    setDate(dcApplicationDate, staff.getApplicationDate());
    setDate(dcCommencementDate, staff.getCommencementDate());
    comboSex.setSelectedItem(staff.getSex());
    comboBlood.setSelectedItem(staff.getBlood());
    comboMaritalStatus.setSelectedItem(staff.getMaritalStatus());
    comboHealthStatus.setSelectedItem(staff.getHealthStatus());
    comboDrivingLicense.setSelectedItem(staff.isHasDrivingLicence() ? "Yes" : "No");
    comboContractType.setSelectedItem(staff.getContractType());
    comboStatus.setSelectedItem(staff.getStatus());
    textContactNo.setText(nullToEmpty(staff.getContactNo()));
    textEmail.setText(nullToEmpty(staff.getEmail()));
    textNID.setText(nullToEmpty(staff.getNid()));
    textNextOfKin.setText(nullToEmpty(staff.getNextOfKin()));
    textEmergencyContact.setText(nullToEmpty(staff.getEmergencyContact()));
    textEducationLevel.setText(nullToEmpty(staff.getEducationLevel()));
    textOtherSkills.setText(nullToEmpty(staff.getOtherSkills()));
    textHobby.setText(nullToEmpty(staff.getHobby()));
    jTextArea1.setText(nullToEmpty(staff.getAddress()));
    textSalary.setText(staff.getSalary() == null ? "" : staff.getSalary().toPlainString());
  }

  /** Static option lists; FK combos load from the database in loadCombos(). */
  private void fillStaticCombos() {
    fillItems(comboSex, "Select Sex", "Male", "Female");
    fillItems(comboBlood, "Select Blood Group",
        "A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-");
    fillItems(comboMaritalStatus, "Select Marital Status",
        "Single", "Married", "Divorced", "Widowed");
    fillItems(comboHealthStatus, "Select Health Status", "Good", "Fair", "Poor");
    fillItems(comboDrivingLicense, "Yes", "No");
    comboDrivingLicense.setSelectedItem("No");
    fillItems(comboStatus, "Active", "Probation", "Resigned");
    fillItems(comboContractType, "Select Contract Type",
        "Permanent", "Contract", "Part-Time", "Intern");
  }

  private static void fillItems(javax.swing.JComboBox<String> combo, String... items) {
    combo.removeAllItems();
    for (String item : items) {
      combo.addItem(item);
    }
  }

  private void setupActions() {
    btnCancel.addActionListener(e -> dispose());
    btnSave.addActionListener(e -> {
      try {
        saveAndClose();
      } catch (AuthorizationException ex) {
        ErrorHandler.handle(this, ex);
      }
    });
  }

  /** Permission gate for Save; static so headless tests can verify it. */
  static void requireSavePermission(boolean isEdit) {
    SecurityService.require(isEdit
            ? Permissions.STAFF_UPDATE : Permissions.STAFF_CREATE);
  }

  /** Documents table + upload wiring; the table lives outside the GEN block. */
  private void setupDocuments() {
    docsTable = HrmsTable.builder(
            "ID", "Name", "Type", "Source", "Uploaded By", "Uploaded")
        .hiddenColumn(0)
        .fixedColumn(1, 240)
        .fixedColumn(2, 60)
        .fixedColumn(3, 160)
        .fixedColumn(4, 140)
        .onDoubleClick((viewRow, modelRow) -> runGuarded(() -> openDocument(docRowId(modelRow))))
        .contextMenu(this::buildDocumentsContextMenu)
        .build();
    jScrollPane2.setViewportView(docsTable);
    btnUpload.addActionListener(e -> runGuarded(this::uploadDocument));
    if (editing == null) {
      btnUpload.setEnabled(false);
      btnUpload.setToolTipText("Save the staff record first, then upload documents.");
    } else {
      loadDocuments();
    }
  }

  /** Permission failures surface as a dialog instead of an uncaught exception. */
  private void runGuarded(Runnable action) {
    try {
      action.run();
    } catch (AuthorizationException ex) {
      ErrorHandler.handle(this, ex);
    }
  }

  private long docRowId(int modelRow) {
    return ((Number) docsTable.getModel().getValueAt(modelRow, 0)).longValue();
  }

  private javax.swing.JPopupMenu buildDocumentsContextMenu() {
    javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();
    javax.swing.JMenuItem openItem = new javax.swing.JMenuItem("Open");
    openItem.addActionListener(e -> runGuarded(() -> {
      int row = docsTable.selectedModelRow();
      if (row >= 0) {
        openDocument(docRowId(row));
      }
    }));
    javax.swing.JMenuItem deleteItem = new javax.swing.JMenuItem("Delete");
    deleteItem.addActionListener(e -> runGuarded(this::deleteSelectedDocument));
    menu.add(openItem);
    menu.add(deleteItem);
    return menu;
  }

  private void loadDocuments() {
    if (editing == null) {
      return;
    }
    UiThread.executeAsync("Load staff documents",
        () -> FileRepository.listBySource(editing.getId(), "STAFF"),
        files -> {
          // Restore the table in case a previous load failed and showed ErrorPanel.
          jScrollPane2.setViewportView(docsTable);
          java.util.List<Object[]> rows = new java.util.ArrayList<>();
          for (StoredFile file : files) {
            rows.add(file.toTableRow());
          }
          docsTable.setRows(rows);
        },
        ex -> {
          logger.log(java.util.logging.Level.WARNING, "Cannot load document list", ex);
          jScrollPane2.setViewportView(new ErrorPanel(
              "Cannot load the document list. Check that the database is running, then try again.",
              this::loadDocuments));
        });
  }

  private void openDocument(long fileId) {
    UiThread.executeAsync("Open document",
        () -> {
          StoredFile file = FileRepository.findById(fileId)
              .orElseThrow(() -> new BusinessException(
                  "File not found.", "This document no longer exists."));
          java.nio.file.Path path = FileStorage.resolve(file.getFilePath());
          if (path == null || !java.nio.file.Files.isRegularFile(path)) {
            throw new BusinessException("File not found on disk.",
                "The stored document could not be found on disk.");
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

  private void deleteSelectedDocument() {
    SecurityService.require(Permissions.STAFF_UPDATE);
    int modelRow = docsTable.selectedModelRow();
    if (modelRow < 0) {
      Dialogs.info(this, "Delete Document", "Select a document row first.");
      return;
    }
    long id = docRowId(modelRow);
    String name = String.valueOf(docsTable.getModel().getValueAt(modelRow, 1));
    if (!Dialogs.confirm(this, "Delete Document", "Delete document '" + name + "'?")) {
      return;
    }
    Long userId = SessionContext.isAuthenticated() ? SessionContext.currentUserId() : null;
    UiThread.executeAsync("Delete document",
        () -> FileRepository.softDelete(id, userId),
        deleted -> loadDocuments(),
        ex -> ErrorHandler.handle(this, ex));
  }

  private void uploadDocument() {
    SecurityService.require(Permissions.STAFF_UPDATE);
    if (editing == null) {
      Dialogs.info(this, "Upload Document", "Save the staff record first.");
      return;
    }
    javax.swing.JFileChooser chooser = new javax.swing.JFileChooser();
    chooser.setDialogTitle("Upload Document");
    if (chooser.showOpenDialog(this) != javax.swing.JFileChooser.APPROVE_OPTION) {
      return;
    }
    java.nio.file.Path source = chooser.getSelectedFile().toPath();
    String fileName = source.getFileName().toString();
    Long userId = SessionContext.isAuthenticated() ? SessionContext.currentUserId() : null;
    btnUpload.setEnabled(false);
    UiThread.executeAsync("Upload document",
        () -> {
          String storedPath = FileStorage.storeDocument(source, editing.getId());
          StoredFile file = StoredFile.of(editing.getId(), "STAFF", fileName,
              storedPath, extensionOf(fileName));
          file.setCreatedBy(userId);
          return FileRepository.insert(file);
        },
        id -> {
          btnUpload.setEnabled(true);
          loadDocuments();
        },
        ex -> {
          btnUpload.setEnabled(true);
          ErrorHandler.handle(this, ex);
        });
  }

  private static String extensionOf(String fileName) {
    int dot = fileName.lastIndexOf('.');
    if (dot < 0) {
      return "";
    }
    String extension = fileName.substring(dot + 1).toLowerCase(java.util.Locale.ROOT);
    return extension.length() > 10 ? extension.substring(0, 10) : extension;
  }

  /** FK combos are loaded from the database — never hardcode the items. */
  private void loadCombos() {
    UiThread.executeAsync("Load staff combos",
        () -> new ComboData(
            DepartmentRepository.listAll(),
            PositionRepository.listAll(),
            TeamRepository.listAll(),
            OfficeRepository.listAll()),
        this::fillCombos);
  }

  private void fillCombos(ComboData data) {
    departmentIds.clear();
    comboDepartment.removeAllItems();
    comboDepartment.addItem("Select Department");
    for (Department department : data.departments()) {
      departmentIds.put(department.getName(), department.getId());
      comboDepartment.addItem(department.getName());
    }

    positionOptions = new java.util.ArrayList<>();
    for (Position position : data.positions()) {
      positionOptions.add(new PositionOption(position.getId(), position.getName(),
          position.getDepartmentId()));
    }

    teamIds.clear();
    comboTeam.removeAllItems();
    comboTeam.addItem("Select Team");
    for (Team team : data.teams()) {
      teamIds.put(team.getName(), team.getId());
      comboTeam.addItem(team.getName());
    }

    officeIds.clear();
    comboOffice.removeAllItems();
    comboOffice.addItem("Select Office");
    for (Office office : data.offices()) {
      officeIds.put(office.getName(), office.getId());
      comboOffice.addItem(office.getName());
    }

    if (editing == null) {
      refillPositions(null);
      combosReady = true;
      return;
    }
    selectById(departmentIds, comboDepartment, editing.getDepartmentId());
    refillPositions(editing.getDepartmentId());
    selectById(positionIds, comboPosition, editing.getPositionId());
    selectById(teamIds, comboTeam, editing.getTeamId());
    selectById(officeIds, comboOffice, editing.getOfficeId());
    combosReady = true;
  }

  /** Department cascade: only that department's positions (all when null). */
  private void refillPositions(Long departmentId) {
    String previous = comboPosition.getSelectedItem() == null
        ? null : comboPosition.getSelectedItem().toString();
    positionIds.clear();
    comboPosition.removeAllItems();
    comboPosition.addItem("Select Position");
    for (PositionOption option : positionOptions) {
      if (departmentId == null || departmentId.equals(option.departmentId())) {
        positionIds.put(option.name(), option.id());
        comboPosition.addItem(option.name());
      }
    }
    if (previous != null && positionIds.containsKey(previous)) {
      comboPosition.setSelectedItem(previous);
    }
  }

  private void saveAndClose() {
    try {
      requireSavePermission(editing != null);
      String code = textCode.getText().trim();
      String name = textName.getText().trim();
      java.time.LocalDate dob = toLocalDate(dcDob.getDate());
      String sex = selectedOrPlaceholder(comboSex);
      String contactNo = textContactNo.getText().trim();
      Long departmentId = selectedId(departmentIds, comboDepartment);
      Long positionId = selectedId(positionIds, comboPosition);
      Long teamId = selectedId(teamIds, comboTeam);
      Long officeId = selectedId(officeIds, comboOffice);
      String salaryText = textSalary.getText().trim();

      java.util.List<String> errors = new java.util.ArrayList<>();
      if (code.isEmpty()) {
        errors.add("Staff code is required.");
      } else if (code.length() > 10) {
        errors.add("Staff code must be 10 characters or fewer.");
      }
      if (name.isEmpty()) {
        errors.add("Staff name is required.");
      } else if (name.length() > 100) {
        errors.add("Staff name must be 100 characters or fewer.");
      }
      if (dob == null) {
        errors.add("Date of birth is required.");
      }
      if (sex == null) {
        errors.add("Sex is required.");
      }
      if (contactNo.isEmpty()) {
        errors.add("Contact number is required.");
      } else if (contactNo.length() > 20) {
        errors.add("Contact number must be 20 characters or fewer.");
      }
      if (departmentId == null) {
        errors.add("Department is required.");
      }
      if (positionId == null) {
        errors.add("Position is required.");
      }
      if (teamId == null) {
        errors.add("Team is required.");
      }
      if (officeId == null) {
        errors.add("Office is required.");
      }
      java.math.BigDecimal salary = null;
      if (salaryText.isEmpty()) {
        errors.add("Salary is required.");
      } else {
        try {
          salary = new java.math.BigDecimal(salaryText);
          if (salary.signum() < 0) {
            errors.add("Salary cannot be negative.");
          }
        } catch (NumberFormatException e) {
          errors.add("Salary must be a number.");
        }
      }
      if (!errors.isEmpty()) {
        throw new ValidationException(errors);
      }

      boolean isEdit = editing != null;
      hrms.model.Staff staff = isEdit ? editing : new hrms.model.Staff();
      staff.setCode(code);
      staff.setName(name);
      staff.setDob(dob);
      staff.setSex(sex);
      staff.setBlood(selectedOrPlaceholder(comboBlood));
      staff.setContactNo(contactNo);
      staff.setDepartmentId(departmentId);
      staff.setEmail(trimToNull(textEmail.getText()));
      staff.setAddress(trimToNull(jTextArea1.getText()));
      staff.setPositionId(positionId);
      staff.setTeamId(teamId);
      staff.setOfficeId(officeId);
      staff.setSalary(salary);
      staff.setApplicationDate(toLocalDate(dcApplicationDate.getDate()));
      staff.setCommencementDate(toLocalDate(dcCommencementDate.getDate()));
      staff.setContractType(selectedOrPlaceholder(comboContractType));
      staff.setMaritalStatus(selectedOrPlaceholder(comboMaritalStatus));
      staff.setNextOfKin(trimToNull(textNextOfKin.getText()));
      staff.setEmergencyContact(trimToNull(textEmergencyContact.getText()));
      staff.setHealthStatus(selectedOrPlaceholder(comboHealthStatus));
      staff.setStatus(comboStatus.getSelectedItem() == null
          ? "Active" : comboStatus.getSelectedItem().toString().trim());
      staff.setNid(trimToNull(textNID.getText()));
      staff.setEducationLevel(trimToNull(textEducationLevel.getText()));
      staff.setOtherSkills(trimToNull(textOtherSkills.getText()));
      staff.setHobby(trimToNull(textHobby.getText()));
      staff.setHasDrivingLicence("Yes".equals(comboDrivingLicense.getSelectedItem()));

      Long userId = SessionContext.isAuthenticated() ? SessionContext.currentUserId() : null;
      if (isEdit) {
        staff.setModifiedBy(userId);
      } else {
        staff.setCreatedBy(userId);
      }

      btnSave.setEnabled(false);
      UiThread.executeAsync(isEdit ? "Update staff" : "Save staff",
          () -> isEdit ? StaffRepository.update(staff) : StaffRepository.insert(staff),
          result -> {
            if (isEdit) {
              LOG.info("Staff updated id={}", staff.getId());
            } else {
              LOG.info("Staff saved with id {}", result);
            }
            notifySaved();
            dispose();
          },
          error -> {
            btnSave.setEnabled(true);
            ErrorHandler.handle(this, error);
          });
    } catch (ValidationException e) {
      ErrorHandler.handle(this, e);
    }
  }

  private static String trimToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }

  private static java.time.LocalDate toLocalDate(java.util.Date date) {
    return date == null ? null : new java.sql.Date(date.getTime()).toLocalDate();
  }

  private static void setDate(com.toedter.calendar.JDateChooser chooser,
          java.time.LocalDate value) {
    chooser.setDate(value == null ? null : java.sql.Date.valueOf(value));
  }

  /** Placeholder items ("Select ...") mean "no value" for optional fields. */
  private static String selectedOrPlaceholder(javax.swing.JComboBox<String> combo) {
    Object item = combo.getSelectedItem();
    if (item == null) {
      return null;
    }
    String text = item.toString().trim();
    if (text.isEmpty() || text.startsWith("Select")) {
      return null;
    }
    return text;
  }

  private static Long selectedId(java.util.Map<String, Long> ids,
          javax.swing.JComboBox<String> combo) {
    Object item = combo.getSelectedItem();
    if (item == null) {
      return null;
    }
    return ids.get(item.toString());
  }

  private static void selectById(java.util.Map<String, Long> ids,
          javax.swing.JComboBox<String> combo, Long value) {
    if (value == null) {
      return;
    }
    for (java.util.Map.Entry<String, Long> entry : ids.entrySet()) {
      if (value.equals(entry.getValue())) {
        combo.setSelectedItem(entry.getKey());
        return;
      }
    }
  }

  /**
   * This method is called from within the constructor to initialize the form.
   * WARNING: Do NOT modify this code. The content of this method is always
   * regenerated by the Form Editor.
   */
  @SuppressWarnings("unchecked")
  // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
  private void initComponents() {
    java.awt.GridBagConstraints gridBagConstraints;

    footer = new javax.swing.JPanel();
    btnSave = new javax.swing.JButton();
    btnCancel = new javax.swing.JButton();
    body = new javax.swing.JPanel();
    jTabbedPane1 = new javax.swing.JTabbedPane();
    personal = new javax.swing.JPanel();
    labelCode = new javax.swing.JLabel();
    textCode = new javax.swing.JTextField();
    labelName = new javax.swing.JLabel();
    textName = new javax.swing.JTextField();
    labelDob = new javax.swing.JLabel();
    dcDob = new com.toedter.calendar.JDateChooser();
    labelSex = new javax.swing.JLabel();
    comboSex = new javax.swing.JComboBox<>();
    labelBlood = new javax.swing.JLabel();
    comboBlood = new javax.swing.JComboBox<>();
    labelNID = new javax.swing.JLabel();
    textNID = new javax.swing.JTextField();
    labelContactNo = new javax.swing.JLabel();
    textContactNo = new javax.swing.JTextField();
    labelEmail = new javax.swing.JLabel();
    textEmail = new javax.swing.JTextField();
    labelMaritalStatus = new javax.swing.JLabel();
    comboMaritalStatus = new javax.swing.JComboBox<>();
    labelNextOfKin = new javax.swing.JLabel();
    textNextOfKin = new javax.swing.JTextField();
    labelEmergencyContact = new javax.swing.JLabel();
    textEmergencyContact = new javax.swing.JTextField();
    labelHealthStatus = new javax.swing.JLabel();
    comboHealthStatus = new javax.swing.JComboBox<>();
    labelEducationLevel = new javax.swing.JLabel();
    textEducationLevel = new javax.swing.JTextField();
    labelOtherSkills = new javax.swing.JLabel();
    textOtherSkills = new javax.swing.JTextField();
    labelHobby = new javax.swing.JLabel();
    textHobby = new javax.swing.JTextField();
    labelHasDrivingLicense = new javax.swing.JLabel();
    comboDrivingLicense = new javax.swing.JComboBox<>();
    jLabel1 = new javax.swing.JLabel();
    jScrollPane1 = new javax.swing.JScrollPane();
    jTextArea1 = new javax.swing.JTextArea();
    employment = new javax.swing.JPanel();
    labelDepartment = new javax.swing.JLabel();
    comboDepartment = new javax.swing.JComboBox<>();
    labelPosition = new javax.swing.JLabel();
    comboPosition = new javax.swing.JComboBox<>();
    labelTeam = new javax.swing.JLabel();
    comboTeam = new javax.swing.JComboBox<>();
    labelOffice = new javax.swing.JLabel();
    comboOffice = new javax.swing.JComboBox<>();
    labelStatus = new javax.swing.JLabel();
    comboStatus = new javax.swing.JComboBox<>();
    labelCommencementDate = new javax.swing.JLabel();
    dcCommencementDate = new com.toedter.calendar.JDateChooser();
    labelContractType = new javax.swing.JLabel();
    comboContractType = new javax.swing.JComboBox<>();
    labelApplicationDate = new javax.swing.JLabel();
    dcApplicationDate = new com.toedter.calendar.JDateChooser();
    labelSalary = new javax.swing.JLabel();
    textSalary = new javax.swing.JTextField();
    documents = new javax.swing.JPanel();
    dhead = new javax.swing.JPanel();
    labelUploadFile = new javax.swing.JLabel();
    btnUpload = new javax.swing.JButton();
    dbody = new javax.swing.JPanel();
    jScrollPane2 = new javax.swing.JScrollPane();
    tableDocument = new javax.swing.JTable();

    setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);

    footer.setPreferredSize(new java.awt.Dimension(700, 60));

    btnSave.setText("Save");

    btnCancel.setText("Cancel");

    javax.swing.GroupLayout footerLayout = new javax.swing.GroupLayout(footer);
    footer.setLayout(footerLayout);
    footerLayout.setHorizontalGroup(
      footerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, footerLayout.createSequentialGroup()
        .addContainerGap(465, Short.MAX_VALUE)
        .addComponent(btnCancel)
        .addGap(40, 40, 40)
        .addComponent(btnSave)
        .addGap(40, 40, 40))
    );
    footerLayout.setVerticalGroup(
      footerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, footerLayout.createSequentialGroup()
        .addContainerGap(18, Short.MAX_VALUE)
        .addGroup(footerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
          .addComponent(btnSave)
          .addComponent(btnCancel))
        .addGap(15, 15, 15))
    );

    getContentPane().add(footer, java.awt.BorderLayout.PAGE_END);

    personal.setLayout(new java.awt.GridBagLayout());

    labelCode.setText("Code");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.weightx = 0.1;
    gridBagConstraints.insets = new java.awt.Insets(0, 18, 2, 4);
    personal.add(labelCode, gridBagConstraints);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.weightx = 0.1;
    gridBagConstraints.insets = new java.awt.Insets(0, 18, 4, 4);
    personal.add(textCode, gridBagConstraints);

    labelName.setText("Name");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.weightx = 0.1;
    gridBagConstraints.insets = new java.awt.Insets(0, 5, 2, 4);
    personal.add(labelName, gridBagConstraints);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.weightx = 0.1;
    gridBagConstraints.insets = new java.awt.Insets(0, 5, 4, 4);
    personal.add(textName, gridBagConstraints);

    labelDob.setText("DOB");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.weightx = 0.1;
    gridBagConstraints.insets = new java.awt.Insets(0, 5, 2, 21);
    personal.add(labelDob, gridBagConstraints);

    dcDob.setPreferredSize(new java.awt.Dimension(200, 26));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 2;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.weightx = 0.1;
    gridBagConstraints.insets = new java.awt.Insets(0, 5, 4, 21);
    personal.add(dcDob, gridBagConstraints);

    labelSex.setText("Sex");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.weightx = 0.1;
    gridBagConstraints.insets = new java.awt.Insets(0, 18, 2, 4);
    personal.add(labelSex, gridBagConstraints);

    comboSex.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.weightx = 0.1;
    gridBagConstraints.insets = new java.awt.Insets(0, 18, 4, 4);
    personal.add(comboSex, gridBagConstraints);

    labelBlood.setText("Blood");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.weightx = 0.1;
    gridBagConstraints.insets = new java.awt.Insets(0, 5, 2, 4);
    personal.add(labelBlood, gridBagConstraints);

    comboBlood.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.weightx = 0.1;
    gridBagConstraints.insets = new java.awt.Insets(0, 5, 4, 4);
    personal.add(comboBlood, gridBagConstraints);

    labelNID.setText("NID");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 2;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.weightx = 0.1;
    gridBagConstraints.insets = new java.awt.Insets(0, 5, 2, 21);
    personal.add(labelNID, gridBagConstraints);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 2;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.weightx = 0.1;
    gridBagConstraints.insets = new java.awt.Insets(0, 5, 4, 21);
    personal.add(textNID, gridBagConstraints);

    labelContactNo.setText("Contact No");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.weightx = 0.1;
    gridBagConstraints.insets = new java.awt.Insets(0, 18, 2, 4);
    personal.add(labelContactNo, gridBagConstraints);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 5;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.weightx = 0.1;
    gridBagConstraints.insets = new java.awt.Insets(0, 18, 4, 4);
    personal.add(textContactNo, gridBagConstraints);

    labelEmail.setText("Email");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.weightx = 0.1;
    gridBagConstraints.insets = new java.awt.Insets(0, 5, 2, 4);
    personal.add(labelEmail, gridBagConstraints);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 5;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.weightx = 0.1;
    gridBagConstraints.insets = new java.awt.Insets(0, 5, 4, 4);
    personal.add(textEmail, gridBagConstraints);

    labelMaritalStatus.setText("Marital Status");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 2;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.weightx = 0.1;
    gridBagConstraints.insets = new java.awt.Insets(0, 5, 2, 21);
    personal.add(labelMaritalStatus, gridBagConstraints);

    comboMaritalStatus.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 2;
    gridBagConstraints.gridy = 5;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.weightx = 0.1;
    gridBagConstraints.insets = new java.awt.Insets(0, 5, 4, 21);
    personal.add(comboMaritalStatus, gridBagConstraints);

    labelNextOfKin.setText("Next of Kin");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 6;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.weightx = 0.1;
    gridBagConstraints.insets = new java.awt.Insets(0, 18, 2, 4);
    personal.add(labelNextOfKin, gridBagConstraints);

    textNextOfKin.setPreferredSize(new java.awt.Dimension(180, 26));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 7;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.weightx = 0.1;
    gridBagConstraints.insets = new java.awt.Insets(0, 18, 4, 4);
    personal.add(textNextOfKin, gridBagConstraints);

    labelEmergencyContact.setText("Emergency Contact");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 6;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.weightx = 0.1;
    gridBagConstraints.insets = new java.awt.Insets(0, 5, 2, 4);
    personal.add(labelEmergencyContact, gridBagConstraints);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 7;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.weightx = 0.1;
    gridBagConstraints.insets = new java.awt.Insets(0, 5, 4, 4);
    personal.add(textEmergencyContact, gridBagConstraints);

    labelHealthStatus.setText("Health Status");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 2;
    gridBagConstraints.gridy = 6;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.weightx = 0.1;
    gridBagConstraints.insets = new java.awt.Insets(0, 5, 2, 21);
    personal.add(labelHealthStatus, gridBagConstraints);

    comboHealthStatus.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 2;
    gridBagConstraints.gridy = 7;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.weightx = 0.1;
    gridBagConstraints.insets = new java.awt.Insets(0, 5, 4, 21);
    personal.add(comboHealthStatus, gridBagConstraints);

    labelEducationLevel.setText("Education Level");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 8;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.weightx = 0.1;
    gridBagConstraints.insets = new java.awt.Insets(0, 5, 2, 4);
    personal.add(labelEducationLevel, gridBagConstraints);

    textEducationLevel.setPreferredSize(new java.awt.Dimension(180, 26));
    textEducationLevel.addActionListener(this::textEducationLevelActionPerformed);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 9;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.weightx = 0.1;
    gridBagConstraints.insets = new java.awt.Insets(0, 5, 4, 4);
    personal.add(textEducationLevel, gridBagConstraints);

    labelOtherSkills.setText("Other Skills");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 2;
    gridBagConstraints.gridy = 8;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.weightx = 0.1;
    gridBagConstraints.insets = new java.awt.Insets(0, 5, 2, 21);
    personal.add(labelOtherSkills, gridBagConstraints);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 2;
    gridBagConstraints.gridy = 9;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.weightx = 0.1;
    gridBagConstraints.insets = new java.awt.Insets(0, 5, 4, 21);
    personal.add(textOtherSkills, gridBagConstraints);

    labelHobby.setText("Hobby");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 8;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.weightx = 0.1;
    gridBagConstraints.insets = new java.awt.Insets(0, 18, 2, 4);
    personal.add(labelHobby, gridBagConstraints);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 9;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTH;
    gridBagConstraints.weightx = 0.1;
    gridBagConstraints.insets = new java.awt.Insets(0, 18, 4, 4);
    personal.add(textHobby, gridBagConstraints);

    labelHasDrivingLicense.setText("Has Driving License");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 10;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.weightx = 0.1;
    gridBagConstraints.insets = new java.awt.Insets(0, 18, 2, 4);
    personal.add(labelHasDrivingLicense, gridBagConstraints);

    comboDrivingLicense.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
    comboDrivingLicense.addActionListener(this::comboDrivingLicenseActionPerformed);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 11;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTH;
    gridBagConstraints.weightx = 0.1;
    gridBagConstraints.insets = new java.awt.Insets(0, 18, 4, 4);
    personal.add(comboDrivingLicense, gridBagConstraints);

    jLabel1.setText("Address");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 10;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    personal.add(jLabel1, gridBagConstraints);

    jTextArea1.setColumns(20);
    jTextArea1.setRows(5);
    jScrollPane1.setViewportView(jTextArea1);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 11;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    personal.add(jScrollPane1, gridBagConstraints);

    jTabbedPane1.addTab("Personal", personal);

    employment.setLayout(new java.awt.GridBagLayout());

    labelDepartment.setText("Department");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.weightx = 0.1;
    gridBagConstraints.insets = new java.awt.Insets(15, 26, 2, 18);
    employment.add(labelDepartment, gridBagConstraints);

    comboDepartment.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
    comboDepartment.addActionListener(this::comboDepartmentActionPerformed);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.weightx = 0.1;
    gridBagConstraints.insets = new java.awt.Insets(7, 26, 8, 18);
    employment.add(comboDepartment, gridBagConstraints);

    labelPosition.setText("Position");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.weightx = 0.1;
    gridBagConstraints.insets = new java.awt.Insets(15, 0, 2, 18);
    employment.add(labelPosition, gridBagConstraints);

    comboPosition.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.weightx = 0.1;
    gridBagConstraints.insets = new java.awt.Insets(7, 0, 8, 18);
    employment.add(comboPosition, gridBagConstraints);

    labelTeam.setText("Team");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.weightx = 0.1;
    gridBagConstraints.insets = new java.awt.Insets(15, 0, 2, 18);
    employment.add(labelTeam, gridBagConstraints);

    comboTeam.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 2;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.weightx = 0.1;
    gridBagConstraints.insets = new java.awt.Insets(7, 0, 8, 18);
    employment.add(comboTeam, gridBagConstraints);

    labelOffice.setText("Office");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.weightx = 0.1;
    gridBagConstraints.insets = new java.awt.Insets(15, 26, 2, 18);
    employment.add(labelOffice, gridBagConstraints);

    comboOffice.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
    comboOffice.setPreferredSize(new java.awt.Dimension(150, 26));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.weightx = 0.1;
    gridBagConstraints.insets = new java.awt.Insets(7, 26, 8, 18);
    employment.add(comboOffice, gridBagConstraints);

    labelStatus.setText("Status");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.weightx = 0.1;
    gridBagConstraints.insets = new java.awt.Insets(15, 0, 2, 18);
    employment.add(labelStatus, gridBagConstraints);

    comboStatus.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
    comboStatus.setPreferredSize(new java.awt.Dimension(150, 26));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.weightx = 0.1;
    gridBagConstraints.insets = new java.awt.Insets(7, 0, 8, 18);
    employment.add(comboStatus, gridBagConstraints);

    labelCommencementDate.setText("Commencement Date");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 2;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.weightx = 0.1;
    gridBagConstraints.insets = new java.awt.Insets(15, 0, 1, 18);
    employment.add(labelCommencementDate, gridBagConstraints);

    dcCommencementDate.setPreferredSize(new java.awt.Dimension(150, 26));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 2;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.weightx = 0.1;
    gridBagConstraints.insets = new java.awt.Insets(7, 0, 8, 18);
    employment.add(dcCommencementDate, gridBagConstraints);

    labelContractType.setText("Contract Type");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.weightx = 0.1;
    gridBagConstraints.insets = new java.awt.Insets(15, 0, 2, 18);
    employment.add(labelContractType, gridBagConstraints);

    comboContractType.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 5;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.weightx = 0.1;
    gridBagConstraints.insets = new java.awt.Insets(7, 0, 8, 18);
    employment.add(comboContractType, gridBagConstraints);

    labelApplicationDate.setText("Application Date");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.weightx = 0.1;
    gridBagConstraints.insets = new java.awt.Insets(15, 26, 2, 18);
    employment.add(labelApplicationDate, gridBagConstraints);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 5;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.weightx = 0.1;
    gridBagConstraints.insets = new java.awt.Insets(7, 26, 8, 18);
    employment.add(dcApplicationDate, gridBagConstraints);

    labelSalary.setText("Salary");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 2;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.weightx = 0.1;
    gridBagConstraints.insets = new java.awt.Insets(15, 0, 2, 18);
    employment.add(labelSalary, gridBagConstraints);

    textSalary.setPreferredSize(new java.awt.Dimension(150, 26));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 2;
    gridBagConstraints.gridy = 5;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.weightx = 0.1;
    gridBagConstraints.insets = new java.awt.Insets(7, 0, 8, 18);
    employment.add(textSalary, gridBagConstraints);

    jTabbedPane1.addTab("Employment", employment);

    documents.setLayout(new java.awt.BorderLayout());

    dhead.setPreferredSize(new java.awt.Dimension(700, 60));

    btnUpload.setText("Upload");

    javax.swing.GroupLayout dheadLayout = new javax.swing.GroupLayout(dhead);
    dhead.setLayout(dheadLayout);
    dheadLayout.setHorizontalGroup(
      dheadLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGroup(dheadLayout.createSequentialGroup()
        .addGap(353, 353, 353)
        .addComponent(labelUploadFile, javax.swing.GroupLayout.PREFERRED_SIZE, 193, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(35, 35, 35)
        .addComponent(btnUpload)
        .addGap(40, 40, 40))
    );
    dheadLayout.setVerticalGroup(
      dheadLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGroup(dheadLayout.createSequentialGroup()
        .addGap(17, 17, 17)
        .addGroup(dheadLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
          .addComponent(labelUploadFile)
          .addComponent(btnUpload)))
    );

    documents.add(dhead, java.awt.BorderLayout.PAGE_START);

    tableDocument.setModel(new javax.swing.table.DefaultTableModel(
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
    jScrollPane2.setViewportView(tableDocument);

    javax.swing.GroupLayout dbodyLayout = new javax.swing.GroupLayout(dbody);
    dbody.setLayout(dbodyLayout);
    dbodyLayout.setHorizontalGroup(
      dbodyLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addComponent(jScrollPane2, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 697, Short.MAX_VALUE)
    );
    dbodyLayout.setVerticalGroup(
      dbodyLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 359, Short.MAX_VALUE)
    );

    documents.add(dbody, java.awt.BorderLayout.CENTER);

    jTabbedPane1.addTab("Documents", documents);

    javax.swing.GroupLayout bodyLayout = new javax.swing.GroupLayout(body);
    body.setLayout(bodyLayout);
    bodyLayout.setHorizontalGroup(
      bodyLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addComponent(jTabbedPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 697, Short.MAX_VALUE)
    );
    bodyLayout.setVerticalGroup(
      bodyLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addComponent(jTabbedPane1)
    );

    getContentPane().add(body, java.awt.BorderLayout.CENTER);

    pack();
  }// </editor-fold>//GEN-END:initComponents

  private void comboDepartmentActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_comboDepartmentActionPerformed
    if (combosReady) {
      refillPositions(selectedId(departmentIds, comboDepartment));
    }
  }//GEN-LAST:event_comboDepartmentActionPerformed

  private void textEducationLevelActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_textEducationLevelActionPerformed
    // TODO add your handling code here:
  }//GEN-LAST:event_textEducationLevelActionPerformed

  private void comboDrivingLicenseActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_comboDrivingLicenseActionPerformed
    // TODO add your handling code here:
  }//GEN-LAST:event_comboDrivingLicenseActionPerformed

  /**
   * @param args the command line arguments
   */
  public static void main(String args[]) {
    /* Set the Nimbus look and feel */
    //<editor-fold defaultstate="collapsed" desc=" Look and feel setting code (optional) ">
    /* If Nimbus (introduced in Java SE 6) is not available, stay with the default look and feel.
         * For details see http://download.oracle.com/javase/tutorial/uiswing/lookandfeel/plaf.html 
     */
    try {
      for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
        if ("Nimbus".equals(info.getName())) {
          javax.swing.UIManager.setLookAndFeel(info.getClassName());
          break;
        }
      }
    } catch (ReflectiveOperationException | javax.swing.UnsupportedLookAndFeelException ex) {
      logger.log(java.util.logging.Level.SEVERE, null, ex);
    }
    //</editor-fold>

    /* Create and display the dialog */
    java.awt.EventQueue.invokeLater(new Runnable() {
      @Override
      public void run() {
        StaffDialog dialog = new StaffDialog(new javax.swing.JFrame(), true);
        dialog.addWindowListener(new java.awt.event.WindowAdapter() {
          @Override
          public void windowClosing(java.awt.event.WindowEvent e) {
            System.exit(0);
          }
        });
        dialog.setVisible(true);
      }
    });
  }

  // Variables declaration - do not modify//GEN-BEGIN:variables
  private javax.swing.JPanel body;
  private javax.swing.JButton btnCancel;
  private javax.swing.JButton btnSave;
  private javax.swing.JButton btnUpload;
  private javax.swing.JComboBox<String> comboBlood;
  private javax.swing.JComboBox<String> comboContractType;
  private javax.swing.JComboBox<String> comboDepartment;
  private javax.swing.JComboBox<String> comboDrivingLicense;
  private javax.swing.JComboBox<String> comboHealthStatus;
  private javax.swing.JComboBox<String> comboMaritalStatus;
  private javax.swing.JComboBox<String> comboOffice;
  private javax.swing.JComboBox<String> comboPosition;
  private javax.swing.JComboBox<String> comboSex;
  private javax.swing.JComboBox<String> comboStatus;
  private javax.swing.JComboBox<String> comboTeam;
  private javax.swing.JPanel dbody;
  private com.toedter.calendar.JDateChooser dcApplicationDate;
  private com.toedter.calendar.JDateChooser dcCommencementDate;
  private com.toedter.calendar.JDateChooser dcDob;
  private javax.swing.JPanel dhead;
  private javax.swing.JPanel documents;
  private javax.swing.JPanel employment;
  private javax.swing.JPanel footer;
  private javax.swing.JLabel jLabel1;
  private javax.swing.JScrollPane jScrollPane1;
  private javax.swing.JScrollPane jScrollPane2;
  private javax.swing.JTabbedPane jTabbedPane1;
  private javax.swing.JTextArea jTextArea1;
  private javax.swing.JLabel labelApplicationDate;
  private javax.swing.JLabel labelBlood;
  private javax.swing.JLabel labelCode;
  private javax.swing.JLabel labelCommencementDate;
  private javax.swing.JLabel labelContactNo;
  private javax.swing.JLabel labelContractType;
  private javax.swing.JLabel labelDepartment;
  private javax.swing.JLabel labelDob;
  private javax.swing.JLabel labelEducationLevel;
  private javax.swing.JLabel labelEmail;
  private javax.swing.JLabel labelEmergencyContact;
  private javax.swing.JLabel labelHasDrivingLicense;
  private javax.swing.JLabel labelHealthStatus;
  private javax.swing.JLabel labelHobby;
  private javax.swing.JLabel labelMaritalStatus;
  private javax.swing.JLabel labelNID;
  private javax.swing.JLabel labelName;
  private javax.swing.JLabel labelNextOfKin;
  private javax.swing.JLabel labelOffice;
  private javax.swing.JLabel labelOtherSkills;
  private javax.swing.JLabel labelPosition;
  private javax.swing.JLabel labelSalary;
  private javax.swing.JLabel labelSex;
  private javax.swing.JLabel labelStatus;
  private javax.swing.JLabel labelTeam;
  private javax.swing.JLabel labelUploadFile;
  private javax.swing.JPanel personal;
  private javax.swing.JTable tableDocument;
  private javax.swing.JTextField textCode;
  private javax.swing.JTextField textContactNo;
  private javax.swing.JTextField textEducationLevel;
  private javax.swing.JTextField textEmail;
  private javax.swing.JTextField textEmergencyContact;
  private javax.swing.JTextField textHobby;
  private javax.swing.JTextField textNID;
  private javax.swing.JTextField textName;
  private javax.swing.JTextField textNextOfKin;
  private javax.swing.JTextField textOtherSkills;
  private javax.swing.JTextField textSalary;
  // End of variables declaration//GEN-END:variables
}
