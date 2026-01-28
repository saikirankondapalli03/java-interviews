package streams;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Java 8 Streams interview prep — code + what each part means.
 */
public class EmployeeStreamsDemo {

    public static void main(String[] args) {
        streamCreation();
        intermediateOperations();
        terminalOperations();
        matchOperations();
        collectors();
        commonPatterns();
        primitiveStreams();
        parallelStreams();
        optionalInStreams();
        sortingAndComparators();
    }

    private static List<Employee> sample() {
        return Arrays.asList(
                new Employee("Alice", 80_000),
                new Employee("Bob", 60_000),
                new Employee("Carol", 90_000),
                new Employee("Dave", 70_000),
                new Employee("Alice", 80_000),
                new Employee("Eve", 55_000)
        );
    }

    // --- 1. STREAM CREATION ---
    // Meaning: where a Stream comes from. Nothing runs until a terminal op is called.
    static void streamCreation() {
        List<Employee> list = sample();

        // From a Collection — default way to get a stream from list/set
        list.stream();

        // From an array — use Arrays.stream(array)
        Arrays.stream(list.toArray(Employee[]::new));

        // From fixed elements — Stream.of(a, b, c)
        Stream.of(new Employee("X", 50_000), new Employee("Y", 60_000));

        // Empty stream — useful when you need a Stream type but have nothing
        Stream.empty();

        // Infinite then limited — iterate(seed, next) produces seed, next(seed), next(next(seed))...; limit(n) stops after n
        Stream.iterate(0, n -> n + 1).limit(2).map(i -> new Employee("E" + i, 50_000.0));

        // Infinite then limited — generate(supplier) keeps calling supplier; limit(n) stops after n
        Stream.generate(() -> new Employee("G", 60_000.0)).limit(1);

        // IntStream.range(from, to) — from inclusive, to exclusive. mapToObj turns ints into objects
        IntStream.range(0, 3).mapToObj(i -> new Employee("I" + i, 40_000.0));

        // IntStream.rangeClosed(from, to) — both inclusive. boxed() gives Stream<Integer> so you can collect to List
        IntStream.rangeClosed(0, 2).boxed();
    }

    // --- 2. INTERMEDIATE OPERATIONS ---
    // Meaning: lazy, return a Stream. They don't run until a terminal op runs. Chain them to build a pipeline.
    static void intermediateOperations() {
        List<Employee> emp = sample();

        // filter(Predicate) — keep only elements where the predicate is true
        emp.stream().filter(e -> e.getSalary() >= 70_000);

        // map(Function) — 1-to-1: each element becomes one new element (e.g. Employee -> String)
        emp.stream().map(Employee::getName);

        // mapToDouble — like map but gives DoubleStream (no boxing; efficient for numbers)
        emp.stream().mapToDouble(Employee::getSalary);

        // flatMap(Function<T, Stream<R>>) — 1-to-many then flatten: each element becomes a stream, all streams are merged into one
        Arrays.asList(Arrays.asList(new Employee("A", 1)), Arrays.asList(new Employee("B", 2)))
                .stream().flatMap(List::stream);

        // distinct() — drop duplicates; uses equals(). For objects, override equals/hashCode.
        emp.stream().distinct();

        // sorted(Comparator) — order by comparator. Natural order: sorted() with Comparable elements.
        emp.stream().sorted(Comparator.comparingDouble(Employee::getSalary));
        emp.stream().sorted(Comparator.comparingDouble(Employee::getSalary).reversed());

        // limit(n) — keep at most n elements from the start
        emp.stream().limit(2);

        // skip(n) — drop the first n elements
        emp.stream().skip(1);

        // peek(Consumer) — run a side-effect (e.g. log) on each element; returns same stream. Use for debugging, not for transforming.
        emp.stream().peek(e -> {});  // Java 9+: takeWhile(pred) stop when pred fails; dropWhile(pred) skip until pred fails
    }

    // --- 3. TERMINAL OPERATIONS ---
    // Meaning: they actually run the pipeline and produce a result (or void). After a terminal op, the stream is consumed.
    static void terminalOperations() {
        List<Employee> emp = sample();

        // forEach(Consumer) — do something for each element; no return value
        emp.stream().forEach(e -> {});

        // collect(Collector) — fold elements into a container (list, set, map, etc.)
        emp.stream().collect(Collectors.toList());
        emp.stream().collect(Collectors.toSet());

        // reduce(identity, BinaryOperator) — start with identity, combine with op: identity op e1 op e2 op ...
        emp.stream().map(Employee::getSalary).reduce(0.0, Double::sum);
        // reduce(BinaryOperator) — no identity; returns Optional (empty if stream empty)
        emp.stream().map(Employee::getName).reduce((a, b) -> a + "," + b);

        // count() — number of elements
        emp.stream().count();

        // min/max(Comparator) — smallest/largest by comparator; return Optional
        emp.stream().min(Comparator.comparingDouble(Employee::getSalary));
        emp.stream().max(Comparator.comparingDouble(Employee::getSalary));

        // findFirst() — first element (order matters); findAny() — any (useful in parallel). Both Optional.
        emp.stream().findFirst();
        emp.stream().findAny();
    }

    // --- 4. MATCH OPERATIONS ---
    // Meaning: short-circuit terminal ops. They return boolean and stop as soon as the result is known.
    static void matchOperations() {
        List<Employee> emp = sample();

        // anyMatch(Predicate) — true if at least one element matches
        emp.stream().anyMatch(e -> e.getSalary() > 85_000);

        // allMatch(Predicate) — true if every element matches (stops at first mismatch)
        emp.stream().allMatch(e -> e.getSalary() > 0);

        // noneMatch(Predicate) — true if no element matches (stops at first match)
        emp.stream().noneMatch(e -> e.getSalary() < 0);
    }

    // --- 5. COLLECTORS ---
    // Meaning: blueprints for “how to collect” stream elements into a result. Used inside collect(Collector).
    static void collectors() {
        List<Employee> emp = sample();

        // toList, toSet — collect into List or Set
        emp.stream().collect(Collectors.toList());
        emp.stream().collect(Collectors.toSet());

        // toCollection(Supplier) — collect into a specific type, e.g. LinkedList
        emp.stream().collect(Collectors.toCollection(LinkedList::new));

        // joining() — concat strings. No arg = no separator; (delim) = between elements; (delim, prefix, suffix) = full wrapper
        emp.stream().map(Employee::getName).collect(Collectors.joining());
        emp.stream().map(Employee::getName).collect(Collectors.joining(", "));
        emp.stream().map(Employee::getName).collect(Collectors.joining(", ", "[", "]"));

        // toMap(keyMapper, valueMapper) — keys must be unique. Third arg = merge when duplicate keys
        emp.stream().collect(Collectors.toMap(Employee::getName, Employee::getSalary, (a, b) -> a));

        // groupingBy(classifier) — group by key; value is List of elements with that key
        emp.stream().collect(Collectors.groupingBy(e -> e.getName().charAt(0)));

        // groupingBy(classifier, downstream) — same, but value is result of downstream collector (e.g. count, set, avg)
        emp.stream().collect(Collectors.groupingBy(e -> e.getName().charAt(0), Collectors.counting()));
        emp.stream().collect(Collectors.groupingBy(e -> e.getName().charAt(0), Collectors.toSet()));
        emp.stream().collect(Collectors.groupingBy(e -> e.getName().charAt(0), Collectors.averagingDouble(Employee::getSalary)));
        emp.stream().collect(Collectors.groupingBy(e -> e.getName().charAt(0), Collectors.maxBy(Comparator.comparingDouble(Employee::getSalary))));

        // partitioningBy(Predicate) — split into two groups: key true = matches, key false = doesn’t match
        emp.stream().collect(Collectors.partitioningBy(e -> e.getSalary() >= 70_000));

        // collectingAndThen(collector, finisher) — collect, then apply a function to the result (e.g. make list unmodifiable)
        emp.stream().collect(Collectors.collectingAndThen(Collectors.toList(), Collections::unmodifiableList));

        // reducing — generic reduce as a collector: (binaryOp) or (identity, mapper, binaryOp)
        emp.stream().collect(Collectors.reducing((a, b) -> a.getSalary() >= b.getSalary() ? a : b));
        emp.stream().collect(Collectors.reducing(0.0, Employee::getSalary, Double::sum));
    }

    // --- 6. COMMON INTERVIEW PATTERNS ---
    static void commonPatterns() {
        List<Employee> emp = sample();

        // Distinct by key — keep one element per key (e.g. by name). toMap(key, identity, merge) keeps first; then take values.
        emp.stream().collect(Collectors.collectingAndThen(
                Collectors.toMap(Employee::getName, Function.identity(), (a, b) -> a),
                m -> new ArrayList<>(m.values())));

        // Nth highest — sort descending, skip n-1, take first
        emp.stream().sorted(Comparator.comparingDouble(Employee::getSalary).reversed()).skip(1).findFirst();

        // Flatten list of lists — flatMap each inner list to its stream, collect to one list
        List<List<Employee>> nested = Arrays.asList(Arrays.asList(new Employee("A", 1)), Arrays.asList(new Employee("B", 2)));
        nested.stream().flatMap(List::stream).collect(Collectors.toList());

        // Group by key, transform values — groupingBy + mapping(downstream) so each group becomes a list of something else (e.g. salaries)
        emp.stream().collect(Collectors.groupingBy(e -> e.getName().charAt(0), Collectors.mapping(Employee::getSalary, Collectors.toList())));

        // Frequency map — how many times each element appears: groupingBy(identity, counting())
        emp.stream().collect(Collectors.groupingBy(Employee::getName, Collectors.counting()));

        // First non-repeating — build frequency map, then find first element with count == 1
        Map<String, Long> freq = emp.stream().collect(Collectors.groupingBy(Employee::getName, Collectors.counting()));
        emp.stream().filter(e -> freq.get(e.getName()) == 1).findFirst();
    }

    // --- 7. PRIMITIVE STREAMS ---
    // Meaning: IntStream, LongStream, DoubleStream avoid boxing and have ops like sum(), average(), summaryStatistics().
    static void primitiveStreams() {
        List<Employee> emp = sample();

        // IntStream.range/rangeClosed → boxed() gives Stream<Integer> so you can use collect(toList()) etc.
        IntStream.range(0, 5).boxed().collect(Collectors.toList());

        // mapToInt/Long/Double — turn object stream into primitive stream (e.g. by mapping to int/long/double)
        emp.stream().mapToInt(e -> e.getName().length());
        emp.stream().mapToDouble(Employee::getSalary).sum();
        emp.stream().mapToDouble(Employee::getSalary).average();

        // summaryStatistics() — one pass gives count, sum, min, max, average (getCount, getSum, getMin, getMax, getAverage)
        emp.stream().mapToDouble(Employee::getSalary).summaryStatistics();
    }

    // --- 8. PARALLEL ---
    // Meaning: split work across threads. Use when the pipeline is heavy and data is large; avoid for small or shared mutable state.
    static void parallelStreams() {
        sample().parallelStream().mapToDouble(Employee::getSalary).sum();
        sample().stream().parallel().mapToDouble(Employee::getSalary).sum();
    }

    // --- 9. OPTIONAL IN STREAMS ---
    // Meaning: stream of Optional<T> → get only present values. Old way: filter(isPresent)+map(get). Idiomatic (Java 9+): flatMap(Optional::stream).
    static void optionalInStreams() {
        List<Optional<Employee>> opts = Arrays.asList(Optional.of(new Employee("A", 10)), Optional.empty(), Optional.of(new Employee("C", 30)));
        opts.stream().filter(Optional::isPresent).map(Optional::get).collect(Collectors.toList());
        opts.stream().flatMap(Optional::stream).collect(Collectors.toList());  // Java 9+: present values only
    }

    // --- 10. SORTING ---
    // Meaning: Comparator.comparing(f) orders by f; reversed() flips order; thenComparing(f) breaks ties; nullsLast/nullsFirst handle nulls.
    static void sortingAndComparators() {
        List<Employee> emp = sample();
        emp.stream().sorted(Comparator.comparing(Employee::getName));
        emp.stream().sorted(Comparator.comparingDouble(Employee::getSalary).reversed());
        emp.stream().sorted(Comparator.comparingDouble(Employee::getSalary).thenComparing(Employee::getName));
        Arrays.<Employee>asList(emp.get(0), null, emp.get(1)).stream()
                .sorted(Comparator.nullsLast(Comparator.comparing(Employee::getName)));
    }

    /*
     * CHEAT: CREATION → stream(), Arrays.stream(), Stream.of(), empty(), iterate(), generate(), IntStream.range/rangeClosed
     * INTERMEDIATE → filter, map, flatMap, distinct, sorted, limit, skip, peek
     * TERMINAL → forEach, collect, reduce, count, min, max, findFirst, findAny, anyMatch, allMatch, noneMatch
     * COLLECTORS → toList, toSet, toCollection, joining, toMap, groupingBy, partitioningBy, collectingAndThen, reducing, mapping, counting, averagingDouble, maxBy
     * PRIMITIVE → boxed(), mapToInt/Long/Double, sum/average, summaryStatistics
     * PARALLEL → parallelStream(), parallel()
     */
}
