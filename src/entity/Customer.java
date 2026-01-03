package entity;

import java.time.LocalDateTime;

/**
 * Customer entity (Client).
 * Represents a person who owns/parks vehicles.
 *
 * Used in:
 * - vehicle/customer association (each vehicle belongs to exactly one customer)
 * - end parking validation by phone number
 * - receipts (client details)
 * - club membership (linked by customerId FK)
 */
public class Customer {

    private int id;                 // PK (Customer.ID / CustomerID)
    private String firstName;
    private String lastName;
    private String phoneNumber;     // mobile phone for validation + SMS

    // Optional fields (keep if you have them, otherwise ignore in DB mapping)
    private LocalDateTime createdAt; // if you store registration time (optional)

    public Customer() {}

    public Customer(int id, String firstName, String lastName, String phoneNumber) {
        this.id = id;
        this.firstName = firstName;
        this.lastName = lastName;
        this.phoneNumber = phoneNumber;
    }

    // ===== Getters / Setters =====

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    // ===== Helpers =====

    public String getFullName() {
        String fn = (firstName == null) ? "" : firstName.trim();
        String ln = (lastName == null) ? "" : lastName.trim();
        return (fn + " " + ln).trim();
    }

    @Override
    public String toString() {
        // Useful for ComboBox/UI/debug
        String name = getFullName();
        if (name.isEmpty()) name = "Customer #" + id;
        String phone = (phoneNumber == null) ? "" : phoneNumber.trim();
        return phone.isEmpty() ? name : (name + " (" + phone + ")");
    }
}