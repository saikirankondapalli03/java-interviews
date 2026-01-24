# FCAT Java Microservices (Kafka) — Practice Questions

**Format:** 15 questions, 70 minutes. Use these to simulate exam conditions.

---

## Questions (Cover First, Then Check Answers)

### Q1. What is the primary benefit of using partitions in a Kafka topic?

**A)** Reduces storage requirements  
**B)** Enables parallel processing by multiple consumers  
**C)** Encrypts messages  
**D)** Compresses message payloads  

<details>
<summary>Answer</summary>

**B) Enables parallel processing by multiple consumers**

Partitions allow different consumers in a consumer group to read from different partitions concurrently. More partitions → more parallelism. Ordering is guaranteed only within a partition.
</details>

---

### Q2. Which `acks` setting ensures that a producer receives acknowledgment only after the record is written to all in-sync replicas?

**A)** `acks=0`  
**B)** `acks=1`  
**C)** `acks=all` or `acks=-1`  
**D)** `acks=2`  

<details>
<summary>Answer</summary>

**C) `acks=all` or `acks=-1`**

- `acks=0`: no ack (fire-and-forget)  
- `acks=1`: leader only  
- `acks=all` / `acks=-1`: leader + all in-sync replicas. Best durability when replicas are available.
</details>

---

### Q3. In a consumer group, how many consumers can read from a single partition at a time?

**A)** Unlimited  
**B)** Two (primary + standby)  
**C)** Exactly one  
**D)** Depends on replication factor  

<details>
<summary>Answer</summary>

**C) Exactly one**

Each partition is consumed by at most one consumer in a consumer group. This preserves per-partition ordering and avoids duplicate processing within the group.
</details>

---

### Q4. Which Spring Kafka component is used to *send* messages to a Kafka topic?

**A)** `@KafkaListener`  
**B)** `KafkaConsumer`  
**C)** `KafkaTemplate`  
**D)** `KafkaStreams`  

<details>
<summary>Answer</summary>

**C) KafkaTemplate**

`KafkaTemplate` is the main producer-side API in Spring Kafka. `@KafkaListener` is for consuming. `KafkaConsumer` is the low-level client; `KafkaStreams` is for stream processing.
</details>

---

### Q5. What does `auto.offset.reset=earliest` do when a consumer has no committed offset for a partition?

**A)** Skip the partition  
**B)** Start from the earliest available offset (beginning of topic)  
**C)** Start from the latest offset only  
**D)** Throw an error  

<details>
<summary>Answer</summary>

**B) Start from the earliest available offset (beginning of topic)**

`earliest` = read from the beginning when there is no committed offset. `latest` = read only new messages. Useful for new consumer groups that must process historical data.
</details>

---

### Q6. Kafka guarantees ordering of messages ___________.

**A)** Across all partitions of a topic  
**B)** Within a single partition only  
**C)** Across the entire cluster  
**D)** Only when using transactions  

<details>
<summary>Answer</summary>

**B) Within a single partition only**

Ordering is per partition. To preserve order for a logical stream, use a consistent partition key so all related messages go to the same partition.
</details>

---

### Q7. Which Kafka API is used for stream processing with Kafka topics as both input and output?

**A)** Producer API  
**B)** Consumer API  
**C)** Streams API  
**D)** Connector API  

<details>
<summary>Answer</summary>

**C) Streams API**

Kafka Streams reads from and writes to Kafka topics, supporting aggregations, joins, windowing, etc. Connector API links Kafka to external systems (DB, etc.).
</details>

---

### Q8. What is a Dead Letter Queue (DLQ) used for in Kafka-based microservices?

**A)** High-priority messages  
**B)** Messages that failed processing, for later inspection or retry  
**C)** Encrypted messages  
**D)** Messages with the longest retention  

<details>
<summary>Answer</summary>

**B) Messages that failed processing, for later inspection or retry**

Failed records are sent to a DLQ topic so the main flow continues, and you can reprocess or debug later. Common pattern with Kafka consumers.
</details>

---

### Q9. Which annotation in Spring Kafka marks a method as a Kafka consumer?

**A)** `@Consumer`  
**B)** `@KafkaConsumer`  
**C)** `@KafkaListener`  
**D)** `@Subscribe`  

<details>
<summary>Answer</summary>

**C) @KafkaListener**

`@KafkaListener(topics = "my-topic", groupId = "my-group")` designates a method as a consumer for the given topics and group.
</details>

---

### Q10. What is the main advantage of event-driven microservices using Kafka over synchronous REST?

**A)** Lower latency for each request  
**B)** Loose coupling, async processing, and resilience via buffering  
**C)** Stronger consistency guarantees  
**D)** No need for API versioning  

<details>
<summary>Answer</summary>

**B) Loose coupling, async processing, and resilience via buffering**

Services communicate via events; they don’t block on each other. Kafka buffers events, so consumers can lag or restart without losing messages. Coupling is reduced compared to direct REST calls.
</details>

---

### Q11. What does "consumer lag" represent?

**A)** Network latency between broker and consumer  
**B)** The difference between the latest offset in a partition and the consumer’s committed offset  
**C)** How long a consumer takes to process one message  
**D)** The number of consumers in a group  

<details>
<summary>Answer</summary>

**B) The difference between the latest offset in a partition and the consumer’s committed offset**

Consumer lag = how far behind the consumer is. High lag can indicate slow processing or need to scale consumers (within partition limits).
</details>

---

### Q12. Log compaction in Kafka ___________.

**A)** Deletes all messages older than the retention period  
**B)** Keeps only the latest value for each key, useful for changelog-style topics  
**C)** Compresses message payloads  
**D)** Merges multiple topics into one  

<details>
<summary>Answer</summary>

**B) Keeps only the latest value for each key, useful for changelog-style topics**

Compaction retains the most recent record per key. Used for caching, materialized views, and restoring state from a topic.
</details>

---

### Q13. To achieve exactly-once semantics with Kafka producers, which configuration is typically used?

**A)** `acks=0` and no retries  
**B)** `enable.idempotence=true` with `acks=all`  
**C)** `batch.size=0`  
**D)** Multiple producer instances  

<details>
<summary>Answer</summary>

**B) `enable.idempotence=true` with `acks=all`**

Idempotent producer prevents duplicates from retries. `acks=all` ensures writes are durable. For exactly-once across produce and consume, transactions are used as well.
</details>

---

### Q14. How does a producer typically ensure that all messages with the same key go to the same partition?

**A)** By setting `partition` explicitly for each message  
**B)** Kafka automatically hashes the key and assigns partition: `hash(key) % num_partitions`  
**C)** Using a separate topic per key  
**D)** Keys are ignored for partitioning  

<details>
<summary>Answer</summary>

**B) Kafka automatically hashes the key and assigns partition: `hash(key) % num_partitions`**

If a key is provided, the default partitioner hashes it to choose a partition. Same key → same partition → ordering for that key. Null key uses round-robin or similar.
</details>

---

### Q15. What is the role of a consumer group in Kafka?

**A)** To replicate messages across brokers  
**B)** To group related topics together  
**C)** To allow multiple consumers to share consumption of a topic (each partition consumed by one consumer in the group)  
**D)** To encrypt consumer traffic  

<details>
<summary>Answer</summary>

**C) To allow multiple consumers to share consumption of a topic (each partition consumed by one consumer in the group)**

Consumer groups provide scalability and parallelism. Partitions are distributed among group members; rebalancing occurs when consumers join or leave.
</details>

---

### Q16. In Spring Kafka, which class represents a single message received by a consumer?

**A)** `ProducerRecord`  
**B)** `ConsumerRecord`  
**C)** `KafkaMessage`  
**D)** `Message`  

<details>
<summary>Answer</summary>

**B) ConsumerRecord**

`ConsumerRecord` holds topic, partition, offset, key, value, headers, timestamp, etc. `ProducerRecord` is used when producing.
</details>

---

### Q17. What does ISR (In-Sync Replicas) mean?

**A)** Consumers that have processed the same offset  
**B)** Replicas of a partition that are fully caught up with the leader  
**C)** Partitions with the same replication factor  
**D)** Brokers in the same datacenter  

<details>
<summary>Answer</summary>

**B) Replicas of a partition that are fully caught up with the leader**

ISR = replicas that match the leader. A record is committed only when written to all ISR replicas (with `acks=all`). Improves durability and availability.
</details>

---

### Q18. Which delivery semantic can result in duplicate message processing?

**A)** At-most-once  
**B)** At-least-once  
**C)** Exactly-once  
**D)** None  

<details>
<summary>Answer</summary>

**B) At-least-once**

At-least-once: no loss, but retries can cause duplicates. At-most-once: may lose messages, no duplicates. Exactly-once: no loss, no duplicates (with idempotence/transactions).
</details>

---

### Q19. Kafka Connector API is primarily used for ___________.

**A)** Connecting producers to consumers  
**B)** Connecting Kafka to external systems (databases, other message queues)  
**C)** Connecting multiple Kafka clusters  
**D)** Connecting Kafka to Zookeeper  

<details>
<summary>Answer</summary>

**B) Connecting Kafka to external systems (databases, other message queues)**

Source connectors ingest from external systems into Kafka; sink connectors write from Kafka to external systems. Part of the broader Kafka Connect framework.
</details>

---

### Q20. Why is an idempotent consumer important in event-driven microservices?

**A)** To reduce memory usage  
**B)** To safely handle at-least-once delivery (duplicates) without incorrect side effects  
**C)** To improve network throughput  
**D)** To enforce ordering across partitions  

<details>
<summary>Answer</summary>

**B) To safely handle at-least-once delivery (duplicates) without incorrect side effects**

With at-least-once, the same event can be processed more than once. Idempotent handling (e.g. upsert by ID, check-before-apply) ensures correct behavior on replays.
</details>

---

### Q21. What is the default behavior of `enable.auto.commit` in Kafka consumers?

**A)** Auto-commit is disabled by default  
**B)** Auto-commit is enabled by default, committing every 5 seconds  
**C)** Auto-commit is enabled by default, committing after each message  
**D)** Auto-commit behavior depends on the consumer group  

<details>
<summary>Answer</summary>

**B) Auto-commit is enabled by default, committing every 5 seconds**

`enable.auto.commit=true` is the default. Offsets are committed automatically every `auto.commit.interval.ms` (default: 5000ms). This can lead to duplicate processing if the consumer crashes between processing and commit.
</details>

---

### Q22. What is the main risk of using automatic offset commit (`enable.auto.commit=true`)?

**A)** Messages may be lost if the consumer crashes  
**B)** Messages may be processed multiple times if the consumer crashes between processing and commit  
**C)** Offsets are never committed  
**D)** Consumer lag will always be zero  

<details>
<summary>Answer</summary>

**B) Messages may be processed multiple times if the consumer crashes between processing and commit**

With auto-commit, offsets are committed periodically (e.g., every 5s). If a consumer processes a message but crashes before the next auto-commit, the offset won't be committed. On restart, it will reprocess that message → duplicate processing (at-least-once).
</details>

---

### Q23. Where are consumer offsets stored in Kafka?

**A)** In the same topic as the messages  
**B)** In a special internal topic called `__consumer_offsets`  
**C)** In Zookeeper (or KRaft metadata)  
**D)** In the consumer's local file system  

<details>
<summary>Answer</summary>

**B) In a special internal topic called `__consumer_offsets`**

Kafka stores consumer group offsets in the `__consumer_offsets` topic. This is a compacted topic that stores the latest committed offset per consumer group + topic + partition combination. Modern Kafka uses this instead of Zookeeper for offset storage.
</details>

---

### Q24. What happens when `auto.offset.reset=none` and a consumer group has no committed offset?

**A)** Consumer starts from the beginning of the partition  
**B)** Consumer starts from the latest offset  
**C)** Consumer throws an exception and fails  
**D)** Consumer skips the partition  

<details>
<summary>Answer</summary>

**C) Consumer throws an exception and fails**

`auto.offset.reset=none` means "fail fast" — if there's no committed offset, throw an exception. This is useful for production to catch configuration errors early. Use `earliest` or `latest` for automatic fallback behavior.
</details>

---

### Q25. What is the difference between `commitSync()` and `commitAsync()` for manual offset commits?

**A)** `commitSync()` is faster but may fail silently; `commitAsync()` blocks until complete  
**B)** `commitSync()` blocks until offset is committed; `commitAsync()` is non-blocking but may fail silently  
**C)** Both are identical  
**D)** `commitSync()` is for automatic commits; `commitAsync()` is for manual commits  

<details>
<summary>Answer</summary>

**B) `commitSync()` blocks until offset is committed; `commitAsync()` is non-blocking but may fail silently**

- **`commitSync()`**: Blocks until the broker confirms the commit. Guaranteed commit but slower.
- **`commitAsync()`**: Returns immediately; commit happens in background. Faster but may fail silently if broker is down.

Best practice: Use `commitAsync()` for performance, but call `commitSync()` on shutdown to ensure final commits.
</details>

---

### Q26. In Spring Kafka, which acknowledgment mode commits offsets after each individual record is processed?

**A)** `BATCH`  
**B)** `MANUAL`  
**C)** `RECORD`  
**D)** `AUTO`  

<details>
<summary>Answer</summary>

**C) RECORD**

Spring Kafka ack modes:
- **`RECORD`**: Commit after each record is processed
- **`BATCH`**: Commit after each batch of records
- **`MANUAL`**: Application must call `ack.acknowledge()` explicitly
- **`MANUAL_IMMEDIATE`**: Commit immediately when `ack.acknowledge()` is called

`RECORD` provides fine-grained control but may impact performance.
</details>

---

### Q27. When does `auto.offset.reset` take effect?

**A)** Every time a consumer starts  
**B)** Only when a consumer group has no committed offset or the committed offset is out of range  
**C)** Only when manually triggered by the application  
**D)** Only during consumer group rebalancing  

<details>
<summary>Answer</summary>

**B) Only when a consumer group has no committed offset or the committed offset is out of range**

`auto.offset.reset` is a fallback policy. It applies when:
1. Consumer group has **never committed an offset** for a partition
2. Committed offset is **out of range** (e.g., data deleted due to retention policy)

If a valid committed offset exists, the consumer resumes from that offset, ignoring `auto.offset.reset`.
</details>

---

### Q28. What is consumer lag, and how is it calculated?

**A)** Network latency between broker and consumer  
**B)** Latest offset in partition minus the consumer's committed offset  
**C)** Time difference between message production and consumption  
**D)** Number of uncommitted messages in memory  

<details>
<summary>Answer</summary>

**B) Latest offset in partition minus the consumer's committed offset**

Consumer lag = `latest_offset - committed_offset`. It measures how far behind a consumer is. High lag indicates slow processing or need to scale consumers. Lag of 0 means the consumer is caught up.
</details>

---

### Q29. Why might you want to use manual offset commit instead of automatic commit?

**A)** To improve performance  
**B)** To ensure offsets are only committed after messages are successfully processed (e.g., after DB write)  
**C)** To reduce network traffic  
**D)** To enable exactly-once semantics automatically  

<details>
<summary>Answer</summary>

**B) To ensure offsets are only committed after messages are successfully processed (e.g., after DB write)**

Manual commit allows you to control **when** offsets are committed. You can commit only after:
- Message is successfully processed
- Database write completes
- External API call succeeds

This prevents reprocessing on restart if processing fails after auto-commit would have occurred.
</details>

---

### Q30. What happens to offsets during a consumer group rebalancing?

**A)** All offsets are reset to zero  
**B)** Offsets are committed automatically before rebalancing to prevent reprocessing  
**C)** Offsets remain unchanged; only partition assignments change  
**D)** Offsets are deleted  

<details>
<summary>Answer</summary>

**B) Offsets are committed automatically before rebalancing to prevent reprocessing**

During rebalancing, Kafka commits offsets **before** partitions are reassigned. This ensures that when a partition moves to a new consumer, it resumes from the last committed offset, avoiding duplicate processing. This happens even with manual commit disabled.
</details>

---

## Quick Self-Check

| # | Topic            | Revise if missed |
|---|------------------|-------------------|
| 1 | Partitions       | Study Guide §1   |
| 2 | Producer acks    | Study Guide §3   |
| 3 | Consumer groups  | Study Guide §4   |
| 4 | Spring Kafka     | Study Guide §6   |
| 5 | Offset reset     | Study Guide §4.5  |
| 6 | Ordering         | Study Guide §1   |
| 7 | Kafka APIs       | Study Guide §2   |
| 8 | DLQ / errors     | Study Guide §7   |
| 9 | @KafkaListener   | Study Guide §6   |
|10 | Event-driven     | Study Guide §5   |
|11 | Consumer lag     | Study Guide §4.5  |
|12 | Log compaction   | Study Guide §9   |
|13 | Exactly-once     | Study Guide §8   |
|14 | Partitioning      | Study Guide §1   |
|15 | Consumer groups  | Study Guide §4   |
|21-30 | Offset management | Study Guide §4.5 |

Good luck on your assessment.
