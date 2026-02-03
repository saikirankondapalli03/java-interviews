package workflow.equilend;

import workflow.equilend.audit.AuditStore;
import workflow.equilend.audit.InMemoryAuditStore;
import workflow.equilend.batch.FileProcessor;
import workflow.equilend.eventing.FileArrivalEvent;
import workflow.equilend.eventbus.EventBus;
import workflow.equilend.eventbus.InMemoryEventBus;
import workflow.equilend.orchestration.Orchestrator;
import workflow.equilend.reportingstore.FileBackedReportingStore;
import workflow.equilend.reportingstore.ReportingStore;
import workflow.equilend.streaming.StreamingConsumer;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;

/**
 * Runs the full pipeline locally: simulate file arrival → orchestration → batch → event bus → streaming → reporting store.
 * After running, start WorkflowApplication and call GET /api/reports/daily-positions?fileDate=20250201 etc.
 */
public final class PipelineRunner {

    public static void main(String[] args) throws Exception {
        // Run from project root (file-processing-workflows-code) so test-data and output are found
        Path basePath = Paths.get(System.getProperty("user.dir", "")).toAbsolutePath();
        String testDataRoot = basePath.resolve("test-data").toString();
        String outputRoot = basePath.resolve("output").toString();
        String curatedPath = outputRoot + "/curated";
        String quarantinePath = outputRoot + "/quarantine";

        EventBus eventBus = new InMemoryEventBus();
        AuditStore auditStore = new InMemoryAuditStore();
        String reportingPath = outputRoot + "/reporting";
        ReportingStore reportingStore = new FileBackedReportingStore(reportingPath);
        StreamingConsumer streamingConsumer = new StreamingConsumer(reportingStore);
        eventBus.subscribe(streamingConsumer);

        FileProcessor fileProcessor = new FileProcessor(curatedPath, quarantinePath, eventBus);
        Orchestrator orchestrator = new Orchestrator(auditStore, fileProcessor, "EQUILEND");

        FileArrivalEvent positionsEvent = new FileArrivalEvent(
                testDataRoot, "positions/equilend_positions_20250201.csv", 0, Instant.now(), null);
        FileArrivalEvent mtmPnlEvent = new FileArrivalEvent(
                testDataRoot, "mtm_pnl/equilend_mtm_pnl_20250201.csv", 0, Instant.now(), null);

        System.out.println("Processing positions file...");
        Orchestrator.OrchestratorResult r1 = orchestrator.handle(positionsEvent, testDataRoot);
        System.out.println("Positions: " + r1.getReason() + (r1.getProcessingResult() != null
                ? " records=" + r1.getProcessingResult().getRecordCount() + " rejects=" + r1.getProcessingResult().getRejectCount() : ""));

        System.out.println("Processing MTM P&L file...");
        Orchestrator.OrchestratorResult r2 = orchestrator.handle(mtmPnlEvent, testDataRoot);
        System.out.println("MTM P&L: " + r2.getReason() + (r2.getProcessingResult() != null
                ? " records=" + r2.getProcessingResult().getRecordCount() + " rejects=" + r2.getProcessingResult().getRejectCount() : ""));

        streamingConsumer.flushAll();
        System.out.println("Flushed streaming consumer. Curated output: " + curatedPath);
        System.out.println("Quarantine output: " + quarantinePath);
        System.out.println("Reporting JSON: " + reportingPath);
        System.out.println("To query reports, run WorkflowApplication (with reporting.store.path=" + reportingPath + ") and call:");
        System.out.println("  GET /api/reports/daily-positions?fileDate=20250201");
        System.out.println("  GET /api/reports/exposure-by-borrower?fileDate=20250201");
        System.out.println("  GET /api/reports/daily-pnl?fileDate=20250201");
    }
}
