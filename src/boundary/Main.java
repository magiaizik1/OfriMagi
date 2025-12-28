package boundary;

import control.AccessDb;
import control.CityManagementController;
import control.ConveyorManagementController;
import control.ParkingLotManagementController;
import control.PriceHistoryManagementController;
import control.PriceListManagementController;

import javax.swing.*;
import java.sql.Connection;

/**
 * Main entry point of ParkWise system.
 * Creates a single AccessDb instance and passes it to all controllers and UIs.
 */
public class Main {

    public static void main(String[] args) {

        SwingUtilities.invokeLater(() -> {

            // Single DB instance for whole system
            AccessDb db = new AccessDb("db/parkwise_OfriMagi.accdb");

            // Test DB connection on startup
            try (Connection c = db.open()) {
                // OK
            } catch (Exception e) {
                JOptionPane.showMessageDialog(null,
                        "Database connection failed:\n" + e.getMessage(),
                        "DB Error",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }

            CityManagementController cityController = new CityManagementController(db);
            ParkingLotManagementController parkingLotController = new ParkingLotManagementController(db);
            ConveyorManagementController conveyorController = new ConveyorManagementController(db);
            PriceListManagementController priceListController = new PriceListManagementController(db);
            PriceHistoryManagementController priceHistoryController = new PriceHistoryManagementController(db);

            LoginUI loginUI = new LoginUI(() -> {

                ParkingLotDashboardUI dashboard =
                        new ParkingLotDashboardUI(
                                db,                     // <<< חובה!
                                parkingLotController,
                                cityController,
                                conveyorController,
                                priceHistoryController,
                                priceListController
                        );

                dashboard.setVisible(true);
            });

            loginUI.setVisible(true);
        });
    }
}