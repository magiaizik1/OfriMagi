package control;

import boundary.*;

import java.util.List;

public class ParkingSessionManagementController {

    private final ParkingEntryController entryController;
    private final ParkingExitController exitController;
    private final ParkingPaymentController paymentController;
    private final ParkingSessionController sessionController;

    public ParkingSessionManagementController(
            AccessDb db,
            ConveyorsControllerPort conveyorsPort,
            GateSensorPort gateSensorPort,
            PaymentGatewayPort paymentGatewayPort,
            SmsGatewayPort smsGatewayPort
    ) {

        this.entryController =
                new ParkingEntryController(db, conveyorsPort, gateSensorPort);

        this.exitController =
                new ParkingExitController(db, conveyorsPort, gateSensorPort);

        this.paymentController =
                new ParkingPaymentController(db, paymentGatewayPort, smsGatewayPort);

        this.sessionController =
                new ParkingSessionController(db);
    }

    public ParkingSessionManagementController(AccessDb db) {
        this(db, null, null, null, null);
    }

    // ===== EXISTING API =====

    public void startParkingSession(int parkingLotId, int vehicleId, int spotId, int conveyorId) throws Exception {
        sessionController.startParkingSession(parkingLotId, vehicleId, spotId, conveyorId);
    }

    public void updateSessionState(int sessionId, String newState) throws Exception {
        sessionController.updateSessionState(sessionId, newState);
    }

    public void endParkingSession(int sessionId) throws Exception {
        sessionController.endParkingSession(sessionId);
    }

    public List<Object[]> getSessionsByParkingLot(int parkingLotId) {
        return sessionController.getSessionsByParkingLot(parkingLotId);
    }

    public List<Object[]> getSessionsByParkingLotForManager(int parkingLotId) {
        return sessionController.getSessionsByParkingLotForManager(parkingLotId, paymentController);
    }

    public void generateReceipt(int sessionId) {
        paymentController.generateReceipt(sessionId);
    }

    // ===== ENTRY FLOW =====

    public ParkingEntryController.SensorArrivalResult simulateVehicleArrivalAndPark(int ignoredParkingLotId) throws Exception {
        return entryController.simulateVehicleArrivalAndPark(ignoredParkingLotId);
    }

    // ===== CLIENT =====

    public List<String> getActiveParkingDetailsByPhone(String phoneNumber) {
        return sessionController.getActiveParkingDetailsByPhone(phoneNumber);
    }

    public String requestEndParkingByVehicleAndPhone(String vehicleNumber, String phoneNumber) {
        try {
            return sessionController.getActiveParkingDetailsByPhone(phoneNumber).toString();
        } catch (Exception e) {
            return "❌ Failed to request exit: " + e.getMessage();
        }
    }

    public String requestPaymentByVehicleAndPhone(String vehicleNumber, String phoneNumber) {
        return "Not implemented here";
    }

    public String requestReceiptByVehicleAndPhone(String vehicleNumber, String phoneNumber) {
        return "Not implemented here";
    }

    // ===== EXIT FLOW =====

    public void requestExit(int sessionId) throws Exception {
        exitController.requestExit(sessionId);
    }

    public void requestPaymentForExit(int sessionId) {
        paymentController.requestPaymentForExit(sessionId, exitController);
    }

    // ===== CLUB PRICING =====

    public ParkingPaymentController.PaymentComputation computeFinalAmountForSession(int sessionId) {
        return paymentController.computeFinalAmountForSession(sessionId);
    }
}
