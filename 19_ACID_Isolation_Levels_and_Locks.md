# 19 — ACID, Isolation Levels & Locking

> **Interview question:** *"What are the ACID properties? Explain the SQL isolation levels and the phenomena they prevent. Difference between optimistic and pessimistic locking?"*

## 30-second answer

**ACID:**
- **A**tomicity — all-or-nothing.
- **C**onsistency — DB moves from one valid state to another (constraints enforced).
- **I**solation — concurrent transactions don't corrupt each other.
- **D**urability — committed data survives crashes.

**Four SQL isolation levels** (in increasing strictness): `READ UNCOMMITTED` → `READ COMMITTED` → `REPEATABLE READ` → `SERIALIZABLE`.

Each blocks more concurrency anomalies but costs more.

---

## The 3 concurrency phenomena

| Phenomenon | Meaning | Example |
|---|---|---|
| **Dirty read** | Read another tx's uncommitted change | T2 sees T1's write before T1 commits |
| **Non-repeatable read** | Same row read twice, values differ | T1 reads row R, T2 updates + commits R, T1 re-reads R |
| **Phantom read** | Same query, different **row set** | T1 counts rows matching filter, T2 inserts matching row + commits, T1 re-runs → higher count |

| Isolation level | Dirty read | Non-repeatable read | Phantom read |
|---|---|---|---|
| `READ UNCOMMITTED` | ✅ | ✅ | ✅ |
| `READ COMMITTED` (PG, Oracle default) | ❌ | ✅ | ✅ |
| `REPEATABLE READ` (MySQL InnoDB default) | ❌ | ❌ | ⚠ (MySQL blocks via gap locks) |
| `SERIALIZABLE` | ❌ | ❌ | ❌ |

⭐ Note: MySQL InnoDB's `REPEATABLE READ` actually blocks phantoms via **gap locks** (unusual but well-known).

---

## How each level is implemented (typical, simplified)

- `READ COMMITTED` — reads see only committed data (snapshot per statement, MVCC in PG).
- `REPEATABLE READ` — snapshot taken at first read; used for the whole tx.
- `SERIALIZABLE` — either strict 2-phase locking, or MVCC + serialization checks (PG's SSI). Slowest but strongest.

---

## Optimistic vs Pessimistic locking

### Optimistic locking (application-level, no DB row locks)
- Add a `version` column.
- On update:
  ```sql
  UPDATE t SET col = ?, version = version + 1 WHERE id = ? AND version = ?
  ```
- If `rowcount = 0` → someone else won → throw `OptimisticLockException` and retry.
- Cheap, no locks; good when contention is **rare**.

JPA:
```java
@Entity
class Order {
    @Id Long id;
    @Version Long version;   // Hibernate handles the check automatically
}
```

### Pessimistic locking (DB-level)
- `SELECT ... FOR UPDATE` locks the row until commit.
- Blocks other writers (and sometimes readers, depending on level).
- Best for **hot rows** with heavy contention (inventory, seat booking).

JPA:
```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
Optional<Order> findById(Long id);
```

⭐ Pessimistic prevents lost updates by serializing access; optimistic prevents them by detecting conflicts at commit time.

---

## Lock types you should recognize

- **Shared (S)** — read lock; many readers allowed.
- **Exclusive (X)** — write lock; blocks everyone else.
- **Intent locks (IS, IX)** — hierarchical hints at table/page level, allowing coarser conflict checks.
- **Gap locks** (MySQL InnoDB) — lock the range **between** index values → prevents phantom inserts.
- **Advisory locks** (PG) — application-defined named locks, unrelated to data.

---

## MVCC in one paragraph (used by PG, Oracle, MySQL, SQL Server snapshot)

Instead of locking rows for reads, each row has **row versions** with commit timestamps. A reader sees the version valid at its snapshot time → readers never block writers, writers never block readers. Costs: garbage collection of old versions (`VACUUM` in PG). SERIALIZABLE + MVCC uses **Serializable Snapshot Isolation** (SSI) → detects conflicts and aborts one tx.

---

## Deadlock in DBs

- Two transactions each hold a lock the other wants → cycle → the DB **detects** and aborts one (usually the tx with less work) with a deadlock error (`SQLState 40001` in PG, `1213` in MySQL).
- App must catch and retry with back-off.
- Reduce via: consistent lock order, shorter transactions, adequate indexes (missing index → row scan → table lock).

---

## Key points to remember ⭐

- ⭐ Memorize the phenomena table (dirty / non-repeatable / phantom vs 4 levels).
- ⭐ PG default = `READ COMMITTED`; MySQL default = `REPEATABLE READ`.
- ⭐ MVCC (Postgres, MySQL) means **readers don't block writers** — most modern DBs.
- ⭐ Optimistic (`@Version`) — cheap; retry on conflict; ideal for low contention.
- ⭐ Pessimistic (`SELECT FOR UPDATE`) — safe; ideal for hot rows.
- ⭐ **Lost update** is possible with `READ COMMITTED` — mitigate with `@Version` (optimistic) or `SELECT FOR UPDATE` (pessimistic).
- ⭐ Serialization anomaly on `SERIALIZABLE` PG → tx aborted with SSI → application must retry.
- ⭐ Long transactions are toxic — they hold locks, prevent VACUUM, cause replica lag.

---

## Common follow-up questions

**Q: What's a lost update?**
A: Two transactions read the same row, both modify it, both commit — the second overwrites the first without seeing it. `READ COMMITTED` allows this. Fix: optimistic or pessimistic locking.

**Q: What's the "write skew" anomaly?**
A: Two transactions read a set of rows, each writes a different row based on that read, both commit — the combined result would have been prevented by SERIALIZABLE. Classic example: two ER doctors going off-shift.

**Q: When would you use `SERIALIZABLE`?**
A: Financial ledgers, seat/ticket reservations — where correctness beats throughput. Handle SerializationFailure retries in the app.

**Q: What's the "2 generals" or CAP theorem relation?**
A: Distributed systems must trade Consistency, Availability, Partition-tolerance. In a partition, choose C (block until healed) or A (respond with maybe-stale data).

**Q: `SELECT FOR UPDATE` vs `SELECT FOR SHARE`?**
A: `FOR UPDATE` — X lock, others can't read (in strict impls) or can't write. `FOR SHARE` (PG) — S lock, others can also SELECT FOR SHARE but not update.

**Q: What's a phantom read again in one sentence?**
A: Same range query returns different rows because another tx inserted or deleted matching rows.

**Q: Why is MySQL's `REPEATABLE READ` unusual?**
A: It blocks phantom inserts via **gap locks** on index ranges, giving near-`SERIALIZABLE` guarantees on some patterns.

**Q: Difference between JPA `@Version` and DB constraint check?**
A: `@Version` is app-side optimistic locking on top of `READ COMMITTED`. DB constraints (UNIQUE, FK) enforce invariants regardless of isolation.

---

## Gotchas / traps

- Assuming `READ COMMITTED` prevents lost updates — it doesn't.
- Doing a `SELECT` in one tx and `UPDATE` in another based on the read value — race → lost update.
- Missing indexes → row locks widen to page/table locks → deadlocks.
- `for update` in a nested loop over many rows → holding N locks → contention explosion.
- Running long-running analytical queries in the same DB as OLTP without a read replica.
- Forgetting to retry on SerializationFailure with `SERIALIZABLE`.
