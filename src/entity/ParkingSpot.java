package entity;

/**
 * ParkingSpot – תא חניה פיזי.
 */
public class ParkingSpot {

    private final int id;
    private int floorNumber;
    private int x;
    private int y;
    private String size;
    private int parkingLotID;

    public ParkingSpot(int id, int floorNumber, int x, int y,
                       String size, int parkingLotID) {

        this.id = id;
        this.floorNumber = floorNumber;
        this.x = x;
        this.y = y;
        this.size = size;
        this.parkingLotID = parkingLotID;
    }

    public int getId() { return id; }
    public int getFloorNumber() { return floorNumber; }
    public int getX() { return x; }
    public int getY() { return y; }
    public String getSize() { return size; }
    public int getParkingLotID() { return parkingLotID; }
}