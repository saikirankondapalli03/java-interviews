# Java 8 Streams & FP — Morning Cheat Sheet

**Read this in 15–20 minutes before the interview.**

---

## Functional Interfaces (memorize)

| Interface | Method | Shape |
|-----------|--------|-------|
| Predicate\<T\> | test | T → boolean |
| Function\<T,R\> | apply | T → R |
| Consumer\<T\> | accept | T → void |
| Supplier\<T\> | get | () → T |
| UnaryOperator\<T\> | apply | T → T |
| BinaryOperator\<T\> | apply | (T,T) → T |
| BiFunction\<T,U,R\> | apply | (T,U) → R |

Compose: `pred.and/or/negate` · `fn.andThen/compose`

---

## Stream pipeline

```
SOURCE → intermediate* (lazy) → TERMINAL (executes)
```

**Cannot reuse a stream.** Intermediate = Stream. Terminal = result/side-effect.

### Intermediate
`filter` `map` `flatMap` `distinct` `sorted` `peek` `limit` `skip` `mapToInt/...`

### Terminal
`collect` `forEach` `reduce` `count` `min/max` `findFirst/findAny` `any/all/noneMatch` `toArray`

### Short-circuit
`limit` `findFirst` `findAny` `anyMatch` `allMatch` `noneMatch`

---

## map vs flatMap

```java
stream.map(f)      // 1 → 1
stream.flatMap(f)  // 1 → N, then flatten
```

```java
employees.stream().map(Employee::getName);           // Stream<String>
employees.stream().flatMap(e -> e.getSkills().stream()); // Stream<String> skills
```

---

## Collectors (say these aloud)

```java
toList / toSet / toCollection(TreeSet::new)
toMap(keyFn, valueFn)
toMap(keyFn, valueFn, (a,b) -> a)          // merge duplicates
joining(", ")
counting / summingInt / averagingDouble / summarizingInt
groupingBy(classifier)
groupingBy(classifier, downstream)
partitioningBy(predicate)                  // Map<Boolean, List>
mapping(mapper, downstream)
collectingAndThen(collector, finisher)
maxBy / minBy / reducing
```

### Grouping template
```java
Map<K, Long> m = list.stream()
  .collect(groupingBy(Item::getKey, counting()));
```

---

## reduce

```java
reduce((a,b) -> a+b)           // Optional
reduce(0, Integer::sum)        // with identity
reduce(0, accumulator, combiner) // parallel / type change
```

**Prefer `collect` for Lists/Maps.** Prefer `reduce` for sums/max of numbers.

---

## Optional

```java
of / ofNullable / empty
map / flatMap / filter
ifPresent / orElse / orElseGet / orElseThrow
```

- `orElse(x)` always evaluates `x`
- `orElseGet(supp)` only if empty
- Avoid `get()`; don't use Optional as field/param

---

## Parallel streams

- Uses **ForkJoinPool.commonPool()**
- Use for **large + CPU-bound + independent** work
- No shared mutable state
- Prefer `findAny` over `findFirst` when order irrelevant
- Measure — not always faster

```java
// BAD
list.parallelStream().forEach(sharedList::add);
// GOOD
list.parallelStream().collect(toList());
```

---

## Method references

| Kind | Example |
|------|---------|
| Static | `Math::max` |
| Bound | `System.out::println` |
| Unbound | `String::length` |
| Ctor | `ArrayList::new` |

---

## One-liners interviewers love

```java
// frequency
groupingBy(Function.identity(), counting())

// top earner by dept
groupingBy(Employee::getDept, maxBy(comparing(Employee::getSalary)))

// second highest
sorted(reverseOrder()).distinct().skip(1).findFirst()

// flatten
flatMap(Collection::stream)

// even/odd split
partitioningBy(n -> n % 2 == 0)

// csv
collect(joining(", "))
```

---

## Trap answers

| Trap | Correct take |
|------|----------------|
| Reuse stream? | No |
| Parallel always faster? | No — measure |
| Stream store data? | No |
| When does filter run? | At terminal |
| this in lambda? | Enclosing class |
| Local var in lambda? | Must be effectively final |
| toMap duplicate keys? | Need merge function |
| build List with reduce? | Prefer collect |

---

## 20-second pitch

> Lambdas implement functional interfaces. Streams are lazy pipelines: filter/map/flatMap then collect/reduce. groupingBy for aggregations. Optional for absent returns. Parallel only when measured and side-effect free.
