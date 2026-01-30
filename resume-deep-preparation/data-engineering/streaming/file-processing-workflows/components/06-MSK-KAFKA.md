# Component: Amazon MSK (Kafka) — Event Bus for Curated Data

**Role in flow:** Spark batch job writes validated rows to Kafka topics (e.g. `equilend.positions.curated`, `equilend.mtm_pnl.curated`). Spark Streaming (or Glue Streaming) consumes from these topics, aggregates, and sinks to Redshift (or S3). Kafka = single place for "reporting-ready" data; multiple consumers possible.

---

## 1. Interview Pain Points & Nuances

### Why Kafka in a File Workflow?

- **Unify file-originated data with streaming:** Same pipeline can later add real-time sources; one consumer (Spark Streaming) reads from Kafka and loads Redshift. No separate "file path" vs "stream path" in the consumer.
- **Multiple consumers:** Spark Streaming loads Redshift; we could add another consumer (e.g. real-time dashboard, risk engine) without changing the producer.
- **Decouple producer (Glue) from consumer (Spark Streaming):** If Redshift load is slow, Kafka buffers. Producer doesn't block.
- **Interview:** "We use Kafka (MSK) so that file-originated data and future event streams share the same bus. Spark Streaming consumes from Kafka and loads Redshift; we can add more consumers without changing the file processing job."

### Topics and Partitioning

- **Topics:** e.g. `equilend.positions.curated`, `equilend.mtm_pnl.curated`. One topic per file type (or one topic with message attributes for type).
- **Partitioning:** Partition by a key so that all rows for the same `file_date` (or `borrower_id`, etc.) go to the same partition. That gives ordering per key and allows consumer to process in order. **Key choice:** `file_date` so that one partition = one day's data; consumer can commit offset after loading that day to Redshift. Or partition by `borrower_id` for downstream aggregation by borrower.
- **Partition count:** More partitions = more parallelism for consumers. Don't over-partition (e.g. 6–12 per topic is common to start). **Interview:** "We partition by file_date so that each partition holds one day's data; the consumer can process partition by partition and commit offsets. We use enough partitions to allow parallel consumer tasks."

### Producer Semantics (Glue → Kafka)

- **At-least-once:** Producer sends; if ack fails, retry. Possible duplicates. Consumer must be idempotent (overwrite by file_date in Redshift).
- **Exactly-once (idempotent producer):** Producer uses idempotence so retries don't create duplicate records in Kafka. Consumer still needs to write idempotently to Redshift (same key overwrite).
- **Interview:** "We use at-least-once producer; our Spark Streaming consumer overwrites by file_date in Redshift so duplicate consumption doesn't corrupt data. We could enable idempotent producer for exactly-once into Kafka if we wanted to avoid duplicate messages in the topic."

### Consumer (Spark Streaming)

- **Consumer group:** One consumer group per "application" (e.g. equilend-reporting). Each partition is consumed by one consumer in the group. Scale by adding more partitions or more consumer instances (up to #partitions).
- **Offset management:** Spark Streaming (Structured Streaming) commits offsets to Kafka (or checkpoint). If the job fails, next run resumes from last committed offset. **Interview:** "We use a consumer group so that Spark Streaming can scale; we commit offsets after successfully writing to Redshift so we don't reprocess on restart."
- **Processing semantics:** At-least-once (process then commit) or exactly-once (transactional write to Redshift + commit offset in same transaction if supported). Redshift doesn't support two-phase commit with Kafka, so we typically do at-least-once + idempotent write (overwrite by file_date).

### Lag (Critical Metric)

- **Consumer lag:** Difference between latest offset in the topic and the offset the consumer has committed. If lag grows, the consumer is slower than the producer; we need to scale consumers or optimize the sink.
- **Alert:** CloudWatch metric for MSK consumer lag (or use Kafka's built-in lag metrics). Alarm if lag > threshold (e.g. 10K messages or 1 hour behind). **Interview:** "We monitor consumer lag; if it grows we scale the Spark Streaming job or optimize the Redshift write. Lag is the key metric for 'is the pipeline keeping up?'"

### MSK vs Self-Managed Kafka

- **MSK:** Managed; AWS handles brokers, storage, scaling. We manage IAM, VPC, encryption. **Interview:** "We use Amazon MSK so we don't run Kafka brokers ourselves; we get managed scaling and integration with IAM and VPC."
- **Serverless MSK:** Option for lower throughput; pay per usage. For batch file workflow, standard MSK is typical.

### Security and IAM

- **Authentication:** MSK can use IAM (no SASL passwords) or SCRAM. We use IAM: Glue and Spark Streaming roles have permission to produce/consume. **Interview:** "We use IAM authentication for MSK; our Glue and EMR/Glue Streaming roles have least-privilege produce/consume permissions."
- **Encryption:** TLS in transit; encryption at rest (KMS). Standard for compliance.

---

## 2. Data Engineering Metrics (MSK/Kafka)

| Metric | What to track | Why |
|--------|----------------|-----|
| **Consumer lag** | Per partition or aggregate | Pipeline keeping up; scale or optimize. |
| **Messages in / out** | Produce rate; consume rate | Throughput. |
| **Broker disk usage** | Storage per topic | Retention; cleanup. |
| **Failed sends** | Producer errors | Connectivity; schema; quota. |

---

## 3. Likely Interview Questions & Answers

- **Why MSK and not Kinesis?** We wanted Kafka-compatible APIs so we can use **Spark Streaming** (or Kafka Streams) with the same APIs we're used to. MSK is managed Kafka on AWS. Kinesis would work too but would require a different consumer API.
- **How do you ensure no duplicate data in Redshift when consuming from Kafka?** We overwrite by file_date (and file_type) in Redshift. So even if we consume the same message twice (at-least-once), the second write overwrites the first. Idempotent sink.
- **What if the consumer falls behind?** We monitor lag; if lag grows we scale the Spark Streaming job (more executors) or optimize the Redshift write (batch size, COPY options). We can also add more partitions and more consumer tasks.
- **Exactly-once?** Exactly-once into Kafka: use idempotent producer. Exactly-once into Redshift: hard (no two-phase commit). We do at-least-once consumption + idempotent write (overwrite by file_date) so end state is correct.

Use this when they ask "why Kafka?" or "how do you prevent duplicates when consuming?"
