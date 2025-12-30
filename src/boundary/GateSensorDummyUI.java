package boundary;

import control.AccessDb;
import control.ParkingLotManagementController;
import control.ParkingSessionManagementController;
import entity.ParkingLot;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDateTime;
import java.util.List;

public class GateSensorDummyUI extends JFrame {

    private final AccessDb db;
    private final ParkingLotManagementController lotController;
    private final ParkingSessionManagementController sessionController;

    private JComboBox<ParkingLot> lotCombo;
    private JButton simulateBtn;
    private JTextArea logArea;

    public GateSensorDummyUI(
            AccessDb db,
            ParkingLotManagementController lotController
    ) {
        this.db = db;
        this.lotController = lotController;
        this.sessionController = new ParkingSessionManagementController(db);

        setTitle("Gate Sensor (Dummy) – ParkWise");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(800, 500);
        setLocationRelativeTo(null);

        initUI();
        loadLots();
    }

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
        List<ParkingLot> lots = lotController.getAllParkingLots(true); // לא משנה לך מנהל, רק קריאה
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
                    // 1) ניסיון מלא: רכב רנדומלי + מסוע + חניה + יצירת סשן
                    ParkingSessionManagementController.SensorArrivalResult r =
                            sessionController.simulateVehicleArrivalAndPark(lot.getId());

                    if ("NO_VEHICLE".equals(r.outcome)) {
                        append("No free vehicle found (all vehicles are in active sessions).");
                        return null;
                    }

                    append("Picked random vehicle: vehicleID=" + r.vehicleId);

                    if ("WAITING_FOR_CONVEYOR".equals(r.outcome)) {
                        append("No AVAILABLE conveyor in OPERATIONAL state. STATUS: WAITING_FOR_CONVEYOR");
                        append("UI message: waiting for conveyor...");
                        return null;
                    }

                    if ("LOT_FULL".equals(r.outcome)) {
                        append("No suitable parking spot available. STATUS: LOT_FULL");
                        append("UI message: parking lot is full.");
                        append("SIMULATION: SMS sent to customer (lot full / cannot park).");
                        return null;
                    }

                    // OK
                    append("Assigned conveyorID=" + r.conveyorId + " (Status=OPERATIONAL, LastStatus=BUSY)");
                    append("Assigned parkingSpotID=" + r.spotId + " (nearest available & size-compatible)");
                    append("Created ParkingSession ID=" + r.sessionId + " state=MOVING_TO_PARKING");

                    // 2) סימולציה של תנועה (כדי שתראי סטטוס “בזמן אמת”)
                    append("Conveyor moving vehicle to spot...");
                    Thread.sleep(2000);

                    // 3) מסיימים חניה: state=PARKED + שחרור מסוע ל-AVAILABLE
                    sessionController.markParkingCompleted(r.sessionId);

                    append("Parking completed: session state=PARKED");
                    append("Conveyor released: LastStatus=AVAILABLE");
                    append("SIMULATION: SMS sent to customer with parking details (session=" + r.sessionId + ").");

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
}