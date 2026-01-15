package entity;

public enum ParkingSessionState {

    // ===== ENTRY / PARKING =====
    MOVING_TO_PARKING,

    // ===== EXIT FLOW =====
    MOVING_TO_EXIT,
    WAITING_FOR_PAYMENT,

    // ===== PAYMENT =====
    PAYMENT_DECLINED,

    // ===== ERRORS =====
    ERROR_MOVE_TO_GATE,

    // ===== FINAL =====
    COMPLETED;

    // =========================
    // ===== HELPERS ===========
    // =========================

    public static ParkingSessionState fromDb(String s) {
        if (s == null) return null;
        return ParkingSessionState.valueOf(s.trim().toUpperCase());
    }

    public String toDb() {
        return this.name();
    }
}

