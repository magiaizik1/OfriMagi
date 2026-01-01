package control;

import java.util.List;
import java.util.Random;

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
     */
    public void simulateSingleArrival() {

        // 1️⃣ בוחרים חניון רנדומלי
        List<entity.ParkingLot> lots = lotController.getAllParkingLots(true);
        if (lots.isEmpty()) {
            System.out.println("❌ No parking lots in system");
            return;
        }

        entity.ParkingLot lot = lots.get(rnd.nextInt(lots.size()));
        int parkingLotId = lot.getId();

        System.out.println("\n🚗 Vehicle arrived at parking lot " + parkingLotId);

        try {
            ParkingSessionManagementController.SensorArrivalResult result =
                    sessionController.simulateVehicleArrivalAndPark(parkingLotId);

            switch (result.outcome) {

                case "NO_VEHICLE":
                    System.out.println("⚠️ No free vehicle available");
                    break;

                case "WAITING_FOR_CONVEYOR":
                    System.out.println("⏳ Waiting for available conveyor");
                    break;

                case "LOT_FULL":
                    System.out.println("🅿️ Parking lot is full");
                    break;

                case "OK":
                    System.out.println("✅ Session created");
                    System.out.println("   Vehicle: " + result.vehicleId);
                    System.out.println("   Conveyor: " + result.conveyorId);
                    System.out.println("   Spot: " + result.spotId);
                    System.out.println("   Session ID: " + result.sessionId);
                    break;

                default:
                    System.out.println("❓ Unknown result: " + result.outcome);
            }

        } catch (Exception e) {
            System.out.println("❌ Error during sensor simulation:");
            e.printStackTrace();
        }
    }
}
