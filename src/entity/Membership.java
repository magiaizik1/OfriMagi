package entity;

import java.time.LocalDate;

public class Membership {
    private final int customerId;
    private final LocalDate joinDate;

    public Membership(int customerId, LocalDate joinDate) {
        this.customerId = customerId;
        this.joinDate = joinDate;
    }

    public int getCustomerId() { return customerId; }
    public LocalDate getJoinDate() { return joinDate; }

    @Override
    public String toString() {
        return "Membership{customerId=" + customerId + ", joinDate=" + joinDate + "}";
    }
}