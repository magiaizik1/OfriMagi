package entity;

import java.util.Objects;

/**
 * Vehicle entity - matches the Access table "Vehicle"
 *
 * Columns:
 * - ID (PK)
 * - color
 * - type
 * - weight
 * - customerID (FK -> Customer.ID)
 * - size
 */
public class Vehicle {

    private int id;              // Vehicle ID (plate / unique number)
    private String color;        // e.g., White, Black, Blue, Other
    private String type;         // e.g., Private, SUV, Truck, Motorcycle
    private int weight;          // in kg
    private int customerId;      // FK -> Customer.ID
    private String size;         // e.g., Small, Medium, Large

    public Vehicle() {
    }

    public Vehicle(int id, String color, String type, int weight, int customerId, String size) {
        this.id = id;
        this.color = color;
        this.type = type;
        this.weight = weight;
        this.customerId = customerId;
        this.size = size;
    }

    // ---------- Getters / Setters ----------

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public int getWeight() {
        return weight;
    }

    public void setWeight(int weight) {
        this.weight = weight;
    }

    public int getCustomerId() {
        return customerId;
    }

    public void setCustomerId(int customerId) {
        this.customerId = customerId;
    }

    public String getSize() {
        return size;
    }

    public void setSize(String size) {
        this.size = size;
    }

    // ---------- Utility ----------

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Vehicle)) return false;
        Vehicle vehicle = (Vehicle) o;
        return id == vehicle.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Vehicle{" +
                "id=" + id +
                ", color='" + color + '\'' +
                ", type='" + type + '\'' +
                ", weight=" + weight +
                ", customerId=" + customerId +
                ", size='" + size + '\'' +
                '}';
    }
}
