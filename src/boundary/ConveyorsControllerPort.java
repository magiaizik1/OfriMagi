package boundary;

public interface ConveyorsControllerPort {

    String moveToParkingSpot(int sessionId, int conveyorId, int vehicleId, int spotId, MoveCallback cb);

    String moveToGate(int sessionId, int conveyorId, int vehicleId, MoveCallback cb);

    interface MoveCallback {
        void onMoveCompleted(String commandId, int newX, int newY, int newFloor);
        void onMoveFailed(String commandId, String reason);
    }
}