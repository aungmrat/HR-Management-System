/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/GUIForms/JDialog.java to edit this template
 */
package hrms.ui.attendanceandroster;

import hrms.exception.AuthorizationException;
import hrms.exception.ErrorHandler;
import hrms.exception.ValidationException;
import hrms.model.Department;
import hrms.model.Shift;
import hrms.repository.DepartmentRepository;
import hrms.repository.DutyRosterRepository;
import hrms.repository.DutyRosterRepository.FillResult;
import hrms.repository.ShiftRepository;
import hrms.security.Permissions;
import hrms.security.SecurityService;
import hrms.security.SessionContext;
import hrms.util.Dialogs;
import hrms.util.UiThread;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author aungmrat
 */
public class AutoFillDialog extends javax.swing.JDialog {

  private static final Logger LOG = LoggerFactory.getLogger(AutoFillDialog.class);

  private static final DateTimeFormatter DAY_FORMAT =
          DateTimeFormatter.ofPattern("dd MMM yyyy");

  /** Monday of the week this dialog fills. */
  private final LocalDate weekStart;

  /** Cached shift list so the preview label can be refreshed on choice. */
  private List<String> staffPreview = List.of();

  /** True while fillCombos is populating; suppresses preview reload storms. */
  private boolean combosLoading;

  /** Bumped per preview request so a stale response cannot overwrite a newer one. */
  private int previewSequence;

  /**
   * Creates new form AutoFillDialog
   */
  public AutoFillDialog(java.awt.Frame parent, boolean modal) {
    this(parent, modal, mondayOf(LocalDate.now()));
  }

  /** Fills {@code weekStart} (Monday) .. +6 days. */
  public AutoFillDialog(java.awt.Frame parent, boolean modal, LocalDate weekStart) {
    super(parent, modal);
    initComponents();
    this.weekStart = mondayOf(weekStart);
    setTitle("Auto-fill Week");
    labelWeekValue.setText(DAY_FORMAT.format(this.weekStart)
            + " – " + DAY_FORMAT.format(this.weekStart.plusDays(6)));
    setupActions();
    loadCombos();
  }

  static LocalDate mondayOf(LocalDate date) {
    return date.with(java.time.DayOfWeek.MONDAY);
  }

  private void setupActions() {
    btnCancel.addActionListener(e -> dispose());
    btnFill.addActionListener(e -> {
      try {
        fillAndClose();
      } catch (AuthorizationException ex) {
        ErrorHandler.handle(this, ex);
      }
    });
    comboDepartment.addActionListener(e -> {
      if (!combosLoading) {
        refreshStaffPreview();
      }
    });
  }

  /** Permission gate for the fill action; static so tests can verify it. */
  static void requireFillPermission() {
    SecurityService.require(Permissions.DUTY_ROSTER_CREATE);
  }

  /**
   * Combo entry holding the database id behind a readable label.
   * toString() is what Swing renders, so the dropdown shows the label
   * while getValue() gives the FK id to save.
   */
  private record ComboItem(Long value, String label) {
    @Override
    public String toString() {
      return label;
    }
  }

  /** Snapshot so all combos load in one background call. */
  private record ComboData(List<Department> departments, List<Shift> shifts) {
  }

  /** All combos are loaded from the database — never hardcode the items. */
  private void loadCombos() {
    UiThread.executeAsync("Load auto-fill combos",
            () -> new ComboData(DepartmentRepository.listAll(),
                    ShiftRepository.listAll()),
            this::fillCombos);
  }

  private void fillCombos(ComboData data) {
    // Block per-item listener fires while the combos are being populated.
    combosLoading = true;
    try {
      comboDepartment.removeAllItems();
      for (Department department : data.departments()) {
        comboDepartment.addItem(new ComboItem(department.getId(),
                department.getCode() + " — " + department.getName()));
      }

      comboShift.removeAllItems();
      for (Shift shift : data.shifts()) {
        comboShift.addItem(new ComboItem(shift.getId(),
                shift.getCode() + " "
                        + shift.getStartTime() + "-" + shift.getEndTime()
                        + (shift.isOvernight() ? " +1" : "")));
      }
    } finally {
      combosLoading = false;
    }
    refreshStaffPreview();
  }

  private Long selectedValue(javax.swing.JComboBox<ComboItem> combo) {
    ComboItem item = (ComboItem) combo.getSelectedItem();
    return item == null ? null : item.value();
  }

  /** Shows how many active staff the fill would cover for the chosen dept. */
  private void refreshStaffPreview() {
    Long departmentId = selectedValue(comboDepartment);
    int request = ++previewSequence;
    if (departmentId == null) {
      staffPreview = List.of();
      labelStaffPreview.setText("Choose a department to preview staff.");
      labelStaffPreview.setToolTipText(null);
      return;
    }
    UiThread.executeAsync("Preview auto-fill staff",
            () -> DutyRosterRepository.listActiveStaffLabels(departmentId),
            labels -> {
              if (request != previewSequence) {
                return; // a newer selection superseded this response
              }
              staffPreview = labels;
              labelStaffPreview.setText(previewText(labels));
              labelStaffPreview.setToolTipText(labels.isEmpty() ? null
                      : String.join("\n", labels));
            });
  }

  /**
   * Keeps the preview label short so a long staff list cannot stretch the
   * dialog. The full list stays in the tooltip.
   */
  static String previewText(List<String> labels) {
    if (labels.isEmpty()) {
      return "No active staff in this department.";
    }
    return labels.size() + " active staff (hover for list)";
  }

  private void fillAndClose() {
    try {
      requireFillPermission();
      Long departmentId = selectedValue(comboDepartment);
      Long shiftId = selectedValue(comboShift);
      boolean skipExisting = checkSkipExisting.isSelected();

      List<String> errors = new ArrayList<>();
      if (departmentId == null) {
        errors.add("Department is required.");
      }
      if (shiftId == null) {
        errors.add("Shift is required.");
      }
      if (!errors.isEmpty()) {
        throw new ValidationException(errors);
      }
      if (staffPreview.isEmpty()) {
        throw new ValidationException(
                List.of("This department has no active staff to fill."));
      }

      Long userId = SessionContext.isAuthenticated() ? SessionContext.currentUserId() : null;
      LocalDate from = weekStart;
      LocalDate to = weekStart.plusDays(6);
      btnFill.setEnabled(false);
      UiThread.executeAsync("Auto-fill week",
              () -> DutyRosterRepository.fillWeek(
                      departmentId, shiftId, from, to, skipExisting, userId),
              (FillResult result) -> {
                LOG.info("Auto-fill done: inserted={}, skipped={}",
                        result.inserted(), result.skipped());
                Dialogs.info(this, "Auto-fill Week",
                        "Created " + result.inserted() + " assignment(s); skipped "
                                + result.skipped() + " existing day(s).");
                dispose();
              },
              error -> {
                btnFill.setEnabled(true);
                ErrorHandler.handle(this, error);
              });
    } catch (ValidationException e) {
      ErrorHandler.handle(this, e);
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
    btnFill = new javax.swing.JButton();
    btnCancel = new javax.swing.JButton();
    body = new javax.swing.JPanel();
    labelWeek = new javax.swing.JLabel();
    labelWeekValue = new javax.swing.JLabel();
    labelDepartment = new javax.swing.JLabel();
    comboDepartment = new javax.swing.JComboBox<>();
    labelShift = new javax.swing.JLabel();
    comboShift = new javax.swing.JComboBox<>();
    checkSkipExisting = new javax.swing.JCheckBox();
    labelStaffPreview = new javax.swing.JLabel();

    setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);

    footer.setPreferredSize(new java.awt.Dimension(400, 60));
    footer.setLayout(new java.awt.GridBagLayout());

    btnFill.setText("Auto-fill");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
    gridBagConstraints.insets = new java.awt.Insets(18, 18, 15, 40);
    footer.add(btnFill, gridBagConstraints);

    btnCancel.setText("Cancel");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
    gridBagConstraints.insets = new java.awt.Insets(18, 175, 15, 0);
    footer.add(btnCancel, gridBagConstraints);

    getContentPane().add(footer, java.awt.BorderLayout.PAGE_END);

    body.setLayout(new java.awt.GridBagLayout());

    labelWeek.setText("Week");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(6, 0, 7, 14);
    body.add(labelWeek, gridBagConstraints);

    labelWeekValue.setText("—");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.insets = new java.awt.Insets(6, 0, 7, 0);
    body.add(labelWeekValue, gridBagConstraints);

    labelDepartment.setText("Department");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(6, 0, 7, 14);
    body.add(labelDepartment, gridBagConstraints);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(6, 0, 7, 0);
    body.add(comboDepartment, gridBagConstraints);

    labelShift.setText("Shift");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(6, 0, 7, 14);
    body.add(labelShift, gridBagConstraints);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(6, 0, 7, 0);
    body.add(comboShift, gridBagConstraints);

    checkSkipExisting.setSelected(true);
    checkSkipExisting.setText("Skip days that already have an assignment");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.insets = new java.awt.Insets(6, 0, 7, 0);
    body.add(checkSkipExisting, gridBagConstraints);

    labelStaffPreview.setText("Choose a department to preview staff.");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.gridwidth = 2;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.insets = new java.awt.Insets(10, 0, 7, 0);
    body.add(labelStaffPreview, gridBagConstraints);

    getContentPane().add(body, java.awt.BorderLayout.CENTER);

    pack();
  }// </editor-fold>//GEN-END:variables

  // Variables declaration - do not modify//GEN-BEGIN:variables
  private javax.swing.JPanel body;
  private javax.swing.JButton btnCancel;
  private javax.swing.JButton btnFill;
  private javax.swing.JCheckBox checkSkipExisting;
  private javax.swing.JComboBox<ComboItem> comboDepartment;
  private javax.swing.JComboBox<ComboItem> comboShift;
  private javax.swing.JPanel footer;
  private javax.swing.JLabel labelDepartment;
  private javax.swing.JLabel labelShift;
  private javax.swing.JLabel labelStaffPreview;
  private javax.swing.JLabel labelWeek;
  private javax.swing.JLabel labelWeekValue;
  // End of variables declaration//GEN-END:variables
}
