# Kafka System Design - Quick Reference

**For 15-minute exam response**

---

## 1. Message Retention Policies & Fault Tolerance

**Key Points:**
- **Time-based**: `retention.ms` (e.g., 7 days) - messages available for recovery window
- **Size-based**: `retention.bytes` - limits disk usage
- **Fault Tolerance**: Failed consumers can replay from last committed offset if within retention period
- **Example**: Consumer crashes, recovers within 7 days → can process all missed messages

**One-liner:** Retention provides a recovery window; consumers can replay events if they fail and recover within the retention period.

---

## 2. Consumer Group Dynamics & Rebalancing

**Key Points:**
- **Partition Assignment**: Each partition consumed by exactly one consumer in group
- **Rebalancing Triggers**: Consumer joins/leaves, partition count changes
- **Process**: Stop → Revoke → Assign → Commit offsets → Resume
- **Fault Tolerance**: Failed consumers detected; partitions reassigned to healthy consumers automatically
- **Best Practice**: Use Cooperative Sticky Assignor for minimal disruption

**One-liner:** Consumer groups automatically reassign partitions when consumers fail, ensuring continuous processing with brief pause during rebalancing.

---

## 3. Offset Management & Failure Recovery

**Key Points:**
- **Automatic Commit**: `enable.auto.commit=true` - commits every 5s (risk of duplicates on crash)
- **Manual Commit**: `enable.auto.commit=false` - commit after processing (better control)
- **Recovery**: Consumer resumes from last committed offset
- **Idempotent Processing**: Handle duplicates safely (check before apply)
- **DLQ**: Dead Letter Queue for permanent failures

**One-liner:** Manual offset commits after processing ensure no data loss; idempotent processing handles duplicates from at-least-once delivery.

---

## 4. Replication Factor & Durability

**Key Points:**
- **RF = 3**: Standard (1 leader + 2 followers)
- **ISR**: In-Sync Replicas - replicas caught up with leader
- **Durability**: `acks=all` + `min.insync.replicas=2` → message written to all ISR before commit
- **Availability**: Automatic leader election if leader fails
- **Tolerance**: Can survive N-1 broker failures (RF=3 → survive 2 failures)

**One-liner:** Replication factor ensures durability (messages survive broker failures) and availability (automatic leader election).

---

## 5. Autoscaling Strategies

**Key Points:**
- **Primary Metric**: Consumer lag (messages behind)
- **Scale Up**: Lag > threshold (e.g., 50K messages) → add consumers
- **Scale Down**: Lag < threshold for sustained period → remove consumers
- **Constraint**: Max consumers = number of partitions
- **Best Practice**: Gradual scaling, graceful shutdown, partition-aware design

**One-liner:** Autoscale consumers based on consumer lag; scale up quickly to handle load, scale down slowly to avoid thrashing.

---

## Exam Response Structure (15 minutes)

### Introduction (1 min)
- State the requirement: 100M+ messages/hour, fault-tolerant
- Mention key components: Kafka cluster, consumer groups, autoscaling

### Five Sections (2-3 min each)

**1. Retention Policies:**
- Time-based (7 days) and size-based retention
- How it enables recovery and replay
- Example scenario

**2. Consumer Groups:**
- Partition assignment (1 partition = 1 consumer in group)
- Rebalancing process and triggers
- Automatic failover on consumer failure

**3. Offset Management:**
- Manual vs automatic commit
- Recovery from last committed offset
- Idempotent processing for duplicates

**4. Replication:**
- RF=3, ISR, leader-follower model
- `acks=all` for durability
- Automatic leader election

**5. Autoscaling:**
- Consumer lag as primary metric
- Scale up/down thresholds
- Partition count constraint

### Conclusion (1 min)
- Summarize how all five aspects work together
- Emphasize fault tolerance and scalability

---

## Key Numbers to Remember

- **100M messages/hour** = ~27,778 messages/second
- **Retention**: 7 days (604800000 ms)
- **Replication Factor**: 3 (standard)
- **Min ISR**: 2 (allows 1 replica down)
- **Auto-commit interval**: 5 seconds (default)
- **Consumer lag threshold**: 50K messages (scale up)
- **Max consumers**: = number of partitions

---

## Quick Configuration Snippet

```yaml
# Topic
partitions: 100
replication.factor: 3
retention.ms: 604800000

# Producer
acks: all
enable.idempotence: true

# Consumer
enable.auto.commit: false
auto.offset.reset: earliest
ack-mode: BATCH
```

---

**Use this with SOLUTION.md for detailed explanations!**
