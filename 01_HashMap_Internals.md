# 01 — How does HashMap work internally?

> **Interview question:** *"Explain how HashMap works internally in Java. What happens when you call `put()` and `get()`? What changed in Java 8?"*

## 30-second answer (recall this first)

- `HashMap` = **array of buckets** (`Node<K,V>[] table`). Default capacity **16**, default load factor **0.75**.
- `put(k, v)`:
  1. Compute `hash = hash(key.hashCode())` — mixes high bits into low bits.
  2. Index = `hash & (n - 1)` where `n` = table length (power of 2).
  3. If bucket empty → place node. If not → walk the bucket:
     - Match on `hash` **and** `key.equals(existingKey)` → replace value.
     - Else append to bucket (Java 8+: **LinkedList → red-black tree** once ≥ 8 nodes and table ≥ 64).
  4. If `size > capacity × loadFactor` → **resize** (double table, rehash all entries).
- `get(k)` → same hash → index → walk bucket → `equals` match → return value.

---

## Detailed explanation

### The bucket array
```java
transient Node<K,V>[] table;
static class Node<K,V> {
    final int hash;
    final K key;
    V value;
    Node<K,V> next;   // linked list; may point to TreeNode
}
```

### Why capacity is a power of 2
- `index = hash & (n - 1)` is cheaper than `hash % n`, and only works correctly when `n` is a power of 2.
- Resize doubles capacity, so index recalculation is essentially a bit shift.

### Java 8 improvement — Treeify
- When a single bucket has ≥ **`TREEIFY_THRESHOLD` = 8** nodes **and** the table length is ≥ **`MIN_TREEIFY_CAPACITY` = 64**, the bucket converts from linked list to **red-black tree**.
- Lookup in a heavily-collided bucket drops from **O(n)** to **O(log n)**.
- Below capacity 64 → `resize()` is preferred over treeifying.
- When tree shrinks below `UNTREEIFY_THRESHOLD` = 6, it reverts to a linked list.

### The hash-spread function
```java
static final int hash(Object key) {
    int h;
    return (key == null) ? 0 : (h = key.hashCode()) ^ (h >>> 16);
}
```
XORs top 16 bits into the bottom → protects small tables from poor hash codes.

### Resize
- Trigger: `size > threshold` where `threshold = capacity × loadFactor`.
- Doubles the array; each existing entry either **stays at old index** or moves to **old index + old capacity** (decided by one bit of the hash).
- Resize is O(n) and can happen at unfortunate times — **pre-size the map** (`new HashMap<>(expectedSize / 0.75 + 1)`) in hot paths.

### `put()` walkthrough (simplified)
```
computeHash → find bucket
if bucket empty:              place new node
else:
    if first node key matches: replace value
    else if TreeNode:          tree.put
    else:                      walk linked list;
                               if key exists → replace
                               else append → maybe treeify
if size > threshold:           resize
```

---

## Key points to remember ⭐

- ⭐ `hashCode()` **decides the bucket**; `equals()` **decides the match inside the bucket** — you MUST override both together.
- ⭐ Two keys with the same `hashCode()` are **collisions**, not duplicates. `equals()` disambiguates them.
- ⭐ Mutating a key **after** insertion so its `hashCode()` changes = **entry is lost** (get/remove will look in the wrong bucket).
- ⭐ HashMap is **not thread-safe**. Concurrent `put` in Java 7 could cause an **infinite loop** during resize (fixed in Java 8, but still not thread-safe → use `ConcurrentHashMap`).
- ⭐ Null keys: **HashMap allows one null key** (stored at index 0). `Hashtable` and `ConcurrentHashMap` do **not** allow null keys or values.
- ⭐ Default load factor 0.75 is a time-vs-space compromise. Lower → less collision, more memory. Higher → more collision, less memory.

---

## Common follow-up questions

**Q: Why is HashMap's initial capacity 16?**
A: A small power of 2 — cheap `& (n-1)` indexing, and 16 is empirically a good balance between initial memory and resize frequency.

**Q: What's the worst-case time complexity of `get()`?**
A: Java 7 → O(n) (linked list of collisions). Java 8+ → O(log n) after treeification. Average always O(1).

**Q: What happens if two keys have the same hashCode?**
A: They go to the same bucket. On lookup, HashMap uses `equals()` to pick the right one.

**Q: Difference between `HashMap` and `LinkedHashMap`?**
A: `LinkedHashMap` keeps a doubly-linked list across all entries → **preserves insertion order** (or access order in LRU mode). Slightly higher memory; iteration is predictable. Perfect base for an LRU cache: `new LinkedHashMap<>(cap, 0.75f, true)` + override `removeEldestEntry`.

**Q: Difference between `HashMap` and `TreeMap`?**
A: `TreeMap` uses a **red-black tree** keyed by natural ordering / `Comparator`. Operations are **O(log n)** but the keys are always **sorted** and it supports range queries (`subMap`, `headMap`, `tailMap`).

**Q: Can HashMap store `null`?**
A: One `null` key, unlimited `null` values.

**Q: Why is `String` a good HashMap key?**
A: Immutable → hashCode never changes after insertion. Cached hashCode. Well-distributed hash. `equals` is defined by content.

**Q: Fail-fast iterator?**
A: Iterators check `modCount`; if the map is structurally modified during iteration (except via `iterator.remove()`), you get `ConcurrentModificationException`.

---

## Gotchas / traps

- Using **mutable objects as keys** (e.g., a POJO with mutable fields used in equals/hashCode) — after mutation you can no longer find the entry.
- Forgetting to override `hashCode()` when overriding `equals()` — the map "loses" logically-equal keys.
- Relying on **iteration order** — HashMap gives none. Use `LinkedHashMap` if you need it.
- Concurrent access without external sync → data corruption. Prefer `ConcurrentHashMap`.
- Very poor `hashCode()` (all returning same value) → all entries in one bucket → tree lookup, but still much slower than well-distributed hashing.
