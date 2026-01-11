package profiling.jvm;// Memory demonstration examples

public class MemoryDemo {
    
    // Static variable - stored in Method Area
    private static int staticCounter = 0;
    
    // Instance variable - stored in Heap
    private String instanceData;
    
    public static void main(String[] args) {
        // Demonstrate stack vs heap
        stackVsHeapDemo();
        
        // Demonstrate object lifecycle
        objectLifecycleDemo();
        
        // Demonstrate memory leak
        memoryLeakDemo();
    }
    
    // Stack: method parameters, local variables
    // Heap: objects created with 'new'
    public static void stackVsHeapDemo() {
        int localVar = 42;           // Stack
        String str = "Hello";        // String literal in String Pool (Heap)
        MemoryDemo obj = new MemoryDemo(); // Object reference on Stack, object on Heap
        
        System.out.println("Stack variable: " + localVar);
        System.out.println("Heap object: " + obj);
    }
    
    // Object lifecycle: creation -> usage -> eligible for GC -> collected
    public static void objectLifecycleDemo() {
        // Creation phase
        MemoryDemo obj1 = new MemoryDemo();
        obj1.instanceData = "Active object";
        
        // Usage phase
        System.out.println(obj1.instanceData);
        
        // Eligible for GC (no more references)
        obj1 = null;
        
        // Suggest GC (not guaranteed)
        System.gc();
    }
    
    // Memory leak example - objects not eligible for GC
    public static void memoryLeakDemo() {
        java.util.List<String> list = new java.util.ArrayList<>();
        
        // This could cause memory leak if list keeps growing
        for (int i = 0; i < 1000; i++) {
            list.add("String " + i);
        }
        
        // list still has references, objects won't be GC'd
        System.out.println("List size: " + list.size());
    }
}