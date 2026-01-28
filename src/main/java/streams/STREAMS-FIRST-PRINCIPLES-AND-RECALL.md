# Java 8 Streams — First Principles & Recall for Interviews

**Use this the night before and morning of:** derive from first principles when you blank; use the one-liner skeletons when you need to "just write it."

---

## 1. First principles (so you can derive, not memorize)

| Principle | Meaning | Interview translation |
|-----------|--------|------------------------|
| **Stream = lazy pipeline** | Nothing runs until a **terminal** op. Intermediate ops just build the recipe. | If you forget whether `sorted()` runs eagerly: "Only terminal runs it." |
| **One consumption** | A stream can be used **once**. Reuse = new `list.stream()`. | "Need the data again? Get a new stream from the source." |
| **Order: source → intermediate → terminal** | `source.stream().filter(...).map(...).collect(...)` | Default mental order: **filter → map → (sort/limit/skip) → collect/reduce** |
| **Primitives avoid boxing** | `mapToInt` / `mapToDouble` / `IntStream` give primitives; `map` + `Integer` causes boxing. | "Numbers only? Think mapToDouble / mapToInt / IntStream." |
| **Optional = 0 or 1** | `findFirst`, `findAny`, `min`, `max`, `reduce(one-arg)` return `Optional` because the stream might be empty. | "Result might be missing? It's Optional." |

**When stuck, ask yourself:**  
*"Am I filtering? Mapping? Combining to one value? Building a Map/List?"*  
That picks the right group of ops below.

---

## 2. "Sentence → code" one-liner skeletons

Recite these. When the interviewer says X, your brain hits the row and you write the skeleton, then fill in the lambdas.

| You want to… | Skeleton |
|--------------|----------|
| **Use a list as a stream** | `list.stream()` |
| **Use an array** | `Arrays.stream(arr)` or `Stream.of(a,b,c)` |
| **Only keep items that pass a test** | `.filter(x -> condition)` |
| **Turn each item into something else** | `.map(x -> expression)` |
| **Turn each item into 0+ items and flatten** | `.flatMap(x -> streamOrCollection.stream())` |
| **No duplicates** | `.distinct()` |
| **Sort** | `.sorted(Comparator.comparing(Type::getter))` or `.comparingDouble(...).reversed()` |
| **Top N** | `.sorted(...).limit(n)` |
| **Skip first N** | `.skip(n)` |
| **Nth highest** | `.sorted(Comparator.comparingDouble(X::getSalary).reversed()).skip(n-1).findFirst()` |
| **Single string, no separator** | `.map(X::getName).collect(Collectors.joining())` |
| **Single string with separator** | `.collect(Collectors.joining(", "))` |
| **With prefix/suffix** | `.collect(Collectors.joining(", ", "[", "]"))` |
| **To list** | `.collect(Collectors.toList())` |
| **To set** | `.collect(Collectors.toSet())` |
| **To custom collection** | `.collect(Collectors.toCollection(LinkedList::new))` |
| **Map: key → value, handle duplicates** | `.collect(Collectors.toMap(X::getId, X::getName, (a,b)->a))` |
| **Group by key → list of items** | `.collect(Collectors.groupingBy(X::getCategory))` |
| **Group by key → count** | `.collect(Collectors.groupingBy(..., Collectors.counting()))` |
| **Group by key → average** | `.collect(Collectors.groupingBy(..., Collectors.averagingDouble(X::getNum)))` |
| **Group by key → max element** | `.collect(Collectors.groupingBy(..., Collectors.maxBy(Comparator.comparingDouble(X::getNum))))` |
| **Two buckets (true/false)** | `.collect(Collectors.partitioningBy(x -> condition))` |
| **Total (sum)** | `.map(X::getNum).reduce(0.0, Double::sum)` or `.mapToDouble(X::getNum).sum()` |
| **One value from “combine pair by pair”** | `.reduce((a,b) -> combine(a,b))` → Optional |
| **Any element match?** | `.anyMatch(x -> condition)` |
| **All match?** | `.allMatch(x -> condition)` |
| **None match?** | `.noneMatch(x -> condition)` |
| **First element** | `.findFirst()` |
| **Distinct by key** | `.collect(Collectors.toMap(X::getKey, Function.identity(), (a,b)->a)).values()` then wrap in list if needed |
| **Frequency map (how many per key)** | `.collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))` or with getter `groupingBy(X::getName, counting())` |
| **Unmodifiable list** | `.collect(Collectors.collectingAndThen(Collectors.toList(), Collections::unmodifiableList))` |
| **Optional stream → only present** | `.flatMap(Optional::stream)` (Java 9+) or `.filter(Optional::isPresent).map(Optional::get)` |
| **Sort with nulls last** | `.sorted(Comparator.nullsLast(Comparator.comparing(X::getName)))` |
| **Sort by A then B** | `.sorted(Comparator.comparing(X::getA).thenComparing(X::getB))` |
| **IntStream → List&lt;Integer&gt;** | `IntStream.range(0,n).boxed().collect(Collectors.toList())` |
| **One-pass count/sum/min/max/avg** | `.mapToDouble(X::getNum).summaryStatistics()` |

---

## 3. Mnemonics & recall tricks

- **"FMR"** — **F**ilter, **M**ap, **R**educe/collect. Default pipeline order.
- **"toMap needs 3 when keys duplicate"** — `toMap(key, value, (a,b)->a)` — third is merge.
- **"groupingBy two args = classifier + downstream"** — second arg says “how to combine” (counting, toList, averagingDouble, maxBy, etc.).
- **"partitioningBy = groupingBy with boolean"** — `partitioningBy(predicate)` → `Map<Boolean, List<T>>`.
- **"nth = sort desc, skip(n-1), findFirst"** — same pattern for 2nd max, 3rd max, etc.
- **"distinct-by-key = toMap(key, identity, first-wins) then values"** — identity = `Function.identity()` or `e -> e`.
- **"joining(delim, prefix, suffix)"** — exact order: delimiter first, then prefix, then suffix.
- **"Optional in stream → flatMap(Optional::stream)"** — turns `Stream<Optional<T>>` into `Stream<T>` (Java 9+).
- **"range = half-open, rangeClosed = both ends"** — `range(0,5)` = 0..4; `rangeClosed(0,5)` = 0..5.

---

## 4. When they ask "how do you…" — quick routing

| Question | Route to |
|----------|----------|
| Create stream from list/array | Q1 / "Sentence → code" |
| Filter + transform + string | filter → map → joining |
| Flatten lists | flatMap(Collection::stream) or flatMap(List::stream) |
| Top 3 / top N | sorted + limit |
| 2nd/3rd largest | sorted desc + skip(n-1) + findFirst |
| Sum / total | reduce(0, Double::sum) or mapToDouble(...).sum() |
| Group by X | groupingBy(X::getter) or groupingBy(x -> …) |
| Count per group | groupingBy(..., counting()) |
| Average per group | groupingBy(..., averagingDouble(...)) |
| Two buckets (yes/no) | partitioningBy(predicate) |
| Distinct by field | toMap(getter, identity, (a,b)->a) → values → new ArrayList |
| Frequency map | groupingBy(identity or getter, counting()) |
| First non-repeating | build frequency, filter(count==1), findFirst |
| Handle Optional in stream | flatMap(Optional::stream) or filter+map(get) |
| Sort with nulls | nullsLast(Comparator.comparing(...)) |
| Parallel | list.parallelStream() or stream().parallel() |
| One-pass stats | mapToDouble(...).summaryStatistics() |

---

## 5. One-paragraph “interview story” you can say

*"A stream is a one-use pipeline: you get it from a source like `list.stream()`, add lazy steps like `filter` and `map`, then run it with one terminal op—`collect`, `reduce`, or `findFirst`-style. For collections we usually `filter` then `map` then `collect` with things like `toList`, `joining`, or `groupingBy`. For one value we use `reduce` or `mapToDouble().sum()`. If they ask for nth-largest, we sort descending, skip(n-1), and findFirst. If they ask for distinct by a key, we use toMap(key, identity, merge) and take values."*

Saying that out loud once the morning of the interview helps cement the narrative.

---

## 6. Last-minute drill (5 min)

1. **Write from scratch** (no peeking): "Filter employees with salary &gt; 70k, get their names, join with comma."
2. **Write**: "2nd highest salary."
3. **Write**: "Group by first letter of name, value = count."
4. **Write**: "Distinct by name, keep first."
5. **Say**: "What does flatMap do?" — *"Takes each element to a stream and flattens all those streams into one."*

If you can do 1–4 and answer 5, you’re in good shape. Your `StreamsInterviewQuestions.java` has the full versions to compare.

Good luck tomorrow.
