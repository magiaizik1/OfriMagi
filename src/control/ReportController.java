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

    // התאמה מלאה למבנה הפרויקט שלך
    private static final String DB_PATH = "db/parkwise_OfriMagi.accdb";
    private static final String REPORT_PATH = "reports/Annual_Parking_Summary.jrxml";

    public void showAnnualSummaryReport(int year) {

        try {
            // 1️⃣ בדיקות קיום
            File dbFile = new File(DB_PATH);
            if (!dbFile.exists()) {
                throw new IllegalStateException("DB not found: " + dbFile.getAbsolutePath());
            }

            File reportFile = new File(REPORT_PATH);
            if (!reportFile.exists()) {
                throw new IllegalStateException("Report not found: " + reportFile.getAbsolutePath());
            }

            // 2️⃣ חיבור ל־Access
            String dbUrl = "jdbc:ucanaccess://" + dbFile.getAbsolutePath();
            try (Connection conn = DriverManager.getConnection(dbUrl)) {

                // 3️⃣ קומפילציה
                JasperReport report =
                        JasperCompileManager.compileReport(reportFile.getAbsolutePath());

                // 4️⃣ פרמטרים
                Map<String, Object> params = new HashMap<>();
                params.put("EnterYear", year);

                // 5️⃣ מילוי והצגה
                JasperPrint print =
                        JasperFillManager.fillReport(report, params, conn);

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
