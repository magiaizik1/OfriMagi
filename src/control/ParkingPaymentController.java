package control;

import boundary.PaymentGatewayPort;
import boundary.SmsGatewayPort;
import entity.PriceList;

import java.sql.Connection;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.YearMonth;

public class ParkingPaymentController {

    private final AccessDb db;
    private final PaymentGatewayPort paymentGatewayPort;
    private final SmsGatewayPort smsGatewayPort;

    private static final double CLUB_DISCOUNT_RATE = 0.05;

    public ParkingPaymentController(AccessDb db,
                                    PaymentGatewayPort paymentGatewayPort,
                                    SmsGatewayPort smsGatewayPort) {
        this.db = db;
        this.paymentGatewayPort = paymentGatewayPort;
        this.smsGatewayPort = smsGatewayPort;
    }

    public void requestPaymentForExit(int sessionId, ParkingExitController exitController) {
        try (Connection c = db.open()) {

            AccessDb.SessionCoreRow s = db.loadSessionCore(c, sessionId);
            if (s.end != null) throw new RuntimeException("Session already completed");

            String state = db.getSessionState(c, sessionId);
            if (state == null || !"WAITING_FOR_PAYMENT".equalsIgnoreCase(state.trim())) {
                throw new RuntimeException("Session not in WAITING_FOR_PAYMENT (current=" + state + ")");
            }

            LocalDateTime now = LocalDateTime.now();

            PriceList price = db.getCurrentPriceListAt(c, s.parkingLotId, now);
            if (price == null) throw new RuntimeException("No active price list for lot " + s.parkingLotId);

            long minutes = Duration.between(s.start, now).toMinutes();
            CalcResult base = calculateAmountAndRate(minutes, price);

            Integer customerId = db.getCustomerIdByVehicle(c, s.vehicleId);
            String phone = (customerId == null) ? "" : safeString(db.getCustomerPhoneSafe(c, customerId));

            double finalAmount = applyClubBenefitsForExitAmount(c, customerId, s.parkingLotId, s.start, base.amount);

            if (paymentGatewayPort == null) return;

            int finalCustomerId = (customerId == null) ? 0 : customerId;
            paymentGatewayPort.requestPayment(
                    sessionId,
                    finalCustomerId,
                    phone,
                    finalAmount,
                    new PaymentGatewayPort.PaymentCallback() {
                        @Override
                        public void onApproved(String paymentId) {
                            try {
                                exitController.confirmPaymentAndExit(sessionId);
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        }

                        @Override
                        public void onDeclined(String paymentId, String reason) {
                            try {
                                db.updateSessionState(sessionId, "PAYMENT_DECLINED");
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        }
                    }
            );

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void generateReceipt(int sessionId) {
        try (Connection c = db.open()) {

            if (db.receiptExists(c, sessionId))
                throw new RuntimeException("Receipt already exists");

            AccessDb.SessionCoreRow s = db.loadSessionCore(c, sessionId);
            if (s.end == null)
                throw new RuntimeException("Session still active");

            PaymentComputation comp = computeFinalAmountForSessionTx(c, sessionId);
            db.insertReceipt(sessionId, comp.finalAmount, comp.appliedRate);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public PaymentComputation computeFinalAmountForSession(int sessionId) {
        try (Connection c = db.open()) {
            return computeFinalAmountForSessionTx(c, sessionId);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private PaymentComputation computeFinalAmountForSessionTx(Connection c, int sessionId) throws Exception {

        AccessDb.SessionCoreRow s = db.loadSessionCore(c, sessionId);
        if (s.end == null)
            throw new RuntimeException("Session still active, cannot compute final amount");

        PriceList price = db.getCurrentPriceListAt(c, s.parkingLotId, s.end);
        if (price == null)
            throw new RuntimeException("No active price list for lot " + s.parkingLotId);

        long minutes = Duration.between(s.start, s.end).toMinutes();
        CalcResult base = calculateAmountAndRate(minutes, price);

        Integer customerId = db.getCustomerIdByVehicle(c, s.vehicleId);
        if (customerId == null) return new PaymentComputation(base.amount, base.rate);

        LocalDateTime joinDate = db.getMembershipJoinDate(c, customerId);
        if (joinDate == null) return new PaymentComputation(base.amount, base.rate);

        boolean free = isFreeFirstSessionInRegistrationMonth(c, customerId, s.parkingLotId, s.start, joinDate);
        if (free) return new PaymentComputation(0.0, base.rate + " + ClubFreeFirstInMonth");

        double discounted = round2(base.amount * (1.0 - CLUB_DISCOUNT_RATE));
        return new PaymentComputation(discounted, base.rate + " + ClubDiscount5%");
    }

    // ===== CLUB PRICING =====

    private double applyClubBenefitsForExitAmount(Connection c, Integer customerId, int parkingLotId,
                                                  LocalDateTime sessionStart, double baseAmount) throws Exception {

        if (customerId == null) return baseAmount;

        LocalDateTime joinDate = db.getMembershipJoinDate(c, customerId);
        if (joinDate == null) return baseAmount;

        boolean free = isFreeFirstSessionInRegistrationMonth(c, customerId, parkingLotId, sessionStart, joinDate);
        if (free) return 0.0;

        return round2(baseAmount * (1.0 - CLUB_DISCOUNT_RATE));
    }

    private boolean isFreeFirstSessionInRegistrationMonth(
            Connection c,
            int customerId,
            int parkingLotId,
            LocalDateTime sessionStart,
            LocalDateTime joinDate
    ) throws Exception {

        YearMonth joinYM = YearMonth.from(joinDate);
        YearMonth sessionYM = YearMonth.from(sessionStart);
        if (!joinYM.equals(sessionYM)) return false;

        if (!db.isPreferredLotAtTime(c, customerId, parkingLotId, sessionStart)) return false;

        return !db.existsAnyCompletedSessionInJoinMonth(c, customerId, joinYM, sessionStart);
    }

    // ===== UTIL =====

    public CalcResult calculateAmountAndRate(long minutes, PriceList p) {
        long hours = (long) Math.ceil(minutes / 60.0);

        if (hours <= 1)
            return new CalcResult(p.getFirstHourPrice(), "FirstHour");

        if (hours < 24)
            return new CalcResult(
                    p.getFirstHourPrice() + (hours - 1) * p.getAdditionalHourPrice(),
                    "AdditionalHours"
            );

        return new CalcResult(p.getFullDayPrice(), "FullDay");
    }

    public double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private String safeString(String s) {
        return s == null ? "" : s;
    }

    // ===== INNER CLASSES =====

    public static class PaymentComputation {
        public final double finalAmount;
        public final String appliedRate;

        public PaymentComputation(double finalAmount, String appliedRate) {
            this.finalAmount = finalAmount;
            this.appliedRate = appliedRate;
        }
    }

    public static class CalcResult {
        public double amount;
        public String rate;

        public CalcResult(double amount, String rate) {
            this.amount = amount;
            this.rate = rate;
        }
    }
}
