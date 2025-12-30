package boundary;

import control.*;

import javax.swing.*;
import java.awt.*;

public class Main {

    public static void main(String[] args) {

        SwingUtilities.invokeLater(() -> {

            // ===== DB =====
        	AccessDb db = new AccessDb("db/parkwise_OfriMagi.accdb"); 

            // ===== Controllers (קיימים בפרויקט) =====
            ParkingLotManagementController parkingLotController =
                    new ParkingLotManagementController(db);
            CityManagementController cityController =
                    new CityManagementController(db);
            ConveyorManagementController conveyorController =
                    new ConveyorManagementController(db);
            PriceHistoryManagementController priceHistoryController =
                    new PriceHistoryManagementController(db);
            PriceListManagementController priceListController =
                    new PriceListManagementController(db);

            // ===== Launcher Window =====
            JFrame frame = new JFrame("ParkWise – Run Interface");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(450, 200);
            frame.setLocationRelativeTo(null);
            frame.setLayout(new BorderLayout(10, 10));

            JLabel title = new JLabel("Choose interface to run:", SwingConstants.CENTER);
            title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
            frame.add(title, BorderLayout.NORTH);

            JButton adminBtn = new JButton("Manager / Admin");
            JButton sensorBtn = new JButton("Gate Sensor (Dummy)");

            // ===== Admin Button =====
            adminBtn.addActionListener(e -> {
                new ParkingLotDashboardUI(
                        db,
                        parkingLotController,
                        cityController,
                        conveyorController,
                        priceHistoryController,
                        priceListController
                ).setVisible(true);
            });

            // ===== Sensor Button =====
            sensorBtn.addActionListener(e -> {
                new GateSensorDummyUI(db, parkingLotController)
                        .setVisible(true);
            });

            JPanel center = new JPanel(new GridLayout(1, 2, 15, 15));
            center.setBorder(BorderFactory.createEmptyBorder(30, 20, 30, 20));
            center.add(adminBtn);
            center.add(sensorBtn);

            frame.add(center, BorderLayout.CENTER);

            frame.setVisible(true);
        });
    }
}