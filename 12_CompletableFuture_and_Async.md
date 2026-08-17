# 12 — CompletableFuture — Async programming in Java

> **Interview question:** *"What is `CompletableFuture`? How is it different from `Future`? How do you compose async tasks? What is the default executor?"*

## 30-second answer

- `Future<V>` (Java 5) = **you can only `.get()` and block** on it. No composition, no callbacks.
- `CompletableFuture<V>` (Java 8) = **fully composable async pipeline** — chain `thenApply`, `thenCompose`, combine with `thenCombine`, handle errors with `exceptionally` / `handle`, all non-blocking.
- Default executor for `*Async` methods (no executor arg) = the **common `ForkJoinPool`**. Provide your own for I/O-bound work.

---

## Creating

```java
// wrap an existing value
CompletableFuture<Integer> done = CompletableFuture.completedFuture(42);

// run a task async on the common pool
CompletableFuture<String> f = CompletableFuture.supplyAsync(() -> fetchName(id));

// with a custom executor
CompletableFuture.supplyAsync(() -> callHttp(), httpPool);

// manually complete
var cf = new CompletableFuture<String>();
someCallback.onDone(cf::complete);
someCallback.onError(cf::completeExceptionally);
```

---

## Chaining — the three key operators

| Operator | Takes | Returns | Analog to |
|---|---|---|---|
| `thenApply(fn)` | `Function<T,U>` | `CompletableFuture<U>` | `map` |
| `thenAccept(cons)` | `Consumer<T>` | `CompletableFuture<Void>` | side-effect |
| `thenRun(runnable)` | `Runnable` | `CompletableFuture<Void>` | side-effect, no value |
| **`thenCompose(fn)`** | `Function<T, CompletableFuture<U>>` | `CompletableFuture<U>` | **`flatMap`** — chains async calls |
| `thenCombine(other, bi)` | another CF + `BiFunction` | `CompletableFuture<R>` | zip 2 futures |

```java
CompletableFuture<Order> orderF = fetchUser(id)               // CF<User>
    .thenCompose(u -> fetchLatestOrder(u.getId()))            // CF<Order>
    .thenApply(this::enrichWithShipping);                     // CF<Order>

CompletableFuture<Invoice> invoiceF = orderF
    .thenCombine(fetchTaxRate(), Invoice::new);               // zip
```

⭐ **`thenApply` vs `thenCompose`:** if the mapper itself returns a `CompletableFuture`, you must use `thenCompose` — otherwise you get `CompletableFuture<CompletableFuture<X>>`.

---

## Sync vs `*Async` variants

Every operator has a plain and an `*Async` version:

- `thenApply(fn)` → runs the callback on **whichever thread completed the previous stage** (or the current thread if already complete).
- `thenApplyAsync(fn)` → runs on the **default executor** (ForkJoinPool.commonPool()).
- `thenApplyAsync(fn, executor)` → your executor.

⭐ Choose `*Async` when the mapper is CPU-heavy or blocking; otherwise the plain form is cheaper. Always pass a **custom executor** for blocking I/O — never block the common pool.

---

## Combining many futures

```java
CompletableFuture<Void> all = CompletableFuture.allOf(f1, f2, f3);  // waits for ALL
CompletableFuture<Object> any = CompletableFuture.anyOf(f1, f2, f3); // first done

// pattern: allOf + join results
List<CompletableFuture<Item>> futs = ids.stream()
    .map(id -> CompletableFuture.supplyAsync(() -> fetch(id), pool))
    .toList();

CompletableFuture<List<Item>> allItems = CompletableFuture
    .allOf(futs.toArray(new CompletableFuture[0]))
    .thenApply(v -> futs.stream().map(CompletableFuture::join).toList());
```

---

## Error handling

```java
future
    .exceptionally(ex -> defaultValue)                  // like catch → fallback
    .handle((v, ex) -> ex == null ? v : recover(ex))    // catch + transform (both paths)
    .whenComplete((v, ex) -> log(v, ex));               // side-effect, does NOT swallow
```

- Exceptions propagate down the chain.
- `handle`/`exceptionally` intercept and recover; `whenComplete` observes without altering.
- **`join()`** vs **`get()`**: `join` wraps checked in unchecked (`CompletionException`); good in streams. `get` throws checked `ExecutionException`.

---

## Timeouts (Java 9+)

```java
future.orTimeout(2, TimeUnit.SECONDS);              // completes exceptionally on timeout
future.completeOnTimeout(fallback, 2, TimeUnit.SECONDS); // completes with fallback
```

---

## Key points to remember ⭐

- ⭐ `CompletableFuture` is Java's promise — composable, non-blocking pipelines.
- ⭐ `thenApply` = map. `thenCompose` = flatMap (use when the mapper itself returns a CF).
- ⭐ Default executor for `*Async` = **`ForkJoinPool.commonPool()`** — sized to `#CPUs − 1`. Never block it with I/O. Always pass a custom executor for blocking calls.
- ⭐ Non-async operators run on whichever thread completed the previous stage. Async operators hop threads.
- ⭐ Use `allOf` + `join` in a `thenApply` to collect a `List<T>` from many futures.
- ⭐ `exceptionally` = catch. `handle` = catch + transform (sees both value and exception).
- ⭐ **Never call `.get()` inside a chain** — that turns async back into blocking.

---

## Common follow-up questions

**Q: How is `CompletableFuture` different from `Future`?**
A: `Future` = only `.get()` + `cancel()`, blocking. `CompletableFuture` = full composition, chaining, error handling, timeouts, manual completion.

**Q: What's the difference between `join()` and `get()`?**
A: `get()` throws `ExecutionException` (checked). `join()` throws `CompletionException` (unchecked). Prefer `join` inside streams and lambdas.

**Q: What is `ForkJoinPool.commonPool()`?**
A: Shared pool sized to `Runtime.getRuntime().availableProcessors() - 1`. Used by parallel streams and default async CF methods. **Do not** block it with I/O — starves everything else.

**Q: How do you cancel a `CompletableFuture`?**
A: `cf.cancel(true)` sets the result to `CancellationException`. Note that the running task itself is NOT interrupted unless you built it to check `Thread.interrupted()` — the underlying task may keep running.

**Q: How do you do rate limiting or bulkhead with CompletableFuture?**
A: Wrap the async call in a `Semaphore.acquire/release` inside a `supplyAsync`, or use a dedicated bounded executor as your bulkhead.

**Q: `CompletableFuture` vs Reactive Streams (Reactor/RxJava)?**
A: CF is a single value or single error, once. Reactive streams (`Flux`, `Observable`) emit 0..N values with back-pressure. Use reactive for streaming data.

**Q: What happens if a `thenApply` mapper throws?**
A: The returned CF completes exceptionally with a `CompletionException` wrapping the thrown exception. Downstream stages skip to the first `exceptionally`/`handle`.

---

## Gotchas / traps

- Blocking I/O inside a stage on `commonPool` → starves parallel streams and other CFs.
- Forgetting to pass an executor to `*Async` for blocking calls.
- Using `thenApply` when the mapper returns a `CompletableFuture` → you get a nested `CF<CF<T>>`. Use `thenCompose`.
- `cf.cancel(true)` — misconception that the underlying task is interrupted. It's not.
- Calling `.get()` from a JVM main / UI thread inside a chain — turns async into sync.
- Exceptions in `thenAccept` / `whenComplete` silently vanish unless you also chain `exceptionally`.
- Mixing `allOf` with results — `allOf` returns `CompletableFuture<Void>`; call `.join()` on each source future to collect results.
