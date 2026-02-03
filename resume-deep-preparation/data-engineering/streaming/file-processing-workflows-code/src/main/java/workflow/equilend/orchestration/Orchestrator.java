package workflow.equilend.orchestration;

import workflow.equilend.audit.AuditRecord;
import workflow.equilend.audit.AuditStatus;
import workflow.equilend.audit.AuditStore;
import workflow.equilend.batch.FileProcessor;
import workflow.equilend.batch.ProcessingResult;
import workflow.equilend.eventing.FileArrivalEvent;
import workflow.equilend.eventing.FileType;

import java.util.Optional;

/**
 * Orchestrator: receives file-arrival event, checks idempotency, reserves run, invokes batch processor.
 * In production: Lambda or Step Functions; Glue job invoked with --input-path, --file-type, --file-date.
 */
public final class Orchestrator {

    private final AuditStore auditStore;
    private final FileProcessor fileProcessor;
    private final String sourceSystem;

    public Orchestrator(AuditStore auditStore, FileProcessor fileProcessor, String sourceSystem) {
        this.auditStore = auditStore;
        this.fileProcessor = fileProcessor;
        this.sourceSystem = sourceSystem != null ? sourceSystem : "EQUILEND";
    }

    /**
     * Process one file-arrival event: idempotency check, reserve, run processor, complete audit.
     * @return true if processing was started and completed (or skipped because already SUCCESS).
     */
    public OrchestratorResult handle(FileArrivalEvent event, String basePath) {
        FileType fileType = event.getFileType();
        String fileDate = event.getFileDateFromKey();
        String fileName = event.getKey().contains("/") ? event.getKey().substring(event.getKey().lastIndexOf('/') + 1) : event.getKey();
        String idempotencyKey = AuditRecord.buildIdempotencyKey(sourceSystem, fileType.name().toLowerCase(), fileDate, fileName);

        Optional<AuditRecord> existing = auditStore.getByKey(idempotencyKey);
        if (existing.isPresent() && existing.get().getStatus() == AuditStatus.SUCCESS) {
            return OrchestratorResult.skippedAlreadySuccess(idempotencyKey);
        }

        boolean reserved = auditStore.reserveRun(idempotencyKey, fileType.name().toLowerCase(), fileDate, event.getBucket() + "/" + event.getKey());
        if (!reserved) {
            return OrchestratorResult.skippedReservationFailed(idempotencyKey);
        }

        String inputPath = event.getInputPath(basePath);
        ProcessingResult result;
        try {
            result = fileProcessor.process(inputPath, fileType, fileDate);
        } catch (Exception e) {
            auditStore.completeRun(idempotencyKey, AuditStatus.FAILED, 0, 0, "local-run");
            return OrchestratorResult.failed(idempotencyKey, e);
        }

        AuditStatus finalStatus = result.getRejectCount() > 0 && result.getRecordCount() > 0
                ? AuditStatus.PARTIAL
                : result.getRecordCount() > 0 ? AuditStatus.SUCCESS : AuditStatus.FAILED;
        auditStore.completeRun(idempotencyKey, finalStatus, result.getRecordCount(), result.getRejectCount(), "local-run");

        return OrchestratorResult.success(idempotencyKey, result);
    }

    public static final class OrchestratorResult {
        private final boolean processed;
        private final String reason;
        private final String idempotencyKey;
        private final ProcessingResult processingResult;
        private final Exception failure;

        private OrchestratorResult(boolean processed, String reason, String idempotencyKey,
                                   ProcessingResult processingResult, Exception failure) {
            this.processed = processed;
            this.reason = reason;
            this.idempotencyKey = idempotencyKey;
            this.processingResult = processingResult;
            this.failure = failure;
        }

        static OrchestratorResult skippedAlreadySuccess(String key) {
            return new OrchestratorResult(false, "SKIPPED_ALREADY_SUCCESS", key, null, null);
        }

        static OrchestratorResult skippedReservationFailed(String key) {
            return new OrchestratorResult(false, "SKIPPED_RESERVATION_FAILED", key, null, null);
        }

        static OrchestratorResult success(String key, ProcessingResult result) {
            return new OrchestratorResult(true, "SUCCESS", key, result, null);
        }

        static OrchestratorResult failed(String key, Exception e) {
            return new OrchestratorResult(false, "FAILED", key, null, e);
        }

        public boolean isProcessed() { return processed; }
        public String getReason() { return reason; }
        public String getIdempotencyKey() { return idempotencyKey; }
        public ProcessingResult getProcessingResult() { return processingResult; }
        public Exception getFailure() { return failure; }
    }
}
