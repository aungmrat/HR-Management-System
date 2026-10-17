/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/GUIForms/JDialog.java to edit this template
 */
package hrms.ui.leavemanagement;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import hrms.exception.AuthorizationException;
import hrms.exception.ErrorHandler;
import hrms.exception.ValidationException;
import hrms.model.GazettedHoliday;
import hrms.repository.GazettedHolidayRepository;
import hrms.security.SessionContext;
import hrms.util.UiThread;

/**
 * Create/edit dialog for one gazetted holiday. A blank End Date saves as a
 * one-day holiday (copied from Start Date). Recurring is intentionally absent
 * from the UI: Myanmar holidays move yearly, so each year's dates are
 * explicit rows.
 *
 * @author aungmrat
 */
public class GazettedHolidayDialog extends javax.swing.JDialog {

  private static final Logger LOG = LoggerFactory.getLogger(GazettedHolidayDialog.class);

  private static final java.util.logging.Logger logger = java.util.logging.Logger.getLogger(GazettedHolidayDialog.class.getName());

  /** Row being edited; null = create mode. */
  private GazettedHoliday editing;

  /** Optional quick-create date (calendar day click), applied before showing. */
  private LocalDate prefillDate;

  /** Creates a new gazetted holiday. */
  public GazettedHolidayDialog(java.awt.Frame parent, boolean modal) {
    this(parent, modal, null);
  }

  /** Creates or edits one holiday; null {@code holiday} = create mode. */
  public GazettedHolidayDialog(java.awt.Frame parent, boolean modal,
          GazettedHoliday holiday) {
    super(parent, modal);
    this.editing = holiday;
    initComponents();
    setupForm();
    setupActions();
    // The GEN pack() runs on empty fields; re-pack so setColumns(24) and the
    // prefill values size the dialog (otherwise the visible layout collapses
    // title/date fields and squeezes Remark to a scrollbar sliver).
    pack();
  }

  /** Prefills Start/End with one date (one-day holiday) in create mode. */
  public void setPrefillDate(LocalDate date) {
    this.prefillDate = date;
    if (date != null && editing == null) {
      dcStartDate.setDate(java.sql.Date.valueOf(date));
      dcEndDate.setDate(null);
      pack();
    }
  }

  private void setupForm() {
    comboStatus.removeAllItems();
    comboStatus.addItem("Active");
    comboStatus.addItem("Inactive");
    comboStatus.setSelectedItem("Active");
    textTitle.setColumns(24);
    if (editing == null) {
      setTitle("New Gazetted Holiday");
      if (prefillDate == null) {
        dcStartDate.setDate(java.sql.Date.valueOf(LocalDate.now()));
      }
    } else {
      setTitle("Edit Holiday - " + (editing.getTitle() == null ? "" : editing.getTitle()));
      prefill(editing);
    }
  }

  private void prefill(GazettedHoliday holiday) {
    textTitle.setText(holiday.getTitle() == null ? "" : holiday.getTitle());
    if (holiday.getStartDate() != null) {
      dcStartDate.setDate(java.sql.Date.valueOf(holiday.getStartDate()));
    }
    if (holiday.getEndDate() != null) {
      dcEndDate.setDate(java.sql.Date.valueOf(holiday.getEndDate()));
    }
    comboStatus.setSelectedItem(
            "Inactive".equalsIgnoreCase(holiday.getStatus()) ? "Inactive" : "Active");
    textRemark.setText(holiday.getRemark() == null ? "" : holiday.getRemark());
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

  private void saveAndClose() {
    try {
      boolean isEdit = editing != null;
      List<String> errors = new ArrayList<>();

      String title = textTitle.getText() == null ? "" : textTitle.getText().trim();
      if (title.isEmpty()) {
        errors.add("Title is required.");
      } else if (title.length() > 200) {
        errors.add("Title must be at most 200 characters.");
      }

      LocalDate start = toLocalDate(dcStartDate.getDate());
      LocalDate end = toLocalDate(dcEndDate.getDate());
      if (start == null) {
        errors.add("Start date is required.");
      }
      if (end == null) {
        end = start; // Blank End Date = one-day holiday.
      } else if (start != null && end.isBefore(start)) {
        errors.add("End date cannot be before start date.");
      }

      String remark = textRemark.getText() == null ? "" : textRemark.getText().trim();
      if (remark.length() > 500) {
        errors.add("Remark must be at most 500 characters.");
      }

      String status = comboStatus.getSelectedItem() == null
              ? "Active" : comboStatus.getSelectedItem().toString();

      if (!errors.isEmpty()) {
        throw new ValidationException(errors);
      }

      Long userId = SessionContext.isAuthenticated() ? SessionContext.currentUserId() : null;
      GazettedHoliday holiday;
      if (isEdit) {
        holiday = editing;
        holiday.setTitle(title);
        holiday.setStartDate(start);
        holiday.setEndDate(end);
        holiday.setRemark(remark.isEmpty() ? null : remark);
        holiday.setStatus(status);
        holiday.setModifiedBy(userId);
      } else {
        holiday = GazettedHoliday.create(start, end, title,
                remark.isEmpty() ? null : remark, false, status, userId);
      }

      btnSave.setEnabled(false);
      UiThread.executeAsync(isEdit ? "Update gazetted holiday" : "Save gazetted holiday",
              () -> isEdit
                      ? GazettedHolidayRepository.update(holiday)
                      : GazettedHolidayRepository.insert(holiday),
              result -> {
                if (isEdit) {
                  LOG.info("Gazetted holiday updated id={}", holiday.getId());
                } else {
                  LOG.info("Gazetted holiday saved with id {}", result);
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

  /** JDateChooser may hand back java.sql.Date or java.util.Date. */
  private static LocalDate toLocalDate(java.util.Date date) {
    if (date == null) {
      return null;
    }
    if (date instanceof java.sql.Date sqlDate) {
      return sqlDate.toLocalDate();
    }
    java.util.Calendar calendar = java.util.Calendar.getInstance();
    calendar.setTime(date);
    return LocalDate.of(calendar.get(java.util.Calendar.YEAR),
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

    jPanel1 = new javax.swing.JPanel();
    btnSave = new javax.swing.JButton();
    btnCancel = new javax.swing.JButton();
    body = new javax.swing.JPanel();
    labelTitle = new javax.swing.JLabel();
    textTitle = new javax.swing.JTextField();
    labelStartDate = new javax.swing.JLabel();
    dcStartDate = new com.toedter.calendar.JDateChooser();
    labelEndDate = new javax.swing.JLabel();
    dcEndDate = new com.toedter.calendar.JDateChooser();
    labelStatus = new javax.swing.JLabel();
    comboStatus = new javax.swing.JComboBox<>();
    labelRemark = new javax.swing.JLabel();
    jScrollPane1 = new javax.swing.JScrollPane();
    textRemark = new javax.swing.JTextArea();

    setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);

    jPanel1.setPreferredSize(new java.awt.Dimension(400, 60));
    jPanel1.setLayout(new java.awt.GridBagLayout());

    btnSave.setText("Save");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
    gridBagConstraints.insets = new java.awt.Insets(18, 12, 15, 60);
    jPanel1.add(btnSave, gridBagConstraints);

    btnCancel.setText("Cancel");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
    gridBagConstraints.insets = new java.awt.Insets(18, 176, 15, 0);
    jPanel1.add(btnCancel, gridBagConstraints);

    getContentPane().add(jPanel1, java.awt.BorderLayout.PAGE_END);

    body.setLayout(new java.awt.GridBagLayout());

    labelTitle.setText("Title");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(7, 0, 3, 20);
    body.add(labelTitle, gridBagConstraints);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(7, 0, 3, 0);
    body.add(textTitle, gridBagConstraints);

    labelStartDate.setText("Start Date");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(7, 0, 3, 20);
    body.add(labelStartDate, gridBagConstraints);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(7, 0, 3, 0);
    body.add(dcStartDate, gridBagConstraints);

    labelEndDate.setText("End Date");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(7, 0, 3, 20);
    body.add(labelEndDate, gridBagConstraints);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(7, 0, 3, 0);
    body.add(dcEndDate, gridBagConstraints);

    labelStatus.setText("Status");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(7, 0, 3, 20);
    body.add(labelStatus, gridBagConstraints);

    comboStatus.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(7, 0, 3, 0);
    body.add(comboStatus, gridBagConstraints);

    labelRemark.setText("Remark");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 5;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHEAST;
    gridBagConstraints.insets = new java.awt.Insets(7, 0, 3, 20);
    body.add(labelRemark, gridBagConstraints);

    textRemark.setColumns(20);
    textRemark.setRows(5);
    jScrollPane1.setViewportView(textRemark);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 5;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(7, 0, 3, 0);
    body.add(jScrollPane1, gridBagConstraints);

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
      logger.log(java.util.logging.Level.SEVERE, null, ex);
    }
    //</editor-fold>

    /* Create and display the dialog */
    java.awt.EventQueue.invokeLater(new Runnable() {
      @Override
      public void run() {
        GazettedHolidayDialog dialog = new GazettedHolidayDialog(new javax.swing.JFrame(), true);
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
  private javax.swing.JComboBox<String> comboStatus;
  private com.toedter.calendar.JDateChooser dcEndDate;
  private com.toedter.calendar.JDateChooser dcStartDate;
  private javax.swing.JPanel jPanel1;
  private javax.swing.JScrollPane jScrollPane1;
  private javax.swing.JLabel labelEndDate;
  private javax.swing.JLabel labelRemark;
  private javax.swing.JLabel labelStartDate;
  private javax.swing.JLabel labelStatus;
  private javax.swing.JLabel labelTitle;
  private javax.swing.JTextArea textRemark;
  private javax.swing.JTextField textTitle;
  // End of variables declaration//GEN-END:variables
}
