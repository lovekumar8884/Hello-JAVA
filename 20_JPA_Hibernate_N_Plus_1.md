# 20 — JPA / Hibernate N+1 problem

> **Interview question:** *"What is the N+1 query problem in JPA/Hibernate? How do you detect it? How do you fix it? Difference between `FetchType.LAZY` and `EAGER`? What is `JOIN FETCH` vs `@EntityGraph`?"*

## 30-second answer

**N+1** = fetching a **parent** with N children makes 1 query for the parent + **N** queries for the children (one per row) instead of 1 combined query.

**Cause:** LAZY associations touched inside a loop.

**Fixes:**
- `JOIN FETCH` in JPQL.
- `@EntityGraph`.
- `@BatchSize` (Hibernate) to batch the extra queries.
- Second-level cache for read-heavy data.
- DTO projections (skip entity load entirely).

---

## The bug in code

```java
@Entity
class Author {
    @Id Long id;
    String name;
    @OneToMany(mappedBy = "author", fetch = FetchType.LAZY)
    List<Book> books;
}

// caller
List<Author> authors = authorRepo.findAll();            // 1 query
for (Author a : authors) {
    log.info("{} wrote {}", a.getName(), a.getBooks().size());  // N queries!
}
```

Log output:
```
select * from author
select * from book where author_id = 1
select * from book where author_id = 2
...
select * from book where author_id = N
```

---

## Fix 1 — `JOIN FETCH`

```java
@Query("select a from Author a join fetch a.books")
List<Author> findAllWithBooks();
```
- One SQL join → one round-trip.
- ⚠ Can't `join fetch` with pagination (`setFirstResult` / `setMaxResults`) → Hibernate warns and loads all in memory.
- Only one `join fetch` for a collection per query (else Cartesian product) — use `distinct` or two queries.

---

## Fix 2 — `@EntityGraph`

```java
@EntityGraph(attributePaths = { "books" })
List<Author> findAll();
```
- Declarative. Same effect as `JOIN FETCH` but composable and works with derived queries.
- Preferred in Spring Data JPA.

---

## Fix 3 — `@BatchSize` (Hibernate-specific)

```java
@Entity
class Author {
    @OneToMany(mappedBy = "author")
    @BatchSize(size = 20)
    List<Book> books;
}
```
- Instead of N queries, Hibernate batches: `where author_id in (?, ?, ..., ?)`.
- Not a single query but reduces round-trips: N/20 queries.
- Doesn't require query changes.

---

## Fix 4 — DTO projection

Ideal when you don't need entities:
```java
public interface AuthorSummary {
    Long getId();
    String getName();
    long getBookCount();
}

@Query("select a.id as id, a.name as name, count(b.id) as bookCount " +
       "from Author a left join a.books b group by a.id, a.name")
List<AuthorSummary> summary();
```

Or JPQL constructor projection:
```java
@Query("select new com.x.dto.AuthorDto(a.id, a.name, size(a.books)) from Author a")
List<AuthorDto> summary();
```

Fastest, most memory-efficient — but loses lazy nav ability. Use for **queries**, not **write paths**.

---

## `FetchType.LAZY` vs `EAGER`

| | LAZY (default for collections) | EAGER (default for single-value like `@ManyToOne`) |
|---|---|---|
| When loaded | On first access | Along with the parent |
| Perf | ✅ Good — pay only when used | ❌ Loads even when not needed; N+1 waiting to happen |
| Session required | ✅ (LazyInitializationException outside) | ❌ |

⭐ **Best practice:** make **everything LAZY**, then use `JOIN FETCH` / `EntityGraph` to eagerly fetch only what a specific use case needs. Never default to EAGER.

---

## LazyInitializationException

```
org.hibernate.LazyInitializationException: could not initialize proxy - no Session
```
- Thrown when you access a LAZY association **outside** the persistence context (e.g., after the transaction closed in a controller).
- Fixes:
  - Fetch it eagerly for the request (`JOIN FETCH` / `@EntityGraph`).
  - Use a DTO projection.
  - Open-Session-In-View (⚠ anti-pattern — hides the problem, hurts perf).

---

## Detecting N+1 in dev

- Enable SQL logging:
  ```yaml
  logging.level.org.hibernate.SQL: DEBUG
  spring.jpa.properties.hibernate.format_sql: true
  ```
- Watch for repeated identical queries per request.
- Tools: **p6spy**, **datasource-proxy**, **Hibernate Statistics** (`hibernate.generate_statistics=true` → `getStatistics().getQueryExecutionCount()` in tests).
- **Recommended:** in tests, use `hibernate-statistics` + JUnit assertion or the `spring-hibernate-query-utils` library that fails a test on unexpected N+1.

---

## Key points to remember ⭐

- ⭐ N+1 = 1 parent query + N child queries. Look at SQL logs to spot.
- ⭐ Default **collection** associations are LAZY; default **single-value** (`@ManyToOne`, `@OneToOne`) are **EAGER** — override to LAZY.
- ⭐ `JOIN FETCH` = imperative, in the JPQL. `@EntityGraph` = declarative, on the method.
- ⭐ You can only `JOIN FETCH` **one collection** per query without Cartesian explosion — split into two queries or use `Set`s (`distinct`).
- ⭐ `@BatchSize` mitigates N+1 without rewriting queries.
- ⭐ For read models, prefer **DTO projections** — no entity graph traversal.
- ⭐ `Open-Session-In-View` (Boot's default `spring.jpa.open-in-view=true`) hides N+1 until prod — turn it off and fix properly.

---

## Common follow-up questions

**Q: Why is `@ManyToOne` EAGER by default?**
A: JPA spec chose it. Almost always wrong for perf. Override to LAZY explicitly.

**Q: Can I fetch two collections in one query?**
A: Not without a Cartesian product between them. Use two `EntityGraph`s or split into two queries.

**Q: What is the second-level cache?**
A: A shared cache across sessions (EHCache, Caffeine, Redis). Reduces DB round-trips for read-mostly data. Requires `@Cacheable` on the entity and cache config. Cache invalidation is the hard part.

**Q: Difference between `getReference()` and `findById()`?**
A: `getReference` returns a proxy — no SQL until you touch it. Good for setting an FK without loading the target. `findById` executes SQL and returns the loaded entity.

**Q: How does Hibernate flush work?**
A: On tx commit / query execution / explicit `flush()`. Uses **dirty checking** on managed entities to emit `UPDATE`s.

**Q: How do you paginate an EAGER join?**
A: Two-query trick: (1) `SELECT id FROM t ORDER BY ... LIMIT/OFFSET`; (2) `SELECT ... JOIN FETCH ... WHERE id IN (?)`. Or use `Slice` + entity graph, but Hibernate may load all rows in memory.

**Q: What is `MultipleBagFetchException`?**
A: Fetching more than one `List` collection in one JPQL. Use `Set<>` for the associations or split queries.

---

## Gotchas / traps

- Default `@ManyToOne` EAGER → transitive N+1 waiting to happen.
- `join fetch` + pagination → Hibernate warns and pulls everything into memory.
- Fetching a `List<Order>` where each order has `List<Item>` → nested N+1.
- Serializing an entity to JSON (Jackson) triggers lazy loads inside the controller — silent N+1 in prod. Return DTOs.
- Turning off `open-in-view` and then hitting LazyInitializationException in serialization — the fix isn't OSIV, it's returning DTOs.
- Second-level cache stale data — invalidate on writes explicitly.
- `List` vs `Set` for `@OneToMany` — using a `List` requires an order column or you get duplicates on Cartesian join fetches.
