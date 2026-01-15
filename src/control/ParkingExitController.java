package control;

import boundary.ConveyorsControllerPort;
import boundary.GateSensorPort;

import java.sql.Connection;

public class ParkingExitController {

    private final AccessDb db;
    private final ConveyorsControllerPort conveyorsPort;
    private final GateSensorPort gateSensorPort;

    public ParkingExitController(AccessDb db,
                                 ConveyorsControllerPort conveyorsPort,
                                 GateSensorPort gateSensorPort) {
        this.db = db;
        this.conveyorsPort = conveyorsPort;
        this.gateSensorPort = gateSensorPort;
    }

    public void requestExit(int sessionId) throws Exception {

        AccessDb.SessionCoreRow s;
        Integer assignedConveyorId;

        try (Connection c = db.open()) {
            c.setAutoCommit(false);

            s = db.loadSessionCore(c, sessionId);
            if (s.end != null) {
                c.rollback();
                throw new RuntimeException("Session already completed");
            }

            AccessDb.VehicleDataRow vRow = db.getVehicleData(c, s.vehicleId);
            VehicleData v = new VehicleData(vRow.id, normalizeSize(vRow.size), vRow.weight);

            assignedConveyorId = db.findAvailableConveyorId(c, s.parkingLotId, v.weight);
            if (assignedConveyorId == null) {
                c.rollback();
                throw new RuntimeException("No available conveyor for exit");
            }

            db.assignConveyorAndSetStateForExit(c, sessionId, assignedConveyorId, "MOVING_TO_EXIT");
            db.setConveyorLastStatus(c, assignedConveyorId, "OPERATIONAL");

            c.commit();
        }

        if (conveyorsPort != null) {
            int finalConveyorId = assignedConveyorId;

            conveyorsPort.moveToGate(sessionId, finalConveyorId, s.vehicleId,
                    new ConveyorsControllerPort.MoveCallback() {
                        @Override
                        public void onMoveCompleted(String commandId, int newX, int newY, int newFloor) {
                            try (Connection c2 = db.open()) {
                                c2.setAutoCommit(false);

                                db.updateSessionStateTx(c2, sessionId, "WAITING_FOR_PAYMENT");
                                db.updateConveyorPositionTx(c2, finalConveyorId, newX, newY, newFloor);

                                c2.commit();
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        }

                        @Override
                        public void onMoveFailed(String commandId, String reason) {
                            try {
                                db.updateSessionState(sessionId, "ERROR_MOVE_TO_GATE");
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        }
                    });
        }
    }

    public void confirmPaymentAndExit(int sessionId) throws Exception {
        int lotId;
        Integer conveyorId;

        try (Connection c = db.open()) {
            c.setAutoCommit(false);

            AccessDb.SessionCoreRow s = db.loadSessionCore(c, sessionId);
            lotId = s.parkingLotId;

            conveyorId = db.getActiveSessionConveyorId(c, sessionId);

            db.completeSessionNow(c, sessionId);
            db.incrementLotSpacesIfPossible(c, lotId);

            if (conveyorId != null) {
                db.setConveyorLastStatus(c, conveyorId, "OPERATIONAL");
            }

            c.commit();
        }

        if (gateSensorPort != null) {
            gateSensorPort.openBarrier(lotId);
        }
    }

    private String normalizeSize(String s) {
        if (s == null) return "SMALL";
        String t = s.trim().toUpperCase();
        if (t.contains("LARGE")) return "LARGE";
        if (t.contains("MED")) return "MEDIUM";
        return "SMALL";
    }

    private static class VehicleData {
        int id;
        String size;
        double weight;

        VehicleData(int id, String size, double weight) {
            this.id = id;
            this.size = size;
            this.weight = weight;
        }
    }
}
