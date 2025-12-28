package boundary;

import control.ConveyorManagementController;
import control.ParkingLotManagementController;
import entity.Conveyor;
import entity.ConveyorLastStatus;
import entity.ConveyorStatus;
import entity.ParkingLot;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

/**
 * ConveyorManagementUI
 *
 * שינויים:
 * - Floor/X/Y read-only
 * - Weight input validation (no crash)
 * - Soft delete (Deactivate) + cannot deactivate twice
 * - Default: show only ACTIVE conveyors
 * - Optional: checkbox "Show inactive"
 * - NEW button: "Turn ON ALL (Lot)"
 */
public class ConveyorManagementUI extends JPanel {

    private final ConveyorManagementController controller;
    private final ParkingLotManagementController parkingLotController;

    private Integer parkingLotId = null;

    private JTable table;
    private DefaultTableModel model;

    private JComboBox<ParkingLot> parkingLotBox;

    private JTextField floorField; // read-only
    private JTextField xField;     // read-only
    private JTextField yField;     // read-only
    private JTextField weightField;

    private JLabel selectedInfoLabel;
    private JLabel pendingInfoLabel;

    private JButton refreshBtn;

    // NEW UI controls
    private JCheckBox showInactiveConveyors;
    private JButton turnOnAllBtn;

    // CRUD
    private JButton addBtn;
    private JButton deleteBtn;
    private JButton moveBtn;

    // State machine actions
    private JButton decideWeightBtn;
    private JButton confirmWeightBtn;

    private JButton turnOnBtn;
    private JButton restartBtn;
    private JButton turnOffBtn;

    public ConveyorManagementUI(ConveyorManagementController controller,
                               ParkingLotManagementController parkingLotController) {
        this.controller = controller;
        this.parkingLotController = parkingLotController;

        setLayout(new BorderLayout());
        initTable();
        initBottomPanel();
        wireSelection();
    }

    public void setParkingLotId(Integer parkingLotId) {
        this.parkingLotId = parkingLotId;

        SwingUtilities.invokeLater(() -> {
            boolean enabled = (parkingLotId != null);
            setPanelEnabled(enabled);

            if (enabled) {
                loadParkingLots();
                loadConveyors();
            } else {
                model.setRowCount(0);
                updateSelectedInfo();
                updateButtonsEnabled();
            }
        });
    }

    // ========================= UI =========================

    private void initTable() {
        model = new DefaultTableModel(
                new Object[]{"ID", "ParkingLotID", "Floor", "X", "Y", "MaxWeight", "Status", "LastStatus"}, 0
        ) {
            @Override public boolean isCellEditable(int row, int col) { return false; }
        };

        table = new JTable(model);
        add(new JScrollPane(table), BorderLayout.CENTER);
    }

    private void initBottomPanel() {
        JPanel bottom = new JPanel(new BorderLayout());

        JPanel infoPanel = new JPanel(new GridLayout(2, 1));
        selectedInfoLabel = new JLabel("Selected: -");
        pendingInfoLabel = new JLabel("Pending weight: -");
        infoPanel.add(selectedInfoLabel);
        infoPanel.add(pendingInfoLabel);
        bottom.add(infoPanel, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridLayout(3, 4, 6, 6));

        parkingLotBox = new JComboBox<>();

        floorField = new JTextField();
        floorField.setEditable(false);
        floorField.setBackground(new Color(240, 240, 240));

        xField = new JTextField();
        xField.setEditable(false);
        xField.setBackground(new Color(240, 240, 240));

        yField = new JTextField();
        yField.setEditable(false);
        yField.setBackground(new Color(240, 240, 240));

        weightField = new JTextField();

        refreshBtn = new JButton("Refresh");
        refreshBtn.addActionListener(e -> loadConveyors());

        showInactiveConveyors = new JCheckBox("Show inactive");
        showInactiveConveyors.addActionListener(e -> loadConveyors());

        addBtn = new JButton("Add (OFF)");
        deleteBtn = new JButton("Deactivate"); // changed label
        moveBtn = new JButton("Move to selected ParkingLot");

        addBtn.addActionListener(e -> addConveyor());
        deleteBtn.addActionListener(e -> deactivateSelected());
        moveBtn.addActionListener(e -> moveSelected());

        form.add(new JLabel("ParkingLot:"));
        form.add(parkingLotBox);
        form.add(refreshBtn);
        form.add(showInactiveConveyors);

        form.add(new JLabel("Floor (read-only):"));
        form.add(floorField);
        form.add(new JLabel("X (read-only):"));
        form.add(xField);

        form.add(new JLabel("Y (read-only):"));
        form.add(yField);
        form.add(new JLabel("MaxWeight (kg):"));
        form.add(weightField);

        bottom.add(form, BorderLayout.CENTER);

        JPanel actions = new JPanel(new GridLayout(2, 4, 6, 6));

        decideWeightBtn = new JButton("Decide Weight");
        confirmWeightBtn = new JButton("Confirm Weight");

        turnOnBtn = new JButton("Turn ON (-> Testing)");
        turnOffBtn = new JButton("Turn OFF (Operational -> Off)");
        restartBtn = new JButton("Restart (Paused -> Testing)");

        decideWeightBtn.addActionListener(e -> decideWeight());
        confirmWeightBtn.addActionListener(e -> confirmWeight());

        turnOnBtn.addActionListener(e -> turnOn());
        turnOffBtn.addActionListener(e -> turnOff());
        restartBtn.addActionListener(e -> restart());

        turnOnAllBtn = new JButton("Turn ON ALL (Lot)");
        turnOnAllBtn.addActionListener(e -> turnOnAll());

        actions.add(decideWeightBtn);
        actions.add(confirmWeightBtn);
        actions.add(turnOnBtn);
        actions.add(turnOffBtn);

        actions.add(restartBtn);
        actions.add(turnOnAllBtn);
        actions.add(addBtn);
        actions.add(deleteBtn);

        bottom.add(actions, BorderLayout.SOUTH);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        right.add(moveBtn);
        bottom.add(right, BorderLayout.EAST);

        add(bottom, BorderLayout.SOUTH);

        updateButtonsEnabled();
    }

    private void wireSelection() {
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateSelectedInfo();
                updateButtonsEnabled();
                updateReadOnlyFields();
            }
        });
    }

    private void setPanelEnabled(boolean enabled) {
        table.setEnabled(enabled);
        parkingLotBox.setEnabled(enabled);
        weightField.setEnabled(enabled);

        addBtn.setEnabled(enabled);
        refreshBtn.setEnabled(enabled);
        showInactiveConveyors.setEnabled(enabled);
        turnOnAllBtn.setEnabled(enabled);

        floorField.setEnabled(enabled);
        xField.setEnabled(enabled);
        yField.setEnabled(enabled);

        updateButtonsEnabled();
    }

    // ========================= Data loading =========================

    private void loadParkingLots() {
        parkingLotBox.removeAllItems();
        if (parkingLotController == null) return;

        // default controller returns only active lots (per our changes)
        for (ParkingLot p : parkingLotController.getAllParkingLots()) {
            parkingLotBox.addItem(p);
        }
        parkingLotBox.setSelectedIndex(-1);
    }

    private void loadConveyors() {
        model.setRowCount(0);
        if (parkingLotId == null) return;

        boolean includeInactive = showInactiveConveyors != null && showInactiveConveyors.isSelected();

        List<Conveyor> list = controller.getConveyorsByParkingLot(parkingLotId, includeInactive);
        for (Conveyor c : list) {

            ConveyorLastStatus last = c.getLastStatus();

            model.addRow(new Object[]{
                    c.getId(),
                    c.getParkingLotId(),
                    c.getFloorNumber(),  // can be null
                    c.getX(),            // can be null
                    c.getY(),            // can be null
                    c.getMaxVehicleWeightKg(),
                    c.getStatus(),
                    last
            });
        }

        updateSelectedInfo();
        updateButtonsEnabled();
        updateReadOnlyFields();
    }

    // ========================= Selection helpers =========================

    private Integer getSelectedConveyorId() {
        int row = table.getSelectedRow();
        if (row < 0) return null;
        Object v = model.getValueAt(row, 0);
        return (v instanceof Integer) ? (Integer) v : null;
    }

    private ConveyorStatus getSelectedStatus() {
        int row = table.getSelectedRow();
        if (row < 0) return null;
        Object st = model.getValueAt(row, 6);
        return (st instanceof ConveyorStatus) ? (ConveyorStatus) st : null;
    }

    private void updateSelectedInfo() {
        Integer id = getSelectedConveyorId();
        if (id == null) {
            selectedInfoLabel.setText("Selected: -");
            pendingInfoLabel.setText("Pending weight: -");
            return;
        }

        ConveyorStatus st = getSelectedStatus();
        Integer pending = controller.getPendingWeight(id);

        selectedInfoLabel.setText("Selected: Conveyor #" + id + " | Status: " + st);
        pendingInfoLabel.setText("Pending weight: " + (pending == null ? "-" : pending));
    }

    private void updateReadOnlyFields() {
        int row = table.getSelectedRow();
        if (row < 0) {
            floorField.setText("");
            xField.setText("");
            yField.setText("");
            return;
        }

        Object floor = model.getValueAt(row, 2);
        Object x = model.getValueAt(row, 3);
        Object y = model.getValueAt(row, 4);

        floorField.setText(floor == null ? "" : String.valueOf(floor));
        xField.setText(x == null ? "" : String.valueOf(x));
        yField.setText(y == null ? "" : String.valueOf(y));
    }

    private void updateButtonsEnabled() {
        Integer sel = getSelectedConveyorId();
        boolean hasSelection = (sel != null);
        ConveyorStatus st = getSelectedStatus();

        deleteBtn.setEnabled(hasSelection);
        moveBtn.setEnabled(hasSelection);

        boolean isOff = hasSelection && st == ConveyorStatus.Off;
        boolean isOperational = hasSelection && st == ConveyorStatus.Operational;
        boolean isPaused = hasSelection && st == ConveyorStatus.Paused;

        decideWeightBtn.setEnabled(isOff);
        confirmWeightBtn.setEnabled(isOff && controller.getPendingWeight(sel) != null);

        turnOnBtn.setEnabled(isOff && controller.getPendingWeight(sel) == null);
        turnOffBtn.setEnabled(isOperational);
        restartBtn.setEnabled(isPaused);

        // Turn ON ALL only makes sense when a lot is selected
        turnOnAllBtn.setEnabled(parkingLotId != null);
    }

    // ========================= Actions =========================

    private Integer parsePositiveIntOrShow(JTextField tf, String name) {
        String raw = tf.getText() == null ? "" : tf.getText().trim();
        if (raw.isEmpty()) {
            JOptionPane.showMessageDialog(this, name + " is required.", "Validation", JOptionPane.WARNING_MESSAGE);
            return null;
        }
        try {
            int v = Integer.parseInt(raw);
            if (v <= 0) throw new NumberFormatException();
            return v;
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, name + " must be a valid positive integer.", "Validation", JOptionPane.WARNING_MESSAGE);
            return null;
        }
    }

    private void addConveyor() {
        if (parkingLotId == null) {
            JOptionPane.showMessageDialog(this, "You must select a parking lot first.", "Validation", JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            ParkingLot lot = (ParkingLot) parkingLotBox.getSelectedItem();
            if (lot == null) {
                JOptionPane.showMessageDialog(this, "Select a ParkingLot from the list.", "Validation", JOptionPane.WARNING_MESSAGE);
                return;
            }

            Integer weight = parsePositiveIntOrShow(weightField, "MaxWeight");
            if (weight == null) return;

            controller.addConveyorToParkingLot(lot.getId(), 1, 1, 1, weight, ConveyorStatus.Off);

            loadConveyors();
            weightField.setText("");

        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void deactivateSelected() {
        Integer id = getSelectedConveyorId();
        if (id == null) return;

        int ok = JOptionPane.showConfirmDialog(this, "Deactivate Conveyor #" + id + " ?", "Confirm", JOptionPane.YES_NO_OPTION);
        if (ok != JOptionPane.YES_OPTION) return;

        try {
            controller.deleteConveyor(id); // now soft delete in controller
            loadConveyors();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void moveSelected() {
        Integer id = getSelectedConveyorId();
        if (id == null) return;

        ParkingLot lot = (ParkingLot) parkingLotBox.getSelectedItem();
        if (lot == null) return;

        try {
            controller.moveConveyorToParkingLot(id, lot.getId());
            loadConveyors();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void decideWeight() {
        Integer id = getSelectedConveyorId();
        if (id == null) return;

        try {
            Integer w = parsePositiveIntOrShow(weightField, "MaxWeight");
            if (w == null) return;

            controller.decideChangeMaxWeight(id, w);
            updateSelectedInfo();
            updateButtonsEnabled();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void confirmWeight() {
        Integer id = getSelectedConveyorId();
        if (id == null) return;

        try {
            controller.confirmChangeMaxWeight(id);
            loadConveyors();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void turnOn() {
        Integer id = getSelectedConveyorId();
        if (id == null) return;

        try {
            controller.turnOnConveyors(id);
            loadConveyors();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void restart() {
        Integer id = getSelectedConveyorId();
        if (id == null) return;

        try {
            controller.restart(id);
            loadConveyors();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void turnOff() {
        Integer id = getSelectedConveyorId();
        if (id == null) return;

        try {
            controller.turnOffConveyors(id);
            loadConveyors();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void turnOnAll() {
        if (parkingLotId == null) return;

        try {
            int count = controller.turnOnAllConveyorsInParkingLot(parkingLotId);
            JOptionPane.showMessageDialog(this, "Turned ON " + count + " conveyor(s).");
            loadConveyors();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}