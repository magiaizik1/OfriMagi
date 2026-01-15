package entity;

import java.time.LocalDateTime;

public class ParkingSession {

    private int id;
    private int vehicleId;
    private int parkingLotId;
    private Integer parkingSpotId;   
    private Integer conveyorId;

    private LocalDateTime startTime;
    private LocalDateTime endTime;

    private ParkingSessionState state;

    // ===== EXISTING CONSTRUCTOR (לא נוגעים בלוגיקה, רק מצב התחלתי תקין) =====
    public ParkingSession(int id, int vehicleId, int parkingLotId, LocalDateTime startTime) {
        this.id = id;
        this.vehicleId = vehicleId;
        this.parkingLotId = parkingLotId;
        this.startTime = startTime;
        this.state = ParkingSessionState.MOVING_TO_PARKING; // במקום ACTIVE
    }

    // ===== CONSTRUCTOR לשימוש AccessDb =====
    public ParkingSession(
            int id,
            int vehicleId,
            int parkingLotId,
            Integer parkingSpotId,
            Integer conveyorId,
            LocalDateTime startTime,
            LocalDateTime endTime,
            String state
    ) {
        this.id = id;
        this.vehicleId = vehicleId;
        this.parkingLotId = parkingLotId;
        this.parkingSpotId = parkingSpotId;
        this.conveyorId = conveyorId;
        this.startTime = startTime;
        this.endTime = endTime;

        // המרה מ-String ל-enum לפי המצבים האמיתיים במערכת
        if (state == null || state.isBlank()) {
            this.state = ParkingSessionState.MOVING_TO_PARKING;
        } else {
            this.state = ParkingSessionState.fromDb(state);
        }
    }

    // =========================
    // ===== Domain Logic ======
    // =========================

    /**
     * סגירת סשן – בדיוק כמו שהיה:
     * רק קובע endTime ומצב COMPLETED
     */
    public void closeSession(LocalDateTime endTime) {
        this.endTime = endTime;
        this.state = ParkingSessionState.COMPLETED;
    }

    /**
     * עדכון מצב חופשי ע"י קונטרולרים (כמו בקוד המקורי)
     */
    public void updateState(String newState) {
        if (newState == null || newState.isBlank()) return;
        this.state = ParkingSessionState.fromDb(newState);
    }

    // =========================
    // ===== Getters / Setters =
    // =========================

    public int getId() { return id; }
    public int getVehicleId() { return vehicleId; }
    public int getParkingLotId() { return parkingLotId; }

    public Integer getParkingSpotId() { return parkingSpotId; }

    public Integer getConveyorId() { return conveyorId; }
    public void setConveyorId(Integer conveyorId) { this.conveyorId = conveyorId; }

    public LocalDateTime getStartTime() { return startTime; }
    public LocalDateTime getEndTime() { return endTime; }

    public ParkingSessionState getState() { return state; }
}
