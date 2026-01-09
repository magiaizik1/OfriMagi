package entity;

import control.AccessDb;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;

/**
 * Represents a single parking session of a vehicle in a parking lot.
 * A parking session starts when the vehicle enters the parking lot
 * and ends after payment and exit.
 */
public class ParkingSession {

    private int id;

    private int vehicleId;
    private int parkingLotId;

    /**
     * The parking spot where the vehicle is parked.
     * NULL while the vehicle is not yet parked.
     */
    private Integer parkingSpotId;

    /**
     * The conveyor currently handling the vehicle.
     * NULL when the vehicle is not on a conveyor.
     */
    private Integer conveyorId;

    private LocalDateTime startTime;
    private LocalDateTime endTime;

    /**
     * Represents the current state of the parking session.
     * This field allows the system to know at each moment
     * where the vehicle is and what stage it is in.
     *
     * Possible values:
     * ARRIVED_AT_GATE
     * WAITING_FOR_DETAILS
     * WAITING_FOR_CONVEYOR
     * MOVING_TO_PARKING
     * PARKED
     * MOVING_TO_EXIT
     * WAITING_FOR_PAYMENT
     * COMPLETED
     */
    private String state;

    public ParkingSession() {}

    // ---------- Getters ----------

    public int getId() {
        return id;
    }

    public int getVehicleId() {
        return vehicleId;
    }

    public int getParkingLotId() {
        return parkingLotId;
    }

    public Integer getParkingSpotId() {
        return parkingSpotId;
    }

    public Integer getConveyorId() {
        return conveyorId;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public String getState() {
        return state;
    }

    // ---------- Setters ----------

    public void setId(int id) {
        this.id = id;
    }

    public void setVehicleId(int vehicleId) {
        this.vehicleId = vehicleId;
    }

    public void setParkingLotId(int parkingLotId) {
        this.parkingLotId = parkingLotId;
    }

    public void setParkingSpotId(Integer parkingSpotId) {
        this.parkingSpotId = parkingSpotId;
    }

    public void setConveyorId(Integer conveyorId) {
        this.conveyorId = conveyorId;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public void setState(String state) {
        this.state = state;
    }

    // =========================
    // ===== Entity DB API =====
    // =========================
    //
    // These methods are added to support ECB/OO design:
    // Controller calls Entity methods; Entity performs DB operations.

    /** Loads a ParkingSession entity from DB by session ID. */
    public static ParkingSession loadById(AccessDb db, int sessionId) throws SQLException {
        String sql =
                "SELECT ID, vehicleID, parkingLotID, parkingSpotID, conveyorID, startTime, endTime, state " +
                "FROM ParkingSession WHERE ID=?";

        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setInt(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new SQLException("Session not found: " + sessionId);

                ParkingSession s = new ParkingSession();
                s.id = rs.getInt("ID");
                s.vehicleId = rs.getInt("vehicleID");
                s.parkingLotId = rs.getInt("parkingLotID");
                s.parkingSpotId = (Integer) rs.getObject("parkingSpotID");
                s.conveyorId = (Integer) rs.getObject("conveyorID");

                Timestamp st = rs.getTimestamp("startTime");
                s.startTime = (st == null) ? null : st.toLocalDateTime();

                Timestamp et = rs.getTimestamp("endTime");
                s.endTime = (et == null) ? null : et.toLocalDateTime();

                s.state = rs.getString("state");
                return s;
            }
        }
    }

    /** Updates the session state in DB (only if session still active). */
    public void updateState(AccessDb db, String newState) throws SQLException {
        String sql =
                "UPDATE ParkingSession SET state=? " +
                "WHERE ID=? AND endTime IS NULL";

        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setString(1, newState);
            ps.setInt(2, this.id);
            ps.executeUpdate();
            this.state = newState;
        }
    }

    /** Ends the session in DB by setting endTime=now and state=COMPLETED (only if active). */
    public void close(AccessDb db) throws SQLException {
        String sql =
                "UPDATE ParkingSession " +
                "SET endTime=?, state=? " +
                "WHERE ID=? AND endTime IS NULL";

        LocalDateTime now = LocalDateTime.now();

        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setTimestamp(1, Timestamp.valueOf(now));
            ps.setString(2, "COMPLETED");
            ps.setInt(3, this.id);
            ps.executeUpdate();

            this.endTime = now;
            this.state = "COMPLETED";
        }
    }
}
