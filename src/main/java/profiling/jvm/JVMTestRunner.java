package profiling.jvm;// Test runner to demonstrate all JVM concepts

public class JVMTestRunner {
    
    public static void main(String[] args) {
        System.out.println("=== JVM & Memory Concepts Demo ===\n");
        
        // 1. Memory demonstration
        System.out.println("1. Running Memory Demo...");
        MemoryDemo.main(args);
        
        System.out.println("\n" + "=".repeat(50) + "\n");
        
        // 2. Class loading demonstration
        System.out.println("2. Running Class Loading Demo...");
        ClassLoadingDemo.main(args);
        
        System.out.println("\n" + "=".repeat(50) + "\n");
        
        // 3. Garbage collection demonstration
        System.out.println("3. Running GC Demo...");
        GCDemo.main(args);
        
        System.out.println("\n" + "=".repeat(50) + "\n");
        
        // 4. JIT compilation demonstration
        System.out.println("4. Running JIT Demo...");
        JITDemo.main(args);
        
        System.out.println("\n" + "=".repeat(50) + "\n");
        
        // 5. Performance monitoring
        System.out.println("5. Performance Monitoring...");
        PerformanceMonitor.printMemoryUsage();
        PerformanceMonitor.printGCInfo();
        
        // 6. Benchmark example
        System.out.println("\n6. Benchmarking...");
        PerformanceMonitor.benchmark(() -> {
            String result = "Hello" + " " + "World";
        }, "String concatenation");
        
        System.out.println("\n=== Demo Complete ===");
        System.out.println("Check the other files for detailed explanations and interview questions!");
    }
}