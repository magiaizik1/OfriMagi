package simulation;

import boundary.ConveyorsControllerPort;

import java.util.UUID;

public class ConsoleConveyorsMock implements ConveyorsControllerPort {

    @Override
    public String moveToParkingSpot(int sessionId, int conveyorId, int vehicleId, int spotId, MoveCallback cb) {
        String cmdId = "CMD-" + UUID.randomUUID();
        System.out.println("[MOCK_CONVEYOR] moveToParkingSpot cmd=" + cmdId +
                " session=" + sessionId + " conveyor=" + conveyorId + " vehicle=" + vehicleId + " spot=" + spotId);

        // simulate success instantly
        cb.onMoveCompleted(cmdId, 10, 10, 0);
        return cmdId;
    }

    @Override
    public String moveToGate(int sessionId, int conveyorId, int vehicleId, MoveCallback cb) {
        String cmdId = "CMD-" + UUID.randomUUID();
        System.out.println("[MOCK_CONVEYOR] moveToGate cmd=" + cmdId +
                " session=" + sessionId + " conveyor=" + conveyorId + " vehicle=" + vehicleId);

        // simulate success instantly
        cb.onMoveCompleted(cmdId, 0, 0, 0);
        return cmdId;
    }
}