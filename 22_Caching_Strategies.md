# 22 — Caching Strategies (Cache-Aside, Write-Through, TTL, Invalidation)

> **Interview question:** *"What caching strategies do you know? When would you use each? How do you invalidate caches? What are cache stampede / thundering herd? Difference between local cache and distributed cache?"*

## 30-second answer

**Five common patterns:**

| Pattern | Read path | Write path | Use case |
|---|---|---|---|
| **Cache-Aside** (Lazy Loading) | App checks cache → miss → load DB → populate cache | App writes to DB, then evicts / updates cache | ⭐ Default. Fits most workloads. |
| **Read-Through** | App asks cache; cache loads from DB on miss | Write via cache | Cache lib manages loading (Caffeine, Guava) |
| **Write-Through** | App reads cache | App writes to cache; cache writes DB synchronously | Ensures cache always consistent with DB |
| **Write-Behind (Write-Back)** | App reads cache | App writes to cache; cache asynchronously flushes to DB | High write throughput; risk of loss on crash |
| **Write-Around** | Cache-aside for reads | App writes only to DB; cache filled on next read | Read-heavy for freshly-written data that's not re-read |

⭐ For most systems: **cache-aside + TTL + explicit invalidate on write**.

---

## Cache-aside (most common)

```java
public User get(long id) {
    User u = cache.get(id);
    if (u == null) {
        u = db.findById(id);
        if (u != null) cache.set(id, u, TTL);
    }
    return u;
}

public void update(User u) {
    db.save(u);
    cache.evict(u.getId());    // or cache.set(u.getId(), u)  — invalidate wins on doubt
}
```

- Pros: simple, cache is optional (miss = fallback).
- Cons: stale on out-of-band DB updates; two write paths (evict + save) need care.

---

## Local vs distributed cache

| | Local (in-process, e.g. Caffeine) | Distributed (Redis, Memcached, Hazelcast) |
|---|---|---|
| Speed | Nanoseconds | Milliseconds (network hop) |
| Sharing | Per JVM | Across all app instances |
| Consistency | Each node has its own copy — potential divergence | Single source of truth |
| Failure blast radius | Only one node | Cluster-wide if Redis down |

⭐ **Two-tier cache** (L1 local + L2 distributed) is common — L1 for hot keys, L2 for consistency. Publish invalidations to a topic so L1s evict.

---

## TTL (Time-To-Live) & TTI (Time-To-Idle)

- **TTL** — max age of an entry; evicted after `X`.
- **TTI** — evict if not accessed for `X`.
- **Refresh-ahead** — refresh entry before TTL expires (Caffeine `refreshAfterWrite`) → hot keys never miss.

⭐ Add **jitter** to TTLs so many keys don't expire at the same time and stampede the DB.

---

## Cache stampede / thundering herd

**Problem:** a hot key expires → 1000 concurrent requests all miss → 1000 concurrent DB queries → DB collapses.

**Mitigations:**
1. **Single-flight (mutex)** — one thread loads; others wait for the result. Caffeine's `get(key, loader)` does this per key by default.
2. **Probabilistic early expiration** — refresh in a small window before TTL with random probability.
3. **Stale-while-revalidate** — serve stale content while a background task refreshes.
4. **Read-through with cache library** (Caffeine `LoadingCache`) → free single-flight.

---

## Eviction policies

- **LRU** — Least Recently Used. Classic.
- **LFU** — Least Frequently Used.
- **W-TinyLFU** (Caffeine) — near-optimal hit ratios; combines LRU + probabilistic LFU.
- **FIFO** — simple, worst hit rate.
- **Random** — cheap, decent.

---

## Cache invalidation strategies

*"There are only two hard things in Computer Science: cache invalidation and naming things."* — Phil Karlton.

- **TTL** — cheapest; tolerate staleness up to TTL.
- **Write-through eviction** — on every DB write, evict/update the cache key.
- **Event-driven** — DB CDC (Debezium) → Kafka → cache invalidator service. Best for microservices sharing state.
- **Versioned keys** — include a version in the key (`user:42:v7`). Bump version to invalidate everything at once (skip actual eviction).
- **Namespaces** — group keys under a prefix; delete the prefix on schema change.

---

## Redis quick reference

- `SETEX key 60 value` — set with TTL.
- `INCR counter` — atomic counter.
- `SETNX key value` — set only if not exists (idempotency keys!).
- `HSET user:42 name Love age 26` — hash → save round-trips vs many small keys.
- `EXPIRE key 300` — set TTL.
- `PUB/SUB` — for cache invalidation broadcast.
- `Redis Cluster` — sharded. Use hash tags `{}` to keep related keys on same slot for transactions.

---

## Spring Cache abstraction

```java
@Cacheable(value = "users", key = "#id")
public User find(long id) { ... }

@CachePut(value = "users", key = "#u.id")
public User update(User u) { ... }

@CacheEvict(value = "users", key = "#id")
public void delete(long id) { ... }

@Caching(evict = {
  @CacheEvict(value = "users", key = "#id"),
  @CacheEvict(value = "userSummaries", allEntries = true)
})
public void purge(long id) { ... }
```

Backed by Caffeine (local), Redis, Hazelcast, EhCache. Same annotations, swap the backend in config.

⭐ **Self-invocation trap** (like `@Transactional`): direct `this.find(id)` does NOT hit the cache — must go through the proxy.

---

## Key points to remember ⭐

- ⭐ Cache-aside + TTL + explicit invalidate on writes = the default.
- ⭐ Add **jitter** to TTL to avoid synchronized expiration.
- ⭐ Cache **stampede** — use single-flight (`LoadingCache`) or stale-while-revalidate.
- ⭐ Local cache (Caffeine): ns latency, per-node. Distributed (Redis): ms latency, consistent.
- ⭐ Two-tier (L1+L2) with pub/sub invalidation is the standard high-scale pattern.
- ⭐ Choose eviction: **W-TinyLFU (Caffeine)** > LRU > LFU > FIFO for hit ratio.
- ⭐ Consistency: cache is **eventually consistent** with DB unless you write-through synchronously.
- ⭐ Never cache secrets / PII without encryption / TTL policies.

---

## Common follow-up questions

**Q: What's the difference between write-through and write-behind?**
A: Write-through — DB updated synchronously with cache; slower writes, strong consistency. Write-behind — cache flushes to DB asynchronously; fast writes, risk data loss on crash.

**Q: When would you NOT use a cache?**
A: Write-heavy workloads with low read ratio, strong-consistency requirements (financial ledgers), or data too large to cache meaningfully.

**Q: How do you cache a paginated list?**
A: Cache by (query + page + size) → hard to invalidate. Or cache entities individually + only the IDs of a page → invalidate a single entity affects any page that had it. Second approach is more work but scales better.

**Q: Redis persistence — RDB vs AOF?**
A: RDB — periodic snapshots; fast restart but recent writes lost. AOF — append-only log; every write logged (can `fsync` per op); durable but slower and larger. Use both for critical data.

**Q: How do you invalidate a cache across many services?**
A: Publish an event (Kafka, Redis Pub/Sub) → each service's local cache listens and evicts. CDC from the DB is the most robust source of truth.

**Q: What's a "negative cache"?**
A: Caching the absence of a value (e.g., "user 42 doesn't exist" for 30 s). Prevents repeated hits for missing keys — but be careful about staleness on later creation.

**Q: Difference between `@Cacheable`, `@CachePut`, `@CacheEvict`?**
A: `@Cacheable` — read-through: if present, return cached; else run method and cache the result. `@CachePut` — always run method, then store result (update). `@CacheEvict` — remove entries.

---

## Gotchas / traps

- Caching mutable objects — mutation after put pollutes the cache.
- No TTL → cache grows forever → OOM.
- Same TTL on many keys → synchronized expiry → stampede.
- Caching after successful DB write, then DB tx rolls back → cache holds ghost data. Cache after commit (or use `@TransactionalEventListener(AFTER_COMMIT)`).
- Cache on read but no eviction on write → stale forever.
- Distributed cache with cluster failover — beware split-brain and stale reads.
- `@Cacheable` self-invocation (calling `this.x()`) — proxy bypassed → cache ignored.
- Caching authorization decisions — hard to invalidate on permission change.
