package control;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

class ParkingSessionController {

    private final AccessDb db;

    ParkingSessionController(AccessDb db) {
        this.db = db;
    }

    // =========================
    // ===== CORE API ==========
    // =========================

    public void startParkingSession(int parkingLotId, int vehicleId, int spotId, int conveyorId) throws Exception {
        db.insertParkingSession(parkingLotId, vehicleId, spotId, conveyorId, "MOVING_TO_PARKING");
    }

    public void updateSessionState(int sessionId, String newState) throws Exception {
        db.updateSessionState(sessionId, newState);
    }

    public void endParkingSession(int sessionId) throws Exception {
        LocalDateTime now = LocalDateTime.now();
        db.closeSession(sessionId, now);
    }

    public List<Object[]> getSessionsByParkingLot(int parkingLotId) {
        try {
            return db.getSessionsByParkingLot(parkingLotId);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public List<Object[]> getSessionsByParkingLotForManager(int parkingLotId,
                                                            ParkingPaymentController paymentController) {
        try {
            List<Object[]> raw = db.getSessionsByParkingLotForManagerRaw(parkingLotId);
            List<Object[]> out = new ArrayList<>();

            for (Object[] row : raw) {
                int sessionId = (int) row[0];
                java.sql.Timestamp endTs = (java.sql.Timestamp) row[2];

                Double amount = (row[6] == null) ? null : ((Number) row[6]).doubleValue();
                String rate = (String) row[7];

                if (amount == null && endTs != null) {
                    ParkingPaymentController.PaymentComputation comp =
                            paymentController.computeFinalAmountForSession(sessionId);
                    amount = comp.finalAmount;
                    rate = comp.appliedRate;
                }

                out.add(new Object[]{ row[0], row[1], row[2], row[3], row[4], row[5], amount, rate });
            }

            return out;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // =========================
    // ===== CLIENT ============
    // =========================

    public List<String> getActiveParkingDetailsByPhone(String phone) {
        try {
            List<Object[]> rows = db.getActiveParkingDetailsByPhoneRaw(phone);
            List<String> out = new ArrayList<>();

            for (Object[] r : rows) {
                out.add(
                        "Active session #" + r[0] +
                                " | lot=" + r[1] +
                                " | vehicle=" + r[2] +
                                " | spot=" + r[3] +
                                " | state=" + r[4] +
                                " | start=" + r[5]
                );
            }
            return out;

        } catch (Exception e) {
            return List.of("❌ Database error.");
        }
    }
}
