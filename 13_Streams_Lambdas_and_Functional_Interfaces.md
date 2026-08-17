# 13 — Streams API + Lambdas + Functional Interfaces

> **Interview question:** *"What are Java Streams? Difference between intermediate and terminal operations? What is a functional interface? What are lambdas? What are the built-in functional interfaces? When would you use a parallel stream?"*

## 30-second answer

- **Lambda** — anonymous implementation of a **functional interface** (an interface with exactly one abstract method).
- **Stream** — lazy, pipeline-style abstraction over a sequence of elements. Not a data structure — it doesn't store anything.
- Pipeline = **Source → intermediate ops → terminal op**. Intermediate ops are **lazy** (nothing happens until a terminal op fires).
- Streams are **single-use** — you cannot iterate twice.
- **Parallel streams** = auto-splitting on `ForkJoinPool.commonPool()`. Good for CPU-bound work on large in-memory data; poor for I/O.

---

## Functional interfaces & lambdas

```java
@FunctionalInterface           // @FunctionalInterface enforces "exactly 1 abstract method"
interface Transformer<T, R> {
    R apply(T t);
}

Transformer<String, Integer> len = s -> s.length();   // lambda
Transformer<String, Integer> len2 = String::length;   // method reference
```

### Built-in functional interfaces (memorize)

| Interface | Method | Shape |
|---|---|---|
| `Function<T, R>` | `R apply(T)` | 1 arg → result |
| `BiFunction<T, U, R>` | `R apply(T, U)` | 2 args → result |
| `Predicate<T>` | `boolean test(T)` | T → boolean |
| `Consumer<T>` | `void accept(T)` | T → void |
| `Supplier<T>` | `T get()` | () → T |
| `UnaryOperator<T>` | `T apply(T)` | T → T (extends `Function<T,T>`) |
| `BinaryOperator<T>` | `T apply(T,T)` | (T,T) → T (extends `BiFunction<T,T,T>`) |

Primitive-specialized variants avoid autoboxing: `IntFunction`, `ToIntFunction`, `IntPredicate`, `IntConsumer`, `IntSupplier`, etc.

### Method reference syntaxes
- `Class::staticMethod` → `Integer::parseInt`
- `Class::instanceMethod` → `String::length` (bound to each element)
- `instance::method` → `System.out::println`
- `Class::new` → `ArrayList::new` (constructor reference)

---

## Streams — intermediate vs terminal

### Intermediate (return `Stream`, lazy)
`filter` `map` `flatMap` `mapToInt` `distinct` `sorted` `peek` `limit` `skip` `takeWhile` `dropWhile`

### Terminal (trigger execution)
`forEach` `toList` (Java 16+) / `collect(Collectors.toList())` `reduce` `count` `min` `max` `sum` `anyMatch` `allMatch` `noneMatch` `findFirst` `findAny`

### Short-circuit ops
`findFirst` `findAny` `anyMatch` `allMatch` `noneMatch` `limit` — can finish before consuming the whole source.

---

## Reference examples

```java
// classic pipeline
List<String> names = employees.stream()
    .filter(e -> e.getSalary() > 100_000)
    .map(Employee::getName)
    .sorted()
    .toList();

// grouping
Map<String, List<Employee>> byDept = employees.stream()
    .collect(groupingBy(Employee::getDept));

// grouping + reducing
Map<String, Double> avgSalaryByDept = employees.stream()
    .collect(groupingBy(Employee::getDept, averagingDouble(Employee::getSalary)));

// partitioning
Map<Boolean, List<Employee>> highPaid = employees.stream()
    .collect(partitioningBy(e -> e.getSalary() > 100_000));

// joining strings
String csv = names.stream().collect(joining(", ", "[", "]"));

// counting
Map<String, Long> countByDept = employees.stream()
    .collect(groupingBy(Employee::getDept, counting()));

// flatMap — flatten nested
List<String> allTags = posts.stream()
    .flatMap(p -> p.getTags().stream())
    .distinct()
    .toList();

// reduce — folding
int total = nums.stream().reduce(0, Integer::sum);
```

---

## `Collectors` you should know cold

- `toList()`, `toSet()`, `toUnmodifiableList()`, `toMap(kFn, vFn)`
- `groupingBy(classifier)` / `groupingBy(classifier, downstream)`
- `partitioningBy(predicate)`
- `counting()`, `summingInt`, `averagingDouble`
- `mapping(fn, downstream)` — transform before collecting
- `reducing(identity, op)`
- `joining(delim, prefix, suffix)`

### `toMap` duplicate key trap

```java
// throws IllegalStateException on duplicate keys!
Map<String, User> byName = users.stream()
    .collect(toMap(User::getName, u -> u));

// safe: provide merge function
Map<String, User> byName = users.stream()
    .collect(toMap(User::getName, u -> u, (a, b) -> a));   // keep first
```

---

## Parallel streams — pros & cons

```java
list.parallelStream().filter(...).collect(...);
```

- Splits work across the common `ForkJoinPool` (parallelism ≈ `#cpus - 1`).
- ✅ Good for **large, in-memory, CPU-bound** work with independent elements.
- ❌ Bad for I/O, small collections, small tasks, order-dependent work, or when the pipeline uses shared mutable state.
- ⭐ **Never do blocking I/O in a parallel stream** — it starves the common pool.

---

## Key points to remember ⭐

- ⭐ Streams are **lazy** — nothing runs until the terminal op.
- ⭐ Streams are **single-use** — reusing throws `IllegalStateException`.
- ⭐ Streams do **not modify** the source collection.
- ⭐ `map` transforms; `flatMap` flattens (1-to-many).
- ⭐ Use **method references** (`Employee::getName`) where they're clearer than lambdas.
- ⭐ Prefer `Collectors.toUnmodifiableList()` / `.toList()` (Java 16+) for defensive coding.
- ⭐ **Avoid `peek()`** in production — it's for debugging, may be elided by optimizations.
- ⭐ `forEach` on a parallel stream gives NO ordering guarantees. Use `forEachOrdered` if order matters.
- ⭐ `reduce(identity, accumulator)` needs an associative accumulator; when parallel add a **combiner**.
- ⭐ In parallel, the `identity` in `reduce` must be a true identity (`0` for sum, `1` for product) — otherwise you get wrong sums (`identity` is applied per split).

---

## Common follow-up questions

**Q: Why can't you reuse a stream?**
A: Streams hold state (source spliterator, chain of ops). Once consumed, they're closed. Rebuild from the source.

**Q: Are streams parallel by default?**
A: No. Use `.parallelStream()` or `.parallel()`.

**Q: How is `flatMap` different from `map`?**
A: `map` turns each element into one element. `flatMap` turns each element into a stream, then flattens all those streams into one.

**Q: Give an example where `parallelStream` is a bad idea.**
A: `list.parallelStream().forEach(x -> httpClient.get(x))` — 1000 items on an 8-core machine → only 8 concurrent HTTP calls and the common pool is starved. Use `CompletableFuture` with a dedicated executor.

**Q: What's the difference between `Collectors.toList()` and `Stream.toList()`?**
A: `Stream.toList()` (Java 16+) returns an **unmodifiable** list and is null-friendly. `Collectors.toList()` returns an unspecified mutable list (usually `ArrayList`).

**Q: What's the difference between `findFirst` and `findAny`?**
A: Sequential — identical. Parallel — `findAny` can return any element that a worker finds first (faster); `findFirst` must respect encounter order.

**Q: Can a lambda access an outer variable?**
A: Yes, but the variable must be **effectively final** (assigned once). Otherwise the compiler rejects it.

**Q: Why "effectively final"?**
A: A lambda captures the *value* of the local. If the local could be reassigned, threads might see different values. Requiring effectively final avoids that.

---

## Gotchas / traps

- `stream().collect(toMap(...))` with duplicate keys → runtime exception. Always provide a merge function.
- Modifying the source collection inside a stream pipeline → `ConcurrentModificationException`.
- Using stateful lambdas (`peek(x -> counter++)`) in parallel — race conditions.
- Auto-boxing in `Stream<Integer>` when `IntStream` would do — big perf hit.
- Using `parallelStream` for I/O — starves the common pool.
- Chaining `sorted()` before `filter()` — sorts the whole stream when you could filter first.
- Returning a `Stream` from a public method — the caller can consume it once and only once; usually better to return a `List`.
