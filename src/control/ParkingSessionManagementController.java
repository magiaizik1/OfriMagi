package control;

import entity.PriceList;

import java.sql.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ParkingSessionManagementController {

    private final AccessDb db;
    private final Random rnd = new Random();

    public ParkingSessionManagementController(AccessDb db) {
        this.db = db;
    }

    // =========================
    // ===== EXISTING API ======
    // =========================

    // Session is created AFTER gate entry, when conveyor + spot were assigned
    public void startParkingSession(
            int parkingLotId,
            int vehicleId,
            int spotId,
            int conveyorId
    ) throws Exception {

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
            ps.setString(6, "MOVING_TO_PARKING"); // לפי הסיפור

            ps.executeUpdate();
        }
    }

    // Used by conveyor / flow logic
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

    // EXISTING UI – 6 columns
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

    // MANAGER VIEW
    // ID | Start | End | Vehicle | Spot | Conveyor | Amount | Rate
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

                    Timestamp startTs = rs.getTimestamp("startTime");
                    Timestamp endTs = rs.getTimestamp("endTime");

                    Double amount = null;
                    String rate = rs.getString("appliedRate");

                    Object amtObj = rs.getObject("finalAmount");
                    if (amtObj != null) {
                        amount = toDouble(amtObj);
                    }

                    if (amount == null && endTs != null) {

                        PriceList priceList =
                                getCurrentPriceListAt(
                                        c,
                                        parkingLotId,
                                        endTs.toLocalDateTime()
                                );

                        if (priceList != null) {
                            long minutes =
                                    Duration.between(
                                            startTs.toLocalDateTime(),
                                            endTs.toLocalDateTime()
                                    ).toMinutes();

                            CalcResult calc =
                                    calculateAmountAndRate(minutes, priceList);

                            amount = calc.amount;
                            rate = calc.rate;
                        } else {
                            rate = "N/A";
                        }
                    }

                    list.add(new Object[]{
                            rs.getInt("ID"),
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

    public void generateReceipt(int sessionId) {

        try (Connection c = db.open()) {

            if (receiptExists(c, sessionId)) {
                throw new RuntimeException("Receipt already exists");
            }

            ParkingSessionData data = getSessionData(c, sessionId);
            if (data.end == null)
                throw new RuntimeException("Session still active");

            PriceList price =
                    getCurrentPriceListAt(c, data.parkingLotId, data.end);

            if (price == null)
                throw new RuntimeException("No active price list");

            long minutes = Duration.between(data.start, data.end).toMinutes();
            CalcResult calc = calculateAmountAndRate(minutes, price);

            String sql =
                    "INSERT INTO Receipt(parkingsessionID, finalAmount, paymentDate, appliedRate) " +
                            "VALUES(?,?,?,?)";

            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, sessionId);
                ps.setDouble(2, calc.amount);
                ps.setTimestamp(3, Timestamp.valueOf(LocalDateTime.now()));
                ps.setString(4, calc.rate);
                ps.executeUpdate();
            }

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // =========================
    // ===== NEW SENSOR API =====
    // =========================

    /** תוצאת “קליטת רכב” (למסך החיישן) */
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

    /** זרימת כניסה מלאה לפי הסיפור (פר חניון) */
    public SensorArrivalResult simulateVehicleArrivalAndPark(int parkingLotId) throws Exception {

        try (Connection c = db.open()) {
            c.setAutoCommit(false);

            // 1) בוחרים רכב רנדומלי שלא נמצא בסשן פעיל
            Integer vehicleId = pickRandomFreeVehicle(c);
            if (vehicleId == null) {
                c.rollback();
                return new SensorArrivalResult("NO_VEHICLE", parkingLotId, null, null, null, null);
            }

            VehicleData v = getVehicleData(c, vehicleId);

            // 2) מוצאים מסוע זמין באותו חניון (OPERATIONAL + AVAILABLE + weight)
            Integer conveyorId = findAvailableConveyorId(c, parkingLotId, v.weight);
            if (conveyorId == null) {
                // עדכון “רק” סטטוס לוגי של חוסר משאב? (לא חובה ליצור סשן)
                c.rollback();
                return new SensorArrivalResult("WAITING_FOR_CONVEYOR", parkingLotId, vehicleId, null, null, null);
            }

            ConveyorLoc cl = getConveyorLoc(c, conveyorId);

            // 3) מוצאים חניה פנויה הכי קרובה (לפי floor + (x,y)) ומתאימה לגודל
            Integer spotId = findNearestAvailableSpotId(c, parkingLotId, v.size, cl.floor, cl.x, cl.y);
            if (spotId == null) {
                c.rollback();
                return new SensorArrivalResult("LOT_FULL", parkingLotId, vehicleId, conveyorId, null, null);
            }

            // 4) מסמנים מסוע BUSY (LastStatus) + יוצרים סשן (state)
            setConveyorLastStatus(c, conveyorId, "BUSY");

            int sessionId = insertParkingSessionReturningId(
                    c,
                    parkingLotId,
                    vehicleId,
                    spotId,
                    conveyorId,
                    "MOVING_TO_PARKING"
            );

            // 5) עדכון availableSpaces (מינוס 1) – אם את משתמשת בו
            decrementLotSpacesIfPossible(c, parkingLotId);

            c.commit();

            return new SensorArrivalResult("OK", parkingLotId, vehicleId, conveyorId, spotId, sessionId);
        }
    }

    /** סימולציה: לאחר שהמסוע “מסיים להזיז”, מסמנים PARKED ומשחררים מסוע */
    public void markParkingCompleted(int sessionId) throws Exception {
        try (Connection c = db.open()) {
            c.setAutoCommit(false);

            Integer conveyorId = getActiveSessionConveyorId(c, sessionId);
            if (conveyorId == null) {
                c.rollback();
                return;
            }

            updateSessionStateTx(c, sessionId, "PARKED");
            setConveyorLastStatus(c, conveyorId, "AVAILABLE");

            c.commit();
        }
    }

    // =========================
    // ===== NEW DB HELPERS =====
    // =========================

    private Integer pickRandomFreeVehicle(Connection c) throws SQLException {

        // Vehicle שאין עליו סשן פעיל (endTime is null)
        // NOTE: Access תומך ב-RND ו-TOP 1
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

    private Integer findAvailableConveyorId(Connection c, int parkingLotId, double vehicleWeight) throws SQLException {

        // Status גדול: OPERATIONAL
        // LastStatus תת-מצב: AVAILABLE
        // MaxWeight >= weight
        // ועוד תנאי חשוב: מסוע לא נמצא בסשן פעיל (יתר ביטחון)
        String sql =
                "SELECT TOP 1 c.ID " +
                        "FROM Conveyor c " +
                        "WHERE c.ParkingLotID=? " +
                        "  AND c.isActive=True " +
                        "  AND c.Status='OPERATIONAL' " +
                        "  AND c.LastStatus='AVAILABLE' " +
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

        // חניה פנויה = לא קיימת עליה ParkingSession פעיל
        // התאמת גודל: spot.size יכול להכיל vehicle.size (SMALL<=MEDIUM<=LARGE)
        // קרבה: קודם אותו floor, ואז מינימום מרחק ריבועי
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

                    // משקללים floor: אותו floor עדיף תמיד
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
        int s = sizeRank(spotSize);
        int v = sizeRank(vehicleSize);
        return s >= v;
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

            // Access לפעמים לא מחזיר getGeneratedKeys, אז עושים fallback
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
                return rs.getInt(1);
            }
        }
    }

    private void decrementLotSpacesIfPossible(Connection c, int parkingLotId) throws SQLException {
        // לא שוברים כלום: אם שדה availablaSpaces קיים אצלך כמו בפרויקט, זה יעדכן.
        // אם לא קיים/שגיאת שם — את תראי חריגה ותעדכני שם שדה לפי אצלך.
        String sql =
                "UPDATE ParkingLot SET availablaSpaces = IIF(availablaSpaces>0, availablaSpaces-1, 0) " +
                        "WHERE ID=?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, parkingLotId);
            ps.executeUpdate();
        }
    }

    // =========================
    // ===== RECEIPT HELPERS ===
    // =========================

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

    private ParkingSessionData getSessionData(Connection c, int id) throws SQLException {

        String sql = "SELECT startTime, endTime, parkingLotID FROM ParkingSession WHERE ID=?";

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new SQLException("Session not found");

                LocalDateTime start = rs.getTimestamp("startTime").toLocalDateTime();
                Timestamp endTs = rs.getTimestamp("endTime");
                LocalDateTime end =
                        (endTs == null) ? null : endTs.toLocalDateTime();

                return new ParkingSessionData(start, end, rs.getInt("parkingLotID"));
            }
        }
    }

    private PriceList getCurrentPriceListAt(
            Connection c,
            int parkingLotId,
            LocalDateTime at
    ) throws SQLException {

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
                    p.getFirstHourPrice() +
                            (hours - 1) * p.getAdditionalHourPrice(),
                    "AdditionalHours"
            );

        return new CalcResult(p.getFullDayPrice(), "FullDay");
    }

    // ===== inner classes =====

    private static class ParkingSessionData {
        LocalDateTime start;
        LocalDateTime end;
        int parkingLotId;

        ParkingSessionData(LocalDateTime start, LocalDateTime end, int parkingLotId) {
            this.start = start;
            this.end = end;
            this.parkingLotId = parkingLotId;
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
            this.x = x; this.y = y; this.floor = floor;
        }
    }
}