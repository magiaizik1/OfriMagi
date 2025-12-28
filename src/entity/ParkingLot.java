package entity;

/**
 * ParkingLot entity
 *
 * שינוי דרישה:
 * - כתובת התפצלה לשני שדות: street + number
 *
 * חשוב:
 * - כדי לא לשבור לוגיקה קיימת בפרויקט (UI/טבלאות), נשמרו גם getAddress()/setAddress()
 *   כממשק נוח להצגה (כתובת מאוחדת), אבל הייצוג האמיתי הוא street+number.
 *
 * שינוי נוסף:
 * - Soft delete: added isActive (true/false)
 */
public class ParkingLot {

    private final int id; // ID ייחודי – לא ניתן לשינוי
    private String name;

    // ✅ NEW (split address)
    private String street;
    private Integer number; // nullable
    private City city;
    private int availableSpaces;

    // ✅ NEW: soft delete flag
    private boolean isActive = true;

    // Existing constructor kept, defaults isActive=true
    public ParkingLot(
            int id,
            String name,
            String street,
            Integer number,
            City city,
            int availableSpaces
    ) {
        this(id, name, street, number, city, availableSpaces, true);
    }

    // ✅ New constructor (DB reads)
    public ParkingLot(
            int id,
            String name,
            String street,
            Integer number,
            City city,
            int availableSpaces,
            boolean isActive
    ) {
        this.id = id;
        this.name = name;
        this.street = street;
        this.number = number;
        this.city = city;
        this.availableSpaces = availableSpaces;
        this.isActive = isActive;
    }

    public int getId() { return id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    // =========================
    // NEW: street + number
    // =========================
    public String getStreet() { return street; }
    public void setStreet(String street) { this.street = street; }

    public Integer getNumber() { return number; }
    public void setNumber(Integer number) { this.number = number; }

    // =========================
    // Backward-compatible helpers (UI display)
    // =========================
    public String getAddress() {
        String s = (street == null ? "" : street.trim());
        String n = (number == null ? "" : String.valueOf(number));
        return (s + " " + n).trim();
    }

    public void setAddress(String address) {
        String a = address == null ? "" : address.trim();
        if (a.isEmpty()) {
            this.street = "";
            this.number = null;
            return;
        }

        String[] parts = a.split("\\s+");
        if (parts.length >= 2) {
            String last = parts[parts.length - 1];
            try {
                int num = Integer.parseInt(last);
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < parts.length - 1; i++) {
                    if (i > 0) sb.append(" ");
                    sb.append(parts[i]);
                }
                this.street = sb.toString().trim();
                this.number = num;
                return;
            } catch (NumberFormatException ignore) {
                // fall through
            }
        }
        this.street = a;
        this.number = null;
    }

    public City getCity() { return city; }
    public void setCity(City city) { this.city = city; }

    public int getAvailableSpaces() { return availableSpaces; }
    public void setAvailableSpaces(int availableSpaces) { this.availableSpaces = availableSpaces; }

    // ✅ NEW
    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }

    @Override
    public String toString() {
        return name + " (" + city + ")";
    }
}
