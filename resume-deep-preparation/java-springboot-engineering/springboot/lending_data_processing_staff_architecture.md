# Lending Data Processing Platform — Staff-Level Architecture

## 1. One-Line Summary

A scalable lending data processing platform that ingests large files from multiple upstream systems, validates and enriches records through configurable workflows, protects downstream systems from fanout overload, and guarantees safe retries through idempotent record-level processing.

---

## 2. Problem Context

Multiple upstream lending systems send files with different formats, frequencies, and sizes.

### Input Characteristics

| Attribute | Example |
|---|---|
| Sources | Source A, Source B, Source C |
| File formats | CSV, PSV, fixed-width |
| File size | 10MB–500MB+ |
| Arrival pattern | Unpredictable, bursty |
| Feed structure | Each source has multiple feeds |
| Field rules | Mandatory and optional fields per feed |
| Workflow | Linear or non-linear |
| Stage type | Synchronous or asynchronous |
| External dependencies | Customer, loan, risk, credit APIs |

---

## 3. Core Staff-Level Challenges

### 3.1 Correctness

The system must avoid:

- duplicate processing
- partial writes
- inconsistent workflow state
- reprocessing completed records
- losing records during failures

### 3.2 Scale

The system must handle:

- large files
- multiple files arriving together
- thousands or millions of records
- bursty workload
- downstream API limits

### 3.3 Failure Isolation

A single bad record should not fail:

- the whole file
- the whole job
- the entire worker fleet
- downstream systems

### 3.4 Workflow Flexibility

Different feeds may require different workflows:

```text
Feed A:
Validate → Enrich Customer → Transform → Persist

Feed B:
Validate → Filter amount > 100 → Enrich Risk → Enrich Loan → Persist

Feed C:
Validate → Branch:
    ├── If secured loan → Collateral Enrichment
    └── If unsecured loan → Risk Enrichment
```

---

## 4. High-Level Architecture

```text
┌────────────────────┐
│  Ingestion Layer   │
│  S3 + EventBridge  │
└─────────┬──────────┘
          │
          ▼
┌────────────────────────────┐
│  Orchestration Layer       │
│  Metadata + Workflow Brain │
│  Partition Planner         │
└─────────┬──────────────────┘
          │
          ▼
┌────────────────────────────┐
│  Event Pipeline            │
│  SQS / Kafka + Retry + DLQ │
└─────────┬──────────────────┘
          │
          ▼
┌────────────────────────────┐
│  Processing Layer          │
│  Validation / Enrichment   │
│  Transformation Workers    │
└─────────┬──────────────────┘
          │
          ▼
┌────────────────────────────┐
│  Storage Layer             │
│  RDS PostgreSQL + Audit    │
└────────────────────────────┘
```

---

## 5. Detailed Architecture Diagram

```text
                                         ┌──────────────────────────────┐
                                         │        Observability          │
                                         │ CloudWatch / Logs / Alerts   │
                                         │ Dashboards / Trace IDs       │
                                         └──────────────▲───────────────┘
                                                        │
                                                        │ metrics, logs, audit events
                                                        │

┌────────────────────────────────────────────────────────────────────────────────────┐
│                              INGESTION LAYER                                      │
│                     Thin, reliable, no business logic                             │
│                                                                                    │
│   ┌──────────────────────┐        ┌────────────────────────┐                      │
│   │ Amazon S3             │──────▶│ Amazon EventBridge      │                      │
│   │ Raw Lending Files     │        │ File Arrival Trigger    │                      │
│   │ CSV / PSV / Fixed     │        │                         │                      │
│   └──────────────────────┘        └────────────┬───────────┘                      │
└────────────────────────────────────────────────┼───────────────────────────────────┘
                                                 │
                                                 ▼
┌────────────────────────────────────────────────────────────────────────────────────┐
│                            ORCHESTRATION LAYER                                    │
│                     The system brain and control plane                             │
│                                                                                    │
│   ┌──────────────────────┐        ┌────────────────────────┐                      │
│   │ Metadata Service      │──────▶│ Workflow Engine         │                      │
│   │ job_id                │        │ feed-specific workflow  │                      │
│   │ file_id               │        │ stage graph             │                      │
│   │ checksum              │        │ sync/async stages       │                      │
│   │ feed_type             │        │ linear/non-linear flow  │                      │
│   └──────────────────────┘        └────────────┬───────────┘                      │
│                                                 │                                  │
│                                                 ▼                                  │
│                              ┌────────────────────────┐                            │
│                              │ Partition Planner       │                            │
│                              │ file → partitions       │                            │
│                              │ partition → records     │                            │
│                              │ track progress          │                            │
│                              └────────────┬───────────┘                            │
│                                           │                                        │
│                                           ▼                                        │
│                              ┌────────────────────────┐                            │
│                              │ Admission Controller    │                            │
│                              │ max active jobs         │                            │
│                              │ per-feed priority       │                            │
│                              │ burst smoothing         │                            │
│                              └────────────┬───────────┘                            │
└───────────────────────────────────────────┼────────────────────────────────────────┘
                                            │
                                            ▼
┌────────────────────────────────────────────────────────────────────────────────────┐
│                              EVENT PIPELINE                                       │
│                    Decoupling, buffering, retry, backpressure                      │
│                                                                                    │
│   ┌──────────────────────┐        ┌────────────────────────┐                      │
│   │ SQS / Kafka Topic     │──────▶│ Retry Queue / Topic     │                      │
│   │ record-work-events    │◀──────│ exponential backoff     │                      │
│   │ key: feed/job/stage   │        │ transient failures     │                      │
│   └──────────┬───────────┘        └────────────────────────┘                      │
│              │                                                                     │
│              │ failure after max retries                                           │
│              ▼                                                                     │
│   ┌──────────────────────┐                                                        │
│   │ Dead Letter Queue     │                                                        │
│   │ poison records        │                                                        │
│   │ schema issues         │                                                        │
│   │ manual replay         │                                                        │
│   └──────────────────────┘                                                        │
└──────────────┬─────────────────────────────────────────────────────────────────────┘
               │
               ▼
┌────────────────────────────────────────────────────────────────────────────────────┐
│                             PROCESSING LAYER                                      │
│                   Stateless, horizontally scalable workers                         │
│                                                                                    │
│   ┌──────────────────────┐                                                        │
│   │ Validation Workers    │                                                        │
│   │ mandatory fields      │                                                        │
│   │ schema validation     │                                                        │
│   │ business validation   │                                                        │
│   └──────────┬───────────┘                                                        │
│              │                                                                     │
│              ▼                                                                     │
│   ┌──────────────────────┐                                                        │
│   │ Filter Workers        │                                                        │
│   │ workflow criteria     │                                                        │
│   │ amount > 100          │                                                        │
│   │ route next stage      │                                                        │
│   └──────────┬───────────┘                                                        │
│              │                                                                     │
│              ▼                                                                     │
│   ┌──────────────────────┐        ┌────────────────────────┐                      │
│   │ Enrichment Workers    │──────▶│ Downstream Protection   │                      │
│   │ async external calls  │        │ rate limiter            │                      │
│   │ idempotent execution  │        │ circuit breaker         │                      │
│   └──────────┬───────────┘        └────────────┬───────────┘                      │
│              │                                 │                                  │
│              │                                 ▼                                  │
│              │                    ┌────────────────────────┐                      │
│              │                    │ External Lending APIs   │                      │
│              │                    │ customer / loan / risk  │                      │
│              │                    └────────────────────────┘                      │
│              ▼                                                                     │
│   ┌──────────────────────┐                                                        │
│   │ Transformation Workers│                                                        │
│   │ normalize records     │                                                        │
│   │ derive fields         │                                                        │
│   └──────────┬───────────┘                                                        │
│              │                                                                     │
│              ▼                                                                     │
│   ┌──────────────────────┐                                                        │
│   │ Finalization Workers  │                                                        │
│   │ persist result        │                                                        │
│   │ update final state    │                                                        │
│   └──────────┬───────────┘                                                        │
└──────────────┼─────────────────────────────────────────────────────────────────────┘
               │
               ▼
┌────────────────────────────────────────────────────────────────────────────────────┐
│                               STORAGE LAYER                                       │
│                                                                                    │
│   ┌──────────────────────┐        ┌────────────────────────┐                      │
│   │ Amazon RDS PostgreSQL │        │ Audit Store             │                      │
│   │ job_metadata          │        │ record lineage          │                      │
│   │ partition_state       │        │ stage transitions       │                      │
│   │ record_state          │        │ failure reason          │                      │
│   │ final_results         │        │ replay history          │                      │
│   └──────────────────────┘        └────────────────────────┘                      │
│                                                                                    │
│   ┌──────────────────────┐                                                        │
│   │ Bad Records Store     │                                                        │
│   │ validation failures   │                                                        │
│   │ repair + replay       │                                                        │
│   └──────────────────────┘                                                        │
└────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 6. Component Responsibilities

## 6.1 Ingestion Layer

### Components

- Amazon S3
- Amazon EventBridge

### Responsibility

The ingestion layer only captures file arrival and triggers orchestration.

It does **not**:

- validate records
- call external APIs
- decide workflow routing
- split files deeply
- perform business logic

### Why this matters

Separating ingestion from orchestration prevents file arrival rate from directly controlling processing rate.

Staff-level line:

> “We intentionally kept ingestion thin so that bursty file arrival would not couple directly to workflow execution.”

---

## 6.2 Orchestration Layer

### Components

- Metadata Service
- Workflow Engine
- Partition Planner
- Admission Controller
- Stage State Manager

### Responsibility

This is the control plane of the system.

It decides:

- what file arrived
- what feed type it belongs to
- which workflow applies
- how the file should be partitioned
- which stages must run
- whether stages are sync or async
- how progress is tracked
- when a job is complete

### Staff-level importance

This is where the system intelligence lives.

Ingestion is the entry point.  
Processing is the muscle.  
Orchestration is the brain.

---

## 6.3 Event Pipeline

### Components

- SQS or Kafka
- Retry Queue / Retry Topic
- DLQ
- Optional Transactional Outbox

### Responsibility

The event pipeline decouples orchestration from processing.

It provides:

- buffering
- backpressure
- retry safety
- fanout control
- failure isolation
- replayability

### Why queue/event pipeline matters

Without this layer:

```text
20k valid records → 20k external API calls → downstream overload
```

With this layer:

```text
20k valid records → queue → controlled workers → rate-limited API calls
```

Staff-level line:

> “The queue was introduced not just for async processing, but as a backpressure boundary between file scale and downstream capacity.”

---

## 6.4 Processing Layer

### Components

- Validation Workers
- Filter Workers
- Enrichment Workers
- Transformation Workers
- Finalization Workers

### Responsibility

Workers execute individual stages.

Each worker should be:

- stateless
- horizontally scalable
- idempotent
- independently deployable
- observable

### Worker Contract

Each worker receives:

```json
{
  "jobId": "JOB-123",
  "fileId": "FILE-456",
  "feedId": "LENDING_FEED_A",
  "recordId": "ROW-10023",
  "stageName": "CUSTOMER_ENRICHMENT",
  "idempotencyKey": "LENDING_FEED_A:FILE-456:ROW-10023:CUSTOMER_ENRICHMENT",
  "payloadRef": "s3://bucket/path/or/db-record-ref"
}
```

---

## 7. Workflow Model

### 7.1 Workflow Definition Example

```yaml
feed: LENDING_FEED_A
stages:
  - name: VALIDATE
    type: sync
    worker: validation-worker

  - name: FILTER
    type: sync
    condition: amount > 100
    worker: filter-worker

  - name: CUSTOMER_ENRICHMENT
    type: async
    worker: enrichment-worker
    externalDependency: customer-api
    maxConcurrency: 100
    retryPolicy:
      maxAttempts: 3
      backoff: exponential

  - name: TRANSFORM
    type: sync
    worker: transform-worker

  - name: FINALIZE
    type: sync
    worker: finalization-worker
```

### 7.2 Linear Workflow

```text
Validate → Filter → Enrich → Transform → Persist
```

### 7.3 Non-Linear Workflow

```text
Validate
   ├── if secured loan → Collateral Enrichment
   ├── if unsecured loan → Risk Enrichment
   └── if commercial loan → Business Entity Enrichment
```

---

## 8. Data Model

### 8.1 job_metadata

Tracks file-level/job-level state.

```sql
CREATE TABLE job_metadata (
    job_id              VARCHAR PRIMARY KEY,
    file_id             VARCHAR NOT NULL,
    feed_id             VARCHAR NOT NULL,
    source_system       VARCHAR NOT NULL,
    file_name           VARCHAR NOT NULL,
    file_checksum       VARCHAR NOT NULL,
    status              VARCHAR NOT NULL,
    total_records       BIGINT,
    valid_records       BIGINT,
    invalid_records     BIGINT,
    created_at          TIMESTAMP NOT NULL,
    updated_at          TIMESTAMP NOT NULL
);
```

Example statuses:

```text
RECEIVED
PARTITIONING
IN_PROGRESS
PARTIALLY_FAILED
FAILED
COMPLETED
```

### 8.2 partition_state

Tracks partition-level progress.

```sql
CREATE TABLE partition_state (
    partition_id        VARCHAR PRIMARY KEY,
    job_id              VARCHAR NOT NULL,
    file_id             VARCHAR NOT NULL,
    start_offset        BIGINT,
    end_offset          BIGINT,
    record_start        BIGINT,
    record_end          BIGINT,
    status              VARCHAR NOT NULL,
    processed_count     BIGINT DEFAULT 0,
    failed_count        BIGINT DEFAULT 0,
    created_at          TIMESTAMP NOT NULL,
    updated_at          TIMESTAMP NOT NULL
);
```

### 8.3 record_state

Tracks record-level workflow state.

```sql
CREATE TABLE record_state (
    record_id           VARCHAR PRIMARY KEY,
    job_id              VARCHAR NOT NULL,
    file_id             VARCHAR NOT NULL,
    feed_id             VARCHAR NOT NULL,
    row_number          BIGINT NOT NULL,
    current_stage       VARCHAR NOT NULL,
    status              VARCHAR NOT NULL,
    idempotency_key     VARCHAR NOT NULL UNIQUE,
    retry_count         INT DEFAULT 0,
    last_error          TEXT,
    created_at          TIMESTAMP NOT NULL,
    updated_at          TIMESTAMP NOT NULL
);
```

### 8.4 record_stage_state

Tracks each stage per record.

```sql
CREATE TABLE record_stage_state (
    id                  BIGSERIAL PRIMARY KEY,
    record_id           VARCHAR NOT NULL,
    job_id              VARCHAR NOT NULL,
    stage_name          VARCHAR NOT NULL,
    status              VARCHAR NOT NULL,
    idempotency_key     VARCHAR NOT NULL UNIQUE,
    input_hash          VARCHAR,
    output_hash         VARCHAR,
    started_at          TIMESTAMP,
    completed_at        TIMESTAMP,
    retry_count         INT DEFAULT 0,
    error_message       TEXT
);
```

### 8.5 final_results

Stores processed lending outcomes.

```sql
CREATE TABLE final_results (
    result_id           VARCHAR PRIMARY KEY,
    record_id           VARCHAR NOT NULL,
    job_id              VARCHAR NOT NULL,
    feed_id             VARCHAR NOT NULL,
    normalized_payload  JSONB NOT NULL,
    processing_status   VARCHAR NOT NULL,
    created_at          TIMESTAMP NOT NULL
);
```

---

## 9. End-to-End Processing Flow

### Step 1: File Arrival

```text
Upstream system uploads file to S3.
S3 emits file-created event.
EventBridge routes event to orchestration.
```

### Step 2: Metadata Registration

```text
Metadata service creates job_id and file_id.
Computes checksum.
Detects duplicate file.
Stores job status as RECEIVED.
```

### Step 3: Workflow Resolution

```text
Workflow engine identifies feed type.
Loads workflow definition.
Determines stages and routing rules.
```

### Step 4: Partition Planning

```text
Partition planner splits file into logical partitions.
Each partition tracks offset/range/record count.
Partition state is stored.
```

### Step 5: Event Publication

```text
For each eligible record or partition:
    create work event
    attach idempotency key
    publish to queue/topic
```

### Step 6: Worker Processing

```text
Worker consumes event.
Checks idempotency state.
Executes stage.
Writes stage output.
Publishes next-stage event.
```

### Step 7: External Enrichment

```text
Enrichment worker calls external APIs through:
    rate limiter
    circuit breaker
    timeout policy
    retry policy
```

### Step 8: Persistence

```text
Finalization worker writes final result.
Updates record state.
Updates partition progress.
Updates job completion if all partitions complete.
```

---

## 10. Idempotency Strategy

### 10.1 Idempotency Key

```text
idempotency_key = feed_id + file_id + row_number + stage_name
```

Example:

```text
LENDING_FEED_A:FILE_2026_05_03:ROW_10023:CUSTOMER_ENRICHMENT
```

### 10.2 Why This Works

A retry of the same record-stage combination maps to the same key.

Before executing a stage:

```text
if record_stage_state.status == COMPLETED:
    skip execution
else:
    process stage
```

### 10.3 Idempotent Write Pattern

```sql
INSERT INTO record_stage_state (
    record_id,
    job_id,
    stage_name,
    status,
    idempotency_key
)
VALUES (?, ?, ?, 'IN_PROGRESS', ?)
ON CONFLICT (idempotency_key)
DO NOTHING;
```

If insert succeeds, this worker owns the stage.  
If insert fails, another worker already processed or is processing it.

Staff-level line:

> “Retries are safe because the unit of idempotency is not the whole job; it is the record-stage execution.”

---

## 11. Retry Strategy

### 11.1 Retry Categories

| Failure Type | Example | Action |
|---|---|---|
| Transient | timeout, 503 | retry with backoff |
| Rate limit | 429 | retry after delay |
| Business validation | missing mandatory field | bad record store |
| Poison message | malformed payload | DLQ |
| Permanent external error | invalid customer id | mark failed / DLQ |

### 11.2 Retry Flow

```text
Worker fails transiently
    → increment retry_count
    → publish to retry queue
    → retry after backoff
    → max retries exceeded
    → DLQ
```

### 11.3 Why Not Infinite Retries?

Infinite retries can:

- clog queues
- hide real defects
- overload downstream systems
- block valid records

Staff-level line:

> “Retries are bounded because retry is a recovery mechanism, not a substitute for correctness.”

---

## 12. DLQ Strategy

### 12.1 What Goes to DLQ?

- poison messages
- schema-incompatible events
- max retries exceeded
- unexpected worker exceptions
- unrecoverable dependency failures

### 12.2 DLQ Payload

```json
{
  "jobId": "JOB-123",
  "fileId": "FILE-456",
  "recordId": "ROW-10023",
  "stageName": "CUSTOMER_ENRICHMENT",
  "failureType": "MAX_RETRIES_EXCEEDED",
  "lastError": "Customer API timed out",
  "retryCount": 3,
  "originalEvent": {}
}
```

### 12.3 Replay Strategy

Replay is allowed only after:

- bug fix
- data correction
- dependency recovery
- manual approval for sensitive feeds

Staff-level line:

> “DLQ is not just a failure bucket; it is an operational recovery mechanism.”

---

## 13. Downstream Protection

### 13.1 Problem

Without protection:

```text
20,000 records → 20,000 external API calls
```

This causes:

- API throttling
- cascading failures
- increased retries
- duplicate work
- SLA breach

### 13.2 Protection Mechanisms

#### Rate Limiter

Controls request rate:

```text
customer-api: 100 requests/sec
risk-api: 50 requests/sec
loan-api: 75 requests/sec
```

#### Circuit Breaker

If downstream is unhealthy:

```text
fail fast → retry later → protect system
```

#### Bulkhead Isolation

Separate worker pools:

```text
customer enrichment workers
risk enrichment workers
loan enrichment workers
```

A failure in one dependency should not block all processing.

Staff-level line:

> “We treated downstream systems as constrained resources, not infinite dependencies.”

---

## 14. Backpressure

### 14.1 Where Backpressure Exists

```text
S3 arrival rate
    → orchestration admission control
        → queue depth
            → worker concurrency
                → downstream rate limits
```

### 14.2 Signals

| Signal | Meaning |
|---|---|
| Queue depth rising | workers cannot keep up |
| DLQ depth rising | data or code quality issue |
| Retry queue rising | downstream instability |
| Worker CPU high | scale workers |
| DB connection saturation | tune writes/pooling |
| API 429s | reduce concurrency |

Staff-level line:

> “Backpressure is applied at multiple boundaries, not only at the queue.”

---

## 15. Partitioning Strategy

### 15.1 Why Partition?

Partitioning allows:

- parallel processing
- progress tracking
- partial retries
- large file handling
- failure isolation

### 15.2 Partitioning Options

| Strategy | Use Case |
|---|---|
| Record-count partition | simple CSV/PSV |
| Byte-offset partition | very large files |
| Feed-based partition | different workflows |
| Hash partition | preserve grouping |
| Range partition | ordered records |

### 15.3 Example

```text
File: 500MB, 1,000,000 records

Partition 1: records 1–50,000
Partition 2: records 50,001–100,000
...
Partition 20: records 950,001–1,000,000
```

### 15.4 Staff-Level Concern

Do not blindly create too many partitions.

Too many partitions cause:

- metadata overhead
- more DB writes
- queue flooding
- coordination complexity

---

## 16. Database Design Concerns

### 16.1 PostgreSQL Responsibilities

PostgreSQL stores:

- job metadata
- partition progress
- record state
- stage state
- final results

### 16.2 Avoiding DB Bottlenecks

Use:

- JDBC batching
- connection pooling
- partitioned tables for large history
- indexes on job_id, file_id, status
- async audit writes where possible
- avoid per-record synchronous metadata updates when volume is high

### 16.3 Important Indexes

```sql
CREATE INDEX idx_record_state_job_status
ON record_state(job_id, status);

CREATE INDEX idx_record_stage_record_stage
ON record_stage_state(record_id, stage_name);

CREATE UNIQUE INDEX idx_record_stage_idempotency
ON record_stage_state(idempotency_key);
```

Staff-level line:

> “The database is the source of truth, but not the coordination bottleneck for every processing step.”

---

## 17. Observability

### 17.1 Metrics

Track:

```text
file_received_count
job_processing_latency
partition_lag
record_processing_latency
stage_latency
worker_error_rate
retry_count
dlq_depth
external_api_latency
external_api_429_count
db_write_latency
```

### 17.2 Logs

Every log should include:

```text
job_id
file_id
feed_id
partition_id
record_id
stage_name
correlation_id
```

### 17.3 Dashboards

Dashboard views:

- jobs by status
- feed-level SLA
- stuck partitions
- DLQ by failure type
- retry trends
- external dependency health
- DB connection pool usage

Staff-level line:

> “At scale, observability is not optional because debugging distributed batch systems without correlation IDs becomes impossible.”

---

## 18. Security and Compliance

### 18.1 Data Protection

Use:

- S3 encryption
- RDS encryption
- least-privilege IAM roles
- private subnets for workers
- VPC endpoints where possible
- secrets in AWS Secrets Manager
- audit logging for replay/manual repair

### 18.2 Sensitive Data

For lending data:

- avoid logging raw PII
- mask sensitive fields
- store only references in events where possible
- restrict access to bad records and audit payloads

Staff-level line:

> “Because lending data can contain sensitive customer information, event payloads should be minimal and references should be preferred over copying full records across systems.”

---

## 19. Deployment Model

### 19.1 Compute Options

| Layer | AWS Option |
|---|---|
| Ingestion | S3, EventBridge |
| Orchestration | ECS/Fargate service or Lambda |
| Workers | ECS/Fargate, EKS, AWS Batch |
| Queue | SQS or MSK/Kafka |
| Storage | RDS PostgreSQL |
| Observability | CloudWatch, OpenSearch, Grafana |

### 19.2 Why ECS/Fargate Workers?

Good fit when:

- long-running workers are needed
- external API calls need controlled concurrency
- service-level scaling is required
- independent deployments are useful

### 19.3 Where Spring Batch Fits

Spring Batch can still be used for:

- file parsing
- chunk-oriented validation
- partition planning
- controlled batch-style operations

But the critical scaling path should not rely only on one giant synchronous batch job.

Staff-level line:

> “Spring Batch remained useful for structured batch semantics, but we evolved the architecture so the heavy fanout path became event-driven.”

---

## 20. Multi-Region Strategy

### 20.1 Should This Be Multi-Region?

Not necessarily.

For this lending batch system, the primary design goal is:

```text
correctness + replayability + controlled processing
```

not ultra-low-latency global availability.

### 20.2 Recommended Position

Use single-region active processing with DR readiness:

```text
Primary Region:
    active ingestion
    active orchestration
    active processing
    active database

DR Strategy:
    S3 cross-region replication
    RDS snapshots / PITR
    infrastructure as code
    replay from durable files/events
```

### 20.3 Staff-Level Answer

> “We did not start with active-active multi-region because it would introduce duplicate processing, cross-region state consistency, and operational complexity. Since the workload is replayable batch processing, we prioritized durable input, idempotency, and recovery.”

---

## 21. Tradeoffs

### 21.1 Event-Driven vs Simple Batch

| Choice | Benefit | Cost |
|---|---|---|
| Simple Spring Batch | easier to build | weak fanout control |
| Event-driven workers | scalable, resilient | more operational complexity |

### 21.2 SQS vs Kafka

| Option | Better For |
|---|---|
| SQS | simple queueing, retry, DLQ, AWS-native |
| Kafka | high-throughput event streams, replay, ordering by key, event history |

Interview-safe answer:

> “If the system primarily needs work distribution and retries, SQS is enough. If we need durable event history, replay by consumers, and multiple downstream subscribers, Kafka/MSK becomes stronger.”

### 21.3 Record-Level vs File-Level Processing

| Choice | Benefit | Cost |
|---|---|---|
| File-level | simpler | one bad record can affect file |
| Record-level | failure isolation | more metadata and events |

### 21.4 Strong Consistency vs Eventual Consistency

| Choice | Benefit | Cost |
|---|---|---|
| Strict sync flow | easier reasoning | poor scale |
| Eventual consistency | scalable | harder debugging |

Staff-level line:

> “We accepted eventual consistency at workflow stage level, while preserving correctness through durable state and idempotency.”

---

## 22. Failure Scenarios and Answers

### 22.1 Worker Dies Mid-Processing

What happens?

```text
Message visibility timeout expires.
Message becomes available again.
Another worker picks it up.
Idempotency check prevents duplicate side effects.
```

### 22.2 External API Times Out

```text
Worker marks transient failure.
Publishes to retry queue.
Backoff is applied.
Circuit breaker protects downstream if failures continue.
```

### 22.3 Poison Message

```text
Worker detects schema/parsing issue.
Message goes to DLQ.
Record is marked failed.
Job continues.
```

### 22.4 Database Temporarily Unavailable

```text
Workers fail fast or retry.
Queue buffers work.
Circuit breaker may pause processing.
No data is lost because raw file and events are durable.
```

### 22.5 Duplicate File Arrival

```text
Metadata service computes checksum.
If same source + feed + checksum already processed:
    reject or mark duplicate.
```

### 22.6 Partial Job Failure

```text
Completed records remain completed.
Failed records go to retry/DLQ.
Job status becomes PARTIALLY_FAILED.
Operator can replay failed subset.
```

---

## 23. Scaling Strategy

### 23.1 What Scales Independently?

| Layer | Scaling Unit |
|---|---|
| Ingestion | S3/EventBridge managed |
| Orchestration | orchestration service replicas |
| Queue | queue/topic partitions |
| Workers | ECS task count / Kubernetes pods |
| DB | connection pool, indexes, read replicas, partitioning |
| External APIs | rate limits, concurrency caps |

### 23.2 Scaling Controls

Use:

- max active jobs
- max partitions per file
- max records in flight
- worker autoscaling by queue depth
- per-stage concurrency
- per-dependency rate limits

Staff-level line:

> “We scaled the system by separating control-plane decisions from data-plane execution.”

---

## 24. What I Would Put on the Interview Slide

### Slide Title

```text
Lending Data Processing Platform — Evolution to Event-Driven, Idempotent Workflow Processing
```

### Diagram Sections

```text
Ingestion → Orchestration → Event Pipeline → Processing → External Systems → Storage
```

### 4 Callouts

```text
1. Ingestion separated from orchestration
2. Queue introduced for backpressure
3. Idempotency at record-stage level
4. DLQ isolates failures and enables replay
```

---

## 25. 10-Minute Narration Structure

### Minute 0–1: Problem

```text
Large lending files from multiple feeds.
Different formats.
Dynamic workflows.
Need correctness, scale, and failure isolation.
```

### Minute 1–3: V1

```text
Started with Spring Batch.
Single job.
Synchronous processing.
Worked at small scale.
```

### Minute 3–5: Pain

```text
Large files caused long runtimes.
20k records caused fanout explosion.
External APIs got overwhelmed.
Retries caused duplicate risk.
Failures were too coarse-grained.
```

### Minute 5–8: Final Architecture

```text
Separated ingestion, orchestration, and processing.
Introduced queues for backpressure.
Workers became stateless and idempotent.
DLQ isolated poison records.
Workflow engine handled feed-specific routing.
```

### Minute 8–9: Tradeoffs

```text
More operational complexity.
Eventual consistency.
Harder debugging.
But better scalability, recovery, and resilience.
```

### Minute 9–10: Impact

```text
Handled bursty file arrivals.
Protected downstream systems.
Reduced full-job failures.
Enabled partial replay.
Improved confidence in correctness.
```

---

## 26. Interview Follow-Up Cheat Sheet

### Q: Why separate ingestion and orchestration?

```text
Because file arrival is not the same as processing readiness.
Ingestion should be reliable and thin.
Orchestration controls partitioning, workflow routing, and admission control.
```

### Q: Why use a queue?

```text
The queue creates a backpressure boundary.
It absorbs burst traffic and lets workers process at a safe rate.
```

### Q: How do you avoid duplicate processing?

```text
Every record-stage execution has a deterministic idempotency key.
Before executing a stage, the worker checks whether that key has already completed.
```

### Q: What happens if 20k records need external calls?

```text
We do not call the external system directly from batch threads.
We enqueue work, use controlled worker concurrency, rate limiting, and circuit breakers.
```

### Q: Why not Spark?

```text
Spark is strong for distributed compute and transformations.
This system required fine-grained workflow orchestration, external API calls, retries, DLQs, and record-level state.
For this use case, event-driven workers were a better fit.
```

### Q: Why not just use Spring Batch partitioning?

```text
Spring Batch partitioning improves throughput, but it does not fully solve downstream fanout, API protection, distributed retries, or independent scaling of stages.
```

### Q: What is the hardest part?

```text
The hardest part is not parsing the file.
The hardest part is preserving correctness while processing records independently across retries, failures, and external dependencies.
```

---

## 27. Staff-Level Summary

This system is not impressive because it uses S3, queues, or workers.

It is impressive because it shows these engineering decisions:

```text
1. Separate ingestion from orchestration.
2. Treat workflow as a configurable state machine.
3. Use queues as backpressure boundaries.
4. Protect downstream systems from uncontrolled fanout.
5. Make retries safe through record-stage idempotency.
6. Isolate failures through DLQ and bad-record handling.
7. Track progress at job, partition, record, and stage levels.
8. Prefer replayability and correctness over premature multi-region complexity.
```

Final staff-level line:

> “The architecture evolved from a simple batch job into a controlled, event-driven workflow platform where correctness, backpressure, and failure isolation were first-class design concerns.”
