package simulation;

import boundary.PaymentGatewayPort;

import java.util.UUID;

public class ConsolePaymentGatewayMock implements PaymentGatewayPort {

    private boolean approveAlways = true;

    public void setApproveAlways(boolean approveAlways) {
        this.approveAlways = approveAlways;
    }

    @Override
    public void requestPayment(int sessionId, int customerId, String phone, double amount, PaymentCallback cb) {

        String paymentId = "PAY-" + UUID.randomUUID();

        System.out.println("=== [MOCK PAYMENT] ===");
        System.out.println("sessionId=" + sessionId + ", customerId=" + customerId +
                ", phone=" + phone + ", amount=" + amount);
        System.out.println("======================");

        // simulate approve/decline immediately
        if (approveAlways) {
            cb.onApproved(paymentId);
        } else {
            cb.onDeclined(paymentId, "Mock decline");
        }
    }
}