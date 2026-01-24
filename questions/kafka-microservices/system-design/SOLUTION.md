# Kafka System Design Solution

**High-Throughput, Fault-Tolerant Data Ingestion Pipeline**  
**Requirement:** 100+ million messages/hour (~27,778 messages/second)

---

## System Overview

### Architecture Components
- **Kafka Cluster**: Multi-broker setup with replication
- **Producers**: High-throughput producers with batching
- **Consumer Groups**: Horizontally scalable consumers
- **Monitoring**: Consumer lag, throughput, error rates
- **Autoscaling**: Dynamic consumer scaling based on metrics

---

## 1. Kafka's Message Retention Policies and Fault Tolerance

### Retention Policies

**Time-Based Retention:**
- **Configuration**: `retention.ms` (e.g., 7 days = 604800000 ms)
- **Purpose**: Messages remain available for a specified duration
- **Fault Tolerance Benefit**: If consumers fail and recover within the retention window, they can replay missed messages

**Size-Based Retention:**
- **Configuration**: `retention.bytes` (e.g., 1 TB per partition)
- **Purpose**: Limits disk usage while maintaining recent data
- **Fault Tolerance Benefit**: Ensures sufficient buffer for recovery scenarios

**Combined Strategy:**
```yaml
# Recommended configuration
retention.ms: 604800000  # 7 days
retention.bytes: 1073741824000  # 1 TB per partition
```

### How Retention Supports Fault Tolerance

1. **Recovery Window**: Failed consumers can recover and process messages from their last committed offset, as long as data hasn't been deleted
2. **Replay Capability**: Enables reprocessing of events for debugging, reprocessing after bug fixes, or handling downstream system failures
3. **Consumer Lag Tolerance**: High consumer lag is acceptable if retention is sufficient; consumers can catch up without data loss
4. **Disaster Recovery**: Provides a buffer for extended outages

**Example Scenario:**
- Consumer crashes at 2:00 PM
- Last committed offset: 1,000,000
- Consumer recovers at 4:00 PM
- With 7-day retention, consumer can resume from offset 1,000,000 and process all missed messages

---

## 2. Consumer Group Dynamics: Partition Assignment and Rebalancing

### Consumer Group Fundamentals

**Partition Assignment:**
- Each partition is consumed by **exactly one consumer** in a consumer group
- Multiple consumers in a group share partitions for parallel processing
- Maximum parallelism = number of partitions (cannot exceed)

**Assignment Strategies:**
- **Range**: Assigns consecutive partitions to consumers (default)
- **Round-Robin**: Distributes partitions evenly
- **Sticky**: Minimizes partition movement during rebalancing (best for stability)
- **Cooperative Sticky**: Incremental rebalancing (preferred in modern Kafka)

### Rebalancing Behavior

**When Rebalancing Occurs:**
1. Consumer joins the group
2. Consumer leaves the group (graceful shutdown or crash)
3. Partition count changes
4. Subscription changes

**Rebalancing Process:**
1. **Stop-the-World Phase**: All consumers stop processing
2. **Partition Revocation**: Consumers release their partitions
3. **Partition Assignment**: Coordinator assigns partitions to consumers
4. **Offset Commit**: Offsets are committed before reassignment
5. **Resume Processing**: Consumers resume from committed offsets

**Impact on Fault Tolerance:**
- **Automatic Recovery**: Failed consumers are detected, and their partitions are reassigned to healthy consumers
- **No Data Loss**: Offsets are committed before rebalancing
- **Processing Pause**: Brief pause during rebalancing (minimize with sticky assignment)

### Best Practices for Rebalancing

```java
// Use sticky partitioner to minimize rebalancing
properties.put(ConsumerConfig.PARTITION_ASSIGNMENT_STRATEGY_CONFIG, 
    "org.apache.kafka.clients.consumer.CooperativeStickyAssignor");

// Graceful shutdown to reduce rebalancing time
consumer.close(Duration.ofSeconds(30));
```

**Optimization:**
- Use **Cooperative Sticky Assignor** for incremental rebalancing
- Implement graceful shutdown (allow in-flight processing to complete)
- Monitor rebalancing frequency and duration

---

## 3. Consumer Offset Management: Commit Strategies and Failure Recovery

### Offset Commit Strategies

#### Automatic Commit (Default)
```yaml
enable.auto.commit: true
auto.commit.interval.ms: 5000  # Commit every 5 seconds
```

**Pros:**
- Simple, no code changes needed
- Low overhead

**Cons:**
- Risk of duplicate processing if consumer crashes between processing and commit
- Less control over commit timing

**Use Case:** When duplicate processing is acceptable (idempotent operations)

#### Manual Commit (Recommended for Critical Systems)

**BATCH Mode (Recommended):**
```yaml
spring:
  kafka:
    consumer:
      enable-auto-commit: false
    listener:
      ack-mode: BATCH  # Commit after processing each batch
```

**MANUAL Mode (Maximum Control):**
```java
@KafkaListener(topics = "high-throughput-topic", groupId = "processor-group")
public void processBatch(List<ConsumerRecord<String, String>> records,
                        Acknowledgment ack) {
    try {
        // Process all records in batch
        for (ConsumerRecord<String, String> record : records) {
            processMessage(record);
        }
        
        // Commit only after successful processing
        ack.acknowledge();  // Commits entire batch
    } catch (Exception e) {
        // Don't acknowledge → messages will be reprocessed
        // Or send to Dead Letter Queue
        handleError(records, e);
    }
}
```

### Failure Recovery Mechanisms

**1. Offset-Based Recovery:**
- Consumer resumes from last committed offset
- Processes all messages since last commit
- Requires idempotent processing to handle duplicates

**2. Idempotent Processing:**
```java
public void processMessage(ConsumerRecord<String, OrderEvent> record) {
    String messageId = record.headers().lastHeader("idempotency-key").value();
    
    // Check if already processed
    if (orderRepository.existsById(messageId)) {
        log.info("Message already processed: {}", messageId);
        return;  // Skip duplicate
    }
    
    // Process and store
    orderRepository.save(record.value());
}
```

**3. Dead Letter Queue (DLQ):**
- Failed messages sent to separate topic
- Prevents blocking main processing flow
- Enables manual inspection and retry

**4. Offset Reset on Failure:**
```yaml
auto.offset.reset: earliest  # Start from beginning if no offset
# OR
auto.offset.reset: latest    # Start from end (only new messages)
```

### Recommended Offset Management Strategy

For **100M messages/hour** with fault tolerance:

```yaml
spring:
  kafka:
    consumer:
      enable-auto-commit: false
      auto-offset-reset: earliest  # Safety: don't skip messages
    listener:
      ack-mode: BATCH  # Balance between control and performance
      concurrency: 10  # Multiple consumer threads per instance
```

**Commit Strategy:**
- Commit after successful batch processing
- Use idempotent processing to handle duplicates
- Implement DLQ for permanent failures
- Monitor consumer lag to detect issues early

---

## 4. Replication Factor: Durability and Availability

### Replication Factor Explained

**Replication Factor (RF):**
- Number of copies of each partition across brokers
- **RF = 3** is industry standard (1 leader + 2 followers)
- Each partition has one **leader** and N-1 **followers**

### How Replication Ensures Durability

**Leader-Follower Model:**
1. Producer writes to **leader** partition
2. Leader replicates to **followers** (in-sync replicas)
3. Message is **committed** when written to all ISR replicas (with `acks=all`)

**ISR (In-Sync Replicas):**
- Replicas that are fully caught up with the leader
- Only ISR replicas can become leaders
- If follower lags too much, it's removed from ISR

### Durability Guarantees

**With `acks=all` and RF=3:**
- Message is durable if at least one replica survives
- Can tolerate **N-1 broker failures** (with RF=3, can lose 2 brokers)
- Zero data loss if failures are within tolerance

**Configuration:**
```yaml
# Topic configuration
replication.factor: 3
min.insync.replicas: 2  # Minimum ISR required for writes

# Producer configuration
acks: all  # Wait for all ISR replicas
```

### Availability Benefits

**Automatic Leader Election:**
- If leader fails, a follower in ISR becomes the new leader
- **Zero downtime** for consumers (brief pause during election)
- Transparent to producers (automatic retry)

**Example Scenario:**
- RF = 3, min.insync.replicas = 2
- Broker 1 (leader) fails
- Broker 2 (follower) automatically becomes leader
- System continues operating with RF = 2
- When Broker 1 recovers, it syncs and rejoins ISR

### Recommended Replication Strategy

For **high-throughput, fault-tolerant system:**

```yaml
# Topic creation
replication.factor: 3
min.insync.replicas: 2

# Producer
acks: all
retries: 2147483647  # Retry indefinitely
enable.idempotence: true  # Prevent duplicates

# Broker configuration
unclean.leader.election.enable: false  # Only elect ISR replicas
```

**Trade-offs:**
- **RF = 3**: Good balance of durability and cost
- **RF = 5**: Higher durability, more storage cost
- **min.insync.replicas = 2**: Allows one replica to be down without blocking writes

---

## 5. Autoscaling Strategies: Dynamic Consumer Scaling

### Autoscaling Principles

**Key Metrics for Autoscaling:**
1. **Consumer Lag**: Primary metric (messages behind)
2. **Throughput**: Messages processed per second
3. **Processing Time**: Time to process a message
4. **Error Rate**: Failed message processing rate

### Autoscaling Triggers

#### Scale Up (Add Consumers)
- **Consumer lag > threshold** (e.g., > 100,000 messages)
- **Lag growth rate > threshold** (e.g., increasing by 10K/sec)
- **CPU/Memory utilization > 80%** (if resource-bound)

#### Scale Down (Remove Consumers)
- **Consumer lag < threshold** (e.g., < 10,000 messages)
- **Lag decreasing consistently** (e.g., for 5 minutes)
- **Low resource utilization** (e.g., < 30% CPU)

### Autoscaling Implementation

#### 1. Kubernetes Horizontal Pod Autoscaler (HPA)

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: kafka-consumer-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: kafka-consumer
  minReplicas: 3
  maxReplicas: 20
  metrics:
  - type: External
    external:
      metric:
        name: kafka_consumer_lag
      target:
        type: AverageValue
        averageValue: "50000"  # Scale when lag > 50K
  behavior:
    scaleUp:
      stabilizationWindowSeconds: 60
      policies:
      - type: Percent
        value: 50  # Increase by 50% at a time
        periodSeconds: 60
    scaleDown:
      stabilizationWindowSeconds: 300  # Wait 5 min before scaling down
      policies:
      - type: Percent
        value: 25  # Decrease by 25% at a time
        periodSeconds: 120
```

#### 2. Custom Autoscaler with Kafka Metrics

```java
@Service
public class KafkaConsumerAutoscaler {
    
    @Autowired
    private KafkaAdminClient kafkaAdmin;
    
    @Scheduled(fixedRate = 30000)  // Check every 30 seconds
    public void scaleConsumers() {
        Map<TopicPartition, Long> lag = getConsumerLag();
        long totalLag = lag.values().stream().mapToLong(Long::longValue).sum();
        
        int currentReplicas = getCurrentConsumerReplicas();
        int targetReplicas = calculateTargetReplicas(totalLag, currentReplicas);
        
        if (targetReplicas != currentReplicas) {
            scaleDeployment(targetReplicas);
        }
    }
    
    private int calculateTargetReplicas(long totalLag, int currentReplicas) {
        long lagPerConsumer = 10000;  // Target: 10K messages per consumer
        int idealReplicas = (int) Math.ceil((double) totalLag / lagPerConsumer);
        
        // Respect min/max bounds
        return Math.max(3, Math.min(20, idealReplicas));
    }
}
```

#### 3. Partition-Aware Scaling

**Important Constraint:**
- **Maximum consumers = number of partitions**
- Scaling beyond partition count provides no benefit
- Design topics with sufficient partitions for peak load

**Example:**
- Topic has **50 partitions**
- Maximum effective consumers = 50
- If scaling to 100 consumers, 50 will be idle

**Recommendation:**
- **Partitions = 2-3x expected peak consumers**
- For 100M messages/hour: ~100-150 partitions (assuming 10-15 consumers at peak)

### Autoscaling Best Practices

**1. Gradual Scaling:**
- Scale up quickly (respond to lag)
- Scale down slowly (avoid thrashing)
- Use stabilization windows

**2. Graceful Shutdown:**
```java
@PreDestroy
public void gracefulShutdown() {
    consumer.close(Duration.ofSeconds(30));  // Allow in-flight processing
}
```

**3. Monitor Rebalancing:**
- Track rebalancing frequency and duration
- Minimize rebalancing with sticky partition assignment
- Scale during low-traffic periods when possible

**4. Health Checks:**
```yaml
livenessProbe:
  httpGet:
    path: /actuator/health
    port: 8080
  initialDelaySeconds: 60
  periodSeconds: 10

readinessProbe:
  httpGet:
    path: /actuator/health/readiness
    port: 8080
  initialDelaySeconds: 30
  periodSeconds: 5
```

### Complete Autoscaling Strategy

**For 100M messages/hour:**

1. **Initial Setup:**
   - 10 consumers (can handle ~10M messages/hour each)
   - 100 partitions (allows scaling to 100 consumers)

2. **Scale-Up Threshold:**
   - Lag > 50,000 messages → add 2 consumers
   - Lag > 200,000 messages → add 5 consumers
   - Maximum: 50 consumers (50% of partitions)

3. **Scale-Down Threshold:**
   - Lag < 10,000 messages for 5 minutes → remove 1 consumer
   - Minimum: 3 consumers (fault tolerance)

4. **Monitoring:**
   - Consumer lag dashboard
   - Rebalancing alerts
   - Throughput metrics

---

## Complete System Design Summary

### Architecture Diagram (Text)

```
[Producers] → [Kafka Cluster (3 brokers, RF=3)]
                    ↓
            [Topic: 100 partitions, 7-day retention]
                    ↓
        [Consumer Group: 10-50 instances]
                    ↓
            [Processing Layer]
                    ↓
        [Database / Downstream Systems]
```

### Key Configuration Summary

```yaml
# Topic Configuration
partitions: 100
replication.factor: 3
retention.ms: 604800000  # 7 days
min.insync.replicas: 2

# Producer Configuration
acks: all
enable.idempotence: true
batch.size: 32768
linger.ms: 10

# Consumer Configuration
group.id: high-throughput-processor
enable.auto.commit: false
auto.offset.reset: earliest
max.poll.records: 500
fetch.min.bytes: 1024

# Spring Kafka Listener
ack-mode: BATCH
concurrency: 10  # Threads per instance
```

### Fault Tolerance Checklist

✅ **Message Retention**: 7-day retention ensures recovery window  
✅ **Replication**: RF=3 with min.insync.replicas=2  
✅ **Offset Management**: Manual commit after processing  
✅ **Idempotent Processing**: Handle duplicates safely  
✅ **Consumer Groups**: Automatic partition reassignment on failure  
✅ **Autoscaling**: Dynamic scaling based on consumer lag  
✅ **Dead Letter Queue**: Handle permanent failures  
✅ **Monitoring**: Consumer lag, throughput, error rates  

---

## Conclusion

This design ensures **high throughput (100M+ messages/hour)**, **fault tolerance** (survives broker/consumer failures), and **scalability** (dynamic autoscaling) while minimizing data loss and processing delays through:

1. **Retention policies** providing recovery windows
2. **Consumer group rebalancing** for automatic failover
3. **Manual offset commits** for precise control
4. **Replication factor** ensuring durability
5. **Autoscaling** maintaining optimal performance

The system can handle consumer failures gracefully, with automatic recovery and minimal data loss.
