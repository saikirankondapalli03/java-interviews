# Java 8 Streams & Functional Programming — Interview Q&A Bank

Answer these out loud. If you freeze, look at the answer once, then re-answer without looking.

---

## A. Lambdas & Functional Interfaces

### A1. What is a functional interface?
Interface with exactly one abstract method (SAM). May have default/static methods. `@FunctionalInterface` enforces this at compile time.

### A2. Why do we need functional interfaces for lambdas?
A lambda needs a target type. The compiler uses the SAM of the functional interface as that target type.

### A3. Difference between `Predicate`, `Function`, `Consumer`, `Supplier`?
- Predicate: T → boolean (`test`)
- Function: T → R (`apply`)
- Consumer: T → void (`accept`)
- Supplier: () → T (`get`)

### A4. What is `BiFunction` vs `BinaryOperator`?
Both take two args. `BinaryOperator<T>` is `BiFunction<T,T,T>` — same type in and out (e.g. `Integer::sum`).

### A5. What does effectively final mean?
A local variable whose value is never changed after assignment. Lambdas can only capture effectively final locals.

### A6. Can a lambda throw a checked exception?
Only if the functional interface method declares that checked exception. Otherwise wrap in try/catch or unchecked exception.

### A7. Lambda vs anonymous class — `this` keyword?
Lambda `this` = enclosing class. Anonymous class `this` = the anonymous instance.

### A8. What is a method reference? Four types?
Shorthand lambda calling an existing method:
1. Static — `Integer::parseInt`
2. Bound instance — `str::toUpperCase`
3. Unbound instance — `String::toUpperCase`
4. Constructor — `HashMap::new`

### A9. How do you compose Predicates / Functions?
`predicate.and(other)`, `.or(other)`, `.negate()`  
`function.andThen(after)`, `.compose(before)`

### A10. Default methods in interfaces — relation to FP?
Allow interfaces to evolve without breaking implementors; functional interfaces can include defaults while remaining SAMs.

---

## B. Stream Fundamentals

### B1. What is a Stream?
Not a data structure. A pipeline for processing a sequence of elements from a source via intermediate + terminal operations.

### B2. What are the three parts of a stream pipeline?
Source → Intermediate operations (lazy) → Terminal operation (triggers execution).

### B3. Why are intermediate operations lazy?
Deferred until terminal op. Enables optimization, fusion, and short-circuiting.

### B4. Can you reuse a Stream?
No. After terminal operation → `IllegalStateException` if reused.

### B5. Stream vs Collection?
Collection stores and is reusable; Stream computes once and doesn't store (except buffering for stateful ops).

### B6. Ways to create a Stream?
`collection.stream()`, `Stream.of`, `Stream.generate`, `Stream.iterate`, `Arrays.stream`, `IntStream.range`, `Files.lines`, `BufferedReader.lines`, `Stream.concat`, pattern `splitAsStream`.

### B7. What is an infinite stream? How do you stop it?
`generate` / `iterate` without bound. Stop with short-circuit ops: `limit`, `findFirst`, `anyMatch`, etc.

### B8. Internal vs external iteration?
External: you write the loop. Internal: Stream API controls iteration (enables parallelism/laziness).

### B9. Stateless vs stateful intermediate ops?
Stateless: filter, map, flatMap, peek.  
Stateful: distinct, sorted, limit, skip (may buffer / depend on other elements).

### B10. What does `peek` do? Should you use it in production logic?
Performs side-effect intermediate op, mainly for debugging. Avoid business logic in `peek`.

---

## C. map / flatMap / filter

### C1. Difference between map and filter?
filter selects elements (Predicate). map transforms each element (Function).

### C2. Difference between map and flatMap?
map: 1→1. flatMap: 1→Stream (0..N) then flatten to single stream.

### C3. What does `map` returning a Stream give you?
`Stream<Stream<T>>`. Use `flatMap` when you want `Stream<T>`.

### C4. When do you use `mapToInt` / `mapToLong` / `mapToDouble`?
To get primitive streams and avoid boxing; enables `sum()`, `average()`, `summaryStatistics()` efficiently.

### C5. How do you flatten `List<List<String>>`?
```java
list.stream().flatMap(Collection::stream).collect(Collectors.toList());
```

### C6. How do you get all skills from a list of employees?
```java
employees.stream().flatMap(e -> e.getSkills().stream()).collect(Collectors.toSet());
```

---

## D. Terminal Operations & reduce

### D1. Name 8 terminal operations.
collect, forEach, reduce, count, min, max, findFirst, findAny, anyMatch, allMatch, noneMatch, toArray.

### D2. findFirst vs findAny?
findFirst respects encounter order. findAny may return any; better for parallel when order doesn't matter.

### D3. anyMatch / allMatch / noneMatch — short-circuit?
Yes. Stop as soon as result is determined.

### D4. Explain reduce with identity.
`reduce(identity, op)` — identity is starting value; op combines. Identity must satisfy `op(identity, x) == x`.

### D5. Why three-arg reduce?
`(identity, accumulator, combiner)` — accumulator folds element into partial result; combiner merges partials (needed for parallel / when result type ≠ element type).

### D6. reduce vs collect?
reduce: immutable reduction. collect: mutable reduction (Lists, Maps, StringBuilder). Prefer collect for collections.

### D7. Why is building a List with reduce a bad idea?
Creates many intermediate lists / incorrect under parallel without careful combiner. Use `Collectors.toList()`.

### D8. What does `count()` return?
`long` number of elements (after intermediate ops).

---

## E. Collectors

### E1. What is a Collector?
Specification for mutable reduction: supplier, accumulator, combiner, finisher, characteristics.

### E2. How do you collect to List / Set / custom collection?
`toList()`, `toSet()`, `toCollection(TreeSet::new)`.

### E3. How does `toMap` handle duplicate keys?
Without merge function → `IllegalStateException`. Provide `(old, neu) -> ...` merge function.

### E4. groupingBy vs partitioningBy?
groupingBy: Function → arbitrary keys.  
partitioningBy: Predicate → always `Map<Boolean, List<T>>`.

### E5. What is a downstream collector?
Second collector applied to each group, e.g. `groupingBy(dept, counting())`.

### E6. How do you get average salary by department?
```java
collect(groupingBy(Employee::getDept, averagingInt(Employee::getSalary)))
```

### E7. How do you get the employee with max salary per department?
```java
collect(groupingBy(Employee::getDept, maxBy(comparingInt(Employee::getSalary))))
```
Returns `Map<Dept, Optional<Employee>>`.

### E8. What does `joining` do?
Concatenates CharSequence stream into one String, optional delimiter/prefix/suffix.

### E9. What is `collectingAndThen`?
Apply a finisher after collection, e.g. make list unmodifiable, or extract value from Optional.

### E10. What is `mapping` collector?
Maps elements before passing to a downstream collector — useful inside groupingBy.

### E11. summarizingInt — what do you get?
`IntSummaryStatistics`: count, sum, min, max, average.

### E12. groupingByConcurrent — when?
Parallel streams when concurrent map is acceptable and encounter order of groups isn't required.

---

## F. Optional

### F1. Why Optional?
Express possible absence in return types; encourage null-safe handling; reduce NPE.

### F2. of vs ofNullable?
`of` — NPE if null. `ofNullable` — empty Optional if null.

### F3. orElse vs orElseGet?
orElse evaluates default always. orElseGet evaluates Supplier only when empty.

### F4. map vs flatMap on Optional?
map wraps result in Optional (becomes nested if function already returns Optional). flatMap expects function returning Optional — flattens.

### F5. Should Optional be used as a field or parameter?
Generally no. Prefer for return types.

### F6. Is Optional serializable / good in entities?
Avoid as JPA/entity fields. Not designed as a universal null replacement.

### F7. How to get a value safely?
`orElse`, `orElseGet`, `orElseThrow`, `ifPresent` — avoid bare `get()`.

---

## G. Parallel Streams

### G1. How to create a parallel stream?
`list.parallelStream()` or `stream.parallel()`.

### G2. Which thread pool?
`ForkJoinPool.commonPool()`.

### G3. When should you use parallel streams?
Large datasets, CPU-heavy independent ops, associative reductions, good splitting (arrays/ArrayList). Always measure.

### G4. When should you avoid them?
Small data, I/O blocking, shared mutable state, order-critical heavy pipelines, latency-sensitive threads competing for common pool.

### G5. Why is `forEach(list::add)` unsafe in parallel?
`ArrayList.add` is not thread-safe → race conditions / CME / lost updates. Use `collect`.

### G6. forEach vs forEachOrdered in parallel?
forEach: nondeterministic order. forEachOrdered: preserves encounter order (slower).

### G7. Does parallel preserve encounter order for collect(toList)?
For ordered sources, `toList()` generally preserves order; still avoid side effects.

### G8. Can parallel make things slower?
Yes — thread overhead, poor splitting (LinkedList), contention, too little work per element.

---

## H. Scenario / Design Questions

### H1. "Convert this for-loop to streams."
Identify: filter conditions → `filter`; transforms → `map`/`flatMap`; aggregation → `collect`/`reduce`; early exit → `findFirst`/`anyMatch`.

### H2. "Find duplicate elements in a list using streams."
```java
list.stream()
  .collect(groupingBy(Function.identity(), counting()))
  .entrySet().stream()
  .filter(e -> e.getValue() > 1)
  .map(Map.Entry::getKey)
  .collect(toList());
```

### H3. "Sort map by value using streams."
```java
map.entrySet().stream()
  .sorted(Map.Entry.comparingByValue())
  .collect(toMap(Map.Entry::getKey, Map.Entry::getValue,
           (a,b)->a, LinkedHashMap::new));
```

### H4. "Nth highest salary."
```java
employees.stream()
  .map(Employee::getSalary).distinct()
  .sorted(Comparator.reverseOrder())
  .skip(n - 1).findFirst();
```

### H5. "Are streams always better than for-loops?"
No. Streams win on readability for transformations/aggregations. Tight numeric loops or very simple iteration may be clearer/faster as for-loops. Choose readability + measure performance when needed.

### H6. "How do streams help in your day job?"
Example answer: "In services I use streams for DTO mapping, grouping trade/events by key, filtering compliance records, and aggregations — especially with collectors. For hot paths I profile before parallelizing."

---

## I. Rapid True/False

1. Intermediate ops are lazy. **T**
2. You can reuse a stream. **F**
3. `map` flattens nested streams. **F** (flatMap does)
4. `partitioningBy` can have more than 2 keys. **F**
5. `orElse` is lazy. **F**
6. Parallel streams use common ForkJoinPool. **T**
7. Functional interface can have default methods. **T**
8. `findAny` always returns first element. **F**
9. Stateful ops can hurt parallel performance. **T**
10. Optional is mandatory for all nullable fields. **F**

---

## J. If You Remember Only 10 Answers

1. Functional interface = one abstract method  
2. Stream = lazy pipeline, not a collection  
3. Terminal op triggers execution  
4. Stream is single-use  
5. flatMap = map + flatten  
6. collect for mutable results; groupingBy for aggregations  
7. toMap needs merge function on duplicate keys  
8. orElse eager; orElseGet lazy  
9. Parallel = common ForkJoinPool; measure; no shared mutation  
10. Prefer readability; know short-circuit ops  

Nail these 10 and you can navigate almost any Streams/FP interview question.
