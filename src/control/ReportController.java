package control;

import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.view.JasperViewer;

import javax.swing.*;
import java.io.File;
import java.sql.Connection;
import java.util.HashMap;
import java.util.Map;

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

                // מילוי הדוח מה-DB
                JasperPrint print =
                        JasperFillManager.fillReport(report, params, conn);

                // הצגה על המסך
                JasperViewer.viewReport(print, false);

                // =========================
                // ייצוא לקבצים
                // =========================

                // PDF
                JasperExportManager.exportReportToPdfFile(
                        print, "reports/Annual_Parking_Summary_" + year + ".pdf");

                // XML
                JasperExportManager.exportReportToXmlFile(
                        print, "reports/Annual_Parking_Summary_" + year + ".xml", true);

                JOptionPane.showMessageDialog(
                        null,
                        "Report exported successfully to PDF and XML!",
                        "Export Complete",
                        JOptionPane.INFORMATION_MESSAGE
                );
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
