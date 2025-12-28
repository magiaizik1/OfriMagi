package boundary;

import control.AccessDb;
import control.CityManagementController;
import control.ConveyorManagementController;
import control.ParkingLotManagementController;
import control.ParkingSessionManagementController;
import control.PriceHistoryManagementController;
import control.PriceListManagementController;
import entity.City;
import entity.ParkingLot;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

/**
 * ParkingLotDashboardUI
 *
 * שינוי דרישה:
 * - כתובת התפצלה לשני שדות: Street + Number
 * - נוספה בדיקת קלט למספר (לא לקרוס אם לא int)
 *
 * שינוי נוסף:
 * - Soft delete: Deactivate instead of DELETE
 * - Default: show only ACTIVE parking lots
 * - Optional: checkbox "Show inactive"
 *
 * תיקון נוסף (Assignment 3):
 * - הוספת מעבר למסך Parking Sessions דרך ParkingSessionManagementUI
 * - שימוש באותו AccessDb קיים של הפרויקט (לא ליצור חיבור חדש)
 */
public class ParkingLotDashboardUI extends JFrame {

    private final AccessDb db;

    private final ParkingLotManagementController parkingLotController;
    private final CityManagementController cityController;
    private final ConveyorManagementController conveyorController;
    private final PriceHistoryManagementController priceHistoryController;
    private final PriceListManagementController priceListController;

    private JButton parkingSessionBtn;
    private JTable table;
    private DefaultTableModel model;

    private ParkingLot selectedParkingLot;

    private JTextField idField;
    private JTextField nameField;

    private JTextField streetField;
    private JTextField numberField;

    private JTextField spacesField;
    private JComboBox<City> cityCombo;

    // search
    private JTextField searchIdField;
    private JButton searchBtn;

    // NEW
    private JCheckBox showInactiveLots;

    // secondary buttons
    private JButton conveyorBtn;
    private JButton priceHistoryBtn;
    private JButton priceListBtn;

    public ParkingLotDashboardUI(
            AccessDb db,
            ParkingLotManagementController parkingLotController,
            CityManagementController cityController,
            ConveyorManagementController conveyorController,
            PriceHistoryManagementController priceHistoryController,
            PriceListManagementController priceListController
    ) {
        this.db = db;
        this.parkingLotController = parkingLotController;
        this.cityController = cityController;
        this.conveyorController = conveyorController;
        this.priceHistoryController = priceHistoryController;
        this.priceListController = priceListController;

        setTitle("ParkWise – Parking Lots");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(950, 600);
        setLocationRelativeTo(null);

        initUI();
        loadCities();
        loadParkingLots();
    }

    private void initUI() {
        setLayout(new BorderLayout(10, 10));

        // ================= NORTH =================
        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        searchPanel.add(new JLabel("ParkingLot ID:"));
        searchIdField = new JTextField(6);
        searchPanel.add(searchIdField);

        searchBtn = new JButton("Find");
        searchBtn.addActionListener(e -> findParkingLotById());
        searchPanel.add(searchBtn);

        // ✅ NEW checkbox
        showInactiveLots = new JCheckBox("Show inactive");
        showInactiveLots.addActionListener(e -> loadParkingLots());
        searchPanel.add(showInactiveLots);

        JPanel secondaryPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        conveyorBtn = new JButton("Conveyors");
        priceHistoryBtn = new JButton("Price History");
        priceListBtn = new JButton("Import Price List");
        parkingSessionBtn = new JButton("Parking Sessions");

        conveyorBtn.addActionListener(e -> openConveyorScreen());
        priceHistoryBtn.addActionListener(e -> openPriceHistoryScreen());
        priceListBtn.addActionListener(e -> openPriceListScreen());
        parkingSessionBtn.addActionListener(e -> openParkingSessionScreen());

        secondaryPanel.add(conveyorBtn);
        secondaryPanel.add(priceHistoryBtn);
        secondaryPanel.add(priceListBtn);
        secondaryPanel.add(parkingSessionBtn);

        JPanel north = new JPanel(new BorderLayout());
        north.add(searchPanel, BorderLayout.WEST);
        north.add(secondaryPanel, BorderLayout.EAST);

        add(north, BorderLayout.NORTH);

        // ================= TABLE =================
        model = new DefaultTableModel(
                new Object[]{"ID", "Name", "Address", "City", "Available Spaces"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };

        table = new JTable(model);
        add(new JScrollPane(table), BorderLayout.CENTER);

        table.getSelectionModel().addListSelectionListener((ListSelectionEvent e) -> {
            if (!e.getValueIsAdjusting()) {
                fillFormFromSelection();
            }
        });

        // ================= FORM =================
        JPanel form = new JPanel(new GridLayout(6, 2, 8, 8));

        idField = new JTextField();
        idField.setEditable(false);

        nameField = new JTextField();

        streetField = new JTextField();
        numberField = new JTextField();

        spacesField = new JTextField();
        spacesField.setEditable(false);

        cityCombo = new JComboBox<>();

        form.add(new JLabel("ID"));
        form.add(idField);

        form.add(new JLabel("Name"));
        form.add(nameField);

        form.add(new JLabel("Street"));
        form.add(streetField);

        form.add(new JLabel("Number"));
        form.add(numberField);

        form.add(new JLabel("City"));
        form.add(cityCombo);

        form.add(new JLabel("Available Spaces"));
        form.add(spacesField);

        JButton addBtn = new JButton("Add");
        addBtn.addActionListener(e -> addParkingLot());

        JButton updateBtn = new JButton("Update");
        updateBtn.addActionListener(e -> updateParkingLot());

        JButton deleteBtn = new JButton("Deactivate");
        deleteBtn.addActionListener(e -> deactivateParkingLot());

        JButton reloadBtn = new JButton("Reload");
        reloadBtn.addActionListener(e -> loadParkingLots());

        JPanel crudButtons = new JPanel(new GridLayout(1, 4, 8, 8));
        crudButtons.add(addBtn);
        crudButtons.add(updateBtn);
        crudButtons.add(deleteBtn);
        crudButtons.add(reloadBtn);

        JPanel south = new JPanel(new BorderLayout(10, 10));
        south.add(form, BorderLayout.CENTER);
        south.add(crudButtons, BorderLayout.SOUTH);

        add(south, BorderLayout.SOUTH);
    }

    // ================= LOGIC =================

    private void loadCities() {
        cityCombo.removeAllItems();
        for (City c : cityController.getAllCities()) {
            cityCombo.addItem(c);
        }
        cityCombo.setSelectedIndex(-1);
    }

    private void loadParkingLots() {
        model.setRowCount(0);

        boolean includeInactive = showInactiveLots != null && showInactiveLots.isSelected();
        List<ParkingLot> lots = parkingLotController.getAllParkingLots(includeInactive);

        for (ParkingLot p : lots) {
            model.addRow(new Object[]{
                    p.getId(),
                    p.getName(),
                    p.getAddress(),
                    p.getCity(),
                    p.getAvailableSpaces()
            });
        }
    }

    private void fillFormFromSelection() {
        int row = table.getSelectedRow();
        if (row < 0) return;

        int id = (int) model.getValueAt(row, 0);

        try {
            selectedParkingLot = parkingLotController.getParkingLot(id);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        idField.setText(String.valueOf(selectedParkingLot.getId()));
        nameField.setText(selectedParkingLot.getName());

        streetField.setText(selectedParkingLot.getStreet() == null ? "" : selectedParkingLot.getStreet());
        numberField.setText(selectedParkingLot.getNumber() == null ? "" : String.valueOf(selectedParkingLot.getNumber()));

        cityCombo.setSelectedItem(selectedParkingLot.getCity());
        spacesField.setText(String.valueOf(selectedParkingLot.getAvailableSpaces()));
    }

    private void findParkingLotById() {
        try {
            int id = Integer.parseInt(searchIdField.getText().trim());
            ParkingLot p = parkingLotController.getParkingLot(id);

            // if lot is inactive and we are not showing inactive -> show a friendly message
            if (!p.isActive() && (showInactiveLots == null || !showInactiveLots.isSelected())) {
                JOptionPane.showMessageDialog(this, "Parking lot is inactive. Enable 'Show inactive' to view it.");
                return;
            }

            // refresh list according to checkbox (so it appears if needed)
            loadParkingLots();

            for (int i = 0; i < model.getRowCount(); i++) {
                if ((int) model.getValueAt(i, 0) == id) {
                    table.setRowSelectionInterval(i, i);
                    table.scrollRectToVisible(table.getCellRect(i, 0, true));
                    return;
                }
            }

            selectedParkingLot = p;
            fillFormFromSelection();

        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Parking lot not found.");
        }
    }

    private void addParkingLot() {
        try {
            Integer number = parsePositiveIntOrShow(numberField, "Number");
            if (number == null) return;

            City city = (City) cityCombo.getSelectedItem();
            if (city == null) {
                JOptionPane.showMessageDialog(this, "City is required.", "Validation", JOptionPane.WARNING_MESSAGE);
                return;
            }

            // available spaces is read-only; if empty -> default 0
            int spaces = 0;
            String rawSpaces = spacesField.getText() == null ? "" : spacesField.getText().trim();
            if (!rawSpaces.isEmpty()) {
                try { spaces = Integer.parseInt(rawSpaces); } catch (Exception ignore) { spaces = 0; }
            }

            parkingLotController.addParkingLot(
                    nameField.getText(),
                    streetField.getText(),
                    number,
                    city,
                    spaces
            );
            loadParkingLots();

        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void updateParkingLot() {
        if (selectedParkingLot == null) {
            JOptionPane.showMessageDialog(this, "Select a parking lot first.");
            return;
        }

        if (!selectedParkingLot.isActive()) {
            JOptionPane.showMessageDialog(this, "Parking lot is inactive and cannot be updated.");
            return;
        }

        try {
            Integer number = parsePositiveIntOrShow(numberField, "Number");
            if (number == null) return;

            City city = (City) cityCombo.getSelectedItem();
            if (city == null) {
                JOptionPane.showMessageDialog(this, "City is required.", "Validation", JOptionPane.WARNING_MESSAGE);
                return;
            }

            parkingLotController.updateParkingLot(
                    selectedParkingLot.getId(),
                    nameField.getText(),
                    streetField.getText(),
                    number,
                    city
            );
            JOptionPane.showMessageDialog(this, "Parking lot updated successfully.");
            loadParkingLots();

        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void deactivateParkingLot() {
        if (selectedParkingLot == null) return;

        if (!selectedParkingLot.isActive()) {
            JOptionPane.showMessageDialog(this, "Parking lot is already inactive.");
            return;
        }

        if (JOptionPane.showConfirmDialog(this,
                "Deactivate this parking lot?",
                "Confirm",
                JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {

            try {
                parkingLotController.deleteParkingLot(selectedParkingLot.getId()); // soft delete
                loadParkingLots();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private Integer parsePositiveIntOrShow(JTextField tf, String fieldName) {
        String raw = tf.getText() == null ? "" : tf.getText().trim();
        if (raw.isEmpty()) {
            JOptionPane.showMessageDialog(this, fieldName + " is required.", "Validation", JOptionPane.WARNING_MESSAGE);
            return null;
        }
        try {
            int v = Integer.parseInt(raw);
            if (v <= 0) throw new NumberFormatException();
            return v;
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, fieldName + " must be a valid positive integer.", "Validation", JOptionPane.WARNING_MESSAGE);
            return null;
        }
    }

    // ================= SECONDARY WINDOWS =================

    private void openConveyorScreen() {
        if (selectedParkingLot == null) {
            JOptionPane.showMessageDialog(this, "Select a parking lot first.");
            return;
        }

        if (!selectedParkingLot.isActive()) {
            JOptionPane.showMessageDialog(this, "Parking lot is inactive.");
            return;
        }

        ConveyorManagementUI ui =
                new ConveyorManagementUI(conveyorController, parkingLotController);

        ui.setParkingLotId(selectedParkingLot.getId());

        JFrame f = new JFrame("Conveyors – ParkingLot " + selectedParkingLot.getId());
        f.setContentPane(ui);
        f.pack();
        f.setLocationRelativeTo(this);
        f.setVisible(true);
    }

    private void openPriceHistoryScreen() {
        if (selectedParkingLot == null) {
            JOptionPane.showMessageDialog(this, "Select a parking lot first.");
            return;
        }

        PriceHistoryManagementUI ui =
                new PriceHistoryManagementUI(priceHistoryController, priceListController);

        ui.setParkingLotId(selectedParkingLot.getId());

        JFrame f = new JFrame("Price History – ParkingLot " + selectedParkingLot.getId());
        f.setContentPane(ui);
        f.pack();
        f.setLocationRelativeTo(this);
        f.setVisible(true);
    }

    private void openPriceListScreen() {
        JFrame f = new JFrame("Import Price List");
        f.setContentPane(new PriceListImportViewUI(priceListController));
        f.pack();
        f.setLocationRelativeTo(this);
        f.setVisible(true);
    }

    /**
     * Opens the Parking Sessions management screen for the currently selected ParkingLot.
     * Uses the existing AccessDb instance of the project (same DB connection strategy).
     */
    private void openParkingSessionScreen() {
        if (selectedParkingLot == null) {
            JOptionPane.showMessageDialog(this, "Select a parking lot first.");
            return;
        }

        ParkingSessionManagementUI ui =
                new ParkingSessionManagementUI(
                        new ParkingSessionManagementController(db),
                        priceListController,
                        priceHistoryController
                );

        ui.setParkingLotId(selectedParkingLot.getId());

        JFrame f = new JFrame("Parking Sessions – Lot " + selectedParkingLot.getId());
        f.setContentPane(ui);
        f.pack();
        f.setLocationRelativeTo(this);
        f.setVisible(true);
    }
}