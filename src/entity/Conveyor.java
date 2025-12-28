package entity;

/**
 * Conveyor entity
 *
 * שינוי דרישה:
 * - השדות Floor/X/Y יכולים להיות NULL (כי מוגדרים ע"י חומרה/מערכת חיצונית)
 * - מנהל לא מזין אותם בהוספה/עדכון
 *
 * שינוי נוסף:
 * - Soft delete: added isActive (true/false)
 */
public class Conveyor {

    private final int id;
    private int parkingLotId;

    // ✅ now nullable (was int)
    private Integer floorNumber;
    private Integer x;
    private Integer y;

    private int maxVehicleWeightKg;
    private ConveyorStatus status;

    // read-only outside (no setter)
    private final ConveyorLastStatus lastStatus;

    // ✅ NEW: soft delete flag
    private boolean isActive = true;

    // Existing constructor (KEEP) – no break
    public Conveyor(int id, int parkingLotId, Integer floorNumber,
                    Integer x, Integer y, int maxVehicleWeightKg,
                    ConveyorStatus status) {
        this(id, parkingLotId, floorNumber, x, y, maxVehicleWeightKg, status, null, true);
    }

    // Existing constructor with lastStatus (KEEP)
    public Conveyor(int id, int parkingLotId, Integer floorNumber,
                    Integer x, Integer y, int maxVehicleWeightKg,
                    ConveyorStatus status,
                    ConveyorLastStatus lastStatus) {
        this(id, parkingLotId, floorNumber, x, y, maxVehicleWeightKg, status, lastStatus, true);
    }

    // ✅ New constructor (DB reads)
    public Conveyor(int id, int parkingLotId, Integer floorNumber,
                    Integer x, Integer y, int maxVehicleWeightKg,
                    ConveyorStatus status,
                    ConveyorLastStatus lastStatus,
                    boolean isActive) {
        this.id = id;
        this.parkingLotId = parkingLotId;
        this.floorNumber = floorNumber;
        this.x = x;
        this.y = y;
        this.maxVehicleWeightKg = maxVehicleWeightKg;
        this.status = (status == null ? ConveyorStatus.Off : status);
        this.lastStatus = lastStatus; // Operational / Testing / null
        this.isActive = isActive;
    }

    public int getId() { return id; }

    public int getParkingLotId() { return parkingLotId; }
    public void setParkingLotId(int parkingLotId) { this.parkingLotId = parkingLotId; }

    public Integer getFloorNumber() { return floorNumber; }
    public void setFloorNumber(Integer floorNumber) { this.floorNumber = floorNumber; }

    public Integer getX() { return x; }
    public void setX(Integer x) { this.x = x; }

    public Integer getY() { return y; }
    public void setY(Integer y) { this.y = y; }

    public int getMaxVehicleWeightKg() { return maxVehicleWeightKg; }
    public void setMaxVehicleWeightKg(int maxVehicleWeightKg) { this.maxVehicleWeightKg = maxVehicleWeightKg; }

    public ConveyorStatus getStatus() { return status; }
    public void setStatus(ConveyorStatus status) {
        this.status = (status == null ? ConveyorStatus.Off : status);
    }

    public ConveyorLastStatus getLastStatus() { return lastStatus; }

    // ✅ NEW
    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }

    @Override
    public String toString() {
        return "Conveyor #" + id +
                " (Lot " + parkingLotId +
                ", Floor " + (floorNumber == null ? "-" : floorNumber) +
                ", Status " + status +
                ", Last " + (lastStatus == null ? "-" : lastStatus) +
                ", Active " + isActive +
                ")";
    }
}
