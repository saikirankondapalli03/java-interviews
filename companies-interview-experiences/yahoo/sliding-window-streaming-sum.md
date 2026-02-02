# Sliding Window - Streaming Version (Time-based Events)

**Source:** Yahoo Interview  
**Pattern:** Sliding Window with Deque  
**Related LeetCode:** 933 (Number of Recent Calls), 362 (Design Hit Counter)

---

## Problem Statement

Stream of data arrives as `(time, total clicks)`:

- `(1, 5)` → 5 clicks at 1st sec
- `(4, 10)` → 10 clicks at 4th sec
- `(6, 2)` → 2 clicks at 6th sec

**Window size:** 3 seconds (sliding)  
**Goal:** Compute the sum of counts over the window as events arrive.

---

## Solution: Streaming Version

Use a **deque** as FIFO. At each event, remove events with `time < t - k + 1`, add current event, emit sum.

```java
public static List<Integer> getWindowSumsStreaming(List<int[]> events, int windowSize) {
    if (events == null || events.isEmpty()) return new ArrayList<>();
    events = new ArrayList<>(events);
    events.sort((a, b) -> Integer.compare(a[0], b[0]));

    List<Integer> result = new ArrayList<>();
    Deque<int[]> window = new ArrayDeque<>();
    int sum = 0;

    for (int[] e : events) {
        int time = e[0], clicks = e[1];
        int windowStart = time - windowSize + 1;

        while (!window.isEmpty() && window.peekFirst()[0] < windowStart) {
            sum -= window.pollFirst()[1];
        }
        window.offerLast(e);
        sum += clicks;
        result.add(sum);
    }
    return result;
}
```

---

## Why `while` not `if`?

Multiple events can exit the window in one step (same timestamp or larger step size). Use `while` to remove all of them.

---

## Deque vs Monotonic Deque

| Use Case | Structure | Purpose |
|----------|-----------|---------|
| **Streaming sum** (this problem) | Deque as **FIFO** | Maintain events in time order; add back, remove front |
| **Max/Min in window** (LeetCode 239) | **Monotonic deque** | Keep indices in decreasing (max) or increasing (min) order for O(1) max/min |

---

## Related LeetCode

- **933 – Number of Recent Calls:** `ping(t)` returns count in last 3000ms – same pattern
- **362 – Design Hit Counter:** `hit(t)` + `getHits(t)` for last 300 seconds
