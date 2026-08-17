# 10 — ExecutorService and ThreadPool internals

> **Interview question:** *"How does `ThreadPoolExecutor` work? What are its parameters (core, max, queue, keep-alive, rejection policy)? Which `Executors.*` factory would you use in production?"*

## 30-second answer

`ThreadPoolExecutor` = a pool of worker threads + a queue of pending tasks + rules.

**The 7 constructor parameters:**
1. `corePoolSize` — always-alive workers.
2. `maximumPoolSize` — upper bound on workers.
3. `keepAliveTime` — how long **extra** workers (above core) idle before dying.
4. `TimeUnit` for #3.
5. `BlockingQueue<Runnable>` — task queue.
6. `ThreadFactory` — customize thread name / daemon / priority.
7. `RejectedExecutionHandler` — what to do when queue is full and pool is at max.

**Task flow:**
```
submit(task) →
  ┌ if runningWorkers < corePoolSize → start a new core worker
  ├ else if queue.offer(task)         → queued
  ├ else if runningWorkers < maxPoolSize → start a new non-core worker
  └ else                              → RejectedExecutionHandler kicks in
```

⭐ Note the counter-intuitive order: **the queue is preferred over creating up to max**. If the queue is *unbounded*, `maximumPoolSize` is never reached.

---

## The 4 built-in RejectionPolicies

| Policy | Behavior |
|---|---|
| `AbortPolicy` (default) | Throws `RejectedExecutionException` |
| `CallerRunsPolicy` | Executes the task on the **submitting thread** — natural back-pressure |
| `DiscardPolicy` | Silently drops the new task |
| `DiscardOldestPolicy` | Removes the head of the queue and retries |

⭐ `CallerRunsPolicy` is a poor-man's back-pressure: slow producers automatically.

---

## `Executors.*` factories — why prod code avoids most of them

| Factory | Under the hood | Prod risk |
|---|---|---|
| `newFixedThreadPool(n)` | core = max = n, **unbounded LinkedBlockingQueue** | 💥 Queue can OOM under overload |
| `newCachedThreadPool()` | core=0, **max=Integer.MAX_VALUE**, SynchronousQueue | 💥 Can spawn unlimited threads → OOM |
| `newSingleThreadExecutor()` | 1 worker, unbounded queue | Same OOM risk as fixed |
| `newScheduledThreadPool(n)` | For delayed / periodic tasks | Uses DelayedWorkQueue |
| `newWorkStealingPool()` | ForkJoinPool, parallelism = CPUs | Task ordering / fairness not guaranteed |
| `newVirtualThreadPerTaskExecutor()` (Java 21) | Each task on a virtual thread | Great for I/O-bound workloads |

**Prod recommendation:** construct `ThreadPoolExecutor` directly with a **bounded queue** and a **`CallerRunsPolicy`** (or explicit back-pressure).

```java
ExecutorService pool = new ThreadPoolExecutor(
    8, 16, 60L, TimeUnit.SECONDS,
    new ArrayBlockingQueue<>(1000),
    new ThreadFactoryBuilder().setNameFormat("worker-%d").build(),
    new ThreadPoolExecutor.CallerRunsPolicy()
);
```

---

## Submitting work

```java
pool.execute(runnable);                  // fire-and-forget
Future<Integer> f = pool.submit(callable);  // get result later
List<Future<T>> all = pool.invokeAll(tasks);  // wait for ALL
T first = pool.invokeAny(tasks);            // wait for FIRST success
```

⭐ `execute` swallows unchecked exceptions to the `UncaughtExceptionHandler` of the worker. `submit` **wraps** the exception in the `Future` — `future.get()` will throw `ExecutionException`. Beware: `pool.submit(runnable)` swallows exceptions if you never call `get()`.

---

## Shutdown sequence

```java
pool.shutdown();                       // no new tasks, existing finish
if (!pool.awaitTermination(30, TimeUnit.SECONDS)) {
    pool.shutdownNow();                // interrupt running tasks
    pool.awaitTermination(10, TimeUnit.SECONDS);
}
```

- `shutdown()` — graceful; queued tasks continue.
- `shutdownNow()` — attempts to cancel; interrupts workers; returns tasks that never started.
- Always integrate with your app lifecycle (Spring `@PreDestroy`, k8s SIGTERM handler).

---

## Sizing rules of thumb

- **CPU-bound:** `poolSize ≈ numCpuCores` (or `+1` to hide occasional stalls).
- **I/O-bound:** `poolSize ≈ numCpuCores × (1 + waitTime/computeTime)`. Often 2–4× cores.
- Java 21+ **virtual threads** — for I/O-bound work you can use hundreds of thousands cheaply.

---

## Key points to remember ⭐

- ⭐ Task placement order: **core → queue → max → reject** (not core → max → queue!).
- ⭐ **Unbounded queues make `maxPoolSize` unreachable.** `newFixedThreadPool` uses one → prod OOM risk.
- ⭐ Prefer **bounded queue + CallerRunsPolicy** for back-pressure.
- ⭐ `submit(runnable)` **hides exceptions** until `future.get()` is called. Use `execute()` when you don't want a Future.
- ⭐ Always give a `ThreadFactory` with **named threads** — makes thread dumps readable.
- ⭐ Always `shutdown()` on application stop; use `awaitTermination` + `shutdownNow` fallback.
- ⭐ `ThreadPoolExecutor` core threads are **lazy-started** by default; call `prestartAllCoreThreads()` to warm up.
- ⭐ For **CPU parallel work with subtasks**, prefer **`ForkJoinPool` / parallel streams** — they use work-stealing.

---

## Common follow-up questions

**Q: Difference between `execute()` and `submit()`?**
A: `execute()` takes `Runnable`, returns void. `submit()` takes `Runnable` or `Callable`, returns a `Future` and captures exceptions inside it.

**Q: What is `ScheduledExecutorService`?**
A: Runs tasks with a delay or at a fixed rate: `schedule`, `scheduleAtFixedRate`, `scheduleWithFixedDelay`. Replaces `java.util.Timer` (which dies on any exception).

**Q: `scheduleAtFixedRate` vs `scheduleWithFixedDelay`?**
A: *fixed rate* = interval measured from the **start** of the previous run (can pile up if slow). *fixed delay* = interval measured from the **end** of the previous run.

**Q: What is a `ForkJoinPool` / work-stealing?**
A: Pool where idle workers "steal" tasks from the tails of busy workers' deques → good load balancing for divide-and-conquer tasks. Backs `parallelStream()` and `CompletableFuture.*Async` without explicit executor.

**Q: What is a virtual thread (Java 21)?**
A: JVM-managed lightweight thread — millions can exist. On blocking I/O the virtual thread is unmounted from a carrier thread → carrier is free to run others. Great for I/O-bound web servers.

**Q: How do you handle exceptions from a worker task?**
A: For `execute()`, set a `Thread.UncaughtExceptionHandler` on the `ThreadFactory`. For `submit()`, call `future.get()` and handle `ExecutionException`.

**Q: What's `SynchronousQueue`?**
A: A queue with zero capacity — every `put()` must wait for a matching `take()` (hand-off). Used by `newCachedThreadPool` to force new-thread creation instead of queuing.

---

## Gotchas / traps

- Using `Executors.newFixedThreadPool` in prod → unbounded queue OOM.
- Using `newCachedThreadPool` under sustained load → unbounded threads OOM.
- Not shutting down the pool → JVM won't exit (non-daemon threads).
- `submit(runnable)` and forgetting `future.get()` → silent exception loss.
- Sharing a pool for CPU-bound and blocking I/O tasks → blocking work steals all workers, CPU work starves. Use **separate pools**.
- Long-running tasks in the common `ForkJoinPool` (via parallel stream) — they block others; use a custom `ForkJoinPool`.
- Configuring `CallerRunsPolicy` but the caller is your HTTP thread → back-pressure ends up throttling your web tier (usually what you want, but understand it).
