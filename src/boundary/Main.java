package boundary;

import control.*;
import simulation.*;

import javax.swing.*;

public class Main {

    public static void main(String[] args) {

        SwingUtilities.invokeLater(() -> {

            // ===== DB =====
            AccessDb db = new AccessDb("db/parkwise_OfriMagi.accdb");

            // ===== HARDWARE ACTORS (Ports Implementations) -> Console =====
            ConsoleConveyorsMock conveyorsPort = new ConsoleConveyorsMock(db);
            ConsoleGateSensorMock gateSensorPort = new ConsoleGateSensorMock();
            ConsolePaymentGatewayMock paymentPort = new ConsolePaymentGatewayMock();
            ConsoleSmsGatewayMock smsPort = new ConsoleSmsGatewayMock();

            // ===== Controllers =====
            ParkingLotManagementController parkingLotController = new ParkingLotManagementController(db);

            ParkingSessionManagementController sessionController =
                    new ParkingSessionManagementController(db, conveyorsPort, gateSensorPort, paymentPort, smsPort);

            CityManagementController cityController = new CityManagementController(db);
            ConveyorManagementController conveyorController = new ConveyorManagementController(db);
            PriceHistoryManagementController priceHistoryController = new PriceHistoryManagementController(db);
            PriceListManagementController priceListController = new PriceListManagementController(db);

            // ===== Human actors GUI: Role selection =====
            RoleSelectUI roleSelect = new RoleSelectUI(new RoleSelectUI.RoleCallback() {
                

                @Override
                public void onAdmin() {
                    try {
                        ParkingLotDashboardUI dashboard = new ParkingLotDashboardUI(
                                db,
                                parkingLotController,
                                cityController,
                                conveyorController,
                                priceHistoryController,
                                priceListController,
                                sessionController
                        );

                        dashboard.setVisible(true);

                    } catch (Exception ex) {
                        ex.printStackTrace();
                        JOptionPane.showMessageDialog(
                                null,
                                "Admin failed to open.\n" +
                                "Most likely DB path is wrong or missing files.\n\n" +
                                ex.getMessage(),
                                "Error",
                                JOptionPane.ERROR_MESSAGE
                        );
                    }
                }

                @Override
                public void onClient() {
                    ClientUiFrame clientFrame = new ClientUiFrame(sessionController);
                    clientFrame.setVisible(true);
                }
            });

            roleSelect.setVisible(true);

            // ===== SENSOR DEMO (Optional) =====
            // ✅ Runs in console and prints hardware logs (GS/CC/PG/SMS)
            new Thread(() -> {
                ParkingSensorRunner sensor = new ParkingSensorRunner(sessionController, parkingLotController);
                sensor.simulateSingleArrival();
            }).start();
        });
    }
}
