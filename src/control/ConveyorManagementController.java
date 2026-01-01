package control;

import entity.Conveyor;
import entity.ConveyorLastStatus;
import entity.ConveyorStatus;
import entity.ParkingSpot;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ConveyorManagementController
 *
 * כולל:
 * 1) CRUD + state-machine 
 * 2) הרחבות לפי הסיפור ParkWise:
 *    - Integrity Check + retries + pause/alert
 *    - Assign vacant conveyor
 *    - Command execution (busy)
 *    - Optimization: nearest vacant spot (size + weight constraints)
 *    - Persist parking completion into ParkingSession

 */
public class ConveyorManagementController {

    private final AccessDb db;

    // ========= In-memory state =========
    private final Map<Integer, Integer> attemptCountById = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> pendingWeightById = new ConcurrentHashMap<>();

    // ========= NEW: Busy state for commands (move vehicle) =========
    private final Set<Integer> busyConveyors = ConcurrentHashMap.newKeySet();

    // ========= NEW: Alerts callback (optional) =========
    public interface AlertListener {
        void onAlert(int conveyorId, String message);
    }
    private AlertListener alertListener;

    public void setAlertListener(AlertListener listener) {
        this.alertListener = listener;
    }

    public ConveyorManagementController(AccessDb db) {
        this.db = db;
    }

    private void ensureDb() {
        if (db == null) throw new IllegalStateException("Access DB is not configured");
    }

    private void alert(int conveyorId, String msg) {
        if (alertListener != null) alertListener.onAlert(conveyorId, msg);
    }

    // =========================
    // CRUD
    // =========================

    public Conveyor addConveyorToParkingLot(int parkingLotId,
                                           int floorNumber, // נשמר חתימה קיימת (לא בשימוש יותר)
                                           int x,          // נשמר חתימה קיימת (לא בשימוש יותר)
                                           int y,          // נשמר חתימה קיימת (לא בשימוש יותר)
                                           int maxVehicleWeightKg,
                                           ConveyorStatus status) {
        ensureDb();

        if (parkingLotId <= 0) throw new IllegalArgumentException("ParkingLotID must be positive.");
        if (maxVehicleWeightKg <= 0) throw new IllegalArgumentException("MaxWeight must be positive.");

        if (status == null) status = ConveyorStatus.Off;

        // On creation Floor/X/Y are NULL + isActive=True
        String sql = "INSERT INTO Conveyor ([ParkingLotID],[Floor],[X],[Y],[MaxWeight],[Status],[LastStatus],[isActive]) VALUES (?,?,?,?,?,?,?,?)";

        try (Connection conn = db.open();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setInt(1, parkingLotId);

            // Floor/X/Y => NULL
            ps.setNull(2, Types.INTEGER);
            ps.setNull(3, Types.INTEGER);
            ps.setNull(4, Types.INTEGER);

            ps.setInt(5, maxVehicleWeightKg);
            ps.setString(6, status.name());
            ps.setString(7, null);
            ps.setBoolean(8, true);

            ps.executeUpdate();

            int newId = readGeneratedId(ps, conn);

            attemptCountById.put(newId, 0);
            pendingWeightById.remove(newId);

            return new Conveyor(newId, parkingLotId, null, null, null, maxVehicleWeightKg, status, null, true);

        } catch (SQLException e) {
            throw new RuntimeException("Failed to add conveyor: " + e.getMessage(), e);
        }
    }

    /**
     * Default: only active conveyors.
     */
    public List<Conveyor> getConveyorsByParkingLot(int parkingLotId) {
        return getConveyorsByParkingLot(parkingLotId, false);
    }

    /**
     * @param includeInactive if true, returns all; else only active.
     */
    public List<Conveyor> getConveyorsByParkingLot(int parkingLotId, boolean includeInactive) {
        ensureDb();

        List<Conveyor> list = new ArrayList<>();
        String sql =
                "SELECT [ID],[ParkingLotID],[Floor],[X],[Y],[MaxWeight],[Status],[LastStatus],[isActive] " +
                "FROM Conveyor WHERE [ParkingLotID]=? " +
                (includeInactive ? "" : "AND [isActive]=True ") +
                (includeInactive ? "ORDER BY [isActive] DESC, [ID] ASC" : "ORDER BY [ID] ASC");

        try (Connection conn = db.open();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, parkingLotId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {

                    int id = rs.getInt("ID");
                    int lotId = rs.getInt("ParkingLotID");

                    Integer floor = getNullableInt(rs, "Floor");
                    Integer x = getNullableInt(rs, "X");
                    Integer y = getNullableInt(rs, "Y");

                    int maxW = rs.getInt("MaxWeight");

                    ConveyorStatus status = parseStatus(rs.getString("Status"));
                    ConveyorLastStatus lastStatus = safeParseLastStatus(rs.getString("LastStatus"));

                    boolean isActive = true;
                    try { isActive = rs.getBoolean("isActive"); } catch (Exception ignore) {}

                    attemptCountById.putIfAbsent(id, 0);

                    list.add(new Conveyor(id, lotId, floor, x, y, maxW, status, lastStatus, isActive));
                }
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed to load conveyors: " + e.getMessage(), e);
        }

        return list;
    }

    public void moveConveyorToParkingLot(int conveyorId, int newParkingLotId) {
        ensureDb();

        requirePositiveId(conveyorId);
        if (newParkingLotId <= 0) throw new IllegalArgumentException("New ParkingLotID must be positive.");

        if (isConveyorInactive(conveyorId)) {
            throw new IllegalStateException("Conveyor is inactive and cannot be moved.");
        }
        if (busyConveyors.contains(conveyorId)) {
            throw new IllegalStateException("Conveyor is busy executing a command and cannot be moved between lots.");
        }

        // When moving, Floor/X/Y become NULL (manager cannot set)
        String sql = "UPDATE Conveyor SET [ParkingLotID]=?, [X]=?, [Y]=?, [Floor]=? WHERE [ID]=? AND [isActive]=True";

        try (Connection conn = db.open();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, newParkingLotId);

            ps.setNull(2, Types.INTEGER);
            ps.setNull(3, Types.INTEGER);
            ps.setNull(4, Types.INTEGER);

            ps.setInt(5, conveyorId);

            int updated = ps.executeUpdate();
            if (updated == 0) throw new IllegalArgumentException("Conveyor not found: " + conveyorId);

        } catch (SQLException e) {
            throw new RuntimeException("Failed to move conveyor: " + e.getMessage(), e);
        }
    }

    /**
     * Soft delete: sets isActive=false instead of DELETE.
     */
    public void deleteConveyor(int conveyorId) {
        ensureDb();
        requirePositiveId(conveyorId);

        if (isConveyorInactive(conveyorId)) {
            throw new IllegalStateException("Conveyor is already inactive.");
        }
        if (busyConveyors.contains(conveyorId)) {
            throw new IllegalStateException("Conveyor is busy executing a command and cannot be deactivated.");
        }

        String sql = "UPDATE Conveyor SET [isActive]=False WHERE [ID]=? AND [isActive]=True";

        try (Connection conn = db.open();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, conveyorId);

            int updated = ps.executeUpdate();
            if (updated == 0) throw new IllegalArgumentException("Conveyor not found: " + conveyorId);

            attemptCountById.remove(conveyorId);
            pendingWeightById.remove(conveyorId);
            busyConveyors.remove(conveyorId);

        } catch (SQLException e) {
            throw new RuntimeException("Failed to deactivate conveyor: " + e.getMessage(), e);
        }
    }

    // =========================
    // Bulk action
    // =========================

    /**
     * Turns ON all ACTIVE conveyors in a parking lot that are currently OFF and have NO pending weight.
     * Returns how many were successfully turned on.
     */
    public int turnOnAllConveyorsInParkingLot(int parkingLotId) {
        ensureDb();
        if (parkingLotId <= 0) throw new IllegalArgumentException("ParkingLotID must be positive.");

        List<Conveyor> list = getConveyorsByParkingLot(parkingLotId, false); // only active
        int turnedOn = 0;

        for (Conveyor c : list) {
            try {
                if (c.getStatus() == ConveyorStatus.Off && !pendingWeightById.containsKey(c.getId())) {
                    attemptCountById.put(c.getId(), 0);
                    updateConveyorStatus_WithHistoryRule(c.getId(), ConveyorStatus.Testing);
                    turnedOn++;
                }
            } catch (Exception ignore) {}
        }
        return turnedOn;
    }

    // =========================
    // State-machine events (existing)
    // =========================

    public void decideChangeMaxWeight(int conveyorId, int newWeight) {
        ensureDb();
        requirePositiveId(conveyorId);
        requirePositiveWeight(newWeight);

        Conveyor c = getConveyorById(conveyorId);
        if (!c.isActive()) throw new IllegalStateException("Conveyor is inactive.");
        if (c.getStatus() != ConveyorStatus.Off)
            throw new IllegalStateException("Max weight change can be decided ONLY when conveyor is OFF.");

        pendingWeightById.put(conveyorId, newWeight);
    }

    public void confirmChangeMaxWeight(int conveyorId) {
        ensureDb();
        requirePositiveId(conveyorId);

        Conveyor c = getConveyorById(conveyorId);
        if (!c.isActive()) throw new IllegalStateException("Conveyor is inactive.");
        if (c.getStatus() != ConveyorStatus.Off)
            throw new IllegalStateException("Max weight can be confirmed ONLY when conveyor is OFF.");

        Integer pending = pendingWeightById.get(conveyorId);
        if (pending == null) throw new IllegalStateException("No pending max weight change for this conveyor.");

        updateConveyorMaxWeight_DBOnly(conveyorId, pending);
        pendingWeightById.remove(conveyorId);
    }

    public Integer getPendingWeight(int conveyorId) {
        return pendingWeightById.get(conveyorId);
    }

    public void turnOnConveyors(int conveyorId) {
        ensureDb();
        requirePositiveId(conveyorId);

        Conveyor c = getConveyorById(conveyorId);
        if (!c.isActive()) throw new IllegalStateException("Conveyor is inactive.");
        if (c.getStatus() != ConveyorStatus.Off)
            throw new IllegalStateException("Turn ON is allowed only from OFF state.");
        if (pendingWeightById.containsKey(conveyorId))
            throw new IllegalStateException("Cannot turn ON while there is a pending weight change (confirm it first).");

        attemptCountById.put(conveyorId, 0);
        updateConveyorStatus_WithHistoryRule(conveyorId, ConveyorStatus.Testing);
    }

    public void restart(int conveyorId) {
        ensureDb();
        requirePositiveId(conveyorId);

        Conveyor c = getConveyorById(conveyorId);
        if (!c.isActive()) throw new IllegalStateException("Conveyor is inactive.");
        if (c.getStatus() != ConveyorStatus.Paused)
            throw new IllegalStateException("Restart is allowed only from PAUSED.");

        attemptCountById.put(conveyorId, 0);
        updateConveyorStatus_WithHistoryRule(conveyorId, ConveyorStatus.Testing);
    }

    public void turnOffConveyors(int conveyorId) {
        ensureDb();
        requirePositiveId(conveyorId);

        if (busyConveyors.contains(conveyorId)) {
            throw new IllegalStateException("Cannot turn OFF while conveyor is executing a route/command.");
        }

        Conveyor c = getConveyorById(conveyorId);
        if (!c.isActive()) throw new IllegalStateException("Conveyor is inactive.");
        if (c.getStatus() != ConveyorStatus.Operational)
            throw new IllegalStateException("Turn OFF is allowed only from OPERATION (Operational).");

        updateConveyorStatus_WithHistoryRule(conveyorId, ConveyorStatus.Off);
    }

    public void pause(int conveyorId) {
        ensureDb();
        requirePositiveId(conveyorId);
        throw new UnsupportedOperationException(
                "Pause is not allowed manually. Paused state is entered by integrity-check failures/timeouts or external events.");
    }

    public void updateConveyorStatus(int conveyorId, ConveyorStatus status) {
        ensureDb();
        requirePositiveId(conveyorId);
        throw new UnsupportedOperationException(
                "Direct status update is not allowed. Use: turnOnConveyors / restart / turnOffConveyors / integrity-check handlers");
    }

    // ==========================================================
    // NEW: Integrity Check handling (per story)
    // ==========================================================

    /**
     * Called when a conveyor finishes its 2-parallel checks (mechanical + electronic).
     * completedWithin10Min: true אם שתי הבדיקות הסתיימו בזמן.
     * allPassed: true אם כל הסעיפים עברו בלי כשל.
     */
    public void onIntegrityCheckFinished(int conveyorId, boolean completedWithin10Min, boolean allPassed) {
        ensureDb();
        requirePositiveId(conveyorId);

        Conveyor c = getConveyorById(conveyorId);
        if (!c.isActive()) throw new IllegalStateException("Conveyor is inactive.");

        if (c.getStatus() != ConveyorStatus.Testing) {
            // בסיפור: integrity check קורה כשהמסוע נדלק (Testing)
            throw new IllegalStateException("Integrity check result is allowed only when conveyor is in TESTING.");
        }

        if (!completedWithin10Min) {
            onIntegrityCheckTimeout(conveyorId);
            return;
        }

        if (!allPassed) {
            // כשל בבדיקות => Paused + alert
            updateConveyorStatus_WithHistoryRule(conveyorId, ConveyorStatus.Paused);
            alert(conveyorId, "Integrity check completed with failures. Conveyor entered PAUSED mode.");
            return;
        }

        // הצלחה מלאה => Operational
        attemptCountById.put(conveyorId, 0);
        updateConveyorStatus_WithHistoryRule(conveyorId, ConveyorStatus.Operational);
    }

    /**
     * Timeout: אם אחת הבדיקות לא הושלמה בתוך 10 דקות => Reset and retry.
     * אחרי 3 ניסיונות רצופים => Paused + alert.
     */
    public void onIntegrityCheckTimeout(int conveyorId) {
        ensureDb();
        requirePositiveId(conveyorId);

        int attempts = attemptCountById.getOrDefault(conveyorId, 0) + 1;
        attemptCountById.put(conveyorId, attempts);

        if (attempts >= 3) {
            updateConveyorStatus_WithHistoryRule(conveyorId, ConveyorStatus.Paused);
            alert(conveyorId, "Integrity check timed out 3 times. Conveyor entered PAUSED mode.");
            return;
        }

        // reset & restart testing
        updateConveyorStatus_WithHistoryRule(conveyorId, ConveyorStatus.Testing);
        alert(conveyorId, "Integrity check timed out. Restarting test (attempt " + attempts + ").");
    }

    // ==========================================================
    // NEW: Assignment + optimization + commands
    // ==========================================================

    /**
     * Finds a vacant (non-busy) OPERATIONAL conveyor in a lot, with enough max weight capacity.
     * "Vacant" here means: not in busyConveyors.
     */
    public Conveyor assignVacantConveyor(int parkingLotId, int vehicleWeightKg) {
        ensureDb();
        if (parkingLotId <= 0) throw new IllegalArgumentException("ParkingLotID must be positive.");
        if (vehicleWeightKg <= 0) throw new IllegalArgumentException("Vehicle weight must be positive.");

        List<Conveyor> conveyors = getConveyorsByParkingLot(parkingLotId, false);
        return conveyors.stream()
                .filter(Conveyor::isActive)
                .filter(c -> c.getStatus() == ConveyorStatus.Operational)
                .filter(c -> !busyConveyors.contains(c.getId()))
                .filter(c -> c.getMaxVehicleWeightKg() >= vehicleWeightKg)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No vacant operational conveyor available for this vehicle weight."));
    }

    /**
     * Optimization algorithm:
     * chooses nearest VACANT spot to the conveyor location (floor/x/y),
     * considering spot size + conveyor max weight.
     *
     * If conveyor has NULL location => chooses any vacant compatible spot (stable).
     */
    public ParkingSpot findNearestVacantSpot(int parkingLotId,
                                            int conveyorId,
                                            String vehicleSize,      // "Small"/"Medium"/"Large"
                                            int vehicleWeightKg) {
        ensureDb();
        if (parkingLotId <= 0) throw new IllegalArgumentException("ParkingLotID must be positive.");
        requirePositiveId(conveyorId);
        if (vehicleSize == null || vehicleSize.isBlank()) throw new IllegalArgumentException("Vehicle size is required.");
        if (vehicleWeightKg <= 0) throw new IllegalArgumentException("Vehicle weight must be positive.");

        Conveyor conveyor = getConveyorById(conveyorId);
        if (!conveyor.isActive()) throw new IllegalStateException("Conveyor is inactive.");
        if (conveyor.getStatus() != ConveyorStatus.Operational)
            throw new IllegalStateException("Optimization allowed only when conveyor is OPERATIONAL.");
        if (conveyor.getMaxVehicleWeightKg() < vehicleWeightKg)
            throw new IllegalStateException("Conveyor max weight is not enough for this vehicle.");

        List<ParkingSpot> spots = loadParkingSpotsByLot(parkingLotId);
        Set<Integer> occupiedSpotIds = loadOccupiedSpotIds(parkingLotId);

        List<ParkingSpot> candidates = new ArrayList<>();
        for (ParkingSpot s : spots) {
            if (occupiedSpotIds.contains(s.getId())) continue;
            if (!isSpotSizeCompatible(s.getSize(), vehicleSize)) continue;
            candidates.add(s);
        }

        if (candidates.isEmpty())
            throw new IllegalStateException("No vacant compatible parking spot available.");

        Integer cf = conveyor.getFloorNumber();
        Integer cx = conveyor.getX();
        Integer cy = conveyor.getY();

        // If conveyor has no location -> return first candidate (stable)
        if (cf == null || cx == null || cy == null) {
            return candidates.get(0);
        }

        ParkingSpot best = null;
        double bestScore = Double.MAX_VALUE;

        for (ParkingSpot s : candidates) {
            // Distance metric (simple, works well):
            // floor penalty + Manhattan on same floor
            double floorPenalty = Math.abs(s.getFloorNumber() - cf) * 1000.0;
            double manhattan = Math.abs(s.getX() - cx) + Math.abs(s.getY() - cy);
            double score = floorPenalty + manhattan;

            if (score < bestScore) {
                bestScore = score;
                best = s;
            }
        }

        return best == null ? candidates.get(0) : best;
    }

    /**
     * Start executing a command (move vehicle).
     * Marks conveyor as BUSY so it cannot accept another command or be turned off.
     *
     * This method does not actually "move" anything physically; it's the system API.
     */
    public void startMoveCommand(int conveyorId) {
        ensureDb();
        requirePositiveId(conveyorId);

        Conveyor c = getConveyorById(conveyorId);
        if (!c.isActive()) throw new IllegalStateException("Conveyor is inactive.");
        if (c.getStatus() != ConveyorStatus.Operational)
            throw new IllegalStateException("Commands are allowed only when conveyor is OPERATIONAL.");
        if (busyConveyors.contains(conveyorId))
            throw new IllegalStateException("Conveyor is already busy.");

        busyConveyors.add(conveyorId);
    }

    /**
     * Finish command execution: free conveyor to accept new commands / allow turn-off.
     */
    public void finishMoveCommand(int conveyorId) {
        ensureDb();
        requirePositiveId(conveyorId);

        busyConveyors.remove(conveyorId);
    }

    /**
     * When parking is completed: store ParkingSession row in DB.
     * This matches your ParkingSession table (startTime, endTime, parkingLotID, vehicleID, parkingSpotID, conveyorID).
     */
    public int storeParkingCompletion(int parkingLotId,
                                      int vehicleId,
                                      int parkingSpotId,
                                      int conveyorId) {
        ensureDb();
        if (parkingLotId <= 0) throw new IllegalArgumentException("ParkingLotID must be positive.");
        if (vehicleId <= 0) throw new IllegalArgumentException("VehicleID must be positive.");
        if (parkingSpotId <= 0) throw new IllegalArgumentException("ParkingSpotID must be positive.");
        requirePositiveId(conveyorId);

        String sql =
                "INSERT INTO ParkingSession(startTime, parkingLotID, vehicleID, parkingSpotID, conveyorID) " +
                "VALUES (?, ?, ?, ?, ?)";

        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now()));
            ps.setInt(2, parkingLotId);
            ps.setInt(3, vehicleId);
            ps.setInt(4, parkingSpotId);
            ps.setInt(5, conveyorId);

            ps.executeUpdate();

            int newId = readGeneratedId(ps, c);
            return newId;

        } catch (SQLException e) {
            throw new RuntimeException("Failed to store parking completion: " + e.getMessage(), e);
        }
    }

    /**
     * Optional: update conveyor location after it moved (floor/x/y).
     * (ב-DB אצלך זה קיים: Floor/X/Y יכולים להיות NULL או מספר)
     */
    public void updateConveyorLocation(int conveyorId, Integer floor, Integer x, Integer y) {
        ensureDb();
        requirePositiveId(conveyorId);

        if (isConveyorInactive(conveyorId)) throw new IllegalStateException("Conveyor is inactive.");

        String sql = "UPDATE Conveyor SET [Floor]=?, [X]=?, [Y]=? WHERE [ID]=? AND [isActive]=True";

        try (Connection conn = db.open();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            if (floor == null) ps.setNull(1, Types.INTEGER); else ps.setInt(1, floor);
            if (x == null) ps.setNull(2, Types.INTEGER); else ps.setInt(2, x);
            if (y == null) ps.setNull(3, Types.INTEGER); else ps.setInt(3, y);

            ps.setInt(4, conveyorId);

            int updated = ps.executeUpdate();
            if (updated == 0) throw new IllegalArgumentException("Conveyor not found: " + conveyorId);

        } catch (SQLException e) {
            throw new RuntimeException("Failed to update conveyor location: " + e.getMessage(), e);
        }
    }

    /**
     * Return vehicle (end parking flow – conveyor assignment part):
     * Assign a vacant conveyor and mark it busy (so it can transfer vehicle to gate).
     */
    public Conveyor assignConveyorForVehicleReturn(int parkingLotId, int vehicleWeightKg) {
        Conveyor c = assignVacantConveyor(parkingLotId, vehicleWeightKg);
        startMoveCommand(c.getId());
        return c;
    }

    // ==========================================================
    // DB helpers for optimization
    // ==========================================================

    private List<ParkingSpot> loadParkingSpotsByLot(int parkingLotId) {
        List<ParkingSpot> list = new ArrayList<>();

        String sql =
                "SELECT [ID],[floorNumber],[X],[Y],[size],[parkingLotID] " +
                "FROM ParkingSpot WHERE [parkingLotID]=?";

        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setInt(1, parkingLotId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new ParkingSpot(
                            rs.getInt("ID"),
                            rs.getInt("floorNumber"),
                            rs.getInt("X"),
                            rs.getInt("Y"),
                            rs.getString("size"),
                            rs.getInt("parkingLotID")
                    ));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load parking spots: " + e.getMessage(), e);
        }

        return list;
    }

    /**
     * Spot is considered occupied if there is an ACTIVE session (endTime IS NULL) on that spot.
     */
    private Set<Integer> loadOccupiedSpotIds(int parkingLotId) {
        Set<Integer> set = new HashSet<>();

        String sql =
                "SELECT parkingSpotID FROM ParkingSession " +
                "WHERE parkingLotID=? AND endTime IS NULL";

        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setInt(1, parkingLotId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    set.add(rs.getInt("parkingSpotID"));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load occupied spots: " + e.getMessage(), e);
        }

        return set;
    }

    /**
     * Size compatibility:
     * - Small spot can hold only Small vehicle
     * - Medium spot can hold Small/Medium
     * - Large spot can hold Small/Medium/Large
     */
    private boolean isSpotSizeCompatible(String spotSize, String vehicleSize) {
        String s = (spotSize == null ? "" : spotSize.trim().toLowerCase());
        String v = (vehicleSize == null ? "" : vehicleSize.trim().toLowerCase());

        int spotRank = sizeRank(s);
        int vehicleRank = sizeRank(v);

        return spotRank >= vehicleRank;
    }

    private int sizeRank(String size) {
        return switch (size) {
            case "small" -> 1;
            case "medium" -> 2;
            case "large" -> 3;
            default -> 3; // unknown => treat as Large to avoid blocking
        };
    }

    // =========================
    // DB-only helpers (existing)
    // =========================

    private void updateConveyorMaxWeight_DBOnly(int conveyorId, int newWeight) {
        String sql = "UPDATE Conveyor SET [MaxWeight]=? WHERE [ID]=? AND [isActive]=True";

        try (Connection conn = db.open();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, newWeight);
            ps.setInt(2, conveyorId);

            int updated = ps.executeUpdate();
            if (updated == 0) throw new IllegalArgumentException("Conveyor not found: " + conveyorId);

        } catch (SQLException e) {
            throw new RuntimeException("Failed to update conveyor max weight: " + e.getMessage(), e);
        }
    }

    /**
     * Rule:
     * - LastStatus becomes the CURRENT Status (only if current is Testing/Operational)
     * - EXCEPT when switching to Off or Paused -> do NOT change LastStatus
     * - LastStatus never becomes Off/Paused
     */
    private void updateConveyorStatus_WithHistoryRule(int conveyorId, ConveyorStatus newStatus) {
        if (isConveyorInactive(conveyorId)) {
            throw new IllegalStateException("Conveyor is inactive.");
        }

        String newText = (newStatus == null ? null : newStatus.name());

        String sql =
                "UPDATE Conveyor " +
                "SET " +
                "  [LastStatus] = IIF( " +
                "       ([Status] IN ('Testing','Operational')) " +
                "       AND (? NOT IN ('Off','Paused')), " +
                "       [Status], " +
                "       [LastStatus] " +
                "  ), " +
                "  [Status] = ? " +
                "WHERE [ID] = ? AND [isActive]=True";

        try (Connection conn = db.open();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, newText);
            ps.setString(2, newText);
            ps.setInt(3, conveyorId);

            int updated = ps.executeUpdate();
            if (updated == 0) throw new IllegalArgumentException("Conveyor not found: " + conveyorId);

        } catch (SQLException e) {
            throw new RuntimeException("Failed to update conveyor status: " + e.getMessage(), e);
        }
    }

    private Conveyor getConveyorById(int id) {
        ensureDb();

        String sql =
                "SELECT [ID],[ParkingLotID],[Floor],[X],[Y],[MaxWeight],[Status],[LastStatus],[isActive] " +
                "FROM Conveyor WHERE [ID]=?";

        try (Connection conn = db.open();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, id);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("Conveyor not found: " + id);

                int lotId = rs.getInt("ParkingLotID");

                Integer floor = getNullableInt(rs, "Floor");
                Integer x = getNullableInt(rs, "X");
                Integer y = getNullableInt(rs, "Y");

                int maxW = rs.getInt("MaxWeight");

                ConveyorStatus status = parseStatus(rs.getString("Status"));
                ConveyorLastStatus lastStatus = safeParseLastStatus(rs.getString("LastStatus"));

                boolean isActive = true;
                try { isActive = rs.getBoolean("isActive"); } catch (Exception ignore) {}

                return new Conveyor(id, lotId, floor, x, y, maxW, status, lastStatus, isActive);
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed to read conveyor: " + e.getMessage(), e);
        }
    }

    private boolean isConveyorInactive(int id) {
        String sql = "SELECT [isActive] FROM Conveyor WHERE [ID]=?";
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
            throw new RuntimeException("Failed to check conveyor active flag: " + e.getMessage(), e);
        }
    }

    private ConveyorStatus parseStatus(String st) {
        if (st == null || st.isBlank()) return ConveyorStatus.Off;
        try { return ConveyorStatus.valueOf(st.trim()); }
        catch (Exception ignore) { return ConveyorStatus.Off; }
    }

    private ConveyorLastStatus safeParseLastStatus(String s) {
        if (s == null || s.isBlank()) return null;
        try { return ConveyorLastStatus.valueOf(s.trim()); }
        catch (Exception ignore) { return null; }
    }

    private void requirePositiveId(int id) {
        if (id <= 0) throw new IllegalArgumentException("Conveyor ID must be positive.");
    }

    private void requirePositiveWeight(int w) {
        if (w <= 0) throw new IllegalArgumentException("MaxWeight must be positive.");
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

    private Integer getNullableInt(ResultSet rs, String col) throws SQLException {
        Object o = rs.getObject(col);
        if (o == null) return null;
        return ((Number) o).intValue();
    }
    private Integer findAvailableConveyorId(Connection c, int parkingLotId, double vehicleWeight)
            throws SQLException {

        String sql =
            "SELECT TOP 1 c.ID " +
            "FROM Conveyor c " +
            "WHERE c.ParkingLotID=? " +
            "  AND c.isActive=True " +
            "  AND c.Status='OPERATIONAL' " +
            "  AND (c.LastStatus='AVAILABLE' OR c.LastStatus IS NULL) " +
            "  AND c.MaxWeight >= ? " +
            "  AND c.ID NOT IN (SELECT conveyorID FROM ParkingSession WHERE endTime IS NULL) " +
            "ORDER BY c.ID";

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, parkingLotId);
            ps.setDouble(2, vehicleWeight);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return rs.getInt(1);
            }
        }
    }

}
