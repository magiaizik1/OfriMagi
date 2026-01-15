package simulation;

import control.ParkingLotManagementController;
import control.ParkingSessionManagementController;
import entity.ParkingLot;

import java.util.List;
import java.util.Random;

public class ParkingSensorRunner {

    private final ParkingSessionManagementController sessionController;
    private final ParkingLotManagementController lotController;
    private final Random rnd = new Random();

    private static final int FIXED_LOT_ID = 14;

    public ParkingSensorRunner(
            ParkingSessionManagementController sessionController,
            ParkingLotManagementController lotController
    ) {
        this.sessionController = sessionController;
        this.lotController = lotController;
    }

    /** Runs N arrivals. Requirement: at least 7 runs. */
    public void simulateArrivals(int runs) {
        for (int i = 1; i <= runs; i++) {
            System.out.println("\n==============================");
            System.out.println("RUN #" + i);
            simulateSingleArrival();
        }
    }

    /** Always uses parking lot 14 and prints clean output. */
    public void simulateSingleArrival() {

        ParkingLot lot14 = null;
        try {
            List<ParkingLot> lots = lotController.getAllParkingLots(true);
            for (ParkingLot l : lots) {
                if (l.getId() == FIXED_LOT_ID) {
                    lot14 = l;
                    break;
                }
            }
        } catch (Exception ignore) {}

        System.out.println("\n=== SENSOR SIMULATION START ===");
        if (lot14 != null) {
            System.out.println("📍 Parking lot: " + FIXED_LOT_ID + " (" + lot14.getName() + ")");
        } else {
            System.out.println("📍 Parking lot: " + FIXED_LOT_ID);
        }

        try {
            // הפרמטר לא משנה – הקונטרולר יכפה 14
            control.ParkingEntryController.SensorArrivalResult result = sessionController.simulateVehicleArrivalAndPark(FIXED_LOT_ID);

            System.out.println("🧩 Outcome: " + result.outcome);

            if ("OK".equals(result.outcome)) {
                System.out.println("🚗 Vehicle selected: " + result.vehicleId);
                System.out.println("🔁 Conveyor assigned: " + result.conveyorId);
                System.out.println("🅿️ Parking spot assigned: " + result.spotId);
                System.out.println("✅ Parking session CREATED in DB");
                System.out.println("🧾 Session ID: " + result.sessionId);
            } else if ("NO_VEHICLE".equals(result.outcome)) {
                System.out.println("⚠️ No free vehicle available");
            } else if ("WAITING_FOR_CONVEYOR".equals(result.outcome)) {
                System.out.println("⏳ No available conveyor that matches weight");
            } else if ("LOT_FULL".equals(result.outcome)) {
                System.out.println("🅿️ No suitable parking spot available");
            } else {
                System.out.println("❓ Unknown outcome");
            }

        } catch (Exception e) {
            System.out.println("❌ Error during sensor simulation");
            e.printStackTrace();
        }
    }
}
