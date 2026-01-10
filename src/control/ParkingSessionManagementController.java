package control;

import boundary.ConveyorsControllerPort;
import boundary.GateSensorPort;
import boundary.PaymentGatewayPort;
import boundary.SmsGatewayPort;
import entity.PriceList;
import entity.ParkingSession;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ParkingSessionManagementController {

    private final AccessDb db;
    private final Random rnd = new Random();

    private final ConveyorsControllerPort conveyorsPort;
    private final GateSensorPort gateSensorPort;
    private final PaymentGatewayPort paymentGatewayPort;
    private final SmsGatewayPort smsGatewayPort;

    private static final double CLUB_DISCOUNT_RATE = 0.05;

    public ParkingSessionManagementController(
            AccessDb db,
            ConveyorsControllerPort conveyorsPort,
            GateSensorPort gateSensorPort,
            PaymentGatewayPort paymentGatewayPort,
            SmsGatewayPort smsGatewayPort
    ) {
        this.db = db;
        this.conveyorsPort = conveyorsPort;
        this.gateSensorPort = gateSensorPort;
        this.paymentGatewayPort = paymentGatewayPort;
        this.smsGatewayPort = smsGatewayPort;
    }

    public ParkingSessionManagementController(AccessDb db) {
        this(db, null, null, null, null);
    }

    // =========================
    // ===== EXISTING API ======
    // =========================

    public void startParkingSession(int parkingLotId, int vehicleId, int spotId, int conveyorId) throws Exception {
        db.insertParkingSession(parkingLotId, vehicleId, spotId, conveyorId, "MOVING_TO_PARKING");
    }

    public void updateSessionState(int sessionId, String newState) throws Exception {
        db.updateSessionState(sessionId, newState);
        ParkingSession s = db.loadParkingSessionById(sessionId);
        if (s != null) s.updateState(newState);
    }

    public void endParkingSession(int sessionId) throws Exception {
        LocalDateTime now = LocalDateTime.now();
        db.closeSession(sessionId, now);
        ParkingSession s = db.loadParkingSessionById(sessionId);
        if (s != null) s.closeSession(now);
    }

    public List<Object[]> getSessionsByParkingLot(int parkingLotId) {
        try {
            return db.getSessionsByParkingLot(parkingLotId);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public List<Object[]> getSessionsByParkingLotForManager(int parkingLotId) {
        try {
            List<Object[]> raw = db.getSessionsByParkingLotForManagerRaw(parkingLotId);
            List<Object[]> out = new ArrayList<>();

            for (Object[] row : raw) {
                int sessionId = (int) row[0];
                java.sql.Timestamp endTs = (java.sql.Timestamp) row[2];

                Double amount = (row[6] == null) ? null : ((Number) row[6]).doubleValue();
                String rate = (String) row[7];

                if (amount == null && endTs != null) {
                    PaymentComputation comp = computeFinalAmountForSession(sessionId);
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

    public void generateReceipt(int sessionId) {
        try (Connection c = db.open()) {
            if (db.receiptExists(c, sessionId)) {
                throw new RuntimeException("Receipt already exists");
            }

            AccessDb.SessionCoreRow s = db.loadSessionCore(c, sessionId);
            if (s.end == null) throw new RuntimeException("Session still active");

            PaymentComputation comp = computeFinalAmountForSessionTx(c, sessionId);
            db.insertReceipt(sessionId, comp.finalAmount, comp.appliedRate);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // =========================
    // ===== ENTRY FLOW ========
    // =========================

    public static class SensorArrivalResult {
        public final String outcome;
        public final Integer parkingLotId;
        public final Integer vehicleId;
        public final Integer conveyorId;
        public final Integer spotId;
        public final Integer sessionId;

        public SensorArrivalResult(String outcome, Integer parkingLotId, Integer vehicleId,
                                   Integer conveyorId, Integer spotId, Integer sessionId) {
            this.outcome = outcome;
            this.parkingLotId = parkingLotId;
            this.vehicleId = vehicleId;
            this.conveyorId = conveyorId;
            this.spotId = spotId;
            this.sessionId = sessionId;
        }
    }

    public SensorArrivalResult simulateVehicleArrivalAndPark(int parkingLotId) throws Exception {

        Integer vehicleId;
        Integer conveyorId;
        Integer spotId;
        int sessionId;

        try (Connection c = db.open()) {
            c.setAutoCommit(false);

            vehicleId = db.pickRandomFreeVehicle(c);
            if (vehicleId == null) {
                c.rollback();
                return new SensorArrivalResult("NO_VEHICLE", parkingLotId, null, null, null, null);
            }

            AccessDb.VehicleDataRow vRow = db.getVehicleData(c, vehicleId);
            VehicleData v = new VehicleData(vRow.id, normalizeSize(vRow.size), vRow.weight);

            conveyorId = db.findAvailableConveyorId(c, parkingLotId, v.weight);
            if (conveyorId == null) {
                c.rollback();
                return new SensorArrivalResult("WAITING_FOR_CONVEYOR", parkingLotId, vehicleId, null, null, null);
            }

            AccessDb.ConveyorLocRow clRow = db.getConveyorLoc(c, conveyorId);
            ConveyorLoc cl = new ConveyorLoc(clRow.x, clRow.y, clRow.floor);

            List<AccessDb.SpotRow> spots = db.getAvailableSpots(c, parkingLotId);
            spotId = chooseNearestSpot(spots, v.size, cl.floor, cl.x, cl.y);
            if (spotId == null) {
                c.rollback();
                return new SensorArrivalResult("LOT_FULL", parkingLotId, vehicleId, conveyorId, null, null);
            }

            db.setConveyorLastStatus(c, conveyorId, "BUSY");
            sessionId = db.insertParkingSessionReturningId(c, parkingLotId, vehicleId, spotId, conveyorId, "MOVING_TO_PARKING");
            db.decrementLotSpacesIfPossible(c, parkingLotId);

            c.commit();
        }

        if (gateSensorPort != null) {
            gateSensorPort.openBarrier(parkingLotId);
        }

        if (conveyorsPort != null) {
            int finalVehicleId = vehicleId;
            int finalConveyorId = conveyorId;
            int finalSpotId = spotId;
            int finalSessionId = sessionId;

            conveyorsPort.moveToParkingSpot(finalSessionId, finalConveyorId, finalVehicleId, finalSpotId,
                    new ConveyorsControllerPort.MoveCallback() {
                        @Override
                        public void onMoveCompleted(String commandId, int newX, int newY, int newFloor) {
                            try (Connection c2 = db.open()) {
                                c2.setAutoCommit(false);

                                db.updateSessionStateTx(c2, finalSessionId, "PARKED");
                                db.setConveyorLastStatus(c2, finalConveyorId, "IDLE");
                                db.updateConveyorPositionTx(c2, finalConveyorId, newX, newY, newFloor);

                                c2.commit();
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }

                            // SMS optional
                            try (Connection c3 = db.open()) {
                                Integer customerId = db.getCustomerIdByVehicle(c3, finalVehicleId);
                                if (customerId != null && smsGatewayPort != null) {
                                    String phone = db.getCustomerPhoneSafe(c3, customerId);
                                    if (phone != null && !phone.isEmpty()) {
                                        smsGatewayPort.sendSms(
                                                phone,
                                                "ParkWise: vehicle " + finalVehicleId +
                                                        " parked at lot " + parkingLotId +
                                                        ", spot " + finalSpotId +
                                                        " (session " + finalSessionId + ")"
                                        );
                                    }
                                }
                            } catch (Exception ignore) {}
                        }

                        @Override
                        public void onMoveFailed(String commandId, String reason) {
                            try {
                                updateSessionState(finalSessionId, "ERROR_MOVE_TO_PARKING");
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        }
                    });
        }

        return new SensorArrivalResult("OK", parkingLotId, vehicleId, conveyorId, spotId, sessionId);
    }

    // =========================
    // ===== CLIENT UI FLOW ====
    // =========================

 // =========================
 // ===== CLIENT UI FLOW =====
 // =========================

 /** Client enters phone -> system shows active parking details. */
 public List<String> getActiveParkingDetailsByPhone(String phoneNumber) {

     String phone = safeString(phoneNumber).trim();

     if (phone.isEmpty())
         return List.of("❗ Please enter phone number.");
     if (!phone.matches("\\d{9,10}"))
         return List.of("❗ Phone must contain 9-10 digits (numbers only).");

     try {

         List<Object[]> rows = db.getActiveParkingDetailsByPhoneRaw(phone);

         if (rows.isEmpty())
             return List.of("No active parking sessions for this phone.");

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

     } catch (SQLException e) {
         return List.of("❌ Database error while searching phone. (Check Customer.mobilePhon column name)");
     } catch (Exception e) {
         return List.of("❌ Unexpected error: " + e.getMessage());
     }
 }


    public String requestEndParkingByVehicleAndPhone(String vehicleNumber, String phoneNumber) {
        String phone = safeString(phoneNumber).trim();
        String vehicleStr = safeString(vehicleNumber).trim();

        if (phone.isEmpty() || vehicleStr.isEmpty()) return "❗ Enter phone + vehicle number.";
        if (!phone.matches("\\d{9,10}")) return "❗ Phone must contain 9-10 digits (numbers only).";
        if (!vehicleStr.matches("\\d+")) return "❗ Vehicle number must be digits only.";

        try (Connection c = db.open()) {
            int vehicleId = Integer.parseInt(vehicleStr);

            Integer customerId = db.getCustomerIdByVehicle(c, vehicleId);
            if (customerId == null) return "Vehicle is not associated with any customer.";

            String realPhone = safeString(db.getCustomerPhoneSafe(c, customerId)).trim();
            if (realPhone.isEmpty()) return "Customer has no phone stored in DB.";
            if (!realPhone.equals(phone)) return "Phone validation failed (phone does not match this vehicle).";

            Integer sessionId = db.getActiveSessionIdByVehicle(c, vehicleId);
            if (sessionId == null) return "No active parking session found for this vehicle.";

            requestExit(sessionId);

            return "✅ Exit requested. Vehicle is being transferred to the gate.\n" +
                    "When state becomes WAITING_FOR_PAYMENT, click Pay Now.";

        } catch (Exception e) {
            return "❌ Failed to request exit: " + e.getMessage();
        }
    }

    public String requestPaymentByVehicleAndPhone(String vehicleNumber, String phoneNumber) {
        String phone = safeString(phoneNumber).trim();
        String vehicleStr = safeString(vehicleNumber).trim();

        if (phone.isEmpty() || vehicleStr.isEmpty()) return "❗ Enter phone + vehicle number.";
        if (!phone.matches("\\d{9,10}")) return "❗ Phone must contain 9-10 digits (numbers only).";
        if (!vehicleStr.matches("\\d+")) return "❗ Vehicle number must be digits only.";

        try (Connection c = db.open()) {
            int vehicleId = Integer.parseInt(vehicleStr);

            Integer customerId = db.getCustomerIdByVehicle(c, vehicleId);
            if (customerId == null) return "Vehicle is not associated with any customer.";

            String realPhone = safeString(db.getCustomerPhoneSafe(c, customerId)).trim();
            if (realPhone.isEmpty()) return "Customer has no phone stored in DB.";
            if (!realPhone.equals(phone)) return "Phone validation failed (phone does not match this vehicle).";

            Integer sessionId = db.getActiveSessionIdByVehicle(c, vehicleId);
            if (sessionId == null) return "No active parking session found for this vehicle.";

            String st = db.getSessionState(c, sessionId);
            if (st == null || !"WAITING_FOR_PAYMENT".equalsIgnoreCase(st.trim())) {
                return "Payment is not available yet.\nCurrent state: " + st + " (expected WAITING_FOR_PAYMENT).";
            }

            requestPaymentForExit(sessionId);
            return "✅ Payment requested for session #" + sessionId;

        } catch (Exception e) {
            return "❌ Failed to request payment: " + e.getMessage();
        }
    }

    public String requestReceiptByVehicleAndPhone(String vehicleNumber, String phoneNumber) {
        try (Connection c = db.open()) {
            int vehicleId = Integer.parseInt(vehicleNumber.trim());

            Integer customerId = db.getCustomerIdByVehicle(c, vehicleId);
            if (customerId == null) return "Vehicle is not linked to any customer.";

            String realPhone = safeString(db.getCustomerPhoneSafe(c, customerId)).trim();
            String reqPhone = safeString(phoneNumber).trim();
            if (realPhone.isEmpty() || reqPhone.isEmpty() || !realPhone.equals(reqPhone)) {
                return "Phone validation failed. Cannot issue receipt.";
            }

            Integer lastSessionId = db.getLastSessionIdByVehicle(c, vehicleId);
            if (lastSessionId == null) return "No session found for this vehicle.";

            AccessDb.SessionCoreRow s = db.loadSessionCore(c, lastSessionId);
            if (s.end == null) return "Session is still active. Receipt is available only after parking ends.";

            generateReceipt(lastSessionId);
            return "Receipt generated for session #" + lastSessionId;

        } catch (Exception e) {
            return "❌ " + e.getMessage();
        }
    }

    // =========================
    // ===== EXIT FLOW =========
    // =========================

    public void requestExit(int sessionId) throws Exception {

        AccessDb.SessionCoreRow s;
        Integer assignedConveyorId;

        try (Connection c = db.open()) {
            c.setAutoCommit(false);

            s = db.loadSessionCore(c, sessionId);
            if (s.end != null) {
                c.rollback();
                throw new RuntimeException("Session already completed");
            }

            AccessDb.VehicleDataRow vRow = db.getVehicleData(c, s.vehicleId);
            VehicleData v = new VehicleData(vRow.id, normalizeSize(vRow.size), vRow.weight);

            assignedConveyorId = db.findAvailableConveyorId(c, s.parkingLotId, v.weight);
            if (assignedConveyorId == null) {
                c.rollback();
                throw new RuntimeException("No available conveyor for exit");
            }

            db.assignConveyorAndSetStateForExit(c, sessionId, assignedConveyorId, "MOVING_TO_EXIT");
            db.setConveyorLastStatus(c, assignedConveyorId, "BUSY");

            c.commit();
        }

        if (conveyorsPort != null) {
            int finalConveyorId = assignedConveyorId;

            conveyorsPort.moveToGate(sessionId, finalConveyorId, s.vehicleId,
                    new ConveyorsControllerPort.MoveCallback() {
                        @Override
                        public void onMoveCompleted(String commandId, int newX, int newY, int newFloor) {
                            try (Connection c2 = db.open()) {
                                c2.setAutoCommit(false);

                                db.updateSessionStateTx(c2, sessionId, "WAITING_FOR_PAYMENT");
                                db.updateConveyorPositionTx(c2, finalConveyorId, newX, newY, newFloor);

                                c2.commit();
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        }

                        @Override
                        public void onMoveFailed(String commandId, String reason) {
                            try {
                                updateSessionState(sessionId, "ERROR_MOVE_TO_GATE");
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        }
                    });
        }
    }

    public void requestPaymentForExit(int sessionId) {
        try (Connection c = db.open()) {

            AccessDb.SessionCoreRow s = db.loadSessionCore(c, sessionId);
            if (s.end != null) throw new RuntimeException("Session already completed");

            String state = db.getSessionState(c, sessionId);
            if (state == null || !"WAITING_FOR_PAYMENT".equalsIgnoreCase(state.trim())) {
                throw new RuntimeException("Session not in WAITING_FOR_PAYMENT (current=" + state + ")");
            }

            LocalDateTime now = LocalDateTime.now();

            PriceList price = db.getCurrentPriceListAt(c, s.parkingLotId, now);
            if (price == null) throw new RuntimeException("No active price list for lot " + s.parkingLotId);

            long minutes = Duration.between(s.start, now).toMinutes();
            CalcResult base = calculateAmountAndRate(minutes, price);

            Integer customerId = db.getCustomerIdByVehicle(c, s.vehicleId);
            String phone = (customerId == null) ? "" : safeString(db.getCustomerPhoneSafe(c, customerId));

            double finalAmount = applyClubBenefitsForExitAmount(c, customerId, s.parkingLotId, s.start, base.amount);

            if (paymentGatewayPort == null) return;

            int finalCustomerId = (customerId == null) ? 0 : customerId;
            paymentGatewayPort.requestPayment(
                    sessionId,
                    finalCustomerId,
                    phone,
                    finalAmount,
                    new PaymentGatewayPort.PaymentCallback() {
                        @Override
                        public void onApproved(String paymentId) {
                            try {
                                confirmPaymentAndExit(sessionId);
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        }

                        @Override
                        public void onDeclined(String paymentId, String reason) {
                            try {
                                updateSessionState(sessionId, "PAYMENT_DECLINED");
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        }
                    }
            );

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void confirmPaymentAndExit(int sessionId) throws Exception {

        int lotId;
        Integer conveyorId;

        try (Connection c = db.open()) {
            c.setAutoCommit(false);

            AccessDb.SessionCoreRow s = db.loadSessionCore(c, sessionId);
            lotId = s.parkingLotId;

            conveyorId = db.getActiveSessionConveyorId(c, sessionId);

            db.completeSessionNow(c, sessionId);

            if (conveyorId != null) {
                db.setConveyorLastStatus(c, conveyorId, "IDLE");
            }

            c.commit();
        }

        if (gateSensorPort != null) {
            gateSensorPort.openBarrier(lotId);
        }
    }

    // =========================
    // ===== CLUB PRICING ======
    // =========================

    public PaymentComputation computeFinalAmountForSession(int sessionId) {
        try (Connection c = db.open()) {
            return computeFinalAmountForSessionTx(c, sessionId);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private PaymentComputation computeFinalAmountForSessionTx(Connection c, int sessionId) throws Exception {

        AccessDb.SessionCoreRow s = db.loadSessionCore(c, sessionId);
        if (s.end == null)
            throw new RuntimeException("Session still active, cannot compute final amount");

        PriceList price = db.getCurrentPriceListAt(c, s.parkingLotId, s.end);
        if (price == null)
            throw new RuntimeException("No active price list for lot " + s.parkingLotId);

        long minutes = Duration.between(s.start, s.end).toMinutes();
        CalcResult base = calculateAmountAndRate(minutes, price);

        Integer customerId = db.getCustomerIdByVehicle(c, s.vehicleId);
        if (customerId == null) return new PaymentComputation(base.amount, base.rate);

        LocalDateTime joinDate = db.getMembershipJoinDate(c, customerId);
        if (joinDate == null) return new PaymentComputation(base.amount, base.rate);

        boolean free = isFreeFirstSessionInRegistrationMonth(c, customerId, s.parkingLotId, s.start, joinDate);
        if (free) return new PaymentComputation(0.0, base.rate + " + ClubFreeFirstInMonth");

        double discounted = round2(base.amount * (1.0 - CLUB_DISCOUNT_RATE));
        return new PaymentComputation(discounted, base.rate + " + ClubDiscount5%");
    }

    private double applyClubBenefitsForExitAmount(Connection c, Integer customerId, int parkingLotId,
                                                  LocalDateTime sessionStart, double baseAmount) throws Exception {

        if (customerId == null) return baseAmount;

        LocalDateTime joinDate = db.getMembershipJoinDate(c, customerId);
        if (joinDate == null) return baseAmount;

        boolean free = isFreeFirstSessionInRegistrationMonth(c, customerId, parkingLotId, sessionStart, joinDate);
        if (free) return 0.0;

        return round2(baseAmount * (1.0 - CLUB_DISCOUNT_RATE));
    }

    private boolean isFreeFirstSessionInRegistrationMonth(
            Connection c,
            int customerId,
            int parkingLotId,
            LocalDateTime sessionStart,
            LocalDateTime joinDate
    ) throws Exception {

        YearMonth joinYM = YearMonth.from(joinDate);
        YearMonth sessionYM = YearMonth.from(sessionStart);
        if (!joinYM.equals(sessionYM)) return false;

        if (!db.isPreferredLotAtTime(c, customerId, parkingLotId, sessionStart)) return false;

        return !db.existsAnyCompletedSessionInJoinMonth(c, customerId, joinYM, sessionStart);
    }

    // =========================
    // ===== UTIL / LOGIC ======
    // =========================

    private Integer chooseNearestSpot(List<AccessDb.SpotRow> spots, String vehicleSize,
                                      int conveyorFloor, int conveyorX, int conveyorY) {

        Integer bestId = null;
        long bestScore = Long.MAX_VALUE;

        for (AccessDb.SpotRow s : spots) {
            String spotSize = normalizeSize(s.size);
            if (!canSpotFitVehicle(spotSize, vehicleSize)) continue;

            long dx = (long) s.x - conveyorX;
            long dy = (long) s.y - conveyorY;
            long dist2 = dx * dx + dy * dy;

            long floorPenalty = (s.floor == conveyorFloor) ? 0 : 1_000_000_000L;
            long score = floorPenalty + dist2;

            if (score < bestScore) {
                bestScore = score;
                bestId = s.id;
            }
        }

        return bestId;
    }

    private boolean canSpotFitVehicle(String spotSize, String vehicleSize) {
        return sizeRank(spotSize) >= sizeRank(vehicleSize);
    }

    private int sizeRank(String size) {
        String t = (size == null) ? "SMALL" : size.trim().toUpperCase();
        if (t.contains("LARGE")) return 3;
        if (t.contains("MED")) return 2;
        return 1;
    }

    private String normalizeSize(String s) {
        if (s == null) return "SMALL";
        String t = s.trim().toUpperCase();
        if (t.contains("LARGE")) return "LARGE";
        if (t.contains("MED")) return "MEDIUM";
        return "SMALL";
    }

    private String safeString(String s) {
        return s == null ? "" : s;
    }

    public double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    public CalcResult calculateAmountAndRate(long minutes, PriceList p) {
        long hours = (long) Math.ceil(minutes / 60.0);

        if (hours <= 1)
            return new CalcResult(p.getFirstHourPrice(), "FirstHour");

        if (hours < 24)
            return new CalcResult(
                    p.getFirstHourPrice() + (hours - 1) * p.getAdditionalHourPrice(),
                    "AdditionalHours"
            );

        return new CalcResult(p.getFullDayPrice(), "FullDay");
    }

    // =========================
    // ===== INNER CLASSES =====
    // =========================

    public static class PaymentComputation {
        public final double finalAmount;
        public final String appliedRate;

        public PaymentComputation(double finalAmount, String appliedRate) {
            this.finalAmount = finalAmount;
            this.appliedRate = appliedRate;
        }
    }

    public static class CalcResult {
        public double amount;
        public String rate;

        public CalcResult(double amount, String rate) {
            this.amount = amount;
            this.rate = rate;
        }
    }

    private static class VehicleData {
        int id;
        String size;
        double weight;

        VehicleData(int id, String size, double weight) {
            this.id = id;
            this.size = size;
            this.weight = weight;
        }
    }

    private static class ConveyorLoc {
        int x, y, floor;
        ConveyorLoc(int x, int y, int floor) {
            this.x = x;
            this.y = y;
            this.floor = floor;
        }
    }
}
