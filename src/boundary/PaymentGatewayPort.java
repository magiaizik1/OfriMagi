package boundary;

/**
 * External actor boundary: Payment Gateway (PG).
 * System sends a payment request to PG, and PG returns approved/declined via callback.
 */
public interface PaymentGatewayPort {

    interface PaymentCallback {
        void onApproved(String paymentId);
        void onDeclined(String paymentId, String reason);
    }

    /**
     * System -> PG:
     * Send a payment request (handled outside system).
     */
    void requestPayment(int sessionId, int customerId, String phone, double amount, PaymentCallback cb);
}