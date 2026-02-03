package workflow.equilend.eventbus;

/**
 * Event bus for curated records (Kafka/MSK in production).
 * Batch processor publishes here; streaming consumer subscribes.
 */
public interface EventBus {

    void publish(CuratedRecordEvent event);

    /** Subscribe to curated events (blocking or async per implementation). */
    void subscribe(CuratedRecordConsumer consumer);

    interface CuratedRecordConsumer {
        void onEvent(CuratedRecordEvent event);
    }
}
