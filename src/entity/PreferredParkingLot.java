package entity;

import java.time.LocalDate;

public class PreferredParkingLot {
    private final int customerId;
    private final int parkingLotId;
    private final LocalDate selectionDate;

    public PreferredParkingLot(int customerId, int parkingLotId, LocalDate selectionDate) {
        this.customerId = customerId;
        this.parkingLotId = parkingLotId;
        this.selectionDate = selectionDate;
    }

    public int getCustomerId() { return customerId; }
    public int getParkingLotId() { return parkingLotId; }
    public LocalDate getSelectionDate() { return selectionDate; }

    @Override
    public String toString() {
        return "PreferredParkingLot{customerId=" + customerId +
                ", parkingLotId=" + parkingLotId +
                ", selectionDate=" + selectionDate + "}";
    }
}