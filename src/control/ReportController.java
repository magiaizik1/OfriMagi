package control;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.HashMap;
import java.util.Map;

// אימפורטים לגרסה 6 (ללא compile או getInstance)
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.view.JasperViewer;

public class ReportController {

    public void generateParkingReport() {
        // נתיבים (לפי התמונות ששלחת)
        String dbUrl = "jdbc:ucanaccess://db/ParkWiseDB.accdb";
        String reportPath = "src/reports/Blank_A4.jrxml";

        try {
            System.out.println("...מתחיל בתהליך הפקת הדוח");

            // 1. חיבור למסד הנתונים
            Connection conn = DriverManager.getConnection(dbUrl);
            System.out.println("1. החיבור ל-Database הצליח.");

            // 2. קימפול הדוח (בגרסה 6 הפעולה היא סטטית)
            JasperReport jasperReport = JasperCompileManager.compileReport(reportPath);
            System.out.println("2. קובץ ה-JRXML קומפל בהצלחה.");

            // 3. פרמטרים (כרגע ריק)
            Map<String, Object> parameters = new HashMap<>();

            // 4. מילוי הדוח בנתונים
            JasperPrint jasperPrint = JasperFillManager.fillReport(jasperReport, parameters, conn);
            System.out.println("3. הדוח מולא בנתונים.");

            // 5. הצגת הדוח
            // false = סגירת הדוח לא תסגור את התוכנה כולה
            JasperViewer.viewReport(jasperPrint, false);

            conn.close();

        } catch (Exception e) {
            System.err.println("שגיאה בהפקת הדוח: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        new ReportController().generateParkingReport();
    }
}