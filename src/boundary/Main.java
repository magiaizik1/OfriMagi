package boundary;

import control.*;
import simulation.*;

import javax.swing.*;

public class Main {

    public static void main(String[] args) {

        SwingUtilities.invokeLater(() -> {

            // ===== DB =====
            AccessDb db = new AccessDb("db/parkwise_OfriMagi.accdb");

            // ===== PORTS (Mocks) =====
            ConsoleConveyorsMock conveyorsPort = new ConsoleConveyorsMock();
            ConsoleGateSensorMock gateSensorPort = new ConsoleGateSensorMock();
            ConsolePaymentGatewayMock paymentPort = new ConsolePaymentGatewayMock();
            ConsoleSmsGatewayMock smsPort = new ConsoleSmsGatewayMock();

            // ===== Controllers =====
            ParkingLotManagementController parkingLotController =
                    new ParkingLotManagementController(db);

            ParkingSessionManagementController sessionController =
                    new ParkingSessionManagementController(
                            db,
                            conveyorsPort,
                            gateSensorPort,
                            paymentPort,
                            smsPort
                    );

            CityManagementController cityController =
                    new CityManagementController(db);
            ConveyorManagementController conveyorController =
                    new ConveyorManagementController(db);
            PriceHistoryManagementController priceHistoryController =
                    new PriceHistoryManagementController(db);
            PriceListManagementController priceListController =
                    new PriceListManagementController(db);

            // ===== Admin UI =====
            ParkingLotDashboardUI dashboard = new ParkingLotDashboardUI(
                    db,
                    parkingLotController,
                    cityController,
                    conveyorController,
                    priceHistoryController,
                    priceListController
            );
            dashboard.setVisible(true);

            // ===== SENSOR DEMO =====
            ParkingSensorRunner sensor =
                    new ParkingSensorRunner(sessionController, parkingLotController);

            System.out.println("\n=== SENSOR SIMULATION START ===");
            sensor.simulateSingleArrival();
        });
    }
}