# 16 — Spring IoC, DI, Bean Lifecycle and Scopes

> **Interview question:** *"What is IoC / Dependency Injection? Difference between constructor, setter, and field injection? What are Spring bean scopes? Explain the bean lifecycle callbacks (`@PostConstruct`, `@PreDestroy`, `InitializingBean`, `BeanPostProcessor`)."*

## 30-second answer

- **IoC (Inversion of Control)** = the container creates and wires objects; your code doesn't `new` its collaborators.
- **DI (Dependency Injection)** = the concrete IoC mechanism — dependencies are passed in.
- **3 forms:** constructor (⭐ preferred), setter, field. Constructor gives immutable, testable, fail-fast beans.
- **Default scope:** `singleton`. Others: `prototype`, `request`, `session`, `application`, `websocket`.
- **Lifecycle order:** constructor → dependencies injected → `@PostConstruct` → bean is used → `@PreDestroy` (on shutdown, singletons only).

---

## The IoC container

- `ApplicationContext` is the runtime container. Spring Boot: `AnnotationConfigApplicationContext` (via `SpringApplication.run(...)`).
- Beans discovered via `@ComponentScan` (`@Component`, `@Service`, `@Repository`, `@Controller`) or defined via `@Configuration` + `@Bean`.

---

## The 3 injection styles

```java
@Service
class OrderService {

    // ⭐ CONSTRUCTOR injection (preferred)
    private final PaymentClient payments;
    private final OrderRepo repo;
    OrderService(PaymentClient payments, OrderRepo repo) {
        this.payments = payments;
        this.repo = repo;
    }

    // SETTER injection
    private DiscountEngine discount;
    @Autowired void setDiscount(DiscountEngine d) { this.discount = d; }

    // FIELD injection — avoid
    @Autowired private AuditLog audit;
}
```

| | Constructor ⭐ | Setter | Field |
|---|---|---|---|
| Immutability | ✅ `final` | ❌ | ❌ |
| Required deps enforced at compile | ✅ | ❌ | ❌ |
| Testable without Spring | ✅ (just `new`) | ✅ | ❌ (needs reflection) |
| Detects circular deps early | ✅ (fails startup) | ❌ | ❌ |
| Good for optional deps | ❌ | ✅ | ❌ |

⭐ Since Spring 4.3, if a class has **one constructor**, `@Autowired` is optional. If Lombok's `@RequiredArgsConstructor`, no annotation needed.

---

## Bean scopes

| Scope | Instances | Use case |
|---|---|---|
| `singleton` (default) | 1 per container | Stateless services, repositories |
| `prototype` | new per injection / `getBean()` | Stateful helper beans |
| `request` | 1 per HTTP request | Per-request context (web scope) |
| `session` | 1 per HTTP session | Per-user state (web scope) |
| `application` | 1 per ServletContext | ~ singleton for the servlet ctx |
| `websocket` | 1 per WebSocket session | WS handlers |

```java
@Component
@Scope("prototype")
public class Cart { ... }
```

⭐ **Prototype trap:** injecting a prototype into a singleton → the prototype is created **once** at wire time and effectively becomes a singleton. Fixes:
- Inject `ObjectProvider<Cart>` or `Provider<Cart>` and call `.getObject()` per use.
- Use `@Scope(value = "prototype", proxyMode = TARGET_CLASS)` → a proxy is injected that resolves a new instance per call.

---

## Bean lifecycle (singleton scope)

```
1. Container starts
2. Bean class loaded → constructor invoked
3. Dependencies injected (setter/field/autowired)
4. BeanNameAware.setBeanName, BeanFactoryAware.setBeanFactory (if implemented)
5. BeanPostProcessor.postProcessBeforeInitialization  (⭐ AOP proxies wired here)
6. @PostConstruct method invoked
7. InitializingBean.afterPropertiesSet() invoked
8. Custom init-method (@Bean(initMethod=...))
9. BeanPostProcessor.postProcessAfterInitialization
10. Bean is ready — used by application
--- on shutdown ---
11. @PreDestroy method invoked
12. DisposableBean.destroy()
13. Custom destroy-method
```

⭐ For most cases, use **`@PostConstruct`** and **`@PreDestroy`** — they're clean, framework-neutral (JSR-250), and don't tie your class to Spring's `InitializingBean` / `DisposableBean`.

⭐ **Prototype-scoped beans don't get `@PreDestroy` invoked** — Spring hands them off and forgets.

---

## `@Configuration` vs `@Component`

Both are beans. `@Configuration` classes get **CGLIB-enhanced** so that `@Bean` method calls within the same class return the **same singleton** (bean-method caching).

```java
@Configuration
class Config {
    @Bean DataSource ds() { return ...; }
    @Bean Repo repo() { return new Repo(ds()); }   // ds() returns the SAME bean each call
}
```

If you use `@Component` instead, `ds()` returns a new instance each time → subtle bugs.

---

## `@Autowired` resolution order

1. By **type** (interface). If exactly one candidate → done.
2. If multiple candidates → filter by `@Primary`.
3. Still multiple → match by **field/parameter name** (the "qualifier is the name").
4. Explicit `@Qualifier("beanName")` overrides.
5. None → `NoUniqueBeanDefinitionException` or `NoSuchBeanDefinitionException`.

---

## Circular dependencies

- **Constructor ↔ constructor**: fails at startup — no way to construct either without the other. This is a *design smell*.
- **Setter/field**: Spring can resolve via early references / proxies, but it's fragile.
- Spring Boot 2.6+ **disabled circular refs by default** — fix them rather than enabling `spring.main.allow-circular-references=true`.

**Fix:** extract a third bean, use `@Lazy`, or use events.

---

## Key points to remember ⭐

- ⭐ Constructor injection > setter > field. Field injection makes classes untestable without a container.
- ⭐ Default scope is **singleton** — beans must be stateless (or use ThreadLocal / prototype).
- ⭐ Prototype inside singleton → prototype behaves like singleton. Use `ObjectProvider` or scoped proxy.
- ⭐ `@PostConstruct` runs after DI, before the bean is used. `@PreDestroy` runs before container shutdown.
- ⭐ `@Configuration` classes are CGLIB-proxied; `@Bean` methods called on the config class return the cached singleton.
- ⭐ `@Component` (and its stereotypes) + `@ComponentScan` = auto-discovery. `@Configuration` + `@Bean` = explicit wiring.
- ⭐ **Circular deps** = design smell. Refactor.
- ⭐ Prefer `@RequiredArgsConstructor` (Lombok) + `final` fields → immutable, thread-safe beans.

---

## Common follow-up questions

**Q: Difference between BeanFactory and ApplicationContext?**
A: `ApplicationContext` extends `BeanFactory` and adds: eager singleton init, event publishing, i18n, resource loading, AOP integration. Almost everyone uses `ApplicationContext`.

**Q: Difference between `@Component`, `@Service`, `@Repository`, `@Controller`?**
A: Meta-annotations for the same thing. `@Repository` also adds JPA/JDBC exception translation. `@Controller`/`@RestController` are picked up by Spring MVC. Semantically distinct, technically similar.

**Q: What is `@Primary` vs `@Qualifier`?**
A: `@Primary` marks a bean as the default when multiple candidates exist. `@Qualifier("name")` at the injection site explicitly picks one — overrides `@Primary`.

**Q: How would you inject a `List<X>` of all beans of a type?**
A: `List<X>` or `Map<String, X>` — Spring injects all beans of that type (map keyed by bean name).

**Q: Difference between `@Autowired` and `@Inject` and `@Resource`?**
A: `@Autowired` — Spring, by type. `@Inject` — JSR-330, by type. `@Resource` — JSR-250, by **name** first then type.

**Q: What is a `BeanPostProcessor`? Give an example.**
A: A hook that intercepts every bean before and after init. Spring's AOP wraps beans in proxies via a BeanPostProcessor. `AutowiredAnnotationBeanPostProcessor` processes `@Autowired`.

**Q: Why is field injection considered bad?**
A: You can't set final. You can't construct without reflection in tests. You hide the dependency count (constructors with 8 params scream "SRP violation").

---

## Gotchas / traps

- Prototype injected into singleton → single instance.
- Self-invocation (`this.transactionalMethod()`) bypasses the proxy → no transaction, no cache, no async.
- `@Autowired` on a **static** field or method — doesn't work.
- `@Bean` method calling another `@Bean` method inside a class annotated with `@Component` (not `@Configuration`) → new instance each time.
- Setter injection of a required dep → `NullPointerException` before Spring calls the setter. Constructor injection prevents this.
- Two beans of the same type + no `@Primary` / `@Qualifier` → startup fails with `NoUniqueBeanDefinitionException`.
