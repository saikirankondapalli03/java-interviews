# JVM & Memory Complete Guide

## Table of Contents
1. [JVM Architecture](#jvm-architecture)
2. [Memory Areas](#memory-areas)
3. [Class Loading](#class-loading)
4. [Garbage Collection](#garbage-collection)
5. [Performance Analysis](#performance-analysis)
6. [Practical Examples](#practical-examples)

## JVM Architecture

The JVM has three main components with concrete JDK examples:

### 1. Class Loader Subsystem
Loads .class files in hierarchical order:

- **Bootstrap ClassLoader** (C++ native)
  - Loads core Java classes: `String`, `Object`, `System` from `rt.jar` (Java 8) or modules (Java 9+)
  - Example: `java.lang.String`, `java.util.ArrayList`

- **Extension ClassLoader** (Java 8) / **Platform ClassLoader** (Java 9+)
  - Loads extension libraries from `$JAVA_HOME/lib/ext/`
  - Example: `javax.crypto.*`, `javax.swing.*`

- **Application ClassLoader** (System ClassLoader)
  - Loads your application classes from classpath
  - Example: Your `MyApp.class`, third-party JARs

### 2. Runtime Data Areas
Memory regions with specific purposes:

- **Method Area (Metaspace)**: Class metadata, static variables
  - Example: `MyClass.class` bytecode, `static int count`

- **Heap**: Object instances and arrays
  - Example: `new String("hello")`, `new int[100]`

- **Stack**: Method calls and local variables (per thread)
  - Example: Method parameters, local variables in `main()`

- **PC Register**: Current instruction pointer (per thread)
  - Example: Points to current bytecode instruction being executed

### 3. Execution Engine
Executes bytecode:

- **Interpreter**: Executes bytecode line by line
  - Example: Initial execution of `System.out.println()`

- **JIT Compiler**: Compiles hot code to native machine code
  - Example: Frequently called methods get compiled for performance

- **Garbage Collector**: Manages heap memory
  - Example: G1GC, ParallelGC cleaning up unused objects

```
┌─────────────────────────────────────┐
│           JVM Architecture          │
├─────────────────────────────────────┤
│  Class Loader Subsystem             │
│  ├── Bootstrap (String, Object)     │
│  ├── Platform (javax.*)             │
│  └── Application (MyApp.class)      │
├─────────────────────────────────────┤
│  Runtime Data Areas                 │
│  ├── Metaspace (class metadata)     │
│  ├── Heap (new String(), arrays)    │
│  ├── Stack (method calls, locals)   │
│  ├── PC Register (instruction ptr)  │
│  └── Native Method Stack            │
├─────────────────────────────────────┤
│  Execution Engine                   │
│  ├── Interpreter (bytecode exec)    │
│  ├── JIT Compiler (hot code)        │
│  └── Garbage Collector (G1, etc)    │
└─────────────────────────────────────┘
```

## Memory Areas

### Stack vs Heap
- **Stack**: Thread-local, stores method calls, local variables, partial results
- **Heap**: Shared, stores objects and instance variables

### Method Area (Metaspace in Java 8+)
- Stores class metadata, constant pool, static variables
- Replaced PermGen in Java 8

## Quick Reference Commands

```bash
# View JVM flags
java -XX:+PrintFlagsFinal -version

# Enable GC logging
java -XX:+PrintGC -XX:+PrintGCDetails MyApp

# Generate heap dump
jcmd <pid> GC.run_finalization
jcmd <pid> VM.gc
jmap -dump:format=b,file=heap.hprof <pid>

# Thread dump
jstack <pid>
```