package control;

import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.view.JasperViewer;

import javax.swing.*;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.HashMap;
import java.util.Map;

public class ReportController {

    // נתיבים לפי הפרויקט שלך
    private static final String DB_PATH = "db/parkwise_OfriMagi.accdb";
    private static final String REPORT_PATH = "reports/Annual_Parking_Summary.jrxml";

    public ReportController() {
        // אין תלות בקונטרולרים אחרים
    }

    public void showAnnualSummaryReport(int year) {

        try {
            // בדיקה שהקבצים קיימים
            File dbFile = new File(DB_PATH);
            if (!dbFile.exists()) {
                throw new IllegalStateException("DB not found: " + dbFile.getAbsolutePath());
            }

            File reportFile = new File(REPORT_PATH);
            if (!reportFile.exists()) {
                throw new IllegalStateException("Report not found: " + reportFile.getAbsolutePath());
            }

            // חיבור ל-Access
            String dbUrl = "jdbc:ucanaccess://" + dbFile.getAbsolutePath();
            try (Connection conn = DriverManager.getConnection(dbUrl)) {

                // קומפילציה של הדוח
                JasperReport report =
                        JasperCompileManager.compileReport(reportFile.getAbsolutePath());

                // פרמטרים
                Map<String, Object> params = new HashMap<>();
                params.put("EnterYear", year);

                // מילוי הדוח מה-DB (SQL מתוך ה-jrxml!)
                JasperPrint print =
                        JasperFillManager.fillReport(report, params, conn);

                // הצגה
                JasperViewer.viewReport(print, false);
            }

        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(
                    null,
                    "Failed to generate report:\n" + e.getMessage(),
                    "Report Error",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }
}
