package control;

import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.view.JasperViewer;

import javax.swing.*;
import java.io.File;
import java.sql.Connection;
import java.util.HashMap;
import java.util.Map;

/**
 * ReportController
 *
 * ✔ Access to DB is done ONLY via AccessDb
 * ✔ No direct DriverManager usage
 * ✔ No hardcoded DB path inside controller
 *
 * This keeps correct architectural separation:
 * Control → AccessDb → Database
 */
public class ReportController {

    private final AccessDb db;

    // נתיב הדוח בלבד (לא DB!)
    private static final String REPORT_PATH = "reports/Annual_Parking_Summary.jrxml";

    public ReportController(AccessDb db) {
        this.db = db;
    }

    public void showAnnualSummaryReport(int year) {

        try {
            // בדיקה שקובץ הדוח קיים
            File reportFile = new File(REPORT_PATH);
            if (!reportFile.exists()) {
                throw new IllegalStateException("Report not found: " + reportFile.getAbsolutePath());
            }

            // חיבור ל-DB דרך AccessDb בלבד
            try (Connection conn = db.open()) {

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
