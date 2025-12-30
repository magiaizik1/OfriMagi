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

    private JTextField searchIdField;
    private JButton searchBtn;

    private JCheckBox showInactiveLots;

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

        // ===== NORTH =====
        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        searchPanel.add(new JLabel("ParkingLot ID:"));
        searchIdField = new JTextField(6);
        searchPanel.add(searchIdField);

        searchBtn = new JButton("Find");
        searchBtn.addActionListener(e -> findParkingLotById());
        searchPanel.add(searchBtn);

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
        parkingSessionBtn.addActionListener(e -> openParkingSessionScreenViewOnly());

        secondaryPanel.add(conveyorBtn);
        secondaryPanel.add(priceHistoryBtn);
        secondaryPanel.add(priceListBtn);
        secondaryPanel.add(parkingSessionBtn);

        JPanel north = new JPanel(new BorderLayout());
        north.add(searchPanel, BorderLayout.WEST);
        north.add(secondaryPanel, BorderLayout.EAST);
        add(north, BorderLayout.NORTH);

        // ===== TABLE =====
        model = new DefaultTableModel(
                new Object[]{"ID", "Name", "Address", "City", "Available Spaces"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };

        table = new JTable(model);
        add(new JScrollPane(table), BorderLayout.CENTER);

        table.getSelectionModel().addListSelectionListener((ListSelectionEvent e) -> {
            if (!e.getValueIsAdjusting()) fillFormFromSelection();
        });

        // ===== FORM =====
        JPanel form = new JPanel(new GridLayout(6, 2, 8, 8));

        idField = new JTextField(); idField.setEditable(false);
        nameField = new JTextField();
        streetField = new JTextField();
        numberField = new JTextField();
        spacesField = new JTextField(); spacesField.setEditable(false);
        cityCombo = new JComboBox<>();

        form.add(new JLabel("ID")); form.add(idField);
        form.add(new JLabel("Name")); form.add(nameField);
        form.add(new JLabel("Street")); form.add(streetField);
        form.add(new JLabel("Number")); form.add(numberField);
        form.add(new JLabel("City")); form.add(cityCombo);
        form.add(new JLabel("Available Spaces")); form.add(spacesField);

        JPanel south = new JPanel(new BorderLayout(10, 10));
        south.add(form, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);
    }

    // ===== DATA =====

    private void loadCities() {
        cityCombo.removeAllItems();
        for (City c : cityController.getAllCities()) cityCombo.addItem(c);
        cityCombo.setSelectedIndex(-1);
    }

    private void loadParkingLots() {
        model.setRowCount(0);

        boolean includeInactive = showInactiveLots.isSelected();
        List<ParkingLot> lots = parkingLotController.getAllParkingLots(includeInactive);

        for (ParkingLot p : lots) {
            String address =
                    (p.getStreet() == null ? "" : p.getStreet()) + " " +
                    (p.getNumber() == null ? "" : p.getNumber());

            model.addRow(new Object[]{
                    p.getId(),
                    p.getName(),
                    address.trim(),
                    p.getCity(),
                    p.getAvailableSpaces()
            });
        }
    }

    private void fillFormFromSelection() {
        int row = table.getSelectedRow();
        if (row < 0) return;

        int id = (int) model.getValueAt(row, 0);
        selectedParkingLot = parkingLotController.getParkingLot(id);

        idField.setText(String.valueOf(id));
        nameField.setText(selectedParkingLot.getName());
        streetField.setText(selectedParkingLot.getStreet());
        numberField.setText(selectedParkingLot.getNumber() == null ? "" : selectedParkingLot.getNumber().toString());
        spacesField.setText(String.valueOf(selectedParkingLot.getAvailableSpaces()));

        for (int i = 0; i < cityCombo.getItemCount(); i++) {
            if (cityCombo.getItemAt(i).getId() == selectedParkingLot.getCity().getId()) {
                cityCombo.setSelectedIndex(i);
                break;
            }
        }
    }

    // ===== PARKING SESSIONS – VIEW ONLY =====

    private void openParkingSessionScreenViewOnly() {
        if (selectedParkingLot == null) {
            JOptionPane.showMessageDialog(this, "Select a parking lot first.");
            return;
        }

        ParkingSessionViewUI ui =
                new ParkingSessionViewUI(
                        new ParkingSessionManagementController(db)
                );

        ui.setParkingLotId(selectedParkingLot.getId());

        JFrame f = new JFrame("Parking Sessions – Lot " + selectedParkingLot.getId());
        f.setContentPane(ui);
        f.pack();
        f.setLocationRelativeTo(this);
        f.setVisible(true);
    }

    // ===== STUBS (כמו שהיה אצלך) =====
    private void openConveyorScreen() {}
    private void openPriceHistoryScreen() {}
    private void openPriceListScreen() {}
    private void findParkingLotById() {}
}
