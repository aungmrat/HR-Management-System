package hrms.ui.leavemanagement;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;

import hrms.model.GazettedHoliday;
import hrms.ui.theme.Palette;
import hrms.ui.theme.ThemeManager;

/**
 * Sun-first month grid of holidays in the hc.png style: weekday header plus
 * day cells carrying one rounded red pill per holiday covering that date.
 * Header/picker/nav live on the owning screen - this is grid only. Owners
 * supply the visible month's holidays and opt into holiday/day clicks (no
 * handlers = read-only). Any click inside a day that has holidays opens
 * edit for the first covering holiday (pill miss included); only empty
 * days trigger the create handler.
 *
 * @author aungmrat
 */
public class HolidayCalendarView extends javax.swing.JPanel {

  private static final String[] WEEKDAYS = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};

  private int year;
  private int month;
  private List<GazettedHoliday> holidays = List.of();

  private java.util.function.Consumer<GazettedHoliday> onHolidayClick;
  private java.util.function.Consumer<LocalDate> onDayClick;

  private final JPanel weekdayPanel = new JPanel(new GridLayout(1, 7, 1, 1));
  private final JPanel gridPanel = new JPanel(new GridLayout(0, 7, 1, 1));

  private final java.util.function.Consumer<ThemeManager.Theme> themeListener =
          theme -> rebuild();

  /** Opens on the current month. */
  public HolidayCalendarView() {
    super(new BorderLayout(0, 6));
    setBorder(BorderFactory.createEmptyBorder(4, 60, 8, 60));

    LocalDate today = LocalDate.now();
    this.year = today.getYear();
    this.month = today.getMonthValue();

    add(weekdayPanel, BorderLayout.NORTH);
    add(gridPanel, BorderLayout.CENTER);

    rebuild();
    ThemeManager.addListener(themeListener);
  }

  /** Detach the theme listener with the component's hierarchy. */
  @Override
  public void removeNotify() {
    super.removeNotify();
    ThemeManager.removeListener(themeListener);
  }

  /** Switches the visible month (data reload is the owner's job). */
  public void showMonth(int year, int month) {
    if (year < 1970 || month < 1 || month > 12) {
      return;
    }
    this.year = year;
    this.month = month;
    rebuild();
  }

  /** Replaces the visible month's holidays and repaints the grid. */
  public void setHolidays(List<GazettedHoliday> holidays) {
    this.holidays = holidays == null ? List.of() : List.copyOf(holidays);
    rebuild();
  }

  public int getYear() {
    return year;
  }

  public int getMonth() {
    return month;
  }

  /** Edit entry: pill click or any click inside a holiday's day cell. Unset = display-only. */
  public void setOnHolidayClick(java.util.function.Consumer<GazettedHoliday> handler) {
    this.onHolidayClick = handler;
    rebuild();
  }

  /** Quick-create entry: click an empty day (holiday days route to edit). Unset = display-only. */
  public void setOnDayClick(java.util.function.Consumer<LocalDate> handler) {
    this.onDayClick = handler;
    rebuild();
  }

  private void rebuild() {
    rebuildWeekdayHeader();

    gridPanel.setBackground(Palette.color(Palette.Role.CARD_BORDER));
    weekdayPanel.setBackground(Palette.color(Palette.Role.CARD_BORDER));

    gridPanel.removeAll();
    LocalDate first = LocalDate.of(year, month, 1);
    int lead = first.getDayOfWeek().getValue() % 7;
    int daysInMonth = first.lengthOfMonth();
    int cells = ((lead + daysInMonth + 6) / 7) * 7;
    for (int index = 0; index < cells; index++) {
      int day = index - lead + 1;
      if (day < 1 || day > daysInMonth) {
        gridPanel.add(buildBlankCell());
      } else {
        gridPanel.add(buildDayCell(LocalDate.of(year, month, day)));
      }
    }
    gridPanel.revalidate();
    gridPanel.repaint();
  }

  private void rebuildWeekdayHeader() {
    weekdayPanel.removeAll();
    Font font = weekdayPanel.getFont().deriveFont(Font.BOLD, 12f);
    for (String name : WEEKDAYS) {
      JLabel label = new JLabel(name, JLabel.CENTER);
      label.setOpaque(true);
      label.setFont(font);
      label.setForeground(Palette.color(Palette.Role.TEXT_MUTED));
      label.setBackground(Palette.color(Palette.Role.CARD_BG));
      label.setBorder(BorderFactory.createEmptyBorder(6, 4, 6, 4));
      weekdayPanel.add(label);
    }
    weekdayPanel.revalidate();
    weekdayPanel.repaint();
  }

  private JPanel buildBlankCell() {
    JPanel cell = new JPanel();
    cell.setBackground(Palette.color(Palette.Role.CARD_BG));
    cell.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
    return cell;
  }

  private JPanel buildDayCell(LocalDate date) {
    boolean today = date.equals(LocalDate.now());
    boolean weekend = date.getDayOfWeek().getValue() >= 6;
    JPanel cell = new JPanel(new BorderLayout(0, 4));
    cell.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
    cell.setBackground(today ? Palette.accentSoft()
            : weekend ? Palette.color(Palette.Role.SURFACE_ALT)
                    : Palette.color(Palette.Role.CARD_BG));

    JLabel dayLabel = new JLabel(String.valueOf(date.getDayOfMonth()));
    dayLabel.setFont(dayLabel.getFont().deriveFont(
            today ? Font.BOLD : Font.PLAIN, 13f));
    dayLabel.setForeground(today ? Palette.color(Palette.Role.ACCENT)
            : Palette.color(Palette.Role.TEXT_MUTED));
    cell.add(dayLabel, BorderLayout.NORTH);

    List<GazettedHoliday> covering = holidaysOn(date);
    JPanel chips = new JPanel();
    chips.setOpaque(false);
    chips.setLayout(new BoxLayout(chips, BoxLayout.Y_AXIS));
    for (GazettedHoliday holiday : covering) {
      chips.add(new HolidayChip(holiday));
    }
    cell.add(chips, BorderLayout.CENTER);

    if (onDayClick != null || onHolidayClick != null) {
      cell.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      cell.addMouseListener(new MouseAdapter() {
        @Override
        public void mouseClicked(MouseEvent e) {
          if (e.getClickCount() != 1) {
            return;
          }
          if (!covering.isEmpty() && onHolidayClick != null) {
            onHolidayClick.accept(covering.get(0));
          } else if (onDayClick != null) {
            onDayClick.accept(date);
          }
        }
      });
    }
    return cell;
  }

  private List<GazettedHoliday> holidaysOn(LocalDate date) {
    List<GazettedHoliday> covering = new ArrayList<>(2);
    for (GazettedHoliday holiday : holidays) {
      if (holiday.covers(date)) {
        covering.add(holiday);
      }
    }
    return covering;
  }

  /** Rounded pastel-red pill for one holiday day (hc.png chip). */
  private final class HolidayChip extends JLabel {

    private final GazettedHoliday holiday;

    HolidayChip(GazettedHoliday holiday) {
      super(holiday.getTitle());
      this.holiday = holiday;
      setAlignmentX(Component.LEFT_ALIGNMENT);
      setOpaque(false);
      setForeground(Palette.color(Palette.Role.DANGER));
      setFont(getFont().deriveFont(Font.BOLD, 11f));
      setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
      setToolTipText(rangeText());
      if (onHolidayClick != null) {
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        addMouseListener(new MouseAdapter() {
          @Override
          public void mouseClicked(MouseEvent e) {
            if (e.getClickCount() != 1) {
              return;
            }
            onHolidayClick.accept(holiday);
          }
        });
      }
    }

    private String rangeText() {
      String title = holiday.getTitle() == null ? "" : holiday.getTitle();
      if (holiday.getStartDate() == null) {
        return title;
      }
      String range = holiday.getEndDate() != null && !holiday.getEndDate().equals(holiday.getStartDate())
              ? holiday.getStartDate() + " \u2248 " + holiday.getEndDate()
              : holiday.getStartDate().toString();
      return title + " (" + range + ")";
    }

    @Override
    protected void paintComponent(Graphics g) {
      Graphics2D g2 = (Graphics2D) g.create();
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
              RenderingHints.VALUE_ANTIALIAS_ON);
      g2.setColor(Palette.statCardBackground(Palette.Role.DANGER));
      g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
      super.paintComponent(g2);
      g2.dispose();
    }
  }
}
