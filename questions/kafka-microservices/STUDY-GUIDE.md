# FCAT Java Microservices (Kafka) — Study Guide

**Exam:** 15 questions, 70 minutes | **Focus:** Java microservices + Apache Kafka

---

## 1. Apache Kafka Fundamentals

### What Is Kafka?
- **Distributed event streaming platform** (not just a message queue)
- Three main capabilities: **publish/subscribe** to streams, **store** streams durably, **process** streams in real time
- Runs as a **cluster** of one or more **brokers**

### Core Concepts

| Concept | Definition |
|--------|------------|
| **Topic** | Category/feed name; events are published to topics (like a folder) |
| **Partition** | Ordered, immutable sequence of records within a topic; enables parallelism |
| **Offset** | Unique ID of a record within a partition; consumers track offsets |
| **Broker** | Kafka server; stores topics and serves producers/consumers |
| **Producer** | Publishes records to Kafka topics |
| **Consumer** | Subscribes to topics and processes records |
| **Consumer Group** | Set of consumers that jointly consume a topic; each partition is consumed by exactly one consumer in the group |

### Partitions & Scalability
- More partitions → more parallelism (more consumers in a group can work in parallel)
- **Ordering guarantee**: only within a partition, not across partitions
- **Partition key**: producer can specify a key; records with same key go to same partition (key → `hash(key) % num_partitions`)

### Replication & Durability
- Topics have a **replication factor** (e.g. 3); each partition has multiple replicas
- One replica per partition is the **leader**; others are **followers**
- **ISR (In-Sync Replicas)**: replicas that are fully in sync with the leader
- Committed = written to all in-sync replicas

---

## 2. Kafka APIs

1. **Producer API** — publish streams to topics  
2. **Consumer API** — subscribe to topics and process streams  
3. **Streams API** — stream processing (inputs/outputs are Kafka topics)  
4. **Connector API** — reusable producers/consumers that connect Kafka to external systems (DB, etc.)

---

## 3. Producer Semantics (Delivery Guarantees)

| Value | Behavior | Use Case |
|-------|----------|----------|
| **acks=0** | Fire-and-forget; no wait for ack | Low latency, possible loss |
| **acks=1** | Leader acknowledges | Default balance |
| **acks=all** (or -1) | Leader + all in-sync replicas acknowledge | No loss when replicas available |

- **Idempotent producer**: `enable.idempotence=true` — avoids duplicates from retries (same PID + sequence)
- **Transactions**: exactly-once across multiple topics/partitions (producer + consumer)

---

## 4. Consumer & Consumer Groups

- **Consumer group**: logical grouping of consumers; each partition is consumed by **at most one** consumer in the group
- **Rebalancing**: when consumers join/leave, partitions are reassigned among the group
- **Offset commit**: consumer commits offset after processing; can be automatic or manual
- **`auto.offset.reset`**:  
  - `earliest` — start from beginning if no committed offset  
  - `latest` — only new messages (default)

---

## 4.5. Offset Management (Critical Topic)

### What is an Offset?
- **Offset**: unique, sequential ID of a record within a partition (starts at 0)
- Consumer tracks the **last committed offset** per partition to know where to resume
- Offsets are stored in Kafka's internal topic: `__consumer_offsets`

### Offset Commit Strategies

| Strategy | Configuration | Behavior |
|----------|---------------|----------|
| **Automatic** | `enable.auto.commit=true` (default) | Consumer commits offsets periodically (every 5s by default) |
| **Manual** | `enable.auto.commit=false` | Application must explicitly commit offsets |

**Automatic Commit:**
- `enable.auto.commit=true` (default)
- `auto.commit.interval.ms=5000` (default: 5 seconds)
- **Risk**: If consumer crashes between processing and auto-commit, messages may be reprocessed
- **Use case**: When duplicate processing is acceptable (at-least-once)

**Manual Commit:**
- `enable.auto.commit=false`
- Two methods:
  - **`commitSync()`**: blocks until offset is committed; guaranteed commit
  - **`commitAsync()`**: non-blocking; faster but may fail silently
- **Best practice**: Commit after processing is complete (e.g., after DB write)
- **Use case**: When you need exactly-once or want to control when commits happen

### Offset Reset Behavior

**When does reset occur?**
- Consumer group has **no committed offset** for a partition
- Committed offset is **out of range** (e.g., data was deleted due to retention)

**`auto.offset.reset` values:**
- **`earliest`**: Start from the beginning of the partition (offset 0 or earliest available)
- **`latest`**: Start from the end (only new messages after consumer starts)
- **`none`**: Throw exception if no offset found (fail fast)

### Offset Storage

- Offsets stored in **`__consumer_offsets`** topic (internal Kafka topic)
- Key: `group.id` + `topic` + `partition`
- Value: committed offset
- Compacted topic (keeps latest offset per key)

### Manual Offset Management

**Seeking to a specific offset:**
```java
consumer.seek(partition, offset);  // Jump to specific offset
consumer.seekToBeginning(partitions);  // Start from beginning
consumer.seekToEnd(partitions);  // Start from end
```

**When to use:**
- Replay historical data
- Skip corrupted messages
- Reset consumer position

### Offset Commit Timing

**Automatic commit timing:**
- Commits happen **periodically** (every 5s) or **before rebalancing**
- **Problem**: May commit before message is fully processed → duplicates on crash

**Manual commit timing:**
- **After processing**: Commit after successful processing (e.g., after DB write)
- **Before rebalancing**: Commit before partition is reassigned
- **Batch processing**: Commit after processing a batch

### Offset-Related Issues

| Issue | Cause | Solution |
|-------|-------|----------|
| **Duplicate processing** | Auto-commit before processing completes | Use manual commit after processing |
| **Lost messages** | Offset committed but processing failed | Commit only after successful processing |
| **Starting from wrong place** | No offset or wrong `auto.offset.reset` | Set appropriate reset policy |
| **High consumer lag** | Slow processing or too few consumers | Scale consumers or optimize processing |

### Spring Kafka Offset Management

**Spring Kafka default:**
- Uses **automatic commit** by default
- Can configure via `spring.kafka.consumer.enable-auto-commit`

**Manual commit in Spring Kafka:**
```java
@KafkaListener(topics = "my-topic", groupId = "my-group")
public void listen(ConsumerRecord<String, String> record, 
                   Acknowledgment ack) {
    // Process message
    processMessage(record);
    // Manually commit
    ack.acknowledge();
}
```

**Configuration:**
```yaml
spring:
  kafka:
    consumer:
      enable-auto-commit: false  # Disable auto-commit
    listener:
      ack-mode: manual  # Use manual acknowledgment
```

**Ack modes:**
- `RECORD`: Commit after each record
- `BATCH`: Commit after each batch
- `MANUAL`: Application calls `ack.acknowledge()`
- `MANUAL_IMMEDIATE`: Commit immediately on ack

---

## 5. Event-Driven Microservices with Kafka

### Why Event-Driven?
- **Asynchronous** — no blocking; services react to events
- **Loose coupling** — services don’t call each other directly
- **Scalability** — consumers scale independently
- **Resilience** — buffer in Kafka if a service is down
- **Audit** — events stored; replay possible

### Patterns
- **Event-driven architecture**: services produce/consume events via Kafka
- **Event sourcing**: state changes stored as events; state rebuilt by replaying
- **CQRS** (Command Query Responsibility Segregation): separate write (commands) and read (queries) models; Kafka often used for sync
- **Saga**: distributed transaction across services; each step publishes events; compensating actions on failure

---

## 6. Spring Boot + Kafka (Java)

### Dependencies
```xml
<dependency>
    <groupId>org.springframework.kafka</groupId>
    <artifactId>spring-kafka</artifactId>
</dependency>
```

### Producer — KafkaTemplate
- Main API for sending messages
- `send(topic, key, value)` or `send(topic, value)`
- Returns `CompletableFuture` (async) or use `send().get()` for sync
- Serializers: typically `StringSerializer`, `JsonSerializer`, or custom

### Consumer — @KafkaListener
```java
@KafkaListener(topics = "my-topic", groupId = "my-group")
public void listen(String message) { ... }

@KafkaListener(topics = "my-topic", groupId = "my-group")
public void listen(ConsumerRecord<String, String> record) {
    String key = record.key();
    String value = record.value();
    long offset = record.offset();
    int partition = record.partition();
}
```

### Configuration (application.yml)
```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9092
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
      acks: all
    consumer:
      group-id: my-app
      auto-offset-reset: earliest
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
```

### Key Annotations / Classes
- `@KafkaListener` — marks a method as a Kafka consumer
- `KafkaTemplate` — producer API
- `ProducerRecord` / `ConsumerRecord` — represent messages
- `@SendTo` — reply to a topic (e.g. request-reply)

---

## 7. Error Handling & Resilience

- **Retries**: producer/consumer retries; avoid infinite retries without backoff
- **Dead Letter Queue (DLQ)**: failed messages sent to a separate topic for later handling
- **Consumer**: ` SeekToCurrentErrorHandler` or `DefaultErrorHandler` (Spring Kafka) to skip or send to DLQ
- **Idempotent consumers**: process same message twice safely (e.g. check DB before applying)

---

## 8. Exactly-Once Semantics

- **Producer**: idempotent producer + `acks=all` + optional transactions
- **Consumer**: read committed + store consumed offsets in same transaction as side effects (e.g. DB)
- **Kafka Streams**: built-in exactly-once with transactional state

---

## 9. Retention & Compaction

- **Retention**: time-based (e.g. 7 days) or size-based; old data deleted
- **Log compaction**: keeps latest value per key; useful for **changelog**-style topics (e.g. cache sync)
- **Compact + delete**: retention policy can still remove old compacted records after a period

---

## 10. Quick Comparison: Kafka vs Traditional MQ

| Aspect | Kafka | Traditional MQ (e.g. RabbitMQ) |
|--------|--------|----------------------------------|
| Model | Log / stream; retain events | Queue; delete after consume |
| Ordering | Per partition | Per queue (typically) |
| Replay | Yes, via offsets | No (messages acked and removed) |
| Scaling | Partition-based | Multiple consumers on queue |
| Use case | Event streaming, analytics, event sourcing | Task queues, RPC-style |

---

## 11. Terms to Remember

- **Zookeeper** (older) / **KRaft** (newer): cluster coordination; exam may mention either
- **Schema Registry**: stores Avro/JSON schemas; used for serialization compatibility (Confluent ecosystem)
- **Exactly-once**: produce + consume without duplicates or gaps
- **At-most-once**: may lose messages; at-least-once: may duplicate
- **Consumer lag**: difference between latest offset in partition and consumer’s committed offset; metric for “how far behind” a consumer is

---

*Use this guide with PRACTICE-QUESTIONS.md and CHEAT-SHEET.md for full exam prep.*
