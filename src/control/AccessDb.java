package control;

import entity.ParkingSession;
import entity.PriceList;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

public final class AccessDb {

    private String accdbPath;

    public AccessDb(String accdbPath) {
        this.accdbPath = accdbPath;
    }

    // =========================
    // ===== CONNECTION ========
    // =========================

    public Connection open() throws SQLException {
        Path resolved = resolveAccdbPath(accdbPath);

        if (!Files.exists(resolved)) {
            throw new SQLException("Access DB file not found at: " + resolved.toAbsolutePath());
        }

        String url = "jdbc:ucanaccess://" + resolved.toAbsolutePath();
        return DriverManager.getConnection(url);
    }

    private Path resolveAccdbPath(String path) {
        Path p = Paths.get(path);
        if (p.isAbsolute()) return p;

        // 1) Try working directory (Eclipse)
        Path runDir = Paths.get(System.getProperty("user.dir")).resolve(path);
        if (Files.exists(runDir)) return runDir;

        // 2) Try folder where the runnable JAR is located (double-click / ZIP extracted)
        Path jarDir = getJarDir();
        if (jarDir != null) {
            // a) jarDir + the given relative path (db/xxx.accdb)
            Path jarTry = jarDir.resolve(path);
            if (Files.exists(jarTry)) return jarTry;

            // b) if accdb is next to the jar: jarDir/parkwise_OfriMagi.accdb
            Path byName = jarDir.resolve(p.getFileName().toString());
            if (Files.exists(byName)) return byName;

            // c) if jar is inside subfolder, also try parent
            Path parent = jarDir.getParent();
            if (parent != null) {
                Path parentTry = parent.resolve(path);
                if (Files.exists(parentTry)) return parentTry;

                Path parentByName = parent.resolve(p.getFileName().toString());
                if (Files.exists(parentByName)) return parentByName;
            }
        }

        // 3) fallback
        return p;
    }

    private Path getJarDir() {
        try {
            URI uri = AccessDb.class.getProtectionDomain().getCodeSource().getLocation().toURI();
            Path location = Paths.get(uri);

            // Running from a JAR file: .../runneble jar/Whatever.jar
            if (Files.isRegularFile(location)) {
                return location.getParent();
            }

            // Running from Eclipse / classes folder
            return location;
        } catch (Exception e) {
            return null;
        }
    }

    // =========================================================
    // ===== DTOs ==============================================
    // =========================================================

    public static final class VehicleDataRow {
        public final int id;
        public final String size;
        public final double weight;

        public VehicleDataRow(int id, String size, double weight) {
            this.id = id;
            this.size = size;
            this.weight = weight;
        }
    }

    public static final class ConveyorLocRow {
        public final int x, y, floor;

        public ConveyorLocRow(int x, int y, int floor) {
            this.x = x;
            this.y = y;
            this.floor = floor;
        }
    }

    public static final class SpotRow {
        public final int id;
        public final int floor;
        public final int x;
        public final int y;
        public final String size;

        public SpotRow(int id, int floor, int x, int y, String size) {
            this.id = id;
            this.floor = floor;
            this.x = x;
            this.y = y;
            this.size = size;
        }
    }

    public static final class SessionCoreRow {
        public final int parkingLotId;
        public final int vehicleId;
        public final LocalDateTime start;
        public final LocalDateTime end;

        public SessionCoreRow(int parkingLotId, int vehicleId, LocalDateTime start, LocalDateTime end) {
            this.parkingLotId = parkingLotId;
            this.vehicleId = vehicleId;
            this.start = start;
            this.end = end;
        }
    }

    // =========================================================
    // ===== ParkingSession CRUD ===============================
    // =========================================================

    public void insertParkingSession(int parkingLotId, int vehicleId, int spotId, int conveyorId, String state) throws SQLException {
        try (Connection c = open()) {
            insertParkingSessionTx(c, parkingLotId, vehicleId, spotId, conveyorId, state);
        }
    }

    public void insertParkingSessionTx(Connection c, int parkingLotId, int vehicleId, int spotId, int conveyorId, String state) throws SQLException {
        String sql =
                "INSERT INTO ParkingSession(startTime, parkingLotID, vehicleID, parkingSpotID, conveyorID, state) " +
                        "VALUES (?, ?, ?, ?, ?, ?)";

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now()));
            ps.setInt(2, parkingLotId);
            ps.setInt(3, vehicleId);
            ps.setInt(4, spotId);
            ps.setInt(5, conveyorId);
            ps.setString(6, state);
            ps.executeUpdate();
        }
    }

    public ParkingSession loadParkingSessionById(int sessionId) throws SQLException {
        String sql =
                "SELECT ID, vehicleID, parkingLotID, parkingSpotID, conveyorID, startTime, endTime, state " +
                        "FROM ParkingSession WHERE ID = ?";

        try (Connection c = open();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setInt(1, sessionId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new ParkingSession(
                            rs.getInt("ID"),
                            rs.getInt("vehicleID"),
                            rs.getInt("parkingLotID"),
                            (Integer) rs.getObject("parkingSpotID"),
                            (Integer) rs.getObject("conveyorID"),
                            rs.getTimestamp("startTime").toLocalDateTime(),
                            rs.getTimestamp("endTime") != null
                                    ? rs.getTimestamp("endTime").toLocalDateTime()
                                    : null,
                            rs.getString("state")
                    );
                }
            }
        }
        return null;
    }

    public void updateSessionState(int sessionId, String newState) throws SQLException {
        try (Connection c = open()) {
            updateSessionStateTx(c, sessionId, newState);
        }
    }

    public void updateSessionStateTx(Connection c, int sessionId, String newState) throws SQLException {
        String sql = "UPDATE ParkingSession SET state = ? WHERE ID = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, newState);
            ps.setInt(2, sessionId);
            ps.executeUpdate();
        }
    }

    public void closeSession(int sessionId, LocalDateTime endTime) throws SQLException {
        try (Connection c = open()) {
            closeSessionTx(c, sessionId, endTime);
        }
    }

    public void closeSessionTx(Connection c, int sessionId, LocalDateTime endTime) throws SQLException {
        String sql = "UPDATE ParkingSession SET endTime = ?, state = 'COMPLETED' WHERE ID = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.valueOf(endTime));
            ps.setInt(2, sessionId);
            ps.executeUpdate();
        }
    }

    // =========================================================
    // ===== Manager queries ===================================
    // =========================================================

    public List<Object[]> getSessionsByParkingLot(int parkingLotId) throws SQLException {
        List<Object[]> list = new ArrayList<>();

        String sql =
                "SELECT ID, startTime, endTime, vehicleID, parkingSpotID, conveyorID " +
                        "FROM ParkingSession WHERE parkingLotID=? ORDER BY startTime DESC";

        try (Connection c = open();
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
        }
        return list;
    }

    public List<Object[]> getSessionsByParkingLotForManagerRaw(int parkingLotId) throws SQLException {
        List<Object[]> list = new ArrayList<>();

        String sql =
                "SELECT s.ID, s.startTime, s.endTime, s.vehicleID, s.parkingSpotID, s.conveyorID, " +
                        "       r.finalAmount, r.appliedRate " +
                        "FROM ParkingSession s " +
                        "LEFT JOIN Receipt r ON r.parkingsessionID = s.ID " +
                        "WHERE s.parkingLotID=? " +
                        "ORDER BY s.startTime DESC";

        try (Connection c = open();
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
                            rs.getObject("conveyorID"),
                            rs.getObject("finalAmount"),
                            rs.getString("appliedRate")
                    });
                }
            }
        }
        return list;
    }

    // =========================================================
    // ===== Receipt ===========================================
    // =========================================================

    public boolean receiptExists(Connection c, int sessionId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM Receipt WHERE parkingsessionID=?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }
        }
    }

    public void insertReceipt(int sessionId, double amount, String rate) throws SQLException {
        String sql =
                "INSERT INTO Receipt(parkingsessionID, finalAmount, paymentDate, appliedRate) " +
                        "VALUES(?,?,?,?)";

        try (Connection c = open();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setInt(1, sessionId);
            ps.setDouble(2, amount);
            ps.setTimestamp(3, Timestamp.valueOf(LocalDateTime.now()));
            ps.setString(4, rate);
            ps.executeUpdate();
        }
    }

    // =========================================================
    // ===== Pricing / club helpers ============================
    // =========================================================

    public PriceList getCurrentPriceListAt(Connection c, int parkingLotId, LocalDateTime at) throws SQLException {
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

    public Integer getCustomerIdByVehicle(Connection c, int vehicleId) throws SQLException {
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

    public String getCustomerPhoneSafe(Connection c, int customerId) throws SQLException {
        try (PreparedStatement ps =
                     c.prepareStatement("SELECT mobilePhon FROM Customer WHERE ID=?")) {
            ps.setInt(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return rs.getString(1);
            }
        }
    }

    public LocalDateTime getMembershipJoinDate(Connection c, int customerId) throws SQLException {
        String sql = "SELECT joinDate FROM Membership WHERE customerId=?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                Timestamp ts = rs.getTimestamp(1);
                return ts == null ? null : ts.toLocalDateTime();
            }
        }
    }

    public boolean isPreferredLotAtTime(Connection c, int customerId, int parkingLotId, LocalDateTime at) throws SQLException {
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

    public boolean existsAnyCompletedSessionInJoinMonth(
            Connection c, int customerId, YearMonth joinYM, LocalDateTime currentSessionStart
    ) throws SQLException {

        LocalDateTime from = joinYM.atDay(1).atStartOfDay();
        LocalDateTime to = joinYM.plusMonths(1).atDay(1).atStartOfDay();

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

    public Integer getActiveSessionIdByVehicle(Connection c, int vehicleId) throws SQLException {
        String sql = "SELECT TOP 1 ID FROM ParkingSession WHERE vehicleID=? AND endTime IS NULL ORDER BY startTime DESC";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, vehicleId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return rs.getInt(1);
            }
        }
    }

    public Integer getLastSessionIdByVehicle(Connection c, int vehicleId) throws SQLException {
        String sql = "SELECT TOP 1 ID FROM ParkingSession WHERE vehicleID=? ORDER BY startTime DESC";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, vehicleId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return rs.getInt(1);
            }
        }
    }

    public String getSessionState(Connection c, int sessionId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT state FROM ParkingSession WHERE ID=?")) {
            ps.setInt(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return rs.getString(1);
            }
        }
    }

    // =========================================================
    // ===== Entry/Exit helpers ================================
    // =========================================================

    public VehicleDataRow getVehicleData(Connection c, int vehicleId) throws SQLException {
        String sql = "SELECT ID, size, weight FROM Vehicle WHERE ID=?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, vehicleId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new SQLException("Vehicle not found: " + vehicleId);
                return new VehicleDataRow(
                        rs.getInt("ID"),
                        rs.getString("size"),
                        rs.getDouble("weight")
                );
            }
        }
    }

    public Integer findAvailableConveyorId(Connection c, int parkingLotId, double vehicleWeight) throws SQLException {
        String sql =
                "SELECT TOP 1 c.ID " +
                        "FROM Conveyor c " +
                        "WHERE c.ParkingLotID=? " +
                        "  AND c.isActive=True " +
                        "  AND UCASE(c.Status)='OPERATIONAL' " +
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

    public ConveyorLocRow getConveyorLoc(Connection c, int conveyorId) throws SQLException {
        String sql = "SELECT X, Y, Floor FROM Conveyor WHERE ID=?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, conveyorId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new SQLException("Conveyor not found: " + conveyorId);
                return new ConveyorLocRow(rs.getInt("X"), rs.getInt("Y"), rs.getInt("Floor"));
            }
        }
    }

    public List<SpotRow> getAvailableSpots(Connection c, int parkingLotId) throws SQLException {
        String sql =
                "SELECT s.ID, s.floorNumber, s.X, s.Y, s.size " +
                        "FROM ParkingSpot s " +
                        "WHERE s.parkingLotID=? " +
                        "  AND s.ID NOT IN (SELECT parkingSpotID FROM ParkingSession WHERE endTime IS NULL)";

        List<SpotRow> list = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, parkingLotId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new SpotRow(
                            rs.getInt("ID"),
                            rs.getInt("floorNumber"),
                            rs.getInt("X"),
                            rs.getInt("Y"),
                            rs.getString("size")
                    ));
                }
            }
        }
        return list;
    }

    public void setConveyorLastStatus(Connection c, int conveyorId, String lastStatus) throws SQLException {
        String sql = "UPDATE Conveyor SET LastStatus=? WHERE ID=?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, lastStatus);
            ps.setInt(2, conveyorId);
            ps.executeUpdate();
        }
    }

    public void updateConveyorPositionTx(Connection c, int conveyorId, int x, int y, int floor) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("UPDATE Conveyor SET X=?, Y=?, Floor=? WHERE ID=?")) {
            ps.setInt(1, x);
            ps.setInt(2, y);
            ps.setInt(3, floor);
            ps.setInt(4, conveyorId);
            ps.executeUpdate();
        }
    }

    public int insertParkingSessionReturningId(Connection c, int parkingLotId, int vehicleId,
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

    public void decrementLotSpacesIfPossible(Connection c, int parkingLotId) throws SQLException {
        String sql =
                "UPDATE ParkingLot SET availablaSpaces = IIF(availablaSpaces>0, availablaSpaces-1, 0) " +
                        "WHERE ID=?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, parkingLotId);
            ps.executeUpdate();
        }
    }

    public void incrementLotSpacesIfPossible(Connection c, int parkingLotId) throws SQLException {
        String sql =
                "UPDATE ParkingLot SET availablaSpaces = availablaSpaces + 1 WHERE ID=?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, parkingLotId);
            ps.executeUpdate();
        }
    }

    public SessionCoreRow loadSessionCore(Connection c, int sessionId) throws SQLException {
        String sql =
                "SELECT parkingLotID, vehicleID, startTime, endTime FROM ParkingSession WHERE ID=?";

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new SQLException("Session not found: " + sessionId);

                int lotId = rs.getInt("parkingLotID");
                int vehicleId = rs.getInt("vehicleID");
                LocalDateTime start = rs.getTimestamp("startTime").toLocalDateTime();
                Timestamp endTs = rs.getTimestamp("endTime");
                LocalDateTime end = (endTs == null) ? null : endTs.toLocalDateTime();

                return new SessionCoreRow(lotId, vehicleId, start, end);
            }
        }
    }

    public void assignConveyorAndSetStateForExit(Connection c, int sessionId, int conveyorId, String state) throws SQLException {
        String sql = "UPDATE ParkingSession SET conveyorID=?, state=? WHERE ID=? AND endTime IS NULL";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, conveyorId);
            ps.setString(2, state);
            ps.setInt(3, sessionId);
            int updated = ps.executeUpdate();
            if (updated == 0) {
                throw new SQLException("Session not found or already completed");
            }
        }
    }

    public Integer getActiveSessionConveyorId(Connection c, int sessionId) throws SQLException {
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

    public void completeSessionNow(Connection c, int sessionId) throws SQLException {
        String sql = "UPDATE ParkingSession SET endTime=?, state=? WHERE ID=? AND endTime IS NULL";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now()));
            ps.setString(2, "COMPLETED");
            ps.setInt(3, sessionId);
            ps.executeUpdate();
        }
    }

    // ===== Client queries =====

    public List<Object[]> getActiveParkingDetailsByPhoneRaw(String phoneNumber) throws SQLException {

        String sql =
                "SELECT s.ID AS sessionId, s.parkingLotID, s.vehicleID, s.parkingSpotID, s.state, s.startTime " +
                        "FROM ParkingSession s " +
                        "JOIN Vehicle v ON v.ID = s.vehicleID " +
                        "JOIN Customer c ON c.ID = v.customerID " +
                        "WHERE c.mobilePhon = ? AND s.endTime IS NULL " +
                        "ORDER BY s.startTime DESC";

        List<Object[]> list = new ArrayList<>();

        try (Connection c = open();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setString(1, phoneNumber);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new Object[]{
                            rs.getInt("sessionId"),
                            rs.getInt("parkingLotID"),
                            rs.getInt("vehicleID"),
                            rs.getObject("parkingSpotID"),
                            rs.getString("state"),
                            rs.getTimestamp("startTime")
                    });
                }
            }
        }

        return list;
    }

    public List<Integer> getFreeVehicleIds(Connection c) throws SQLException {
        String sql =
                "SELECT v.ID FROM Vehicle v " +
                        "WHERE v.ID NOT IN (SELECT vehicleID FROM ParkingSession WHERE endTime IS NULL) " +
                        "ORDER BY RND(v.ID)";
        List<Integer> ids = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) ids.add(rs.getInt(1));
        }
        return ids;
    }

    
    // ===== helpers =====
    private double toDouble(Object o) {
        if (o == null) return 0.0;
        if (o instanceof java.math.BigDecimal)
            return ((java.math.BigDecimal) o).doubleValue();
        if (o instanceof Number)
            return ((Number) o).doubleValue();
        return Double.parseDouble(o.toString());
    }
}
