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
import hrms.model.Team;
import hrms.repository.DepartmentRepository;
import hrms.repository.ShiftRepository;
import hrms.repository.TeamRepository;
import hrms.security.Permissions;
import hrms.security.SecurityService;
import hrms.security.SessionContext;
import hrms.util.UiThread;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author aungmrat
 */
public class ShiftDialog extends javax.swing.JDialog {

  private static final Logger LOG = LoggerFactory.getLogger(ShiftDialog.class);

  /** null = create mode. */
  private final Shift editing;

  /** Cached team list so the team combo can follow the department choice. */
  private java.util.List<Team> teams = java.util.List.of();

  /**
   * Creates new form ShiftDialog
   */
  public ShiftDialog(java.awt.Frame parent, boolean modal) {
    this(parent, modal, null);
  }

  /** Create when {@code existing} is null; edit when non-null. */
  public ShiftDialog(java.awt.Frame parent, boolean modal, Shift existing) {
    super(parent, modal);
    initComponents();
    this.editing = existing;
    if (existing == null) {
      setTitle("New Shift");
    } else {
      setTitle("Edit Shift");
      prefill(existing);
    }
    setupActions();
    loadCombos();
  }

  /** Apply stored values first; combo selections are applied after loadCombos(). */
  private void prefill(Shift shift) {
    textCode.setText(shift.getCode() == null ? "" : shift.getCode());
    textStartTime.setText(shift.getStartTime() == null ? "" : shift.getStartTime().toString());
    textEndTime.setText(shift.getEndTime() == null ? "" : shift.getEndTime().toString());
    comboIsOvernight.setSelectedItem(shift.isOvernight() ? "Yes" : "No");
    textRemark.setText(shift.getRemark() == null ? "" : shift.getRemark());
  }

  private void setupActions() {
    // The form's GEN block has no event handlers, so both buttons are wired here.
    btnCancel.addActionListener(e -> dispose());
    btnSave.addActionListener(e -> {
      try {
        saveAndClose();
      } catch (AuthorizationException ex) {
        ErrorHandler.handle(this, ex);
      }
    });
    // Team belongs to one department, so the team list follows the choice.
    comboDepartment.addActionListener(e -> refillTeams());
  }

  /** Permission gate for Save; static so headless tests can verify it. */
  static void requireSavePermission(boolean isEdit) {
    SecurityService.require(isEdit
            ? Permissions.SHIFT_UPDATE : Permissions.SHIFT_CREATE);
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
  private record ComboData(java.util.List<Department> departments,
          java.util.List<Team> teams) {
  }

  /** All combos are loaded from the database — never hardcode the items. */
  private void loadCombos() {
    UiThread.executeAsync("Load shift combos",
            () -> new ComboData(DepartmentRepository.listAll(),
                    TeamRepository.listAll()),
            this::fillCombos);
  }

  private void fillCombos(ComboData data) {
    this.teams = data.teams();

    comboDepartment.removeAllItems();
    for (Department department : data.departments()) {
      comboDepartment.addItem(new ComboItem(department.getId(),
              department.getCode() + " — " + department.getName()));
    }

    if (editing == null) {
      return;
    }
    selectByValue(comboDepartment, editing.getDepartmentId());
    refillTeams();
    selectByValue(comboTeam, editing.getTeamId());
  }

  /**
   * Rebuilds the team combo for the selected department, keeping the
   * current choice when that team still belongs to it.
   */
  private void refillTeams() {
    ComboItem department = (ComboItem) comboDepartment.getSelectedItem();
    Long departmentId = department == null ? null : department.value();
    ComboItem previous = (ComboItem) comboTeam.getSelectedItem();
    comboTeam.removeAllItems();
    comboTeam.addItem(new ComboItem(null, "(None)"));
    for (Team team : teams) {
      if (team.getDepartmentId() != null && (departmentId == null
              || team.getDepartmentId().equals(departmentId))) {
        comboTeam.addItem(new ComboItem(team.getId(), team.getName()));
      }
    }
    if (previous != null) {
      selectByValue(comboTeam, previous.value());
    }
  }

  private void selectByValue(javax.swing.JComboBox<ComboItem> combo, Long value) {
    for (int i = 0; i < combo.getItemCount(); i++) {
      ComboItem item = combo.getItemAt(i);
      if (java.util.Objects.equals(item.value(), value)) {
        combo.setSelectedIndex(i);
        return;
      }
    }
  }

  private Long selectedValue(javax.swing.JComboBox<ComboItem> combo) {
    ComboItem item = (ComboItem) combo.getSelectedItem();
    return item == null ? null : item.value();
  }

  private void saveAndClose() {
    try {
      requireSavePermission(editing != null);
      Long departmentId = selectedValue(comboDepartment);
      Long teamId = selectedValue(comboTeam);
      String code = textCode.getText();
      String startText = textStartTime.getText();
      String endText = textEndTime.getText();
      boolean overnight = "Yes".equals(comboIsOvernight.getSelectedItem());
      String remark = textRemark.getText();

      java.util.List<String> errors = new java.util.ArrayList<>();
      if (departmentId == null) {
        errors.add("Department is required.");
      }
      if (code == null || code.isBlank()) {
        errors.add("Shift code is required.");
      } else if (code.trim().length() > 20) {
        errors.add("Shift code must be at most 20 characters.");
      }

      java.time.LocalTime startTime = null;
      java.time.LocalTime endTime = null;
      if (startText == null || startText.isBlank()) {
        errors.add("Start time is required (HH:mm).");
      } else {
        try {
          startTime = java.time.LocalTime.parse(startText.trim());
        } catch (java.time.format.DateTimeParseException ex) {
          errors.add("Start time must be HH:mm (e.g. 09:00).");
        }
      }
      if (endText == null || endText.isBlank()) {
        errors.add("End time is required (HH:mm).");
      } else {
        try {
          endTime = java.time.LocalTime.parse(endText.trim());
        } catch (java.time.format.DateTimeParseException ex) {
          errors.add("End time must be HH:mm (e.g. 17:00).");
        }
      }
      // Mirrors DB CHECK: end > start OR is_overnight = TRUE.
      if (startTime != null && endTime != null
              && !endTime.isAfter(startTime) && !overnight) {
        errors.add("End time must be after start time, unless Is Overnight is Yes.");
      }
      if (remark != null && remark.trim().length() > 500) {
        errors.add("Remark must be at most 500 characters.");
      }
      if (!errors.isEmpty()) {
        throw new ValidationException(errors);
      }

      Long userId = SessionContext.isAuthenticated() ? SessionContext.currentUserId() : null;
      boolean isEdit = editing != null;
      Shift shift;
      if (isEdit) {
        shift = editing;
        shift.setDepartmentId(departmentId);
        shift.setTeamId(teamId);
        shift.setCode(code.trim());
        shift.setStartTime(startTime);
        shift.setEndTime(endTime);
        shift.setOvernight(overnight);
        shift.setRemark(remark);
        shift.setModifiedBy(userId);
      } else {
        shift = Shift.create(departmentId, teamId, code.trim(),
                startTime, endTime, overnight, remark, userId);
      }

      btnSave.setEnabled(false);
      UiThread.executeAsync(isEdit ? "Update shift" : "Save shift",
              () -> isEdit
                      ? ShiftRepository.update(shift)
                      : ShiftRepository.insert(shift),
              result -> {
                if (isEdit) {
                  LOG.info("Shift updated id={}", shift.getId());
                } else {
                  LOG.info("Shift saved with id {}", result);
                }
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
    labelDepartment = new javax.swing.JLabel();
    comboDepartment = new javax.swing.JComboBox<>();
    labelCode = new javax.swing.JLabel();
    textCode = new javax.swing.JTextField();
    labelTeam = new javax.swing.JLabel();
    comboTeam = new javax.swing.JComboBox<>();
    labelStartTime = new javax.swing.JLabel();
    textStartTime = new javax.swing.JTextField();
    labelEndTime = new javax.swing.JLabel();
    textEndTime = new javax.swing.JTextField();
    labelIsOvernight = new javax.swing.JLabel();
    comboIsOvernight = new javax.swing.JComboBox<>();
    labelRemark = new javax.swing.JLabel();
    textRemark = new javax.swing.JTextField();

    setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);

    footer.setPreferredSize(new java.awt.Dimension(400, 60));
    footer.setLayout(new java.awt.GridBagLayout());

    btnSave.setText("Save");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
    gridBagConstraints.insets = new java.awt.Insets(18, 18, 15, 40);
    footer.add(btnSave, gridBagConstraints);

    btnCancel.setText("Cancel");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
    gridBagConstraints.insets = new java.awt.Insets(18, 190, 15, 0);
    footer.add(btnCancel, gridBagConstraints);

    getContentPane().add(footer, java.awt.BorderLayout.PAGE_END);

    body.setLayout(new java.awt.GridBagLayout());

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

    labelCode.setText("Code");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(6, 0, 7, 14);
    body.add(labelCode, gridBagConstraints);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(6, 0, 7, 0);
    body.add(textCode, gridBagConstraints);

    labelTeam.setText("Team");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(6, 0, 7, 14);
    body.add(labelTeam, gridBagConstraints);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(6, 0, 7, 0);
    body.add(comboTeam, gridBagConstraints);

    labelStartTime.setText("Start Time (HH:mm)");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(6, 0, 7, 14);
    body.add(labelStartTime, gridBagConstraints);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(6, 0, 7, 0);
    body.add(textStartTime, gridBagConstraints);

    labelEndTime.setText("End Time (HH:mm)");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(6, 0, 7, 14);
    body.add(labelEndTime, gridBagConstraints);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(6, 0, 7, 0);
    body.add(textEndTime, gridBagConstraints);

    labelIsOvernight.setText("Is Overnight");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 5;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(6, 0, 7, 14);
    body.add(labelIsOvernight, gridBagConstraints);

    comboIsOvernight.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "No", "Yes" }));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 5;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(6, 0, 7, 0);
    body.add(comboIsOvernight, gridBagConstraints);

    labelRemark.setText("Remark");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 6;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(6, 0, 7, 14);
    body.add(labelRemark, gridBagConstraints);

    textRemark.setPreferredSize(new java.awt.Dimension(200, 26));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 6;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(6, 0, 7, 0);
    body.add(textRemark, gridBagConstraints);

    getContentPane().add(body, java.awt.BorderLayout.CENTER);

    pack();
  }// </editor-fold>//GEN-END:initComponents

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
      LOG.error("Cannot set look and feel", ex);
    }
    //</editor-fold>

    /* Create and display the dialog */
    java.awt.EventQueue.invokeLater(new Runnable() {
      @Override
      public void run() {
        ShiftDialog dialog = new ShiftDialog(new javax.swing.JFrame(), true);
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
  private javax.swing.JComboBox<ComboItem> comboDepartment;
  private javax.swing.JComboBox<String> comboIsOvernight;
  private javax.swing.JComboBox<ComboItem> comboTeam;
  private javax.swing.JPanel footer;
  private javax.swing.JLabel labelCode;
  private javax.swing.JLabel labelDepartment;
  private javax.swing.JLabel labelEndTime;
  private javax.swing.JLabel labelIsOvernight;
  private javax.swing.JLabel labelRemark;
  private javax.swing.JLabel labelStartTime;
  private javax.swing.JLabel labelTeam;
  private javax.swing.JTextField textCode;
  private javax.swing.JTextField textEndTime;
  private javax.swing.JTextField textRemark;
  private javax.swing.JTextField textStartTime;
  // End of variables declaration//GEN-END:variables
}
