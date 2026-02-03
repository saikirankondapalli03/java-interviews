# Java ↔ Python Concurrency Primitives: Senior Systems Interview Reference

> Systems-focused correlation between Java `java.util.concurrent` and Python concurrency primitives. Assumes backend experience; skips textbook theory.

---

## Part 0: Low-Level Fundamentals

Understanding these under-the-hood concepts eliminates confusion when choosing primitives and explaining trade-offs.

### CPU-Bound vs I/O-Bound

**CPU-bound (compute-limited):**

- The thread spends most of its time **executing CPU instructions** (arithmetic, branches, memory access).
- Cores are actively crunching; little time waiting.
- **Bottleneck:** Number of CPU cores and instructions per second.
- **Concurrency strategy:** True parallelism; more threads than cores = context-switch overhead, little gain.
- **Examples:** Crypto hashing, JSON parsing, compression, image processing, matrix math.

**I/O-bound (wait-limited):**

- The thread spends most of its time **waiting**—network round-trip, disk read, DB query, lock acquisition.
- Cores are often idle; the CPU issued the request and is blocked until data arrives.
- **Bottleneck:** Latency of external systems (network, disk), not raw CPU speed.
- **Concurrency strategy:** Many threads/tasks can share few cores; when one blocks, another runs.
- **Examples:** HTTP API calls, DB queries, file I/O, Kafka consume, gRPC.

**Why it matters:** Pick the right tool. CPU-bound → `ForkJoinPool`, `ProcessPoolExecutor`, threads ≈ cores. I/O-bound → thread pools, `asyncio`, many concurrent tasks.

---

### Work Stealing — What Actually Happens

**Problem:** With divide-and-conquer (e.g., recursive tasks), some threads finish early; others have large backlogs. Without stealing, cores sit idle.

**Mechanics:**

1. **Each worker has its own deque** (double-ended queue).
   - Push/pop from the **head** for its own tasks (LIFO = good cache locality).
   - Steal from the **tail** of another worker's deque.

2. **Normal case:** Worker pushes new task to its head, pops from head when ready. No contention.

3. **Steal case:** Worker's deque is empty → pick another worker, steal from the **tail** of its deque. If successful, run that task (subtasks go to your deque).

4. **Why tail for stealing?** Head = most recent, often smaller/dependent work. Tail = older, larger, more independent. Stealing from tail minimizes contention and keeps good locality for the owner.

**Mental model:**

```
Worker 0: [task0][task1][task2][task3]  ← head (own pop) ... tail (others steal)
Worker 1: []  ← empty, steals from Worker 0's tail
Worker 2: [taskA]  ← busy
```

**Cost:** Stealing has overhead (CAS, cache coherency). Worth it for large recursive workloads; overkill for small or I/O-heavy tasks.

---

### Context Switching — Why More Threads ≠ Faster (for CPU Work)

**What it is:** The OS saves one thread's state (registers, program counter, stack) and loads another's so a different thread can run on the same core.

**When it happens:** Context switching occurs in **both** I/O and CPU scenarios: (1) **CPU:** the scheduler preempts a running thread (time slice expired, higher-priority thread ready) and runs another; (2) **I/O:** a thread blocks on I/O (e.g. `read()`, `recv()`), the kernel deschedules it and switches to a runnable thread. The same mechanism—save current state, load another—applies in both cases.

**Cost:** Registers/TLB save-restore, cache pollution (new thread touches different memory), kernel transitions. Roughly microseconds per switch; under load, a large share of CPU time can be spent switching.

**Implication:**

- **CPU-bound, threads = cores:** Little switching; cores stay busy.
- **CPU-bound, threads >> cores:** Constant switching; each thread runs in tiny bursts; throughput degrades.
- **I/O-bound:** Switching is expected and beneficial—threads block on I/O and free the core; context switches are what let other threads run instead of leaving cores idle.

---

### Cores vs Hardware Threads (Hyperthreading)

- **Physical core:** One actual execution unit.
- **Hardware thread (logical core):** Many CPUs expose 2 logical cores per physical core (SMT/hyperthreading). When one logical core stalls (cache miss, branch mispredict), the other can run.

**Rule of thumb:** For CPU-bound work, cap threads at *physical* cores or slightly above (e.g., `Runtime.getRuntime().availableProcessors()`). For I/O-bound, threads can far exceed core count.

---

### Blocking vs Non-Blocking I/O

- **Blocking:** Thread calls `read()`/`recv()` and sleeps until data arrives. Kernel deschedules the thread.
- **Non-blocking:** Thread calls `read()`, gets "would block" immediately if no data; can poll or use `select`/`epoll`/`kqueue` to wait for many FDs at once.

**Relevance:** `asyncio` and NIO use non-blocking I/O + event loop; one thread can juggle thousands of connections. Traditional thread-per-connection uses blocking I/O; each connection consumes a thread.

---

### Python GIL (Global Interpreter Lock)

- **What:** A single mutex around the Python interpreter; only one thread executes Python bytecode at a time.
- **Implication:** Multithreading in Python does **not** give true parallelism for CPU-bound code. Use `multiprocessing` or native extensions (NumPy, etc.) that release the GIL for CPU-bound work.
- **I/O-bound:** GIL is released during I/O (e.g., `socket.recv`), so threads can overlap I/O waits; multithreading helps.

---

### Quick Reference: Choose Your Model

| Workload Type | Java | Python |
|---------------|------|--------|
| CPU-bound, parallel | ForkJoinPool, parallel streams | `multiprocessing`, `ProcessPoolExecutor` |
| I/O-bound, many connections | ThreadPoolExecutor, virtual threads (21+) | `asyncio`, `ThreadPoolExecutor` |
| Mixed | Tuned thread pool, separate CPU pool | `asyncio` + `run_in_executor` for CPU bursts |

---

### One-Liners for Fundamentals

| Concept | One-Liner |
|---------|-----------|
| **CPU-bound** | "Limited by CPU; more threads than cores adds context-switch overhead." |
| **I/O-bound** | "Limited by wait time; many threads can share few cores because they block." |
| **Work stealing** | "Idle workers steal from the tail of busy workers' deques to keep cores busy." |
| **Context switch** | "OS saves/restores thread state; happens on both I/O block and CPU preemption; costs registers, cache; hurts CPU-bound throughput, fine for I/O-bound." |
| **GIL** | "Python mutex; only one thread runs bytecode; use multiprocessing for CPU-bound." |

---

## 1. Semaphore

### Core Problem
**Bounded resource access:** Limit how many threads/coroutines can hold a resource (DB connections, file handles, API slots) at once. One thread acquiring reduces availability; releasing restores it.

### Java Example

```java
import java.util.concurrent.Semaphore;

public class SemaphoreDemo {
    private static final Semaphore DB_POOL = new Semaphore(10);

    public static void queryDb(String task) throws InterruptedException {
        DB_POOL.acquire();  // blocks if no permits
        try {
            // use connection
        } finally {
            DB_POOL.release();
        }
    }
}
```

### Python Equivalent

```python
import threading

# BoundedSemaphore prevents release > acquire (safer than Semaphore)
DB_POOL = threading.BoundedSemaphore(10)

def query_db(task: str) -> None:
    DB_POOL.acquire()
    try:
        # use connection
    finally:
        DB_POOL.release()
```

**asyncio** (single-threaded, for I/O-bound backends):

```python
import asyncio

db_sem = asyncio.Semaphore(10)

async def query_db(task: str) -> None:
    async with db_sem:
        # use connection
```

### Guarantees, Reusability, Failure Modes

| Aspect | Java Semaphore | Python threading.Semaphore | Python asyncio.Semaphore |
|--------|----------------|---------------------------|--------------------------|
| Reusable | Yes | Yes | Yes |
| Over-release | Can exceed initial permits (bug-prone) | Same unless `BoundedSemaphore` | Same |
| Failure on release | None | `BoundedSemaphore`: ValueError if release > acquire | Same |
| Fairness | Optional `new Semaphore(10, true)` | No fairness control | N/A |

### When to Use
DB connection pools, API rate limiting (N concurrent calls), file descriptor limits, admission control in batch jobs.

### Pitfall
Releasing without acquiring (e.g., in a branch that skipped acquire) or releasing twice. **Use `BoundedSemaphore` in Python** or always release in `finally`/`try-with-resources`.

---

## 2. CountDownLatch

### Core Problem
**One-shot rendezvous:** A waiter blocks until N independent events have occurred. One or more producers count down; consumers await. Count is decremented, not incremented; latch never resets.

### Java Example

```java
import java.util.concurrent.CountDownLatch;

public class CountDownLatchDemo {
    public static void main(String[] args) throws InterruptedException {
        int workers = 5;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(workers);

        for (int i = 0; i < workers; i++) {
            int id = i;
            new Thread(() -> {
                try {
                    start.await();  // all wait for "go"
                    // do work
                } finally {
                    done.countDown();
                }
            }).start();
        }
        start.countDown();  // release all workers
        done.await();      // wait for all to finish
    }
}
```

### Python Equivalent

Python has **no built-in CountDownLatch**. Implement with `threading.Condition`:

```python
import threading

class CountDownLatch:
    def __init__(self, count: int):
        self._count = count
        self._lock = threading.Condition()

    def count_down(self) -> None:
        with self._lock:
            if self._count > 0:
                self._count -= 1
                if self._count == 0:
                    self._lock.notify_all()

    def await_latch(self, timeout: float | None = None) -> bool:
        with self._lock:
            while self._count > 0:
                if timeout is None:
                    self._lock.wait()
                else:
                    # wait() returns False on timeout, True when notified
                    if not self._lock.wait(timeout) and self._count > 0:
                        return False
            return True
```

**asyncio** (Event + shared counter or custom):

```python
import asyncio

class AsyncCountDownLatch:
    def __init__(self, count: int):
        self.count = count
        self.event = asyncio.Event()

    async def count_down(self) -> None:
        self.count -= 1
        if self.count <= 0:
            self.event.set()

    async def wait(self) -> None:
        await self.event.wait()
```

### Guarantees, Reusability, Failure Modes

| Aspect | Java CountDownLatch | Python (DIY) |
|--------|--------------------|--------------|
| Reusable | **No** | Same—count goes to 0 and stays |
| await after count=0 | Returns immediately | Same |
| countDown after count=0 | No-op, no error | DIY: can go negative if not guarded |
| Interruption | `await()` throws InterruptedException | Use `threading.Event` or `Condition` |

### When to Use
Batch pipeline startup (wait for N services ready), test harness (wait for N workers to finish), microservice orchestration (wait for N downstream calls).

### Pitfall
Reusing a latch. It’s one-shot. For repeated phases, use `CyclicBarrier` or `Phaser`. Also: over-counting (countDown more than N times) without guards.

---

## 3. CyclicBarrier

### Core Problem
**Multi-thread rendezvous:** N threads must reach a common point before any proceed. Resets after each “wave,” so it can be reused for multiple phases.

### Java Example

```java
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.BrokenBarrierException;

public class CyclicBarrierDemo {
    public static void main(String[] args) {
        int parties = 3;
        CyclicBarrier barrier = new CyclicBarrier(parties, () -> System.out.println("All arrived"));

        for (int i = 0; i < parties; i++) {
            int id = i;
            new Thread(() -> {
                try {
                    System.out.println("Worker " + id + " at barrier");
                    barrier.await();
                    System.out.println("Worker " + id + " past barrier");
                } catch (InterruptedException | BrokenBarrierException e) { /* handle */ }
            }).start();
        }
    }
}
```

### Python Equivalent

**threading.Barrier** — direct analogue to CyclicBarrier:

```python
import threading

def on_release():
    print("All arrived")

barrier = threading.Barrier(3, action=on_release)

def worker(id: int) -> None:
    print(f"Worker {id} at barrier")
    barrier.wait()
    print(f"Worker {id} past barrier")

threads = [threading.Thread(target=worker, args=(i,)) for i in range(3)]
for t in threads:
    t.start()
for t in threads:
    t.join()
```

**asyncio** (3.11+):

```python
import asyncio

barrier = asyncio.Barrier(3)

async def worker(id: int) -> None:
    print(f"Worker {id} at barrier")
    await barrier.wait()
    print(f"Worker {id} past barrier")

async def main():
    await asyncio.gather(*[worker(i) for i in range(3)])

asyncio.run(main())
```

### Guarantees, Reusability, Failure Modes

| Aspect | Java CyclicBarrier | Python threading.Barrier |
|--------|--------------------|--------------------------|
| Reusable | Yes | Yes |
| Action on release | Optional Runnable | Optional `action` |
| BrokenBarrierException | If one thread times out or is interrupted | `BrokenBarrierError` |
| After broken | Must create new barrier | `barrier.reset()` |
| Dynamic parties | No (fixed at construction) | No |

### When to Use
Parallel computation phases (e.g., map → barrier → reduce), multi-worker batch stages that must sync before next stage, simulation steps.

### Pitfall
`BrokenBarrierException` when one thread fails or times out—all waiters are released with an exception. Must handle and possibly rebuild the barrier. Also: mismatch between `parties` and number of threads calling `await()`.

---

## 4. Phaser

### Core Problem
**Multi-phase synchronization with dynamic parties:** Like a reusable barrier, but phases can be repeated and the number of registered parties can change between phases. Supports termination and tiered phase progression.

### Java Example

```java
import java.util.concurrent.Phaser;

public class PhaserDemo {
    public static void main(String[] args) {
        Phaser phaser = new Phaser(3);

        for (int i = 0; i < 3; i++) {
            int id = i;
            new Thread(() -> {
                for (int phase = 0; phase < 3; phase++) {
                    System.out.println("Worker " + id + " phase " + phase);
                    phaser.arriveAndAwaitAdvance();
                }
            }).start();
        }
    }
}
```

### Python Equivalent

**No built-in Phaser.** Approximate with `threading.Barrier` for fixed parties and phases, or DIY with `threading.Condition`:

```python
import threading

class Phaser:
    def __init__(self, parties: int):
        self.parties = parties
        self.phase = 0
        self.arrived = 0
        self.lock = threading.Condition()

    def arrive_and_await_advance(self) -> int:
        with self.lock:
            self.arrived += 1
            if self.arrived < self.parties:
                current = self.phase
                while self.phase == current:
                    self.lock.wait()
                return self.phase
            else:
                self.phase += 1
                self.arrived = 0
                self.lock.notify_all()
                return self.phase
```

**asyncio** — use `asyncio.Barrier` per phase, or a custom phase coordinator.

### Guarantees, Reusability, Failure Modes

| Aspect | Java Phaser | Python (DIY / Barrier) |
|--------|-------------|------------------------|
| Reusable | Yes, many phases | Barrier: yes per wave; DIY: needs full impl |
| Dynamic parties | `register()`, `bulkRegister()`, `arriveAndDeregister()` | DIY only |
| Termination | `arriveAndDeregister()` until 0 | DIY |
| Failure | One failure can strand others | Same as Barrier if not handled |

### When to Use
Multi-phase pipelines (load → transform → validate → write), staged simulations, phased batch ETL where workers may join/leave.

### Pitfall
Phaser is complex; misuse of `arriveAndDeregister()` can deadlock. Prefer `CyclicBarrier` when parties are fixed. Dynamic party changes require careful testing.

---

## 5. Exchanger

### Core Problem
**Pairwise swap:** Two threads meet at a point and swap objects. Symmetric; both block until the other arrives. One-shot per exchange.

### Java Example

```java
import java.util.concurrent.Exchanger;

public class ExchangerDemo {
    public static void main(String[] args) {
        Exchanger<String> ex = new Exchanger<>();

        new Thread(() -> {
            try {
                String mine = "data-A";
                String other = ex.exchange(mine);
                System.out.println("Thread1 got: " + other);
            } catch (InterruptedException e) { /* handle */ }
        }).start();

        new Thread(() -> {
            try {
                String mine = "data-B";
                String other = ex.exchange(mine);
                System.out.println("Thread2 got: " + other);
            } catch (InterruptedException e) { /* handle */ }
        }).start();
    }
}
```

### Python Equivalent

**No built-in Exchanger.** Implement with `queue.Queue(maxsize=1)` and two queues (one per direction), or a single exchange point:

```python
import threading

class Exchanger:
    """Two threads swap values. First caller blocks; second receives first's value, first receives second's."""

    def __init__(self):
        self._lock = threading.Lock()
        self._slot = None
        self._paired = False  # True when second thread has exchanged
        self._arrived = threading.Condition(self._lock)

    def exchange(self, value, timeout: float | None = None):
        with self._lock:
            if self._slot is None:
                self._slot = value
                self._paired = False
                if timeout is not None:
                    self._arrived.wait(timeout)
                else:
                    self._arrived.wait()
                if not self._paired:
                    self._slot = None
                    raise TimeoutError("exchange timed out")
                result = self._slot
                self._slot = None
                return result
            else:
                result = self._slot
                self._slot = value
                self._paired = True
                self._arrived.notify()
                return result
```

**asyncio:**

```python
import asyncio

async def async_exchange(chan_a: asyncio.Queue, chan_b: asyncio.Queue, my_val):
    await chan_a.put(my_val)
    return await chan_b.get()
```

### Guarantees, Reusability, Failure Modes

| Aspect | Java Exchanger | Python (DIY / Queue) |
|--------|----------------|----------------------|
| Reusable | Yes, per exchange | DIY: yes if state reset correctly |
| Exactly two parties | Yes | DIY: must enforce |
| Timeout | `exchange(v, timeout, unit)` | Queue.get(timeout) or Event.wait(timeout) |
| Third thread | Blocks until one of the pair uses it | DIY: undefined unless designed for it |

### When to Use
Producer–consumer pipelines where two stages swap buffers, handoff between pipeline stages, symmetric two-way handshake protocols.

### Pitfall
Exchanger is strictly for two threads. A third caller blocks indefinitely. Prefer `SynchronousQueue`/`Queue(1)` for clearer semantics when more than two participants are possible.

---

## Comparison Table: Java → Python

| Java | Python threading | Python asyncio | Notes |
|------|------------------|----------------|-------|
| `Semaphore(n)` | `threading.Semaphore(n)` / `BoundedSemaphore(n)` | `asyncio.Semaphore(n)` | Use BoundedSemaphore to avoid over-release |
| `CountDownLatch(n)` | DIY (`Condition` + count) | DIY (`Event` + count) | No stdlib equivalent |
| `CyclicBarrier(n)` | `threading.Barrier(n)` | `asyncio.Barrier(n)` (3.11+) | Direct equivalent |
| `Phaser(n)` | DIY or `Barrier` per phase | DIY or `asyncio.Barrier` | No stdlib Phaser |
| `Exchanger<V>` | DIY (Queue or Lock+Event) | DIY (`asyncio.Queue`) | No stdlib equivalent |

---

## Interview One-Liners

| Primitive | One-Liner |
|-----------|-----------|
| **Semaphore** | "Bounded permits: acquire reduces, release restores; use for pools and rate limits." |
| **CountDownLatch** | "One-shot: N events must happen before waiter proceeds; count down to 0, never resets." |
| **CyclicBarrier** | "N threads meet, then all proceed; resets after each wave—reusable rendezvous." |
| **Phaser** | "Multi-phase barrier with dynamic party registration; for phased pipelines." |
| **Exchanger** | "Two threads swap objects at a sync point; symmetric, pairwise handoff." |
| **BlockingQueue** | "Buffered producer-consumer; put blocks when full, take when empty." |
| **ForkJoinPool** | "Work-stealing pool for recursive divide-and-conquer; parallelStream uses common pool." |
| **ConcurrentHashMap** | "Lock-striped map; use compute/merge for atomic updates." |
| **SynchronousQueue** | "Zero-capacity handoff; put blocks until take." |
| **CopyOnWriteArrayList** | "Write clones array; readers never block; read-heavy only." |
| **Monitor / Lock** | "One thread in critical section; Lock/synchronized around shared state." |
| **Reader-Writer Lock** | "Many readers OR one writer; for read-heavy caches/config." |
| **Guarded Suspension** | "Wait under lock until condition holds; loop on wait." |
| **Balking** | "If not ready, return immediately—no wait." |
| **Future/Promise** | "Handle to result later; get() blocks; CompletableFuture composes." |
| **Thread Pool** | "Reuse N threads for many tasks; fixed/cached/scheduled." |
| **Thread-Local** | "Per-thread data; clear in pooled threads to avoid leaks." |
| **Immutable** | "No mutators; safe to share without locks." |
| **Double-Checked Lock** | "Lazy singleton; use volatile+sync or static holder." |
| **Atomic** | "CAS for single variable; no lock." |
| **Reactor** | "Event loop + non-blocking I/O; one thread, many connections." |
| **Active Object** | "Method calls enqueued; one thread drains—serial execution." |
| **Pipeline** | "Stages connected by queues; scale per stage." |
| **Scheduler** | "Delayed or periodic execution; ScheduledExecutorService." |

---

## Mapping to Real Systems

| System Pattern | Primitive | Why |
|----------------|-----------|-----|
| **DB connection pool** | Semaphore | Cap concurrent connections; acquire before checkout, release on return |
| **API rate limiting** | Semaphore (or token bucket) | Limit N concurrent outbound calls per client/service |
| **Batch pipeline startup** | CountDownLatch | Main orchestrator waits for N workers to be "ready" |
| **Multi-stage ETL** | CyclicBarrier / Phaser | Stage 1 completes → barrier → stage 2; repeat |
| **Producer–consumer handoff** | Exchanger / Queue | Swap buffers between stages for zero-copy or double-buffering |
| **Graceful shutdown** | CountDownLatch | Coordinator waits for N workers to signal shutdown complete |
| **Parallel phase computation** | CyclicBarrier | Map phase → barrier → reduce phase |
| **Producer-consumer (buffered)** | BlockingQueue | Standard pattern; ArrayBlockingQueue / LinkedBlockingQueue |
| **Task handoff (no buffer)** | SynchronousQueue | Cached thread pools; direct handoff |
| **Shared cache / counters** | ConcurrentHashMap | Atomic compute/merge; no full-map lock |
| **Parallel streams / recursive tasks** | ForkJoinPool | Work-stealing; parallelStream default |
| **Listeners, read-heavy lists** | CopyOnWriteArrayList | Infrequent writes, many readers |
| **Read-heavy cache / config** | Reader-Writer Lock | Many readers OR one writer |
| **High-concurrency I/O server** | Reactor (NIO / asyncio) | Event loop, non-blocking I/O |
| **Request-scoped context** | Thread-Local | Per-thread data, no locking |
| **Lazy singleton / factory** | Double-Checked Lock or static holder | One-time init, thread-safe |

---

## Part II: Concurrent Collections & Queues

Beyond coordination primitives, `java.util.concurrent` provides thread-safe collections and queues. BlockingQueue is the classic producer-consumer building block; these extend the toolkit.

### BlockingQueue (Baseline)

**Core problem:** Buffered handoff between producers and consumers; producers block when full, consumers block when empty.

**Implementations:**

| Implementation | Bounded? | Structure | Blocking? |
|----------------|----------|-----------|-----------|
| `ArrayBlockingQueue` | Yes (capacity) | Array | Yes |
| `LinkedBlockingQueue` | Optional (default unbounded) | Linked nodes | Yes |
| `SynchronousQueue` | 0 (no storage) | N/A | Yes |
| `DelayQueue` | Unbounded | Heap by delay | Yes |
| `PriorityBlockingQueue` | Unbounded | Heap by priority | Yes |
| `LinkedTransferQueue` | Unbounded | Linked nodes | Yes (also non-blocking transfer) |

---

### ForkJoinPool / ForkJoinTask

**Core problem:** Divide-and-conquer parallelism with **work-stealing**. Each worker has its own deque; idle workers steal from busy ones. Optimized for recursive, CPU-bound tasks.

**Java example:**

```java
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;

class SumTask extends RecursiveTask<Long> {
    private final int[] arr;
    private final int lo, hi;
    private static final int THRESHOLD = 1000;

    SumTask(int[] arr, int lo, int hi) { this.arr = arr; this.lo = lo; this.hi = hi; }

    @Override
    protected Long compute() {
        if (hi - lo <= THRESHOLD) {
            long sum = 0;
            for (int i = lo; i < hi; i++) sum += arr[i];
            return sum;
        }
        int mid = (lo + hi) >>> 1;
        SumTask left = new SumTask(arr, lo, mid);
        SumTask right = new SumTask(arr, mid, hi);
        left.fork();
        return right.compute() + left.join();
    }
}

// Usage: ForkJoinPool.commonPool() is used by parallelStream()
long sum = new ForkJoinPool().invoke(new SumTask(arr, 0, arr.length));
```

**Python equivalent:** `concurrent.futures.ProcessPoolExecutor` for CPU-bound; no built-in work-stealing. For I/O-bound, `ThreadPoolExecutor` or `asyncio`.

**When to use:** Parallel streams, recursive algorithms (merge sort, parallel reduce), large CPU-bound workloads.

**Pitfall:** Overhead for small tasks; use a size threshold to avoid creating too many subtasks.

**One-liner:** "Work-stealing pool for recursive divide-and-conquer; `parallelStream()` uses the common pool by default."

---

### ConcurrentHashMap

**Core problem:** Thread-safe map without locking the entire structure. Uses lock striping (pre-Java 8) or CAS + tree bins (Java 8+). Iteration is weakly consistent.

**Key methods:** `computeIfAbsent`, `computeIfPresent`, `merge`, `getOrDefault` — use these for atomic updates instead of get-then-put.

```java
// Atomic increment
map.merge(key, 1, Long::sum);

// Atomic get-or-compute
map.computeIfAbsent(key, k -> expensiveLoad(k));
```

**Python equivalent:** No lock-striped map in stdlib. Use `threading.Lock` around a dict, or `multiprocessing.Manager().dict()` for process-safe. Third-party: libraries like `concurrent-futures` don't provide this; `queue` + single writer is common.

**When to use:** Shared caches, counters, configuration maps updated by multiple threads.

**Pitfall:** `computeIfAbsent` lambda must not modify the map (can cause `ConcurrentModificationException` or deadlock). `size()` is approximate.

**One-liner:** "Lock-striped map; use compute/merge for atomic updates, never get-then-put loops."

---

### SynchronousQueue

**Core problem:** Zero-capacity handoff. A `put` blocks until a `take` consumes; no buffering. Used by `Executors.newCachedThreadPool()`.

```java
SynchronousQueue<String> q = new SynchronousQueue<>();
// Thread A: q.put("x") blocks until Thread B: q.take()
```

**Python equivalent:** `queue.Queue(maxsize=0)` in Python 2; in Python 3, `Queue(maxsize=0)` is unbounded. For true handoff, use two `Queue(maxsize=1)` in opposite directions, or a custom Exchanger-style impl.

**When to use:** Direct handoff when buffering is undesirable; cached thread pools.

**Pitfall:** Any imbalance in put/take leads to blocking; not a buffer.

**One-liner:** "Zero-capacity queue; put blocks until take, or vice versa—strict handoff."

---

### CopyOnWriteArrayList / CopyOnWriteArraySet

**Core problem:** Read-optimized structures. Every write clones the backing array; readers never block. Good when reads >> writes.

**When to use:** Listeners, small read-heavy lists, infrequently updated caches.

**Pitfall:** Writes are O(n); don't use for write-heavy or large collections.

**One-liner:** "Write clones the array; readers never block; good when reads dominate writes."

---

### Other Useful Collections

| Collection | Blocking? | Use Case |
|------------|-----------|----------|
| `ConcurrentLinkedQueue` | No | Non-blocking FIFO; `poll`/`offer` return immediately |
| `LinkedTransferQueue` | Yes (also `transfer`) | Combines BlockingQueue + SynchronousQueue-style transfer |
| `DelayQueue` | Yes | Elements available after delay; scheduling, retries |
| `PriorityBlockingQueue` | Yes | Priority-ordered blocking queue |
| `ConcurrentSkipListMap/Set` | No | Sorted concurrent structures; range queries |
| `BlockingDeque` | Yes | Double-ended; work stealing, LIFO consumption |

---

### Collections: Java → Python Mapping

| Java | Python |
|------|--------|
| `BlockingQueue` | `queue.Queue` (bounded) |
| `SynchronousQueue` | No direct equivalent; two Queues or custom |
| `ConcurrentHashMap` | `threading.Lock` + dict, or third-party |
| `CopyOnWriteArrayList` | No stdlib; rarely needed (GIL) |
| `ConcurrentLinkedQueue` | `queue.Queue` (non-blocking with `get_nowait`) |
| `ForkJoinPool` | `ProcessPoolExecutor` (no work-stealing) |

---

### Collections: Systems Mapping

| System Pattern | Collection |
|----------------|------------|
| Producer-consumer | BlockingQueue |
| Cached thread pool handoff | SynchronousQueue |
| Shared cache / counters | ConcurrentHashMap |
| Event listeners, config lists | CopyOnWriteArrayList |
| Parallel batch / streams | ForkJoinPool |
| Scheduled retries, rate limit | DelayQueue |
| Priority task queue | PriorityBlockingQueue |

---

## Part III: Multithreading Design Patterns

Beyond primitives and collections, these **design patterns** structure how threads coordinate. Interviewers often ask "how would you implement X?"—these are the building blocks.

### Monitor (Mutual Exclusion / Lock)

**Core problem:** Only one thread may execute a critical section at a time. Protects shared mutable state.

**Java:** `synchronized` (intrinsic lock) or `ReentrantLock` (explicit, with `tryLock`, fairness).

```java
// Intrinsic
synchronized (lock) { /* critical section */ }

// Explicit
ReentrantLock lock = new ReentrantLock();
lock.lock();
try {
    // critical section
} finally { lock.unlock(); }
```

**Python:** `threading.Lock()` (non-reentrant). For reentrancy, use `threading.RLock()`.

```python
lock = threading.Lock()
with lock:
    # critical section
```

**When to use:** Any shared mutable variable; smallest possible critical section. Prefer higher-level primitives (e.g., ConcurrentHashMap) when they fit.

**One-liner:** "One thread in critical section at a time; use Lock/synchronized around shared state."

---

### Reader-Writer Lock

**Core problem:** Many readers OR one writer; readers can run concurrently, writers exclude everyone. Read-heavy shared state (caches, config).

**Java:** `ReentrantReadWriteLock`. `readLock()` for readers, `writeLock()` for writers.

```java
ReentrantReadWriteLock rwl = new ReentrantReadWriteLock();
// Reader
rwl.readLock().lock();
try { /* read */ } finally { rwl.readLock().unlock(); }
// Writer
rwl.writeLock().lock();
try { /* write */ } finally { rwl.writeLock().unlock(); }
```

**Python:** No stdlib read-write lock. Implement with a lock + reader count and condition, or use third-party (e.g., `readerwriterlock`). Simple option: one `threading.Lock()` (coarse-grained) or a custom RW lock with `Condition`.

**When to use:** Read-heavy caches, configuration maps, in-memory indexes.

**Pitfall:** Writer starvation if readers keep arriving; use fair lock or bounded reader count in custom impl.

**One-liner:** "Multiple readers or single writer; use when reads >> writes."

---

### Producer-Consumer (Bounded Buffer)

**Core problem:** Producers put items, consumers take; buffer has finite capacity. Producers block when full, consumers when empty.

**Implementation:** BlockingQueue (ArrayBlockingQueue, LinkedBlockingQueue). Already covered in Part II; this is the **pattern** name.

**Variants:** Single producer / single consumer (can use lock-free queues); multiple producers/consumers (BlockingQueue handles contention).

**One-liner:** "Bounded buffer; put blocks when full, take when empty—classic BlockingQueue."

---

### Guarded Suspension

**Core problem:** Thread must wait until a condition holds (e.g., "queue non-empty," "resource available"). Check condition under lock; if false, wait; when another thread makes condition true, it notifies.

**Java:** `Object.wait()` / `notify()` / `notifyAll()` with a condition predicate, or `Condition` (from Lock): `await()` / `signal()` / `signalAll()`.

```java
// Idiom: always wait in a loop (spurious wakeups)
synchronized (lock) {
    while (!condition) lock.wait();
    // use resource
}
// Another thread:
synchronized (lock) {
    condition = true;
    lock.notifyAll();
}
```

**Python:** `threading.Condition`: `with cond: while not pred: cond.wait()` then `cond.notify()` / `notify_all()`.

**When to use:** Custom synchronization when primitives (Semaphore, BlockingQueue) don’t fit; e.g., "wait until pool has at least one idle connection."

**One-liner:** "Wait under lock until condition holds; loop on wait to handle spurious wakeups."

---

### Balking

**Core problem:** "If the object is not in a valid state for the operation, return immediately without performing it." No blocking—e.g., "if already shutting down, don’t accept new work."

**Java:** Check state under lock; return or throw if invalid.

```java
synchronized (this) {
    if (shuttingDown) return;
    // do work
}
```

**Python:** Same: `with lock:` then `if not ready: return`.

**When to use:** Reject work when system is busy or shutting down; avoid queueing when you want fast failure.

**One-liner:** "If not ready, leave immediately—no wait."

---

### Future / Promise (Async Result)

**Core problem:** Submit work and get a handle to retrieve the result later (or be notified when done). Decouples submission from completion.

**Java:** `Future<V>` (get blocks), `CompletableFuture<V>` (compose, thenApply, thenCompose, allOf, anyOf).

```java
CompletableFuture<String> f = CompletableFuture.supplyAsync(() -> fetch());
f.thenAccept(s -> process(s));
// or blocking: f.get();
```

**Python:** `concurrent.futures.Future` (ThreadPoolExecutor/ProcessPoolExecutor.submit), `asyncio.Future` / coroutines.

**When to use:** Async I/O, parallel task submission, pipeline stages.

**One-liner:** "Handle to a result that will be available later; get() blocks, CompletableFuture composes."

---

### Thread Pool (Executor / Worker Threads)

**Core problem:** Reuse a fixed (or bounded) set of threads to execute many tasks. Avoids thread-creation cost and caps concurrency.

**Java:** `Executors.newFixedThreadPool(n)`, `newCachedThreadPool()` (SynchronousQueue), `newSingleThreadExecutor()`, `ScheduledExecutorService` for delay/period.

**Python:** `concurrent.futures.ThreadPoolExecutor(max_workers=n)`, `ProcessPoolExecutor` for CPU-bound.

**When to use:** I/O-bound: pool size often >> cores. CPU-bound: pool size ≈ cores (or use ForkJoinPool).

**One-liner:** "Reuse N threads for many tasks; fixed vs cached vs scheduled."

---

### Thread-Local Storage

**Core problem:** Per-thread data so each thread has its own copy—no sharing, no locking. E.g., request ID, DB connection per thread, user context.

**Java:** `ThreadLocal<T>`. `get()` / `set()` / `remove()`. Watch for leaks in pooled threads (remove in finally or use try-with-resources pattern).

**Python:** `threading.local()`. Same idea: attributes on the local object are per-thread.

**When to use:** Request-scoped or thread-scoped context; avoid passing parameters through many layers.

**Pitfall:** In thread pools, thread is reused; clear or remove after use to avoid leaking previous request’s data.

**One-liner:** "One copy per thread; no locking; clear in pooled threads to avoid leaks."

---

### Immutable Object

**Core problem:** Share data across threads without locking—no one can modify after construction. Safe publication: final fields in Java guarantee visibility.

**Java:** Final fields, no mutators; for collections use unmodifiable wrappers or copy-on-read. Records (Java 16+) are naturally immutable.

**Python:** Use frozen dataclasses, tuples, or immutable types; no mutators.

**When to use:** Config, DTOs, cache entries that are replaced rather than updated.

**One-liner:** "No mutators after construction; safe to share without locks."

---

### Double-Checked Locking (Lazy Singleton)

**Core problem:** Initialize once, lazily, with minimal locking. Classic pattern (often replaced by static holder or enum in Java).

**Java:** Requires `volatile` for the reference so publication is visible. Without volatile, another thread can see a partially constructed object.

```java
private static volatile MySingleton instance;
static MySingleton getInstance() {
    if (instance == null) {
        synchronized (MySingleton.class) {
            if (instance == null) instance = new MySingleton();
        }
    }
    return instance;
}
```

**Preferred in Java:** Static holder—no volatile, lazy and thread-safe:

```java
private static class Holder { static final MySingleton INSTANCE = new MySingleton(); }
static MySingleton getInstance() { return Holder.INSTANCE; }
```

**Python:** Custom with Lock + double check, or use module-level initialization (thread-safe at import).

**One-liner:** "Lazy init with volatile + sync; or use static holder / enum to avoid DCL."

---

### Atomic Variables (CAS)

**Core problem:** Update a single variable (counter, reference) without locking, using compare-and-swap. Non-blocking, good for contention on a single word.

**Java:** `AtomicInteger`, `AtomicLong`, `AtomicReference<V>`, `AtomicBoolean`. Methods: `get`, `set`, `compareAndSet`, `getAndIncrement`, `updateAndGet`.

**Python:** No stdlib atomics. Use `threading.Lock` around an int, or C extensions. For simple counters, Lock is acceptable.

**When to use:** Counters, sequence numbers, state flags, lock-free data structures.

**One-liner:** "Single-variable updates via CAS; no lock; use for counters and refs."

---

### Reactor (Event Loop)

**Core problem:** One (or few) threads dispatch I/O events to handlers. Non-blocking I/O + multiplexing (select/epoll); high connection count with few threads.

**Java:** NIO with `Selector`, Netty, or virtual threads (JEP 444) handling blocking I/O with many threads. Reactor = event loop + channels.

**Python:** `asyncio`—single-threaded event loop, `async`/`await`, non-blocking I/O.

**When to use:** High-concurrency I/O servers (APIs, proxies), chat, streaming.

**One-liner:** "One thread, many connections; non-blocking I/O + event dispatch."

---

### Active Object

**Core problem:** Decouple method invocation from execution. Calls become messages enqueued to the object’s thread; the object processes its own queue (single-threaded execution, no external locking).

**Implementation:** In Java: dedicated thread + BlockingQueue of commands (Runnable/Callable); public methods enqueue and return a Future. Similar in Python with a thread + queue.

**When to use:** Encapsulate state and serialize access without exposing locks; actor-like single-threaded processing.

**One-liner:** "Method calls enqueued; one thread drains queue—encapsulated serial execution."

---

### Pipeline (Stages with Queues)

**Core problem:** Multi-stage processing; each stage runs in its own thread(s), connected by queues. Data flows: stage1 → queue → stage2 → queue → stage3.

**Implementation:** BlockingQueues between stages; each stage is a loop that takes from input queue, processes, puts to output queue.

**When to use:** ETL, image/video pipelines, multi-step request processing.

**One-liner:** "Stages connected by queues; each stage can scale independently."

---

### Scheduler (Delayed / Periodic Execution)

**Core problem:** Run a task once after a delay, or repeatedly at an interval. Central scheduler thread (or pool) manages timing.

**Java:** `ScheduledExecutorService.schedule`, `scheduleAtFixedRate`, `scheduleWithFixedDelay`. Also `DelayQueue` for custom delay handling.

**Python:** `threading.Timer`, or `asyncio` with `asyncio.sleep` + loop; `sched.scheduler` for simple cases.

**When to use:** Retries, heartbeats, batch windows, rate limiters.

**One-liner:** "Run once after delay or at fixed rate; ScheduledExecutorService or asyncio."

---

### Design Patterns: Quick Reference

| Pattern | Java | Python | One-Liner |
|---------|------|--------|-----------|
| **Monitor** | synchronized, ReentrantLock | threading.Lock, RLock | One thread in critical section |
| **Reader-Writer** | ReentrantReadWriteLock | DIY or third-party | Many readers OR one writer |
| **Producer-Consumer** | BlockingQueue | queue.Queue | Bounded buffer; put/take block |
| **Guarded Suspension** | wait/notify, Condition | Condition | Wait until condition; loop on wait |
| **Balking** | if (!ok) return under lock | Same | If not ready, return |
| **Future/Promise** | Future, CompletableFuture | concurrent.futures, asyncio | Result handle; get or compose |
| **Thread Pool** | ExecutorService | ThreadPoolExecutor | Reuse N threads |
| **Thread-Local** | ThreadLocal | threading.local | Per-thread data; clear in pools |
| **Immutable** | final, records | frozen, tuples | Share without locks |
| **Double-Checked Lock** | volatile + sync / static holder | Lock + double check | Lazy singleton |
| **Atomic** | AtomicInteger, etc. | Lock + int or C ext | CAS, no lock |
| **Reactor** | NIO Selector, Netty | asyncio | Event loop, non-blocking I/O |
| **Active Object** | Thread + BlockingQueue of commands | Thread + queue | Serial execution via queue |
| **Pipeline** | Stages + BlockingQueues | Stages + queues | Multi-stage with queues |
| **Scheduler** | ScheduledExecutorService | Timer, asyncio | Delayed / periodic run |

---

## Summary

**Fundamentals (Part 0):** CPU-bound vs I/O-bound determines your concurrency model. Work stealing keeps ForkJoinPool busy. Context switching hurts CPU-bound throughput when threads >> cores. GIL limits Python CPU parallelism; use multiprocessing. Blocking vs non-blocking I/O explains thread-per-connection vs event-loop scaling.

- **Semaphore**: Resource bounding—use for pools and rate limits. Python: prefer `BoundedSemaphore`.
- **CountDownLatch**: One-shot “N done” signal. Python: implement with `Condition` or `Event`.
- **CyclicBarrier**: Reusable N-way rendezvous. Python: `threading.Barrier` / `asyncio.Barrier`.
- **Phaser**: Phased, dynamic-party sync. Python: no stdlib; use Barrier per phase or custom impl.
- **Exchanger**: Two-way object swap. Python: implement with `Queue` or Lock+Event.

**Primitives:** Semaphore (pools, rate limits), CountDownLatch (one-shot "N done"), CyclicBarrier (reusable rendezvous), Phaser (phased, dynamic parties), Exchanger (pairwise swap). Python: Barrier maps to CyclicBarrier; CountDownLatch, Phaser, Exchanger require DIY impls.

**Collections:** BlockingQueue is the producer-consumer baseline. ForkJoinPool for work-stealing, recursive parallelism; ConcurrentHashMap for shared maps with atomic compute/merge; SynchronousQueue for zero-capacity handoff; CopyOnWriteArrayList for read-heavy lists. Python: `queue.Queue` ≈ BlockingQueue; no direct equivalents for SynchronousQueue, ConcurrentHashMap, or ForkJoin work-stealing.

**Design patterns (Part III):** Monitor (Lock/synchronized), Reader-Writer Lock, Producer-Consumer (BlockingQueue), Guarded Suspension (wait/notify, Condition), Balking, Future/Promise (CompletableFuture), Thread Pool (ExecutorService), Thread-Local, Immutable Object, Double-Checked Locking, Atomic Variables (CAS), Reactor (event loop), Active Object (queue + single thread), Pipeline (stages + queues), Scheduler (delayed/periodic). These name the recurring structures behind "how would you implement X?" questions.

In backends, Semaphores and BlockingQueue dominate. ForkJoin powers parallel streams; ConcurrentHashMap powers shared caches. Know ForkJoin, ConcurrentHashMap, SynchronousQueue, CopyOnWrite, and the design patterns above for depth.
