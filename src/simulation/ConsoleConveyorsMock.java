package simulation;

import boundary.ConveyorsControllerPort;
import control.AccessDb;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

public class ConsoleConveyorsMock implements ConveyorsControllerPort {

    private final AccessDb db;

    public ConsoleConveyorsMock(AccessDb db) {
        this.db = db;
    }

    @Override
    public String moveToParkingSpot(int sessionId, int conveyorId, int vehicleId, int spotId, MoveCallback cb) {
        String cmdId = "CMD-" + UUID.randomUUID();
        System.out.println("[MOCK_CONVEYOR] moveToParkingSpot cmd=" + cmdId +
                " session=" + sessionId + " conveyor=" + conveyorId + " vehicle=" + vehicleId + " spot=" + spotId);

        int x = 10, y = 10, floor = 0;

        // Try to fetch spot location from DB for more realistic simulation
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT X, Y, floorNumber FROM ParkingSpot WHERE ID=?")) {
            ps.setInt(1, spotId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    x = rs.getInt("X");
                    y = rs.getInt("Y");
                    floor = rs.getInt("floorNumber");
                }
            }
        } catch (Exception ignore) {}

        cb.onMoveCompleted(cmdId, x, y, floor);
        return cmdId;
    }

    @Override
    public String moveToGate(int sessionId, int conveyorId, int vehicleId, MoveCallback cb) {
        String cmdId = "CMD-" + UUID.randomUUID();
        System.out.println("[MOCK_CONVEYOR] moveToGate cmd=" + cmdId +
                " session=" + sessionId + " conveyor=" + conveyorId + " vehicle=" + vehicleId);

        // Simple: treat gate as (0,0,0). If you have gate location in ParkingLot, you can fetch it too.
        cb.onMoveCompleted(cmdId, 0, 0, 0);
        return cmdId;
    }
}
