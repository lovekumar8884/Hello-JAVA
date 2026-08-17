# 04 — equals() and hashCode() contract (and Comparable / Comparator)

> **Interview question:** *"What is the contract between `equals()` and `hashCode()`? Why must you override both? What breaks if you don't? How is `Comparable` different from `Comparator`?"*

## 30-second answer

**The Contract:**
1. **Reflexive:** `x.equals(x) == true`.
2. **Symmetric:** `x.equals(y) == y.equals(x)`.
3. **Transitive:** if `a.equals(b)` and `b.equals(c)` then `a.equals(c)`.
4. **Consistent:** repeated calls give the same result (if objects unchanged).
5. **`x.equals(null) == false`** always.
6. ⭐ **If `a.equals(b)` then `a.hashCode() == b.hashCode()`.** (The reverse is NOT required.)

**Why both?** Hash-based collections (`HashMap`, `HashSet`) use `hashCode()` to find the bucket, `equals()` to find the entry inside it. Break rule 6 → objects "disappear" from the map.

---

## Canonical implementation (POJO)

```java
public final class User {
    private final long id;
    private final String email;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;                   // reflexive shortcut
        if (!(o instanceof User)) return false;       // handles null + wrong type
        User u = (User) o;
        return id == u.id && Objects.equals(email, u.email);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, email);               // use the SAME fields as equals
    }
}
```

Since Java 14+, prefer a **`record`** — it generates `equals`, `hashCode`, `toString` for you.

---

## Why is symmetry commonly broken?

Subclassing + `instanceof` vs `getClass()`:

```java
// BREAKS symmetry when comparing Point ↔ ColoredPoint
class Point { boolean equals(Object o){ return o instanceof Point && ... } }
class ColoredPoint extends Point { boolean equals(Object o){ return o instanceof ColoredPoint && ... } }
```

Rule of thumb: mark the class **`final`**, or use `getClass() == o.getClass()` (Liskov violation, but preserves symmetry).

---

## Comparable vs Comparator

| | `Comparable<T>` | `Comparator<T>` |
|---|---|---|
| Where defined | Inside the class (`natural ordering`) | Outside — passed as an argument |
| Method | `int compareTo(T o)` | `int compare(T a, T b)` |
| Example | `String`, `Integer`, `LocalDate` | Custom sort orders |
| Use with | `Collections.sort(list)` | `Collections.sort(list, cmp)` / `stream().sorted(cmp)` |

### Java 8 Comparator combinators
```java
Comparator<Employee> cmp = Comparator
    .comparing(Employee::getDept)
    .thenComparing(Employee::getSalary, Comparator.reverseOrder())
    .thenComparing(Employee::getName);

list.sort(cmp);
```

### `equals()` vs `compareTo()` should be **consistent with equals**
- If `a.compareTo(b) == 0` but `!a.equals(b)`, then `TreeSet` / `TreeMap` (which use `compareTo`) will treat them as equal → surprising behavior mixing with `HashSet`.

---

## Key points to remember ⭐

- ⭐ **Equal objects MUST have equal hashCodes.** Unequal objects *may* have the same hashCode (collision).
- ⭐ Override BOTH or NEITHER. Never just one.
- ⭐ `hashCode()` must be based on the **same fields** as `equals()`, and those fields should be **immutable** (or at least not modified after the object is put in a hash-based collection).
- ⭐ Prefer `Objects.equals(a, b)` and `Objects.hash(a, b, c)` — null-safe and short.
- ⭐ For value objects, prefer **`record`** (Java 14+) — auto-generated, correct, immutable.
- ⭐ `TreeMap` / `TreeSet` use `compareTo` / `Comparator`, **NOT** `equals`. Ensure the ordering is *consistent with equals* to avoid surprises.

---

## Common follow-up questions

**Q: Two objects have the same `hashCode`. Are they equal?**
A: Not necessarily. It's a collision. `HashMap` will still call `equals()` to disambiguate.

**Q: What happens in HashMap if I forget to override `hashCode()`?**
A: The default `hashCode()` is identity-based (memory address). Two logically equal objects go to different buckets → `map.get(logicallyEqualKey)` returns null.

**Q: What if I return `0` from `hashCode()` for every object?**
A: The map still works correctly, but all entries collide into one bucket. Lookups degrade to O(n) — or O(log n) in Java 8 after treeification. Also, this is a common DoS attack vector — real code should never do it.

**Q: Should you use mutable fields in `equals`/`hashCode`?**
A: Avoid it. If you must, then never mutate the field once the object is inside a hash-based collection.

**Q: `String.hashCode()` — how is it computed?**
A: `s[0]*31^(n-1) + s[1]*31^(n-2) + ... + s[n-1]`. 31 is odd prime and `31*i` = `(i << 5) - i` (fast shift+sub). Cached inside `String`.

**Q: Why is 31 used as the multiplier?**
A: Odd prime, produces good distribution, and JIT compiles `31*x` to `(x<<5) - x`.

**Q: How is `Comparable` used by `PriorityQueue`?**
A: `PriorityQueue` orders elements by `Comparable` (or a `Comparator` passed to the constructor). Head is always the smallest.

---

## Gotchas / traps

- Using an object with mutable equals/hashCode fields as a HashMap key, then mutating it → entry is orphaned.
- `equals` accepting a wrong type without returning false → `ClassCastException` at runtime.
- Comparators that overflow: `(a, b) -> a.getAge() - b.getAge()` can wrap around for extreme values → use `Integer.compare(a.getAge(), b.getAge())`.
- Returning inconsistent `compareTo` values (e.g., dependent on mutable state) → `TreeMap` gets corrupted.
- Overriding `equals` but forgetting `hashCode` (or vice-versa) — the most common bug in this area.
