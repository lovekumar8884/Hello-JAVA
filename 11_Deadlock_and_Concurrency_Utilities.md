# 11 — Deadlock, Livelock, Starvation + Concurrency Utilities

> **Interview question:** *"What is a deadlock? What are the four conditions? How do you detect and prevent it? Difference between deadlock, livelock, and starvation?"*

## 30-second answer

**Deadlock** = ≥2 threads each waiting for a lock the other holds. Nothing progresses, no CPU is used.

**Four Coffman conditions (all must be true):**
1. **Mutual exclusion** — resources not shareable.
2. **Hold and wait** — a thread holds one lock while waiting for another.
3. **No preemption** — locks cannot be forcibly taken.
4. **Circular wait** — cycle in the lock-wait graph.

**Break any ONE condition** → deadlock impossible.

---

## Classic deadlock example

```java
Object A = new Object();
Object B = new Object();

// Thread 1
synchronized (A) {
    synchronized (B) { ... }
}
// Thread 2
synchronized (B) {
    synchronized (A) { ... }
}
```

**Fix:** always acquire in a **fixed global order** (e.g., by `System.identityHashCode`).

```java
Object first  = System.identityHashCode(A) < System.identityHashCode(B) ? A : B;
Object second = (first == A) ? B : A;
synchronized (first) {
    synchronized (second) { ... }
}
```

---

## Deadlock vs Livelock vs Starvation

| | Symptom | Cause | Fix |
|---|---|---|---|
| **Deadlock** | Everyone blocked forever, CPU 0% | Circular lock wait | Fixed lock order; `tryLock` with timeout |
| **Livelock** | Everyone busy but no progress; CPU 100% | Threads keep reacting to each other and retrying (e.g., both back off simultaneously) | Randomized back-off (Ethernet-style) |
| **Starvation** | Some threads make no progress, others do | Unfair scheduling, low priority, or a greedy thread holds a lock too long | Fair locks; break work into smaller units |

---

## Detection

- **Thread dump** (`jstack <pid>` or Ctrl+Break) — JVM identifies deadlocks:
  ```
  Found one Java-level deadlock: ...
  ```
- Programmatically:
  ```java
  ThreadMXBean mx = ManagementFactory.getThreadMXBean();
  long[] deadlocked = mx.findDeadlockedThreads();
  ```
- Monitoring (Prometheus + JMX exporter) can alert on `jvm_threads_deadlocked`.

---

## Prevention strategies ⭐

1. **Global lock ordering.** Assign every lock a rank; always acquire in ascending order.
2. **Timeouts.** `lock.tryLock(1, SECONDS)` — release everything and retry if you can't get all locks.
3. **Lock-free structures.** `ConcurrentHashMap`, `AtomicReference` + CAS.
4. **Reduce lock scope.** Hold locks for the smallest region possible.
5. **Coarse-grained single lock.** Sometimes one big lock is safer than many small ones.
6. **Immutable objects.** No mutation → no locks needed.
7. **Message passing** (actors / queues) → no shared state → no locks.

---

## Essential `java.util.concurrent` utilities

### `BlockingQueue` — bounded producer-consumer
```java
BlockingQueue<Order> q = new ArrayBlockingQueue<>(100);
q.put(order);          // blocks if full
Order o = q.take();    // blocks if empty
```
- `ArrayBlockingQueue` — bounded array
- `LinkedBlockingQueue` — optionally bounded; separate head/tail locks (2× throughput)
- `SynchronousQueue` — capacity 0, hand-off
- `PriorityBlockingQueue` — unbounded, ordered
- `DelayQueue` — elements available after their delay

### `CountDownLatch` — wait until N events happen
```java
CountDownLatch latch = new CountDownLatch(3);
// workers do: latch.countDown();
latch.await();          // main waits
```
⭐ One-shot; can't be reset.

### `CyclicBarrier` — N threads wait for each other, reusable
```java
CyclicBarrier barrier = new CyclicBarrier(4, () -> allArrived());
barrier.await();        // each of the 4 threads
```
Resets automatically → useful for iterative parallel algorithms.

### `Semaphore` — limit concurrent access
```java
Semaphore permits = new Semaphore(10);
permits.acquire();
try { ... } finally { permits.release(); }
```
Use to cap concurrent DB connections, HTTP client parallelism, etc.

### `Phaser` — like CyclicBarrier but supports dynamic party count.

### `Exchanger<V>` — two threads swap objects at a rendezvous.

---

## Key points to remember ⭐

- ⭐ Four Coffman conditions; break any one → no deadlock.
- ⭐ **Fixed lock ordering** is the #1 practical fix.
- ⭐ Use **`tryLock(timeout)`** to detect and back off.
- ⭐ Use `jstack` for post-mortem; `ThreadMXBean.findDeadlockedThreads()` for programmatic detection.
- ⭐ `CountDownLatch` = one-shot. `CyclicBarrier` = reusable.
- ⭐ `BlockingQueue` is the correct pattern for producer-consumer — never roll your own `wait/notify`.
- ⭐ **Livelock** is deadlock's noisy cousin — threads are busy but not progressing.
- ⭐ For read-mostly shared state, `CopyOnWriteArrayList` or `ConcurrentHashMap` avoid locking on the read path.

---

## Common follow-up questions

**Q: How does the JVM detect deadlock?**
A: The `ThreadMXBean` walks the thread graph looking for cycles in "waiting for lock owned by" edges.

**Q: Can `ReentrantLock` deadlock?**
A: Yes — same lock-order problem. But `tryLock` gives you an escape.

**Q: Can a single thread deadlock with itself?**
A: With reentrant locks, no. With non-reentrant locks (like `Semaphore`), yes — a thread `acquire()`s twice.

**Q: What's the difference between `CountDownLatch` and `CyclicBarrier`?**
A: Latch is one-shot; one thread counts down and others wait. Barrier is N-way rendezvous, reusable.

**Q: What is a "Dining philosophers" deadlock?**
A: 5 philosophers share 5 forks; each grabs left then right → circular wait → deadlock. Solutions: ordered acquisition, allow only 4 to eat simultaneously (semaphore), or asymmetric (odd/even).

**Q: How does `CompletableFuture` avoid deadlock?**
A: Callbacks are chained, not locked. But **thenApply on a stuck upstream** still hangs — not a deadlock but a starvation.

---

## Gotchas / traps

- Locks acquired in different orders in different code paths.
- Long-running synchronized methods → other threads pile up in BLOCKED.
- Nested `synchronized` on the same object across public API entry points — hard to reason about the global lock graph.
- Using `synchronized(String literal)` or `synchronized(Integer.valueOf(0))` — cached / shared → deadlock with unrelated code.
- Calling out to unknown code (callbacks, listeners) while holding a lock — they might try to re-enter your lock.
- Ignoring `InterruptedException` in `tryLock(long, unit)` timeouts.
