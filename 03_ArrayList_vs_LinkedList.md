# 03 — ArrayList vs LinkedList — which to use when?

> **Interview question:** *"What's the difference between `ArrayList` and `LinkedList`? Which one would you pick and why?"*

## 30-second answer

| Operation | ArrayList | LinkedList |
|---|---|---|
| Backing store | Dynamic array (`Object[]`) | Doubly-linked list of nodes |
| `get(i)` / random access | **O(1)** | O(n) — walks the list |
| `add(e)` at end (amortized) | **O(1)** | O(1) |
| `add(0, e)` at head | O(n) shift | **O(1)** |
| `remove(i)` in middle | O(n) shift | O(n) to find + O(1) to unlink |
| Memory / entry | Just the reference | Reference + 2 pointers + node object ≈ 3–4× |
| Cache friendliness | ⭐ Excellent (contiguous) | ❌ Poor (pointer chasing) |

**Practical rule:** Default to `ArrayList`. In 95% of real code, its cache-friendly array beats `LinkedList` even for insertions, because `System.arraycopy` is extremely fast. Pick `LinkedList` only when you use it **as a `Deque` / queue** (head/tail adds).

---

## Under the hood

### ArrayList
- Default capacity **10** on first add.
- Growth: `newCap = oldCap + (oldCap >> 1)` → **~1.5×** every resize.
- `add(index, e)` uses `System.arraycopy` to shift right — memory move, not per-element loop.
- Iteration uses direct index → very fast.

### LinkedList
- Each element wrapped in a `Node<E>` with `prev` / `next` pointers.
- Implements both `List` **and** `Deque` → use it for `addFirst / addLast / pollFirst / pollLast`.
- Random `get(i)` walks from the closer end (head or tail).

---

## Key points to remember ⭐

- ⭐ "LinkedList has O(1) insert" is technically true, but only if you already hold a reference to the node. `list.add(index, e)` first **walks** to `index` → still O(n).
- ⭐ Modern CPUs favor ArrayList because contiguous memory hits L1/L2 cache; LinkedList's pointer chasing causes cache misses.
- ⭐ For queue/stack behavior, prefer `ArrayDeque` over both — faster than LinkedList, no capacity issues, better cache locality.
- ⭐ `ArrayList` allows null; both are **not thread-safe**. For concurrency use `CopyOnWriteArrayList` (read-heavy) or `Collections.synchronizedList`.
- ⭐ `subList(from, to)` returns a **view**, not a copy. Modifying the sublist modifies the original.

---

## Common follow-up questions

**Q: If I need to insert at the beginning of a list 1 million times, which is faster?**
A: LinkedList (O(1) per `addFirst`) beats ArrayList (O(n) shift). But if you plan to later index into it, ArrayList wins overall.

**Q: Which is more memory-efficient?**
A: ArrayList — one reference per element (plus some slack in the array). LinkedList has ~3× overhead per element for the node + prev/next.

**Q: What's the growth factor of ArrayList?**
A: 1.5× (`oldCap + oldCap/2`). Historical trivia: Vector was 2×. 1.5× is a compromise between wasted memory and resize frequency.

**Q: How does `remove(Object)` work on ArrayList?**
A: O(n) — linear scan to find matching element (uses `.equals()`), then `System.arraycopy` to shift.

**Q: `List.of(...)` vs `Arrays.asList(...)` vs `new ArrayList<>()`?**
- `List.of(...)` — **immutable** since Java 9. Any modification throws `UnsupportedOperationException`.
- `Arrays.asList(...)` — **fixed size**, backed by the array. Can `set(i, v)` but not `add / remove`.
- `new ArrayList<>(Arrays.asList(...))` — fully mutable.

**Q: How would you make an ArrayList thread-safe?**
A: Options:
- `Collections.synchronizedList(list)` — locks each op; iterate under `synchronized(list){}`.
- `CopyOnWriteArrayList` — writes copy the whole array; **great for read-heavy** (e.g., listener lists); bad for write-heavy.

---

## Gotchas / traps

- Iterating and removing at the same time → `ConcurrentModificationException`. Use `iterator.remove()` or `removeIf(...)`.
- `Arrays.asList(int[] a)` → returns a `List<int[]>` of size 1! Use boxed `Integer[]` or `IntStream.of(a).boxed().collect(...)`.
- `ArrayList.trimToSize()` exists — useful before serialization / long-term storage to reclaim slack.
- `LinkedList` implementing `Queue` / `Deque` lures juniors into using it for FIFO. **Prefer `ArrayDeque`** — it's faster.
