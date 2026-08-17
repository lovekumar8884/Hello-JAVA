# Java Interview Revision Kit (SDE-2)

> Open any file — the **title itself is the interviewer's question**. Skim the top of the file to recall the answer in 30 seconds; read further only if you need depth.

Each topic file follows the same layout so revision is fast:

1. **Interview question** (the trigger)
2. **30-second answer** (bullet recall)
3. **Detailed explanation** (with example)
4. **Key points to remember** (⭐ interview gold)
5. **Common follow-up questions**
6. **Gotchas / traps**

---

## Index

### 1. Collections & Data Structures
- [01 — How does HashMap work internally?](01_HashMap_Internals.md)
- [02 — ConcurrentHashMap vs HashMap vs Hashtable vs SynchronizedMap](02_ConcurrentHashMap_and_Thread_Safe_Collections.md)
- [03 — ArrayList vs LinkedList — which to use when?](03_ArrayList_vs_LinkedList.md)
- [04 — equals() and hashCode() contract (and Comparable/Comparator)](04_Equals_HashCode_and_Comparable.md)

### 2. Core Java
- [05 — String vs StringBuilder vs StringBuffer + why String is immutable](05_String_StringBuilder_and_Immutability.md)
- [06 — JVM Memory Model & Garbage Collection](06_JVM_Memory_Model_and_Garbage_Collection.md)
- [07 — Checked vs Unchecked Exceptions](07_Exception_Handling.md)

### 3. Concurrency & Multithreading
- [08 — How do you create a Thread? Thread lifecycle](08_Threads_and_Runnable.md)
- [09 — synchronized vs volatile vs Lock](09_Synchronized_Volatile_and_Locks.md)
- [10 — ExecutorService and ThreadPool internals](10_ExecutorService_and_ThreadPool.md)
- [11 — Deadlock: causes, detection, prevention](11_Deadlock_and_Concurrency_Utilities.md)
- [12 — CompletableFuture — async programming in Java](12_CompletableFuture_and_Async.md)

### 4. Java 8+ Features
- [13 — Streams API + Lambdas + Functional Interfaces](13_Streams_Lambdas_and_Functional_Interfaces.md)
- [14 — Optional — how to use it correctly](14_Optional.md)

### 5. OOP & Design
- [15 — SOLID principles + common Design Patterns](15_SOLID_and_Design_Patterns.md)

### 6. Spring & Spring Boot
- [16 — Spring IoC, DI and Bean Lifecycle / Scopes](16_Spring_IoC_DI_and_Bean_Lifecycle.md)
- [17 — @Transactional — propagation, isolation, and AOP](17_Spring_Transactional_and_AOP.md)
- [18 — Spring Boot Autoconfiguration & starters](18_Spring_Boot_Autoconfiguration.md)

### 7. Databases & JPA
- [19 — ACID, Isolation Levels & Locking](19_ACID_Isolation_Levels_and_Locks.md)
- [20 — JPA / Hibernate N+1 problem](20_JPA_Hibernate_N_Plus_1.md)

### 8. System & API Design
- [21 — REST API design + Idempotency + Retries](21_REST_API_Design_and_Idempotency.md)
- [22 — Caching strategies (cache-aside, write-through, TTL, invalidation)](22_Caching_Strategies.md)

### 9. Coding Problems (quick warm-ups)
- [Coding_FirstNonRepeatingCharacter.java](Coding_FirstNonRepeatingCharacter.java) — return the char
- [Coding_FirstUniqueCharIndex.java](Coding_FirstUniqueCharIndex.java) — return the index (LeetCode 387)
- [Coding_MergeSort.java](Coding_MergeSort.java) — divide & conquer, stable O(n log n)
- [Coding_SetMatrixZeroes.java](Coding_SetMatrixZeroes.java) — in-place, O(1) extra space (LeetCode 73)

---

## How to revise fast (recommended cycle)

- **Day 1–2:** Collections (01–04) — most-asked bucket.
- **Day 3:** Core Java (05–07).
- **Day 4–5:** Concurrency (08–12) — heavily asked at SDE-2.
- **Day 6:** Java 8 (13–14).
- **Day 7:** OOP / SOLID (15).
- **Day 8:** Spring (16–18).
- **Day 9:** DB + N+1 (19–20).
- **Day 10:** API design & caching (21–22).

> Rule: for every file, first read only the **question title + 30-second answer**. Only dive deeper into files where recall failed.
