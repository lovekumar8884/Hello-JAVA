# 17 — @Transactional — Propagation, Isolation and AOP

> **Interview question:** *"How does `@Transactional` work internally? What are propagation and isolation levels? Why doesn't `@Transactional` work on a private method / self-invocation? When does the transaction roll back?"*

## 30-second answer

- `@Transactional` = AOP proxy wraps the bean method. On entry it starts (or joins) a transaction; on exit it commits or rolls back.
- **Rollback defaults:** rolls back on **unchecked** exceptions (`RuntimeException`, `Error`). Does NOT roll back on **checked** exceptions unless you specify `rollbackFor`.
- **Propagation** = how a method behaves when a transaction already exists. Default = `REQUIRED`.
- **Isolation** = DB-level; controls what concurrent transactions can see.
- **Doesn't work on:** private / final / static methods, and self-invocation (`this.x()` bypasses the proxy).

---

## How it works — proxies

- Spring creates a **JDK dynamic proxy** (if the class implements an interface) or **CGLIB subclass** proxy.
- The proxy intercepts calls, delegates to `TransactionInterceptor`, which uses a `PlatformTransactionManager` to `begin` / `commit` / `rollback`.
- Only calls **through the proxy** are intercepted — direct `this` calls skip it.

```
caller → proxy → TransactionInterceptor.begin → real method → commit/rollback
```

---

## Propagation levels

| Value | Behavior when a tx exists | If no tx exists |
|---|---|---|
| `REQUIRED` (default) | Join it | Create new |
| `REQUIRES_NEW` | Suspend outer, start a new independent tx | Create new |
| `NESTED` | Savepoint inside the outer tx | Create new |
| `SUPPORTS` | Join it | Execute non-transactionally |
| `NOT_SUPPORTED` | Suspend the tx, run non-transactionally | Run non-transactionally |
| `MANDATORY` | Join it | **Throw** |
| `NEVER` | **Throw** | Non-transactional |

⭐ **`REQUIRES_NEW`** is the key one — used to persist an audit record even if the outer transaction rolls back:
```java
@Transactional(propagation = REQUIRES_NEW)
void writeAudit(...) { ... }
```

⭐ **`NESTED`** uses JDBC savepoints; the inner rollback undoes only inner work but the outer tx can still commit. Not all DBs support it.

---

## Isolation levels (DB-level)

| Level | Dirty read | Non-repeatable read | Phantom read |
|---|---|---|---|
| `READ_UNCOMMITTED` | ✅ possible | ✅ | ✅ |
| `READ_COMMITTED` (default in PG, Oracle) | ❌ | ✅ | ✅ |
| `REPEATABLE_READ` (default in MySQL InnoDB) | ❌ | ❌ | ✅ (but MySQL blocks phantoms via gap locks) |
| `SERIALIZABLE` | ❌ | ❌ | ❌ |

- **Dirty read** — read a value another tx wrote but hasn't committed.
- **Non-repeatable read** — read the same row twice, see different values (another tx committed a change between).
- **Phantom read** — same query twice returns different **row sets** (another tx inserted matching rows).

⭐ For most CRUD apps, DB default (`READ_COMMITTED` on PG) is fine. Bump to `REPEATABLE_READ` for read-your-writes semantics inside a business transaction.

---

## Rollback rules

```java
// Rolls back on unchecked only (default)
@Transactional
void a() throws IOException { throw new IOException(); }   // ⚠ commits!

// Force rollback on checked exception
@Transactional(rollbackFor = IOException.class)
void b() throws IOException { throw new IOException(); }   // rolls back

// Never roll back on this exception (e.g., "expected" not-found)
@Transactional(noRollbackFor = ResourceNotFoundException.class)
void c() { ... }
```

Manual rollback:
```java
TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
```

---

## Read-only

```java
@Transactional(readOnly = true)
public User find(long id) { ... }
```

- Hint to Hibernate to skip dirty checks and flush.
- Hint to the DB / JDBC driver to potentially route to a read replica.
- Not a security guarantee — you can still call save; Hibernate just won't flush.

---

## Timeouts

```java
@Transactional(timeout = 5)   // seconds
```

Enforced by the transaction manager — if the tx exceeds it, next SQL fails.

---

## The self-invocation trap ⭐

```java
@Service
class OrderService {
    public void a() { b(); }              // ⚠ direct call — proxy not involved
    @Transactional public void b() { ... } // no tx starts!
}
```

**Fixes:**
- Move `b()` to another Spring bean and inject it.
- Inject self via `ApplicationContext` or `@Lazy` self-injection (Spring 4.3+):
  ```java
  @Autowired @Lazy OrderService self;
  public void a() { self.b(); }
  ```
- Use `AopContext.currentProxy()` (needs `expose-proxy=true`).

Similarly `@Transactional` won't work on:
- `private` or `protected` methods (with `interfaces` proxy);
- `static` methods;
- `final` methods (CGLIB can't override them);
- calls from constructors / init blocks.

---

## Key points to remember ⭐

- ⭐ Default rollback = **RuntimeException / Error only**. Checked exceptions do NOT rollback unless you say `rollbackFor`.
- ⭐ `@Transactional` requires a **proxy call** — no self-invocation, no private/final/static.
- ⭐ Default propagation `REQUIRED` joins the existing tx. Use `REQUIRES_NEW` for independent audits / logs.
- ⭐ Default isolation = DB default (usually `READ_COMMITTED`).
- ⭐ `@Transactional(readOnly=true)` on queries — perf hint (skip dirty check) and enables replica routing.
- ⭐ **Long transactions are evil** — hold locks, bloat undo, cause deadlocks. Do external I/O (HTTP calls, emails) **outside** the tx boundary.
- ⭐ For batching many writes, tune Hibernate `hibernate.jdbc.batch_size` — otherwise each save is a round-trip.
- ⭐ `Isolation` and `propagation` on nested `@Transactional` methods — inner values are **ignored** if propagation is `REQUIRED` (joins existing tx). Only `REQUIRES_NEW` gets a fresh iso level.

---

## Common follow-up questions

**Q: Why doesn't `@Transactional` roll back on checked exceptions by default?**
A: Historical — following EJB conventions. RuntimeException = programmer error / unrecoverable; checked = "expected" business exception which the caller might handle. Override with `rollbackFor`.

**Q: What's the difference between `@Transactional` on class vs method?**
A: Class-level applies to every public method. Method-level overrides class-level.

**Q: How does `@Transactional` interact with `@Async`?**
A: `@Async` runs on a different thread. Transactions in Spring are **thread-bound** (via `ThreadLocal`), so the async method starts a NEW transaction — the outer one does not propagate.

**Q: What is `TransactionSynchronizationManager`?**
A: The ThreadLocal holder for the current transaction. Lets you register `afterCommit` / `afterCompletion` callbacks (great for firing events only if the tx commits).

**Q: How to fire an event only if the tx commits?**
A: `TransactionalEventListener(phase = AFTER_COMMIT)` on the listener, or manual `TransactionSynchronization.afterCommit`.

**Q: What is optimistic vs pessimistic locking?**
A: Optimistic — `@Version` column; on commit, check version and fail if mismatched. Pessimistic — `SELECT ... FOR UPDATE` locks the row. Prefer optimistic for low-contention; pessimistic for hot rows (inventory).

**Q: Can you have `@Transactional` on a `@Async` method?**
A: Yes — the transaction lives on the async thread. Just remember: the caller doesn't share it.

---

## Gotchas / traps

- Self-invocation without proxy — no tx.
- Private/final method with `@Transactional` — no tx (proxy can't intercept).
- Throwing checked exception, expecting rollback — doesn't happen unless `rollbackFor`.
- Catching an exception inside a `@Transactional` method — Spring sees a normal return → commits.
- Making an HTTP call inside a tx → holds DB locks for network duration.
- Injecting a prototype-scoped repository / dao — likely a bug.
- Nested `@Transactional` where you expected rollback of only inner — that needs `REQUIRES_NEW` or `NESTED`.
- `@Transactional` on a Kafka listener + calling `send()` — send happens in whatever offset commit semantics you configured; align tx boundaries with the messaging semantics.
