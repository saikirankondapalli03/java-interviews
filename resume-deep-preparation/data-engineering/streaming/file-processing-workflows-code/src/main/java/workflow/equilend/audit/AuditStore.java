package workflow.equilend.audit;

import java.util.Optional;

/**
 * Audit and idempotency store (DynamoDB or RDS in production).
 * - Reserve run: put PENDING only if not exists.
 * - Update on completion: set SUCCESS/PARTIAL/FAILED only if status was PENDING.
 */
public interface AuditStore {

    /**
     * Try to reserve a run for this file (conditional put: only if no existing record).
     * @return true if this caller won the reservation (record created as PENDING).
     */
    boolean reserveRun(String idempotencyKey, String fileType, String fileDate, String s3Uri);

    /**
     * Get existing record by idempotency key (use strongly consistent read in production).
     */
    Optional<AuditRecord> getByKey(String idempotencyKey);

    /**
     * Update record on job completion. Should only succeed if current status is PENDING.
     */
    boolean completeRun(String idempotencyKey, AuditStatus status, long recordCount, long rejectCount, String jobId);
}
