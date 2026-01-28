package streams;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.DoubleSummaryStatistics;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Java 8 Streams — 10 question-based interview prep (problem + solution).
 * Use for muscle memory: read problem → code the solution → compare.
 *
 * QUICK MAP — what each question covers:
 * Q1  → stream(), Arrays.stream(), Stream.of(), empty(), iterate(), IntStream.range/rangeClosed
 * Q2  → filter, map, flatMap, distinct, sorted, limit, skip, findFirst, joining
 * Q3  → reduce(identity,op), reduce(op), count, min, max, findFirst, findAny
 * Q4  → anyMatch, allMatch, noneMatch (short-circuit)
 * Q5  → toList, toSet, toCollection, joining(3 forms), toMap+merge
 * Q6  → groupingBy(classifier), partitioningBy(Predicate)
 * Q7  → groupingBy+counting/averagingDouble/maxBy, collectingAndThen, reducing
 * Q8  → distinct-by-key, nth-highest, frequency map, first-non-repeating, groupBy+transform values
 * Q9  → mapToDouble, sum, average, summaryStatistics, boxed, parallelStream, parallel
 * Q10 → Optional in streams (filter+get, flatMap Optional::stream), comparing, reversed, thenComparing, nullsLast
 */
public class StreamsInterviewQuestions {

    private static List<Employee> employees() {
        return Arrays.asList(
                new Employee("Alice", 80_000),
                new Employee("Bob", 60_000),
                new Employee("Carol", 90_000),
                new Employee("Dave", 70_000),
                new Employee("Alice", 80_000),
                new Employee("Eve", 55_000)
        );
    }

    // ==================== Q1: STREAM CREATION ====================
    /*
     * PROBLEM: Show different ways to create a Stream:
     * (a) from a List, (b) from an array, (c) Stream.of(varargs), (d) Stream.empty(),
     * (e) Stream.iterate(seed, next) limited, (f) IntStream.range(from, to) and IntStream.rangeClosed.
     * COVERS: stream(), Arrays.stream(), Stream.of(), empty(), iterate(), generate(), IntStream.range/rangeClosed
     */
    static void q1_streamCreation() {
        List<Employee> list = employees();
        Stream<Employee> fromList = list.stream();
        Stream<Employee> fromArray = Arrays.stream(list.toArray(Employee[]::new));
        Stream<Employee> of = Stream.of(new Employee("X", 50_000), new Employee("Y", 60_000));
        Stream<Employee> empty = Stream.empty();
        Stream<Employee> iterate = Stream.iterate(0, n -> n + 1).limit(3).map(i -> new Employee("E" + i, 50_000.0));
        IntStream.range(0, 5);           // 0,1,2,3,4 (to exclusive)
        IntStream.rangeClosed(0, 5);     // 0,1,2,3,4,5 (to inclusive)
    }

    // ==================== Q2: FILTER, MAP, FLATMAP, DISTINCT, SORTED, LIMIT, SKIP ====================
    /*
     * PROBLEM: Given a list of Employees,
     * (a) get names of employees with salary >= 70_000, as comma-separated string,
     * (b) flatten a List<List<Employee>> into one List<Employee>,
     * (c) get distinct employees (by equals),
     * (d) get top 3 by salary (desc),
     * (e) get 2nd highest salary holder (sort desc, skip(1), findFirst).
     * COVERS: filter, map, flatMap, distinct, sorted, limit, skip, findFirst, joining
     */
    static void q2_intermediateOps() {
        List<Employee> emp = employees();

        // (a) names of salary >= 70k, joined
        String names = emp.stream()
                .filter(e -> e.getSalary() >= 70_000)
                .map(Employee::getName)
                .collect(Collectors.joining(", "));

        // (b) flatten list of lists
        List<List<Employee>> nested = Arrays.asList(
                Arrays.asList(new Employee("A", 1), new Employee("B", 2)),
                Arrays.asList(new Employee("C", 3))
        );
        List<Employee> flat = nested.stream().flatMap(List::stream).collect(Collectors.toList());

        // (c) distinct (uses equals/hashCode)
        List<Employee> distinct = emp.stream().distinct().collect(Collectors.toList());

        // (d) top 3 by salary desc
        List<Employee> top3 = emp.stream()
                .sorted(Comparator.comparingDouble(Employee::getSalary).reversed())
                .limit(3)
                .collect(Collectors.toList());

        // (e) 2nd highest salary
        Optional<Employee> second = emp.stream()
                .sorted(Comparator.comparingDouble(Employee::getSalary).reversed())
                .skip(1)
                .findFirst();
    }

    // ==================== Q3: TERMINAL — REDUCE, COUNT, MIN, MAX, FINDFIRST, FINDANY ====================
    /*
     * PROBLEM: Given a list of Employees, compute:
     * (a) total salary using reduce(identity, BinaryOperator),
     * (b) concatenate all names with "," using reduce(BinaryOperator) — returns Optional,
     * (c) count, (d) min salary employee, (e) max salary employee,
     * (f) findFirst, (g) findAny (mention: findAny is non-deterministic, useful in parallel).
     * COVERS: reduce, count, min, max, findFirst, findAny
     */
    static void q3_terminalOps() {
        List<Employee> emp = employees();

        double total = emp.stream().map(Employee::getSalary).reduce(0.0, Double::sum);
        Optional<String> namesConcat = emp.stream().map(Employee::getName).reduce((a, b) -> a + "," + b);
        long count = emp.stream().count();
        Optional<Employee> minSal = emp.stream().min(Comparator.comparingDouble(Employee::getSalary));
        Optional<Employee> maxSal = emp.stream().max(Comparator.comparingDouble(Employee::getSalary));
        Optional<Employee> first = emp.stream().findFirst();
        Optional<Employee> any = emp.stream().findAny();
    }

    // ==================== Q4: MATCH — ANYMATCH, ALLMATCH, NONEMATCH ====================
    /*
     * PROBLEM: Given a list of Employees, check:
     * (a) is there any employee with salary > 85_000?
     * (b) do all have salary > 0?
     * (c) does none have salary < 0?
     * Mention: short-circuit — they stop as soon as result is known.
     * COVERS: anyMatch, allMatch, noneMatch
     */
    static void q4_matchOps() {
        List<Employee> emp = employees();
        boolean anyHigh = emp.stream().anyMatch(e -> e.getSalary() > 85_000);
        boolean allPositive = emp.stream().allMatch(e -> e.getSalary() > 0);
        boolean noneNegative = emp.stream().noneMatch(e -> e.getSalary() < 0);
    }

    // ==================== Q5: COLLECTORS — TOLIST, TOSET, TOCOLLECTION, JOINING, TOMAP ====================
    /*
     * PROBLEM: From a list of Employees, collect:
     * (a) to List, (b) to Set, (c) to LinkedList (toCollection),
     * (d) all names as single string (no delimiter), (e) names joined by ", ", (f) names joined by ", " with "[ prefix ] suffix",
     * (g) Map<name, salary> — if duplicate keys, keep first (merge function (a,b)->a).
     * COVERS: toList, toSet, toCollection, joining (3 forms), toMap with merge
     */
    static void q5_collectorsBasic() {
        List<Employee> emp = employees();
        List<Employee> list = emp.stream().collect(Collectors.toList());
        Set<Employee> set = emp.stream().collect(Collectors.toSet());
        LinkedList<Employee> linked = emp.stream().collect(Collectors.toCollection(LinkedList::new));
        String noDelim = emp.stream().map(Employee::getName).collect(Collectors.joining());
        String delim = emp.stream().map(Employee::getName).collect(Collectors.joining(", "));
        String wrapped = emp.stream().map(Employee::getName).collect(Collectors.joining(", ", "[", "]"));
        Map<String, Double> nameToSalary = emp.stream()
                .collect(Collectors.toMap(Employee::getName, Employee::getSalary, (a, b) -> a));
    }

    // ==================== Q6: GROUPINGBY, PARTITIONINGBY ====================
    /*
     * PROBLEM: From a list of Employees,
     * (a) group by first letter of name → Map<Character, List<Employee>>,
     * (b) partition into two groups: salary >= 70k (true) vs < 70k (false) → Map<Boolean, List<Employee>>.
     * COVERS: groupingBy(classifier), partitioningBy(Predicate)
     */
    static void q6_groupingPartitioning() {
        List<Employee> emp = employees();
        Map<Character, List<Employee>> byFirst = emp.stream()
                .collect(Collectors.groupingBy(e -> e.getName().charAt(0)));
        Map<Boolean, List<Employee>> byHigh = emp.stream()
                .collect(Collectors.partitioningBy(e -> e.getSalary() >= 70_000));
    }

    // ==================== Q7: GROUPINGBY + DOWNSTREAM, COLLECTINGANDTHEN, REDUCING ====================
    /*
     * PROBLEM: From a list of Employees,
     * (a) group by first letter, value = count per group,
     * (b) group by first letter, value = average salary per group,
     * (c) group by first letter, value = employee with max salary in that group (Optional<Employee>),
     * (d) collect to unmodifiable list (collectingAndThen(toList(), Collections::unmodifiableList)),
     * (e) reduce to single employee with max salary; reduce to total salary (reducing(identity, mapper, op)).
     * COVERS: groupingBy+counting, groupingBy+averagingDouble, groupingBy+maxBy, collectingAndThen, reducing
     */
    static void q7_collectorsAdvanced() {
        List<Employee> emp = employees();
        Map<Character, Long> countByFirst = emp.stream()
                .collect(Collectors.groupingBy(e -> e.getName().charAt(0), Collectors.counting()));
        Map<Character, Double> avgByFirst = emp.stream()
                .collect(Collectors.groupingBy(e -> e.getName().charAt(0), Collectors.averagingDouble(Employee::getSalary)));
        Map<Character, Optional<Employee>> maxByFirst = emp.stream()
                .collect(Collectors.groupingBy(e -> e.getName().charAt(0), Collectors.maxBy(
                        Comparator.comparingDouble(Employee::getSalary))));
        List<Employee> unmod = emp.stream()
                .collect(Collectors.collectingAndThen(Collectors.toList(), Collections::unmodifiableList));
        Optional<Employee> maxEmp = emp.stream().collect(Collectors.reducing((a, b) -> a.getSalary() >= b.getSalary() ? a : b));
        Double total = emp.stream().collect(Collectors.reducing(0.0, Employee::getSalary, Double::sum));
    }

    // ==================== Q8: COMMON INTERVIEW PATTERNS ====================
    /*
     * PROBLEM:
     * (a) Distinct by key — keep one employee per name (first occurrence). Hint: toMap(name, identity, (a,b)->a) then values.
     * (b) Nth highest salary — e.g. 2nd highest. Hint: sort desc, skip(n-1), findFirst.
     * (c) Frequency map — count how many times each name appears. Hint: groupingBy(Identity, counting()).
     * (d) First non-repeating name — first employee whose name appears exactly once. Hint: build frequency map, filter count==1, findFirst.
     * (e) Group by first letter, value = list of salaries (not employees). Hint: groupingBy + mapping(getSalary, toList()).
     * COVERS: distinct-by-key, nth-highest, frequency map, first-non-repeating, groupBy+transform values
     */
    static void q8_commonPatterns() {
        List<Employee> emp = employees();

        // (a) distinct by name, keep first
        List<Employee> distinctByName = emp.stream()
                .collect(Collectors.collectingAndThen(
                        Collectors.toMap(Employee::getName, Function.identity(), (a, b) -> a),
                        m -> new ArrayList<>(m.values())));

        // (b) 2nd highest salary
        Optional<Employee> second = emp.stream()
                .sorted(Comparator.comparingDouble(Employee::getSalary).reversed())
                .skip(1)
                .findFirst();

        // (c) frequency: name -> count
        Map<String, Long> freq = emp.stream()
                .collect(Collectors.groupingBy(Employee::getName, Collectors.counting()));

        // (d) first name that appears exactly once
        Optional<Employee> firstUnique = emp.stream()
                .filter(e -> freq.get(e.getName()) == 1)
                .findFirst();

        // (e) group by first letter -> list of salaries
        Map<Character, List<Double>> salariesByFirst = emp.stream()
                .collect(Collectors.groupingBy(e -> e.getName().charAt(0), Collectors.mapping(Employee::getSalary, Collectors.toList())));
    }

    // ==================== Q9: PRIMITIVE STREAMS, PARALLEL ====================
    /*
     * PROBLEM:
     * (a) Sum all salaries using mapToDouble (avoid boxing). Also average.
     * (b) One pass over salaries to get count, sum, min, max, average — summaryStatistics().
     * (c) IntStream.range(0,10).boxed() — what does boxed() do? Convert IntStream to Stream<Integer> so you can collect to List.
     * (d) Compute sum of salaries using parallelStream(); same using stream().parallel().
     * COVERS: mapToDouble, sum, average, summaryStatistics, boxed, parallelStream, parallel
     */
    static void q9_primitiveAndParallel() {
        List<Employee> emp = employees();
        double sum = emp.stream().mapToDouble(Employee::getSalary).sum();
        OptionalDouble avg = emp.stream().mapToDouble(Employee::getSalary).average();
        DoubleSummaryStatistics stats = emp.stream().mapToDouble(Employee::getSalary).summaryStatistics();
        List<Integer> fromInt = IntStream.range(0, 10).boxed().collect(Collectors.toList());
        double sumPar = emp.parallelStream().mapToDouble(Employee::getSalary).sum();
        double sumPar2 = emp.stream().parallel().mapToDouble(Employee::getSalary).sum();
    }

    // ==================== Q10: OPTIONAL IN STREAMS, SORTING, NULLS ====================
    /*
     * PROBLEM:
     * (a) Given List<Optional<Employee>>, get list of only present employees. Old: filter(isPresent).map(get). Idiomatic (Java 9+): flatMap(Optional::stream).
     * (b) Sort employees by name (natural). By salary descending. By salary then by name (thenComparing).
     * (c) Sort a list that may contain nulls — put nulls last by name. Comparator.nullsLast(Comparator.comparing(Employee::getName)).
     * COVERS: Optional in streams (filter+get, flatMap Optional::stream), comparing, reversed, thenComparing, nullsLast
     */
    static void q10_optionalAndSorting() {
        List<Optional<Employee>> opts = Arrays.asList(Optional.of(new Employee("A", 10)), Optional.empty(), Optional.of(new Employee("C", 30)));
        List<Employee> present = opts.stream().filter(Optional::isPresent).map(Optional::get).collect(Collectors.toList());
        List<Employee> present9 = opts.stream().flatMap(Optional::stream).collect(Collectors.toList());  // Java 9+

        List<Employee> emp = employees();
        List<Employee> byName = emp.stream().sorted(Comparator.comparing(Employee::getName)).collect(Collectors.toList());
        List<Employee> bySalDesc = emp.stream().sorted(Comparator.comparingDouble(Employee::getSalary).reversed()).collect(Collectors.toList());
        List<Employee> bySalThenName = emp.stream().sorted(Comparator.comparingDouble(Employee::getSalary).thenComparing(Employee::getName)).collect(Collectors.toList());

        List<Employee> withNull = Arrays.asList(emp.get(0), null, emp.get(1));
        List<Employee> nullsLast = withNull.stream().sorted(Comparator.nullsLast(Comparator.comparing(Employee::getName))).collect(Collectors.toList());
    }

    // ----- RUN ALL -----
    public static void main(String[] args) {
        q1_streamCreation();
        q2_intermediateOps();
        q3_terminalOps();
        q4_matchOps();
        q5_collectorsBasic();
        q6_groupingPartitioning();
        q7_collectorsAdvanced();
        q8_commonPatterns();
        q9_primitiveAndParallel();
        q10_optionalAndSorting();
    }
}
