package boundary;

import control.AccessDb;
import control.ParkingLotManagementController;
import control.ParkingSessionManagementController;
import entity.ParkingLot;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDateTime;
import java.util.List;

public class GateSensorDummyUI extends JFrame implements GateSensor {

    private final ParkingSessionManagementController sessionController;
    private final ParkingLotManagementController lotController;

    private JComboBox<ParkingLot> lotCombo;
    private JButton simulateBtn;
    private JTextArea logArea;

    public GateSensorDummyUI(
            AccessDb db,
            ParkingLotManagementController lotController
    ) {
        this.lotController = lotController;
        this.sessionController = new ParkingSessionManagementController(db);

        setTitle("Gate Sensor (Dummy) – ParkWise");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(800, 500);
        setLocationRelativeTo(null);

        initUI();
        loadLots();
    }

    // ======================
    // === GateSensor API ===
    // ======================

    @Override
    public ParkingSessionManagementController.SensorArrivalResult
    vehicleArrived(int parkingLotId) {
        try {
            return sessionController.simulateVehicleArrivalAndPark(parkingLotId);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void openBarrier() {
        append("Gate barrier opened.");
    }

    @Override
    public void showInstructions() {
        append("Barrier could not be opened.");
        append("Please enter personal details and upload vehicle photos.");
    }

    // ======================
    // ======= UI ===========
    // ======================

    private void initUI() {
        setLayout(new BorderLayout(10, 10));

        JPanel north = new JPanel(new FlowLayout(FlowLayout.LEFT));
        north.add(new JLabel("Select Parking Lot (sensor is per lot):"));

        lotCombo = new JComboBox<>();
        lotCombo.setPreferredSize(new Dimension(320, 25));
        north.add(lotCombo);

        simulateBtn = new JButton("Simulate Vehicle Arrival");
        simulateBtn.addActionListener(e -> simulateArrival());
        north.add(simulateBtn);

        add(north, BorderLayout.NORTH);

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        add(new JScrollPane(logArea), BorderLayout.CENTER);
    }

    private void loadLots() {
        lotCombo.removeAllItems();
        List<ParkingLot> lots = lotController.getAllParkingLots(true);
        for (ParkingLot p : lots) lotCombo.addItem(p);
        if (lotCombo.getItemCount() > 0) lotCombo.setSelectedIndex(0);
    }

    private void simulateArrival() {
        ParkingLot lot = (ParkingLot) lotCombo.getSelectedItem();
        if (lot == null) {
            JOptionPane.showMessageDialog(this, "Select parking lot first.");
            return;
        }

        simulateBtn.setEnabled(false);
        append("=== [" + now() + "] Sensor ACTIVE for Lot #" + lot.getId() + " (" + lot.getName() + ") ===");
        append("Vehicle arrived at gate. Searching DB...");

        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                try {
                    var r = vehicleArrived(lot.getId());

                    if ("NO_VEHICLE".equals(r.outcome)) {
                        append("No free vehicle found.");
                        return null;
                    }

                    append("Picked vehicleID=" + r.vehicleId);

                    if ("WAITING_FOR_CONVEYOR".equals(r.outcome)) {
                        append("No AVAILABLE conveyor. STATUS: WAITING_FOR_CONVEYOR");
                        showInstructions();
                        return null;
                    }

                    if ("LOT_FULL".equals(r.outcome)) {
                        append("Parking lot is full.");
                        showInstructions();
                        return null;
                    }

                    append("Assigned conveyorID=" + r.conveyorId);
                    append("Assigned parkingSpotID=" + r.spotId);
                    append("Created sessionID=" + r.sessionId);

                    append("Conveyor moving vehicle...");
                    Thread.sleep(2000);

                    sessionController.markParkingCompleted(r.sessionId);
                    append("Parking completed: state=PARKED");
                    openBarrier();

                } catch (Exception ex) {
                    append("ERROR: " + ex.getMessage());
                }
                return null;
            }

            @Override
            protected void done() {
                simulateBtn.setEnabled(true);
                append("=== [" + now() + "] Ready for next arrival ===");
            }
        };

        worker.execute();
    }

    private void append(String s) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(s + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    private String now() {
        return LocalDateTime.now().toString();
    }
    @Override
    public void vehicleArrivedForExit(int sessionId) {
        append("Vehicle arrived at exit gate for session " + sessionId);
        append("Waiting for payment approval...");
    }

}
