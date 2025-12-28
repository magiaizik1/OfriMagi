package control;

import entity.PriceList;

import java.sql.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * ParkingSessionManagementController
 * אחראי על:
 * - פתיחת סשן חניה
 * - סיום סשן
 * - שליפת סשנים לפי חניון
 * - חישוב משך חניה
 * - יצירת קבלה
 */
public class ParkingSessionManagementController {

    private final AccessDb db;

    public ParkingSessionManagementController(AccessDb db) {
        this.db = db;
    }

    // ================= פתיחת חניה =================
    public void startParkingSession(int parkingLotId, int vehicleId, int spotId, int conveyorId) throws Exception {

        String sql =
                "INSERT INTO ParkingSession(startTime, parkingLotID, vehicleID, parkingSpotID, conveyorID) " +
                "VALUES (?, ?, ?, ?, ?)";

        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now()));
            ps.setInt(2, parkingLotId);
            ps.setInt(3, vehicleId);
            ps.setInt(4, spotId);
            ps.setInt(5, conveyorId);
            ps.executeUpdate();
        }
    }

    // ================= סיום חניה =================
    public void endParkingSession(int sessionId) throws Exception {

        String sql =
                "UPDATE ParkingSession SET endTime=? WHERE ID=? AND endTime IS NULL";

        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now()));
            ps.setInt(2, sessionId);
            ps.executeUpdate();
        }
    }

    // ================= שליפת סשנים לפי חניון =================
    public List<Object[]> getSessionsByParkingLot(int parkingLotId) {

        List<Object[]> list = new ArrayList<>();

        String sql =
                "SELECT ID, startTime, endTime, vehicleID, parkingSpotID, conveyorID " +
                "FROM ParkingSession WHERE parkingLotID=? ORDER BY startTime DESC";

        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setInt(1, parkingLotId);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                list.add(new Object[]{
                        rs.getInt("ID"),
                        rs.getTimestamp("startTime"),
                        rs.getTimestamp("endTime"),
                        rs.getInt("vehicleID"),
                        rs.getInt("parkingSpotID"),
                        rs.getInt("conveyorID")
                });
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return list;
    }

    // ================= משך חניה בדקות =================
    public long getParkingDurationMinutes(int sessionId) {

        String sql = "SELECT startTime, endTime FROM ParkingSession WHERE ID=?";

        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setInt(1, sessionId);
            ResultSet rs = ps.executeQuery();

            if (!rs.next())
                throw new RuntimeException("Session not found");

            LocalDateTime start = rs.getTimestamp("startTime").toLocalDateTime();
            Timestamp endTs = rs.getTimestamp("endTime");

            if (endTs == null)
                throw new RuntimeException("Session still active");

            LocalDateTime end = endTs.toLocalDateTime();
            return Duration.between(start, end).toMinutes();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ================= יצירת קבלה =================
    public void generateReceipt(int sessionId) {

        try (Connection c = db.open()) {

            ParkingSessionData data = getSessionData(c, sessionId);
            PriceList price = getCurrentPriceList(c, data.parkingLotId);

            long minutes = Duration.between(data.start, data.end).toMinutes();
            double amount = calculateAmount(minutes, price);

            String sql =
                    "INSERT INTO Receipt(parkingSessionID, amount, createdAt) VALUES(?,?,?)";

            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, sessionId);
                ps.setDouble(2, amount);
                ps.setTimestamp(3, Timestamp.valueOf(LocalDateTime.now()));
                ps.executeUpdate();
            }

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ================= חישוב תשלום =================
    private double calculateAmount(long minutes, PriceList p) {

        long hours = (long) Math.ceil(minutes / 60.0);

        if (hours <= 1)
            return p.getFirstHourPrice();

        if (hours < 24)
            return p.getFirstHourPrice() +
                    (hours - 1) * p.getAdditionalHourPrice();

        return p.getFullDayPrice();
    }

    // ================= שליפת נתוני סשן =================
    private ParkingSessionData getSessionData(Connection c, int id) throws SQLException {

        String sql = "SELECT startTime,endTime,parkingLotID FROM ParkingSession WHERE ID=?";

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            rs.next();

            return new ParkingSessionData(
                    rs.getTimestamp("startTime").toLocalDateTime(),
                    rs.getTimestamp("endTime").toLocalDateTime(),
                    rs.getInt("parkingLotID")
            );
        }
    }

    // ================= שליפת מחירון פעיל =================
    private PriceList getCurrentPriceList(Connection c, int parkingLotId) throws SQLException {

        String sql =
                "SELECT TOP 1 p.* FROM PriceList p " +
                "JOIN PriceHistory h ON p.ID=h.priceListID " +
                "WHERE h.parkingLotID=? ORDER BY h.effectiveFrom DESC";

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, parkingLotId);
            ResultSet rs = ps.executeQuery();
            rs.next();

            return new PriceList(
                    rs.getInt("ID"),
                    rs.getInt("year"),
                    rs.getDouble("firstHourPrice"),
                    rs.getDouble("additionalHourPrice"),
                    rs.getDouble("fullDayPrice")
            );
        }
    }

    private record ParkingSessionData(LocalDateTime start,
                                      LocalDateTime end,
                                      int parkingLotId) {}
}