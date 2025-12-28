package control;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

import entity.City;
import entity.ParkingLot;

/**
 * ParkingLotManagementController
 * ------------------------------
 * Works with Access table ParkingLot.
 *
 * שינוי דרישה:
 * - כתובת התפצלה ל: street + number
 *
 * חשוב:
 * - לא נוגעים בלוגיקה מעבר לזה.
 * - availableSpaces נשאר כמו שהיה (מוגדר ב-INSERT, לא מתעדכן ב-UPDATE).
 *
 * שינוי נוסף:
 * - Soft delete: isActive (true/false) במקום DELETE אמיתי.
 * - ברירת מחדל: מחזירים רק פעילים.
 */
public class ParkingLotManagementController {

    private final AccessDb db;

    public ParkingLotManagementController(AccessDb db) {
        this.db = db;
    }

    private void ensureDb() {
        if (db == null) throw new IllegalStateException("Access DB is not configured");
    }

    /**
     * Adds a new ParkingLot (ID AutoNumber).
     * Note: availableSpaces is allowed on INSERT (initial value).
     */
    public ParkingLot addParkingLot(String name, String street, Integer number, City city, int availableSpaces) {
        ensureDb();

        String n = name == null ? "" : name.trim();
        String s = street == null ? "" : street.trim();

        if (n.isEmpty()) throw new IllegalArgumentException("Parking lot name is required.");
        if (s.isEmpty()) throw new IllegalArgumentException("Street is required.");
        if (number == null) throw new IllegalArgumentException("Number is required.");
        if (number <= 0) throw new IllegalArgumentException("Number must be positive.");
        if (city == null) throw new IllegalArgumentException("City is required.");
        if (availableSpaces < 0) throw new IllegalArgumentException("Available spaces must be 0 or higher.");

        // ✅ NEW: isActive default true
        final String sql =
                "INSERT INTO ParkingLot ([name],[street],[number],[cityID],[availablaSpaces],[isActive]) VALUES (?,?,?,?,?,?)";

        try (Connection conn = db.open();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, n);
            ps.setString(2, s);
            ps.setInt(3, number);
            ps.setInt(4, city.getId());
            ps.setInt(5, availableSpaces);
            ps.setBoolean(6, true);

            ps.executeUpdate();

            int newId = readGeneratedId(ps, conn);
            if (newId <= 0) throw new RuntimeException("Insert succeeded but could not read generated ID");

            return new ParkingLot(newId, n, s, number, city, availableSpaces, true);

        } catch (SQLException e) {
            throw new RuntimeException("Failed to add parking lot: " + e.getMessage(), e);
        }
    }

    /**
     * Updates ParkingLot by ID:
     * - updates ONLY name/street/number/cityID
     * - does NOT change availableSpaces
     */
    public void updateParkingLot(int id, String name, String street, Integer number, City city) {
        ensureDb();

        String n = name == null ? "" : name.trim();
        String s = street == null ? "" : street.trim();

        if (n.isEmpty()) throw new IllegalArgumentException("Parking lot name is required.");
        if (s.isEmpty()) throw new IllegalArgumentException("Street is required.");
        if (number == null) throw new IllegalArgumentException("Number is required.");
        if (number <= 0) throw new IllegalArgumentException("Number must be positive.");
        if (city == null) throw new IllegalArgumentException("City is required.");

        // ✅ Only update if active (optional rule). If you prefer allow updating inactive, remove AND isActive=True
        final String sql =
                "UPDATE ParkingLot SET [name]=?, [street]=?, [number]=?, [cityID]=? WHERE [ID]=? AND [isActive]=True";

        try (Connection conn = db.open();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, n);
            ps.setString(2, s);
            ps.setInt(3, number);
            ps.setInt(4, city.getId());
            ps.setInt(5, id);

            int updated = ps.executeUpdate();
            if (updated == 0) {
                // check if exists but inactive
                if (isParkingLotInactive(id)) {
                    throw new IllegalStateException("Parking lot is inactive and cannot be updated.");
                }
                throw new IllegalArgumentException("ParkingLot not found: " + id);
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed to update parking lot: " + e.getMessage(), e);
        }
    }

    /**
     * Soft delete: sets isActive=false instead of DELETE.
     */
    public void deleteParkingLot(int id) {
        ensureDb();

        if (isParkingLotInactive(id)) {
            throw new IllegalStateException("Parking lot is already inactive.");
        }

        final String sql = "UPDATE ParkingLot SET [isActive]=False WHERE [ID]=? AND [isActive]=True";

        try (Connection conn = db.open();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, id);
            int updated = ps.executeUpdate();
            if (updated == 0) throw new IllegalArgumentException("ParkingLot not found: " + id);

        } catch (SQLException e) {
            throw new RuntimeException("Failed to deactivate parking lot: " + e.getMessage(), e);
        }
    }

    /**
     * Loads a ParkingLot including City object (join City table).
     */
    public ParkingLot getParkingLot(int id) {
        ensureDb();

        final String sql =
                "SELECT p.[ID], p.[name], p.[street], p.[number], p.[availablaSpaces], p.[isActive], " +
                "       c.[ID] AS CityID, c.[cityName] AS CityName " +
                "FROM ParkingLot p " +
                "LEFT JOIN City c ON p.[cityID] = c.[ID] " +
                "WHERE p.[ID]=?";

        try (Connection conn = db.open();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, id);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("ParkingLot not found: " + id);

                City city = null;
                int cityId = rs.getInt("CityID");
                if (!rs.wasNull()) {
                    city = new City(cityId, rs.getString("CityName"));
                }

                String street = rs.getString("street");
                Integer number = null;
                Object numObj = rs.getObject("number");
                if (numObj != null) number = ((Number) numObj).intValue();

                boolean isActive = true;
                try {
                    isActive = rs.getBoolean("isActive");
                } catch (Exception ignore) {}

                return new ParkingLot(
                        rs.getInt("ID"),
                        rs.getString("name"),
                        street,
                        number,
                        city,
                        rs.getInt("availablaSpaces"),
                        isActive
                );
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed to load parking lot: " + e.getMessage(), e);
        }
    }

    /**
     * Default: only active parking lots.
     */
    public List<ParkingLot> getAllParkingLots() {
        return getAllParkingLots(false);
    }

    /**
     * Loads parking lots including City object (join City table).
     * @param includeInactive if true, returns all; else only active.
     */
    public List<ParkingLot> getAllParkingLots(boolean includeInactive) {
        ensureDb();

        List<ParkingLot> lots = new ArrayList<>();

        final String sql =
                "SELECT p.[ID], p.[name], p.[street], p.[number], p.[availablaSpaces], p.[isActive], " +
                "       c.[ID] AS CityID, c.[cityName] AS CityName " +
                "FROM ParkingLot p " +
                "LEFT JOIN City c ON p.[cityID] = c.[ID] " +
                (includeInactive ? "" : "WHERE p.[isActive]=True ") +
                (includeInactive ? "ORDER BY p.[isActive] DESC, p.[ID] ASC" : "ORDER BY p.[ID] ASC");

        try (Connection conn = db.open();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                City city = null;
                int cityId = rs.getInt("CityID");
                if (!rs.wasNull()) {
                    city = new City(cityId, rs.getString("CityName"));
                }

                String street = rs.getString("street");
                Integer number = null;
                Object numObj = rs.getObject("number");
                if (numObj != null) number = ((Number) numObj).intValue();

                boolean isActive = true;
                try {
                    isActive = rs.getBoolean("isActive");
                } catch (Exception ignore) {}

                lots.add(new ParkingLot(
                        rs.getInt("ID"),
                        rs.getString("name"),
                        street,
                        number,
                        city,
                        rs.getInt("availablaSpaces"),
                        isActive
                ));
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed to load parking lots: " + e.getMessage(), e);
        }

        return lots;
    }

    private boolean isParkingLotInactive(int id) {
        final String sql = "SELECT [isActive] FROM ParkingLot WHERE [ID]=?";
        try (Connection conn = db.open();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return false;
                boolean active = true;
                try { active = rs.getBoolean("isActive"); } catch (Exception ignore) {}
                return !active;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to check parking lot active flag: " + e.getMessage(), e);
        }
    }

    private int readGeneratedId(PreparedStatement ps, Connection conn) {
        int newId = -1;

        try (ResultSet keys = ps.getGeneratedKeys()) {
            if (keys != null && keys.next()) newId = keys.getInt(1);
        } catch (Exception ignore) {}

        if (newId <= 0) {
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT @@IDENTITY")) {
                if (rs.next()) newId = rs.getInt(1);
            } catch (Exception ignore) {}
        }

        return newId;
    }
}
