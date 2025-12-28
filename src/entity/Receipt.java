package entity;

import java.time.LocalDateTime;

/**
 * Receipt – קבלה על חניה.
 */
public class Receipt {

    private final int id;
    private int parkingSessionID;
    private double finalAmount;
    private LocalDateTime paymentDate;
    private String appliedRate;

    public Receipt(int id, int parkingSessionID,
                   double finalAmount,
                   LocalDateTime paymentDate,
                   String appliedRate) {

        this.id = id;
        this.parkingSessionID = parkingSessionID;
        this.finalAmount = finalAmount;
        this.paymentDate = paymentDate;
        this.appliedRate = appliedRate;
    }

    public int getId() { return id; }
    public int getParkingSessionID() { return parkingSessionID; }
    public double getFinalAmount() { return finalAmount; }
    public LocalDateTime getPaymentDate() { return paymentDate; }
    public String getAppliedRate() { return appliedRate; }
}