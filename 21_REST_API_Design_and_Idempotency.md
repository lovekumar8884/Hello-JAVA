# 21 — REST API Design + Idempotency + Retries

> **Interview question:** *"How would you design a REST API? What's the difference between PUT and POST? What makes an endpoint idempotent? How do you handle retries and duplicate requests safely?"*

## 30-second answer

**REST = Resources (nouns), verbs = HTTP methods, state carried in URLs and status codes.**

**Idempotent** = the same request repeated N times has the same effect as once.

**HTTP method table (memorize):**

| Method | Purpose | Safe? | Idempotent? |
|---|---|---|---|
| `GET` | Read | ✅ | ✅ |
| `HEAD` | Metadata only | ✅ | ✅ |
| `OPTIONS` | Discovery | ✅ | ✅ |
| `PUT` | Create/replace at known URL | ❌ | ✅ (whole-resource replace) |
| `DELETE` | Remove | ❌ | ✅ (2nd call returns 404 or 204) |
| `POST` | Create with server-generated ID; non-idempotent action | ❌ | ❌ |
| `PATCH` | Partial update | ❌ | ❌ by spec (can be made idempotent) |

⭐ Retries are safe **only** on idempotent methods. Non-idempotent → require an `Idempotency-Key`.

---

## HTTP status codes to recall cold

- **2xx Success**
  - `200 OK` — generic success
  - `201 Created` — resource created; include `Location` header
  - `202 Accepted` — async processing accepted
  - `204 No Content` — success, no body (great for `PUT`/`DELETE`)
- **3xx Redirection**
  - `301 Moved Permanently` — cacheable redirect
  - `304 Not Modified` — conditional GET hit
- **4xx Client error**
  - `400 Bad Request` — malformed
  - `401 Unauthorized` — no/invalid auth
  - `403 Forbidden` — authenticated but not allowed
  - `404 Not Found`
  - `409 Conflict` — version conflict, duplicate
  - `410 Gone` — permanently removed
  - `415 Unsupported Media Type`
  - `422 Unprocessable Entity` — validation failed (semantic, not syntax)
  - `429 Too Many Requests` — rate-limited (include `Retry-After`)
- **5xx Server error**
  - `500 Internal Server Error` — bug
  - `502 Bad Gateway` — upstream returned invalid
  - `503 Service Unavailable` — down or maintenance
  - `504 Gateway Timeout`

---

## URL design

- **Nouns, not verbs:** `/orders/123/items` ✅ not `/getOrderItems?id=123` ❌.
- Use plurals: `/users`, `/users/{id}`.
- Nest to show ownership: `/users/{id}/orders`.
- Query params for filters, pagination, sort: `/orders?status=OPEN&page=2&size=20&sort=createdAt,desc`.
- Version via prefix (`/v1/...`) or `Accept` header — prefix is more discoverable.

---

## Idempotency in practice

### Idempotency by design
- `PUT /users/{id}` with full state → repeat = same final state.
- `DELETE /users/{id}` → repeated calls: first 204, subsequent 404 or 204 (safe either way).

### Idempotency for `POST` — the `Idempotency-Key` pattern
```
POST /payments
Idempotency-Key: 3f0e6a4c-...-...  (UUID from client)
```
Server logic:
1. Look up the key.
2. If seen and completed → return **the stored response** (same status, same body).
3. If seen and in-flight → return `409` or wait/return same result.
4. If new → save the key + request hash + start processing; store response.
- TTL the key (e.g., 24h) so it doesn't grow forever.
- Storage: Redis with `SETNX` + TTL, or a `processed_requests` table.

⭐ This pattern is essential for payments, order creation, refunds — anything that must not double-charge on a network retry.

---

## Retry patterns

- **Exponential backoff with jitter** — retry after 100 ms, 200 ms, 400 ms + random 0–100 ms. Prevents thundering herd.
- **Max attempts** (usually 3–5). After that, dead-letter or fail fast.
- **Circuit breaker** (Resilience4j / Hystrix): after a burst of failures, stop calling upstream for a cooldown period → fail fast → let upstream recover.
- **Bulkhead**: separate thread pool per downstream to isolate slowdowns.
- **Timeout everywhere** — including connect and read. Default HTTP client timeouts are usually too long.

```java
Retry retry = Retry.of("payment", RetryConfig.custom()
    .maxAttempts(4)
    .intervalFunction(IntervalFunction.ofExponentialRandomBackoff(100, 2, 0.5, 5_000))
    .retryOnException(ex -> ex instanceof IOException)
    .build());
```

---

## HATEOAS (link-driven APIs)

Not commonly required, but be aware: responses include `_links` to next actions.
```json
{
  "id": 42, "status": "PENDING",
  "_links": { "cancel": { "href": "/orders/42/cancel" } }
}
```

---

## Pagination

- `Offset + limit` — simple; `page=2&size=20`. Slow on deep offsets.
- `Cursor-based` (`?after=<opaque_cursor>&limit=20`) — stable for infinite feeds, ordered timeline. Better perf.

Response typically includes:
```json
{
  "data": [...],
  "page": { "number": 2, "size": 20, "totalElements": 812, "totalPages": 41 }
}
```

---

## Error responses (Problem Details, RFC 7807)

```json
{
  "type": "https://api.example.com/errors/duplicate-email",
  "title": "Duplicate email",
  "status": 409,
  "detail": "The email a@b.com is already in use.",
  "instance": "/users",
  "traceId": "abc123"
}
```
Consistent across the API → easy for clients to handle.

---

## Key points to remember ⭐

- ⭐ **Idempotent methods can be safely retried; non-idempotent need an `Idempotency-Key`.**
- ⭐ `PUT` replaces (or creates at known URL), `POST` creates with server-assigned ID.
- ⭐ `201 Created` should include `Location: /users/42`.
- ⭐ Retries: **exponential backoff + jitter + cap** to avoid thundering herd.
- ⭐ Timeouts on both connect and read; circuit-break on repeated failure.
- ⭐ Return domain errors as **RFC 7807 Problem Details** — consistent shape wins in prod.
- ⭐ Prefer **cursor pagination** for large / real-time feeds; offset is fine for admin lists.
- ⭐ Rate limit with `429 + Retry-After`.
- ⭐ Use **`Etag` + `If-None-Match`** for conditional GET; **`If-Match`** for concurrency-safe PUT (optimistic locking at the API layer).

---

## Common follow-up questions

**Q: Is PUT always idempotent?**
A: By HTTP spec yes — same PUT body twice results in the same final state. Your implementation must not violate that (e.g., append-to-list on PUT would break it).

**Q: Difference between 401 and 403?**
A: 401 = "who are you? (auth)". 403 = "I know who you are, you're not allowed".

**Q: Is `POST /transfers` idempotent?**
A: Not by default. Add `Idempotency-Key` header, or dedupe by an `externalId` in the payload.

**Q: PATCH vs PUT?**
A: PUT replaces the whole resource. PATCH applies a partial diff (JSON Patch RFC 6902 or JSON Merge Patch RFC 7396).

**Q: How do you version APIs?**
A: URL prefix (`/v1`), custom header (`Api-Version`), or `Accept` header content negotiation. URL is most transparent.

**Q: What is HATEOAS?**
A: Hypermedia As The Engine Of Application State — responses embed links to legal next actions. Advanced level of REST maturity; rarely fully implemented.

**Q: How do you protect against replay attacks?**
A: Short-lived tokens (JWT with `exp`), nonces / `Idempotency-Key`, HTTPS everywhere, `X-Request-ID`.

**Q: Difference between REST and gRPC?**
A: REST — text (JSON) over HTTP/1.1, human-debuggable. gRPC — binary (Protobuf) over HTTP/2, streaming, typed contracts (`.proto`). gRPC ~2–10× faster, worse browser support.

---

## Gotchas / traps

- Using `POST` for reads because "it has a body" — breaks caching and idempotency.
- Returning `200` with an error body — clients keep treating failures as success.
- Retrying `POST` without an idempotency key → double-charge.
- Missing timeouts → hangs cascading into every service.
- Not rate-limiting a public endpoint → DoS vector.
- Verbs in URL: `/getUsers`, `/deleteOrder` — anti-REST.
- Bulky payloads on `GET` — inefficient; break into paginated resources.
- Exposing internal DB IDs when you should hash / use UUIDs — enumeration risk.
