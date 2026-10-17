/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/GUIForms/JPanel.java to edit this template
 */
package hrms.ui.leavemanagement;

import java.time.LocalDate;

import javax.swing.SpinnerNumberModel;

import hrms.exception.AuthorizationException;
import hrms.exception.ErrorHandler;
import hrms.repository.GazettedHolidayRepository;
import hrms.security.Permissions;
import hrms.security.SecurityService;
import hrms.util.UiThread;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The app's single holiday calendar: hc.png-style month grid driven by the
 * header picker and prev/today/next buttons. Clicks are interactive only for
 * GAZETTED_HOLIDAY_VIEW holders (pill = edit, empty day = create); everyone
 * else gets a read-only view with tooltips.
 *
 * @author aungmrat
 */
public class HolidayCalendar extends javax.swing.JPanel {

  private static final Logger LOG = LoggerFactory.getLogger(HolidayCalendar.class);

  private static final String[] MONTHS = {
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December"
  };

  /** Injected into the GEN body at runtime (grid only - header lives in the form). */
  private HolidayCalendarView calendarView;

  /** Footer count line, added to the GEN footer panel at runtime. */
  private javax.swing.JLabel labelFooter;

  /** True while nav syncs the header picker (suppresses reload loops). */
  private boolean syncingMonth;

  /**
   * Creates new form HolidayCalendar
   */
  public HolidayCalendar() {
    initComponents();
    fillMonths();
    setupCalendar();
    setupActions();
    refreshTable();
  }

  private void fillMonths() {
    comboMonth.removeAllItems();
    for (String month : MONTHS) {
      comboMonth.addItem(month);
    }
  }

  private void setupCalendar() {
    LocalDate now = LocalDate.now();
    spinnerYear.setModel(new SpinnerNumberModel(now.getYear(), 2020, 2100, 1));
    calendarView = new HolidayCalendarView();
    calendarView.showMonth(now.getYear(), now.getMonthValue());

    syncingMonth = true;
    comboMonth.setSelectedIndex(now.getMonthValue() - 1);
    syncingMonth = false;

    // Interactive only for holiday managers; staff keep the read-only grid.
    if (SecurityService.can(Permissions.GAZETTED_HOLIDAY_VIEW)) {
      calendarView.setOnHolidayClick(holiday ->
          runGuarded(() -> openEditDialog(holiday.getId())));
      calendarView.setOnDayClick(date ->
          runGuarded(() -> openCreateDialog(date)));
    }

    body.setLayout(new java.awt.BorderLayout());
    body.add(calendarView, java.awt.BorderLayout.CENTER);

    labelFooter = new javax.swing.JLabel(" ");
    labelFooter.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 60, 0, 60));
    footer.setLayout(new java.awt.BorderLayout());
    footer.add(labelFooter, java.awt.BorderLayout.CENTER);
  }

  private void setupActions() {
    comboMonth.addActionListener(e -> onPickerChanged());
    spinnerYear.addChangeListener(e -> onPickerChanged());
    btnPrevi.addActionListener(e -> shiftMonth(-1));
    btnNext.addActionListener(e -> shiftMonth(1));
    btnToday.addActionListener(e -> {
      LocalDate now = LocalDate.now();
      applyMonth(now.getYear(), now.getMonthValue());
    });
  }

  private void onPickerChanged() {
    if (syncingMonth) {
      return;
    }
    int month = comboMonth.getSelectedIndex() + 1;
    if (month < 1) {
      return;
    }
    applyMonth(((Number) spinnerYear.getValue()).intValue(), month);
  }

  private void shiftMonth(int delta) {
    LocalDate moved = LocalDate.of(calendarView.getYear(), calendarView.getMonth(), 1)
            .plusMonths(delta);
    applyMonth(moved.getYear(), moved.getMonthValue());
  }

  private void applyMonth(int year, int month) {
    syncingMonth = true;
    comboMonth.setSelectedIndex(month - 1);
    spinnerYear.setValue(year);
    syncingMonth = false;
    calendarView.showMonth(year, month);
    load();
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

  private void openCreateDialog(LocalDate date) {
    GazettedHolidayDialog dialog = new GazettedHolidayDialog(ownerFrame(), true);
    dialog.setPrefillDate(date);
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

  private void load() {
    int year = calendarView.getYear();
    int month = calendarView.getMonth();
    LocalDate from = LocalDate.of(year, month, 1);
    LocalDate to = from.withDayOfMonth(from.lengthOfMonth());
    UiThread.executeAsync("Load holiday calendar",
        () -> GazettedHolidayRepository.findRange(from, to),
        holidays -> {
          calendarView.setHolidays(holidays);
          labelFooter.setText(holidays.size() == 1
                  ? "1 holiday" : holidays.size() + " holidays");
        },
        ex -> LOG.warn("Cannot load the holiday calendar for {}-{}", year, month, ex));
  }

  /** Refresh hook for MainFrame navigation. */
  public void refreshTable() {
    load();
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
    labelMonth = new javax.swing.JLabel();
    comboMonth = new javax.swing.JComboBox<>();
    labelYear = new javax.swing.JLabel();
    spinnerYear = new javax.swing.JSpinner();
    btnPrevi = new javax.swing.JButton();
    btnToday = new javax.swing.JButton();
    btnNext = new javax.swing.JButton();
    footer = new javax.swing.JPanel();
    body = new javax.swing.JPanel();

    setLayout(new java.awt.BorderLayout());

    header.setPreferredSize(new java.awt.Dimension(1237, 60));

    labelMonth.setText("Month:");

    comboMonth.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));

    labelYear.setText("Year:");

    btnPrevi.setText("<");

    btnToday.setText("Today");

    btnNext.setText(">");

    javax.swing.GroupLayout headerLayout = new javax.swing.GroupLayout(header);
    header.setLayout(headerLayout);
    headerLayout.setHorizontalGroup(
      headerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGroup(headerLayout.createSequentialGroup()
        .addGap(60, 60, 60)
        .addComponent(labelMonth)
        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
        .addComponent(comboMonth, javax.swing.GroupLayout.PREFERRED_SIZE, 86, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(60, 60, 60)
        .addComponent(labelYear)
        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
        .addComponent(spinnerYear, javax.swing.GroupLayout.PREFERRED_SIZE, 95, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        .addComponent(btnPrevi)
        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
        .addComponent(btnToday)
        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
        .addComponent(btnNext)
        .addGap(60, 60, 60))
    );
    headerLayout.setVerticalGroup(
      headerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGroup(headerLayout.createSequentialGroup()
        .addContainerGap(18, Short.MAX_VALUE)
        .addGroup(headerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
          .addComponent(labelMonth)
          .addComponent(comboMonth, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
          .addComponent(labelYear)
          .addComponent(spinnerYear, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
          .addComponent(btnPrevi)
          .addComponent(btnToday)
          .addComponent(btnNext))
        .addGap(15, 15, 15))
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

    javax.swing.GroupLayout bodyLayout = new javax.swing.GroupLayout(body);
    body.setLayout(bodyLayout);
    bodyLayout.setHorizontalGroup(
      bodyLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGap(0, 1237, Short.MAX_VALUE)
    );
    bodyLayout.setVerticalGroup(
      bodyLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGap(0, 740, Short.MAX_VALUE)
    );

    add(body, java.awt.BorderLayout.CENTER);
  }// </editor-fold>//GEN-END:initComponents


  // Variables declaration - do not modify//GEN-BEGIN:variables
  private javax.swing.JPanel body;
  private javax.swing.JButton btnNext;
  private javax.swing.JButton btnPrevi;
  private javax.swing.JButton btnToday;
  private javax.swing.JComboBox<String> comboMonth;
  private javax.swing.JPanel footer;
  private javax.swing.JPanel header;
  private javax.swing.JLabel labelMonth;
  private javax.swing.JLabel labelYear;
  private javax.swing.JSpinner spinnerYear;
  // End of variables declaration//GEN-END:variables
}
