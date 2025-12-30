package control;

import entity.PriceList;

import java.sql.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class ParkingSessionManagementController {

    private final AccessDb db;

    public ParkingSessionManagementController(AccessDb db) {
        this.db = db;
    }

    // ================= Start Parking Session =================
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

    // ================= End Parking Session =================
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

    // ================= Sessions by lot (EXISTING UI – 6 columns) =================
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

    // ================= Sessions by lot (MANAGER VIEW) =================
    // Columns:
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

                    // אם יש Receipt – לוקחים ממנו
                    Object amtObj = rs.getObject("finalAmount");
                    if (amtObj != null) {
                        amount = toDouble(amtObj);
                    }

                    // אם אין Receipt אבל הסשן הסתיים – מחשבים להצגה בלבד
                    if (amount == null && endTs != null) {

                        PriceList priceList =
                                getCurrentPriceListAt(c, parkingLotId, endTs.toLocalDateTime());

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
                            amount = null;
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

    // ================= Receipt =================
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

    // ================= Helpers =================

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
                LocalDateTime end = (endTs == null) ? null : endTs.toLocalDateTime();

                return new ParkingSessionData(start, end, rs.getInt("parkingLotID"));
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
}
