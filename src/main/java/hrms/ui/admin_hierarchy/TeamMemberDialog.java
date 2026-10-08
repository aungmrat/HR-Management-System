/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/GUIForms/JDialog.java to edit this template
 */
package hrms.ui.admin_hierarchy;

import hrms.exception.AuthorizationException;
import hrms.exception.ErrorHandler;
import hrms.exception.ValidationException;
import hrms.model.Team;
import hrms.model.TeamMember;
import hrms.repository.TeamMemberRepository;
import hrms.repository.TeamRepository;
import hrms.security.Permissions;
import hrms.security.SecurityService;
import hrms.security.SessionContext;
import hrms.util.UiThread;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author user
 */
public class TeamMemberDialog extends javax.swing.JDialog {

    private static final Logger LOG = LoggerFactory.getLogger(TeamMemberDialog.class);

    /** null = create mode. */
    private final TeamMember editing;

    /**
     * Creates new form TeamMemberDialog
     */
    public TeamMemberDialog(java.awt.Frame parent, boolean modal) {
        this(parent, modal, null);
    }

    /** Create when {@code existing} is null; edit when non-null. */
    public TeamMemberDialog(java.awt.Frame parent, boolean modal, TeamMember existing) {
        super(parent, modal);
        initComponents();
        this.editing = existing;
        if (existing == null) {
            setTitle("New Team Member");
        } else {
            setTitle("Edit Team Member");
            prefill(existing);
        }
        setupActions();
        loadCombos();
    }

    /** Apply stored values first; combo selections are applied after loadCombos(). */
    private void prefill(TeamMember member) {
        textRemark.setText(member.getRemark() == null ? "" : member.getRemark());
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
        // Staff and parent choices depend on the selected team.
        comboTeam.addActionListener(e -> refillOptions());
    }

    /** Permission gate for Save; static so headless tests can verify it. */
    static void requireSavePermission(boolean isEdit) {
        SecurityService.require(isEdit
                ? Permissions.TEAM_MEMBER_UPDATE : Permissions.TEAM_MEMBER_CREATE);
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

    /** Staff + membership options for the currently selected team. */
    private record Options(java.util.List<TeamMemberRepository.StaffOption> staff,
            java.util.List<TeamMember> members) {
    }

    /** The team combo is loaded from the database — never hardcode the items. */
    private void loadCombos() {
        UiThread.executeAsync("Load team member combos",
                TeamRepository::listAll,
                teams -> {
                    comboTeam.removeAllItems();
                    for (Team team : teams) {
                        comboTeam.addItem(new ComboItem(team.getId(), team.getName()));
                    }
                    if (editing != null) {
                        selectByValue(comboTeam, editing.getTeamId());
                    }
                });
    }

    /**
     * Rebuilds staff and parent choices for the selected team. While editing,
     * the stored choices are restored whenever the team is unchanged.
     */
    private void refillOptions() {
        ComboItem team = (ComboItem) comboTeam.getSelectedItem();
        Long teamId = team == null ? null : team.value();
        ComboItem previousStaff = (ComboItem) comboStaff.getSelectedItem();
        ComboItem previousParent = (ComboItem) comboParentMember.getSelectedItem();
        Long keepStaffId = editing == null ? null : editing.getStaffId();
        boolean restoring = editing != null && teamId != null
                && teamId.equals(editing.getTeamId());

        UiThread.executeAsync("Load member options",
                () -> new Options(
                        TeamMemberRepository.listStaffOptions(teamId, keepStaffId),
                        TeamMemberRepository.listByTeam(teamId == null ? -1L : teamId)),
                options -> {
                    comboStaff.removeAllItems();
                    for (TeamMemberRepository.StaffOption option : options.staff()) {
                        comboStaff.addItem(new ComboItem(option.id(), option.label()));
                    }

                    comboParentMember.removeAllItems();
                    comboParentMember.addItem(new ComboItem(null, "(None)"));
                    for (TeamMember member : options.members()) {
                        if (editing != null && member.getId() == editing.getId()) {
                            continue;
                        }
                        comboParentMember.addItem(
                                new ComboItem(member.getId(), staffLabel(member)));
                    }

                    if (restoring) {
                        selectByValue(comboStaff, editing.getStaffId());
                        selectByValue(comboParentMember, editing.getParentId());
                    } else {
                        if (previousStaff != null) {
                            selectByValue(comboStaff, previousStaff.value());
                        }
                        if (previousParent != null) {
                            selectByValue(comboParentMember, previousParent.value());
                        }
                    }
                });
    }

    /** {@code CODE — Name} label for a membership row's staff. */
    private static String staffLabel(TeamMember member) {
        String name = member.getStaffName() == null ? "" : member.getStaffName();
        String code = member.getStaffCode();
        return code == null || code.isBlank() ? name : code + " — " + name;
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
            Long teamId = selectedValue(comboTeam);
            Long staffId = selectedValue(comboStaff);
            Long parentId = selectedValue(comboParentMember);
            String remark = textRemark.getText();

            java.util.List<String> errors = new java.util.ArrayList<>();
            if (teamId == null) {
                errors.add("Team is required.");
            }
            if (staffId == null) {
                errors.add("Staff is required.");
            }
            if (remark != null && remark.trim().length() > 500) {
                errors.add("Remark must be at most 500 characters.");
            }
            if (!errors.isEmpty()) {
                throw new ValidationException(errors);
            }

            Long userId = SessionContext.isAuthenticated() ? SessionContext.currentUserId() : null;
            boolean isEdit = editing != null;
            TeamMember member;
            if (isEdit) {
                member = editing;
                member.setTeamId(teamId);
                member.setStaffId(staffId);
                member.setParentId(parentId);
                member.setRemark(remark);
                member.setModifiedBy(userId);
            } else {
                member = TeamMember.create(staffId, teamId, parentId, remark, userId);
            }

            btnSave.setEnabled(false);
            UiThread.executeAsync(isEdit ? "Update team member" : "Save team member",
                    () -> isEdit
                            ? TeamMemberRepository.update(member)
                            : TeamMemberRepository.insert(member),
                    result -> {
                        if (isEdit) {
                            LOG.info("Team member updated id={}", member.getId());
                        } else {
                            LOG.info("Team member saved with id {}", result);
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
        labelTeam = new javax.swing.JLabel();
        comboTeam = new javax.swing.JComboBox<>();
        labelStaff = new javax.swing.JLabel();
        comboStaff = new javax.swing.JComboBox<>();
        labelParentMember = new javax.swing.JLabel();
        comboParentMember = new javax.swing.JComboBox<>();
        labelRemark = new javax.swing.JLabel();
        jScrollPane1 = new javax.swing.JScrollPane();
        textRemark = new javax.swing.JTextArea();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);

        footer.setPreferredSize(new java.awt.Dimension(400, 60));
        footer.setLayout(new java.awt.GridBagLayout());

        btnSave.setText("Save");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(18, 18, 19, 40);
        footer.add(btnSave, gridBagConstraints);

        btnCancel.setText("Cancel");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(18, 198, 19, 0);
        footer.add(btnCancel, gridBagConstraints);

        getContentPane().add(footer, java.awt.BorderLayout.PAGE_END);

        body.setLayout(new java.awt.GridBagLayout());

        labelTeam.setText("Team");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        gridBagConstraints.insets = new java.awt.Insets(15, 0, 10, 20);
        body.add(labelTeam, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(15, 0, 10, 0);
        body.add(comboTeam, gridBagConstraints);

        labelStaff.setText("Staff");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        gridBagConstraints.insets = new java.awt.Insets(15, 0, 10, 20);
        body.add(labelStaff, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(15, 0, 10, 0);
        body.add(comboStaff, gridBagConstraints);

        labelParentMember.setText("Parent Member");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        gridBagConstraints.insets = new java.awt.Insets(15, 0, 10, 20);
        body.add(labelParentMember, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(15, 0, 10, 0);
        body.add(comboParentMember, gridBagConstraints);

        labelRemark.setText("Remark");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHEAST;
        gridBagConstraints.insets = new java.awt.Insets(15, 0, 10, 20);
        body.add(labelRemark, gridBagConstraints);

        textRemark.setColumns(20);
        textRemark.setRows(5);
        jScrollPane1.setViewportView(textRemark);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(15, 0, 10, 0);
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
            LOG.error("Cannot set look and feel", ex);
        }
        //</editor-fold>

        /* Create and display the dialog */
        java.awt.EventQueue.invokeLater(new Runnable() {
            @Override
            public void run() {
                TeamMemberDialog dialog = new TeamMemberDialog(new javax.swing.JFrame(), true);
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
    private javax.swing.JComboBox<ComboItem> comboParentMember;
    private javax.swing.JComboBox<ComboItem> comboStaff;
    private javax.swing.JComboBox<ComboItem> comboTeam;
    private javax.swing.JPanel footer;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JLabel labelParentMember;
    private javax.swing.JLabel labelRemark;
    private javax.swing.JLabel labelStaff;
    private javax.swing.JLabel labelTeam;
    private javax.swing.JTextArea textRemark;
    // End of variables declaration//GEN-END:variables
}
