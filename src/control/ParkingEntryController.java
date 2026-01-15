package control;

import boundary.ConveyorsControllerPort;
import boundary.GateSensorPort;

import java.sql.Connection;
import java.util.List;
import java.util.Random;

public class ParkingEntryController {

    private final AccessDb db;
    private final Random rnd = new Random();

    private final ConveyorsControllerPort conveyorsPort;
    private final GateSensorPort gateSensorPort;

    public ParkingEntryController(AccessDb db,
                                  ConveyorsControllerPort conveyorsPort,
                                  GateSensorPort gateSensorPort) {
        this.db = db;
        this.conveyorsPort = conveyorsPort;
        this.gateSensorPort = gateSensorPort;
    }

    // =========================
    // ===== ENTRY FLOW ========
    // =========================

    public static class SensorArrivalResult {
        public final String outcome;
        public final Integer parkingLotId;
        public final Integer vehicleId;
        public final Integer conveyorId;
        public final Integer spotId;
        public final Integer sessionId;

        public SensorArrivalResult(String outcome, Integer parkingLotId, Integer vehicleId,
                                   Integer conveyorId, Integer spotId, Integer sessionId) {
            this.outcome = outcome;
            this.parkingLotId = parkingLotId;
            this.vehicleId = vehicleId;
            this.conveyorId = conveyorId;
            this.spotId = spotId;
            this.sessionId = sessionId;
        }
    }

    public SensorArrivalResult simulateVehicleArrivalAndPark(int ignoredParkingLotId) throws Exception {

        final int fixedLotId = 14; // חניון 14 בלבד

        Integer vehicleId = null;
        Integer conveyorId = null;
        Integer spotId = null;
        int sessionId = -1;

        try (Connection c = db.open()) {
            c.setAutoCommit(false);

            // 1) רכבים חופשיים
            List<Integer> freeVehicles = db.getFreeVehicleIds(c);
            if (freeVehicles == null || freeVehicles.isEmpty()) {
                c.rollback();
                return new SensorArrivalResult("NO_VEHICLE", fixedLotId, null, null, null, null);
            }

            // 2) חניות פנויות בחניון 14
            List<AccessDb.SpotRow> spots = db.getAvailableSpots(c, fixedLotId);
            if (spots == null || spots.isEmpty()) {
                c.rollback();
                return new SensorArrivalResult("LOT_FULL", fixedLotId, null, null, null, null);
            }

            boolean sawAnyConveyorMatch = false;

            // 3) נסה רכבים אחד אחד: מסוע שמתאים למשקל + חניה שמתאימה לגודל
            for (int vId : freeVehicles) {

                AccessDb.VehicleDataRow vRow = db.getVehicleData(c, vId);
                VehicleData v = new VehicleData(vRow.id, normalizeSize(vRow.size), vRow.weight);

                Integer candConveyorId = db.findAvailableConveyorId(c, fixedLotId, v.weight);
                if (candConveyorId == null) {
                    continue;
                }
                sawAnyConveyorMatch = true;

                AccessDb.ConveyorLocRow clRow = db.getConveyorLoc(c, candConveyorId);
                ConveyorLoc cl = new ConveyorLoc(clRow.x, clRow.y, clRow.floor);

                Integer candSpotId = chooseNearestSpot(spots, v.size, cl.floor, cl.x, cl.y);
                if (candSpotId == null) {
                    continue; // יש מסוע מתאים אבל אין חניה שמתאימה לגודל הרכב
                }

                // מצאנו קומבינציה עובדת
                vehicleId = vId;
                conveyorId = candConveyorId;
                spotId = candSpotId;
                break;
            }

            if (vehicleId == null) {
                c.rollback();
                if (!sawAnyConveyorMatch) {
                    return new SensorArrivalResult("WAITING_FOR_CONVEYOR", fixedLotId, null, null, null, null);
                }
                return new SensorArrivalResult("LOT_FULL", fixedLotId, null, null, null, null);
            }

            // 4) צור סשן אמיתי
            db.setConveyorLastStatus(c, conveyorId, "OPERATIONAL");

            sessionId = db.insertParkingSessionReturningId(
                    c, fixedLotId, vehicleId, spotId, conveyorId, "MOVING_TO_PARKING"
            );
            db.decrementLotSpacesIfPossible(c, fixedLotId);

            c.commit();
        }

        if (gateSensorPort != null) {
            gateSensorPort.openBarrier(fixedLotId);
        }

        return new SensorArrivalResult("OK", fixedLotId, vehicleId, conveyorId, spotId, sessionId);
    }

    // ===== UTIL / LOGIC =====

    private Integer chooseNearestSpot(List<AccessDb.SpotRow> spots, String vehicleSize,
                                      int conveyorFloor, int conveyorX, int conveyorY) {

        Integer bestId = null;
        long bestScore = Long.MAX_VALUE;

        for (AccessDb.SpotRow s : spots) {
            String spotSize = normalizeSize(s.size);
            if (!canSpotFitVehicle(spotSize, vehicleSize)) continue;

            long dx = (long) s.x - conveyorX;
            long dy = (long) s.y - conveyorY;
            long dist2 = dx * dx + dy * dy;

            long floorPenalty = (s.floor == conveyorFloor) ? 0 : 1_000_000_000L;
            long score = floorPenalty + dist2;

            if (score < bestScore) {
                bestScore = score;
                bestId = s.id;
            }
        }

        return bestId;
    }

    private boolean canSpotFitVehicle(String spotSize, String vehicleSize) {
        return sizeRank(spotSize) >= sizeRank(vehicleSize);
    }

    private int sizeRank(String size) {
        String t = (size == null) ? "SMALL" : size.trim().toUpperCase();
        if (t.contains("LARGE")) return 3;
        if (t.contains("MED")) return 2;
        return 1;
    }

    private String normalizeSize(String s) {
        if (s == null) return "SMALL";
        String t = s.trim().toUpperCase();
        if (t.contains("LARGE")) return "LARGE";
        if (t.contains("MED")) return "MEDIUM";
        return "SMALL";
    }

    // ===== INNER CLASSES =====

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

    private static class ConveyorLoc {
        int x, y, floor;
        ConveyorLoc(int x, int y, int floor) {
            this.x = x;
            this.y = y;
            this.floor = floor;
        }
    }
}
