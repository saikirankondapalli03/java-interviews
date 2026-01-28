package streams;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.IntSummaryStatistics;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Java 8 Streams — interview-ready coverage in one class.
 * Covers creation, intermediate ops, terminal ops, Collectors, primitives, and parallel.
 */
public class StreamsInterviewPrep {

    public static void main(String[] args) {
        streamCreation();
        intermediateOperations();
        terminalOperations();
        allMatchReturnBehaviorDemo();
        collectingAndThenVisualDemo();
        collectors();
        commonPatternDemos();
        primitiveStreams();
        parallelStreams();
        optionalInStreams();
        sortingAndComparators();
        realWorldExamples();
    }

    // ------ 1. STREAM CREATION ------

    public static void streamCreation() {
        // From Collection
        List<String> list = Arrays.asList("a", "b", "c");
        Stream<String> fromList = list.stream();

        // From array
        String[] arr = {"x", "y", "z"};
        Stream<String> fromArray = Arrays.stream(arr);

        // Stream.of(varargs)
        Stream<String> ofStream = Stream.of("one", "two", "three");

        // Empty stream
        Stream<Object> empty = Stream.empty();

        // Infinite streams
        Stream<Integer> iterate = Stream.iterate(0, n -> n + 1);      // 0, 1, 2, ...
        Stream<Double> generate = Stream.generate(Math::random);

        // Primitive streams
        IntStream intStream = IntStream.range(0, 10);           // 0..9 (exclusive end)
        IntStream intStreamClosed = IntStream.rangeClosed(0, 10); // 0..10 (inclusive)
        IntStream intStreamOf = IntStream.of(1, 2, 3);
    }

    // ------ 2. INTERMEDIATE OPERATIONS (lazy, return Stream) ------

    public static void intermediateOperations() {
        List<Integer> nums = Arrays.asList(1, 2, 2, 3, 4, 4, 5, 6);

        // filter(Predicate)
        List<Integer> evens = nums.stream().filter(n -> n % 2 == 0).collect(Collectors.toList());

        // map(Function) — 1-to-1
        List<Integer> doubled = nums.stream().map(n -> n * 2).collect(Collectors.toList());

        // flatMap — 1-to-many, flattens
        List<List<Integer>> nested = Arrays.asList(Arrays.asList(1, 2), Arrays.asList(3, 4));
        List<Integer> flat = nested.stream()
                .flatMap(List::stream)
                .collect(Collectors.toList());  // [1,2,3,4]

        // distinct (by equals)
        List<Integer> unique = nums.stream().distinct().collect(Collectors.toList());

        // sorted() — natural order; sorted(Comparator)
        List<Integer> asc = nums.stream().sorted().collect(Collectors.toList());
        List<Integer> desc = nums.stream().sorted(Comparator.reverseOrder()).collect(Collectors.toList());

        // limit(n), skip(n)
        List<Integer> first3 = nums.stream().limit(3).collect(Collectors.toList());
        List<Integer> after2 = nums.stream().skip(2).collect(Collectors.toList());

        // takeWhile / dropWhile (Java 9+) — stop when predicate fails
        List<Integer> taken = nums.stream().takeWhile(n -> n < 4).collect(Collectors.toList());
        List<Integer> dropped = nums.stream().dropWhile(n -> n < 4).collect(Collectors.toList());
    }

    // ------ 3. TERMINAL OPERATIONS (trigger execution) ------

    public static void terminalOperations() {
        List<String> words = Arrays.asList("one", "two", "three", "four");

        // forEach(Consumer)
        words.stream().forEach(System.out::println);

        // collect(Collector)
        List<String> asList = words.stream().collect(Collectors.toList());
        Set<String> asSet = words.stream().collect(Collectors.toSet());

        // reduce(identity, BinaryOperator)
        Optional<String> concat = words.stream().reduce((a, b) -> a + "," + b);
        String withIdentity = words.stream().reduce("", (a, b) -> a + "," + b);

        // count, min, max
        long count = words.stream().count();
        Optional<String> min = words.stream().min(Comparator.comparingInt(String::length));
        Optional<String> max = words.stream().max(Comparator.naturalOrder());

        // findFirst, findAny (useful for parallel)
        Optional<String> first = words.stream().findFirst();
        Optional<String> any = words.stream().findAny();

        // Match — short-circuit
        boolean anyMatch = words.stream().anyMatch(s -> s.startsWith("t"));
        boolean allMatch = words.stream().allMatch(s -> s.length() > 2);
        boolean noneMatch = words.stream().noneMatch(s -> s.isEmpty());
    }

    /**
     * Demo: how "return" inside an allMatch lambda behaves.
     * - "return" exits only the lambda (feeds boolean to allMatch), not the enclosing method.
     * - allMatch short-circuits: stops on first element that does not match.
     */
    public static void allMatchReturnBehaviorDemo() {
        List<String> words = Arrays.asList("one", "two", "three", "no");  // "no" has length 2

        // 1) Block lambda with explicit return — return is from the lambda only
        boolean result = words.stream().allMatch(s -> {
            if (s == null) return false;
            return s.length() > 2;   // "no" fails here
        });

        // 2) Short-circuit: we stop at first mismatch
        List<String> list = Arrays.asList("aaa", "bb", "ccc");  // "bb" fails
        int[] checked = {0};
        boolean r2 = list.stream().allMatch(s -> {
            checked[0]++;
            return s.length() > 2;
        });

        // 3) "return" inside lambda does NOT exit the enclosing method
        words.stream().allMatch(s -> {
            if ("no".equals(s)) return false;  // only returns from lambda
            return true;
        });
    }

    /**
     * Visualize collectingAndThen: stream → toList → unmodifiableList.
     * Run this to see each step with real data.
     */
    public static void collectingAndThenVisualDemo() {
        List<String> items = Arrays.asList("apple", "banana", "apricot", "berry", "avocado");

        // STEP 1: Only the inner collector — you get a mutable list
        List<String> step1_mutable = items.stream().collect(Collectors.toList());

        // STEP 2: Pass that list to the "finisher" — wrap it so it can't be changed
        List<String> step2_wrapped = Collections.unmodifiableList(step1_mutable);

        // ONE LINER: collectingAndThen = do step1, then apply step2's function to the result
        List<String> unmodifiable = items.stream()
                .collect(Collectors.collectingAndThen(Collectors.toList(), Collections::unmodifiableList));

        // Proving it's unmodifiable:
        try {
            unmodifiable.add("x");
        } catch (UnsupportedOperationException e) {
            // expected
        }
    }

    // ------ 4. COLLECTORS (often asked) ------

    public static void collectors() {
        List<String> items = Arrays.asList("apple", "banana", "apricot", "berry", "avocado");

        // toList, toSet, toCollection
        List<String> list = items.stream().collect(Collectors.toList());
        Set<String> set = items.stream().collect(Collectors.toSet());
        LinkedList<String> linked = items.stream().collect(Collectors.toCollection(LinkedList::new));

        // joining
        String joined = items.stream().collect(Collectors.joining());
        String joinedDelim = items.stream().collect(Collectors.joining(", "));
        String joinedWithPrefixSuffix = items.stream().collect(Collectors.joining(", ", "[", "]"));

        // toMap(keyMapper, valueMapper) — keys must be unique
        Map<String, Integer> map = items.stream().collect(Collectors.toMap(Function.identity(), String::length));
        // With duplicate keys — merge function
        Map<String, Integer> withMerge = items.stream()
                .collect(Collectors.toMap(Function.identity(), String::length, (a, b) -> a));

        // groupingBy(classifier) → Map<K, List<V>>
        Map<Character, List<String>> byFirst = items.stream()
                .collect(Collectors.groupingBy(s -> s.charAt(0)));

        // groupingBy(classifier, downstream) — e.g. toSet as downstream
        Map<Character, Set<String>> byFirstSet = items.stream()
                .collect(Collectors.groupingBy(s -> s.charAt(0), Collectors.toSet()));

        // groupingBy + counting
        Map<Character, Long> countByFirst = items.stream()
                .collect(Collectors.groupingBy(s -> s.charAt(0), Collectors.counting()));

        // partitioningBy(Predicate) → Map<Boolean, List<V>>
        Map<Boolean, List<String>> byLength = items.stream()
                .collect(Collectors.partitioningBy(s -> s.length() > 5));

        /*
         * collectingAndThen(collector, finisher) — VISUAL:
         *
         *   items = [apple, banana, apricot, berry, avocado]
         *        |
         *        v
         *   Collectors.toList()     -->  mutable ArrayList: [apple, banana, ...]
         *        |
         *        v
         *   Collections::unmodifiableList  -->  same elements, but list.add() throws
         *
         *   One expression: stream → collect → then wrap (finisher).
         */
        List<String> unmodifiable = items.stream()
                .collect(Collectors.collectingAndThen(Collectors.toList(), Collections::unmodifiableList));

        // reducing
        Optional<String> reduced = items.stream()
                .collect(Collectors.reducing((a, b) -> a + "|" + b));
        Integer sumLengths = items.stream()
                .collect(Collectors.reducing(0, String::length, Integer::sum));
    }

    // ------ 5. COMMON INTERVIEW PATTERNS ------

    /** Get distinct elements by a key (e.g. distinct by name). */
    public static <T, K> List<T> distinctByKey(List<T> list, Function<T, K> keyExtractor) {
        return list.stream()
                .collect(Collectors.collectingAndThen(
                        Collectors.toMap(keyExtractor, Function.identity(), (a, b) -> a),
                        m -> new ArrayList<>(m.values())));
    }

    /** Nth highest/lowest — sort and skip. */
    public static Optional<Integer> nthHighest(int[] arr, int n) {
        return Arrays.stream(arr)
                .boxed()
                .sorted(Comparator.reverseOrder())
                .skip(n - 1)
                .findFirst();
    }

    /** Flatten list of lists. */
    public static <T> List<T> flatten(List<List<T>> nested) {
        return nested.stream().flatMap(List::stream).collect(Collectors.toList());
    }

    /** Group and transform values (e.g. group names and get list of lengths). */
    public static Map<Character, List<Integer>> groupByAndTransform(List<String> items) {
        return items.stream()
                .collect(Collectors.groupingBy(s -> s.charAt(0),
                        Collectors.mapping(String::length, Collectors.toList())));
    }

    /** Frequency map: element -> count. */
    public static <T> Map<T, Long> frequencyMap(Collection<T> coll) {
        return coll.stream().collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
    }

    /** First non-repeat (or custom logic) using streams. */
    public static Optional<String> firstNonRepeating(List<String> list) {
        Map<String, Long> counts = frequencyMap(list);
        return list.stream().filter(s -> counts.get(s) == 1).findFirst();
    }

    /** Runs all common-pattern methods with sample data so every example is exercised. */
    public static void commonPatternDemos() {
        // distinctByKey: keep first of each key (e.g. by first letter)
        List<String> items = Arrays.asList("apple", "apricot", "avocado", "banana", "berry");
        List<String> distinctByFirstChar = distinctByKey(items, s -> s.charAt(0));

        // nthHighest: 2nd largest in [50, 20, 40, 10, 30]
        int[] nums = {50, 20, 40, 10, 30};
        Optional<Integer> secondHighest = nthHighest(nums, 2);

        // flatten: [[1,2],[3,4],[5]] -> [1,2,3,4,5]
        List<List<Integer>> nested = Arrays.asList(Arrays.asList(1, 2), Arrays.asList(3, 4), Arrays.asList(5));
        List<Integer> flat = flatten(nested);

        // groupByAndTransform: group by first char, values = list of lengths
        List<String> words = Arrays.asList("apple", "banana", "apricot", "berry", "avocado");
        Map<Character, List<Integer>> byFirstAndLength = groupByAndTransform(words);

        // frequencyMap: element -> count
        List<String> letters = Arrays.asList("a", "b", "a", "c", "b", "a");
        Map<String, Long> freq = frequencyMap(letters);

        // firstNonRepeating: first element that appears exactly once
        List<String> repeat = Arrays.asList("x", "y", "x", "z", "y");
        Optional<String> firstUnique = firstNonRepeating(repeat);
    }

    // ------ 6. PRIMITIVE STREAMS (IntStream, LongStream, DoubleStream) ------

    public static void primitiveStreams() {
        System.out.println("[BREAK] primitiveStreams() entered");
        // Boxing: IntStream -> Stream<Integer>
        List<Integer> fromInt = IntStream.range(0, 5).boxed().collect(Collectors.toList());
        System.out.println("[BREAK] primitiveStreams -> boxed fromInt: " + fromInt);

        // mapToInt, mapToLong, mapToDouble from object streams
        System.out.println("[BREAK] primitiveStreams -> before mapToInt/Long/Double");
        List<String> strNums = Arrays.asList("1", "2", "3");
        int[] ints = strNums.stream().mapToInt(Integer::parseInt).toArray();
        long sum = strNums.stream().mapToLong(Long::parseLong).sum();
        double avg = strNums.stream().mapToDouble(Double::parseDouble).average().orElse(0);
        System.out.println("[BREAK] primitiveStreams -> sum=" + sum + " avg=" + avg);

        // IntStream summary: sum, min, max, average, count
        System.out.println("[BREAK] primitiveStreams -> before summaryStatistics");
        IntSummaryStatistics stats = IntStream.of(1, 2, 3, 4, 5).summaryStatistics();
        long sumVal = stats.getSum();
        int minVal = stats.getMin();
        double avgVal = stats.getAverage();
        System.out.println("[BREAK] primitiveStreams() done -> stats: sum=" + sumVal + " min=" + minVal);
    }

    // ------ 7. PARALLEL STREAMS ------

    public static void parallelStreams() {
        List<Integer> nums = IntStream.range(0, 100).boxed().collect(Collectors.toList());

        // .parallelStream() on Collection
        long sumPar = nums.parallelStream().mapToLong(Integer::longValue).sum();

        // .parallel() on existing stream
        long sumPar2 = nums.stream().parallel().mapToLong(Integer::longValue).sum();
    }

    // ------ 8. OPTIONAL IN STREAMS ------

    public static void optionalInStreams() {
        List<Optional<String>> optList = Arrays.asList(
                Optional.of("a"), Optional.empty(), Optional.of("c"));

        // Filter out empty, get values
        List<String> present = optList.stream()
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());

        // Same using flatMap (idiomatic)
        List<String> present2 = optList.stream()
                .flatMap(Optional::stream)  // Java 9+
                .collect(Collectors.toList());
    }

    // ------ 9. SORTING & COMPARATORS ------

    public static void sortingAndComparators() {
        System.out.println("[BREAK] sortingAndComparators() entered");
        List<String> words = Arrays.asList("banana", "apple", "cherry", "date");
        System.out.println("[BREAK] sortingAndComparators -> words: " + words);

        // Natural order
        List<String> natural = words.stream().sorted().collect(Collectors.toList());
        System.out.println("[BREAK] sortingAndComparators -> natural: " + natural);

        // By length
        List<String> byLength = words.stream()
                .sorted(Comparator.comparingInt(String::length))
                .collect(Collectors.toList());
        System.out.println("[BREAK] sortingAndComparators -> byLength: " + byLength);

        // Then by natural order (length, then alpha)
        List<String> byLengthThenAlpha = words.stream()
                .sorted(Comparator.comparingInt(String::length).thenComparing(Function.identity()))
                .collect(Collectors.toList());

        // Reverse
        List<String> rev = words.stream().sorted(Comparator.reverseOrder()).collect(Collectors.toList());
        System.out.println("[BREAK] sortingAndComparators -> reverse: " + rev);

        // Null-friendly
        System.out.println("[BREAK] sortingAndComparators -> before nullsLast");
        List<String> withNulls = Arrays.asList("b", null, "a");
        List<String> nullsLast = withNulls.stream()
                .sorted(Comparator.nullsLast(Comparator.naturalOrder()))
                .collect(Collectors.toList());
        System.out.println("[BREAK] sortingAndComparators() done -> nullsLast: " + nullsLast);
    }

    // ------ 10. REAL-WORLD STYLE EXAMPLE (Employee/Department) ------

    public static class Employee {
        final String name;
        final String dept;
        final int salary;

        Employee(String name, String dept, int salary) {
            this.name = name;
            this.dept = dept;
            this.salary = salary;
        }

        String getDept() { return dept; }
        int getSalary() { return salary; }
        String getName() { return name; }
    }

    public static void realWorldExamples() {
        List<Employee> employees = Arrays.asList(
                new Employee("Alice", "IT", 80),
                new Employee("Bob", "HR", 60),
                new Employee("Carol", "IT", 90),
                new Employee("Dave", "HR", 70));

        // Group by department
        Map<String, List<Employee>> byDept = employees.stream()
                .collect(Collectors.groupingBy(Employee::getDept));

        // Average salary per department
        Map<String, Double> avgSalByDept = employees.stream()
                .collect(Collectors.groupingBy(Employee::getDept,
                        Collectors.averagingInt(Employee::getSalary)));

        // Highest salary per department
        Map<String, Optional<Employee>> topByDept = employees.stream()
                .collect(Collectors.groupingBy(Employee::getDept,
                        Collectors.maxBy(Comparator.comparingInt(Employee::getSalary))));

        // Names in IT, comma-separated
        String itNames = employees.stream()
                .filter(e -> "IT".equals(e.getDept()))
                .map(Employee::getName)
                .collect(Collectors.joining(", "));

        // Total salary
        int total = employees.stream().mapToInt(Employee::getSalary).sum();

        // Any employee in IT earning > 85?
        boolean hasHighEarner = employees.stream()
                .anyMatch(e -> "IT".equals(e.getDept()) && e.getSalary() > 85);
    }

    // ------ 11. SHORT CHEAT REFERENCE (method names only) ------

    /*
     * CREATION: stream(), Arrays.stream(), Stream.of(), Stream.iterate(), Stream.generate(),
     *           IntStream.range/rangeClosed/of, empty()
     * INTERMEDIATE: filter, map, flatMap, distinct, sorted, limit, skip, peek,
     *               takeWhile, dropWhile (9+)
     * TERMINAL: forEach, collect, reduce, count, min, max, findFirst, findAny,
     *           anyMatch, allMatch, noneMatch
     * COLLECTORS: toList, toSet, toMap, joining, groupingBy, partitioningBy,
     *             collectingAndThen, reducing, mapping, counting, averagingInt
     * PRIMITIVE: boxed(), mapToInt/Long/Double, sum/average/min/max, summaryStatistics
     * PARALLEL: parallelStream(), parallel()
     */
}
