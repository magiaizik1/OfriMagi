
package control;

import boundary.ConveyorsControllerPort;
import boundary.GateSensorPort;
import boundary.PaymentGatewayPort;
import boundary.SmsGatewayPort;
import entity.PriceList;

import java.sql.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * ParkingSessionManagementController
 *
 * ✅ Internal system logic:
 * - Creates/updates ParkingSession in DB
 * - Finds suitable conveyor + nearest spot (optimization: same floor preferred, then min dist^2)
 * - Calculates price by PriceHistory/PriceList
 * - Applies club rules (discount + free first session in join month in preferred lot)
 *
 * ✅ External actors (do NOT implement here; just call ports):
 * - Gate Sensor (GS)          -> GateSensorPort
 * - Conveyors Controller (CC) -> ConveyorsControllerPort
 * - Payment Gateway (PG)      -> PaymentGatewayPort
 * - SMS Provider              -> SmsGatewayPort
 *
 * ⚠️ Important (per your DB reality):
 * - Conveyor is "available" if: isActive=True AND Status='OPERATIONAL' AND NOT in active ParkingSession
 * - We do NOT require LastStatus='AVAILABLE' (because you said it doesn't exist as a real state)
 */
public class ParkingSessionManagementController {

    private final AccessDb db;
    private final Random rnd = new Random();

    // ===== External Ports (nullable) =====
    private final ConveyorsControllerPort conveyorsPort; // CC
    private final GateSensorPort gateSensorPort;         // GS
    private final PaymentGatewayPort paymentGatewayPort; // PG
    private final SmsGatewayPort smsGatewayPort;         // SMS

    // Club: fixed discount for ALL club members (except when FREE applies)
    private static final double CLUB_DISCOUNT_RATE = 0.05; // 5%

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

    /** Convenience ctor: no external simulation (ports are null). */
    public ParkingSessionManagementController(AccessDb db) {
        this(db, null, null, null, null);
    }

    // =========================
    // ===== EXISTING API ======
    // =========================

    /** Session is created AFTER entry, when conveyor + spot were assigned. */
    public void startParkingSession(int parkingLotId, int vehicleId, int spotId, int conveyorId) throws Exception {

        String sql =
                "INSERT INTO ParkingSession(" +
                        "startTime, parkingLotID, vehicleID, parkingSpotID, conveyorID, state" +
                        ") VALUES (?, ?, ?, ?, ?, ?)";

        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now()));
            ps.setInt(2, parkingLotId);
            ps.setInt(3, vehicleId);
            ps.setInt(4, spotId);
            ps.setInt(5, conveyorId);
            ps.setString(6, "MOVING_TO_PARKING");

            ps.executeUpdate();
        }
    }

    /** Used by flow logic. */
    public void updateSessionState(int sessionId, String newState) throws Exception {

        String sql =
                "UPDATE ParkingSession SET state=? " +
                        "WHERE ID=? AND endTime IS NULL";

        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setString(1, newState);
            ps.setInt(2, sessionId);
            ps.executeUpdate();
        }
    }

    /** Internal finalization (endTime + COMPLETED). */
    public void endParkingSession(int sessionId) throws Exception {

        String sql =
                "UPDATE ParkingSession " +
                        "SET endTime=?, state=? " +
                        "WHERE ID=? AND endTime IS NULL";

        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now()));
            ps.setString(2, "COMPLETED");
            ps.setInt(3, sessionId);
            ps.executeUpdate();
        }
    }

    /** EXISTING UI – 6 columns. */
    public List<Object[]> getSessionsByParkingLot(int parkingLotId) {

        List<Object[]> list = new ArrayList<>();

        String sql =
                "SELECT ID, startTime, endTime, vehicleID, parkingSpotID, conveyorID " +
                        "FROM ParkingSession WHERE parkingLotID=? ORDER BY startTime DESC";

        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setInt(1, parkingLotId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new Object[]{
                            rs.getInt("ID"),
                            rs.getTimestamp("startTime"),
                            rs.getTimestamp("endTime"),
                            rs.getInt("vehicleID"),
                            rs.getObject("parkingSpotID"),
                            rs.getObject("conveyorID")
                    });
                }
            }

        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return list;
    }

    /**
     * MANAGER VIEW:
     * ID | Start | End | Vehicle | Spot | Conveyor | Amount | Rate
     *
     * If Receipt doesn't exist but session ended, computes amount online (including club rules).
     */
    public List<Object[]> getSessionsByParkingLotForManager(int parkingLotId) {

        List<Object[]> list = new ArrayList<>();

        String sql =
                "SELECT s.ID, s.startTime, s.endTime, s.vehicleID, s.parkingSpotID, s.conveyorID, " +
                        "       r.finalAmount, r.appliedRate " +
                        "FROM ParkingSession s " +
                        "LEFT JOIN Receipt r ON r.parkingsessionID = s.ID " +
                        "WHERE s.parkingLotID=? " +
                        "ORDER BY s.startTime DESC";

        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setInt(1, parkingLotId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {

                    int sessionId = rs.getInt("ID");
                    Timestamp startTs = rs.getTimestamp("startTime");
                    Timestamp endTs = rs.getTimestamp("endTime");

                    Double amount = null;
                    String rate = rs.getString("appliedRate");

                    Object amtObj = rs.getObject("finalAmount");
                    if (amtObj != null) amount = toDouble(amtObj);

                    if (amount == null && endTs != null) {
                        PaymentComputation comp = computeFinalAmountForSessionTx(c, sessionId);
                        amount = comp.finalAmount;
                        rate = comp.appliedRate;
                    }

                    list.add(new Object[]{
                            sessionId,
                            startTs,
                            endTs,
                            rs.getInt("vehicleID"),
                            rs.getObject("parkingSpotID"),
                            rs.getObject("conveyorID"),
                            amount,
                            rate
                    });
                }
            }

        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return list;
    }

    /**
     * Receipt is created ONLY when client requests it (per story).
     * Payment itself is handled outside (PG).
     */
    public void generateReceipt(int sessionId) {

        try (Connection c = db.open()) {

            if (receiptExists(c, sessionId)) {
                throw new RuntimeException("Receipt already exists");
            }

            SessionCore s = loadSessionCore(c, sessionId);
            if (s.end == null) throw new RuntimeException("Session still active");

            PaymentComputation comp = computeFinalAmountForSessionTx(c, sessionId);

            String sql =
                    "INSERT INTO Receipt(parkingsessionID, finalAmount, paymentDate, appliedRate) " +
                            "VALUES(?,?,?,?)";

            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, sessionId);
                ps.setDouble(2, comp.finalAmount);
                ps.setTimestamp(3, Timestamp.valueOf(LocalDateTime.now()));
                ps.setString(4, comp.appliedRate);
                ps.executeUpdate();
            }

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // =========================
    // ===== ENTRY FLOW (DB) ===
    // =========================

    /** Result for sensor/console simulation. */
    public static class SensorArrivalResult {
        public final String outcome; // OK / WAITING_FOR_CONVEYOR / LOT_FULL / NO_VEHICLE
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

    /**
     * Entry flow (per story, DB-backed):
     * 1) Pick a free vehicle (not in active session)
     * 2) Find an available conveyor in lot that can carry weight
     * 3) Find nearest available spot that fits vehicle size
     * 4) Create ParkingSession with state MOVING_TO_PARKING
     * 5) Decrement available spaces (if your column exists)
     * 6) External (optional):
     *    - GS openBarrier
     *    - CC moveToParkingSpot and callback to mark PARKED + update conveyor position
     */
    public SensorArrivalResult simulateVehicleArrivalAndPark(int parkingLotId) throws Exception {

        Integer vehicleId;
        Integer conveyorId;
        Integer spotId;
        int sessionId;

        try (Connection c = db.open()) {
            c.setAutoCommit(false);

            vehicleId = pickRandomFreeVehicle(c);
            if (vehicleId == null) {
                c.rollback();
                return new SensorArrivalResult("NO_VEHICLE", parkingLotId, null, null, null, null);
            }

            VehicleData v = getVehicleData(c, vehicleId);

            conveyorId = findAvailableConveyorId(c, parkingLotId, v.weight);
            if (conveyorId == null) {
                c.rollback();
                return new SensorArrivalResult("WAITING_FOR_CONVEYOR", parkingLotId, vehicleId, null, null, null);
            }

            ConveyorLoc cl = getConveyorLoc(c, conveyorId);

            spotId = findNearestAvailableSpotId(c, parkingLotId, v.size, cl.floor, cl.x, cl.y);
            if (spotId == null) {
                c.rollback();
                return new SensorArrivalResult("LOT_FULL", parkingLotId, vehicleId, conveyorId, null, null);
            }

            // Optional: mark LastStatus for auditing/debug (NOT used as "availability" rule)
            setConveyorLastStatus(c, conveyorId, "BUSY");

            sessionId = insertParkingSessionReturningId(
                    c, parkingLotId, vehicleId, spotId, conveyorId, "MOVING_TO_PARKING"
            );

            decrementLotSpacesIfPossible(c, parkingLotId);

            c.commit();
        }

        // ===== External calls (optional) =====
        if (gateSensorPort != null) {
            gateSensorPort.openBarrier(parkingLotId);
        }

        // In the real story: CC moves vehicle and notifies system on completion + new conveyor location.
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

                                updateSessionStateTx(c2, finalSessionId, "PARKED");
                                setConveyorLastStatus(c2, finalConveyorId, "IDLE");
                                updateConveyorPositionTx(c2, finalConveyorId, newX, newY, newFloor);

                                c2.commit();
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }

                            // SMS is external
                            try (Connection c3 = db.open()) {
                                Integer customerId = getCustomerIdByVehicle(c3, finalVehicleId);
                                if (customerId != null && smsGatewayPort != null) {
                                    String phone = getCustomerPhoneSafe(c3, customerId);
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
    // ===== EXIT FLOW =========
    // =========================

    /**
     * Client requests to end parking (UI):
     * - Validate active session
     * - Move to MOVING_TO_EXIT
     * - Assign a vacant conveyor (we choose one here because port requires conveyorId)
     * - Ask CC to move to gate -> callback sets WAITING_FOR_PAYMENT + updates conveyor location
     */
    public void requestExit(int sessionId) throws Exception {

        SessionCore s;
        Integer assignedConveyorId;

        try (Connection c = db.open()) {
            c.setAutoCommit(false);

            s = loadSessionCore(c, sessionId);
            if (s.end != null) {
                c.rollback();
                throw new RuntimeException("Session already completed");
            }

            VehicleData v = getVehicleData(c, s.vehicleId);

            // Choose a vacant conveyor in same lot that can carry this vehicle
            assignedConveyorId = findAvailableConveyorId(c, s.parkingLotId, v.weight);
            if (assignedConveyorId == null) {
                c.rollback();
                throw new RuntimeException("No available conveyor for exit");
            }

            // Update session: assign conveyor + MOVING_TO_EXIT
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE ParkingSession SET conveyorID=?, state=? WHERE ID=? AND endTime IS NULL")) {
                ps.setInt(1, assignedConveyorId);
                ps.setString(2, "MOVING_TO_EXIT");
                ps.setInt(3, sessionId);

                int updated = ps.executeUpdate();
                if (updated == 0) {
                    c.rollback();
                    throw new RuntimeException("Session not found or already completed");
                }
            }

            setConveyorLastStatus(c, assignedConveyorId, "BUSY");

            c.commit();
        }

        // Ask CC to move to gate (external)
        if (conveyorsPort != null) {
            int finalConveyorId = assignedConveyorId;
            SessionCore finalS = s;

            conveyorsPort.moveToGate(sessionId, finalConveyorId, finalS.vehicleId,
                    new ConveyorsControllerPort.MoveCallback() {
                        @Override
                        public void onMoveCompleted(String commandId, int newX, int newY, int newFloor) {
                            try (Connection c2 = db.open()) {
                                c2.setAutoCommit(false);

                                updateSessionStateTx(c2, sessionId, "WAITING_FOR_PAYMENT");
                                updateConveyorPositionTx(c2, finalConveyorId, newX, newY, newFloor);

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

    /**
     * Called when session is WAITING_FOR_PAYMENT:
     * - Calculate amount (until now) by PriceList/History
     * - Apply club rules
     * - Send payment request to PG (external)
     * - On approved -> complete session + open barrier
     * - Receipt is NOT auto-created (client can request generateReceipt)
     */
    public void requestPaymentForExit(int sessionId) {

        try (Connection c = db.open()) {

            SessionCore s = loadSessionCore(c, sessionId);
            if (s.end != null) throw new RuntimeException("Session already completed");

            String state = getSessionState(c, sessionId);
            if (state == null || !"WAITING_FOR_PAYMENT".equalsIgnoreCase(state.trim())) {
                throw new RuntimeException("Session not in WAITING_FOR_PAYMENT (current=" + state + ")");
            }

            LocalDateTime now = LocalDateTime.now();

            PriceList price = getCurrentPriceListAt(c, s.parkingLotId, now);
            if (price == null) throw new RuntimeException("No active price list for lot " + s.parkingLotId);

            long minutes = Duration.between(s.start, now).toMinutes();
            CalcResult base = calculateAmountAndRate(minutes, price);

            Integer customerId = getCustomerIdByVehicle(c, s.vehicleId);
            String phone = (customerId == null) ? "" : safeString(getCustomerPhoneSafe(c, customerId));

            double finalAmount = applyClubBenefitsForExitAmount(
                    c, customerId, s.parkingLotId, s.start, base.amount
            );

            if (paymentGatewayPort == null) {
                // No PG simulation attached -> just leave as is (UI can show finalAmount).
                return;
            }

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

    /** Payment approved externally -> finalize session + release conveyor + open barrier (external). */
    private void confirmPaymentAndExit(int sessionId) throws Exception {

        int lotId;
        Integer conveyorId;

        try (Connection c = db.open()) {
            c.setAutoCommit(false);

            SessionCore s = loadSessionCore(c, sessionId);
            lotId = s.parkingLotId;

            conveyorId = getActiveSessionConveyorId(c, sessionId);

            try (PreparedStatement ps =
                         c.prepareStatement("UPDATE ParkingSession SET endTime=?, state=? WHERE ID=? AND endTime IS NULL")) {
                ps.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now()));
                ps.setString(2, "COMPLETED");
                ps.setInt(3, sessionId);
                ps.executeUpdate();
            }

            if (conveyorId != null) {
                setConveyorLastStatus(c, conveyorId, "IDLE");
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

        SessionCore s = loadSessionCore(c, sessionId);
        if (s.end == null)
            throw new RuntimeException("Session still active, cannot compute final amount");

        PriceList price = getCurrentPriceListAt(c, s.parkingLotId, s.end);
        if (price == null)
            throw new RuntimeException("No active price list for lot " + s.parkingLotId);

        long minutes = Duration.between(s.start, s.end).toMinutes();
        CalcResult base = calculateAmountAndRate(minutes, price);

        Integer customerId = getCustomerIdByVehicle(c, s.vehicleId);
        if (customerId == null) return new PaymentComputation(base.amount, base.rate);

        LocalDateTime joinDate = getMembershipJoinDate(c, customerId);
        if (joinDate == null) return new PaymentComputation(base.amount, base.rate); // not a member

        boolean free = isFreeFirstSessionInRegistrationMonth(
                c, customerId, s.parkingLotId, s.start, joinDate
        );

        if (free) {
            return new PaymentComputation(0.0, base.rate + " + ClubFreeFirstInMonth");
        }

        double discounted = round2(base.amount * (1.0 - CLUB_DISCOUNT_RATE));
        return new PaymentComputation(discounted, base.rate + " + ClubDiscount5%");
    }

    /** For EXIT payment request (until "now"). */
    private double applyClubBenefitsForExitAmount(Connection c, Integer customerId, int parkingLotId,
                                                  LocalDateTime sessionStart, double baseAmount) throws SQLException {

        if (customerId == null) return baseAmount;

        LocalDateTime joinDate = getMembershipJoinDate(c, customerId);
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
    ) throws SQLException {

        YearMonth joinYM = YearMonth.from(joinDate);
        YearMonth sessionYM = YearMonth.from(sessionStart);
        if (!joinYM.equals(sessionYM)) return false;

        // Must be in preferred lots (at time of selectionDate <= sessionStart)
        if (!isPreferredLotAtTime(c, customerId, parkingLotId, sessionStart)) return false;

        // Must be first completed session in that month (before this session)
        return !existsAnyCompletedSessionInJoinMonth(c, customerId, joinYM, sessionStart);
    }

    private boolean isPreferredLotAtTime(Connection c, int customerId, int parkingLotId, LocalDateTime at) throws SQLException {

        // ⚠️ Ensure your table/columns match:
        // PreferredParkingLot(customerID, parkingLotID, selectionDate)
        String sql =
                "SELECT COUNT(*) " +
                        "FROM PreferredParkingLot " +
                        "WHERE customerID=? AND parkingLotID=? AND selectionDate <= ?";

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, customerId);
            ps.setInt(2, parkingLotId);
            ps.setTimestamp(3, Timestamp.valueOf(at));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }
        }
    }

    private boolean existsAnyCompletedSessionInJoinMonth(
            Connection c, int customerId, YearMonth joinYM, LocalDateTime currentSessionStart
    ) throws SQLException {

        LocalDateTime from = joinYM.atDay(1).atStartOfDay();
        LocalDateTime to = joinYM.plusMonths(1).atDay(1).atStartOfDay();

        // We detect customer sessions via Vehicle.customerID association
        String sql =
                "SELECT COUNT(*) " +
                        "FROM ParkingSession s " +
                        "JOIN Vehicle v ON v.ID = s.vehicleID " +
                        "WHERE v.customerID = ? " +
                        "  AND s.endTime IS NOT NULL " +
                        "  AND s.startTime >= ? AND s.startTime < ? " +
                        "  AND s.startTime < ?";

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, customerId);
            ps.setTimestamp(2, Timestamp.valueOf(from));
            ps.setTimestamp(3, Timestamp.valueOf(to));
            ps.setTimestamp(4, Timestamp.valueOf(currentSessionStart));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }
        }
    }

    public static class PaymentComputation {
        public final double finalAmount;
        public final String appliedRate;

        public PaymentComputation(double finalAmount, String appliedRate) {
            this.finalAmount = finalAmount;
            this.appliedRate = appliedRate;
        }
    }

    // =========================
    // ===== DB HELPERS ========
    // =========================

    private String getSessionState(Connection c, int sessionId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT state FROM ParkingSession WHERE ID=?")) {
            ps.setInt(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return rs.getString(1);
            }
        }
    }

    private void updateConveyorPositionTx(Connection c, int conveyorId, int x, int y, int floor) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("UPDATE Conveyor SET X=?, Y=?, Floor=? WHERE ID=?")) {
            ps.setInt(1, x);
            ps.setInt(2, y);
            ps.setInt(3, floor);
            ps.setInt(4, conveyorId);
            ps.executeUpdate();
        }
    }

    private Integer getCustomerIdByVehicle(Connection c, int vehicleId) throws SQLException {
        String sql = "SELECT customerID FROM Vehicle WHERE ID=?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, vehicleId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                Object o = rs.getObject(1);
                if (o == null) return null;
                return rs.getInt(1);
            }
        }
    }

    private LocalDateTime getMembershipJoinDate(Connection c, int customerId) throws SQLException {
        // CustomerClubMembership(customerID, joinDate)
        String sql = "SELECT joinDate FROM CustomerClubMembership WHERE customerID=?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                Timestamp ts = rs.getTimestamp(1);
                return ts == null ? null : ts.toLocalDateTime();
            }
        }
    }

    /**
     * ⚠️ Update the column name if needed (phone / phoneNumber / mobile / mobilePhone etc.)
     * This is the ONLY place you might need to rename a column.
     */
    private String getCustomerPhoneSafe(Connection c, int customerId) {
        try (PreparedStatement ps = c.prepareStatement("SELECT phone FROM Customer WHERE ID=?")) {
            ps.setInt(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return rs.getString(1);
            }
        } catch (Exception e) {
            return null;
        }
    }

    private String safeString(String s) {
        return s == null ? "" : s;
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private Integer pickRandomFreeVehicle(Connection c) throws SQLException {
        String sql =
                "SELECT TOP 1 v.ID " +
                        "FROM Vehicle v " +
                        "WHERE v.ID NOT IN ( " +
                        "   SELECT vehicleID FROM ParkingSession WHERE endTime IS NULL " +
                        ") " +
                        "ORDER BY RND(v.ID)";

        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) return null;
            return rs.getInt(1);
        }
    }

    private VehicleData getVehicleData(Connection c, int vehicleId) throws SQLException {
        String sql = "SELECT ID, size, weight FROM Vehicle WHERE ID=?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, vehicleId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new SQLException("Vehicle not found: " + vehicleId);
                String size = rs.getString("size");
                double weight = rs.getDouble("weight");
                return new VehicleData(vehicleId, normalizeSize(size), weight);
            }
        }
    }

    private String normalizeSize(String s) {
        if (s == null) return "SMALL";
        String t = s.trim().toUpperCase();
        if (t.contains("LARGE")) return "LARGE";
        if (t.contains("MED")) return "MEDIUM";
        return "SMALL";
    }

    /**
     * ✅ Your rule (no AVAILABLE state):
     * Available = isActive=True AND Status='OPERATIONAL' AND MaxWeight ok AND not in active ParkingSession
     */
    private Integer findAvailableConveyorId(Connection c, int parkingLotId, double vehicleWeight) throws SQLException {

        String sql =
                "SELECT TOP 1 c.ID " +
                        "FROM Conveyor c " +
                        "WHERE c.ParkingLotID=? " +
                        "  AND c.isActive=True " +
                        "  AND c.Status='OPERATIONAL' " +
                        "  AND c.MaxWeight >= ? " +
                        "  AND c.ID NOT IN (SELECT conveyorID FROM ParkingSession WHERE endTime IS NULL) " +
                        "ORDER BY c.ID";

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, parkingLotId);
            ps.setDouble(2, vehicleWeight);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return rs.getInt(1);
            }
        }
    }

    private ConveyorLoc getConveyorLoc(Connection c, int conveyorId) throws SQLException {
        String sql = "SELECT X, Y, Floor FROM Conveyor WHERE ID=?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, conveyorId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new SQLException("Conveyor not found: " + conveyorId);
                int x = rs.getInt("X");
                int y = rs.getInt("Y");
                int floor = rs.getInt("Floor");
                return new ConveyorLoc(x, y, floor);
            }
        }
    }

    private Integer findNearestAvailableSpotId(Connection c, int parkingLotId, String vehicleSize,
                                               int conveyorFloor, int conveyorX, int conveyorY) throws SQLException {

        String sql =
                "SELECT s.ID, s.floorNumber, s.X, s.Y, s.size " +
                        "FROM ParkingSpot s " +
                        "WHERE s.parkingLotID=? " +
                        "  AND s.ID NOT IN (SELECT parkingSpotID FROM ParkingSession WHERE endTime IS NULL)";

        Integer bestId = null;
        long bestScore = Long.MAX_VALUE;

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, parkingLotId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int id = rs.getInt("ID");
                    int floor = rs.getInt("floorNumber");
                    int x = rs.getInt("X");
                    int y = rs.getInt("Y");
                    String spotSize = normalizeSize(rs.getString("size"));

                    if (!canSpotFitVehicle(spotSize, vehicleSize)) continue;

                    long dx = (long) x - conveyorX;
                    long dy = (long) y - conveyorY;
                    long dist2 = dx * dx + dy * dy;

                    long floorPenalty = (floor == conveyorFloor) ? 0 : 1_000_000_000L;
                    long score = floorPenalty + dist2;

                    if (score < bestScore) {
                        bestScore = score;
                        bestId = id;
                    }
                }
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

    private void setConveyorLastStatus(Connection c, int conveyorId, String lastStatus) throws SQLException {
        String sql = "UPDATE Conveyor SET LastStatus=? WHERE ID=?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, lastStatus);
            ps.setInt(2, conveyorId);
            ps.executeUpdate();
        }
    }

    private int insertParkingSessionReturningId(Connection c, int parkingLotId, int vehicleId,
                                                int spotId, int conveyorId, String state) throws SQLException {

        String sql =
                "INSERT INTO ParkingSession(startTime, parkingLotID, vehicleID, parkingSpotID, conveyorID, state) " +
                        "VALUES(?,?,?,?,?,?)";

        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now()));
            ps.setInt(2, parkingLotId);
            ps.setInt(3, vehicleId);
            ps.setInt(4, spotId);
            ps.setInt(5, conveyorId);
            ps.setString(6, state);

            ps.executeUpdate();

            int id = -1;
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys != null && keys.next()) id = keys.getInt(1);
            } catch (Exception ignore) {}

            if (id <= 0) {
                try (Statement st = c.createStatement();
                     ResultSet rs = st.executeQuery("SELECT @@IDENTITY")) {
                    if (rs.next()) id = rs.getInt(1);
                }
            }

            if (id <= 0) throw new SQLException("Could not read new ParkingSession ID");
            return id;
        }
    }

    private void updateSessionStateTx(Connection c, int sessionId, String newState) throws SQLException {
        String sql = "UPDATE ParkingSession SET state=? WHERE ID=? AND endTime IS NULL";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, newState);
            ps.setInt(2, sessionId);
            ps.executeUpdate();
        }
    }

    private Integer getActiveSessionConveyorId(Connection c, int sessionId) throws SQLException {
        String sql = "SELECT conveyorID FROM ParkingSession WHERE ID=? AND endTime IS NULL";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                Object o = rs.getObject(1);
                if (o == null) return null;
                return rs.getInt(1);
            }
        }
    }

    private void decrementLotSpacesIfPossible(Connection c, int parkingLotId) throws SQLException {
        // You had this field name: availablaSpaces (typo) - keep it as you had.
        String sql =
                "UPDATE ParkingLot SET availablaSpaces = IIF(availablaSpaces>0, availablaSpaces-1, 0) " +
                        "WHERE ID=?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, parkingLotId);
            ps.executeUpdate();
        }
    }

    private boolean receiptExists(Connection c, int sessionId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM Receipt WHERE parkingsessionID=?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }
        }
    }

    private SessionCore loadSessionCore(Connection c, int sessionId) throws SQLException {

        String sql =
                "SELECT parkingLotID, vehicleID, startTime, endTime " +
                        "FROM ParkingSession WHERE ID=?";

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new SQLException("Session not found: " + sessionId);

                int lotId = rs.getInt("parkingLotID");
                int vehicleId = rs.getInt("vehicleID");
                LocalDateTime start = rs.getTimestamp("startTime").toLocalDateTime();
                Timestamp endTs = rs.getTimestamp("endTime");
                LocalDateTime end = (endTs == null) ? null : endTs.toLocalDateTime();

                return new SessionCore(lotId, vehicleId, start, end);
            }
        }
    }

    private PriceList getCurrentPriceListAt(Connection c, int parkingLotId, LocalDateTime at) throws SQLException {

        String sql =
                "SELECT TOP 1 p.ID, p.year, p.firstHourPrice, p.additionalHourPrice, p.fullDayPrice " +
                        "FROM PriceList p " +
                        "JOIN PriceHistory h ON p.ID = h.priceListID " +
                        "WHERE h.parkingLotID=? " +
                        "AND ? >= h.effectiveFrom " +
                        "AND (h.effectiveTo IS NULL OR ? <= h.effectiveTo) " +
                        "ORDER BY h.effectiveFrom DESC";

        try (PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setInt(1, parkingLotId);
            ps.setTimestamp(2, Timestamp.valueOf(at));
            ps.setTimestamp(3, Timestamp.valueOf(at));

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;

                return new PriceList(
                        rs.getInt("ID"),
                        rs.getInt("year"),
                        toDouble(rs.getObject("firstHourPrice")),
                        toDouble(rs.getObject("additionalHourPrice")),
                        toDouble(rs.getObject("fullDayPrice"))
                );
            }
        }
    }

    private double toDouble(Object o) {
        if (o == null) return 0.0;
        if (o instanceof java.math.BigDecimal)
            return ((java.math.BigDecimal) o).doubleValue();
        if (o instanceof Number)
            return ((Number) o).doubleValue();
        return Double.parseDouble(o.toString());
    }

    private CalcResult calculateAmountAndRate(long minutes, PriceList p) {

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

    private static class SessionCore {
        int parkingLotId;
        int vehicleId;
        LocalDateTime start;
        LocalDateTime end;

        SessionCore(int parkingLotId, int vehicleId, LocalDateTime start, LocalDateTime end) {
            this.parkingLotId = parkingLotId;
            this.vehicleId = vehicleId;
            this.start = start;
            this.end = end;
        }
    }

    private static class CalcResult {
        double amount;
        String rate;

        CalcResult(double amount, String rate) {
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