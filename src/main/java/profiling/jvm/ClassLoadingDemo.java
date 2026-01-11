package profiling.jvm;// Class Loading demonstration

public class ClassLoadingDemo {
    
    public static void main(String[] args) {
        demonstrateClassLoaders();
        demonstrateClassLoadingPhases();
    }
    
    // Three types of class loaders
    public static void demonstrateClassLoaders() {
        // Bootstrap ClassLoader (loads core Java classes)
        System.out.println("String class loader: " + String.class.getClassLoader());
        
        // Extension ClassLoader (loads extension classes)
        // Application ClassLoader (loads application classes)
        System.out.println("This class loader: " + ClassLoadingDemo.class.getClassLoader());
        
        // Parent delegation model
        ClassLoader appLoader = ClassLoadingDemo.class.getClassLoader();
        System.out.println("Parent of app loader: " + appLoader.getParent());
        System.out.println("Parent of ext loader: " + appLoader.getParent().getParent());
    }
    
    // Class loading phases: Loading -> Linking -> Initialization
    public static void demonstrateClassLoadingPhases() {
        System.out.println("=== Class Loading Phases ===");
        
        // 1. Loading: .class file -> binary data in method area
        // 2. Linking: 
        //    - Verification: bytecode verification
        //    - Preparation: allocate memory for static variables
        //    - Resolution: symbolic references -> direct references
        // 3. Initialization: execute static initializers
        
        // This triggers class loading of LazyClass
        LazyClass.staticMethod();
    }
}

class LazyClass {
    // Static block executes during initialization phase
    static {
        System.out.println("LazyClass static block executed - Initialization phase");
    }
    
    // Static variable prepared during preparation phase
    private static int staticVar = initializeStaticVar();
    
    private static int initializeStaticVar() {
        System.out.println("Static variable initialized");
        return 42;
    }
    
    public static void staticMethod() {
        System.out.println("Static method called, staticVar = " + staticVar);
    }
}