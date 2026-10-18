/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/GUIForms/JDialog.java to edit this template
 */
package hrms.ui.payrollandcompensation;

import hrms.exception.AuthorizationException;
import hrms.exception.ErrorHandler;
import hrms.exception.ValidationException;
import hrms.model.Benefit;
import hrms.model.StaffBenefit;
import hrms.repository.BenefitRepository;
import hrms.repository.StaffBenefitRepository;
import hrms.security.Permissions;
import hrms.security.SecurityService;
import hrms.security.SessionContext;
import hrms.util.UiThread;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Assign a benefit rule to a staff member, or edit an existing assignment's
 * dates. The rate comes from the selected {@code benefit} rule - this dialog
 * never asks for an amount.
 *
 * @author aungmrat
 */
public class StaffBenefitDialog extends javax.swing.JDialog {

  private static final Logger LOG = LoggerFactory.getLogger(StaffBenefitDialog.class);

  /** null = create (assign) mode. */
  private final StaffBenefit editing;

  /** Display label → staff id for the generated {@code JComboBox<String>}. */
  private final java.util.Map<String, Long> staffIdsByLabel = new java.util.LinkedHashMap<>();

  /** Display label → benefit rule for the generated {@code JComboBox<String>}. */
  private final java.util.Map<String, Benefit> benefitByLabel = new java.util.LinkedHashMap<>();

  /** Creates a new assignment. */
  public StaffBenefitDialog(java.awt.Frame parent, boolean modal) {
    this(parent, modal, null);
  }

  /** Assign when {@code existing} is null; edit dates when non-null. */
  public StaffBenefitDialog(java.awt.Frame parent, boolean modal, StaffBenefit existing) {
    super(parent, modal);
    this.editing = existing;
    initComponents();
    setTitle(existing == null ? "Assign Benefit" : "Edit Benefit Dates");
    setupActions();
    // Breathe: GEN lays the rows flush against the window edge, so pad the
    // whole content pane (covers body rows and the footer alike).
    ((javax.swing.JComponent) getContentPane()).setBorder(
            javax.swing.BorderFactory.createEmptyBorder(10, 12, 10, 12));
    // Right-align Save/Cancel regardless of dialog width: the GEN GridBag pins
    // the buttons with fixed left insets that break when the dialog grows to
    // fit the long benefit combo labels (same override as the history dialogs).
    footer.setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 8, 7));
    footer.setPreferredSize(new java.awt.Dimension(400, 40));
    // The GEN code adds Save before Cancel; FlowLayout follows add order, so
    // put Cancel first to render the usual [Cancel] [Save] order.
    footer.setComponentZOrder(btnCancel, 0);
    comboStaff.removeAllItems();
    comboBenefit.removeAllItems();
    loadOptions();
    pack();
  }

  /** Combo label for one benefit rule: "Name — 100,000.00/Monthly (Flat)". */
  private static String benefitLabel(Benefit benefit) {
    return benefit.getName() + " \u2014 " + money(benefit.getRate())
            + "/" + benefit.getFrequency() + " (" + benefit.getUnit() + ")";
  }

  /** Both combo lists loaded in one background task. */
  private record Options(java.util.List<StaffBenefitRepository.StaffOption> staff,
          java.util.List<Benefit> benefits) {
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

  private void loadOptions() {
    UiThread.executeAsync("Load assign options", () -> {
      java.util.List<Benefit> benefits = new java.util.ArrayList<>();
      for (Benefit benefit : BenefitRepository.listAll()) {
        if (benefit.isActive() && !benefit.isDeleted()) {
          benefits.add(benefit);
        }
      }
      return new Options(StaffBenefitRepository.listStaffOptions(), benefits);
    }, options -> {
      staffIdsByLabel.clear();
      benefitByLabel.clear();
      comboStaff.removeAllItems();
      for (StaffBenefitRepository.StaffOption staff : options.staff()) {
        String label = staff.toString();
        staffIdsByLabel.put(label, staff.id());
        comboStaff.addItem(label);
      }
      comboBenefit.removeAllItems();
      for (Benefit benefit : options.benefits()) {
        String label = benefitLabel(benefit);
        benefitByLabel.put(label, benefit);
        comboBenefit.addItem(label);
      }
      if (editing != null) {
        prefill();
      }
      // Size to the loaded combo labels once - before this the dialog was
      // packed with empty combos and the benefit label overflowed the width.
      pack();
    }, error -> ErrorHandler.handle(this, error));
  }

  /** Selects the assignment's staff/benefit/dates and locks the pickers. */
  private void prefill() {
    for (java.util.Map.Entry<String, Long> entry : staffIdsByLabel.entrySet()) {
      if (entry.getValue() == editing.getStaffId()) {
        comboStaff.setSelectedItem(entry.getKey());
        break;
      }
    }
    for (java.util.Map.Entry<String, Benefit> entry : benefitByLabel.entrySet()) {
      if (entry.getValue().getId() == editing.getBenefitId()) {
        comboBenefit.setSelectedItem(entry.getKey());
        break;
      }
    }
    if (editing.getEffectiveDate() != null) {
      dcEffectiveDate.setDate(java.sql.Date.valueOf(editing.getEffectiveDate()));
    }
    if (editing.getEndDate() != null) {
      dcEndDate.setDate(java.sql.Date.valueOf(editing.getEndDate()));
    }
    comboStaff.setEnabled(false);
    comboBenefit.setEnabled(false);
  }

  private static String money(java.math.BigDecimal value) {
    if (value == null) {
      return "\u2014";
    }
    return new java.text.DecimalFormat("#,##0.00",
            new java.text.DecimalFormatSymbols(java.util.Locale.ROOT)).format(value);
  }

  /** Permission gate for Save. */
  private static void requireSavePermission(boolean isEdit) {
    SecurityService.require(isEdit
            ? Permissions.STAFF_BENEFIT_UPDATE : Permissions.STAFF_BENEFIT_ASSIGN);
  }

  private void saveAndClose() {
    try {
      boolean isEdit = editing != null;
      requireSavePermission(isEdit);

      java.util.List<String> errors = new java.util.ArrayList<>();
      Long staffId = null;
      Benefit benefit = null;
      if (!isEdit) {
        if (comboStaff.getSelectedItem() instanceof String label
                && staffIdsByLabel.containsKey(label)) {
          staffId = staffIdsByLabel.get(label);
        } else {
          errors.add("Staff is required.");
        }
        if (comboBenefit.getSelectedItem() instanceof String label
                && benefitByLabel.containsKey(label)) {
          benefit = benefitByLabel.get(label);
        } else {
          errors.add("Benefit is required.");
        }
      }

      java.time.LocalDate effective = toLocalDate(dcEffectiveDate.getDate());
      java.time.LocalDate end = toLocalDate(dcEndDate.getDate());
      if (effective == null) {
        errors.add("Effective date is required.");
      }
      if (effective != null && end != null && end.isBefore(effective)) {
        errors.add("End date cannot be before effective date.");
      }

      if (!errors.isEmpty()) {
        throw new ValidationException(errors);
      }

      Long userId = SessionContext.isAuthenticated() ? SessionContext.currentUserId() : null;
      final Long assignStaffId = staffId;
      final Benefit assignBenefit = benefit;
      btnSave.setEnabled(false);
      if (isEdit) {
        UiThread.executeAsync("Update benefit assignment",
            () -> {
              if (end == null && StaffBenefitRepository.openRowExists(
                      editing.getStaffId(), editing.getBenefitId(), editing.getId())) {
                throw new hrms.exception.BusinessException(
                        "Another open assignment of this benefit already exists for the staff.");
              }
              return StaffBenefitRepository.updateDates(
                      editing.getId(), effective, end, userId);
            },
            ok -> {
              LOG.info("Benefit assignment updated id={}", editing.getId());
              dispose();
            },
            error -> {
              btnSave.setEnabled(true);
              ErrorHandler.handle(this, error);
            });
      } else {
        UiThread.executeAsync("Assign benefit",
            () -> {
              if (StaffBenefitRepository.openRowExists(
                      assignStaffId, assignBenefit.getId(), -1L)) {
                throw new hrms.exception.BusinessException(
                        "This benefit is already assigned to the staff. End the current assignment first.");
              }
              StaffBenefit row = new StaffBenefit();
              row.setStaffId(assignStaffId);
              row.setBenefitId(assignBenefit.getId());
              row.setEffectiveDate(effective);
              row.setEndDate(end);
              row.setCreatedBy(userId);
              return StaffBenefitRepository.insert(row);
            },
            id -> {
              LOG.info("Benefit assigned id {} for staff {}", id, assignStaffId);
              dispose();
            },
            error -> {
              btnSave.setEnabled(true);
              ErrorHandler.handle(this, error);
            });
      }
    } catch (ValidationException e) {
      ErrorHandler.handle(this, e);
    }
  }

  /** JDateChooser may hand back java.sql.Date or java.util.Date. */
  private static java.time.LocalDate toLocalDate(java.util.Date date) {
    if (date == null) {
      return null;
    }
    if (date instanceof java.sql.Date sqlDate) {
      return sqlDate.toLocalDate();
    }
    java.util.Calendar calendar = java.util.Calendar.getInstance();
    calendar.setTime(date);
    return java.time.LocalDate.of(calendar.get(java.util.Calendar.YEAR),
            calendar.get(java.util.Calendar.MONTH) + 1,
            calendar.get(java.util.Calendar.DAY_OF_MONTH));
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
    labelStaff = new javax.swing.JLabel();
    comboStaff = new javax.swing.JComboBox<>();
    labelBenefit = new javax.swing.JLabel();
    comboBenefit = new javax.swing.JComboBox<>();
    labelEffectiveDate = new javax.swing.JLabel();
    dcEffectiveDate = new com.toedter.calendar.JDateChooser();
    labelEndDate = new javax.swing.JLabel();
    dcEndDate = new com.toedter.calendar.JDateChooser();

    setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);

    footer.setPreferredSize(new java.awt.Dimension(400, 60));
    footer.setRequestFocusEnabled(false);
    footer.setLayout(new java.awt.GridBagLayout());

    btnSave.setText("Save");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
    gridBagConstraints.insets = new java.awt.Insets(17, 6, 16, 40);
    footer.add(btnSave, gridBagConstraints);

    btnCancel.setText("Cancel");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
    gridBagConstraints.insets = new java.awt.Insets(17, 202, 16, 0);
    footer.add(btnCancel, gridBagConstraints);

    getContentPane().add(footer, java.awt.BorderLayout.PAGE_END);

    body.setLayout(new java.awt.GridBagLayout());

    labelStaff.setText("Staff");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(6, 0, 6, 17);
    body.add(labelStaff, gridBagConstraints);

    comboStaff.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(6, 0, 6, 0);
    body.add(comboStaff, gridBagConstraints);

    labelBenefit.setText("Benefit");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(6, 0, 6, 17);
    body.add(labelBenefit, gridBagConstraints);

    comboBenefit.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(6, 0, 6, 0);
    body.add(comboBenefit, gridBagConstraints);

    labelEffectiveDate.setText("Effective Date");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(6, 0, 6, 17);
    body.add(labelEffectiveDate, gridBagConstraints);

    dcEffectiveDate.setPreferredSize(new java.awt.Dimension(200, 26));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.insets = new java.awt.Insets(6, 0, 6, 0);
    body.add(dcEffectiveDate, gridBagConstraints);

    labelEndDate.setText("End Date");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(6, 0, 6, 17);
    body.add(labelEndDate, gridBagConstraints);

    dcEndDate.setPreferredSize(new java.awt.Dimension(200, 26));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.insets = new java.awt.Insets(6, 0, 6, 0);
    body.add(dcEndDate, gridBagConstraints);

    getContentPane().add(body, java.awt.BorderLayout.CENTER);

    pack();
  }// </editor-fold>//GEN-END:initComponents

  // Variables declaration - do not modify//GEN-BEGIN:variables
  private javax.swing.JPanel body;
  private javax.swing.JButton btnCancel;
  private javax.swing.JButton btnSave;
  private javax.swing.JComboBox<String> comboBenefit;
  private javax.swing.JComboBox<String> comboStaff;
  private com.toedter.calendar.JDateChooser dcEffectiveDate;
  private com.toedter.calendar.JDateChooser dcEndDate;
  private javax.swing.JPanel footer;
  private javax.swing.JLabel labelBenefit;
  private javax.swing.JLabel labelEffectiveDate;
  private javax.swing.JLabel labelEndDate;
  private javax.swing.JLabel labelStaff;
  // End of variables declaration//GEN-END:variables
}
