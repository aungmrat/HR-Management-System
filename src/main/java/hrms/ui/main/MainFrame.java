package hrms.ui.main;

import hrms.ui.theme.ThemeManager;
import hrms.ui.theme.Palette;
import hrms.ui.theme.Palette.Role;
import hrms.component.ModernButton;
import hrms.config.ServiceRegistry;
import hrms.ui.office.Office;
import hrms.ui.dashboard.Dashboard;
import hrms.ui.department.Department;
import hrms.ui.admin_hierarchy.Position;
import hrms.ui.admin_hierarchy.Roles;
import hrms.ui.admin_hierarchy.Team;
import hrms.ui.admin_hierarchy.TeamMember;
import hrms.ui.user_management.User;
import hrms.ui.staff_management.Staff;
import hrms.security.Permissions;
import hrms.security.SessionContext;
import java.util.List;
import java.util.Set;

import hrms.util.UiGraphics;
import hrms.util.IconLoader;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import javax.swing.JScrollPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author aungmrat
 */
public class MainFrame extends javax.swing.JFrame {

  private static final Logger LOG = LoggerFactory.getLogger(MainFrame.class);

  private static final int MENU_ITEM_HEIGHT = 36;

  /** Keeps the header theme toggle in sync: moon in light, sun in dark. */
  private final java.util.function.Consumer<ThemeManager.Theme> themeToggleListener =
      this::syncThemeToggleIcon;

  /**
   * Side-menu leaf: stable navigation id (CardLayout key) + display label +
   * optional permission that controls menu visibility ({@code null} = every
   * signed-in user). Labels never drive navigation — ids do.
   */
  private record MenuLeaf(String id, String label, Permissions viewPerm) {
  }

  /** Collapsible group: display header + its visible-filtered leaves. */
  private record MenuGroup(String label, String iconKey, List<MenuLeaf> leaves) {
  }

  private static final MenuLeaf DASHBOARD_LEAF =
          new MenuLeaf("dashboard", "Dashboard", null);

  /**
   * Single source of truth for the side menu. One entry per panel; adding a
   * screen means adding a leaf here plus a wired card in setupContentPanels()
   * (unwired leaves automatically get a placeholder card).
   */
  private static final List<MenuGroup> MENU = List.of(
          new MenuGroup("Organization", "building", List.of(
                  new MenuLeaf("department", "Department", Permissions.DEPARTMENT_VIEW),
                  new MenuLeaf("office", "Office", Permissions.OFFICE_VIEW),
                  new MenuLeaf("org_tree", "Org Tree View", Permissions.ORG_TREE_VIEW))),
          new MenuGroup("Admin Hierarchy", "admin_hierarchy", List.of(
                  new MenuLeaf("role", "Role", Permissions.ROLE_VIEW),
                  new MenuLeaf("position", "Position", Permissions.POSITION_VIEW),
                  new MenuLeaf("team", "Team", Permissions.TEAM_VIEW),
                  new MenuLeaf("team_member", "Team Member", Permissions.TEAM_MEMBER_VIEW))),
          new MenuGroup("Staff Management", "employees", List.of(
                  new MenuLeaf("staff", "Staff", Permissions.STAFF_VIEW),
                  new MenuLeaf("language_skill", "Language Skill", Permissions.LANGUAGE_SKILL_VIEW),
                  new MenuLeaf("military_service", "Military Service", Permissions.MILITARY_SERVICE_VIEW),
                  new MenuLeaf("family_member", "Family Member", Permissions.FAMILY_MEMBER_VIEW))),
          new MenuGroup("Leave Management", "leave", List.of(
                  new MenuLeaf("leave_type", "Leave Type", Permissions.LEAVE_TYPE_VIEW),
                  new MenuLeaf("leave_entitlement", "Leave Entitlement", Permissions.LEAVE_ENTITLEMENT_VIEW),
                  new MenuLeaf("leave_application", "Leave Application", Permissions.LEAVE_APPLICATION_VIEW),
                  new MenuLeaf("leave_approval", "Leave Approval", Permissions.LEAVE_APPROVAL_VIEW),
                  new MenuLeaf("leave_history", "Leave History", Permissions.LEAVE_HISTORY_VIEW),
                  new MenuLeaf("gazetted_holiday", "Gazetted Holiday", Permissions.GAZETTED_HOLIDAY_VIEW),
                  new MenuLeaf("holiday_calendar", "Holiday Calendar", Permissions.HOLIDAY_CALENDAR_VIEW))),
          new MenuGroup("Compensation Policy", "settings", List.of(
                  new MenuLeaf("overtime_policy", "Overtime Policy", Permissions.OVERTIME_POLICY_VIEW),
                  new MenuLeaf("benefit", "Benefit", Permissions.BENEFIT_VIEW),
                  new MenuLeaf("allowance", "Allowance", Permissions.ALLOWANCE_VIEW),
                  new MenuLeaf("salary_rate", "Salary Rate", Permissions.SALARY_RATE_VIEW),
                  new MenuLeaf("tax", "Tax", Permissions.TAX_VIEW))),
          new MenuGroup("Payroll&Compensation", "payroll", List.of(
                  new MenuLeaf("staff_salary", "Staff Salary", Permissions.STAFF_SALARY_VIEW),
                  new MenuLeaf("staff_benefit", "Staff Benefit", Permissions.STAFF_BENEFIT_VIEW),
                  new MenuLeaf("staff_allowance", "Staff Allowance", Permissions.STAFF_ALLOWANCE_VIEW),
                  new MenuLeaf("payroll", "Payroll", Permissions.PAYROLL_VIEW),
                  new MenuLeaf("payroll_details", "Payroll Details", Permissions.PAYROLL_DETAILS_VIEW),
                  new MenuLeaf("payslip", "Payslip", Permissions.PAYSLIP_VIEW))),
          new MenuGroup("Attendance&Roster", "attendance", List.of(
                  new MenuLeaf("attendance", "Attendance", Permissions.ATTENDANCE_VIEW),
                  new MenuLeaf("shift", "Shift", Permissions.SHIFT_VIEW),
                  new MenuLeaf("duty_roster", "Duty Roster", Permissions.DUTY_ROSTER_VIEW))),
          new MenuGroup("Report", "documents", List.of(
                  new MenuLeaf("report", "Report", Permissions.REPORT_VIEW))),
          new MenuGroup("User Management", "employees", List.of(
                  new MenuLeaf("user", "User", Permissions.USER_MANAGE)))
  );

  /**
   * Leaf ids backed by a real panel; every other visible leaf gets a placeholder.
   */
  private static final Set<String> WIRED_CARDS =
          Set.of("dashboard", "department", "office", "position", "role",
                  "team", "team_member", "user", "staff", "language_skill",
                  "military_service", "family_member", "overtime_policy",
                  "benefit");

  /**
   * Cards that expose {@code refreshTable()}; invoked on navigation so a screen
   * is never shown with data from before another screen's changes.
   */
  private final java.util.Map<String, Runnable> refreshers = new java.util.HashMap<>();

  /**
   * Creates new form MainFrame
   */
  public MainFrame() {
    initComponents();
    hrms.setFont(hrms.getFont().deriveFont(Font.BOLD, 15f));
    hrms.setForeground(Palette.color(Role.TEXT));
//    
//    title.setFont(title.getFont().deriveFont(Font.BOLD, 15f));
//    title.setForeground(Palette.color(Role.TEXT));

    menuScroll.setBorder(null);
    menuScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
    menuScroll.getVerticalScrollBar().setUnitIncrement(16);
    // Slim scrollbar so the collapsed icon rail keeps clear space around it.
    menuScroll.getVerticalScrollBar().setPreferredSize(new Dimension(8, 0));
    menuScroll.setOpaque(false);
    menuScroll.getViewport().setOpaque(false);

    setupContentPanels();
    setupSidebarTree();
    setupFooter();
    syncThemeToggleIcon(ThemeManager.current());
    ThemeManager.addListener(themeToggleListener);
  }

  /** Moon offers "switch to dark"; sun offers "switch to light". */
  private void syncThemeToggleIcon(ThemeManager.Theme theme) {
    boolean dark = theme == ThemeManager.Theme.DARK;
    theme_toogle.setIcon(IconLoader.tinted(dark ? "sun" : "moon",
        IconLoader.SIZE_DEFAULT, Palette.color(Role.TEXT)));
    theme_toogle.setToolTipText(dark
        ? "Switch to light theme" : "Switch to dark theme");
  }

  /** Logout disposes this frame; drop the theme listener with it. */
  @Override
  public void removeNotify() {
    super.removeNotify();
    ThemeManager.removeListener(themeToggleListener);
  }

  private void setupContentPanels() {
    content_panel.add(new Dashboard(), "dashboard");
    Department department = new Department();
    Office office = new Office();
    Position position = new Position();
    Roles role = new Roles();
    Team team = new Team();
    TeamMember teamMember = new TeamMember();
    User user = new User();
    Staff staff = new Staff();
    hrms.ui.staff_management.LanguageSkill languageSkill = new hrms.ui.staff_management.LanguageSkill();
    hrms.ui.staff_management.MilitaryService militaryService = new hrms.ui.staff_management.MilitaryService();
    hrms.ui.staff_management.FamilyMember familyMember = new hrms.ui.staff_management.FamilyMember();
    hrms.ui.compensation_policy.OvertimePolicy overtimePolicy =
            new hrms.ui.compensation_policy.OvertimePolicy();
    hrms.ui.compensation_policy.Benefit benefitPanel =
            new hrms.ui.compensation_policy.Benefit();
    content_panel.add(department, "department");
    content_panel.add(office, "office");
    content_panel.add(position, "position");
    content_panel.add(role, "role");
    content_panel.add(team, "team");
    content_panel.add(teamMember, "team_member");
    content_panel.add(user, "user");
    content_panel.add(staff, "staff");
    content_panel.add(languageSkill, "language_skill");
    content_panel.add(militaryService, "military_service");
    content_panel.add(familyMember, "family_member");
    content_panel.add(overtimePolicy, "overtime_policy");
    content_panel.add(benefitPanel, "benefit");
    refreshers.put("department", department::refreshTable);
    refreshers.put("office", office::refreshTable);
    refreshers.put("position", position::refreshTable);
    refreshers.put("role", role::refreshTable);
    refreshers.put("team", team::refreshTable);
    refreshers.put("team_member", teamMember::refreshTable);
    refreshers.put("user", user::refreshTable);
    refreshers.put("staff", staff::refreshTable);
    refreshers.put("language_skill", languageSkill::refreshTable);
    refreshers.put("military_service", militaryService::refreshTable);
    refreshers.put("family_member", familyMember::refreshTable);
    refreshers.put("overtime_policy", overtimePolicy::refreshTable);
    refreshers.put("benefit", benefitPanel::refreshTable);
    // Every other visible menu leaf gets a placeholder card so navigation
    // never silently no-ops.
    for (MenuLeaf leaf : visibleLeaves()) {
      if (!WIRED_CARDS.contains(leaf.id())) {
        content_panel.add(placeholderPanel(leaf.label()), leaf.id());
      }
    }
  }

  /** True when the current session may see this menu leaf. */
  private static boolean canSee(MenuLeaf leaf) {
    return leaf.viewPerm() == null || SessionContext.has(leaf.viewPerm());
  }

  /** Dashboard + every permitted leaf, in menu order. */
  private static List<MenuLeaf> visibleLeaves() {
    java.util.List<MenuLeaf> visible = new java.util.ArrayList<>();
    if (canSee(DASHBOARD_LEAF)) {
      visible.add(DASHBOARD_LEAF);
    }
    for (MenuGroup group : MENU) {
      for (MenuLeaf leaf : group.leaves()) {
        if (canSee(leaf)) {
          visible.add(leaf);
        }
      }
    }
    return visible;
  }

  private static java.awt.Component placeholderPanel(String label) {
    javax.swing.JPanel panel = new javax.swing.JPanel(new java.awt.BorderLayout());
    panel.setOpaque(false);
    javax.swing.JLabel message = new javax.swing.JLabel(
            "<html><div style='text-align:center;'>" + label + "<br/><br/>"
            + "This screen is not available yet.</div></html>",
            javax.swing.SwingConstants.CENTER);
    message.setForeground(Palette.color(Role.TEXT_MUTED));
    panel.add(message, java.awt.BorderLayout.CENTER);
    return panel;
  }

  private void setupSidebarTree() {
    javax.swing.tree.DefaultMutableTreeNode root = new javax.swing.tree.DefaultMutableTreeNode("Menu");

    if (canSee(DASHBOARD_LEAF)) {
      root.add(new javax.swing.tree.DefaultMutableTreeNode(DASHBOARD_LEAF));
    }
    for (MenuGroup group : MENU) {
      javax.swing.tree.DefaultMutableTreeNode groupNode =
              new javax.swing.tree.DefaultMutableTreeNode(
                      new SidebarItem(group.label(), group.iconKey()));
      for (MenuLeaf leaf : group.leaves()) {
        if (canSee(leaf)) {
          groupNode.add(new javax.swing.tree.DefaultMutableTreeNode(leaf));
        }
      }
      // Hide a group entirely when the session may see none of its leaves.
      if (groupNode.getChildCount() > 0) {
        root.add(groupNode);
      }
    }

    javax.swing.tree.DefaultTreeModel treeModel = new javax.swing.tree.DefaultTreeModel(root);
    sidemenu_tree.setModel(treeModel);
    sidemenu_tree.setRootVisible(false);
    sidemenu_tree.setShowsRootHandles(false); // Hide the default left-side arrows

    // Single-click to expand/collapse category nodes
    sidemenu_tree.setToggleClickCount(1);
    // FIX 1 & 2: Make JTree transparent & remove default selection highlight box
    sidemenu_tree.setOpaque(false);
    sidemenu_tree.putClientProperty("FlatLaf.style",
            "selectionBackground: #00000000; "
            + "selectionInactiveBackground: #00000000; "
            + "selectionBorderColor: #00000000");

    for (int i = 0; i < sidemenu_tree.getRowCount(); i++) {
      sidemenu_tree.collapseRow(i);
    }
    sidemenu_tree.expandRow(0);

    // Apply MenuButton Renderer
    SidebarTreeCellRenderer renderer = new SidebarTreeCellRenderer();
    sidemenu_tree.setCellRenderer(renderer);
    sidemenu_tree.setRowHeight(MENU_ITEM_HEIGHT);

    // Mouse Motion Listener for Hover Effect on JTree
    sidemenu_tree.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
      @Override
      public void mouseMoved(java.awt.event.MouseEvent e) {
        int row = sidemenu_tree.getRowForLocation(e.getX(), e.getY());
        renderer.setHoveredRow(row);
        sidemenu_tree.repaint();
      }
    });

    sidemenu_tree.addMouseListener(new java.awt.event.MouseAdapter() {
      @Override
      public void mouseExited(java.awt.event.MouseEvent e) {
        renderer.setHoveredRow(-1);
        sidemenu_tree.repaint();
      }
    });

    // Content Panel Navigation on Leaf Click (id-based, never label-based)
    sidemenu_tree.addTreeSelectionListener(event -> {
      javax.swing.tree.DefaultMutableTreeNode node
              = (javax.swing.tree.DefaultMutableTreeNode) sidemenu_tree.getLastSelectedPathComponent();
      if (node == null || !node.isLeaf()) {
        return;
      }
      if (node.getUserObject() instanceof MenuLeaf leaf) {
        navigateTo(leaf, groupLabelOf(node));
      }
    });
  }

  /**
   * Shows the card registered under the leaf id and updates the breadcrumb
   * title: grouped leaves read "Group › Leaf" while root leaves (Dashboard)
   * keep a plain title.
   */
  private void navigateTo(MenuLeaf leaf, String groupLabel) {
    java.awt.CardLayout layout = (java.awt.CardLayout) content_panel.getLayout();
    layout.show(content_panel, leaf.id());
    refreshCard(leaf.id());

    if (title != null) {
      title.setText(groupLabel == null
              ? leaf.label()
              : groupLabel + " › " + leaf.label());
    }
  }

  /** Reloads a panel's data when its card is shown, so nothing looks stale. */
  private void refreshCard(String id) {
    Runnable refresher = refreshers.get(id);
    if (refresher == null) {
      return;
    }
    try {
      refresher.run();
    } catch (RuntimeException ex) {
      javax.swing.JOptionPane.showMessageDialog(this, ex.getMessage(),
              "Refresh failed", javax.swing.JOptionPane.WARNING_MESSAGE);
    }
  }

  /** Group label of the node's parent, or null when the leaf sits on the root. */
  private static String groupLabelOf(javax.swing.tree.DefaultMutableTreeNode node) {
    if (node.getParent() instanceof javax.swing.tree.DefaultMutableTreeNode parent
            && parent.getUserObject() instanceof SidebarItem item) {
      return item.label;
    }
    return null;
  }

  /**
   * Tree Node တွေကို App ရဲ့ Palette Color/Font အတိုင်း ဆွဲပေးတဲ့ Renderer
   */
  /**
   * Tree Cell Renderer for Category Headers (Right-aligned SVG Chevrons) & Leaf
   * Items
   */
  /**
   * Tree Cell Renderer (Supports Hover effect on Category Headers & Child
   * Items)
   */
  private class SidebarTreeCellRenderer implements javax.swing.tree.TreeCellRenderer {

    private final MenuButton rendererButton = new MenuButton("renderer", null, "");
    private final CategoryHeader categoryHeader = new CategoryHeader();
    private int hoveredRow = -1;

    public void setHoveredRow(int hoveredRow) {
      this.hoveredRow = hoveredRow;
    }

    @Override
    public java.awt.Component getTreeCellRendererComponent(
            javax.swing.JTree tree, Object value,
            boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {

      if (value instanceof javax.swing.tree.DefaultMutableTreeNode node) {
        Object userObject = node.getUserObject();

        // 1. Group / Category Headers (Non-leaf) -> Pass hover state
        if (!leaf) {
          String label = "";
          String iconKey = null;
          if (userObject instanceof SidebarItem item) {
            label = item.label;
            iconKey = item.iconKey;
          } else if (userObject != null) {
            label = userObject.toString();
          }
          categoryHeader.configure(label, iconKey, expanded, row == hoveredRow);
          return categoryHeader;
        }

        // 2. Clickable Inner Items (Leaf) -> Text only MenuButton
        if (userObject instanceof MenuLeaf leafItem) {
          rendererButton.setLabel(leafItem.label());
        } else if (userObject instanceof SidebarItem item) {
          rendererButton.setLabel(item.label);
        } else if (userObject != null) {
          rendererButton.setLabel(userObject.toString());
        }

        rendererButton.applyIcon(null);
      }

      rendererButton.setSelected(selected);
      rendererButton.setHovered(row == hoveredRow && !selected);
      rendererButton.setSize(tree.getWidth(), tree.getRowHeight());

      return rendererButton;
    }

    /**
     * Component that draws category icon + text on the left, right chevron
     * arrow, and hover background.
     */
    private static final class CategoryHeader extends javax.swing.JComponent {

      private String text;
      private String iconKey;
      private boolean expanded;
      private boolean hovered;

      CategoryHeader() {
        setOpaque(false);
        setPreferredSize(new java.awt.Dimension(210, MENU_ITEM_HEIGHT));
      }

      void configure(String text, String iconKey, boolean expanded, boolean hovered) {
        this.text = text;
        this.iconKey = iconKey;
        this.expanded = expanded;
        this.hovered = hovered;
      }

      @Override
      protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);

        int width = getWidth() > 0 ? getWidth() : getPreferredSize().width;
        int height = getHeight() > 0 ? getHeight() : getPreferredSize().height;

        // 1. Paint Hover Background
        if (hovered) {
          Color hoverBg = Palette.color(Role.SIDEBAR_HOVER_BG);
          UiGraphics.fillRoundRect(g, 0, 0, width, height, 9, hoverBg);
        }

        Color fg = Palette.color(Role.SIDEBAR_FG);

        // 2. Draw Category Icon (Left Edge)
        int textX = 12;
        if (iconKey != null && !iconKey.isEmpty()) {
          try {
            javax.swing.Icon catIcon = IconLoader.tinted(iconKey, IconLoader.SIZE_DEFAULT, fg);
            if (catIcon != null) {
              int catIconY = (height - catIcon.getIconHeight()) / 2;
              catIcon.paintIcon(this, g, 16, catIconY);
              textX = 48; // Align category label with child item text
            }
          } catch (Exception ignored) {
          }
        }

        // 3. Draw Category Label
        if (text != null) {
          g.setColor(fg);
          g.setFont(g.getFont().deriveFont(Font.BOLD, 12f));
          java.awt.FontMetrics metrics = g.getFontMetrics();
          int textY = (height + metrics.getAscent() - metrics.getDescent()) / 2;
          g.drawString(text, textX, textY);
        }

        // 4. Draw Right SVG Chevron
        try {
          javax.swing.Icon chevron = IconLoader.tinted("chevron-right", IconLoader.SIZE_SMALL, fg);
          if (chevron != null && chevron.getIconWidth() > 0) {
            int iconX = width - chevron.getIconWidth() - 12;
            int iconY = (height - chevron.getIconHeight()) / 2;

            if (expanded) {
              Graphics2D gRotated = (Graphics2D) g.create();
              int centerX = iconX + chevron.getIconWidth() / 2;
              int centerY = iconY + chevron.getIconHeight() / 2;
              gRotated.rotate(Math.toRadians(90), centerX, centerY);
              chevron.paintIcon(this, gRotated, iconX, iconY);
              gRotated.dispose();
            } else {
              chevron.paintIcon(this, g, iconX, iconY);
            }
          }
        } catch (Exception ignored) {
          g.setColor(fg);
          g.setStroke(new java.awt.BasicStroke(1.5f));
          int arrowX = width - 18;
          int arrowY = height / 2;
          if (expanded) {
            g.drawLine(arrowX - 4, arrowY - 2, arrowX, arrowY + 2);
            g.drawLine(arrowX, arrowY + 2, arrowX + 4, arrowY - 2);
          } else {
            g.drawLine(arrowX - 2, arrowY - 4, arrowX + 2, arrowY);
            g.drawLine(arrowX + 2, arrowY, arrowX - 2, arrowY + 4);
          }
        }

        g.dispose();
      }
    }
  }

  private static class SidebarItem {

    final String label;
    final String iconKey;

    SidebarItem(String label, String iconKey) {
      this.label = label;
      this.iconKey = iconKey;
    }

    @Override
    public String toString() {
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
    java.awt.GridBagConstraints gridBagConstraints;

    header_panel = new javax.swing.JPanel() {
      @Override
      public void updateUI() {
        super.updateUI();
        if (hrms != null) {
          hrms.setForeground(Palette.color(Palette.Role.TEXT));
        }
      }

      @Override
      public java.awt.Dimension getPreferredSize() {
        return new java.awt.Dimension(super.getPreferredSize().width, 60);
      }

      @Override
      protected void paintComponent(java.awt.Graphics graphics) {
        super.paintComponent(graphics);
        java.awt.Graphics2D g = (java.awt.Graphics2D) graphics.create();
        g.setColor(Palette.color(Palette.Role.CARD_BG));
        g.fillRect(0, 0, getWidth(), getHeight());
        g.setColor(Palette.color(Palette.Role.CARD_BORDER));
        g.fillRect(0, getHeight() - 1, getWidth(), 1);
        g.dispose();
      }
    };
    left = new javax.swing.JPanel() {
      @Override
      protected void paintComponent(java.awt.Graphics graphics) {
        super.paintComponent(graphics);
        java.awt.Graphics2D g = (java.awt.Graphics2D) graphics.create();
        g.setColor(Palette.color(Palette.Role.CARD_BG));
        g.fillRect(0, 0, getWidth(), getHeight());
        g.setColor(Palette.color(Palette.Role.CARD_BORDER));
        g.fillRect(0, getHeight() - 1, getWidth(), 1);
        g.dispose();
      }
    };
    menu_icon = ModernButton.iconOnly("menu", "Toggle menu");
    hrms = new javax.swing.JLabel();
    right = new javax.swing.JPanel() {
      @Override
      protected void paintComponent(java.awt.Graphics graphics) {
        super.paintComponent(graphics);
        java.awt.Graphics2D g = (java.awt.Graphics2D) graphics.create();
        g.setColor(Palette.color(Palette.Role.CARD_BG));
        g.fillRect(0, 0, getWidth(), getHeight());
        g.setColor(Palette.color(Palette.Role.CARD_BORDER));
        g.fillRect(0, getHeight() - 1, getWidth(), 1);
        g.dispose();
      }
    };
    theme_toogle = ModernButton.iconOnly("moon", "Switch to dark theme");
    title = new javax.swing.JLabel();
    sidemenu_panel = new javax.swing.JPanel() {
      @Override
      protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        UiGraphics.fillRoundRect(g, 0, 0, getWidth(), getHeight(), 14, Palette.color(Role.SIDEBAR_BG));
        g.dispose();
      }
    };
    menuScroll = new javax.swing.JScrollPane();
    sidemenu_tree = new javax.swing.JTree();
    footer = new javax.swing.JPanel() {
      @Override
      protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        UiGraphics.fillRoundRect(g, 0, 0, getWidth(), getHeight(), 14, Palette.color(Role.SIDEBAR_BG));
        g.dispose();
      }
    };
    content_panel = new javax.swing.JPanel();

    setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
    setMinimumSize(new java.awt.Dimension(1180, 740));
    setName("main_frame"); // NOI18N

    header_panel.setBackground(new java.awt.Color(255, 255, 255));
    header_panel.setPreferredSize(new java.awt.Dimension(1440, 60));
    header_panel.setLayout(new java.awt.BorderLayout());

    left.setPreferredSize(new java.awt.Dimension(220, 60));
    left.setLayout(new java.awt.GridBagLayout());

    menu_icon.setPreferredSize(new java.awt.Dimension(40, 20));
    menu_icon.addActionListener(this::menu_iconActionPerformed);
    left.add(menu_icon, new java.awt.GridBagConstraints());

    hrms.setText("HRMS  ");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.insets = new java.awt.Insets(0, 24, 0, 9);
    left.add(hrms, gridBagConstraints);

    header_panel.add(left, java.awt.BorderLayout.LINE_START);

    theme_toogle.setPreferredSize(new java.awt.Dimension(40, 20));
    theme_toogle.addActionListener(this::theme_toogleActionPerformed);

    title.setText("Dashboard");

    javax.swing.GroupLayout rightLayout = new javax.swing.GroupLayout(right);
    right.setLayout(rightLayout);
    rightLayout.setHorizontalGroup(
      rightLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, rightLayout.createSequentialGroup()
        .addGap(57, 57, 57)
        .addComponent(title)
        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 694, Short.MAX_VALUE)
        .addComponent(theme_toogle, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(325, 325, 325))
    );
    rightLayout.setVerticalGroup(
      rightLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGroup(rightLayout.createSequentialGroup()
        .addGap(16, 16, 16)
        .addGroup(rightLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
          .addComponent(theme_toogle, javax.swing.GroupLayout.DEFAULT_SIZE, 28, Short.MAX_VALUE)
          .addComponent(title, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        .addContainerGap(16, Short.MAX_VALUE))
    );

    header_panel.add(right, java.awt.BorderLayout.CENTER);

    getContentPane().add(header_panel, java.awt.BorderLayout.PAGE_START);

    sidemenu_panel.setBackground(new java.awt.Color(255, 255, 255));
    sidemenu_panel.setBorder(javax.swing.BorderFactory.createEmptyBorder(18, 12, 14, 12));
    sidemenu_panel.setPreferredSize(new java.awt.Dimension(240, 840));
    sidemenu_panel.setLayout(new java.awt.BorderLayout());

    menuScroll.setViewportView(sidemenu_tree);

    sidemenu_panel.add(menuScroll, java.awt.BorderLayout.CENTER);

    javax.swing.GroupLayout footerLayout = new javax.swing.GroupLayout(footer);
    footer.setLayout(footerLayout);
    footerLayout.setHorizontalGroup(
      footerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGap(0, 216, Short.MAX_VALUE)
    );
    footerLayout.setVerticalGroup(
      footerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGap(0, 100, Short.MAX_VALUE)
    );

    sidemenu_panel.add(footer, java.awt.BorderLayout.PAGE_END);

    getContentPane().add(sidemenu_panel, java.awt.BorderLayout.LINE_START);

    content_panel.setMinimumSize(new java.awt.Dimension(1220, 840));
    content_panel.setLayout(new java.awt.CardLayout());
    getContentPane().add(content_panel, java.awt.BorderLayout.CENTER);

    pack();
  }// </editor-fold>//GEN-END:initComponents

  private void menu_iconActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_menu_iconActionPerformed
    // TODO add your handling code here:
  }//GEN-LAST:event_menu_iconActionPerformed

  private final class MenuButton extends javax.swing.JComponent {

    private String label;
    private String iconKey;
    private Runnable action;
    private boolean hovered;
    private boolean selected;

    MenuButton(String id, String iconKey, String label) {
      this.label = label;
      this.iconKey = iconKey;
      setOpaque(false);
      setPreferredSize(new Dimension(210, MENU_ITEM_HEIGHT));
      setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));

      // ADD MOUSE LISTENER FOR INTERACTIVE BUTTON USAGE
      addMouseListener(new java.awt.event.MouseAdapter() {
        @Override
        public void mouseEntered(java.awt.event.MouseEvent e) {
          hovered = true;
          repaint();
        }

        @Override
        public void mouseExited(java.awt.event.MouseEvent e) {
          hovered = false;
          repaint();
        }

        @Override
        public void mousePressed(java.awt.event.MouseEvent e) {
          if (action != null) {
            action.run();
          }
        }
      });

    }

    void setLabel(String label) {
      this.label = label;
    }

    void setHovered(boolean hovered) {
      this.hovered = hovered;
    }

    void setAction(Runnable action) {
      this.action = action;
    }

    void applyIcon(String iconKey) {
      this.iconKey = iconKey;
    }

    void setSelected(boolean value) {
      this.selected = value;
    }

    String label() {
      return label;
    }

    @Override
    protected void paintComponent(Graphics graphics) {
      Graphics2D g = (Graphics2D) graphics.create();
      int width = getWidth();
      int height = getHeight();

      // 1. Background (Active / Hover / Default)
      Color background = selected
              ? Palette.color(Role.SIDEBAR_ACTIVE_BG)
              : hovered ? Palette.color(Role.SIDEBAR_HOVER_BG) : null;
      if (background != null) {
        UiGraphics.fillRoundRect(g, 0, 0, width, height, 9, background);
      }

      // 2. Foreground Color
      Color foreground = selected
              ? Palette.color(Role.SIDEBAR_ACTIVE_FG)
              : Palette.color(Role.SIDEBAR_FG);
      if (!foreground.equals(getForeground())) {
        setForeground(foreground);
      }

      // 3. Active Indicator Pill (Left Edge)
      if (selected) {
        g.setColor(Palette.color(Role.SIDEBAR_ACTIVE_FG));
        g.fillRoundRect(0, height / 2 - 11, 3, 22, 3, 3);
      }

      // 4. Fixed Icon Drawing
      if (iconKey != null) {
        javax.swing.Icon current = IconLoader.tinted(iconKey, 20, foreground);
        current.paintIcon(this, g, 16, (height - current.getIconHeight()) / 2);
      }

      // 5. Fixed Text Label
      if (label != null) {
        g.setColor(foreground);
        g.setFont(g.getFont().deriveFont(Font.PLAIN, 13f));
        java.awt.FontMetrics metrics = g.getFontMetrics();
        int textY = (height + metrics.getAscent() - metrics.getDescent()) / 2;
        g.drawString(label, 48, textY);
      }

      g.dispose();
    }
  }

  private void setupFooter() {
    footer.setOpaque(false);
    footer.setLayout(new java.awt.BorderLayout());
    footer.setPreferredSize(new java.awt.Dimension(216, MENU_ITEM_HEIGHT));

    MenuButton logoutBtn = new MenuButton("logout", "logout", "Logout");

    logoutBtn.setAction(() -> {
      LOG.info("User logged out");
      ServiceRegistry.authService().logout();
      dispose();
      java.awt.Window[] windows = java.awt.Window.getWindows();
      boolean anyVisible = false;
      for (java.awt.Window w : windows) {
        if (w.isShowing()) {
          anyVisible = true;
          break;
        }
      }
      if (!anyVisible) {
        new hrms.ui.login.LoginFrame().setVisible(true);
      }
    });

    footer.add(logoutBtn, java.awt.BorderLayout.CENTER);
    footer.revalidate();
    footer.repaint();
    sidemenu_panel.revalidate();
    sidemenu_panel.repaint();
  }

  private void theme_toogleActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_theme_toogleActionPerformed
    ThemeManager.toggle();
  }//GEN-LAST:event_theme_toogleActionPerformed

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

    /* Create and display the form */
    java.awt.EventQueue.invokeLater(() -> new MainFrame().setVisible(true));
  }

  // Variables declaration - do not modify//GEN-BEGIN:variables
  private javax.swing.JPanel content_panel;
  private javax.swing.JPanel footer;
  private javax.swing.JPanel header_panel;
  private javax.swing.JLabel hrms;
  private javax.swing.JPanel left;
  private javax.swing.JScrollPane menuScroll;
  private javax.swing.JButton menu_icon;
  private javax.swing.JPanel right;
  private javax.swing.JPanel sidemenu_panel;
  private javax.swing.JTree sidemenu_tree;
  private javax.swing.JButton theme_toogle;
  private javax.swing.JLabel title;
  // End of variables declaration//GEN-END:variables
}
