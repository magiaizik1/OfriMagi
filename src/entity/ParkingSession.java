package entity;

import java.time.LocalDateTime;

/**
 * ParkingSession
 * מייצג חניית רכב אחת מהכניסה ועד היציאה.
 * תואם 1:1 לטבלת ParkingSession ב-Access.
 */
public class ParkingSession {

    private final int id;              // ID AutoNumber
    private LocalDateTime startTime;   // startTime
    private LocalDateTime endTime;     // endTime (יכול להיות null)
    private int parkingLotID;          // FK -> ParkingLot.ID
    private int vehicleID;             // FK -> Vehicle.ID
    private int parkingSpotID;         // FK -> ParkingSpot.ID
    private Integer conveyorID;        // FK -> Conveyor.ID (nullable)

    public ParkingSession(int id,
                          LocalDateTime startTime,
                          LocalDateTime endTime,
                          int parkingLotID,
                          int vehicleID,
                          int parkingSpotID,
                          Integer conveyorID) {

        this.id = id;
        this.startTime = startTime;
        this.endTime = endTime;
        this.parkingLotID = parkingLotID;
        this.vehicleID = vehicleID;
        this.parkingSpotID = parkingSpotID;
        this.conveyorID = conveyorID;
    }

    public int getId() { return id; }
    public LocalDateTime getStartTime() { return startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public int getParkingLotID() { return parkingLotID; }
    public int getVehicleID() { return vehicleID; }
    public int getParkingSpotID() { return parkingSpotID; }
    public Integer getConveyorID() { return conveyorID; }

    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
    public void setConveyorID(Integer conveyorID) { this.conveyorID = conveyorID; }
}