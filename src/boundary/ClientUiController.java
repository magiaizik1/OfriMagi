package boundary;

import control.ParkingSessionManagementController;

import java.util.Collections;
import java.util.List;

/**
 * Boundary Controller for Client UI.
 *
 * ✅ Responsibilities (Boundary):
 * - Validate human input (phone / vehicle)
 * - Prevent GUI crashes (catch exceptions)
 * - Return user-friendly messages / results
 *
 * ✅ Does NOT touch DB directly.
 * ✅ Calls ONLY existing methods in ParkingSessionManagementController.
 */
public class ClientUiController {

    private final ParkingSessionManagementController sessionCtrl;

    public ClientUiController(ParkingSessionManagementController sessionCtrl) {
        if (sessionCtrl == null) throw new IllegalArgumentException("sessionCtrl is null");
        this.sessionCtrl = sessionCtrl;
    }

    // =========================
    // ===== GUI Actions =======
    // =========================

    /** Show active sessions/details by phone (as implemented in ParkingSessionManagementController). */
    public List<String> showActiveDetails(String phoneInput) {
        String phone = normalize(phoneInput);

        String err = validatePhone(phone);
        if (err != null) return List.of(err);

        try {
            List<String> lines = sessionCtrl.getActiveParkingDetailsByPhone(phone);
            return (lines == null || lines.isEmpty())
                    ? List.of("No active parking sessions for this phone.")
                    : lines;
        } catch (Exception e) {
            return List.of("❌ Could not fetch active details: " + safeMsg(e));
        }
    }

    /** End parking (vehicle + phone) using existing requestEndParkingByVehicleAndPhone. */
    public String endParking(String vehicleInput, String phoneInput) {
        String phone = normalize(phoneInput);
        String vehicle = normalize(vehicleInput);

        String err = validatePhone(phone);
        if (err != null) return err;

        err = validateVehicleNumber(vehicle);
        if (err != null) return err;

        try {
            return sessionCtrl.requestEndParkingByVehicleAndPhone(vehicle, phone);
        } catch (Exception e) {
            return "❌ Could not end parking: " + safeMsg(e);
        }
    }

    /** Request payment (vehicle + phone) using existing requestPaymentByVehicleAndPhone. */
    public String payNow(String vehicleInput, String phoneInput) {
        String phone = normalize(phoneInput);
        String vehicle = normalize(vehicleInput);

        String err = validatePhone(phone);
        if (err != null) return err;

        err = validateVehicleNumber(vehicle);
        if (err != null) return err;

        try {
            return sessionCtrl.requestPaymentByVehicleAndPhone(vehicle, phone);
        } catch (Exception e) {
            return "❌ Could not request payment: " + safeMsg(e);
        }
    }

    /** Request receipt (vehicle + phone) using existing requestReceiptByVehicleAndPhone. */
    public String requestReceipt(String vehicleInput, String phoneInput) {
        String phone = normalize(phoneInput);
        String vehicle = normalize(vehicleInput);

        String err = validatePhone(phone);
        if (err != null) return err;

        err = validateVehicleNumber(vehicle);
        if (err != null) return err;

        try {
            return sessionCtrl.requestReceiptByVehicleAndPhone(vehicle, phone);
        } catch (Exception e) {
            return "❌ Could not generate receipt: " + safeMsg(e);
        }
    }

    // =========================
    // ===== Validation ========
    // =========================

    private String validatePhone(String phone) {
        if (phone.isEmpty()) return "❗ Please enter phone number.";
        // Minimal validation: digits only, length 6-15 (works for Israel + generic)
        if (!phone.matches("\\d{6,15}")) {
            return "❗ Invalid phone. Please enter digits only (6-15 digits).";
        }
        return null;
    }

    private String validateVehicleNumber(String vehicle) {
        if (vehicle.isEmpty()) return "❗ Please enter vehicle number.";
        // Your DB uses Vehicle.ID as "vehicle number" -> must be an integer
        if (!vehicle.matches("\\d+")) return "❗ Invalid vehicle number. Digits only.";
        // Avoid silly values
        try {
            long v = Long.parseLong(vehicle);
            if (v <= 0) return "❗ Invalid vehicle number. Must be positive.";
        } catch (Exception e) {
            return "❗ Invalid vehicle number.";
        }
        return null;
    }

    // =========================
    // ===== Helpers ===========
    // =========================

    private String normalize(String s) {
        return s == null ? "" : s.trim();
    }

    private String safeMsg(Exception e) {
        String m = (e == null) ? null : e.getMessage();
        if (m == null || m.trim().isEmpty()) return "Unexpected error.";
        return m;
    }
}
