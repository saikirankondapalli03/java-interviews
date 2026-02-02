# Component 06: MSK (Kafka) — Question & Answer Bank

**Reference:** `components/06-MSK-KAFKA.md`  
**Interview coverage:** Intro, architecture, technical depth, scenarios, follow-ups, evaluation.

---

## 1. Intro & Context (Opening)

### Q1.1 What is the role of Kafka (MSK) in the file processing flow?

**Answer:** Spark batch job writes validated rows to Kafka topics (e.g. `equilend.positions.curated`, `equilend.mtm_pnl.curated`). Spark Streaming (or Glue Streaming) consumes from these topics, aggregates, and sinks to **Snowflake** (or S3). Kafka = single place for “reporting-ready” data; multiple consumers possible.

---

### Q1.2 Why Kafka in a file workflow? Why not just write to Snowflake from Glue?

**Answer:** **Unify file-originated data with streaming:** Same pipeline can later add real-time sources; one consumer (Spark Streaming) reads from Kafka and loads Snowflake. No separate “file path” vs “stream path” in the consumer. **Multiple consumers:** Spark Streaming loads Snowflake; we could add another consumer (e.g. real-time dashboard, risk engine) without changing the producer. **Decouple producer (Glue) from consumer (Spark Streaming):** If Snowflake load is slow Kafka buffers. Producer doesn’t block. **Interview:** “We use Kafka (MSK) so that file-originated data and future event streams share the same bus. Spark Streaming consumes from Kafka and loads Snowflake; we can add more consumers without changing the file processing job.”

---

## 2. Architecture & Ownership

### Q2.1 How do you design topics and partitioning?

**Answer:** **Topics:** e.g. `equilend.positions.curated`, `equilend.mtm_pnl.curated`. One topic per file type (or one topic with message attributes for type). **Partitioning:** Partition by a key so that all rows for the same `file_date` (or `borrower_id`) go to the same partition. That gives ordering per key and allows consumer to process in order. **Key choice:** `file_date` so one partition = one day’s data; consumer can commit offset after loading that day to Snowflake. **Partition count:** More partitions = more parallelism; don’t over-partition (e.g. 6–12 per topic to start). **Interview:** “We partition by file_date so that each partition holds one day’s data; the consumer can process partition by partition and commit offsets. We use enough partitions to allow parallel consumer tasks.”

---

### Q2.2 At-least-once vs exactly-once producer? What about the consumer?

**Answer:** **At-least-once:** Producer sends; if ack fails, retry. Possible duplicates. Consumer must be idempotent (overwrite by file_date in Snowflake). **Exactly-once (idempotent producer):** Producer uses idempotence so retries don’t create duplicate records in Kafka. Consumer still needs to write idempotently to Snowflake (same key overwrite). **Interview:** “We use at-least-once producer; our Spark Streaming consumer overwrites by file_date in Snowflake so duplicate consumption doesn’t corrupt data. We could enable idempotent producer for exactly-once into Kafka if we wanted to avoid duplicate messages in the topic.”

---

## 3. Technical Depth (Pain Points)

### Q3.1 How does the consumer (Spark Streaming) manage offsets and scale?

**Answer:** **Consumer group:** One consumer group per “application” (e.g. equilend-reporting). Each partition is consumed by one consumer in the group. Scale by adding more partitions or more consumer instances (up to #partitions). **Offset management:** Spark Structured Streaming commits offsets to Kafka (or checkpoint). If the job fails, next run resumes from last committed offset. **Interview:** “We use a consumer group so that Spark Streaming can scale; we commit offsets after successfully writing to Snowflake so we don’t reprocess on restart.”

---

### Q3.2 What is consumer lag and why is it critical?

**Answer:** **Consumer lag:** Difference between latest offset in the topic and the offset the consumer has committed. If lag grows the consumer is slower than the producer; we need to scale consumers or optimize the sink. **Alert:** CloudWatch metric for MSK consumer lag. Alarm if lag > threshold (e.g. 10K messages or 1 hour behind). **Interview:** “We monitor consumer lag; if it grows we scale the Spark Streaming job or optimize the Snowflake write. Lag is the key metric for ‘is the pipeline keeping up?’”

---

### Q3.3 MSK vs self-managed Kafka? Security?

**Answer:** **MSK:** Managed; AWS handles brokers, storage, scaling. We manage IAM, VPC, encryption. **Security:** MSK can use IAM (no SASL passwords) or SCRAM. We use IAM: Glue and Spark Streaming roles have permission to produce/consume. TLS in transit; encryption at rest (KMS). **Interview:** “We use IAM authentication for MSK; our Glue and EMR/Glue Streaming roles have least-privilege produce/consume permissions.”

---

## 4. Scenario & Design

### Q4.1 Why MSK and not Kinesis?

**Answer:** We wanted Kafka-compatible APIs so we can use **Spark Streaming** (or Kafka Streams) with the same APIs we’re used to. MSK is managed Kafka on AWS. Kinesis would work too but would require a different consumer API.

---

### Q4.2 How do you ensure no duplicate data in Snowflake when consuming from Kafka?

**Answer:** We overwrite by file_date (and file_type) in Snowflake. So even if we consume the same message twice (at-least-once) the second write overwrites the first. Idempotent sink.

---

### Q4.3 What if the consumer falls behind?

**Answer:** We monitor lag; if lag grows we scale the Spark Streaming job (more executors) or optimize the Snowflake write (batch size, COPY options). We can also add more partitions and more consumer tasks.

---

### Q4.4 Exactly-once into Snowflake?

**Answer:** Exactly-once into Kafka: use idempotent producer. Exactly-once into Snowflake: hard (no two-phase commit). We do at-least-once consumption + idempotent write (overwrite by file_date) so end state is correct.

---

## 5. Data Engineering Metrics (MSK/Kafka)

### Q5.1 What MSK/Kafka metrics do you track?

**Answer:**

| Metric | What to track | Why |
|--------|----------------|-----|
| **Consumer lag** | Per partition or aggregate | Pipeline keeping up; scale or optimize. |
| **Messages in / out** | Produce rate; consume rate | Throughput. |
| **Broker disk usage** | Storage per topic | Retention; cleanup. |
| **Failed sends** | Producer errors | Connectivity; schema; quota. |

---

## 6. Evaluation Cheat Sheet

| Area | What good looks like | Red flag |
|------|----------------------|----------|
| **Why Kafka** | Decouple; multiple consumers; same bus for file + stream | “We just use it” |
| **Partitioning** | Key by file_date (or business key); ordering per partition | Random key or no key |
| **Lag** | Monitor lag; alarm; scale or optimize | No mention of lag |
| **Idempotency** | Overwrite by file_date in Snowflake; idempotent producer optional | “We get exactly-once” without explaining sink |

Use this when they ask “why Kafka?” or “how do you prevent duplicates when consuming?”
