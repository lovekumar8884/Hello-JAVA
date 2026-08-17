# 05 — String vs StringBuilder vs StringBuffer + Why is String immutable?

> **Interview question:** *"Why is String immutable in Java? What are the differences between `String`, `StringBuilder`, and `StringBuffer`? What is the String pool?"*

## 30-second answer

| | `String` | `StringBuilder` | `StringBuffer` |
|---|---|---|---|
| Mutable? | ❌ Immutable | ✅ Mutable | ✅ Mutable |
| Thread-safe? | ✅ (immutable) | ❌ No | ✅ Yes — every method `synchronized` |
| Speed | — | **Fastest** in single-threaded code | Slower due to sync overhead |
| Since | 1.0 | 1.5 | 1.0 |

**Rule:** Use `String` for constants and short concatenations; use `StringBuilder` for building strings in a loop; use `StringBuffer` only in legacy code — modern concurrent code prefers other primitives.

---

## Why is String immutable?

1. **String pool / interning** — literals are shared. If Strings were mutable, one reference could mutate the pooled value and affect every other holder.
2. **Security** — used everywhere: file paths, URLs, class names for `Class.forName`, DB connection strings, HashMap keys. Mutation would enable TOCTOU (time-of-check vs time-of-use) attacks.
3. **Thread safety by design** — no synchronization needed to share across threads.
4. **HashCode caching** — String caches its hashCode. Only safe because the content never changes → safe key for HashMap/HashSet.
5. **Class loading integrity** — class names and package names must not mutate between "check" and "use."

### Implementation
```java
public final class String { ...      // final so no subclass can add mutability
    private final byte[] value;      // final field, private, never leaked
    private int hash;                // cached hashCode (0 until computed)
}
```
Since Java 9, `String` stores **`byte[]`** with a `coder` byte (LATIN-1 or UTF-16), saving memory for ASCII-heavy strings ("compact strings").

---

## String pool (a.k.a. intern pool)

- All **string literals** ("hello") are automatically pooled in the heap (moved from PermGen to heap in Java 7+).
- `"abc" == "abc"` → **true** (same pooled instance).
- `new String("abc") == "abc"` → **false** (new is on heap, not the pool).
- `new String("abc").intern() == "abc"` → **true**.

```java
String a = "hello";               // literal → pool
String b = "hello";               // returns SAME pooled reference
String c = new String("hello");   // NEW object on heap
System.out.println(a == b);       // true
System.out.println(a == c);       // false
System.out.println(a.equals(c));  // true
```

⭐ Interview trap: `==` compares references. Always use `.equals()` for content.

---

## Concatenation compilation

```java
String s = "a" + "b" + var + "c";
```
- Constant-only concatenation (`"a" + "b"`) → compiler folds at compile time.
- Mixed (contains variables) → in modern JDKs (9+), the compiler emits an **`invokedynamic`** call to `StringConcatFactory` which picks the best strategy at runtime. Old JDKs emitted `StringBuilder.append(...)`.
- In a **loop**, always use `StringBuilder` explicitly — otherwise each iteration allocates a new StringBuilder and copies.

```java
// BAD in a loop — O(n^2) allocations
String s = "";
for (int i = 0; i < 1000; i++) s += i;

// GOOD — O(n)
StringBuilder sb = new StringBuilder();
for (int i = 0; i < 1000; i++) sb.append(i);
String s = sb.toString();
```

---

## Key points to remember ⭐

- ⭐ `String` is **final and immutable** — cannot be extended, cannot be mutated.
- ⭐ Two ways to create a String: **literal** (goes to pool) and **`new String(...)`** (heap, not pool by default). `intern()` moves it to the pool.
- ⭐ `hashCode()` is cached — makes String an ideal HashMap key.
- ⭐ Use `StringBuilder` in loops; `StringBuffer` only if you truly need thread safety (rare — usually you'd use higher-level concurrent structures).
- ⭐ `String.equals` compares content, `==` compares references.
- ⭐ Since Java 9, `String` uses `byte[]` with `coder` flag → half the memory for ASCII text.
- ⭐ Prefer `String.format`, text blocks (`"""..."""` Java 15+), or `StringBuilder` over `+` in performance-critical paths.

---

## Common follow-up questions

**Q: Where is the string pool stored?**
A: Since Java 7, on the **heap** (moved from PermGen). Since Java 8, PermGen was replaced by Metaspace.

**Q: What does `String.intern()` do?**
A: Adds the current string to the pool if not present; returns the pooled reference. Excessive interning in old JDKs could OOM PermGen — safer on heap since Java 7.

**Q: How many objects are created by `String s = new String("abc")`?**
A: **Two**: `"abc"` in the pool (if not already there) and a new String on the heap. If already interned, then just one new one.

**Q: Is `StringBuilder` thread-safe?**
A: No. `StringBuffer` is (methods are `synchronized`). Modern advice: keep a StringBuilder thread-local rather than reaching for StringBuffer.

**Q: Why is `String` a good HashMap key?**
A: Immutable, hashCode is cached, well-defined `equals` and `hashCode`, well-distributed hash.

**Q: How do you compare strings ignoring case?**
A: `s1.equalsIgnoreCase(s2)` or `s1.compareToIgnoreCase(s2)`.

**Q: What is a text block (Java 15+)?**
A: Multi-line string literal:
```java
String json = """
    {
      "name": "Java"
    }
    """;
```

---

## Gotchas / traps

- `==` vs `.equals()` — reflex answer must be `.equals()` for content.
- Concatenation in a hot loop without `StringBuilder`.
- Using `+=` on a `String` inside a method thinking it modifies the caller's variable — String is immutable; the local reference is reassigned only.
- Interning huge unpredictable strings — floods the pool.
- `String.substring` in Java 6 shared the char[] with the parent (memory leak risk). Fixed in Java 7 — now it copies.
