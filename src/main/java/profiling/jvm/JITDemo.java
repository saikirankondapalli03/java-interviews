package profiling.jvm;// JIT Compilation and Performance Analysis

public class JITDemo {
    
    public static void main(String[] args) {
        demonstrateJITCompilation();
        demonstrateHotspotOptimizations();
    }
    
    // JIT (Just-In-Time) compilation demonstration
    public static void demonstrateJITCompilation() {
        System.out.println("=== JIT Compilation ===");
        
        /*
        JIT Compilation Process:
        1. Bytecode interpretation (slow)
        2. Profiling and hotspot detection
        3. Compilation to native code (fast)
        4. Optimizations (inlining, dead code elimination, etc.)
        */
        
        // Method will be interpreted first, then compiled after threshold
        long start = System.nanoTime();
        
        // Call method many times to trigger JIT compilation
        for (int i = 0; i < 100000; i++) {
            hotMethod(i);
        }
        
        long end = System.nanoTime();
        System.out.println("Time taken: " + (end - start) / 1_000_000 + " ms");
        
        // Second run should be faster due to JIT compilation
        start = System.nanoTime();
        for (int i = 0; i < 100000; i++) {
            hotMethod(i);
        }
        end = System.nanoTime();
        System.out.println("Second run: " + (end - start) / 1_000_000 + " ms");
    }
    
    // This method will become a "hotspot" and get JIT compiled
    private static int hotMethod(int x) {
        return x * 2 + 1;
    }
    
    // JIT Optimizations demonstration
    public static void demonstrateHotspotOptimizations() {
        System.out.println("=== JIT Optimizations ===");
        
        /*
        Common JIT Optimizations:
        1. Method Inlining
        2. Dead Code Elimination
        3. Loop Unrolling
        4. Escape Analysis
        5. Null Check Elimination
        6. Range Check Elimination
        */
        
        // Method inlining example
        for (int i = 0; i < 10000; i++) {
            int result = add(i, 5); // JIT will inline this method call
        }
        
        // Dead code elimination
        int x = 10;
        if (false) {
            System.out.println("This will be eliminated"); // Dead code
        }
        
        // Loop unrolling
        int sum = 0;
        for (int i = 0; i < 4; i++) {
            sum += i; // JIT might unroll this loop
        }
    }
    
    // Small method - candidate for inlining
    private static int add(int a, int b) {
        return a + b;
    }
}

// Performance monitoring utilities
class PerformanceMonitor {
    
    // Monitor memory usage
    public static void printMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        
        System.out.println("=== Memory Usage ===");
        System.out.println("Total: " + formatBytes(totalMemory));
        System.out.println("Used:  " + formatBytes(usedMemory));
        System.out.println("Free:  " + formatBytes(freeMemory));
        System.out.println("Max:   " + formatBytes(runtime.maxMemory()));
    }
    
    // Monitor GC activity
    public static void printGCInfo() {
        System.out.println("=== GC Information ===");
        java.lang.management.ManagementFactory.getGarbageCollectorMXBeans()
            .forEach(gc -> {
                System.out.println("GC Name: " + gc.getName());
                System.out.println("Collections: " + gc.getCollectionCount());
                System.out.println("Time: " + gc.getCollectionTime() + " ms");
            });
    }
    
    // Format bytes for readability
    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
        return (bytes / (1024 * 1024)) + " MB";
    }
    
    // Benchmark method execution
    public static void benchmark(Runnable task, String description) {
        System.out.println("Benchmarking: " + description);
        
        // Warm up JIT
        for (int i = 0; i < 10000; i++) {
            task.run();
        }
        
        // Actual benchmark
        long start = System.nanoTime();
        for (int i = 0; i < 100000; i++) {
            task.run();
        }
        long end = System.nanoTime();
        
        System.out.println("Average time: " + (end - start) / 100000 + " ns");
    }
}