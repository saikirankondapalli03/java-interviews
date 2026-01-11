# Interview Cheat Sheet - Quick Reference

## 30-Second Answers (Most Asked)

### "Explain JVM memory structure"
Stack (method calls, local vars) + Heap (objects: Young/Old gen) + Metaspace (class metadata)

### "Stack vs Heap?"
Stack: thread-local, method calls, fast access
Heap: shared, objects, GC managed

### "How does GC work?"
Mark unreachable objects → Sweep/compact → Reclaim memory
Young gen (frequent, fast) → Old gen (less frequent, slower)

### "Memory leak in Java?"
Objects referenced but not used: static collections, listeners, ThreadLocal, unclosed resources

### "G1 vs Parallel GC?"
G1: low latency, predictable pauses, region-based
Parallel: high throughput, stop-the-world, generation-based

## Red Flag Answers to Avoid
❌ "Java has no memory leaks" (Wrong - has logical leaks)
❌ "GC always runs when needed" (Can be tuned/disabled)
❌ "Stack stores objects" (Objects always on heap)
❌ "String pool is in heap" (Moved to heap in Java 7)

## Impressive Technical Details
✅ Mention escape analysis for stack allocation
✅ Know Metaspace replaced PermGen in Java 8
✅ Understand generational hypothesis
✅ Can explain JIT compilation thresholds
✅ Know difference between minor/major/full GC

## Common Follow-up Questions
Q: "How to debug OutOfMemoryError?"
A: Check error type → Generate heap dump → Analyze with MAT → Look for leaks

Q: "JVM flags for microservices?"
A: -XX:+UseContainerSupport, G1GC, smaller heap, fast startup flags

Q: "How to reduce GC pause times?"
A: Use G1/ZGC, tune pause time goals, optimize allocation patterns

## Production Experience Points
- "Used jstat to monitor GC in production"
- "Analyzed heap dumps with Eclipse MAT"
- "Tuned G1GC pause times from 200ms to 50ms"
- "Fixed memory leak in static cache causing OOM"