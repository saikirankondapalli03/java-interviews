# Java 8 Streams — Coding Drills (Do Tonight)

Write each on paper or in an IDE **without looking**. Then check the solution.

Assume:
```java
class Employee {
    String name;
    String dept;
    int salary;
    List<String> skills;
    // getters...
}
```

---

## Drill 1 — Names of high earners
Get sorted names of employees with salary > 100000.

<details><summary>Solution</summary>

```java
List<String> names = employees.stream()
    .filter(e -> e.getSalary() > 100_000)
    .map(Employee::getName)
    .sorted()
    .collect(Collectors.toList());
```
</details>

---

## Drill 2 — Average salary by department

<details><summary>Solution</summary>

```java
Map<String, Double> avg = employees.stream()
    .collect(Collectors.groupingBy(
        Employee::getDept,
        Collectors.averagingInt(Employee::getSalary)));
```
</details>

---

## Drill 3 — Count employees per department

<details><summary>Solution</summary>

```java
Map<String, Long> counts = employees.stream()
    .collect(Collectors.groupingBy(Employee::getDept, Collectors.counting()));
```
</details>

---

## Drill 4 — Highest paid employee (Optional)

<details><summary>Solution</summary>

```java
Optional<Employee> top = employees.stream()
    .max(Comparator.comparingInt(Employee::getSalary));
```
</details>

---

## Drill 5 — Highest paid per department

<details><summary>Solution</summary>

```java
Map<String, Optional<Employee>> topByDept = employees.stream()
    .collect(Collectors.groupingBy(
        Employee::getDept,
        Collectors.maxBy(Comparator.comparingInt(Employee::getSalary))));

// Or unwrap names:
Map<String, String> topNameByDept = employees.stream()
    .collect(Collectors.groupingBy(
        Employee::getDept,
        Collectors.collectingAndThen(
            Collectors.maxBy(Comparator.comparingInt(Employee::getSalary)),
            opt -> opt.map(Employee::getName).orElse("N/A"))));
```
</details>

---

## Drill 6 — Word frequency

<details><summary>Solution</summary>

```java
Map<String, Long> freq = Arrays.stream(text.toLowerCase().split("\\W+"))
    .filter(s -> !s.isEmpty())
    .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
```
</details>

---

## Drill 7 — Flatten all unique skills

<details><summary>Solution</summary>

```java
Set<String> skills = employees.stream()
    .flatMap(e -> e.getSkills().stream())
    .collect(Collectors.toSet());
```
</details>

---

## Drill 8 — Partition even / odd

<details><summary>Solution</summary>

```java
Map<Boolean, List<Integer>> parts = numbers.stream()
    .collect(Collectors.partitioningBy(n -> n % 2 == 0));
// parts.get(true) = evens, parts.get(false) = odds
```
</details>

---

## Drill 9 — Second highest distinct number

<details><summary>Solution</summary>

```java
Optional<Integer> second = numbers.stream()
    .distinct()
    .sorted(Comparator.reverseOrder())
    .skip(1)
    .findFirst();
```
</details>

---

## Drill 10 — Sum of salaries with mapToInt

<details><summary>Solution</summary>

```java
int total = employees.stream()
    .mapToInt(Employee::getSalary)
    .sum();
```
</details>

---

## Drill 11 — Join names as CSV

<details><summary>Solution</summary>

```java
String csv = employees.stream()
    .map(Employee::getName)
    .collect(Collectors.joining(", ", "[", "]"));
```
</details>

---

## Drill 12 — toMap id → employee (handle duplicates)

<details><summary>Solution</summary>

```java
Map<Integer, Employee> map = employees.stream()
    .collect(Collectors.toMap(
        Employee::getId,
        Function.identity(),
        (existing, replacement) -> existing));
```
</details>

---

## Drill 13 — Multi-level group: dept → (skill → employees who have it)

<details><summary>Solution</summary>

```java
Map<String, Map<String, List<Employee>>> nested = employees.stream()
    .collect(Collectors.groupingBy(
        Employee::getDept,
        Collectors.flatMapping(
            e -> e.getSkills().stream().map(skill -> new AbstractMap.SimpleEntry<>(skill, e)),
            Collectors.groupingBy(Map.Entry::getKey,
                Collectors.mapping(Map.Entry::getValue, Collectors.toList())))));
```

**Simpler Java 8-only variant** (interview-friendly):

```java
Map<String, List<Employee>> byDept = employees.stream()
    .collect(Collectors.groupingBy(Employee::getDept));

// Then for one dept, group by skill with flatMap separately if asked.
// Or:
Map<String, Long> skillCount = employees.stream()
    .flatMap(e -> e.getSkills().stream())
    .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
```
</details>

---

## Drill 14 — findFirst vs anyMatch

```java
boolean hasRich = employees.stream().anyMatch(e -> e.getSalary() > 200_000);

Optional<Employee> firstRich = employees.stream()
    .filter(e -> e.getSalary() > 200_000)
    .findFirst();
```

Explain: `anyMatch` short-circuits to boolean; `findFirst` returns the element.

---

## Drill 15 — Reduce product of numbers

<details><summary>Solution</summary>

```java
int product = numbers.stream().reduce(1, (a, b) -> a * b);
```
</details>

---

## Drill 16 — Infinite stream of even numbers, first 10

<details><summary>Solution</summary>

```java
List<Integer> evens = Stream.iterate(0, n -> n + 2)
    .limit(10)
    .collect(Collectors.toList());
```
</details>

---

## Drill 17 — Custom sort: salary desc, then name asc

<details><summary>Solution</summary>

```java
List<Employee> sorted = employees.stream()
    .sorted(Comparator.comparingInt(Employee::getSalary).reversed()
            .thenComparing(Employee::getName))
    .collect(Collectors.toList());
```
</details>

---

## Drill 18 — SummarizingInt

<details><summary>Solution</summary>

```java
IntSummaryStatistics stats = employees.stream()
    .collect(Collectors.summarizingInt(Employee::getSalary));
// stats.getCount(), getSum(), getMin(), getMax(), getAverage()
```
</details>

---

## Drill 19 — Parallel-safe collect vs bad forEach

Explain what's wrong and fix:

```java
// WRONG
List<String> names = new ArrayList<>();
employees.parallelStream().map(Employee::getName).forEach(names::add);

// RIGHT
List<String> names = employees.parallelStream()
    .map(Employee::getName)
    .collect(Collectors.toList());
```

---

## Drill 20 — Optional chain

```java
String city = findEmployee(id)
    .map(Employee::getAddress)
    .map(Address::getCity)
    .orElse("Unknown");
```

If `getAddress()` returns `Optional<Address>`:
```java
String city = findEmployee(id)
    .flatMap(Employee::getAddress)
    .map(Address::getCity)
    .orElse("Unknown");
```

---

## Whiteboard script (practice saying while coding)

1. "I'll stream the collection."
2. "Filter with a Predicate for the business rule."
3. "Map to the field I need."
4. "Use a Collector — groupingBy/counting/toList."
5. "Return Optional if it might be empty."
6. "If parallel: no shared mutable state; collect instead of forEach add."

Do drills **1, 2, 5, 6, 7, 9, 12, 19** first — highest interview hit rate.
