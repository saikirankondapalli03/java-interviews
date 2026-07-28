# Java 8 — Streams & Functional Programming Interview Guide

**Goal:** Answer any interview question on lambdas, functional interfaces, streams, collectors, Optional, and parallel streams.

**How to use tonight:** Read Sections 1–6 carefully. Skim Section 7–8. Do the coding drills in `CODING-DRILLS.md`. Morning: read `CHEAT-SHEET.md` only.

---

## 1. Functional Programming Basics (Java 8)

### Q1. What is functional programming in Java 8?

**Answer:** Treating functions as first-class values — pass them as arguments, return them, store them in variables. Java 8 added:
- Lambda expressions
- Functional interfaces
- Method references
- Stream API
- Optional
- `java.util.function` package

Java is still OOP-first; FP is an additional style used especially for data transformation pipelines.

### Q2. What is a lambda expression?

**Answer:** A concise way to implement a functional interface (an interface with exactly one abstract method).

```java
// Before Java 8
Runnable r = new Runnable() {
    @Override
    public void run() {
        System.out.println("Hi");
    }
};

// Java 8
Runnable r = () -> System.out.println("Hi");
```

**Syntax forms:**
```java
(a, b) -> a + b                    // expression body
(a, b) -> { return a + b; }        // block body
x -> x * 2                         // single param, no parens needed
() -> 42                           // no params
(String s) -> s.length()           // explicit types
```

### Q3. What is a functional interface?

**Answer:** An interface with **exactly one abstract method** (SAM — Single Abstract Method). It may have default/static methods.

Marked with `@FunctionalInterface` (optional but recommended — compiler enforces SAM rule).

```java
@FunctionalInterface
interface Calculator {
    int calc(int a, int b);   // only abstract method

    default void print() { System.out.println("ok"); }  // allowed
    static void help() {}                                // allowed
}
```

### Q4. Built-in functional interfaces you MUST know

| Interface | Method | Input → Output | Example |
|-----------|--------|----------------|---------|
| `Predicate<T>` | `test(T)` | T → boolean | `x -> x > 10` |
| `Function<T,R>` | `apply(T)` | T → R | `s -> s.length()` |
| `Consumer<T>` | `accept(T)` | T → void | `System.out::println` |
| `Supplier<T>` | `get()` | () → T | `() -> new Date()` |
| `UnaryOperator<T>` | `apply(T)` | T → T | `x -> x * 2` |
| `BinaryOperator<T>` | `apply(T,T)` | (T,T) → T | `Integer::sum` |
| `BiFunction<T,U,R>` | `apply(T,U)` | (T,U) → R | `(a,b) -> a + b` |
| `BiPredicate<T,U>` | `test(T,U)` | (T,U) → boolean | `(a,b) -> a.equals(b)` |
| `BiConsumer<T,U>` | `accept(T,U)` | (T,U) → void | `map::put` |

**Primitive specializations (avoid boxing):**
`IntPredicate`, `IntFunction`, `ToIntFunction`, `IntConsumer`, `IntSupplier`, `IntBinaryOperator`, etc. Same for `Long*` and `Double*`.

### Q5. What is a method reference?

**Answer:** Shorthand for a lambda that only calls an existing method.

| Type | Lambda | Method reference |
|------|--------|------------------|
| Static | `x -> Math.abs(x)` | `Math::abs` |
| Instance (bound) | `() -> str.toLowerCase()` | `str::toLowerCase` |
| Instance (unbound) | `s -> s.toLowerCase()` | `String::toLowerCase` |
| Constructor | `() -> new ArrayList<>()` | `ArrayList::new` |
| Array ctor | `n -> new int[n]` | `int[]::new` |

### Q6. Effective final — what can lambdas capture?

**Answer:** Lambdas can capture local variables only if they are **final or effectively final** (never reassigned after initialization).

```java
int x = 10;
Runnable r = () -> System.out.println(x); // OK — effectively final
// x = 20; // would make the lambda illegal
```

**Instance/static fields** can be mutated — they are not subject to the effectively-final rule (but be careful with concurrency).

### Q7. Lambda vs anonymous inner class — differences?

| Point | Lambda | Anonymous class |
|-------|--------|-----------------|
| `this` | Enclosing class | Anonymous class itself |
| Bytecode | `invokedynamic` | Separate `.class` file |
| Scope | Cannot shadow enclosing locals the same way | Can declare own fields |
| Functional | Only for SAM interfaces | Any interface/class |

**Interview favorite — `this`:**
```java
class Demo {
    void test() {
        Runnable r1 = () -> System.out.println(this);      // Demo instance
        Runnable r2 = new Runnable() {
            public void run() { System.out.println(this); } // Runnable instance
        };
    }
}
```

---

## 2. Stream API — Core Model

### Q8. What is a Stream?

**Answer:** A sequence of elements supporting sequential/parallel aggregate operations. It is **not a data structure** — it does not store elements; it pipelines operations over a source (collection, array, generator, I/O, etc.).

**Three parts of a stream pipeline:**
1. **Source** — `list.stream()`, `Stream.of(...)`, `Files.lines(...)`
2. **Intermediate ops** — lazy, return a Stream (`filter`, `map`, `flatMap`, `sorted`, `distinct`, `peek`, `limit`, `skip`)
3. **Terminal op** — triggers execution, produces result or side effect (`collect`, `forEach`, `reduce`, `count`, `findFirst`, `anyMatch`, …)

### Q9. Lazy evaluation — why does it matter?

**Answer:** Intermediate operations are not executed until a terminal operation is invoked. This enables:
- Fusion of operations (one pass when possible)
- Short-circuiting (`findFirst`, `anyMatch`, `limit`)

```java
List<String> result = names.stream()
    .filter(n -> {
        System.out.println("filter: " + n);
        return n.length() > 3;
    })
    .map(n -> {
        System.out.println("map: " + n);
        return n.toUpperCase();
    })
    .limit(1)
    .collect(Collectors.toList());
// Only processes as many elements as needed for limit(1)
```

### Q10. Can you reuse a Stream?

**Answer:** **No.** A stream can be consumed only once. After a terminal operation, further use throws `IllegalStateException`.

```java
Stream<String> s = list.stream();
s.forEach(System.out::println);
s.count(); // IllegalStateException
```

Create a new stream from the source each time.

### Q11. Collection vs Stream?

| | Collection | Stream |
|--|------------|--------|
| Storage | Stores elements | No storage |
| Iteration | External (you control loop) | Internal (library controls) |
| Consumption | Reusable | One-shot |
| Purpose | Data structure | Computation pipeline |
| Modification | Can add/remove | Source usually unchanged |

### Q12. How do you create streams?

```java
list.stream();
list.parallelStream();
Arrays.stream(array);
Stream.of(1, 2, 3);
Stream.empty();
Stream.generate(() -> Math.random()).limit(5);   // infinite + limit
Stream.iterate(0, n -> n + 2).limit(10);         // 0,2,4,...
IntStream.range(1, 5);                           // 1,2,3,4
IntStream.rangeClosed(1, 5);                     // 1..5
"abc".chars();                                   // IntStream of code points
BufferedReader::lines / Files.lines(path);
Pattern.compile(",").splitAsStream("a,b,c");
Stream.concat(s1, s2);
```

---

## 3. Intermediate Operations (Must Verbalize Clearly)

### Q13. `filter` vs `map` vs `flatMap`?

- **`filter(Predicate)`** — keep/drop elements (size may shrink)
- **`map(Function)`** — 1→1 transform (same count)
- **`flatMap(Function)`** — 1→N transform, then flatten into one stream

```java
// map: List<String> → Stream<List<String>>? No — each word → length
words.stream().map(String::length);

// flatMap: each employee has List<String> skills → one stream of skills
employees.stream()
    .flatMap(e -> e.getSkills().stream())
    .distinct()
    .collect(Collectors.toList());

// Classic: flatten List<List<T>>
listOfLists.stream()
    .flatMap(Collection::stream)
    .collect(Collectors.toList());
```

**Interview line:** *map wraps; flatMap flattens.*  
`map` of stream → `Stream<Stream<T>>`. `flatMap` → `Stream<T>`.

### Q14. `distinct`, `sorted`, `limit`, `skip`, `peek`?

```java
stream.distinct();                 // equals/hashCode based
stream.sorted();                   // natural order
stream.sorted(Comparator.reverseOrder());
stream.sorted(Comparator.comparing(Employee::getSalary)
                        .thenComparing(Employee::getName));
stream.limit(10);                  // short-circuiting stateful
stream.skip(5);
stream.peek(System.out::println);  // debug side-effect; avoid in prod logic
```

**Stateful vs stateless intermediate ops:**
- **Stateless:** `filter`, `map`, `flatMap`, `peek` — each element independent
- **Stateful:** `distinct`, `sorted`, `limit`, `skip` — may need to see previous elements / buffer

Stateful ops are more expensive, especially in parallel streams.

### Q15. `mapToInt` / primitive streams — why?

**Answer:** Avoid boxing/unboxing overhead.

```java
int sum = employees.stream()
    .mapToInt(Employee::getSalary)
    .sum();

OptionalDouble avg = IntStream.of(1,2,3).average();
IntSummaryStatistics stats = IntStream.rangeClosed(1,100).summaryStatistics();
// stats.getSum(), getAverage(), getMin(), getMax(), getCount()
```

Convert back: `intStream.boxed()` → `Stream<Integer>`, or `mapToObj(...)`.

---

## 4. Terminal Operations

### Q16. Common terminal ops

```java
collect(collector);          // most important
forEach(consumer);
forEachOrdered(consumer);    // encounter order in parallel
reduce(identity, accumulator);
reduce(accumulator);         // Optional
count();
min(comparator) / max(comparator);
findFirst() / findAny();     // Optional; findAny better for parallel
anyMatch / allMatch / noneMatch(predicate);  // short-circuit
toArray() / toArray(String[]::new);
```

### Q17. `findFirst` vs `findAny`?

- **`findFirst`** — first element in encounter order (stable)
- **`findAny`** — any element; may be faster in parallel (no order constraint)

Prefer `findAny` when order does not matter and you use parallel streams.

### Q18. Explain `reduce` deeply (interview favorite)

**Reduction** combines stream elements into a single value.

```java
// 1) Optional reduce — no identity
Optional<Integer> sum = nums.stream().reduce((a, b) -> a + b);
Optional<Integer> sum2 = nums.stream().reduce(Integer::sum);

// 2) With identity
int sum3 = nums.stream().reduce(0, Integer::sum);

// 3) Three-arg reduce (needed for parallel / type-changing)
int totalSalary = employees.parallelStream()
    .reduce(0,
            (sum, e) -> sum + e.getSalary(),  // accumulator
            Integer::sum);                    // combiner
```

**Identity rules:**
- Identity must be such that `accumulator.apply(identity, x) == x`
- Combiner must be associative for parallel correctness

**`collect` vs `reduce`:**  
Prefer **`collect`** for mutable containers (`List`, `Map`, `StringBuilder`). Prefer **`reduce`** for immutable reductions (sum, max of numbers). Using `reduce` to build lists is inefficient and error-prone under parallelism.

### Q19. Short-circuiting operations?

Terminal: `findFirst`, `findAny`, `anyMatch`, `allMatch`, `noneMatch`  
Intermediate: `limit`, (effectively also impacts `takeWhile` in Java 9+)

They can stop processing early without consuming the whole source.

---

## 5. Collectors — Highest Interview Density

### Q20. What is a Collector?

**Answer:** A recipe for mutable reduction: supplier + accumulator + combiner + finisher (+ characteristics).

```java
list.stream().collect(Collectors.toList());
```

### Q21. Collectors you must know cold

```java
Collectors.toList();
Collectors.toSet();
Collectors.toCollection(TreeSet::new);
Collectors.toMap(Employee::getId, Function.identity());
Collectors.toMap(Employee::getId, Employee::getName, (a, b) -> a); // merge fn for dup keys
Collectors.toConcurrentMap(...);

Collectors.joining(", ");
Collectors.joining(", ", "[", "]");

Collectors.counting();
Collectors.summingInt(Employee::getSalary);
Collectors.averagingDouble(Employee::getSalary);
Collectors.summarizingInt(Employee::getSalary); // IntSummaryStatistics

Collectors.maxBy(Comparator.comparing(Employee::getSalary));
Collectors.minBy(...);

Collectors.reducing(...);
Collectors.mapping(Employee::getName, Collectors.toList());
Collectors.filtering(e -> e.getSalary() > 100k, Collectors.toList()); // Java 9+
Collectors.flatMapping(...); // Java 9+
Collectors.collectingAndThen(Collectors.toList(), Collections::unmodifiableList);
```

### Q22. `groupingBy` and `partitioningBy` (MUST KNOW)

```java
// group employees by department
Map<Dept, List<Employee>> byDept =
    employees.stream()
        .collect(Collectors.groupingBy(Employee::getDept));

// group + downstream collector
Map<Dept, Long> countByDept =
    employees.stream()
        .collect(Collectors.groupingBy(Employee::getDept, Collectors.counting()));

Map<Dept, Integer> salaryByDept =
    employees.stream()
        .collect(Collectors.groupingBy(Employee::getDept,
                 Collectors.summingInt(Employee::getSalary)));

Map<Dept, Optional<Employee>> topEarner =
    employees.stream()
        .collect(Collectors.groupingBy(Employee::getDept,
                 Collectors.maxBy(Comparator.comparing(Employee::getSalary))));

// multi-level grouping
Map<Dept, Map<Level, List<Employee>>> nested =
    employees.stream()
        .collect(Collectors.groupingBy(Employee::getDept,
                 Collectors.groupingBy(Employee::getLevel)));

// partitioningBy — always Map<Boolean, List<T>>
Map<Boolean, List<Employee>> partitioned =
    employees.stream()
        .collect(Collectors.partitioningBy(e -> e.getSalary() > 100_000));
```

**`groupingBy` vs `partitioningBy`:**
- `partitioningBy` → classifier is `Predicate` → keys are only `true`/`false`
- `groupingBy` → classifier is `Function` → arbitrary keys

**Parallel tip:** `groupingByConcurrent` for parallel streams when order doesn't matter.

### Q23. `toMap` duplicate key problem

```java
// Throws IllegalStateException on duplicate keys
Collectors.toMap(Employee::getDept, Employee::getName);

// Fix: provide merge function
Collectors.toMap(Employee::getDept, Employee::getName, (n1, n2) -> n1 + "|" + n2);

// Or pick one
Collectors.toMap(Employee::getId, Function.identity(), (a, b) -> a, TreeMap::new);
```

---

## 6. Optional

### Q24. What is Optional and why?

**Answer:** A container that may or may not hold a non-null value. Designed to make absence explicit in return types and reduce NPEs. **Not** meant to replace every null.

```java
Optional<Employee> opt = employees.stream()
    .filter(e -> e.getId() == 10)
    .findFirst();
```

### Q25. Optional API essentials

```java
Optional.of(value);          // NPE if value null
Optional.ofNullable(value);  // empty if null
Optional.empty();

opt.isPresent();             // prefer ifPresent / map when possible
opt.isEmpty();               // Java 11+
opt.get();                   // avoid — may throw NoSuchElementException
opt.orElse(defaultValue);
opt.orElseGet(Supplier);     // lazy — prefer when default is expensive
opt.orElseThrow();
opt.orElseThrow(MyEx::new);

opt.ifPresent(System.out::println);
opt.ifPresentOrElse(System.out::println, () -> System.out.println("none")); // Java 9+

opt.map(Employee::getName);
opt.flatMap(Employee::getOptionalAddress);
opt.filter(e -> e.getSalary() > 0);
opt.stream();                // Java 9+ — 0 or 1 element Stream
```

### Q26. `orElse` vs `orElseGet` trap

```java
opt.orElse(getExpensiveDefault());     // ALWAYS calls getExpensiveDefault()
opt.orElseGet(this::getExpensiveDefault); // calls only if empty
```

### Q27. Optional anti-patterns (say this in interview)

- Don't use Optional as method **parameter** (overloads / clear API better)
- Don't use Optional as **field** (usually)
- Don't wrap collections — return empty list instead
- Don't call `get()` without checking / prefer `orElse*`
- Don't use Optional just to chain everything — keep it readable

---

## 7. Parallel Streams

### Q28. How do parallel streams work?

**Answer:** Use the common **ForkJoinPool** (`ForkJoinPool.commonPool()`). Work is split (if spliterator allows), processed in chunks, then combined.

```java
list.parallelStream()...
stream.parallel()...
stream.sequential()...
```

### Q29. When to use / when NOT to use parallel streams?

**Use when:**
- Large dataset (typically tens of thousands+ elements — measure!)
- CPU-bound, independent operations
- Associative reductions / good split characteristics (ArrayList, arrays — good; LinkedList — poor)
- No shared mutable state

**Avoid when:**
- Small data
- I/O bound / blocking calls (unless custom pool — advanced)
- Order-sensitive ops with heavy stateful intermediates
- Shared mutable state without synchronization
- In latency-sensitive request threads (common pool contention)
- Operations with side effects

**Interview line:** *Parallel is not free. Measure. Correctness first, then throughput.*

### Q30. Parallel stream pitfalls

```java
// BAD — race on shared mutable list
List<Integer> out = new ArrayList<>();
list.parallelStream().forEach(out::add); // not thread-safe

// GOOD
List<Integer> out = list.parallelStream().collect(Collectors.toList());
```

- `forEach` order is nondeterministic in parallel; use `forEachOrdered` if needed (costs performance)
- `findFirst` more expensive than `findAny` in parallel
- Avoid `peek`/side effects
- Boxing-heavy pipelines may not scale

### Q31. Can you set parallel stream thread count?

Common pool size ≈ `Runtime.getRuntime().availableProcessors() - 1` (implementation detail varies).

```java
System.setProperty("java.util.concurrent.ForkJoinPool.common.parallelism", "8");
```

Better for isolation: submit work to a **custom ForkJoinPool** (advanced interview follow-up). Changing common pool affects whole JVM.

---

## 8. Ordering, Spliterators, Internals (Senior Differentiators)

### Q32. Encounter order?

- Ordered sources (List, array) preserve encounter order unless you call `unordered()`
- Some ops don't care about order (`distinct` may be faster if unordered)
- Sets may be unordered depending on implementation

### Q33. What is a Spliterator?

**Answer:** The parallel analogue of Iterator — can **split** (`trySplit`) for Fork/Join, and reports characteristics (`ORDERED`, `SIZED`, `DISTINCT`, `SORTED`, `NONNULL`, `IMMUTABLE`, `CONCURRENT`, `SUBSIZED`).

You usually don't write one, but knowing it explains why `ArrayList` parallelizes better than `LinkedList`.

### Q34. How does Stream work under the hood? (short version)

1. Source provides a `Spliterator`
2. Intermediate ops build a pipeline of stages (linked list of ops)
3. Terminal op triggers evaluation
4. For parallel: split spliterator → ForkJoin tasks → combine results
5. Lambdas use `invokedynamic` + LambdaMetafactory (not anonymous classes)

### Q35. Infinite streams — how do they not hang?

Use short-circuiting: `limit`, `findFirst`, `anyMatch`, etc.

```java
Stream.iterate(0, n -> n + 1)
    .filter(n -> n % 2 == 0)
    .limit(10)
    .forEach(System.out::println);
```

Without short-circuit, an infinite stream + non-short-circuit terminal (`collect`, `count` without limit) never finishes.

---

## 9. Classic Coding Questions (Verbalize Pattern First)

### Pattern A — filter → map → collect
```java
List<String> names = employees.stream()
    .filter(e -> e.getSalary() > 100_000)
    .map(Employee::getName)
    .sorted()
    .collect(Collectors.toList());
```

### Pattern B — group and aggregate
```java
Map<String, Double> avgSalaryByDept = employees.stream()
    .collect(Collectors.groupingBy(Employee::getDept,
             Collectors.averagingDouble(Employee::getSalary)));
```

### Pattern C — frequency map
```java
Map<String, Long> freq = words.stream()
    .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
```

### Pattern D — second highest / Nth
```java
OptionalInt second = nums.stream()
    .mapToInt(Integer::intValue)
    .distinct()
    .boxed()
    .sorted(Comparator.reverseOrder())
    .skip(1)
    .mapToInt(Integer::intValue)
    .findFirst();
```

### Pattern E — join strings
```java
String csv = list.stream().map(Object::toString).collect(Collectors.joining(", "));
```

### Pattern F — flatMap objects
```java
Set<String> allSkills = employees.stream()
    .flatMap(e -> e.getSkills().stream())
    .collect(Collectors.toSet());
```

### Pattern G — partition pass/fail
```java
Map<Boolean, List<Integer>> parts = nums.stream()
    .collect(Collectors.partitioningBy(n -> n % 2 == 0));
```

---

## 10. Rapid-Fire Q&A (Exact Phrases to Say)

| Question | Crisp answer |
|----------|--------------|
| Is Stream a data structure? | No — a view for computation over a source |
| Intermediate ops return? | Another Stream; lazy |
| What triggers execution? | Terminal operation |
| Can stream be reused? | No |
| filter vs map? | filter selects; map transforms 1:1 |
| map vs flatMap? | flatMap maps to streams and flattens |
| reduce vs collect? | reduce immutable; collect mutable reduction |
| findFirst vs findAny? | order vs any; findAny better parallel |
| orElse vs orElseGet? | orElse always evaluates default |
| parallel default pool? | ForkJoinPool.commonPool() |
| When parallel? | large CPU-bound, measured, no shared mutation |
| Functional interface? | Exactly one abstract method |
| Why @FunctionalInterface? | Compile-time SAM enforcement |
| Predicate composition? | `and`, `or`, `negate` |
| Function composition? | `compose`, `andThen` |

### Predicate / Function composition examples
```java
Predicate<Integer> gt10 = x -> x > 10;
Predicate<Integer> even = x -> x % 2 == 0;
Predicate<Integer> both = gt10.and(even);

Function<String, String> trim = String::trim;
Function<String, String> upper = String::toUpperCase;
Function<String, String> pipeline = trim.andThen(upper);
```

---

## 11. Common Interview Traps

1. **Modifying source while streaming** → `ConcurrentModificationException` (fail-fast iterators)
2. **Checked exceptions inside lambdas** — must catch or wrap in runtime exception (lambdas can't throw checked unless functional interface declares them)
3. **`Collectors.toList()`** — returns mutable List but type not guaranteed `ArrayList` (Java 16+ `Stream.toList()` returns unmodifiable)
4. **Nulls in streams** — `map`/`filter` NPEs; filter nulls explicitly or use Optional carefully
5. **`sorted()` on parallel stream of large data** — expensive global barrier
6. **Using `peek` for business logic** — side effects; use `map`/`forEach` appropriately
7. **Saying parallel always faster** — wrong; interviewers expect nuance
8. **`distinct()` without proper equals/hashCode** — subtle bugs
9. **Building lists with `reduce`** — prefer `collect`
10. **Optional.get() everywhere** — red flag

---

## 12. Java 8 vs Later (if they ask)

| Feature | Version |
|---------|---------|
| Lambdas, Streams, Optional, new Date/Time | Java 8 |
| `takeWhile`, `dropWhile`, `ofNullable`, `Optional.stream` | Java 9 |
| `var` for locals, `Collectors.toUnmodifiable*` | Java 10 |
| `String.isBlank`, `Optional.isEmpty`, new HTTP client | Java 11 |
| `Stream.toList()` (unmodifiable) | Java 16 |
| Pattern matching, records (often used with streams) | Java 16/17 |

For a "Java 8" interview, stay fluent in Java 8 APIs; mentioning Java 9+/16 differences shows currency.

---

## 13. 60-Second Closing Narrative (Memorize)

> "Java 8 brought functional style through lambdas and functional interfaces. Streams let us express data pipelines declaratively: source, lazy intermediate ops like filter/map/flatMap, and terminal ops like collect/reduce. I prefer collect for mutable results and groupingBy for aggregations. Optional makes absence explicit on return types. Parallel streams use the common ForkJoinPool — I use them only for large CPU-bound workloads after measuring, and I avoid shared mutable state."

If you can say that confidently and write the patterns in Section 9, you can handle essentially any Streams/FP interview round.
