/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/GUIForms/JDialog.java to edit this template
 */
package hrms.ui.admin_hierarchy;

import hrms.exception.AuthorizationException;
import hrms.exception.ErrorHandler;
import hrms.exception.ValidationException;
import hrms.model.Department;
import hrms.model.Office;
import hrms.model.Team;
import hrms.repository.DepartmentRepository;
import hrms.repository.OfficeRepository;
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
public class TeamDialog extends javax.swing.JDialog {

    private static final Logger LOG = LoggerFactory.getLogger(TeamDialog.class);

    /** null = create mode. */
    private final Team editing;

    /** Cached office list so the office combo can follow the department choice. */
    private java.util.List<Office> offices = java.util.List.of();

    /**
     * Creates new form TeamDialog
     */
    public TeamDialog(java.awt.Frame parent, boolean modal) {
        this(parent, modal, null);
    }

    /** Create when {@code existing} is null; edit when non-null. */
    public TeamDialog(java.awt.Frame parent, boolean modal, Team existing) {
        super(parent, modal);
        initComponents();
        this.editing = existing;
        if (existing == null) {
            setTitle("New Team");
        } else {
            setTitle("Edit Team");
            prefill(existing);
        }
        setupActions();
        loadCombos();
    }

    /** Apply stored values first; combo selections are applied after loadCombos(). */
    private void prefill(Team team) {
        textName.setText(team.getName() == null ? "" : team.getName());
        textRemark.setText(team.getRemark() == null ? "" : team.getRemark());
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
        // Office belongs to one department, so the office list follows the choice.
        comboDepartment.addActionListener(e -> refillOffices());
    }

    /** Permission gate for Save; static so headless tests can verify it. */
    static void requireSavePermission(boolean isEdit) {
        SecurityService.require(isEdit
                ? Permissions.TEAM_UPDATE : Permissions.TEAM_CREATE);
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
            java.util.List<Office> offices, java.util.List<Team> teams) {
    }

    /** All combos are loaded from the database — never hardcode the items. */
    private void loadCombos() {
        UiThread.executeAsync("Load team combos",
                () -> new ComboData(DepartmentRepository.listAll(),
                        OfficeRepository.listAll(), TeamRepository.listAll()),
                this::fillCombos);
    }

    private void fillCombos(ComboData data) {
        this.offices = data.offices();

        comboDepartment.removeAllItems();
        for (Department department : data.departments()) {
            comboDepartment.addItem(new ComboItem(department.getId(),
                    department.getCode() + " — " + department.getName()));
        }

        // Parent choices: never the team itself and never a team below it.
        comboParentTeam.removeAllItems();
        comboParentTeam.addItem(new ComboItem(null, "(None)"));
        for (Team team : data.teams()) {
            if (editing != null && isSelfOrDescendant(team.getId(), editing.getId(), data.teams())) {
                continue;
            }
            comboParentTeam.addItem(new ComboItem(team.getId(), team.getName()));
        }

        if (editing == null) {
            return;
        }
        selectByValue(comboDepartment, editing.getDepartmentId());
        refillOffices();
        selectByValue(comboOffice, editing.getOfficeId());
        selectByValue(comboParentTeam, editing.getParentId());
    }

    /**
     * Rebuilds the office combo for the selected department, keeping the
     * current choice when that office still belongs to it.
     */
    private void refillOffices() {
        ComboItem department = (ComboItem) comboDepartment.getSelectedItem();
        Long departmentId = department == null ? null : department.value();
        ComboItem previous = (ComboItem) comboOffice.getSelectedItem();
        comboOffice.removeAllItems();
        for (Office office : offices) {
            if (departmentId != null && office.getDepartmentId() != null
                    && office.getDepartmentId().equals(departmentId)) {
                comboOffice.addItem(new ComboItem(office.getId(),
                        office.getCode() + " — " + office.getName()));
            }
        }
        if (previous != null) {
            selectByValue(comboOffice, previous.value());
        }
    }

    /** True when {@code candidateId} is {@code ancestorId} or lies below it. */
    private static boolean isSelfOrDescendant(long candidateId, long ancestorId,
            java.util.List<Team> teams) {
        java.util.Map<Long, Long> parents = new java.util.HashMap<>();
        for (Team team : teams) {
            parents.put(team.getId(), team.getParentId());
        }
        Long cursor = candidateId;
        int guard = 0;
        while (cursor != null && guard++ < 100) {
            if (cursor == ancestorId) {
                return true;
            }
            cursor = parents.get(cursor);
        }
        return false;
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
            String name = textName.getText();
            String remark = textRemark.getText();
            Long departmentId = selectedValue(comboDepartment);
            Long officeId = selectedValue(comboOffice);
            Long parentId = selectedValue(comboParentTeam);

            java.util.List<String> errors = new java.util.ArrayList<>();
            if (departmentId == null) {
                errors.add("Department is required.");
            }
            if (officeId == null) {
                errors.add("Office is required.");
            }
            if (name == null || name.isBlank()) {
                errors.add("Team name is required.");
            } else if (name.trim().length() > 100) {
                errors.add("Team name must be at most 100 characters.");
            }
            if (remark != null && remark.trim().length() > 500) {
                errors.add("Remark must be at most 500 characters.");
            }
            if (!errors.isEmpty()) {
                throw new ValidationException(errors);
            }

            Long userId = SessionContext.isAuthenticated() ? SessionContext.currentUserId() : null;
            boolean isEdit = editing != null;
            Team team;
            if (isEdit) {
                team = editing;
                team.setDepartmentId(departmentId);
                team.setOfficeId(officeId);
                team.setParentId(parentId);
                team.setName(name.trim());
                team.setRemark(remark);
                team.setModifiedBy(userId);
            } else {
                team = Team.create(departmentId, officeId, parentId, name.trim(), remark, userId);
            }

            btnSave.setEnabled(false);
            UiThread.executeAsync(isEdit ? "Update team" : "Save team",
                    () -> isEdit
                            ? TeamRepository.update(team)
                            : TeamRepository.insert(team),
                    result -> {
                        if (isEdit) {
                            LOG.info("Team updated id={}", team.getId());
                        } else {
                            LOG.info("Team saved with id {}", result);
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
        labelName = new javax.swing.JLabel();
        textName = new javax.swing.JTextField();
        labelDepartment = new javax.swing.JLabel();
        comboDepartment = new javax.swing.JComboBox<>();
        labelOffice = new javax.swing.JLabel();
        comboOffice = new javax.swing.JComboBox<>();
        labelParentTeam = new javax.swing.JLabel();
        comboParentTeam = new javax.swing.JComboBox<>();
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
        gridBagConstraints.insets = new java.awt.Insets(19, 18, 18, 40);
        footer.add(btnSave, gridBagConstraints);

        btnCancel.setText("Cancel");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(19, 198, 18, 0);
        footer.add(btnCancel, gridBagConstraints);

        getContentPane().add(footer, java.awt.BorderLayout.PAGE_END);

        body.setLayout(new java.awt.GridBagLayout());

        labelName.setText("Name");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        gridBagConstraints.insets = new java.awt.Insets(12, 0, 6, 19);
        body.add(labelName, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(12, 0, 6, 0);
        body.add(textName, gridBagConstraints);

        labelDepartment.setText("Department");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        gridBagConstraints.insets = new java.awt.Insets(12, 0, 6, 19);
        body.add(labelDepartment, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(12, 0, 6, 0);
        body.add(comboDepartment, gridBagConstraints);

        labelOffice.setText("Office");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        gridBagConstraints.insets = new java.awt.Insets(12, 0, 6, 19);
        body.add(labelOffice, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(12, 0, 6, 0);
        body.add(comboOffice, gridBagConstraints);

        labelParentTeam.setText("Parent Team");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        gridBagConstraints.insets = new java.awt.Insets(12, 0, 6, 19);
        body.add(labelParentTeam, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(12, 0, 6, 0);
        body.add(comboParentTeam, gridBagConstraints);

        labelRemark.setText("Remark");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHEAST;
        gridBagConstraints.insets = new java.awt.Insets(12, 0, 6, 19);
        body.add(labelRemark, gridBagConstraints);

        textRemark.setColumns(20);
        textRemark.setRows(5);
        jScrollPane1.setViewportView(textRemark);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(12, 0, 6, 0);
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
                TeamDialog dialog = new TeamDialog(new javax.swing.JFrame(), true);
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
    private javax.swing.JComboBox<ComboItem> comboOffice;
    private javax.swing.JComboBox<ComboItem> comboParentTeam;
    private javax.swing.JPanel footer;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JLabel labelDepartment;
    private javax.swing.JLabel labelName;
    private javax.swing.JLabel labelOffice;
    private javax.swing.JLabel labelParentTeam;
    private javax.swing.JLabel labelRemark;
    private javax.swing.JTextField textName;
    private javax.swing.JTextArea textRemark;
    // End of variables declaration//GEN-END:variables
}
