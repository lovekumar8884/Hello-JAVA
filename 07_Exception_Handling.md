# 07 — Checked vs Unchecked Exceptions (Exception Handling)

> **Interview question:** *"Difference between checked and unchecked exceptions? Which should you throw in your own code? What is try-with-resources? What are best practices?"*

## 30-second answer

- **Checked** (extends `Exception` but NOT `RuntimeException`): compiler forces you to `throws` or `try/catch`. E.g., `IOException`, `SQLException`, `InterruptedException`.
- **Unchecked** (extends `RuntimeException`): compiler doesn't force handling. E.g., `NullPointerException`, `IllegalArgumentException`, `IllegalStateException`.
- **Error** (extends `Error`): serious JVM issues; do not catch. E.g., `OutOfMemoryError`, `StackOverflowError`.

**Modern preference:** use **unchecked** exceptions for most application code (Spring's philosophy). Checked exceptions clutter APIs and don't compose with lambdas.

---

## Exception hierarchy

```
Throwable
├── Error                          (do NOT catch)
│   ├── OutOfMemoryError
│   └── StackOverflowError
└── Exception                      (checked, unless...)
    ├── IOException                (checked)
    ├── SQLException               (checked)
    └── RuntimeException           (unchecked)
        ├── NullPointerException
        ├── IllegalArgumentException
        ├── IllegalStateException
        └── IndexOutOfBoundsException
```

---

## try / catch / finally / try-with-resources

```java
// classic
try {
    var conn = ds.getConnection();
    // ...
} catch (SQLException e) {
    log.error("db failure", e);
    throw new DataAccessException(e);
} finally {
    // cleanup
}

// try-with-resources — auto-close, since Java 7 (multi-resource since Java 9)
try (var conn = ds.getConnection();
     var ps = conn.prepareStatement(sql)) {
    // ...
}   // ps.close(), then conn.close() — reverse order, guaranteed
```

Any resource implementing **`AutoCloseable`** works. Suppressed exceptions from `close()` are attached via `Throwable.getSuppressed()`.

### Multi-catch (Java 7)
```java
catch (IOException | SQLException e) { ... }
```

---

## Key points to remember ⭐

- ⭐ Checked = compile-time contract. Unchecked = programmer error (bug).
- ⭐ Rule of thumb (Josh Bloch): *"Use checked for recoverable conditions, unchecked for programming errors."*
- ⭐ Modern frameworks (Spring, JPA) wrap checked in unchecked → easier to compose with lambdas / streams.
- ⭐ **Never swallow exceptions** — at minimum log with stack trace. `catch (Exception e) {}` is a bug.
- ⭐ **Never catch `Throwable` / `Error`** — you'd catch OOM and stack overflow. Only catch specific exceptions you can handle.
- ⭐ Preserve the cause: `throw new MyException("failed", e);` — never lose the original stack.
- ⭐ `try-with-resources` is preferred to `finally` — closes in reverse order, handles suppressed exceptions, less code.
- ⭐ `finally` runs even after `return`/`break` — but **not** after `System.exit()` or a JVM crash.
- ⭐ Do NOT throw from `finally` — it silently overwrites the exception from `try`.
- ⭐ Do NOT catch `InterruptedException` and ignore it — either restore the flag (`Thread.currentThread().interrupt()`) or rethrow.

---

## Custom exceptions — pattern

```java
public class OrderNotFoundException extends RuntimeException {
    public OrderNotFoundException(long id) {
        super("Order not found: " + id);
    }
}
```

- Prefer **RuntimeException** for domain errors (like Spring's `DataAccessException`).
- Include enough context in the message (IDs, keys) — not stack traces alone.
- Provide constructors that accept a `cause`.

---

## Common follow-up questions

**Q: Why doesn't Java's lambda expression support checked exceptions?**
A: Functional interfaces like `Function<T,R>` don't declare `throws`. You either wrap in a try/catch inside the lambda or use libraries like Vavr's `CheckedFunction`.

**Q: What is exception chaining?**
A: `throw new BusinessException(e);` — the wrapped `e` is available via `Throwable.getCause()`. Prevents losing the root cause.

**Q: What's a suppressed exception?**
A: If `try-with-resources` throws in the body AND `close()` throws, the close-exception is *suppressed* and attached to the primary via `getSuppressed()`.

**Q: When would you catch `Error`?**
A: Almost never. Sometimes at the very top-level of a framework (thread pool worker) to log and let the thread die.

**Q: What is `NoClassDefFoundError` vs `ClassNotFoundException`?**
A: `ClassNotFoundException` (checked) — thrown by `Class.forName` when the class isn't found at runtime. `NoClassDefFoundError` (Error) — class was present at compile time but is missing at runtime (usually a classpath issue).

**Q: When does `finally` NOT run?**
A: `System.exit(...)`, JVM crash, infinite loop or blocking call inside `try` that never returns, or the daemon thread being killed on JVM shutdown.

**Q: What happens if both `try` and `finally` return?**
A: The `finally` return **wins** and swallows the `try` return (and any exception). This is a bug — never return from `finally`.

---

## Gotchas / traps

- **Empty catch blocks.** `catch (Exception e) {}` — silent failure. Reviewers will fail you.
- **Logging AND throwing.** Pick one — otherwise the same error is logged multiple times up the stack.
- **`throws Exception`** on public APIs — too broad; forces callers to catch everything.
- **Losing InterruptedException** — always either rethrow or `Thread.currentThread().interrupt();`.
- **Overusing checked exceptions** in library APIs — makes the API painful and doesn't help correctness.
- **`e.printStackTrace()`** — writes to stderr, ignores logging framework, unstructured. Use `log.error("msg", e)`.
