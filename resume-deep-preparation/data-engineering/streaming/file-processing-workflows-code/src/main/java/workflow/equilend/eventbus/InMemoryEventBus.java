package workflow.equilend.eventbus;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory event bus for local/demo. Publishes are synchronous to subscribed consumers.
 * In production: Kafka (MSK) with topics equilend.positions.curated, equilend.mtm_pnl.curated.
 */
public final class InMemoryEventBus implements EventBus {

    private final List<CuratedRecordConsumer> consumers = new CopyOnWriteArrayList<>();

    @Override
    public void publish(CuratedRecordEvent event) {
        for (CuratedRecordConsumer c : consumers) {
            try {
                c.onEvent(event);
            } catch (Exception e) {
                // log and continue
            }
        }
    }

    @Override
    public void subscribe(CuratedRecordConsumer consumer) {
        if (consumer != null) consumers.add(consumer);
    }
}
