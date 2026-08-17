# 14 — Optional — How to use it correctly

> **Interview question:** *"What is `Optional`? Why was it introduced? How is it used correctly (and misused)? Difference between `orElse`, `orElseGet`, `orElseThrow`?"*

## 30-second answer

- `Optional<T>` (Java 8) = a container that either **holds a value** or is **empty**.
- **Purpose:** a **return type** that makes "may be absent" explicit → forces callers to handle the empty case → reduces `NullPointerException`.
- **Correct usage:** as a **return type** of methods where absence is a valid outcome (e.g., "user not found").
- **Anti-patterns:** field type, method parameter, collection element, or blindly calling `.get()`.

---

## Creating an Optional

```java
Optional.of(value);            // NPE if value is null
Optional.ofNullable(value);    // empty if null, else present
Optional.empty();              // always empty
```

⭐ `Optional.of` is a bug detector — use it when you're sure the value is non-null.

---

## Consuming an Optional (idioms)

```java
Optional<User> maybeUser = repo.findByEmail(email);

// 1) provide a default
User u = maybeUser.orElse(GUEST);

// 2) provide a default LAZILY (only computed if empty)
User u = maybeUser.orElseGet(() -> loadDefaultFromDb());

// 3) throw if empty
User u = maybeUser.orElseThrow(() -> new UserNotFoundException(email));
User u = maybeUser.orElseThrow();   // NoSuchElementException, Java 10+

// 4) act if present
maybeUser.ifPresent(user -> emailSvc.send(user.getEmail(), msg));

// 5) act if present or otherwise (Java 9+)
maybeUser.ifPresentOrElse(
    user -> log.info("hit {}", user),
    () -> log.info("miss")
);

// 6) transform
Optional<String> name = maybeUser.map(User::getName);

// 7) transform to another Optional (avoid Optional<Optional<X>>)
Optional<Address> addr = maybeUser.flatMap(User::findAddress);   // findAddress returns Optional<Address>

// 8) filter
Optional<User> premium = maybeUser.filter(User::isPremium);

// 9) convert to Stream (Java 9+)
maybeUser.stream().map(User::getName).forEach(System.out::println);

// 10) fallback to another Optional (Java 9+)
Optional<User> user = repo.findByEmail(email).or(() -> repo.findByPhone(phone));
```

---

## `orElse` vs `orElseGet` — the most common trap ⭐

```java
// BAD: heavy() is called even if the Optional is present
user = maybeUser.orElse(heavy());

// GOOD: heavy() is only called if empty
user = maybeUser.orElseGet(() -> heavy());
```

- `orElse(x)` — always evaluates `x`. Use for **constants or cheap defaults**.
- `orElseGet(supplier)` — lazy. Use for **expensive defaults**.

---

## Key points to remember ⭐

- ⭐ Purpose = **explicit "value may be absent"** in return types.
- ⭐ **Never call `.get()` without a prior `isPresent()` check** — it throws `NoSuchElementException`. Prefer `orElse*` / `ifPresent`.
- ⭐ **Never** use `Optional` as a **field** — not `Serializable`, extra allocation, defeats the purpose (fields can be null anyway).
- ⭐ **Never** use `Optional` as a **method parameter** — pushes the burden onto the caller. Overload the method instead.
- ⭐ **Never** put `Optional` inside a collection — use empty collections to represent absence.
- ⭐ Prefer `Optional.ofNullable` when the source is nullable; use `Optional.of` when you're *asserting* non-null.
- ⭐ `map` transforms the value. `flatMap` is for functions that already return `Optional` (avoids `Optional<Optional<X>>`).
- ⭐ `orElseGet(supplier)` is lazy; `orElse(value)` always evaluates the argument.

---

## Anti-patterns (don't do these)

```java
// ❌ Optional field — Optional is not serializable; adds boxing per instance
class User { private Optional<String> nickname; }

// ❌ Optional parameter
void save(User u, Optional<Address> addr) { ... }
// Prefer overloading:
void save(User u) { ... }
void save(User u, Address addr) { ... }

// ❌ Optional inside collection
Map<String, Optional<User>> cache;
// use: return empty Optional or a sentinel; use Map.getOrDefault or computeIfAbsent

// ❌ isPresent + get — the check-then-use anti-pattern
if (o.isPresent()) { return o.get(); }
// Prefer:
return o.orElse(null);   // or ifPresent / orElseThrow

// ❌ Optional as return of getters on entity — noisy; JPA doesn't like it on managed fields
```

---

## Common follow-up questions

**Q: Why not just return `null`?**
A: `null` is silent — callers forget to check. `Optional` is loud — the type says "may be absent," compiler helps enforce handling.

**Q: Is `Optional` serializable?**
A: No. Do not put it into DTOs sent over the wire or JPA entities you serialize.

**Q: When should you use `Optional`?**
A: **Return type** of methods where "no result" is a normal case (`findById`, `findByEmail`, cache lookups).

**Q: What are `OptionalInt`, `OptionalLong`, `OptionalDouble`?**
A: Primitive-specialized variants to avoid autoboxing. Used by `IntStream.max()` etc.

**Q: How do you convert `Optional<T>` to a `Stream<T>`?**
A: `Optional.stream()` (Java 9+). Great for flattening `List<Optional<T>>` to `List<T>` via `.flatMap(Optional::stream)`.

**Q: What throws `NoSuchElementException` on `Optional`?**
A: `.get()` and `.orElseThrow()` when empty.

**Q: How do you chain multiple potentially-empty lookups?**
A: `findX().or(() -> findY()).or(() -> findZ()).orElseThrow(...)`.

---

## Gotchas / traps

- `optional.orElse(compute())` — `compute()` runs every time.
- `optional.get()` without check — an NPE-equivalent in disguise.
- Serializing an `Optional` field with Jackson — depends on module, defaults to `{"value": ...}` in older versions.
- Wrapping already-`Optional` results in another `Optional` — use `flatMap`.
- Storing `Optional` in an entity — JPA / DB layer rarely likes it.
- Using `Optional` in `equals`/`hashCode` — the extra boxing rarely helps.
