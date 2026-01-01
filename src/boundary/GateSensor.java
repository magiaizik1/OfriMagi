package boundary;

import control.ParkingSessionManagementController;

public interface GateSensor {

    // ===== ENTRY =====
    ParkingSessionManagementController.SensorArrivalResult
    vehicleArrived(int parkingLotId);

    void openBarrier();

    void showInstructions();

    // ===== EXIT (NEW – per story) =====
    void vehicleArrivedForExit(int sessionId);
}
