# JVM & Memory Interview Questions

## Basic Level

### Q1: What is JVM and what are its main components?
**Answer:** JVM (Java Virtual Machine) is a runtime environment that executes Java bytecode. Main components:
- **Class Loader Subsystem**: Loads .class files
- **Runtime Data Areas**: Memory areas (heap, stack, method area, PC registers)
- **Execution Engine**: Interpreter, JIT compiler, garbage collector

### Q2: Explain Stack vs Heap memory
**Answer:**
- **Stack**: Thread-local, stores method calls, local variables, LIFO structure
- **Heap**: Shared among threads, stores objects and arrays, managed by GC

### Q3: What is the difference between Young and Old generation?
**Answer:**
- **Young Generation**: New objects, divided into Eden, S0, S1 spaces
- **Old Generation**: Long-lived objects promoted from Young generation
- Objects start in Eden, survive GC cycles move to Survivor spaces, then Old generation

## Intermediate Level

### Q4: Explain the class loading process
**Answer:** Three phases:
1. **Loading**: Read .class file, create binary representation in method area
2. **Linking**: 
   - Verification: Bytecode verification
   - Preparation: Allocate memory for static variables
   - Resolution: Symbolic references → direct references
3. **Initialization**: Execute static initializers and static blocks

### Q5: What is escape analysis and how does it help?
**Answer:** JIT compiler optimization that determines if object references escape method scope:
- **No escape**: Object can be stack-allocated (faster)
- **Escapes**: Must be heap-allocated
- Benefits: Reduced GC pressure, better performance

### Q6: Compare different garbage collectors
**Answer:**
- **Serial GC**: Single-threaded, small applications
- **Parallel GC**: Multi-threaded, throughput-focused
- **G1 GC**: Low-latency, predictable pause times
- **ZGC/Shenandoah**: Ultra-low latency, concurrent collection

## Advanced Level

### Q7: How does JIT compilation work?
**Answer:**
1. Code starts as interpreted bytecode
2. JVM profiles execution, identifies "hot spots"
3. Hot methods compiled to native code
4. Optimizations applied (inlining, dead code elimination)
5. Deoptimization if assumptions become invalid

### Q8: What causes memory leaks in Java?
**Answer:**
- Static collections that keep growing
- Listeners not properly removed
- ThreadLocal variables not cleaned up
- Unclosed resources (streams, connections)
- Inner class references to outer class

### Q9: Explain Metaspace vs PermGen
**Answer:**
- **PermGen** (Java 7-): Fixed size, stored class metadata, caused OutOfMemoryError
- **Metaspace** (Java 8+): Native memory, auto-expanding, better memory management

## Scenario-Based Questions

### Q10: Application experiencing frequent GC pauses. How to diagnose?
**Answer:**
1. Enable GC logging: `-Xlog:gc*:gc.log`
2. Analyze pause times and frequency
3. Check heap utilization patterns
4. Consider different GC algorithm (G1, ZGC)
5. Tune heap sizes and GC parameters

### Q11: OutOfMemoryError in production. Investigation steps?
**Answer:**
1. Identify error type (heap space, metaspace, direct memory)
2. Generate heap dump: `jmap -dump:format=b,file=heap.hprof <pid>`
3. Analyze with MAT/VisualVM for memory leaks
4. Check GC logs for patterns
5. Monitor allocation rates and object lifecycle

### Q12: How to optimize JVM for microservices?
**Answer:**
- Use container-aware JVM flags: `-XX:+UseContainerSupport`
- Choose low-latency GC: G1 or ZGC
- Optimize startup time: `-XX:+TieredCompilation`
- Set appropriate heap size for container limits
- Consider GraalVM native image for faster startup

## Code Analysis Questions

### Q13: Identify potential issues in this code:
```java
public class CacheManager {
    private static Map<String, Object> cache = new HashMap<>();
    
    public static void put(String key, Object value) {
        cache.put(key, value);
    }
    
    public static Object get(String key) {
        return cache.get(key);
    }
}
```

**Issues:**
- Memory leak: cache grows indefinitely
- Thread safety: HashMap not thread-safe
- No eviction policy

**Solutions:**
- Use ConcurrentHashMap or synchronized access
- Implement LRU cache with size limits
- Use WeakHashMap for automatic cleanup

### Q14: What's wrong with this memory usage pattern?
```java
public void processLargeFile() {
    List<String> lines = new ArrayList<>();
    
    try (BufferedReader reader = Files.newBufferedReader(path)) {
        String line;
        while ((line = reader.readLine()) != null) {
            lines.add(line); // Loading entire file into memory
        }
    }
    
    // Process lines...
}
```

**Issues:**
- Loads entire file into memory
- Can cause OutOfMemoryError for large files

**Solution:**
- Stream processing: process line by line
- Use lazy evaluation with Stream API

## Performance Tuning Questions

### Q15: JVM flags for a high-throughput application?
```bash
-Xms4g -Xmx4g                    # Fixed heap size
-XX:+UseG1GC                     # G1 garbage collector
-XX:MaxGCPauseMillis=100         # Target pause time
-XX:+UseStringDeduplication      # Reduce string memory usage
-XX:+OptimizeStringConcat        # Optimize string operations
```

### Q16: Monitoring and alerting setup for JVM applications?
**Metrics to monitor:**
- Heap utilization (< 80%)
- GC pause times (< 100ms)
- GC frequency
- Thread count
- CPU usage
- Allocation rate

**Tools:**
- Micrometer + Prometheus
- JVM built-in MBeans
- Application Performance Monitoring (APM) tools

---

# Memory Aid: Simple JVM Story - The Java House

## The House Layout (Basic)
**JVM = A 3-room house:**
- **Front Door** = Class Loader (lets .class files in)
- **Living Room** = Memory (Stack = private closets, Heap = shared space)
- **Kitchen** = Execution Engine (cooks bytecode into machine code)

**Memory Rooms:**
- **Stack** = Your private bedroom (method calls, local variables)
- **Heap** = Shared living room (objects live here, GC cleans up)

**Heap has 2 floors:**
- **Young Floor** = Nursery (Eden + 2 Survivor rooms S0, S1)
- **Old Floor** = Adult room (long-lived objects)

## The Immigration (Intermediate)
**3-step process to enter the house:**
1. **Loading** = Check ID at door
2. **Linking** = Background check + assign room + update address book
3. **Initialization** = Welcome ceremony

**Escape Analysis** = Security guard checks if objects try to leave their room

**4 Cleaning Services:**
- **Serial** = 1 cleaner (small house)
- **Parallel** = Multiple cleaners (big house)
- **G1** = Scheduled cleaning (predictable)
- **ZGC** = Super fast cleaning (no pause)

## The Smart Systems (Advanced)
**JIT Compiler** = Smart cook who learns your favorite recipes and makes them faster

**Memory Leaks** = 5 bad roommates:
1. **Static Hoarder** (never throws away stuff)
2. **Listener Ghost** (won't leave)
3. **ThreadLocal Zombie** (haunts threads)
4. **Resource Vampire** (never closes doors)
5. **Inner Class Stalker** (follows outer class)

**PermGen → Metaspace** = Old fixed-size basement → New expandable attic

## Emergency Procedures (Scenarios)
**GC Problems:** Turn on logs → Check patterns → Try different cleaner → Adjust settings

**OutOfMemory:** Find problem type → Take house photo (heap dump) → Analyze with tools → Fix leaks

**Microservices:** Use container-friendly settings + fast GC + quick startup

## Code Problems
**Bad Cache:** HashMap grows forever + not thread-safe → Use ConcurrentHashMap with limits

**File Loading:** Don't load entire file → Read line by line

## Performance Settings
```bash
-Xms4g -Xmx4g        # House size
-XX:+UseG1GC         # G1 cleaner
-XX:MaxGCPauseMillis=100  # Max 100ms cleaning
```

**Monitor:** Heap < 80%, GC < 100ms, allocation rate

---
**Memory Trick:** JVM = House with 3 rooms, immigration process, cleaning services, smart systems, emergency procedures, and performance tuning. Each concept maps to something familiar in a house.