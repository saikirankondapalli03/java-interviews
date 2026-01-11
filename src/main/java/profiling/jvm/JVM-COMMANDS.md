# JVM Analysis Commands & Tools

## Essential JVM Flags

### Memory Settings
```bash
# Heap size
-Xms2g          # Initial heap size
-Xmx4g          # Maximum heap size
-XX:NewRatio=3  # Old/Young generation ratio

# Metaspace (Java 8+)
-XX:MetaspaceSize=256m
-XX:MaxMetaspaceSize=512m

# Stack size
-Xss1m          # Thread stack size
```

### GC Configuration
```bash
# G1 GC (recommended for most applications)
-XX:+UseG1GC
-XX:MaxGCPauseMillis=200
-XX:G1HeapRegionSize=16m

# Parallel GC
-XX:+UseParallelGC
-XX:ParallelGCThreads=4

# ZGC (Java 11+)
-XX:+UseZGC
-XX:+UnlockExperimentalVMOptions

# CMS (deprecated)
-XX:+UseConcMarkSweepGC
-XX:+CMSIncrementalMode
```

### GC Logging
```bash
# Java 8
-XX:+PrintGC
-XX:+PrintGCDetails
-XX:+PrintGCTimeStamps
-Xloggc:gc.log

# Java 9+
-Xlog:gc*:gc.log:time
-Xlog:gc,heap:gc-heap.log
```

## JVM Diagnostic Tools

### Command Line Tools
```bash
# Process information
jps                              # List Java processes
jps -v                          # With JVM arguments

# Memory analysis
jmap -histo <pid>               # Histogram of objects
jmap -dump:format=b,file=heap.hprof <pid>  # Heap dump

# Thread analysis
jstack <pid>                    # Thread dump
jstack -l <pid>                 # With lock information

# JVM information
jinfo <pid>                     # JVM flags and properties
jinfo -flag <flag> <pid>        # Specific flag value

# Statistics
jstat -gc <pid> 1s              # GC stats every second
jstat -gccapacity <pid>         # Heap capacity info
jstat -gcutil <pid> 5s 10       # GC utilization, 5s interval, 10 times

# Multi-purpose tool
jcmd <pid> help                 # Available commands
jcmd <pid> VM.flags             # JVM flags
jcmd <pid> GC.run               # Force GC
jcmd <pid> Thread.print         # Thread dump
```

### Memory Analysis
```bash
# Heap dump analysis with Eclipse MAT
# 1. Generate heap dump
jcmd <pid> GC.run
jmap -dump:live,format=b,file=heap.hprof <pid>

# 2. Analyze with MAT or VisualVM
# Look for:
# - Memory leaks
# - Large objects
# - Duplicate strings
# - Class loader leaks
```

### GC Log Analysis
```bash
# GC log analysis tools:
# - GCViewer
# - GCPlot.com
# - CRaC (Coordinated Restore at Checkpoint)

# Key metrics to monitor:
# - Pause times
# - Throughput
# - Allocation rate
# - Promotion rate
```

## Performance Tuning Checklist

### 1. Memory Tuning
- [ ] Set appropriate heap size (-Xms, -Xmx)
- [ ] Choose right GC algorithm
- [ ] Monitor allocation patterns
- [ ] Check for memory leaks

### 2. GC Tuning
- [ ] Analyze GC logs
- [ ] Tune pause time goals
- [ ] Adjust generation sizes
- [ ] Monitor GC overhead

### 3. JIT Tuning
- [ ] Monitor compilation thresholds
- [ ] Check for deoptimization
- [ ] Profile hot methods
- [ ] Optimize critical paths

## Common Issues & Solutions

### OutOfMemoryError Types
```java
// 1. Java heap space
// Solution: Increase -Xmx or fix memory leaks

// 2. Metaspace (Java 8+)
// Solution: Increase -XX:MaxMetaspaceSize

// 3. GC overhead limit exceeded
// Solution: Increase heap or optimize GC

// 4. Direct buffer memory
// Solution: Increase -XX:MaxDirectMemorySize
```

### Memory Leak Detection
```bash
# 1. Monitor heap usage over time
jstat -gc <pid> 10s

# 2. Compare heap dumps
jmap -dump:format=b,file=heap1.hprof <pid>
# ... wait some time ...
jmap -dump:format=b,file=heap2.hprof <pid>

# 3. Analyze with MAT
# Look for objects that should be GC'd but aren't
```

### Performance Monitoring
```bash
# Application performance
java -XX:+PrintCompilation MyApp

# GC performance
java -XX:+PrintGCApplicationStoppedTime MyApp

# JIT compilation
java -XX:+TraceClassLoading MyApp
```