/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/GUIForms/JPanel.java to edit this template
 */
package hrms.ui.attendanceandroster;

import hrms.component.ErrorPanel;
import hrms.component.HrmsTable;
import hrms.component.SearchField;
import hrms.component.StatusBadge;
import hrms.exception.AuthorizationException;
import hrms.exception.ErrorHandler;
import hrms.model.Department;
import hrms.repository.DepartmentRepository;
import hrms.repository.DutyRosterRepository;
import hrms.repository.DutyRosterRepository.StaffOption;
import hrms.security.Permissions;
import hrms.security.SecurityService;
import hrms.security.SessionContext;
import hrms.ui.theme.Palette;
import hrms.ui.theme.Palette.Role;
import hrms.util.Dialogs;
import hrms.util.UiGraphics;
import hrms.util.UiThread;
import java.awt.Color;
import java.awt.Component;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author aungmrat
 */
public class DutyRoster extends javax.swing.JPanel {

  private static final Logger LOG = LoggerFactory.getLogger(DutyRoster.class);

  private static final DateTimeFormatter DAY_FORMAT =
          DateTimeFormatter.ofPattern("dd MMM");

  /** Monday of the week currently shown. */
  private LocalDate weekStart;

  /** Last loaded snapshot, kept so cell interactions can resolve ids. */
  private List<StaffOption> currentStaff = List.of();
  private List<hrms.model.DutyRoster> currentRosters = List.of();

  /**
   * Creates new form DutyRoster
   */
  public DutyRoster() {
    initComponents();
    this.weekStart = mondayOf(LocalDate.now());
    setupTables();
    setupActions();
    loadDepartments();
    refreshWeekLabel();
    loadWeek();
  }

  // ------------------------------------------------------------------
  // Week navigation
  // ------------------------------------------------------------------

  static LocalDate mondayOf(LocalDate date) {
    return date.with(java.time.DayOfWeek.MONDAY);
  }

  private void refreshWeekLabel() {
    LocalDate end = weekStart.plusDays(6);
    String text;
    if (weekStart.getYear() == end.getYear()
            && weekStart.getMonth() == end.getMonth()) {
      text = DAY_FORMAT.format(weekStart) + " – " + DAY_FORMAT.format(end)
              + " " + end.getYear();
    } else {
      text = DAY_FORMAT.format(weekStart) + " – " + DAY_FORMAT.format(end)
              + " " + end.getYear();
    }
    labelWeek.setText(text);
  }

  private void showPreviousWeek() {
    weekStart = weekStart.minusWeeks(1);
    refreshWeekLabel();
    loadWeek();
  }

  private void showNextWeek() {
    weekStart = weekStart.plusWeeks(1);
    refreshWeekLabel();
    loadWeek();
  }

  private void showToday() {
    weekStart = mondayOf(LocalDate.now());
    refreshWeekLabel();
    loadWeek();
  }

  // ------------------------------------------------------------------
  // Actions & permissions
  // ------------------------------------------------------------------

  private void setupActions() {
    btnPrevWeek.addActionListener(e -> runGuarded(this::showPreviousWeek));
    btnNextWeek.addActionListener(e -> runGuarded(this::showNextWeek));
    btnToday.addActionListener(e -> runGuarded(this::showToday));
    btnAssignShift.addActionListener(e -> runGuarded(this::openNewAssignmentDialog));
    btnAutoFill.addActionListener(e -> runGuarded(this::openAutoFillDialog));
    comboDepartment.addActionListener(e -> runGuarded(this::loadWeek));
    if (textSearch instanceof SearchField searchField) {
      searchField.onTextChanged(keyword -> runGuarded(this::loadWeek));
    }
    if (!SecurityService.can(Permissions.DUTY_ROSTER_CREATE)) {
      btnAssignShift.setEnabled(false);
      btnAssignShift.setToolTipText("Requires permission: DUTY_ROSTER_CREATE");
      btnAutoFill.setEnabled(false);
      btnAutoFill.setToolTipText("Requires permission: DUTY_ROSTER_CREATE");
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

  private void openNewAssignmentDialog() {
    SecurityService.require(Permissions.DUTY_ROSTER_CREATE);
    QuickAssignDialog dialog = new QuickAssignDialog(ownerFrame(), true, null, null, null);
    dialog.setLocationRelativeTo(this);
    dialog.setVisible(true);
    loadWeek();
  }

  private void openAssignmentDialog(hrms.model.DutyRoster roster) {
    SecurityService.require(Permissions.DUTY_ROSTER_UPDATE);
    QuickAssignDialog dialog = new QuickAssignDialog(ownerFrame(), true, roster, null, null);
    dialog.setLocationRelativeTo(this);
    dialog.setVisible(true);
    loadWeek();
  }

  /** Opens the assign dialog prefilled from a week-grid cell. */
  private void openAssignAt(long staffId, LocalDate date) {
    SecurityService.require(Permissions.DUTY_ROSTER_CREATE);
    QuickAssignDialog dialog = new QuickAssignDialog(ownerFrame(), true, null, staffId, date);
    dialog.setLocationRelativeTo(this);
    dialog.setVisible(true);
    loadWeek();
  }

  private void openAutoFillDialog() {
    SecurityService.require(Permissions.DUTY_ROSTER_CREATE);
    AutoFillDialog dialog = new AutoFillDialog(ownerFrame(), true, weekStart);
    dialog.setLocationRelativeTo(this);
    dialog.setVisible(true);
    loadWeek();
  }

  private void deleteRoster(long id, String description) {
    SecurityService.require(Permissions.DUTY_ROSTER_UPDATE);
    if (!Dialogs.confirm(this, "Remove Assignment",
        "Remove " + description + "? This is a soft delete.")) {
      return;
    }
    Long userId = SessionContext.isAuthenticated() ? SessionContext.currentUserId() : null;
    UiThread.executeAsync("Remove assignment",
        () -> DutyRosterRepository.softDelete(id, userId),
        deleted -> loadWeek());
  }

  // ------------------------------------------------------------------
  // Department filter
  // ------------------------------------------------------------------

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

  private void loadDepartments() {
    UiThread.executeAsync("Load departments",
        DepartmentRepository::listAll,
        this::fillDepartments);
  }

  private void fillDepartments(List<Department> departments) {
    comboDepartment.removeAllItems();
    comboDepartment.addItem(new ComboItem(null, "Department: All"));
    for (Department department : departments) {
      comboDepartment.addItem(new ComboItem(department.getId(),
          department.getCode() + " — " + department.getName()));
    }
  }

  private Long selectedDepartmentId() {
    ComboItem item = (ComboItem) comboDepartment.getSelectedItem();
    return item == null ? null : item.value();
  }

  // ------------------------------------------------------------------
  // Tables
  // ------------------------------------------------------------------

  /**
   * Week grid chip: shift code + derived state painted like StatusBadge.
   * {@code bgRole} tints the cell background — the shift band (AM/PM/Night)
   * for normal states, or the alarm color when the state needs attention.
   */
  private record CellChip(String code, String state, String tooltip, Role bgRole) {
  }

  /** Replaces the GEN placeholder JTable on the Table tab (same pattern as Shift). */
  private HrmsTable listTable;

  private void setupTables() {
    tableWeek.setRowHeight(34);
    tableWeek.setShowGrid(false);
    tableWeek.setFillsViewportHeight(true);
    tableWeek.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
    tableWeek.getTableHeader().setReorderingAllowed(false);
    tableWeek.setDefaultRenderer(Object.class, new ChipRenderer());
    tableWeek.addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent e) {
        handleGridPopup(e);
      }

      @Override
      public void mouseReleased(MouseEvent e) {
        handleGridPopup(e);
      }

      @Override
      public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() == 2 && e.getButton() == MouseEvent.BUTTON1) {
          runGuarded(() -> {
            CellAt cell = cellAt(e);
            if (cell == null) {
              return;
            }
            hrms.model.DutyRoster roster = rosterAt(cell.staffId(), cell.date());
            if (roster == null) {
              openAssignAt(cell.staffId(), cell.date());
            } else {
              openAssignmentDialog(roster);
            }
          });
        }
      }
    });

    listTable = HrmsTable.builder(
        "ID", "Staff", "Date", "Shift", "State", "Remark")
        .hiddenColumn(0)
        .fixedColumn(1, 220)
        .fixedColumn(2, 100)
        .fixedColumn(3, 200)
        .fixedColumn(4, 110)
        .fixedColumn(5, 180)
        .badgeColumn(4)
        .onDoubleClick((viewRow, modelRow) -> runGuarded(() -> {
          long id = ((Number) listTable.getModel().getValueAt(modelRow, 0)).longValue();
          currentRosters.stream()
              .filter(r -> r.getId() == id)
              .findFirst()
              .ifPresent(this::openAssignmentDialog);
        }))
        .contextMenu(() -> {
          JPopupMenu menu = new JPopupMenu();
          int row = listTable.selectedModelRow();
          if (row < 0) {
            return menu;
          }
          long id = ((Number) listTable.getModel().getValueAt(row, 0)).longValue();
          hrms.model.DutyRoster roster = currentRosters.stream()
              .filter(r -> r.getId() == id)
              .findFirst()
              .orElse(null);
          if (roster == null) {
            return menu;
          }
          JMenuItem editItem = new JMenuItem("Edit");
          editItem.addActionListener(e -> runGuarded(() -> openAssignmentDialog(roster)));
          JMenuItem deleteItem = new JMenuItem("Remove");
          deleteItem.addActionListener(e -> runGuarded(() -> deleteRoster(roster.getId(),
              "assignment for " + roster.getStaffName() + " on " + roster.getRosterDate())));
          menu.add(editItem);
          menu.add(deleteItem);
          return menu;
        })
        .build();
    jScrollPane2.setViewportView(listTable);
  }

  /** Resolves the grid cell under a mouse event, or null when outside. */
  private record CellAt(long staffId, LocalDate date) {
  }

  private CellAt cellAt(MouseEvent e) {
    int viewRow = tableWeek.rowAtPoint(e.getPoint());
    int viewCol = tableWeek.columnAtPoint(e.getPoint());
    if (viewRow < 0 || viewCol < 1) {
      return null;
    }
    int modelRow = tableWeek.convertRowIndexToModel(viewRow);
    int modelCol = tableWeek.convertColumnIndexToModel(viewCol);
    if (modelRow >= currentStaff.size() || modelCol - 1 >= 7) {
      return null;
    }
    return new CellAt(currentStaff.get(modelRow).id(),
        weekStart.plusDays(modelCol - 1));
  }

  private hrms.model.DutyRoster rosterAt(long staffId, LocalDate date) {
    return currentRosters.stream()
        .filter(r -> r.getStaffId() == staffId && date.equals(r.getRosterDate()))
        .findFirst()
        .orElse(null);
  }

  private void handleGridPopup(MouseEvent e) {
    if (!e.isPopupTrigger()) {
      return;
    }
    runGuarded(() -> {
      CellAt cell = cellAt(e);
      if (cell == null) {
        return;
      }
      hrms.model.DutyRoster roster = rosterAt(cell.staffId(), cell.date());
      JPopupMenu menu = new JPopupMenu();
      if (roster == null) {
        JMenuItem assignItem = new JMenuItem("Assign shift...");
        assignItem.setEnabled(SecurityService.can(Permissions.DUTY_ROSTER_CREATE));
        assignItem.addActionListener(ev -> openAssignAt(cell.staffId(), cell.date()));
        menu.add(assignItem);
      } else {
        JMenuItem editItem = new JMenuItem("Change shift...");
        editItem.setEnabled(SecurityService.can(Permissions.DUTY_ROSTER_UPDATE));
        editItem.addActionListener(ev -> openAssignmentDialog(roster));
        JMenuItem removeItem = new JMenuItem("Remove");
        removeItem.setEnabled(SecurityService.can(Permissions.DUTY_ROSTER_UPDATE));
        removeItem.addActionListener(ev -> deleteRoster(roster.getId(),
            "assignment for " + roster.getStaffName() + " on " + roster.getRosterDate()));
        menu.add(editItem);
        menu.add(removeItem);
      }
      menu.show(e.getComponent(), e.getX(), e.getY());
    });
  }

  // ------------------------------------------------------------------
  // Loading
  // ------------------------------------------------------------------

  private record WeekData(List<StaffOption> staff, List<hrms.model.DutyRoster> rosters) {
  }

  private void loadWeek() {
    // Restore the tables in case a previous load failed and showed ErrorPanel.
    jScrollPane1.setViewportView(tableWeek);
    jScrollPane2.setViewportView(listTable);
    String keyword = textSearch.getText() == null ? "" : textSearch.getText().trim();
    Long departmentId = selectedDepartmentId();
    LocalDate from = weekStart;
    LocalDate to = weekStart.plusDays(6);
    UiThread.executeAsync("Load duty roster week",
        () -> new WeekData(
            DutyRosterRepository.listActiveStaff(departmentId, keyword),
            DutyRosterRepository.findRange(from, to, departmentId, keyword)),
        this::fillWeek,
        this::showLoadError);
  }

  private void fillWeek(WeekData data) {
    currentStaff = data.staff();
    currentRosters = data.rosters();
    fillWeekGrid();
    fillListTable();
  }

  private void fillWeekGrid() {
    Map<String, hrms.model.DutyRoster> byKey = new HashMap<>();
    for (hrms.model.DutyRoster roster : currentRosters) {
      byKey.put(roster.getStaffId() + "|" + roster.getRosterDate(), roster);
    }

    Object[] columns = new Object[8];
    columns[0] = "Staff";
    for (int day = 0; day < 7; day++) {
      columns[day + 1] = DAY_FORMAT.format(weekStart.plusDays(day));
    }
    DefaultTableModel model = new DefaultTableModel(columns, 0) {
      @Override
      public boolean isCellEditable(int row, int column) {
        return false;
      }
    };
    for (StaffOption staff : currentStaff) {
      Object[] row = new Object[8];
      row[0] = staff.code() + " - " + staff.name();
      for (int day = 0; day < 7; day++) {
        LocalDate date = weekStart.plusDays(day);
        hrms.model.DutyRoster roster = byKey.get(staff.id() + "|" + date);
        if (roster == null) {
          row[day + 1] = null;
        } else {
          String code = roster.getShiftLabel() == null
              ? "" : roster.getShiftLabel().split(" ")[0];
          String state = roster.displayState();
          String tooltip = roster.getShiftLabel() + " — " + state;
          row[day + 1] = new CellChip(code, state, tooltip, chipBackground(state, roster));
        }
      }
      model.addRow(row);
    }
    tableWeek.setModel(model);
    TableColumn staffColumn = tableWeek.getColumnModel().getColumn(0);
    staffColumn.setPreferredWidth(220);
    for (int day = 1; day <= 7; day++) {
      tableWeek.getColumnModel().getColumn(day).setPreferredWidth(100);
    }
  }

  /**
   * Background role for a week-grid chip: alarm states (Missed/Absent) stay
   * red and Leave stays blue so problems read at a glance; otherwise the
   * shift band tints the cell (amber = AM, teal = PM, indigo = Night).
   */
  static Role chipBackground(String state, hrms.model.DutyRoster roster) {
    return switch (state) {
      case "Missed", "Absent" -> Role.DANGER;
      case "Leave" -> Role.INFO;
      default -> switch (roster.shiftBand()) {
        case "AM" -> Role.WARNING;
        case "NIGHT" -> Role.ACCENT;
        default -> Role.INFO;
      };
    };
  }

  private void fillListTable() {
    List<Object[]> rows = new java.util.ArrayList<>(currentRosters.size());
    for (hrms.model.DutyRoster roster : currentRosters) {
      rows.add(roster.toTableRow());
    }
    listTable.setRows(rows);
  }

  /**
   * Swaps the table area for an inline error state with retry (details go to
   * the log).
   */
  private void showLoadError(Exception ex) {
    LOG.warn("Cannot load duty roster week", ex);
    String message =
        "Cannot load the duty roster. Check that the database is running, then try again.";
    jScrollPane1.setViewportView(new ErrorPanel(message, this::loadWeek));
    jScrollPane2.setViewportView(new ErrorPanel(message, this::loadWeek));
  }

  /** Entry point wired into MainFrame's refresher map. */
  public void refreshTable() {
    loadWeek();
  }

  /**
   * Paints week-grid cells as rounded shift-code chips colored by the same
   * semantic roles StatusBadge uses.
   */
  private static final class ChipRenderer extends DefaultTableCellRenderer {

    private static final int CHIP_HEIGHT = 22;

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value,
        boolean isSelected, boolean hasFocus, int row, int column) {
      JLabel label = (JLabel) super.getTableCellRendererComponent(
          table, value, isSelected, hasFocus, row, column);
      if (value instanceof CellChip chip) {
        label.setText(chip.code());
        label.setToolTipText(chip.tooltip());
        label.setHorizontalAlignment(SwingConstants.CENTER);
        label.setOpaque(true);
        Role stateRole = StatusBadge.roleFor(chip.state());
        Color stateColor = Palette.color(stateRole);
        Color chipBg = Palette.color(chip.bgRole());
        if (isSelected) {
          // Keep a hint of the chip color but anchor to the selection colors
          // so the text stays readable (semantic red on accent blue did not).
          label.setBackground(UiGraphics.blend(
              table.getSelectionBackground(), chipBg, 0.30));
          label.setForeground(table.getSelectionForeground());
        } else {
          label.setBackground(UiGraphics.blend(
              Palette.color(Role.CARD_BG), chipBg, 0.14));
          label.setForeground(stateColor);
        }
      } else {
        // Plain-text cells (Staff column): show the value and reset the
        // CENTER alignment the chip branch sets on this shared renderer.
        label.setText(value == null ? "" : value.toString());
        label.setToolTipText(null);
        label.setHorizontalAlignment(SwingConstants.LEFT);
        label.setOpaque(true);
        label.setBackground(isSelected ? table.getSelectionBackground()
            : table.getBackground());
        label.setForeground(isSelected ? table.getSelectionForeground()
            : table.getForeground());
      }
      return label;
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

    header = new javax.swing.JPanel();
    btnAssignShift = new javax.swing.JButton();
    btnAutoFill = new javax.swing.JButton();
    textSearch =  new SearchField("Search by name or code...");
    comboDepartment = new javax.swing.JComboBox<>();
    btnToday = new javax.swing.JButton();
    btnPrevWeek = new javax.swing.JButton();
    labelWeek = new javax.swing.JLabel();
    btnNextWeek = new javax.swing.JButton();
    footer = new javax.swing.JPanel();
    body = new javax.swing.JPanel();
    jTabbedPane1 = new javax.swing.JTabbedPane();
    weekgrid = new javax.swing.JPanel();
    jScrollPane1 = new javax.swing.JScrollPane();
    tableWeek = new javax.swing.JTable();
    table = new javax.swing.JPanel();
    jScrollPane2 = new javax.swing.JScrollPane();
    tableList = new javax.swing.JTable();

    setLayout(new java.awt.BorderLayout());

    header.setPreferredSize(new java.awt.Dimension(1237, 60));

    btnAssignShift.setText("+ Assign Shift");

    btnAutoFill.setText("Auto-fill week...");

    btnToday.setText("Today");

    btnPrevWeek.setText("<");

    labelWeek.setText("05 – 11 Oct 2026");

    btnNextWeek.setText(">");

    javax.swing.GroupLayout headerLayout = new javax.swing.GroupLayout(header);
    header.setLayout(headerLayout);
    headerLayout.setHorizontalGroup(
      headerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, headerLayout.createSequentialGroup()
        .addGap(20, 20, 20)
        .addComponent(btnPrevWeek)
        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
        .addComponent(labelWeek)
        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
        .addComponent(btnNextWeek)
        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
        .addComponent(btnToday)
        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
        .addComponent(comboDepartment, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
        .addComponent(textSearch, javax.swing.GroupLayout.PREFERRED_SIZE, 200, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 365, Short.MAX_VALUE)
        .addComponent(btnAutoFill)
        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
        .addComponent(btnAssignShift)
        .addGap(60, 60, 60))
    );
    headerLayout.setVerticalGroup(
      headerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGroup(headerLayout.createSequentialGroup()
        .addGap(16, 16, 16)
        .addGroup(headerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
          .addComponent(btnAssignShift)
          .addComponent(btnAutoFill)
          .addComponent(textSearch, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
          .addComponent(comboDepartment, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
          .addComponent(btnToday)
          .addComponent(btnPrevWeek)
          .addComponent(labelWeek)
          .addComponent(btnNextWeek))
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

    tableWeek.setModel(new javax.swing.table.DefaultTableModel(
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
    jScrollPane1.setViewportView(tableWeek);

    javax.swing.GroupLayout weekgridLayout = new javax.swing.GroupLayout(weekgrid);
    weekgrid.setLayout(weekgridLayout);
    weekgridLayout.setHorizontalGroup(
      weekgridLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addComponent(jScrollPane1, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 1237, Short.MAX_VALUE)
    );
    weekgridLayout.setVerticalGroup(
      weekgridLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 705, Short.MAX_VALUE)
    );

    jTabbedPane1.addTab("Week Grid", weekgrid);

    tableList.setModel(new javax.swing.table.DefaultTableModel(
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
    jScrollPane2.setViewportView(tableList);

    javax.swing.GroupLayout tableLayout = new javax.swing.GroupLayout(table);
    table.setLayout(tableLayout);
    tableLayout.setHorizontalGroup(
      tableLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addComponent(jScrollPane2, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 1237, Short.MAX_VALUE)
    );
    tableLayout.setVerticalGroup(
      tableLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 705, Short.MAX_VALUE)
    );

    jTabbedPane1.addTab("Table", table);

    javax.swing.GroupLayout bodyLayout = new javax.swing.GroupLayout(body);
    body.setLayout(bodyLayout);
    bodyLayout.setHorizontalGroup(
      bodyLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addComponent(jTabbedPane1, javax.swing.GroupLayout.Alignment.TRAILING)
    );
    bodyLayout.setVerticalGroup(
      bodyLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addComponent(jTabbedPane1, javax.swing.GroupLayout.Alignment.TRAILING)
    );

    add(body, java.awt.BorderLayout.CENTER);
  }// </editor-fold>//GEN-END:initComponents


  // Variables declaration - do not modify//GEN-BEGIN:variables
  private javax.swing.JPanel body;
  private javax.swing.JButton btnAssignShift;
  private javax.swing.JButton btnAutoFill;
  private javax.swing.JButton btnNextWeek;
  private javax.swing.JButton btnPrevWeek;
  private javax.swing.JButton btnToday;
  private javax.swing.JComboBox<ComboItem> comboDepartment;
  private javax.swing.JPanel footer;
  private javax.swing.JPanel header;
  private javax.swing.JScrollPane jScrollPane1;
  private javax.swing.JScrollPane jScrollPane2;
  private javax.swing.JTabbedPane jTabbedPane1;
  private javax.swing.JLabel labelWeek;
  private javax.swing.JPanel table;
  private javax.swing.JTable tableList;
  private javax.swing.JTable tableWeek;
  private javax.swing.JTextField textSearch;
  private javax.swing.JPanel weekgrid;
  // End of variables declaration//GEN-END:variables
}
