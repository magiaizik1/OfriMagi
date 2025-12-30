package entity;

import java.time.LocalDateTime;

/**
 * Represents a single parking session of a vehicle in a parking lot.
 * A parking session starts when the vehicle enters the parking lot
 * and ends after payment and exit.
 */
public class ParkingSession {

    private int id;

    private int vehicleId;
    private int parkingLotId;

    /**
     * The parking spot where the vehicle is parked.
     * NULL while the vehicle is not yet parked.
     */
    private Integer parkingSpotId;

    /**
     * The conveyor currently handling the vehicle.
     * NULL when the vehicle is not on a conveyor.
     */
    private Integer conveyorId;

    private LocalDateTime startTime;
    private LocalDateTime endTime;

    /**
     * Represents the current state of the parking session.
     * This field allows the system to know at each moment
     * where the vehicle is and what stage it is in.
     *
     * Possible values:
     * ARRIVED_AT_GATE
     * WAITING_FOR_DETAILS
     * WAITING_FOR_CONVEYOR
     * MOVING_TO_PARKING
     * PARKED
     * MOVING_TO_EXIT
     * WAITING_FOR_PAYMENT
     * COMPLETED
     */
    private String state;

    public ParkingSession() {}

    // ---------- Getters ----------

    public int getId() {
        return id;
    }

    public int getVehicleId() {
        return vehicleId;
    }

    public int getParkingLotId() {
        return parkingLotId;
    }

    public Integer getParkingSpotId() {
        return parkingSpotId;
    }

    public Integer getConveyorId() {
        return conveyorId;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public String getState() {
        return state;
    }

    // ---------- Setters ----------

    public void setId(int id) {
        this.id = id;
    }

    public void setVehicleId(int vehicleId) {
        this.vehicleId = vehicleId;
    }

    public void setParkingLotId(int parkingLotId) {
        this.parkingLotId = parkingLotId;
    }

    public void setParkingSpotId(Integer parkingSpotId) {
        this.parkingSpotId = parkingSpotId;
    }

    public void setConveyorId(Integer conveyorId) {
        this.conveyorId = conveyorId;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public void setState(String state) {
        this.state = state;
    }
}