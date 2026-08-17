# 18 — Spring Boot Autoconfiguration & Starters

> **Interview question:** *"How does Spring Boot autoconfiguration work? What is a starter? Difference between `@Component`, `@Configuration`, `@ComponentScan`, `@EnableAutoConfiguration`, `@SpringBootApplication`? How do you disable / customize an autoconfiguration?"*

## 30-second answer

- **Autoconfiguration** = Spring Boot's convention-over-configuration: when it sees certain classes on the classpath (H2, Tomcat, Redis…), it auto-creates the beans you'd otherwise wire manually — **but only if you haven't defined them yourself**.
- **Starter** = a pom that bundles a set of dependencies + autoconfig for a use case: `spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `spring-boot-starter-security`.
- `@SpringBootApplication` = `@Configuration` + `@EnableAutoConfiguration` + `@ComponentScan` (on the package of the annotated class).

---

## `@SpringBootApplication` breakdown

```java
@Configuration            // this is a config class
@EnableAutoConfiguration  // turn on autoconfig
@ComponentScan            // scan current package + subpackages
public @interface SpringBootApplication { ... }
```

⭐ Structural rule: your `@SpringBootApplication` class should live in the **root package** of your app so component scan reaches everything.

---

## How autoconfiguration actually works

1. Spring Boot loads all `AutoConfiguration` classes listed in:
   - `META-INF/spring.factories` (Boot 2.x)
   - `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` (Boot 2.7+ / 3.x)
2. Each is a `@Configuration` guarded by **conditional annotations**:

| Annotation | Fires only if… |
|---|---|
| `@ConditionalOnClass` | Class is on classpath |
| `@ConditionalOnMissingClass` | Class is NOT on classpath |
| `@ConditionalOnBean` | A bean of that type already exists |
| `@ConditionalOnMissingBean` | ⭐ No bean of that type exists — the "user override" trick |
| `@ConditionalOnProperty` | A property has a specific value |
| `@ConditionalOnWebApplication` | Web app (servlet or reactive) |
| `@ConditionalOnResource` | A resource is present |
| `@ConditionalOnExpression` | SpEL expression true |

⭐ The **`@ConditionalOnMissingBean`** pattern is how Boot lets you override: if you define your own `DataSource`, Boot's autoconfig backs off.

3. Each autoconfig may pull config from a `@ConfigurationProperties` class (bound from `application.yml`).

---

## Simplified autoconfig example

```java
@Configuration
@ConditionalOnClass(HikariDataSource.class)
@EnableConfigurationProperties(DataSourceProperties.class)
public class DataSourceAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean            // user override wins
    public DataSource dataSource(DataSourceProperties p) {
        return DataSourceBuilder.create()
            .url(p.getUrl()).username(p.getUsername()).password(p.getPassword())
            .build();
    }
}
```

---

## `application.yml` binding

```java
@ConfigurationProperties(prefix = "app.mail")
@Component
public class MailProps {
    private String host;
    private int port = 25;
    private Duration timeout = Duration.ofSeconds(5);
    // getters / setters (or use records / Lombok)
}
```
```yaml
app:
  mail:
    host: smtp.example.com
    port: 587
    timeout: 3s
```

- Type-safe, validated (`@Validated + @NotBlank`), IDE-completed via `spring-boot-configuration-processor`.
- Preferred over scattering `@Value("${...}")` all over the code.

---

## Property source precedence (highest wins) ⭐

1. Command-line args: `--server.port=9000`
2. `SPRING_APPLICATION_JSON` env var
3. OS environment variables
4. Java system properties (`-D...`)
5. **Profile-specific** `application-{profile}.yml`
6. `application.yml` in current dir → classpath
7. `@PropertySource`
8. Defaults set in code

That's why `SERVER_PORT=8081 java -jar app.jar` overrides `application.yml`.

---

## Profiles

```java
@Profile("prod")           // this bean loaded only when 'prod' profile is active
@Component
class ProdMailer implements Mailer { ... }
```
Activate via:
- `spring.profiles.active=prod` (property)
- `SPRING_PROFILES_ACTIVE=prod` (env)
- `--spring.profiles.active=prod` (CLI)

---

## Disabling an autoconfig

```java
@SpringBootApplication(exclude = { DataSourceAutoConfiguration.class })
```
Or via property:
```
spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
```

---

## Actuator (know these endpoints)

- `/actuator/health` — liveness / readiness composite
- `/actuator/info` — build info, git commit
- `/actuator/metrics` — Micrometer metrics
- `/actuator/prometheus` — Prometheus scrape endpoint
- `/actuator/env` — env / properties (secrets masked)
- `/actuator/beans` — full bean graph
- `/actuator/mappings` — HTTP mappings
- `/actuator/heapdump`, `/actuator/threaddump` — profiling

⭐ In prod, expose only what you need: `management.endpoints.web.exposure.include=health,info,prometheus`.

---

## Debugging: which autoconfigs fired?

- Start with `--debug` → prints the "Conditions Evaluation Report":
  - **Positive matches** — enabled autoconfigs.
  - **Negative matches** — why others were skipped (e.g., "did not find bean X").
- Actuator's `/actuator/conditions`.

---

## Key points to remember ⭐

- ⭐ Autoconfigs are **just `@Configuration`s with `@Conditional*`**.
- ⭐ `@ConditionalOnMissingBean` = "user's bean wins" — the mechanism for override.
- ⭐ `@SpringBootApplication` = `@Configuration` + `@EnableAutoConfiguration` + `@ComponentScan`.
- ⭐ Property precedence: CLI > env > system props > profile YAML > default YAML.
- ⭐ Prefer **`@ConfigurationProperties`** over scattered `@Value` — type-safe, validated, discoverable.
- ⭐ Actuator + Micrometer = ops story out of the box.
- ⭐ Boot **auto-detects** `application-{profile}.yml` when profile is active.
- ⭐ To disable Tomcat and use Netty: swap `spring-boot-starter-web` for `spring-boot-starter-webflux`.

---

## Common follow-up questions

**Q: How does Boot know which starter to use?**
A: Each starter has an autoconfig JAR listing autoconfig classes. Boot loads them all and lets `@Conditional` decide which take effect.

**Q: What's the difference between `application.properties` and `application.yml`?**
A: Same functionality — one is flat key=value, the other hierarchical. YAML is easier to read for nested config.

**Q: How do you write your own autoconfig library?**
A: Create a jar with:
- `@Configuration` classes with `@Conditional*`
- `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` listing them
- Optionally a `spring-configuration-metadata.json` for IDE completion

**Q: Difference between `@Value` and `@ConfigurationProperties`?**
A: `@Value("${x.y}")` — one property per field, no validation, no bulk binding. `@ConfigurationProperties` — binds a whole tree, type-safe, supports validation, IDE metadata.

**Q: How do you profile-specific override a bean?**
A: `@Profile("prod")` on the alternative bean; `@Profile("!prod")` on the fallback.

**Q: How does Spring Boot embed Tomcat?**
A: `spring-boot-starter-web` pulls `spring-boot-starter-tomcat` (default). The autoconfig starts an embedded Tomcat and registers the `DispatcherServlet`. No `web.xml`.

**Q: What are Spring Boot layers in a Docker image?**
A: `spring-boot-maven-plugin` supports layered jars — deps, snapshot-deps, resources, classes. Docker `COPY --from=extract` builds cacheable layers per change frequency.

---

## Gotchas / traps

- `@SpringBootApplication` not in the root package → some `@Component`s not found.
- Two beans of the same type from two starters → `NoUniqueBeanDefinitionException`. Use `@Primary` or `@Qualifier`.
- Assuming `application.yml` overrides env vars — it's the opposite.
- Exposing `/actuator/env` in prod without securing it → leaks secrets.
- Using `spring-boot-starter-parent` and `dependencyManagement` — pick one BOM strategy.
- Boot 3 requires **Java 17+** and switches from `javax.*` to `jakarta.*` — a migration trap when upgrading.
