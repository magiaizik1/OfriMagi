package simulation;

import boundary.GateSensorPort;

public class ConsoleGateSensorMock implements GateSensorPort {

    @Override
    public void openBarrier(int parkingLotId) {
        System.out.println("🚧 [MOCK-GATE] Barrier opened for lot " + parkingLotId);
    }

    @Override
    public void showEntranceInstructions(int parkingLotId, String message) {
        System.out.println("📢 [MOCK-GATE] Instructions at lot " + parkingLotId + ": " + message);
    }
}