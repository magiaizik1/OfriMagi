package boundary;

import control.AccessDb;
import control.ParkingSessionManagementController;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.Timestamp;
import java.util.List;

public class ParkingSessionViewUI extends JPanel {

    private final ParkingSessionManagementController controller;

    private int parkingLotId = -1;

    private final DefaultTableModel model;
    private final JTable table;
    private final JLabel titleLabel;

    public ParkingSessionViewUI(AccessDb db) {
        this.controller = new ParkingSessionManagementController(db);

        setLayout(new BorderLayout(10, 10));

        titleLabel = new JLabel("Parking Sessions – Lot: (not selected)");
        add(titleLabel, BorderLayout.NORTH);

        // ===== MANAGER VIEW COLUMNS =====
        model = new DefaultTableModel(
                new Object[]{
                        "ID",
                        "Start Time",
                        "End Time",
                        "Vehicle ID",
                        "Spot ID",
                        "Conveyor ID",
                        "Amount",
                        "Rate"
                },
                0
        ) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        table = new JTable(model);
        add(new JScrollPane(table), BorderLayout.CENTER);

        JButton refreshBtn = new JButton("Refresh");
        refreshBtn.addActionListener(e -> reload());

        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        south.add(refreshBtn);
        add(south, BorderLayout.SOUTH);
    }

    public void setParkingLotId(int parkingLotId) {
        this.parkingLotId = parkingLotId;
        titleLabel.setText("Parking Sessions – Lot: " + parkingLotId);
        reload();
    }

    private void reload() {
        if (parkingLotId <= 0) return;

        model.setRowCount(0);

        // ===== USE MANAGER API =====
        List<Object[]> rows =
                controller.getSessionsByParkingLotForManager(parkingLotId);

        for (Object[] r : rows) {
            model.addRow(new Object[]{
                    r[0],                     // ID
                    formatTs(r[1]),           // startTime
                    formatTs(r[2]),           // endTime
                    r[3],                     // vehicleID
                    r[4],                     // parkingSpotID
                    r[5],                     // conveyorID
                    r[6],                     // amount (may be null)
                    r[7]                      // rate (may be null)
            });
        }
    }

    private String formatTs(Object ts) {
        if (ts == null) return "";
        if (ts instanceof Timestamp) return ts.toString();
        return String.valueOf(ts);
    }
}
