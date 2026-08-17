# 09 — synchronized vs volatile vs Lock (ReentrantLock, ReadWriteLock)

> **Interview question:** *"What's the difference between `synchronized`, `volatile`, and `ReentrantLock`? When do you use which? What is the happens-before relationship?"*

## 30-second answer

| | `volatile` | `synchronized` | `ReentrantLock` |
|---|---|---|---|
| Guarantees **atomicity**? | ❌ (except assign of primitive) | ✅ | ✅ |
| Guarantees **visibility**? | ✅ | ✅ | ✅ |
| Blocking? | ❌ non-blocking | ✅ | ✅ |
| Fairness option? | — | ❌ | ✅ (`new ReentrantLock(true)`) |
| Try-lock / timed lock? | — | ❌ | ✅ (`tryLock`, `tryLock(timeout)`) |
| Interruptible acquire? | — | ❌ | ✅ (`lockInterruptibly`) |
| Multiple condition vars? | — | 1 (implicit) | ✅ many via `newCondition()` |

**Rule:**
- Need only **visibility** of a single variable → `volatile`.
- Need **mutual exclusion** of a small block → `synchronized`.
- Need **advanced features** (tryLock, fairness, multiple conditions, timed acquire) → `ReentrantLock`.

---

## What is the Java Memory Model (JMM)?

Without synchronization, threads may observe **stale values** of fields due to CPU caches and compiler reorderings. The JMM defines the **happens-before** relationship: an action A happens-before B if all effects of A are visible to B.

**Happens-before edges you must know:**
- **Program order** inside a single thread.
- Unlock happens-before the next lock of the same monitor.
- Write to `volatile` field happens-before every subsequent read.
- `Thread.start()` happens-before any action in the started thread.
- All actions in a thread happen-before another thread's successful `join()` on it.

---

## `volatile`

- **Visibility only.** A write to a volatile field is immediately visible to reads on other threads.
- Prevents the compiler / CPU from reordering across the read/write.
- Does NOT provide atomicity for compound actions like `count++` (that's read + inc + write).

```java
private volatile boolean shutdown = false;   // safe stop-flag

void producer() { shutdown = true; }
void consumer() { while (!shutdown) doWork(); }   // guaranteed to see the update
```

Atomic compound updates → use `AtomicInteger`, `LongAdder`, or `synchronized`.

---

## `synchronized`

- Implicit monitor lock. Two forms:
  ```java
  synchronized (obj) { ... }             // block form — pick the monitor object
  public synchronized void m() { ... }   // method form — monitor = `this`
  public static synchronized void s() {} // method form — monitor = Class object
  ```
- **Reentrant** — the same thread can enter the same monitor multiple times.
- No fairness (any waiter can win the lock next).
- Not interruptible — a thread blocked on `synchronized` cannot be woken by `interrupt`.
- Since Java 6+, JIT can **bias-lock**, **thin-lock**, and only inflate to full OS mutex under contention → fast in the uncontended case.

---

## `ReentrantLock`

```java
private final ReentrantLock lock = new ReentrantLock();
private final Condition notEmpty = lock.newCondition();

void put(E e) throws InterruptedException {
    lock.lockInterruptibly();
    try {
        while (full) notEmpty.await();
        add(e);
        notEmpty.signalAll();
    } finally {
        lock.unlock();   // ⭐ ALWAYS in finally
    }
}
```

Advantages over `synchronized`:
- `tryLock()` — non-blocking attempt.
- `tryLock(timeout, unit)` — timed acquire.
- `lockInterruptibly()` — respects `interrupt`.
- `Condition` — multiple wait sets on the same lock (e.g., `notFull` and `notEmpty` on a buffer).
- Optional **fairness** — FIFO on the wait queue.

Disadvantages:
- More code — you must `unlock()` in a `finally` block.
- No JIT lock elision / bias-lock optimizations.

---

## `ReadWriteLock` / `StampedLock`

- **`ReentrantReadWriteLock`** — many concurrent readers or one writer. Great for read-heavy, write-rare data.
- **`StampedLock`** (Java 8) — supports **optimistic reads** (no locking, then validate). Best throughput but no reentrancy and no `Condition`.

```java
ReentrantReadWriteLock rw = new ReentrantReadWriteLock();
Lock r = rw.readLock();
Lock w = rw.writeLock();
```

---

## `AtomicInteger` / `LongAdder`

Lock-free updates using CAS (compare-and-swap):
```java
AtomicInteger counter = new AtomicInteger();
counter.incrementAndGet();
counter.updateAndGet(x -> x * 2);
```
- Under **very high contention**, prefer **`LongAdder` / `LongAccumulator`** — they stripe the counter across cells → less CAS contention.

---

## Key points to remember ⭐

- ⭐ `volatile` gives **visibility**, not atomicity. `count++` on a volatile is still broken.
- ⭐ `synchronized` gives **atomicity + visibility**.
- ⭐ Prefer `synchronized` for simple mutual exclusion — the JIT makes it very cheap and the code is shorter.
- ⭐ Reach for `ReentrantLock` when you need `tryLock`, timeouts, interruption, or multiple `Condition`s.
- ⭐ **Always release locks in a `finally` block.**
- ⭐ Prefer **higher-level concurrency utilities** (`ConcurrentHashMap`, `BlockingQueue`, `CountDownLatch`, `Semaphore`) over rolling your own locking.
- ⭐ Under high contention, `LongAdder` >> `AtomicLong`.
- ⭐ `synchronized(String literal)` or `synchronized(Integer)` is a bug — those may be shared / cached and you'll deadlock with unrelated code.

---

## Common follow-up questions

**Q: Is `HashMap.get()` safe if only one thread writes and others read?**
A: No — even reads can see partially-constructed state / corrupted linked lists. Use `ConcurrentHashMap` or `volatile` reference switch.

**Q: What is a `Condition`?**
A: Analogous to `Object.wait/notify` but associated with a `Lock` — one lock can have many conditions, so waiters can be grouped by what they're waiting for.

**Q: How is `ReentrantLock` implemented?**
A: Built on `AbstractQueuedSynchronizer` (AQS) — a FIFO queue of waiting threads plus an atomic `state` int for the lock count.

**Q: What is a happens-before relationship?**
A: A partial ordering the JMM enforces so that writes on one thread become visible to reads on another. Without a happens-before edge, no visibility guarantee.

**Q: What is double-checked locking? Is it broken?**
A: The pattern:
```java
if (instance == null) {
    synchronized (Singleton.class) {
        if (instance == null) instance = new Singleton();
    }
}
```
Broken **before Java 5** because `instance` could be published half-constructed. Since Java 5, if `instance` is **`volatile`**, it works correctly.

**Q: What is a `ReentrantReadWriteLock` bad at?**
A: Writer starvation under continuous readers (unless fair mode). Also higher overhead than a plain lock.

**Q: `synchronized(this)` vs `synchronized(privateFinalLock)`?**
A: Prefer a **private final `Object lock = new Object();`** — prevents outside code from accidentally acquiring your lock.

---

## Gotchas / traps

- `count++` on `volatile int` — broken.
- `synchronized(String literal)` — shared with unrelated code.
- Forgetting `lock.unlock()` in `finally` → permanent deadlock.
- Nested locks acquired in different orders → **deadlock**.
- Using `notify()` instead of `notifyAll()` when multiple threads wait on different conditions.
- `wait()` in `if` instead of `while` — spurious wakeups + missed signals.
- Reading a large `long`/`double` non-volatile field without sync — the read is not atomic on 32-bit JVMs.
