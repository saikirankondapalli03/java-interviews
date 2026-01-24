# FCAT Java Microservices (Kafka) — Cheat Sheet

**Quick reference for last-minute review (70 min exam, 15 questions).**

---

## Kafka Core

| Term | Meaning |
|------|---------|
| **Topic** | Category/feed; events published here |
| **Partition** | Ordered, immutable log within a topic; enables parallelism |
| **Offset** | Record ID within partition; consumer tracks this |
| **Broker** | Kafka server in the cluster |
| **Producer** | Publishes to topics |
| **Consumer** | Subscribes and processes |
| **Consumer group** | Consumers share a topic; each partition → 1 consumer in group |
| **ISR** | In-Sync Replicas; replicas fully caught up with leader |

**Ordering:** Only **within a partition**. Same key → same partition (hash).

**Consumer group:** Each partition consumed by **exactly one** consumer in the group.

---

## Producer

| acks | Behavior |
|------|----------|
| 0 | No ack; possible loss |
| 1 | Leader ack |
| all / -1 | Leader + all ISR; best durability |

**Exactly-once:** `enable.idempotence=true` + `acks=all` (+ transactions if needed).

**Partition key:** Same key → same partition → ordering for that key.

---

## Consumer

| Config | Effect |
|--------|--------|
| `auto.offset.reset=earliest` | Start from beginning if no offset |
| `auto.offset.reset=latest` | Only new messages (default) |
| `auto.offset.reset=none` | Throw exception if no offset found |
| `enable.auto.commit=true` | Auto-commit every 5s (default) |
| `enable.auto.commit=false` | Manual commit required |

**Consumer lag** = latest offset − committed offset (how far behind).

**Offset storage:** `__consumer_offsets` topic (internal, compacted).

**Auto-commit risk:** May commit before processing completes → duplicates on crash.

**Manual commit:** `commitSync()` (blocking) or `commitAsync()` (non-blocking).

---

## Kafka APIs

1. **Producer** — publish
2. **Consumer** — subscribe & process
3. **Streams** — process streams (Kafka in/out)
4. **Connector** — Kafka ↔ external systems (DB, etc.)

---

## Spring Kafka (Java)

| Component | Role |
|-----------|------|
| `KafkaTemplate` | **Produce** messages |
| `@KafkaListener` | **Consume** messages |
| `ConsumerRecord` | Incoming message (key, value, offset, partition, etc.) |
| `ProducerRecord` | Outgoing message |

**Consumer:** `@KafkaListener(topics = "x", groupId = "y")`  
**Producer:** `kafkaTemplate.send(topic, key, value)` or `send(topic, value)`

---

## Delivery Semantics

| Semantic | Loss? | Duplicates? |
|----------|-------|-------------|
| At-most-once | Yes | No |
| At-least-once | No | Yes |
| Exactly-once | No | No |

**DLQ** = Dead Letter Queue; failed messages go here for retry/investigation.

**Idempotent consumer** = safe to process same message twice (handles at-least-once).

---

## Event-Driven Microservices

- **Async:** no blocking; react to events
- **Loose coupling:** no direct service-to-service calls
- **Resilience:** Kafka buffers; consumers can lag/restart
- **Replay:** events stored; reprocess via offsets

---

## Retention & Compaction

- **Retention:** time/size-based; old data deleted
- **Log compaction:** keep **latest value per key**; good for changelog/cache sync

---

## One-Liners to Remember

- **Partitions** → parallelism; **ordering** only per partition.
- **Consumer group** → 1 partition : 1 consumer in group.
- **acks=all** → strongest durability (leader + ISR).
- **KafkaTemplate** = produce; **@KafkaListener** = consume.
- **Consumer lag** = how far behind the consumer is.
- **Exactly-once** = idempotent producer + acks=all (+ transactions).
- **DLQ** = where failed messages go.
- **ISR** = replicas in sync with leader.
- **Offsets** stored in `__consumer_offsets` topic.
- **Auto-commit** = commits every 5s; risk of duplicates on crash.
- **Manual commit** = commit after processing; better control.
- **auto.offset.reset** = fallback when no committed offset exists.

---

**Files:** `STUDY-GUIDE.md` (concepts) | `PRACTICE-QUESTIONS.md` (20 Q&A) | `CHEAT-SHEET.md` (this)
