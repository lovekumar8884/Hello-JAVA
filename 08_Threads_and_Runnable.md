# 08 — How do you create a Thread? Thread Lifecycle

> **Interview question:** *"How do you create a thread in Java? What are the states in the thread lifecycle? Difference between Runnable and Callable? Difference between `start()` and `run()`?"*

## 30-second answer

**Four ways to run code on a thread:**
1. Extend `Thread` and override `run()`. (Rarely used — you use up your one inheritance slot.)
2. Implement `Runnable` → pass to `new Thread(runnable).start()`.
3. Implement `Callable<T>` → submit to an `ExecutorService`, get a `Future<T>`.
4. `CompletableFuture.supplyAsync(...)` or `ExecutorService.submit(...)` — the modern way.

**Prefer #3 / #4** in real code. Almost never manage raw `Thread` objects yourself.

---

## Runnable vs Callable

| | `Runnable` | `Callable<V>` |
|---|---|---|
| Method | `void run()` | `V call() throws Exception` |
| Returns a value? | ❌ | ✅ |
| Throws checked exception? | ❌ | ✅ |
| Since | 1.0 | 1.5 |
| Used with | `Thread` / `Executor.execute` | `ExecutorService.submit → Future` |

```java
ExecutorService pool = Executors.newFixedThreadPool(4);
Future<Integer> f = pool.submit(() -> heavyCompute());   // Callable<Integer>
int result = f.get();                                    // blocks
```

---

## Thread lifecycle (6 states in `Thread.State`)

```
NEW ──start()──► RUNNABLE ──scheduler──► (running on CPU)
                    │
                    │ synchronized / Lock.lock()
                    ▼
                 BLOCKED
                    │
                    │ Object.wait() / Thread.sleep(ms) / join(ms)
                    ▼
                 WAITING / TIMED_WAITING
                    │
                    │ notify() / notifyAll() / interrupt / timeout
                    ▼
                 RUNNABLE
                    │
                    │ run() returns / uncaught exception
                    ▼
                TERMINATED
```

| State | Meaning |
|---|---|
| `NEW` | Thread object created, `start()` not yet called |
| `RUNNABLE` | Eligible to run — may be running or waiting for CPU |
| `BLOCKED` | Waiting to acquire a monitor lock (entering `synchronized`) |
| `WAITING` | Waiting indefinitely — `wait()`, `join()`, `LockSupport.park()` |
| `TIMED_WAITING` | Waiting with a timeout — `sleep(ms)`, `wait(ms)`, `join(ms)` |
| `TERMINATED` | `run()` completed (normally or via exception) |

---

## `start()` vs `run()`

- `start()` — creates a new OS thread and invokes `run()` in it. **Non-blocking.**
- `run()` — just a normal method call in the current thread. NO new thread is created.

```java
new Thread(task).start();  // ✅ runs on new thread
new Thread(task).run();    // ❌ runs on current thread — like calling task.run() directly
```

⭐ Calling `start()` twice → `IllegalThreadStateException`. Threads are one-shot.

---

## Daemon threads

```java
Thread t = new Thread(task);
t.setDaemon(true);
t.start();
```
- Daemon threads **do not block JVM shutdown**. When all non-daemon threads exit, the JVM exits and daemons are killed abruptly (finally may not run).
- Use for background housekeeping (e.g., cache eviction, metrics flush).

---

## `wait()` / `notify()` / `notifyAll()` — must be called inside `synchronized`

```java
synchronized (lock) {
    while (!condition) lock.wait();   // ALWAYS in while, not if — spurious wakeups!
    // consume
}

synchronized (lock) {
    condition = true;
    lock.notifyAll();
}
```
- `wait()` releases the monitor and blocks. `notify()` wakes one waiter; `notifyAll()` wakes all.
- Prefer higher-level tools: `ReentrantLock` + `Condition`, `BlockingQueue`, `Semaphore`, `CountDownLatch`.

---

## Interruption — the cooperative cancel signal

```java
Thread.currentThread().interrupt();      // sets the flag
Thread.interrupted();                    // reads and clears
Thread.currentThread().isInterrupted();  // reads without clearing

// If you catch InterruptedException, restore the flag or rethrow
try { sleep(1000); }
catch (InterruptedException e) {
    Thread.currentThread().interrupt();  // ⭐ restore
    return;
}
```

⭐ Never swallow `InterruptedException` silently.

---

## Key points to remember ⭐

- ⭐ `start()` creates a new thread; `run()` doesn't.
- ⭐ Prefer `Callable` + `ExecutorService` over raw `Thread`. Manage lifecycles centrally.
- ⭐ 6 `Thread.State` values: NEW, RUNNABLE, BLOCKED, WAITING, TIMED_WAITING, TERMINATED. (There's **no** "RUNNING" separate state in Java — RUNNABLE covers both ready-to-run and running.)
- ⭐ Always `wait()` inside a `while` loop, guarded by the same monitor.
- ⭐ Threads are one-shot — you can't restart a `TERMINATED` thread.
- ⭐ `Thread.sleep(ms)` keeps the lock; `wait(ms)` releases it.
- ⭐ Daemon vs user threads — JVM only waits for user threads.

---

## Common follow-up questions

**Q: `sleep()` vs `wait()`?**
A: `sleep` is on `Thread`, holds all locks, ignores notify. `wait` is on `Object`, must be inside `synchronized` on that object, releases the monitor, wakes on `notify` or timeout.

**Q: `yield()` — what does it do?**
A: Hints the scheduler that the current thread is willing to give up its slot. Not portable, not reliable, rarely useful.

**Q: How do you gracefully stop a thread?**
A: Use interruption + a volatile `running` flag. `Thread.stop()` is deprecated (unsafe — can leave locks in inconsistent state).

**Q: Can we override `start()`?**
A: Technically yes, but you must call `super.start()` — otherwise no new thread is created.

**Q: How many threads can a JVM have?**
A: Limited by OS process limits and thread stack size (`-Xss`, default ~512 KB on Linux). Typical: thousands. Prefer thread pools to avoid unbounded creation.

**Q: What is a `ThreadLocal`?**
A: Per-thread variable — `ThreadLocal<SimpleDateFormat> fmt = ThreadLocal.withInitial(SimpleDateFormat::new)`. Each thread gets its own value. **Memory leak trap** in thread pools — always `remove()` in a `finally`.

**Q: Runnable vs Thread — which is preferred?**
A: `Runnable` — decouples the task from the thread; works with executors; leaves your inheritance slot free.

---

## Gotchas / traps

- Calling `run()` instead of `start()`.
- Not restoring the interrupt flag after `InterruptedException`.
- Using `Thread.stop()` / `suspend()` / `resume()` — all deprecated, unsafe.
- Using `notify()` when multiple waiters exist and they wait on different conditions → wrong thread woken.
- `wait()` in `if` instead of `while` → spurious wakeups cause bugs.
- Creating threads without a pool — unbounded creation → OOM.
- `ThreadLocal` leaks in thread pools — remove in `finally`.
