# Spring Boot — Hard Interview Study Guide

**Target:** Senior/Staff-level Spring Boot interviews. Covers concepts, deep-dive Q&A, and common pitfalls.

---

## 1. Core Concepts You Must Know

### 1.1 IoC (Inversion of Control) vs DI (Dependency Injection)

| Concept | What It Means |
|--------|----------------|
| **IoC** | Framework controls object creation and lifecycle; your code receives dependencies instead of creating them (`new`). "Hollywood principle": *Don't call us, we'll call you.* |
| **DI** | Implementation of IoC: dependencies are *injected* into a class (constructor, setter, field) rather than constructed internally. |

**Why it matters:** Testability (mock injection), loose coupling, centralized configuration.

---

### 1.2 Spring vs Spring Boot

| | Spring Framework | Spring Boot |
|---|------------------|-------------|
| **Config** | XML or Java config; manual setup | Convention over configuration; minimal config |
| **Embedded server** | You deploy to Tomcat/Jetty | Bundles embedded Tomcat/Jetty/Undertow |
| **Starters** | Manual dependency management | `spring-boot-starter-*` — curated deps |
| **Auto-configuration** | None | Conditional beans based on classpath + properties |
| **Production-ready** | You wire metrics, health, etc. | Actuator provides health, metrics, env |

**Spring Boot = Spring + opinionated defaults + auto-configuration + embedded server + Actuator.**

---

### 1.3 Bean Lifecycle (High-Value Interview Topic)

```
Instantiation → Populate properties → BeanNameAware → BeanFactoryAware
  → ApplicationContextAware → BeanPostProcessor.postProcessBeforeInitialization
  → @PostConstruct / InitializingBean.afterPropertiesSet
  → Custom init-method
  → BeanPostProcessor.postProcessAfterInitialization
  → Bean ready
  → @PreDestroy / DisposableBean.destroy / destroy-method
```

**Key:** `BeanPostProcessor` runs for *every* bean; use for cross-cutting logic (e.g. proxying). `@PostConstruct` runs after DI, before bean is "ready."

---

### 1.4 Bean Scopes

| Scope | Description |
|-------|-------------|
| **singleton** (default) | One instance per IoC container |
| **prototype** | New instance per request (`getBean()` / injection) |
| **request** | One per HTTP request (web) |
| **session** | One per HTTP session (web) |
| **application** | One per `ServletContext` (web) |

**Gotcha:** Injecting a prototype into a singleton means the singleton holds one prototype instance forever. Use `ObjectFactory<MyProto>` or `Provider<MyProto>` to get a new instance each time, or `@Lookup` (method injection).

---

## 2. Auto-Configuration (How Spring Boot "Magic" Works)

### 2.1 Mechanism

1. **`@SpringBootApplication`** = `@Configuration` + `@EnableAutoConfiguration` + `@ComponentScan`.
2. **`@EnableAutoConfiguration`** loads `META-INF/spring.factories` (or `spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` in Boot 3.x) from each jar.
3. Auto-config classes use **`@Conditional*`** annotations:
   - `@ConditionalOnClass` — class on classpath
   - `@ConditionalOnBean` / `@ConditionalOnMissingBean` — bean exists / missing
   - `@ConditionalOnProperty` — property present/value
   - `@ConditionalOnWebApplication` / `@ConditionalOnNotWebApplication`

4. Only if conditions match, the auto-config `@Bean` methods run. Your explicit `@Bean` typically wins over auto-config (e.g. `@ConditionalOnMissingBean`).

### 2.2 Disabling Auto-Config

```java
@SpringBootApplication(exclude = { DataSourceAutoConfiguration.class })
// or in application.properties:
// spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
```

---

## 3. Dependency Injection — Advanced

### 3.1 `@Primary` vs `@Qualifier`

- **`@Primary`:** When multiple beans match an injection point, the one marked `@Primary` is chosen by default.
- **`@Qualifier`:** Explicitly pick a bean by name or custom qualifier.

```java
@Bean @Primary
public MyService defaultService() { ... }

@Bean
public MyService auditService() { ... }

// Injected: defaultService
public MyClient(MyService s) { ... }

// Injected: auditService
public MyClient(@Qualifier("auditService") MyService s) { ... }
```

### 3.2 Circular Dependency

**Problem:** A → B → C → A. Spring usually resolves it **if** injection is **constructor-based** for at least one leg of the cycle? No — **constructor-only circular deps cause failure.**

- **By default,** Spring uses **setter/field injection** for one of the beans in the cycle (via **lazy proxy**), so most circular deps work with field/setter injection.
- **Constructor-only** circular dependency **cannot** be resolved; Spring throws `BeanCurrentlyInCreationException`.

**Fix:** Prefer **refactoring** (extract shared logic, use events). If you must: break cycle with `@Lazy` on one constructor arg, or use setter/field for one dependency.

---

## 4. AOP (Aspect-Oriented Programming)

### 4.1 Concepts

- **Aspect:** Module encapsulating cross-cutting concern (logging, tx, security).
- **Join point:** Point in execution (e.g. method invocation).
- **Advice:** Action taken at a join point — **@Before**, **@After**, **@AfterReturning**, **@AfterThrowing**, **@Around**.
- **Pointcut:** Expression that matches join points (e.g. `@annotation(Transactional)`).

### 4.2 How Spring AOP Works

- **Proxy-based:** Spring wraps the target bean in a **JDK dynamic proxy** (if interface) or **CGLIB proxy** (no interface). Calls go through the proxy → advice → target.
- **Self-invocation:** A method calling another method **on the same object** does **not** go through the proxy. So `@Transactional`, `@Async`, or custom aspects **won’t apply** to the callee.

**Fix:** Inject self (`@Autowired self`), call another bean, or use `AopContext.currentProxy()` (requires `exposeProxy = true`).

### 4.3 `@Transactional` Is AOP

- Applied via proxy. Same self-invocation rules apply.
- **Non-public** methods: typically **not** proxied → **no transaction**. Keep `@Transactional` on **public** methods.

---

## 5. Transactions — Critical for Hard Interviews

### 5.1 Propagation

| Propagation | Behavior |
|-------------|----------|
| **REQUIRED** (default) | Join existing tx; create new if none. |
| **REQUIRES_NEW** | Always suspend current (if any) and create new tx. |
| **NESTED** | Use savepoint within existing tx (JDBC). New logical "sub-transaction." |
| **SUPPORTS** | Run in tx if one exists; otherwise none. |
| **NOT_SUPPORTED** | Suspend current tx; run non-transactional. |
| **MANDATORY** | Must run within existing tx; else exception. |
| **NEVER** | Must *not* run within tx; else exception. |

**Classic scenario:**  
- `methodA` (REQUIRED) calls `methodB` (REQUIRES_NEW).  
- B runs in its **own** tx. If B commits and A later throws, A rolls back but **B’s commit stays**. Use when you want independent tx (e.g. audit log).

### 5.2 Isolation

- **READ_UNCOMMITTED**, **READ_COMMITTED**, **REPEATABLE_READ**, **SERIALIZABLE** — same as DB. Default usually **READ_COMMITTED**.
- **`@Transactional(isolation = Isolation.REPEATABLE_READ)`** — use when you need consistent reads within the same tx.

### 5.3 `rollbackFor`

- Default: rolls back on **unchecked** exceptions only. **Checked exceptions do not** roll back.
- **`@Transactional(rollbackFor = Exception.class)`** — roll back on any `Exception` (and subclasses).

### 5.4 Other Gotchas

- **Private methods:** Not proxied → **no transaction**.
- **Same-bean call:** Self-invocation → **no new tx**; propagation rules ignored for the callee.
- **Transactional-specific read-only:** `readOnly = true` hints to DB/Orm for optimizations; it does **not** enforce read-only at application level.

---

## 6. Spring MVC / REST

### 6.1 Request Flow

```
Request → DispatcherServlet → HandlerMapping → Controller
       → ModelAndView / @ResponseBody
       → ViewResolver (if view) or HttpMessageConverter (REST)
       → Response
```

- **Filters** run **before** `DispatcherServlet`. **Interceptors** run before/after controller (around handler execution).

### 6.2 Key Annotations

- **`@RestController`** = `@Controller` + `@ResponseBody`.
- **`@RequestBody`** / **`@ResponseBody`**: use `HttpMessageConverter` (e.g. Jackson) for (de)serialization.
- **`@ExceptionHandler`** in `@ControllerAdvice`: centralized exception → HTTP response.

### 6.3 Content Negotiation

- By **Accept** header or **format** param (e.g. `?format=json`). Configurable via `ContentNegotiationConfigurer`.

---

## 7. Spring Data JPA — N+1, Lazy Loading

### 7.1 N+1 Problem

- One query fetches **N** entities; for each, a lazy association triggers **1** extra query → **1 + N** queries.
- **Fix:**  
  - **`JOIN FETCH`** in JPQL: `SELECT e FROM Entity e JOIN FETCH e.items`  
  - **`@EntityGraph`**  
  - **`fetch = FetchType.EAGER`** (use sparingly; can cause N+1 in other directions).

### 7.2 Lazy Loading & `Transactional`

- Lazy loading typically uses **Hibernate session**. Session is usually **request-scoped** (e.g. "Open Session in View").  
- Outside a tx (or outside request), lazy access can throw **`LazyInitializationException`**.  
- Keep **read** operations that touch lazy associations **inside** a `@Transactional` (or OSIV) boundary.

### 7.3 `@Modifying` Queries

- **`@Query`** with **UPDATE/DELETE** must be annotated **`@Modifying`**.  
- Use **`clearAutomatically = true`** if you need the persistence context cleared after the update so subsequent reads see fresh data.

---

## 8. Spring Security (Filter Chain)

### 8.1 High-Level Flow

- **SecurityFilterChain:** chain of filters (auth, session, CSRF, etc.). Order matters.
- **Authentication:** typically **UsernamePasswordAuthenticationToken**; **AuthenticationManager** delegates to **Provider** (e.g. `DaoAuthenticationProvider`).
- **Authorization:** **AccessDecisionManager** + **voters**; or **method security** (`@PreAuthorize`, etc.) via AOP.

### 8.2 Method Security

- **`@PreAuthorize`**, **`@PostAuthorize`**, **`@Secured`** — enforced via **proxy**.  
- **Self-invocation** again: calls within same bean **bypass** the proxy → **no security check**. Inject self or use another bean.

### 8.3 Stateless JWT

- No session; each request carries **JWT** in **Authorization** header.  
- Filter validates JWT, builds **Authentication**, puts it in **SecurityContextHolder**.

---

## 9. Async — `@Async` & `TaskExecutor`

### 9.1 Basics

- **`@EnableAsync`** enables processing of **`@Async`** methods.  
- `@Async` methods run in a **different thread** (from `TaskExecutor`). Return type: **`void`**, **`Future<T>`**, or **`CompletableFuture<T>`**.

### 9.2 Self-Invocation

- **Same-bean call** to `@Async` method **does not** go through proxy → runs **synchronously**.  
- **Fix:** Call `@Async` method on **another bean**, or inject self and call via proxy.

### 9.3 Executor

- Custom **`TaskExecutor`** bean is used by `@Async` when provided.  
- **`ThreadPoolTaskExecutor`:** core/max pool size, queue capacity. Rejection policy when queue full (e.g. `CallerRunsPolicy`).

### 9.4 Transaction Boundary

- Async method runs in **new thread** → **new transaction** (if `@Transactional`). No shared tx with caller.

---

## 10. Configuration & Profiles

### 10.1 `application.properties` vs `application.yml`

- Same goal; YAML supports hierarchy, often used for complex config.  
- **Profile-specific:** `application-{profile}.properties` / `application-{profile}.yml`.  
- **`spring.profiles.active`** / **`SPRING_PROFILES_ACTIVE`** sets active profiles.

### 10.2 `@ConfigurationProperties`

- Binds **prefix** (e.g. `app.feature`) to a **typed** bean.  
- **`@EnableConfigurationProperties(MyProps.class)`** or **`@ConfigurationPropertiesScan`**.  
- Validation via **`@Validated`** + JSR-303 (e.g. `@NotEmpty`).

### 10.3 Externalized Config Order (Precedence, Higher Wins)

- Dev tools global properties  
- Test `@TestPropertySource`  
- Command-line args  
- `SPRING_APPLICATION_JSON` (env or system)  
- ServletConfig init params  
- ServletContext init params  
- JNDI  
- Java system properties  
- OS env vars  
- `application-{profile}.yml` (jar then file)  
- `application.yml` (jar then file)  
- `@PropertySource`  
- Default `SpringApplication.setDefaultProperties`

---

## 11. Actuator & Observability

### 11.1 Endpoints

- **`/actuator/health`** — health (DB, disk, etc.). **`/health`** can expose details (secure in prod).  
- **`/actuator/metrics`** — Micrometer metrics.  
- **`/actuator/info`** — app info.  
- **`/actuator/env`**, **`/actuator/configprops`** — config (sensitive; restrict in prod).

### 11.2 Enabling

- **`management.endpoints.web.exposure.include=health,info,metrics`**. Avoid `*` in production.  
- **`management.endpoint.health.show-details=when-authorized`** or `always` (only if secured).

### 11.3 Health Indicators

- **`HealthIndicator`** beans contribute to **`/health`**.  
- Custom: implement **`HealthIndicator`**, return **`Health.up()`** / **`Health.down()`**.

---

## 12. Testing

### 12.1 Slices

- **`@WebMvcTest`** — controller layer only; MockMvc; other beans mocked.  
- **`@DataJpaTest`** — JPA + in-memory DB; **`@Transactional`** rolls back by default.  
- **`@JsonTest`** — JSON serialization.  
- **`@SpringBootTest`** — full context (integration).

### 12.2 `@MockBean`

- Replaces/adds a bean in the context **only for that test**.  
- **`@MockBean MyService myService`** then **`Mockito.when(myService.foo()).thenReturn(...)`**.

### 12.3 Context Caching

- Same **configuration** (same set of `@ContextConfiguration` / `@SpringBootTest` config) **reuses** application context across tests.  
- **`@MockBean`** changes the context → no reuse with tests that don’t use the same mocks.  
- **`@DirtiesContext`** forces context reload after (or before) test.

---

## 13. Performance & Production

### 13.1 Lazy Initialization

- **`spring.main.lazy-initialization=true`**: beans created **on first use**.  
- **Pros:** Faster startup, lower memory if many beans unused.  
- **Cons:** First request slower; some startup bugs hide until first use.

### 13.2 Connection Pooling

- **HikariCP** default with **`spring-boot-starter-jdbc`** / **`-data-jpa`**.  
- Tune **`spring.datasource.hikari.maximum-pool-size`**, **`connection-timeout`**, etc.

### 13.3 Startup

- **`spring.jpa.defer-datasource-initialization=true`** — run **`data.sql`** after Hibernate init (e.g. when using Hibernate schema create).  
- **`spring.sql.init.mode=always`** — run **`schema.sql`** / **`data.sql`** (EMBEDDED DB default; **NEVER** for production DB typically).

---

## 14. Common Pitfalls (Quick Checklist)

| Pitfall | Why It Fails | Fix |
|--------|----------------|-----|
| **`@Transactional`** on same-bean call | Self-invocation bypasses proxy | Call via another bean or inject self |
| **`@Transactional`** on private method | Not proxied | Use public (or protected) |
| **`@Async`** on same-bean call | Same proxy issue | Call via another bean or self via proxy |
| **`@Cacheable`** on same-bean call | Same proxy issue | Same as above |
| **`@PreAuthorize`** on same-bean call | Same proxy issue | Same as above |
| **N+1** with JPA | Lazy loading in loop | `JOIN FETCH` / `@EntityGraph` |
| **`LazyInitializationException`** | Lazy access outside tx/session | Use tx or OSIV; avoid lazy access in async / batch |
| **Circular dependency** (constructor-only) | Can’t create cycle | `@Lazy` on one ctor arg, or refactor |
| **Checked exception** no rollback | Default `rollbackFor` = unchecked only | `rollbackFor = Exception.class` |
| **Wrong config in test** | Different profile or props | `@ActiveProfiles`, `@TestPropertySource` |

---

## 15. Quick Concept Checklist

- [ ] IoC vs DI, why we use them  
- [ ] Spring Boot vs Spring; auto-configuration and `@Conditional*`  
- [ ] Bean lifecycle, `BeanPostProcessor`, `@PostConstruct`  
- [ ] Scopes; prototype-in-singleton gotcha  
- [ ] `@Primary` vs `@Qualifier`  
- [ ] Circular dependency, constructor vs setter/field  
- [ ] AOP: proxy-based; self-invocation; **`@Transactional`** as AOP  
- [ ] Propagation (REQUIRED, REQUIRES_NEW, NESTED), isolation, `rollbackFor`  
- [ ] N+1, `JOIN FETCH`, `@EntityGraph`, `@Modifying`  
- [ ] Spring Security filter chain, method security, JWT  
- [ ] `@Async` + `TaskExecutor`; self-invocation; tx boundary  
- [ ] Profiles, `@ConfigurationProperties`, config precedence  
- [ ] Actuator health/metrics, custom `HealthIndicator`  
- [ ] Test slices, `@MockBean`, `@DirtiesContext`, context caching  
- [ ] Lazy init, HikariCP, startup vs runtime config  

Use this as your **conceptual map** and **pitfall checklist** when preparing for hard Spring Boot interviews. Combine with the **Q&A** and **CHEAT-SHEET** in the same folder for quick recall.
