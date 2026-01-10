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
     * The parking spot currently assigned to the vehicle.
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

    public ParkingSession(int id, int vehicleId, int parkingLotId, Integer parkingSpotId,
                          Integer conveyorId, LocalDateTime startTime, LocalDateTime endTime, String state) {
        this.id = id;
        this.vehicleId = vehicleId;
        this.parkingLotId = parkingLotId;
        this.parkingSpotId = parkingSpotId;
        this.conveyorId = conveyorId;
        this.startTime = startTime;
        this.endTime = endTime;
        this.state = state;
    }

    public ParkingSession(int vehicleId, int parkingLotId, LocalDateTime startTime, String state) {
        this.vehicleId = vehicleId;
        this.parkingLotId = parkingLotId;
        this.startTime = startTime;
        this.state = state;
    }

    // -------- Business Logic (NO DB) --------

    public void updateState(String newState) {
        this.state = newState;
    }

    public void assignParkingSpot(Integer parkingSpotId) {
        this.parkingSpotId = parkingSpotId;
    }

    public void assignConveyor(Integer conveyorId) {
        this.conveyorId = conveyorId;
    }

    public void closeSession(LocalDateTime endTime) {
        this.endTime = endTime;
        this.state = "COMPLETED";
    }

    // -------- Getters --------

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

    // -------- Setters --------

    public void setId(int id) {
        this.id = id;
    }

    public void setParkingSpotId(Integer parkingSpotId) {
        this.parkingSpotId = parkingSpotId;
    }

    public void setConveyorId(Integer conveyorId) {
        this.conveyorId = conveyorId;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public void setState(String state) {
        this.state = state;
    }
}
