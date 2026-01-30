---

### 3.1 Concrete Example: Understanding Shard Limits

**Scenario**: You have a `quotes-stream` with **2 shards**, processing stock price updates.

#### **Write Limits: 1 MB/sec OR 1,000 records/sec (whichever hits first)**

**Example 1: Record count limit hits first**
- Each record = 200 bytes (small JSON: `{"symbol":"AAPL","price":150.25}`)
- You send 1,200 records/sec to Shard 1
- **Result**: ❌ **THROTTLED** at 1,000 records/sec
- Even though 1,200 × 200 bytes = 240 KB/sec (< 1 MB), the **record count limit** applies first

**Example 2: Size limit hits first**
- Each record = 2 KB (larger JSON with more fields)
- You send 600 records/sec to Shard 1
- **Result**: ❌ **THROTTLED** at 1 MB/sec
- 600 × 2 KB = 1.2 MB/sec (> 1 MB), so the **size limit** applies first

**Example 3: Both limits OK**
- Each record = 500 bytes
- You send 1,500 records/sec to Shard 1
- **Result**: ❌ **THROTTLED** at 1,000 records/sec
- Even though 1,500 × 500 bytes = 750 KB/sec (< 1 MB), the **record count** is exceeded

**Key Point**: Kinesis enforces **both** limits simultaneously. You hit whichever limit comes first.

---

#### **Read Limits: 2 MB/sec per shard (shared across consumers)**

**Setup**: Shard 1 has records totaling 2 MB/sec being written.

**Standard Consumer Model** (what Lambda uses):
- **Consumer A** (Lambda function) reads from Shard 1
- **Consumer B** (another Lambda or KCL app) also reads from Shard 1
- **Total read capacity**: 2 MB/sec **shared** between A and B
- If A reads 1.5 MB/sec, B can only read 0.5 MB/sec
- If A reads 2 MB/sec, B gets **throttled** (0 MB/sec)

**Enhanced Fan-Out** (different model, not used with Lambda):
- Each consumer gets dedicated 2 MB/sec
- More expensive, used for high-throughput scenarios

---

#### **Ordering: Records within a shard are ordered**

**Scenario**: Stock symbol `"AAPL"` is your partition key → all AAPL records go to Shard 1

**Timeline**:
```
10:00:00.100 - Record 1: AAPL @ $150.00 (seq: 1001)
10:00:00.200 - Record 2: AAPL @ $150.25 (seq: 1002)
10:00:00.300 - Record 3: AAPL @ $150.50 (seq: 1003)
```

**Lambda reads from Shard 1**:
- ✅ **Guaranteed order**: Always receives 1001 → 1002 → 1003
- ✅ **No out-of-order**: Even if network delays occur, Kinesis maintains sequence
- ✅ **Per-symbol ordering**: Since all AAPL records go to same shard, price updates are ordered

**What if AAPL records went to different shards?**
- ❌ **No ordering guarantee**: Shard 1 might have $150.00, Shard 2 might have $150.25
- Lambda processes shards independently → could see $150.25 before $150.00
- **Solution**: Use `"AAPL"` as partition key → always same shard → ordering preserved

---

#### **Lambda: One invocation per shard at a time**

**Setup**: Your stream has 2 shards (Shard 1, Shard 2)

**Normal Operation**:
```
Time 10:00:00
├─ Shard 1: Lambda invocation #1 processes batch [1001, 1002, 1003]
└─ Shard 2: Lambda invocation #2 processes batch [2001, 2002]

Time 10:00:01 (while #1 and #2 still running)
├─ Shard 1: ⏳ Waiting... (invocation #1 still processing)
└─ Shard 2: ⏳ Waiting... (invocation #2 still processing)

Time 10:00:02 (after #1 and #2 complete)
├─ Shard 1: Lambda invocation #3 processes batch [1004, 1005]
└─ Shard 2: Lambda invocation #4 processes batch [2003, 2004]
```

**Key Points**:
- ✅ **Parallel processing**: Different shards = different Lambda invocations (can run simultaneously)
- ✅ **Sequential per shard**: Same shard = one Lambda at a time (prevents race conditions)
- ✅ **Batching**: Each invocation gets multiple records (e.g., 100 records per batch)
- ✅ **Trigger**: Lambda is invoked automatically when new records arrive

**What if Lambda is slow?**
- If Shard 1's Lambda takes 5 seconds to process, new records accumulate
- Kinesis waits for Lambda to finish before sending next batch
- **Solution**: Optimize Lambda (faster processing) or increase shards (more parallelism)

**Configuration**:
- **Batch size**: 100–10,000 records per invocation
- **Concurrent executions**: Can be increased (e.g., 10 concurrent per shard), but default is 1 per shard
- **Reserved concurrency**: Limits total Lambda invocations across all shards

---

#### **Complete Example: Stock Quotes Stream**

**Stream**: `quotes-stream` with **3 shards**

**Producers write**:
- AAPL quotes → Shard 1 (partition key: `"AAPL"`)
- MSFT quotes → Shard 2 (partition key: `"MSFT"`)
- GOOGL quotes → Shard 3 (partition key: `"GOOGL"`)

**Write throughput**:
- Shard 1: 800 records/sec × 300 bytes = 240 KB/sec ✅ (under both limits)
- Shard 2: 1,200 records/sec × 200 bytes = 240 KB/sec ❌ (exceeds 1,000 records/sec limit)
- Shard 3: 400 records/sec × 3 KB = 1.2 MB/sec ❌ (exceeds 1 MB/sec limit)

**Lambda consumers**:
- **Lambda-A** reads from Shard 1: Processes AAPL quotes in order
- **Lambda-B** reads from Shard 2: Processes MSFT quotes in order
- **Lambda-C** reads from Shard 3: Processes GOOGL quotes in order
- All 3 Lambdas run **in parallel** (different shards)

**Read throughput**:
- Each shard can read up to 2 MB/sec
- Lambda-A reads 240 KB/sec from Shard 1 ✅
- Lambda-B reads 240 KB/sec from Shard 2 ✅
- Lambda-C reads 1.2 MB/sec from Shard 3 ✅
- All under the 2 MB/sec read limit per shard

**Ordering guarantee**:
- AAPL price sequence: $150.00 → $150.25 → $150.50 ✅ (all in Shard 1, ordered)
- MSFT price sequence: $300.00 → $300.10 → $300.20 ✅ (all in Shard 2, ordered)
- But: AAPL $150.50 might be processed before MSFT $300.00 (different shards, no cross-shard ordering)