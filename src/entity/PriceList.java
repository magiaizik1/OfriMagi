package entity;

public class PriceList {

    private  int id;          // ID ייחודי – לא ניתן לשינוי
    private int year;
    private double firstHourPrice;
    private double additionalHourPrice;
    private double fullDayPrice;

    public PriceList() {
    }
    public PriceList(
            int id,
            int year,
            double firstHourPrice,
            double additionalHourPrice,
            double fullDayPrice
    ) {
        this.id = id;
        this.year = year;
        this.firstHourPrice = firstHourPrice;
        this.additionalHourPrice = additionalHourPrice;
        this.fullDayPrice = fullDayPrice;
    }
    
    
    public int getId() {
        return id;
    }

    public int getYear() {
        return year;
    }

    public void setYear(int year) {
        this.year = year;
    }

    public double getFirstHourPrice() {
        return firstHourPrice;
    }

    public void setFirstHourPrice(double firstHourPrice) {
        this.firstHourPrice = firstHourPrice;
    }

    public double getAdditionalHourPrice() {
        return additionalHourPrice;
    }

    public void setAdditionalHourPrice(double additionalHourPrice) {
        this.additionalHourPrice = additionalHourPrice;
    }

    public double getFullDayPrice() {
        return fullDayPrice;
    }

    public void setFullDayPrice(double fullDayPrice) {
        this.fullDayPrice = fullDayPrice;
    }
    
    @Override
    public String toString() {
        return "Price List [ID=" + id +
                ", Year=" + year +
                ", First Hour=" + firstHourPrice +
                ", Additional Hour=" + additionalHourPrice +
                ", Full Day=" + fullDayPrice +
                "]";
    }
  

}