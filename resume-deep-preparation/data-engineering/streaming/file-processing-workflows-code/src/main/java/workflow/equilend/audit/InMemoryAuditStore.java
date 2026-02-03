package workflow.equilend.audit;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of AuditStore for local/demo use.
 * Production would use DynamoDB (conditional PutItem/UpdateItem) or RDS.
 */
public final class InMemoryAuditStore implements AuditStore {

    private final Map<String, AuditRecord> store = new ConcurrentHashMap<>();

    @Override
    public boolean reserveRun(String idempotencyKey, String fileType, String fileDate, String s3Uri) {
        AuditRecord existing = store.get(idempotencyKey);
        if (existing != null) return false;
        AuditRecord pending = new AuditRecord(
                idempotencyKey, fileType, fileDate, s3Uri,
                AuditStatus.PENDING, 0L, 0L, null, null);
        return store.putIfAbsent(idempotencyKey, pending) == null;
    }

    @Override
    public Optional<AuditRecord> getByKey(String idempotencyKey) {
        return Optional.ofNullable(store.get(idempotencyKey));
    }

    @Override
    public boolean completeRun(String idempotencyKey, AuditStatus status, long recordCount, long rejectCount, String jobId) {
        AuditRecord current = store.get(idempotencyKey);
        if (current == null || current.getStatus() != AuditStatus.PENDING) return false;
        AuditRecord updated = new AuditRecord(
                current.getIdempotencyKey(), current.getFileType(), current.getFileDate(), current.getS3Uri(),
                status, recordCount, rejectCount, Instant.now(), jobId);
        return store.replace(idempotencyKey, current, updated);
    }
}
