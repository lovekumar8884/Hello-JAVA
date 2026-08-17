# 02 — ConcurrentHashMap vs HashMap vs Hashtable vs Collections.synchronizedMap

> **Interview question:** *"How do you use a Map safely from multiple threads? What's the difference between `Hashtable`, `Collections.synchronizedMap`, and `ConcurrentHashMap`? How does `ConcurrentHashMap` achieve high concurrency?"*

## 30-second answer

| Option | Thread-safe? | Locking granularity | Nulls? | Notes |
|---|---|---|---|---|
| `HashMap` | ❌ No | — | 1 null key, many null values | Fastest; single-threaded only |
| `Hashtable` | ✅ Yes | **Whole map** (method-level `synchronized`) | ❌ No null key or value | Legacy — avoid |
| `Collections.synchronizedMap(new HashMap<>())` | ✅ Yes | **Whole map** (one intrinsic lock) | Same as HashMap | Simple, but iterate under `synchronized(map){...}` |
| `ConcurrentHashMap` | ✅ Yes | **Bucket / CAS-level** (very fine-grained) | ❌ No null key or value | Preferred for concurrent code |

**Rule of thumb:** New code → `ConcurrentHashMap`. Read-mostly, small map → `synchronizedMap` is fine. Never use `Hashtable`.

---

## How ConcurrentHashMap achieves concurrency

### Java 7 — Segment-based locking
- Divided into ~16 "segments," each a mini-hashtable with its own `ReentrantLock`.
- Different keys in different segments → operations run in parallel.
- Global operations (`size`, `isEmpty`) required locking all segments.

### Java 8+ — CAS + synchronized on the first bucket node
- No segments anymore. It's the same bucket array as `HashMap`.
- `put`:
  - If bucket empty → **`Unsafe.compareAndSwap`** to install the first node **without any lock**.
  - If bucket non-empty → **`synchronized (firstNode)`** while walking / updating that bucket only.
- Different buckets = independent locks → massive parallelism.
- `get` is **lock-free** (uses volatile reads).
- Resize is **concurrent** — multiple threads help move buckets during rehashing.
- `size()` uses a striped counter (`LongAdder`-style) → no global lock but eventually consistent.

### Why no nulls?
`get(k)` returning `null` is ambiguous between "not present" and "present with value null" in a concurrent context (no way to atomically check-and-return without a lock).

---

## Useful atomic operations

```java
ConcurrentHashMap<String, Integer> map = new ConcurrentHashMap<>();

// safe frequency counting — no lost updates
map.merge("hits", 1, Integer::sum);

// compute-if-absent with expensive initializer
map.computeIfAbsent(key, k -> loadFromDb(k));

// atomic update
map.compute(key, (k, v) -> v == null ? 1 : v + 1);

// replace only if current value matches
map.replace(key, oldValue, newValue);
```

⭐ Prefer these over `if (!map.containsKey(k)) map.put(k, v);` — the check-then-act is **not atomic**.

---

## Iteration semantics — fail-fast vs weakly consistent

- `HashMap`, `Hashtable`, `synchronizedMap` → **fail-fast** iterators (`ConcurrentModificationException` if the map changes during iteration).
- `ConcurrentHashMap` → **weakly consistent** iterators:
  - Never throw `ConcurrentModificationException`.
  - Reflect **some** but not necessarily all updates made since the iterator was created.
  - Guaranteed to visit each element **at most once**.

---

## Key points to remember ⭐

- ⭐ `synchronizedMap` locks the **entire** map for every operation → reads block reads. `ConcurrentHashMap` allows concurrent reads and per-bucket writes.
- ⭐ Even with `synchronizedMap`, you **must** manually synchronize on the map when iterating:
  ```java
  Map<K,V> m = Collections.synchronizedMap(new HashMap<>());
  synchronized (m) {
      for (K k : m.keySet()) { ... }
  }
  ```
- ⭐ `ConcurrentHashMap` does **not** allow null keys or values.
- ⭐ Use `computeIfAbsent` / `merge` / `compute` for atomic conditional updates — do not roll your own `get + put`.
- ⭐ `size()` on `ConcurrentHashMap` is **approximate** under concurrent modification (though `mappingCount()` returns a `long`).

---

## Common follow-up questions

**Q: Is `ConcurrentHashMap.get()` blocked by a concurrent `put`?**
A: No. `get` is lock-free (volatile reads). It may see the old or new value, but never a corrupted state.

**Q: Can two threads both execute the mapper in `computeIfAbsent` for the same key?**
A: No — `computeIfAbsent` holds the bucket lock while running the mapper. That's why the mapper should be **fast and non-recursive** (do not call `computeIfAbsent` on the same map inside the mapper — you'll deadlock).

**Q: What's the difference between `ConcurrentHashMap` and `ConcurrentSkipListMap`?**
A: `ConcurrentSkipListMap` is the concurrent analogue of `TreeMap` — sorted keys, O(log n), supports range queries. Use it when you need ordering with concurrent access.

**Q: When would you still pick `synchronizedMap`?**
A: Very small map, very simple code path, no hot contention. Also when you already have external locking for other reasons.

**Q: What was the "infinite loop during resize" HashMap bug?**
A: In Java 7 HashMap, concurrent `put` during resize could create a **cyclic linked list** in a bucket. A subsequent `get` would loop forever pinning a CPU at 100%. Fixed in Java 8's resize algorithm (splits list into two rather than reversing it), but HashMap is **still not thread-safe** — use ConcurrentHashMap.

---

## Gotchas / traps

- **Compound actions** are not atomic: `if (map.containsKey(k)) map.put(k, v+1);` — two threads can both read the old value. Use `merge` / `compute`.
- Iterating `ConcurrentHashMap` and expecting a strong snapshot — you get a weakly consistent view.
- Storing `null` values → NPE.
- Assuming `size()` is accurate under load — it's approximate.
