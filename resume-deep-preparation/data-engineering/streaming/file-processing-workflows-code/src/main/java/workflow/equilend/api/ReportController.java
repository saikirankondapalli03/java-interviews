package workflow.equilend.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import workflow.equilend.reportingstore.ReportingStore;

import java.util.List;

/**
 * REST API for reporting: Daily Positions, Exposure by Borrower, Daily P&L.
 * In production: queries Snowflake via JDBC; here uses injected ReportingStore.
 */
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportingStore reportingStore;

    public ReportController(ReportingStore reportingStore) {
        this.reportingStore = reportingStore;
    }

    @GetMapping("/daily-positions")
    public ResponseEntity<List<ReportingStore.DailyPositionView>> dailyPositions(
            @RequestParam String fileDate) {
        List<ReportingStore.DailyPositionView> list = reportingStore.getDailyPositions(fileDate);
        return ResponseEntity.ok(list);
    }

    @GetMapping("/exposure-by-borrower")
    public ResponseEntity<List<ReportingStore.ExposureByBorrower>> exposureByBorrower(
            @RequestParam String fileDate) {
        List<ReportingStore.ExposureByBorrower> list = reportingStore.getExposureByBorrower(fileDate);
        return ResponseEntity.ok(list);
    }

    @GetMapping("/daily-pnl")
    public ResponseEntity<List<ReportingStore.DailyPnlView>> dailyPnl(
            @RequestParam String fileDate) {
        List<ReportingStore.DailyPnlView> list = reportingStore.getDailyPnl(fileDate);
        return ResponseEntity.ok(list);
    }
}
