/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/GUIForms/JDialog.java to edit this template
 */
package hrms.ui.leavemanagement;

import hrms.exception.AuthorizationException;
import hrms.exception.ErrorHandler;
import hrms.model.LeaveEntitlement;
import hrms.model.LeaveType;
import hrms.repository.LeaveEntitlementRepository;
import hrms.repository.LeaveTypeRepository;
import hrms.security.Permissions;
import hrms.security.SecurityService;
import hrms.security.SessionContext;
import hrms.util.Dialogs;
import hrms.util.UiThread;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author aungmrat
 */
public class LeaveEntitlementGenerateDialog extends javax.swing.JDialog {

  private static final Logger LOG = LoggerFactory.getLogger(LeaveEntitlementGenerateDialog.class);

  /** Active leave types; index-aligned with the JList contents. */
  private final List<LeaveType> leaveTypes = new ArrayList<>();

  public LeaveEntitlementGenerateDialog(java.awt.Frame parent, boolean modal) {
    super(parent, modal);
    initComponents();
    setTitle("Generate Leave Entitlements");
    applyDefaults();
    setupActions();
    loadTypes();
  }

  private void applyDefaults() {
    textPreview.setEditable(false);
    spinnerYear.setValue(LocalDate.now().getYear());
    comboCarryForward.setModel(new javax.swing.DefaultComboBoxModel<>(
            new String[]{"Yes", "No"}));
    comboCarryForward.setSelectedItem("Yes");
    labelHint.setText("<html>Existing (staff, type, 1 Jan) rows are skipped — safe to re-run."
            + "<br>Mid-year joiners are pro-rated automatically.</html>");
  }

  private void setupActions() {
    btnCancel.addActionListener(e -> dispose());
    btnGenerate.addActionListener(e -> {
      try {
        generate();
      } catch (AuthorizationException ex) {
        ErrorHandler.handle(this, ex);
      }
    });
    spinnerYear.addChangeListener(e -> refreshPreview());
    comboCarryForward.addActionListener(e -> refreshPreview());
    listLeaveTypes.addListSelectionListener(e -> {
      if (!e.getValueIsAdjusting()) {
        refreshPreview();
      }
    });
  }

  private void loadTypes() {
    UiThread.executeAsync("Load leave types",
        () -> LeaveTypeRepository.listAll().stream().filter(LeaveType::isActive).toList(),
        types -> {
          leaveTypes.clear();
          leaveTypes.addAll(types);
          String[] labels = new String[types.size()];
          for (int i = 0; i < types.size(); i++) {
            LeaveType type = types.get(i);
            labels[i] = type.getCode() + " - " + type.getName();
          }
          listLeaveTypes.setListData(labels);
          listLeaveTypes.setSelectionInterval(0, Math.max(labels.length - 1, 0));
        },
        error -> ErrorHandler.handle(this, error));
  }

  private int selectedYear() {
    Object value = spinnerYear.getValue();
    return value instanceof Number number ? number.intValue() : 0;
  }

  private boolean carryForward() {
    return "Yes".equals(comboCarryForward.getSelectedItem());
  }

  /** The active leave types checked in the list (index-aligned). */
  private List<LeaveType> selectedTypes() {
    int[] indices = listLeaveTypes.getSelectedIndices();
    List<LeaveType> selected = new ArrayList<>(indices.length);
    for (int index : indices) {
      if (index >= 0 && index < leaveTypes.size()) {
        selected.add(leaveTypes.get(index));
      }
    }
    return selected;
  }

  private record Plan(int staffCount, int typeCount, long toInsert, long skipped,
          long skippedGender, long prorated, boolean carryForward) {

    String describe() {
      return """
              Active staff: %d
              Selected types: %d
              Rows to insert: %d
              Skipped (already exist): %d
              Skipped (gender): %d
              Prorated (joined mid-year): %d
              Carry forward from previous year: %s"""
              .formatted(staffCount, typeCount, toInsert, skipped, skippedGender,
                      prorated, carryForward ? "Yes" : "No");
    }
  }

  private void refreshPreview() {
    int year = selectedYear();
    List<LeaveType> types = selectedTypes();
    if (year < 2000 || year > 2100) {
      textPreview.setText("Choose a valid year.");
      return;
    }
    if (types.isEmpty()) {
      textPreview.setText("Select at least one leave type.");
      return;
    }
    UiThread.executeAsync("Preview entitlement generation",
        () -> computePlan(year, types, carryForward()),
        plan -> textPreview.setText(plan.describe()),
        error -> {
          textPreview.setText("Preview failed — see log.");
          ErrorHandler.handle(this, error);
        });
  }

  private static Plan computePlan(int year, List<LeaveType> types, boolean carry) {
    LocalDate effective = LocalDate.of(year, 1, 1);
    List<LeaveEntitlementRepository.StaffBasic> staff = LeaveEntitlementRepository.listActiveStaff();
    Set<String> existing = LeaveEntitlementRepository.existingKeys(effective);
    long skipped = 0;
    long skippedGender = 0;
    long toInsert = 0;
    for (LeaveEntitlementRepository.StaffBasic member : staff) {
      for (LeaveType type : types) {
        if (existing.contains(member.id() + ":" + type.getId())) {
          skipped++;
        } else if (!type.allowsSex(member.sex())) {
          skippedGender++;
        } else {
          toInsert++;
        }
      }
    }
    long prorated = staff.stream()
        .filter(s -> s.commencementDate() != null && s.commencementDate().isAfter(effective))
        .count();
    return new Plan(staff.size(), types.size(), toInsert, skipped, skippedGender,
            prorated, carry);
  }

  private void generate() {
    SecurityService.require(Permissions.LEAVE_ENTITLEMENT_CREATE);
    int year = selectedYear();
    List<LeaveType> types = selectedTypes();
    if (year < 2000 || year > 2100) {
      Dialogs.info(this, "Generate Entitlements", "Choose a valid year.");
      return;
    }
    if (types.isEmpty()) {
      Dialogs.info(this, "Generate Entitlements", "Select at least one leave type.");
      return;
    }
    if (!Dialogs.confirm(this, "Generate Entitlements",
        "Generate leave entitlements for " + year + "? Existing rows are skipped.")) {
      return;
    }
    btnGenerate.setEnabled(false);
    boolean carry = carryForward();
    Long userId = SessionContext.isAuthenticated() ? SessionContext.currentUserId() : null;
    UiThread.executeAsync("Generate leave entitlements",
        () -> insertRows(year, types, carry, userId),
        inserted -> {
          LOG.info("Generated {} leave entitlement rows for {}", inserted, year);
          Dialogs.info(this, "Generate Entitlements",
              "Inserted " + inserted + " row(s) for " + year + ".");
          dispose();
        },
        error -> {
          btnGenerate.setEnabled(true);
          ErrorHandler.handle(this, error);
        });
  }

  /** Builds and inserts every missing row; returns the inserted count. */
  private static long insertRows(int year, List<LeaveType> types, boolean carry, Long userId) {
    LocalDate effective = LocalDate.of(year, 1, 1);
    LocalDate expiry = LocalDate.of(year, 12, 31);
    LocalDate previousEffective = LocalDate.of(year - 1, 1, 1);
    Set<String> existing = LeaveEntitlementRepository.existingKeys(effective);
    List<LeaveEntitlementRepository.StaffBasic> staff = LeaveEntitlementRepository.listActiveStaff();
    long inserted = 0;
    for (LeaveEntitlementRepository.StaffBasic member : staff) {
      for (LeaveType type : types) {
        if (existing.contains(member.id() + ":" + type.getId())) {
          continue;
        }
        if (!type.allowsSex(member.sex())) {
          continue;
        }
        int entitled = LeaveEntitlementRepository.maxEntitledDays(
            member.commencementDate(), effective, expiry, type.getStandardDaysPerYear());
        int carried = carry ? previousCarriedDays(member.id(), type, previousEffective) : 0;
        LeaveEntitlement entitlement = LeaveEntitlement.create(
            member.id(), type.getId(), entitled, carried, 0, effective, expiry,
            "Auto-generated " + year, userId);
        LeaveEntitlementRepository.insert(entitlement);
        inserted++;
      }
    }
    return inserted;
  }

  /**
   * Previous-year balance for a carry-forward type, capped at
   * {@code carry_max_days}. Zero when the type does not carry or has no cap.
   */
  private static int previousCarriedDays(long staffId, LeaveType type,
          LocalDate previousEffective) {
    if (!type.isCarryForward() || type.getCarryMaxDays() == null) {
      return 0;
    }
    return LeaveEntitlementRepository.findByStaffTypeAndEffective(
            staffId, type.getId(), previousEffective)
        .map(previous -> {
          int balance = previous.getEntitledDays()
              + (previous.getCarriedDays() == null ? 0 : previous.getCarriedDays())
              - (previous.getUsedDays() == null ? 0 : previous.getUsedDays());
          return Math.max(0, Math.min(balance, type.getCarryMaxDays()));
        })
        .orElse(0);
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
    btnGenerate = new javax.swing.JButton();
    btnCancel = new javax.swing.JButton();
    body = new javax.swing.JPanel();
    labelYear = new javax.swing.JLabel();
    spinnerYear = new javax.swing.JSpinner();
    labelCarryForward = new javax.swing.JLabel();
    comboCarryForward = new javax.swing.JComboBox<>();
    labelLeaveType = new javax.swing.JLabel();
    scrollLeaveTypes = new javax.swing.JScrollPane();
    listLeaveTypes = new javax.swing.JList<>();
    labelPreview = new javax.swing.JLabel();
    scrollPreview = new javax.swing.JScrollPane();
    textPreview = new javax.swing.JTextArea();
    labelHint = new javax.swing.JLabel();

    setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);

    footer.setPreferredSize(new java.awt.Dimension(400, 60));
    footer.setLayout(new java.awt.GridBagLayout());

    btnGenerate.setText("Generate");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
    gridBagConstraints.insets = new java.awt.Insets(17, 18, 16, 40);
    footer.add(btnGenerate, gridBagConstraints);

    btnCancel.setText("Cancel");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
    gridBagConstraints.insets = new java.awt.Insets(17, 185, 16, 0);
    footer.add(btnCancel, gridBagConstraints);

    getContentPane().add(footer, java.awt.BorderLayout.PAGE_END);

    body.setLayout(new java.awt.GridBagLayout());

    labelYear.setText("Year");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(5, 11, 5, 18);
    body.add(labelYear, gridBagConstraints);

    spinnerYear.setPreferredSize(new java.awt.Dimension(90, 26));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.insets = new java.awt.Insets(5, 0, 5, 0);
    body.add(spinnerYear, gridBagConstraints);

    labelCarryForward.setText("Carry Forward");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(5, 11, 5, 18);
    body.add(labelCarryForward, gridBagConstraints);

    comboCarryForward.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
    comboCarryForward.setPreferredSize(new java.awt.Dimension(120, 26));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.insets = new java.awt.Insets(5, 0, 5, 0);
    body.add(comboCarryForward, gridBagConstraints);

    labelLeaveType.setText("Leave Type");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(5, 11, 5, 18);
    body.add(labelLeaveType, gridBagConstraints);

    scrollLeaveTypes.setPreferredSize(new java.awt.Dimension(240, 110));

    listLeaveTypes.setModel(new javax.swing.AbstractListModel<String>() {
      String[] strings = { "Annual Leave", "Child Care Leave", "Maternity Leave", "Medical Leave", "Unpaid Leave" };
      public int getSize() { return strings.length; }
      public String getElementAt(int i) { return strings[i]; }
    });
    scrollLeaveTypes.setViewportView(listLeaveTypes);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.insets = new java.awt.Insets(5, 0, 5, 13);
    body.add(scrollLeaveTypes, gridBagConstraints);

    labelPreview.setText("Preview");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(5, 11, 5, 18);
    body.add(labelPreview, gridBagConstraints);

    textPreview.setColumns(26);
    textPreview.setRows(7);
    scrollPreview.setViewportView(textPreview);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.weighty = 1.0;
    gridBagConstraints.insets = new java.awt.Insets(5, 0, 5, 13);
    body.add(scrollPreview, gridBagConstraints);

    labelHint.setText("<html>Existing (staff, type, 1 Jan) rows are skipped — safe to re-run.<br>Mid-year joiners are pro-rated automatically.</html>");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.gridwidth = 2;
    gridBagConstraints.insets = new java.awt.Insets(10, 0, 5, 0);
    body.add(labelHint, gridBagConstraints);

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
        LeaveEntitlementGenerateDialog dialog = new LeaveEntitlementGenerateDialog(new javax.swing.JFrame(), true);
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
  private javax.swing.JButton btnGenerate;
  private javax.swing.JComboBox<String> comboCarryForward;
  private javax.swing.JPanel footer;
  private javax.swing.JLabel labelCarryForward;
  private javax.swing.JLabel labelHint;
  private javax.swing.JLabel labelLeaveType;
  private javax.swing.JLabel labelPreview;
  private javax.swing.JLabel labelYear;
  private javax.swing.JList<String> listLeaveTypes;
  private javax.swing.JScrollPane scrollLeaveTypes;
  private javax.swing.JScrollPane scrollPreview;
  private javax.swing.JSpinner spinnerYear;
  private javax.swing.JTextArea textPreview;
  // End of variables declaration//GEN-END:variables
}
