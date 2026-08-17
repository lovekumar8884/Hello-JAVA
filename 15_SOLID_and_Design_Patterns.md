# 15 — SOLID Principles + Common Design Patterns

> **Interview question:** *"Explain the SOLID principles with examples. What design patterns have you used in real projects? Difference between Factory / Strategy / Template Method / Observer / Singleton / Builder?"*

## 30-second answer — SOLID

- **S**ingle Responsibility — one reason to change per class.
- **O**pen / Closed — open for extension, closed for modification (add behavior without editing existing code).
- **L**iskov Substitution — a subtype must be usable wherever the base type is expected, without breaking behavior.
- **I**nterface Segregation — many small role-specific interfaces > one fat interface.
- **D**ependency Inversion — depend on **abstractions**, not concretions. High-level modules shouldn't depend on low-level modules.

---

## SOLID with 1-line examples

### S — Single Responsibility
```java
// BAD: does 3 things
class Report { void generate(); void save(); void email(); }

// GOOD
class ReportGenerator { void generate(); }
class ReportStorage   { void save(Report r); }
class ReportMailer    { void email(Report r); }
```

### O — Open/Closed
```java
// BAD: adding a new shape edits the class
double area(Shape s) {
    if (s instanceof Circle) ...
    else if (s instanceof Square) ...
}

// GOOD: polymorphism — new shapes add themselves
interface Shape { double area(); }
class Circle implements Shape { public double area() { ... } }
```

### L — Liskov
```java
// Classic violation: Square extends Rectangle
class Rectangle { void setW(int w); void setH(int h); }
class Square extends Rectangle { setW → also sets h → surprises callers }
// A caller that "assumes setW doesn't affect height" is broken.
```

### I — Interface Segregation
```java
// BAD: implementer forced to fake methods
interface Worker { void work(); void eat(); }
class Robot implements Worker { work() ...; eat() { throw ... } }

// GOOD
interface Workable { void work(); }
interface Eatable  { void eat(); }
class Robot implements Workable {}
class Human implements Workable, Eatable {}
```

### D — Dependency Inversion
```java
// BAD: Service concretely depends on a specific DB
class OrderService {
    private final MySqlOrderDao dao = new MySqlOrderDao();
}

// GOOD: depends on an abstraction; concrete injected
class OrderService {
    private final OrderDao dao;
    OrderService(OrderDao dao) { this.dao = dao; }
}
```

⭐ Dependency Injection (Spring) is the mechanical way to enforce DIP.

---

## Common design patterns

### Creational

**Singleton** — one instance, global access.
```java
// idiomatic: enum singleton (thread-safe, serialization-safe, reflection-safe)
public enum Config { INSTANCE; public String get(String k) {...} }

// classic thread-safe: double-checked locking with volatile
public class Config {
    private static volatile Config i;
    public static Config get() {
        Config r = i;
        if (r == null) synchronized (Config.class) {
            r = i;
            if (r == null) i = r = new Config();
        }
        return r;
    }
}
```

**Factory Method** — subclasses decide which class to instantiate.
```java
interface HttpClient { ... }
abstract class HttpClientFactory {
    abstract HttpClient create();
}
```

**Abstract Factory** — factory of related factories (family of products).

**Builder** — step-by-step construction, especially for objects with many optional params.
```java
Pizza p = new Pizza.Builder().size(12).cheese().pepperoni().build();
```
Since Java 14+, records + wither methods often replace builders for simple cases.

**Prototype** — `clone()` an existing instance instead of constructing.

---

### Structural

**Adapter** — bridges incompatible interfaces (`InputStreamReader` bridges `InputStream` → `Reader`).

**Decorator** — wraps an object to add behavior transparently. Java I/O is a decorator maze: `new BufferedReader(new InputStreamReader(in))`.

**Facade** — a simple interface over a complex subsystem.

**Proxy** — stand-in for another object (lazy-load, remote, security, logging). Spring AOP uses **JDK dynamic proxies** and **CGLIB** proxies for `@Transactional`, `@Cacheable`, etc.

**Composite** — tree structure where leaf and composite share the same interface (file system directories).

---

### Behavioral

**Strategy** — swappable algorithm.
```java
interface Sorter { <T> void sort(List<T> l, Comparator<T> c); }
class QuickSorter implements Sorter { ... }
```
Spring uses strategy everywhere: `PasswordEncoder`, `AuthenticationProvider`.

**Template Method** — abstract class with a fixed algorithm skeleton and abstract hooks.
```java
abstract class DataImporter {
    public final void run() { read(); validate(); write(); }
    protected abstract void read();
    protected abstract void validate();
    protected abstract void write();
}
```

**Observer** — publish/subscribe. Java's `PropertyChangeListener`, Spring's `ApplicationEventPublisher`.

**Chain of Responsibility** — handlers in sequence, each decides to handle or pass. Servlet Filters, Spring Security filter chain.

**Command** — encapsulate an action as an object (`Runnable` is the simplest form).

**State** — behavior varies by internal state, delegated to state objects.

**Iterator** — sequential access without exposing structure (`java.util.Iterator`).

**Visitor** — separates an algorithm from the object structure it operates on. Used by ASTs.

---

## Key points to remember ⭐

- ⭐ SOLID is a **guideline**, not a rulebook — overusing SRP creates a mess of tiny classes.
- ⭐ **Dependency Injection** is DIP mechanized. Constructor injection is preferred (immutable, testable, fail-fast).
- ⭐ Prefer **composition over inheritance** — LSP violations usually mean "should have been composition."
- ⭐ **Enum singleton** is Bloch's recommended form: thread-safe, serialization-safe, reflection-safe.
- ⭐ Spring itself is a walking design-patterns tour: DI (constructor injection), Factory (`BeanFactory`), Proxy (`@Transactional`), Template (`JdbcTemplate`, `RestTemplate`), Observer (`ApplicationEventPublisher`), Strategy (`PasswordEncoder`, `Converter`), Facade (`RestTemplate` over `HttpClient`).
- ⭐ Design patterns are a **vocabulary** — knowing the names lets you say "let's use the Strategy pattern here" and everyone knows what you mean.

---

## Common follow-up questions

**Q: Give a real-world example of Strategy pattern.**
A: Payment processing — a `PaymentStrategy` interface with `CreditCardStrategy`, `UpiStrategy`, `PaypalStrategy`. Runtime picks by request type.

**Q: Difference between Strategy and State?**
A: Strategy — client picks the algorithm at construction. State — the object switches its own strategy based on internal transitions.

**Q: Difference between Factory Method and Abstract Factory?**
A: Factory Method: one product, subclasses decide the concrete class. Abstract Factory: creates **families** of related products (e.g., `WinButton + WinCheckbox` vs `MacButton + MacCheckbox`).

**Q: Why is Singleton considered an anti-pattern by some?**
A: It hides dependencies (global state), makes unit testing hard, complicates lifecycle in containers. Prefer a container-managed single bean (Spring).

**Q: What is CGLIB vs JDK dynamic proxy?**
A: JDK proxies wrap **interfaces**. CGLIB generates a **subclass** at runtime — works on classes without interfaces. Spring picks CGLIB when the bean has no interface (or `proxy-target-class=true`).

**Q: Why does Spring's `@Transactional` not work on a private method or self-invocation?**
A: Because the annotation is proxy-based. Private methods don't go through the proxy. Self-invocation (`this.x()`) bypasses the proxy → no transaction.

**Q: Give an example where inheritance is worse than composition.**
A: `Stack extends Vector` (JDK classic mistake) — Stack inherits `add(index, element)` which lets clients break the LIFO invariant.

---

## Gotchas / traps

- Overengineering: not every problem needs a pattern.
- Misapplying LSP by extending "for reuse" rather than "is-a".
- Silently reintroducing global state via Singleton.
- Interface Segregation → 20 one-method interfaces per class → over-fragmentation.
- Builder pattern for a class with 2 fields — just use a constructor.
- Strategy where you actually needed a lookup table (`Map<Type, Strategy>`).
