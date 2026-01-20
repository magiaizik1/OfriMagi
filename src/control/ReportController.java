package control;

import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.view.JasperViewer;

import javax.swing.*;
import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.util.HashMap;
import java.util.Map;

public class ReportController {

    private final AccessDb db;

    // works in Eclipse (reports/...) וגם נתמך “ליד ה-JAR”
    private static final String REPORT_PATH = "reports/Annual_Parking_Summary.jrxml";

    public ReportController(AccessDb db) {
        this.db = db;
    }

    public void showAnnualSummaryReport(int year) {
        try {
            File reportFile = resolveFile(REPORT_PATH);
            if (reportFile == null || !reportFile.exists()) {
                throw new IllegalStateException(
                        "Report not found. Expected: " + REPORT_PATH + " (or next to the runnable JAR)"
                );
            }

            try (Connection conn = db.open()) {
                JasperReport report = JasperCompileManager.compileReport(reportFile.getAbsolutePath());

                Map<String, Object> params = new HashMap<>();
                params.put("EnterYear", year);

                JasperPrint print = JasperFillManager.fillReport(report, params, conn);

                JasperViewer.viewReport(print, false);

                File exportDir = ensureExportReportsDir();
                String pdfOut = new File(exportDir, "Annual_Parking_Summary_" + year + ".pdf").getAbsolutePath();
                String xmlOut = new File(exportDir, "Annual_Parking_Summary_" + year + ".xml").getAbsolutePath();

                JasperExportManager.exportReportToPdfFile(print, pdfOut);
                JasperExportManager.exportReportToXmlFile(print, xmlOut, true);

                JOptionPane.showMessageDialog(null,
                        "Report exported successfully to PDF and XML!",
                        "Export Complete",
                        JOptionPane.INFORMATION_MESSAGE);
            }

        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null,
                    "Failed to generate report:\n" + e.getMessage(),
                    "Report Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private File resolveFile(String relativePath) {
        Path p = Paths.get(relativePath);
        if (p.isAbsolute()) return p.toFile();

        // 1) working directory
        Path wd = Paths.get(System.getProperty("user.dir")).resolve(relativePath);
        if (Files.exists(wd)) return wd.toFile();

        // 2) jar directory
        Path jarDir = getJarDir();
        if (jarDir != null) {
            // a) jarDir + reports/...
            Path jarTry = jarDir.resolve(relativePath);
            if (Files.exists(jarTry)) return jarTry.toFile();

            // b) same folder as jar (by filename only)
            Path byName = jarDir.resolve(p.getFileName().toString());
            if (Files.exists(byName)) return byName.toFile();

            // c) jarDir/reports/<filename>
            Path inReports = jarDir.resolve("reports").resolve(p.getFileName().toString());
            if (Files.exists(inReports)) return inReports.toFile();
        }

        return p.toFile();
    }

    private File ensureExportReportsDir() {
        Path jarDir = getJarDir();
        if (jarDir != null) {
            Path reportsDir = jarDir.resolve("reports");
            try {
                Files.createDirectories(reportsDir);
                return reportsDir.toFile();
            } catch (Exception ignored) {}
        }

        Path wdReports = Paths.get(System.getProperty("user.dir")).resolve("reports");
        try { Files.createDirectories(wdReports); } catch (Exception ignored) {}
        return wdReports.toFile();
    }

    private Path getJarDir() {
        try {
            URI uri = ReportController.class.getProtectionDomain().getCodeSource().getLocation().toURI();
            Path location = Paths.get(uri);
            if (Files.isRegularFile(location)) return location.getParent();
            return location;
        } catch (Exception e) {
            return null;
        }
    }
}