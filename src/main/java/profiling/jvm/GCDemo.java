package profiling.jvm;// Garbage Collection demonstration

public class GCDemo {
    
    public static void main(String[] args) {
        demonstrateGenerations();
        demonstrateGCTypes();
        demonstrateEscapeAnalysis();
    }
    
    // Heap generations: Young (Eden + S0 + S1) + Old
    public static void demonstrateGenerations() {
        System.out.println("=== Heap Generations ===");
        
        // Objects start in Eden space (Young Generation)
        for (int i = 0; i < 1000; i++) {
            String temp = new String("Object " + i);
            // Most objects die young (short-lived)
        }
        
        // Long-lived objects eventually move to Old Generation
        String longLived = "I will survive multiple GC cycles";
        
        // Force minor GC (Young Generation)
        System.gc();
        
        System.out.println("Objects created and GC suggested");
    }
    
    // GC Types and their characteristics
    public static void demonstrateGCTypes() {
        System.out.println("=== GC Types ===");
        
        /*
        Serial GC (-XX:+UseSerialGC):
        - Single-threaded
        - Good for small applications
        - Stop-the-world for both minor and major GC
        
        Parallel GC (-XX:+UseParallelGC) [Default in Java 8]:
        - Multi-threaded
        - Good for throughput
        - Stop-the-world but parallel threads
        
        CMS GC (-XX:+UseConcMarkSweepGC) [Deprecated in Java 9]:
        - Concurrent collection for Old Generation
        - Lower pause times
        - More CPU overhead
        
        G1 GC (-XX:+UseG1GC) [Default in Java 9+]:
        - Low-latency collector
        - Divides heap into regions
        - Predictable pause times
        
        ZGC (-XX:+UseZGC) [Java 11+]:
        - Ultra-low latency
        - Concurrent collection
        - Colored pointers technique
        
        Shenandoah (-XX:+UseShenandoahGC) [Java 12+]:
        - Low pause times
        - Concurrent collection
        - Independent of heap size
        */
        
        // Print current GC
        java.lang.management.ManagementFactory.getGarbageCollectorMXBeans()
            .forEach(gc -> System.out.println("GC: " + gc.getName()));
    }
    
    // Escape Analysis - JIT optimization
    public static void demonstrateEscapeAnalysis() {
        System.out.println("=== Escape Analysis ===");
        
        // Object doesn't escape method - can be stack allocated
        localObject();
        
        // Object escapes - must be heap allocated
        Object escaped = escapingObject();
    }
    
    private static void localObject() {
        // This object doesn't escape the method
        // JIT can optimize to stack allocation
        StringBuilder sb = new StringBuilder();
        sb.append("Local");
        String result = sb.toString();
        // sb becomes eligible for optimization
    }
    
    private static Object escapingObject() {
        // This object escapes the method
        // Must be allocated on heap
        return new StringBuilder("Escaping");
    }
}

// Memory leak examples
class MemoryLeakExamples {
    
    // 1. Static collections that keep growing
    private static java.util.List<String> staticList = new java.util.ArrayList<>();
    
    // 2. Listeners not removed
    private java.util.List<Object> listeners = new java.util.ArrayList<>();
    
    // 3. ThreadLocal not cleaned
    private static ThreadLocal<String> threadLocal = new ThreadLocal<>();
    
    public void addToStaticList(String item) {
        staticList.add(item); // Memory leak if not managed
    }
    
    public void addListener(Object listener) {
        listeners.add(listener); // Should remove when done
    }
    
    public void setThreadLocal(String value) {
        threadLocal.set(value); // Should call remove() when done
    }
    
    // Proper cleanup
    public void cleanup() {
        listeners.clear();
        threadLocal.remove();
    }
}