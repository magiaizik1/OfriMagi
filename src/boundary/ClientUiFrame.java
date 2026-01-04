package boundary;

import control.ParkingSessionManagementController;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class ClientUiFrame extends JFrame {

    private final ParkingSessionManagementController sessionCtrl;

    private final JTextField phoneField = new JTextField(16);
    private final JTextField vehicleField = new JTextField(16);
    private final JTextArea output = new JTextArea(12, 52);

    public ClientUiFrame(ParkingSessionManagementController sessionCtrl) {
        super("ParkWise - Client");
        this.sessionCtrl = sessionCtrl;

        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));

        // ===== Form =====
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(4, 6, 4, 6);
        gc.anchor = GridBagConstraints.WEST;

        gc.gridx = 0; gc.gridy = 0;
        form.add(new JLabel("Phone:"), gc);
        gc.gridx = 1;
        form.add(phoneField, gc);

        gc.gridx = 0; gc.gridy = 1;
        form.add(new JLabel("Vehicle number:"), gc);
        gc.gridx = 1;
        form.add(vehicleField, gc);

        add(form, BorderLayout.NORTH);

        // ===== Buttons =====
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton btnDetails = new JButton("Show Active Details");
        JButton btnEnd = new JButton("End Parking");
        JButton btnPay = new JButton("Pay Now");
        JButton btnReceipt = new JButton("Request Receipt");
        JButton btnClear = new JButton("Clear");

        buttons.add(btnDetails);
        buttons.add(btnEnd);
        buttons.add(btnPay);
        buttons.add(btnReceipt);
        buttons.add(btnClear);

        add(buttons, BorderLayout.CENTER);

        output.setEditable(false);
        output.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        add(new JScrollPane(output), BorderLayout.SOUTH);

        btnDetails.addActionListener(e -> showDetails());
        btnEnd.addActionListener(e -> endParking());
        btnPay.addActionListener(e -> payNow());
        btnReceipt.addActionListener(e -> receipt());
        btnClear.addActionListener(e -> output.setText(""));

        pack();
        setLocationRelativeTo(null);
    }

    private void showDetails() {
        String phone = phoneField.getText().trim();
        if (phone.isEmpty()) {
            log("❗ Enter phone.\n\n");
            return;
        }
        try {
            List<String> lines = sessionCtrl.getActiveParkingDetailsByPhone(phone);
            log("=== Active Parking Details ===\n");
            if (lines.isEmpty()) log("(none)\n");
            for (String s : lines) log("• " + s + "\n");
            log("\n");
        } catch (Exception ex) {
            log("❌ " + ex.getMessage() + "\n\n");
        }
    }

    private void endParking() {
        String phone = phoneField.getText().trim();
        String vehicle = vehicleField.getText().trim();
        if (phone.isEmpty() || vehicle.isEmpty()) {
            log("❗ Enter phone + vehicle.\n\n");
            return;
        }
        try {
            String msg = sessionCtrl.requestEndParkingByVehicleAndPhone(vehicle, phone);
            log("=== End Parking ===\n" + msg + "\n\n");
        } catch (Exception ex) {
            log("❌ " + ex.getMessage() + "\n\n");
        }
    }

    private void payNow() {
        String phone = phoneField.getText().trim();
        String vehicle = vehicleField.getText().trim();
        if (phone.isEmpty() || vehicle.isEmpty()) {
            log("❗ Enter phone + vehicle.\n\n");
            return;
        }
        try {
            // ✅ this is the correct GUI method that matches your controller code
            String msg = sessionCtrl.requestPaymentByVehicleAndPhone(vehicle, phone);
            log("=== Payment ===\n" + msg + "\n\n");
        } catch (Exception ex) {
            log("❌ " + ex.getMessage() + "\n\n");
        }
    }

    private void receipt() {
        String phone = phoneField.getText().trim();
        String vehicle = vehicleField.getText().trim();
        if (phone.isEmpty() || vehicle.isEmpty()) {
            log("❗ Enter phone + vehicle.\n\n");
            return;
        }
        try {
            String msg = sessionCtrl.requestReceiptByVehicleAndPhone(vehicle, phone);
            log("=== Receipt ===\n" + msg + "\n\n");
        } catch (Exception ex) {
            log("❌ " + ex.getMessage() + "\n\n");
        }
    }

    private void log(String s) {
        output.append(s);
        output.setCaretPosition(output.getDocument().getLength());
    }
}
