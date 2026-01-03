package boundary;

/**
 * External actor: Gate Sensor (GS).
 * System calls GS to open barrier / show instructions.
 */
public interface GateSensorPort {
    void openBarrier(int parkingLotId);
    void showEntranceInstructions(int parkingLotId, String message);
}