package simulation;

import entity.ParkingLot;

import java.util.List;
import java.util.Random;

import control.ParkingLotManagementController;
import control.ParkingSessionManagementController;
import control.ParkingSessionManagementController.SensorArrivalResult;
import control.ParkingSessionManagementController;

public class ParkingSensorRunner {

    private final ParkingSessionManagementController sessionController;
    private final ParkingLotManagementController lotController;
    private final Random rnd = new Random();

    public ParkingSensorRunner(
            ParkingSessionManagementController sessionController,
            ParkingLotManagementController lotController
    ) {
        this.sessionController = sessionController;
        this.lotController = lotController;
    }

    /**
     * Simulates a single vehicle arrival to a random parking lot.
     * This method triggers REAL DB logic:
     * - selects a real parking lot
     * - selects a real free vehicle
     * - checks conveyors and parking spots in DB
     * - creates a real ParkingSession if possible
     */
    public void simulateSingleArrival() {

        // 1️⃣ Pick random active parking lot from DB
        List<ParkingLot> lots = lotController.getAllParkingLots(true);
        if (lots.isEmpty()) {
            System.out.println("❌ No parking lots in system");
            return;
        }

        ParkingLot lot = lots.get(rnd.nextInt(lots.size()));
        int parkingLotId = lot.getId();

        System.out.println("\n=== SENSOR SIMULATION START ===");
        System.out.println("📍 Parking lot: " + parkingLotId + " (" + lot.getName() + ")");

        try {
            SensorArrivalResult result =
                    sessionController.simulateVehicleArrivalAndPark(parkingLotId);

            switch (result.outcome) {

                case "NO_VEHICLE":
                    System.out.println("⚠️ No free vehicle available in system");
                    break;

                case "WAITING_FOR_CONVEYOR":
                    System.out.println("🚗 Vehicle selected: " + result.vehicleId);
                    System.out.println("⏳ No available conveyor that matches weight → waiting");
                    break;

                case "LOT_FULL":
                    System.out.println("🚗 Vehicle selected: " + result.vehicleId);
                    System.out.println("🅿️ No suitable parking spot available → lot full");
                    break;

                case "OK":
                    System.out.println("🚗 Vehicle selected: " + result.vehicleId);
                    System.out.println("🔁 Conveyor assigned: " + result.conveyorId);
                    System.out.println("🅿️ Parking spot assigned: " + result.spotId);
                    System.out.println("✅ Parking session CREATED in DB");
                    System.out.println("🧾 Session ID: " + result.sessionId);
                    break;

                default:
                    System.out.println("❓ Unknown result: " + result.outcome);
            }

        } catch (Exception e) {
            System.out.println("❌ Error during sensor simulation");
            e.printStackTrace();
        }
    }
}