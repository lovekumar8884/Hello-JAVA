# 06 — JVM Memory Model & Garbage Collection

> **Interview question:** *"Explain JVM memory areas. Where do objects live? What is generational GC? What are the different garbage collectors and when would you pick each?"*

## 30-second answer

**JVM runtime memory areas:**

| Area | Shared? | Stores |
|---|---|---|
| **Heap** | ✅ across threads | All objects, all arrays. GC-managed. Split into Young (Eden + S0 + S1) and Old. |
| **Metaspace** (Java 8+) | ✅ | Class metadata, static fields, constant pool. Native memory (not heap). Replaced PermGen. |
| **JVM Stack** | ❌ per-thread | Method frames — local variables, operand stack, return address. |
| **PC Register** | ❌ per-thread | Address of current bytecode instruction. |
| **Native Method Stack** | ❌ per-thread | JNI native calls. |
| **Code Cache** | ✅ | JIT-compiled machine code. |

**Generational GC hypothesis:** *"Most objects die young."* → Cheap frequent Young GC, rare expensive Old GC.

---

## Heap layout

```
┌─────────────────────────────────────────────────────────┐
│                       Old Generation                    │
│                  (Tenured — long-lived)                 │
├─────────────────────────────────────────────────────────┤
│                    Young Generation                     │
│  ┌────────────┬────────┬────────┐                       │
│  │    Eden    │   S0   │   S1   │                       │
│  └────────────┴────────┴────────┘                       │
│         ~80%     ~10%    ~10%                           │
└─────────────────────────────────────────────────────────┘
```

- New objects go to **Eden**.
- When Eden fills → **Minor GC** (Young GC):
  - Live objects copied to the empty survivor space; Eden + the other survivor cleared.
  - Objects that survive N cycles → promoted to Old.
- When Old fills → **Major GC / Full GC** (slower, longer pause).

---

## Common garbage collectors

| GC | Since | Optimizes for | Pause behavior | When to use |
|---|---|---|---|---|
| **Serial** | 1.3 | Small heap, single core | Stop-the-world | CLI tools, tiny services |
| **Parallel (Throughput)** | 1.5 | **Throughput** | STW but multi-threaded | Batch jobs, ETL |
| **CMS** *(deprecated JDK 9, removed JDK 14)* | 1.5 | Low pause | Mostly concurrent | Legacy |
| **G1 (Garbage-First)** | 7u4, default from 9 | Predictable pause on large heaps | Concurrent + region-based | Default for most services |
| **ZGC** | 11 (production 15) | **Sub-ms pauses**, huge heaps (TB) | Concurrent | Latency-sensitive apps |
| **Shenandoah** | 12 (Red Hat) | Sub-ms pauses | Concurrent | Similar to ZGC |
| **Epsilon** | 11 | No-op GC | Never collects | Perf tests, short-lived jobs |

- **Default (Java 9+):** G1.
- G1 divides heap into ~2048 equal-size **regions** (Eden / Survivor / Old / Humongous); collects the regions with the most garbage first.

---

## GC roots (what keeps objects alive)

An object is reachable if it can be reached from any **GC root**:
- Local variables & parameters on any thread's stack.
- Static fields of loaded classes.
- Active JNI references.
- Thread objects themselves.

Anything not reachable → eligible for GC.

---

## Reference types

| Type | Collected when | Use case |
|---|---|---|
| **Strong** (normal) | Never while reachable | Default |
| **SoftReference** | On low memory | Memory-sensitive caches |
| **WeakReference** | Next GC cycle if only weak refs remain | `WeakHashMap`, canonical maps |
| **PhantomReference** | After finalization; used with `ReferenceQueue` | Cleanup hooks; replacement for `finalize()` |

---

## Key JVM flags to remember ⭐

```
-Xms512m -Xmx4g                 # initial and max heap
-Xmn1g                          # young gen size
-XX:+UseG1GC                    # select G1
-XX:+UseZGC                     # select ZGC
-XX:MaxGCPauseMillis=200        # G1 pause target
-XX:MaxMetaspaceSize=256m       # cap metaspace (default = unlimited)
-XX:+HeapDumpOnOutOfMemoryError # dump heap on OOM
-XX:HeapDumpPath=/var/log/app/  # dump destination
-Xlog:gc*:file=gc.log           # unified GC logging (Java 9+)
```

---

## Key points to remember ⭐

- ⭐ Objects **always** live on the heap; primitives inside methods live on the **stack**.
- ⭐ Since Java 8, class metadata lives in **Metaspace** (native memory, not heap). PermGen is gone.
- ⭐ Generational hypothesis: most objects die young → cheap Young GC.
- ⭐ Default GC on modern JDKs is **G1**. For ultra-low pause use **ZGC** or **Shenandoah**.
- ⭐ Two OOM flavors: `java.lang.OutOfMemoryError: Java heap space` (bump `-Xmx`) vs `Metaspace` (bump `-XX:MaxMetaspaceSize`, or find class-loader leak).
- ⭐ **`finalize()` is deprecated** since Java 9 — use `try-with-resources` and `Cleaner`.
- ⭐ Escape analysis can allocate short-lived objects on the stack (scalar replacement) → not always on heap.

---

## Common follow-up questions

**Q: What's the difference between Stack and Heap?**
A: Stack is per-thread, LIFO frames, holds primitives and object references; heap is shared, holds actual objects. StackOverflowError vs OutOfMemoryError.

**Q: What's a memory leak in a GC'd language?**
A: Unintentional strong references keeping objects alive: static maps that grow forever, un-removed listeners, ThreadLocal in thread pools, class-loader leaks in servlet containers.

**Q: What replaces `finalize()`?**
A: `AutoCloseable` + try-with-resources for deterministic cleanup, and `java.lang.ref.Cleaner` for post-GC cleanup.

**Q: What is Stop-The-World (STW)?**
A: A GC phase that pauses all application threads. Even G1/ZGC have some STW phases, but they're kept in the millisecond / sub-millisecond range.

**Q: How would you debug a GC issue?**
A: Enable GC logs (`-Xlog:gc*`), inspect with **GCViewer / GCEasy**. Take a heap dump (`jmap -dump` or `-XX:+HeapDumpOnOutOfMemoryError`) and analyze with **MAT (Eclipse Memory Analyzer)**. Watch **Live Set** growth over time — a leak looks like Old Gen slowly ratcheting up after each Full GC.

**Q: Difference between G1 and ZGC in one line?**
A: G1 = predictable pauses (~100 ms) on medium heaps; ZGC = sub-ms pauses on huge heaps (TB), pays with slightly higher CPU cost.

**Q: What is escape analysis?**
A: JIT optimization that proves an object never escapes a method → allocates it on the stack (or "scalar replaces" its fields into registers) → zero GC pressure.

---

## Gotchas / traps

- Assuming `System.gc()` forces a GC — it's only a **hint**; the JVM can ignore it.
- Confusing PermGen with Metaspace (Metaspace = Java 8+; unlimited by default → can eat all memory if class-loading leaks).
- Treating heap dump analysis as "look for the biggest object" — usually the leak is a **shallow-small but retention-huge** object like a static cache.
- Assuming `finalize()` will run — it may run late, or never; do not rely on it.
- Setting `-Xmx` = physical RAM — leaves nothing for Metaspace, native code, thread stacks. Rule of thumb: heap ≤ 70% of container memory.
