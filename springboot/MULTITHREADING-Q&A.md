# Spring Boot — Multithreading Interview Q&A

Basic to intermediate multithreading questions aligned with Spring Boot. Covers core Java concurrency and Spring Boot async patterns.

---

## Core Java Multithreading

**Q: What is a thread? How does it differ from a process?**  
**A:** A **thread** is the smallest unit of execution within a process. A **process** has its own memory space; **threads** within the same process **share** memory (heap). Threads are lighter-weight and faster to create/switch than processes. In Java, each thread has its own **stack** but shares the **heap**.

**Q: What are the different ways to create a thread in Java?**  
**A:** 
1. **Extend `Thread`** and override `run()` → `new MyThread().start()`
2. **Implement `Runnable`** → `new Thread(new MyRunnable()).start()`
3. **Lambda** → `new Thread(() -> { ... }).start()`
4. **`ExecutorService`** → `executor.submit(() -> { ... })` (preferred)

**Q: What is the difference between `start()` and `run()` methods?**  
**A:** **`start()`** creates a **new thread** and calls `run()` in that thread. **`run()`** executes the code in the **current thread** (synchronously). Calling `run()` directly does **not** create a new thread.

**Q: What is thread safety? Give an example of a thread-safe and thread-unsafe operation.**  
**A:** **Thread safety** means code behaves correctly when accessed by multiple threads simultaneously. **Thread-safe:** `StringBuffer`, `ConcurrentHashMap`, `AtomicInteger`. **Thread-unsafe:** `StringBuilder`, `HashMap`, `ArrayList` (without synchronization).

**Q: What is a race condition?**  
**A:** A **race condition** occurs when the outcome depends on the **timing** of thread execution. Example: two threads increment a counter (`count++`) — the result may be incorrect because the operation is **not atomic** (read-modify-write).

**Q: What is the `volatile` keyword? When would you use it?**  
**A:** **`volatile`** ensures **visibility**: changes by one thread are **immediately visible** to other threads. It prevents compiler optimizations that cache the variable in CPU registers. Use for **simple flags** (e.g., `volatile boolean stop = false`). **Not** a replacement for synchronization; does **not** make compound operations atomic.

**Q: Explain `synchronized` keyword. What are synchronized blocks vs synchronized methods?**  
**A:** **`synchronized`** provides **mutual exclusion** — only one thread can execute synchronized code at a time. **Synchronized method:** locks on the **object instance** (or class for static). **Synchronized block:** locks on a **specific object** (e.g., `synchronized (lock) { ... }`). More flexible; can lock on different objects.

**Q: What is a deadlock? How can you prevent it?**  
**A:** **Deadlock** occurs when two or more threads are **blocked forever**, each waiting for a resource held by another. Prevention: **acquire locks in the same order**, use **timeouts** (`tryLock(timeout)`), avoid **nested locks**, use **higher-level concurrency utilities** (e.g., `ConcurrentHashMap`).

**Q: What is the difference between `wait()`, `notify()`, and `notifyAll()`?**  
**A:** All are methods of **`Object`** (not `Thread`). **`wait()`**: releases the lock and waits until **`notify()`** / **`notifyAll()`** is called. **`notify()`**: wakes **one** waiting thread. **`notifyAll()`**: wakes **all** waiting threads. Must be called within a **`synchronized`** block. Prefer **`notifyAll()`** unless you're sure only one thread should proceed.

**Q: What is `ThreadLocal`? When would you use it?**  
**A:** **`ThreadLocal`** provides **thread-local variables** — each thread has its own **independent copy**. Common use: **`SecurityContextHolder`** in Spring Security (stores `Authentication` per thread), **request context**, **transaction context**. **Clean up** with `remove()` to avoid memory leaks (especially in thread pools).

---

## Thread Pools & Executors

**Q: What is an `ExecutorService`? Why use it instead of creating threads directly?**  
**A:** **`ExecutorService`** manages a **pool of threads** and a **queue of tasks**. Benefits: **reuse threads** (cheaper than creating new ones), **control concurrency** (pool size), **lifecycle management** (shutdown), **task scheduling**. Creating threads directly is expensive and harder to manage.

**Q: Explain the different types of thread pools provided by `Executors`.**  
**A:**
- **`newFixedThreadPool(n)`**: Fixed number of threads; unbounded queue.
- **`newCachedThreadPool()`**: Creates threads as needed; reuses idle threads; no queue.
- **`newSingleThreadExecutor()`**: Single thread; sequential execution; unbounded queue.
- **`newScheduledThreadPool(n)`**: For scheduled/repeating tasks.

**Q: What are the core parameters of a `ThreadPoolExecutor`?**  
**A:**
- **`corePoolSize`**: Minimum threads kept alive (even if idle).
- **`maximumPoolSize`**: Maximum threads that can be created.
- **`keepAliveTime`**: How long idle threads (beyond core) live before termination.
- **`workQueue`**: Queue for tasks when all core threads are busy.
- **`RejectedExecutionHandler`**: Policy when queue is full and max threads reached.

**Q: What happens when a thread pool's queue is full?**  
**A:** Depends on **`RejectedExecutionHandler`**:
- **`AbortPolicy`** (default): Throws `RejectedExecutionException`.
- **`CallerRunsPolicy`**: Executes task in the **calling thread** (backpressure).
- **`DiscardPolicy`**: Silently discards the task.
- **`DiscardOldestPolicy`**: Removes oldest task and adds new one.

**Q: How do you properly shut down an `ExecutorService`?**  
**A:** 
1. **`shutdown()`**: Stops accepting new tasks; waits for running tasks to finish.
2. **`shutdownNow()`**: Attempts to stop all tasks (interrupts); returns list of pending tasks.
3. **`awaitTermination(timeout)`**: Waits for shutdown to complete.

```java
executor.shutdown();
try {
    if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
        executor.shutdownNow();
    }
} catch (InterruptedException e) {
    executor.shutdownNow();
    Thread.currentThread().interrupt();
}
```

---

## CompletableFuture

**Q: What is `CompletableFuture`? How does it differ from `Future`?**  
**A:** **`CompletableFuture`** is a **completable** `Future` that can be **manually completed** and supports **chaining** operations. **`Future`** is read-only; you can only `get()` or `cancel()`. **`CompletableFuture`** allows **`thenApply()`**, **`thenCompose()`**, **`thenCombine()`**, **`allOf()`**, **`anyOf()`** for async composition.

**Q: Explain `thenApply()` vs `thenCompose()` in `CompletableFuture`.**  
**A:**
- **`thenApply()`**: Transforms the **result** (returns a value). `CompletableFuture<String>` → `thenApply(s -> s.length())` → `CompletableFuture<Integer>`.
- **`thenCompose()`**: Chains **another `CompletableFuture`** (flattens). `CompletableFuture<String>` → `thenCompose(s -> fetchData(s))` → `CompletableFuture<Data>` (not `CompletableFuture<CompletableFuture<Data>>`).

**Q: How do you handle exceptions in `CompletableFuture`?**  
**A:** Use **`exceptionally()`** (returns default value) or **`handle()`** (handles both success and failure). **`whenComplete()`** runs regardless but doesn't transform the result.

```java
future.exceptionally(ex -> {
    log.error("Error", ex);
    return defaultValue;
});
```

**Q: How do you wait for multiple `CompletableFuture`s to complete?**  
**A:** 
- **`CompletableFuture.allOf(...)`**: Waits for **all** to complete.
- **`CompletableFuture.anyOf(...)`**: Returns when **any** completes.

```java
CompletableFuture.allOf(f1, f2, f3)
    .thenRun(() -> System.out.println("All done"));
```

---

## Spring Boot Async & Multithreading

**Q: How does `@Async` work in Spring Boot?**  
**A:** **`@Async`** is an **AOP aspect** that runs the method in a **different thread** via a **`TaskExecutor`**. Requires **`@EnableAsync`**. The method returns **`void`**, **`Future<T>`**, or **`CompletableFuture<T>`**. By default, uses **`SimpleAsyncTaskExecutor`** (creates new thread per task).

**Q: Where should you place `@EnableAsync`?**  
**A:** Place **`@EnableAsync`** on a **`@Configuration`** class. Common options:

1. **Dedicated async configuration class** (recommended):
```java
@Configuration
@EnableAsync
public class AsyncConfig {
    @Bean
    public TaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("async-");
        executor.initialize();
        return executor;
    }
}
```

2. **Main application class** (if simple setup):
```java
@SpringBootApplication
@EnableAsync
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

3. **Any `@Configuration` class** that's component-scanned. **Best practice**: Keep it in a dedicated config class alongside your `TaskExecutor` bean definition for better organization.

**Q: Why doesn't `@Async` work when called from the same class?**  
**A:** **Self-invocation**: the call doesn't go through the **proxy**, so the `@Async` aspect never runs. The method executes **synchronously** in the same thread. **Fix:** Call the `@Async` method on **another bean**, or **inject self** and call via the proxy.

**Q: How do you configure a custom thread pool for `@Async`?**  
**A:** Define a **`TaskExecutor`** bean (e.g., **`ThreadPoolTaskExecutor`**). Spring will use it automatically. For multiple executors, use **`@Async("executorBeanName")`**.

```java
@Bean
public TaskExecutor taskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(5);
    executor.setMaxPoolSize(10);
    executor.setQueueCapacity(100);
    executor.setThreadNamePrefix("async-");
    executor.initialize();
    return executor;
}
```

**Q: What is the difference between `ThreadPoolTaskExecutor` and `ThreadPoolExecutor`?**  
**A:** **`ThreadPoolTaskExecutor`** is Spring's wrapper around **`ThreadPoolExecutor`**. It implements **`TaskExecutor`** and **`AsyncTaskExecutor`** (Spring interfaces). Provides Spring integration (e.g., error handling, task decoration). Under the hood, it uses **`ThreadPoolExecutor`**.

**Q: Are `@Async` methods transactional?**  
**A:** They run in a **different thread**. If the async method is `@Transactional`, it runs in its **own transaction**. There is **no shared transaction** with the caller. The caller's transaction commits independently of the async work.

**Q: How do you handle exceptions in `@Async` methods?**  
**A:** 
1. Return **`CompletableFuture<T>`** and handle with **`exceptionally()`** / **`handle()`**.
2. Implement **`AsyncUncaughtExceptionHandler`** (for `void` return types).
3. Wrap in try-catch inside the async method.

```java
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) -> {
            log.error("Async error in {}", method.getName(), ex);
        };
    }
}
```

**Q: What is `@EnableAsync`? What does it do?**  
**A:** **`@EnableAsync`** enables Spring's async processing. It registers **`AsyncAnnotationBeanPostProcessor`**, which processes **`@Async`** annotations and creates proxies. Without it, `@Async` is **ignored**.

**Q: How do you use `CompletableFuture` with Spring Boot?**  
**A:** Return **`CompletableFuture<T>`** from an `@Async` method, or create/manually complete `CompletableFuture` in regular code. Spring doesn't require `@Async` if you're managing `CompletableFuture` yourself.

```java
@Async
public CompletableFuture<String> fetchData() {
    return CompletableFuture.completedFuture("data");
}
```

**Q: How do you test `@Async` methods?**  
**A:** 
1. Use **`@SpringBootTest`** with **`@EnableAsync`**.
2. **`await()`** on the `Future` / `CompletableFuture` (or use **`get()`** with timeout).
3. Mock the async behavior or use **`@MockBean`** for dependencies.

```java
CompletableFuture<String> future = service.asyncMethod();
String result = future.get(5, TimeUnit.SECONDS);
```

---

## Common Pitfalls & Best Practices

**Q: What are common multithreading pitfalls in Spring Boot applications?**  
**A:**
1. **Self-invocation** with `@Async` / `@Transactional` (bypasses proxy).
2. **`ThreadLocal` leaks** in thread pools (not calling `remove()`).
3. **Shared mutable state** without synchronization.
4. **Blocking operations** in async methods (defeats the purpose).
5. **Unbounded thread pools** (resource exhaustion).
6. **Not handling exceptions** in async code.

**Q: Why should you avoid using `ThreadLocal` with thread pools?**  
**A:** Thread pools **reuse threads**, so `ThreadLocal` values **persist** across tasks. If you don't **`remove()`** the value, it can leak memory and cause **data pollution** (one task seeing another task's data). Always **`remove()`** in a **`finally`** block or use **`try-with-resources`** pattern.

**Q: How do you pass context (e.g., `SecurityContext`) to async threads?**  
**A:** Use **`DelegatingSecurityContextExecutorService`** (Spring Security) or **`TaskDecorator`** to copy context from the calling thread to the async thread.

```java
@Bean
public TaskExecutor taskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setTaskDecorator(new SecurityContextTaskDecorator());
    return executor;
}
```

**Q: What is the difference between parallel and concurrent execution?**  
**A:** **Parallel**: Tasks run **simultaneously** on multiple CPUs/cores (true parallelism). **Concurrent**: Tasks make **progress** over time by interleaving execution (may or may not be parallel). In Java, **`ForkJoinPool`** enables parallelism; **`ExecutorService`** provides concurrency.

**Q: When would you use `ForkJoinPool` vs `ThreadPoolExecutor`?**  
**A:** **`ForkJoinPool`** is optimized for **recursive, divide-and-conquer** tasks (work-stealing). Use for **parallel streams**, **`CompletableFuture`** (default executor), or recursive algorithms. **`ThreadPoolExecutor`** is general-purpose; use for **independent tasks**, **I/O-bound** work, **task queues**.

**Example: `ForkJoinPool` for recursive divide-and-conquer tasks:**
```java
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;

// Recursive task: sum array elements
class SumTask extends RecursiveTask<Long> {
    private final int[] array;
    private final int start, end;
    private static final int THRESHOLD = 1000;
    
    SumTask(int[] array, int start, int end) {
        this.array = array;
        this.start = start;
        this.end = end;
    }
    
    @Override
    protected Long compute() {
        int length = end - start;
        if (length <= THRESHOLD) {
            // Base case: compute directly
            long sum = 0;
            for (int i = start; i < end; i++) {
                sum += array[i];
            }
            return sum;
        } else {
            // Divide: split into two subtasks
            int mid = start + length / 2;
            SumTask left = new SumTask(array, start, mid);
            SumTask right = new SumTask(array, mid, end);
            
            // Fork: execute subtasks in parallel (work-stealing)
            left.fork();
            long rightResult = right.compute();
            long leftResult = left.join();
            
            return leftResult + rightResult;
        }
    }
}

// Usage
ForkJoinPool pool = ForkJoinPool.commonPool();
int[] array = new int[10000];
// ... populate array
long sum = pool.invoke(new SumTask(array, 0, array.length));

// Parallel streams use ForkJoinPool internally
long sum2 = Arrays.stream(array).parallel().sum();
```

**Example: `ThreadPoolExecutor` for independent I/O-bound tasks:**
```java
import java.util.concurrent.*;
import java.util.List;
import java.util.ArrayList;

// Independent tasks: fetch data from multiple APIs
public class ApiService {
    private final ThreadPoolExecutor executor;
    
    public ApiService() {
        this.executor = new ThreadPoolExecutor(
            10,                      // corePoolSize
            20,                      // maxPoolSize
            60L, TimeUnit.SECONDS,   // keepAliveTime
            new LinkedBlockingQueue<>(100), // workQueue
            new ThreadFactory() {
                private int count = 0;
                @Override
                public Thread newThread(Runnable r) {
                    return new Thread(r, "api-worker-" + count++);
                }
            },
            new ThreadPoolExecutor.CallerRunsPolicy() // rejection policy
        );
    }
    
    public List<String> fetchMultipleApis(List<String> urls) {
        List<Future<String>> futures = new ArrayList<>();
        
        // Submit independent tasks
        for (String url : urls) {
            Future<String> future = executor.submit(() -> {
                // I/O-bound: HTTP call
                return httpClient.get(url);
            });
            futures.add(future);
        }
        
        // Collect results
        List<String> results = new ArrayList<>();
        for (Future<String> future : futures) {
            try {
                results.add(future.get(5, TimeUnit.SECONDS));
            } catch (Exception e) {
                // handle error
            }
        }
        return results;
    }
    
    public void shutdown() {
        executor.shutdown();
    }
}
```

**Key Differences:**

| Aspect | `ForkJoinPool` | `ThreadPoolExecutor` |
|--------|----------------|---------------------|
| **Best for** | Recursive, CPU-intensive tasks | Independent, I/O-bound tasks |
| **Work-stealing** | Yes (threads steal work from others) | No (fixed task queue) |
| **Task splitting** | Tasks split into subtasks | Tasks are independent units |
| **Use cases** | Parallel streams, recursive algorithms, divide-and-conquer | HTTP calls, DB queries, file I/O, job queues |
| **Default for** | `CompletableFuture.supplyAsync()`, parallel streams | Manual creation for custom executors |

**Q: How do you monitor thread pool health in Spring Boot?**  
**A:** 
1. **Actuator metrics**: Expose **`executor.*`** metrics (pool size, active threads, queue size).
2. **Custom health indicator**: Check pool saturation, queue capacity.
3. **Logging**: Log thread pool stats periodically.

```java
@Bean
public HealthIndicator threadPoolHealth(ThreadPoolTaskExecutor executor) {
    return () -> {
        int active = executor.getActiveCount();
        int max = executor.getMaxPoolSize();
        return active < max * 0.8 
            ? Health.up().build() 
            : Health.down().withDetail("active", active).build();
    };
}
```

**Q: What is the producer-consumer pattern? How is it implemented in Java?**  
**A:** **Producer** generates tasks; **consumer** processes them. Implemented via **`BlockingQueue`** (e.g., `LinkedBlockingQueue`, `ArrayBlockingQueue`). Producer **`put()`** (blocks if full); consumer **`take()`** (blocks if empty). **`ThreadPoolExecutor`** uses this pattern internally.

**Q: Explain the difference between `CountDownLatch` and `CyclicBarrier`.**  
**A:**
- **`CountDownLatch`**: One-time use. Threads wait for a **count to reach zero**. Used for "wait for N events" (e.g., wait for all tasks to start).
- **`CyclicBarrier`**: Reusable. Threads wait at a **barrier** until **all arrive**, then proceed. Used for "synchronize at a point" (e.g., parallel computation phases).

---

## Spring Boot Specific Scenarios

**Q: How do you handle long-running tasks in a Spring Boot REST API?**  
**A:**
1. **`@Async`** with **`CompletableFuture`** → return immediately, client polls for status.
2. **Job queue** (e.g., RabbitMQ, Redis) → return job ID, client polls.
3. **Server-Sent Events (SSE)** or **WebSocket** for real-time updates.
4. **Background job framework** (e.g., Spring Batch, Quartz).

**Q: How do you ensure thread safety in a Spring Boot service?**  
**A:**
1. **Stateless services** (preferred) — no shared mutable state.
2. **Immutable objects** — `final` fields, no setters.
3. **Thread-safe collections** — `ConcurrentHashMap`, `CopyOnWriteArrayList`.
4. **Synchronization** — `synchronized`, `ReentrantLock`.
5. **Atomic classes** — `AtomicInteger`, `AtomicReference`.

**Q: What is the relationship between Spring's request scope and threads?**  
**A:** In web apps, each **HTTP request** is handled by a **thread**. **`@RequestScope`** beans are created **per request** and stored in thread-local context. **`@Async`** methods run in **different threads**, so they **cannot access** request-scoped beans directly. Use **`RequestContextHolder`** or **`TaskDecorator`** to propagate context.

**Q: How do you implement retry logic with async operations?**  
**A:** Use **Spring Retry** (`@Retryable`) or **`CompletableFuture`** with custom retry logic:

```java
@Retryable(value = {Exception.class}, maxAttempts = 3)
@Async
public CompletableFuture<String> fetchWithRetry() {
    // ...
}
```

Or manually:
```java
CompletableFuture<String> future = CompletableFuture
    .supplyAsync(() -> fetchData())
    .exceptionally(ex -> retryFetch());
```

---

Use this **Q&A** for quick rehearsal. For deeper understanding, refer to **STUDY-GUIDE.md** and **CHEAT-SHEET.md** in the same folder.
