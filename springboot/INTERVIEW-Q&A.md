# Spring Boot — Hard Interview Q&A

Concise **questions** and **answers** for senior-level Spring Boot interviews. Pair with **STUDY-GUIDE.md** for deeper concepts.

---

## Core Spring & Boot

**Q: What is IoC, and how does Spring implement it?**  
**A:** Inversion of Control means the *framework* creates and wires objects instead of your code. Spring implements it via **Dependency Injection**: you declare dependencies (constructor/setter/field), and the container injects them. The container is the **ApplicationContext**, which holds **beans** and resolves dependencies.

**Q: What is `@SpringBootApplication` and what does it do under the hood?**  
**A:** It’s a composite of:
- **`@Configuration`** — class can define `@Bean`s  
- **`@EnableAutoConfiguration`** — enables auto-config based on classpath and properties  
- **`@ComponentScan`** — scans the package (and subpackages) of the annotated class for `@Component` and friends  

**Q: Explain how Spring Boot auto-configuration works.**  
**A:** `@EnableAutoConfiguration` reads **`META-INF/spring.factories`** (or Boot 3.x `AutoConfiguration.imports`) from jars. It loads **auto-configuration classes** that define `@Bean`s guarded by **`@Conditional*`** (e.g. `@ConditionalOnClass`, `@ConditionalOnMissingBean`, `@ConditionalOnProperty`). If conditions match, those beans are registered. Your explicit `@Bean`s usually override them via `@ConditionalOnMissingBean`.

**Q: What’s the difference between `@Component`, `@Service`, `@Repository`, and `@Controller`?**  
**A:** All are **stereotypes** that make the class a Spring bean. Semantically: **`@Service`** for business logic, **`@Repository`** for data access (adds exception translation for persistence), **`@Controller`** for web MVC, **`@Component`** generic. Functionally, **`@Repository`** is the only one with extra behavior (e.g. `PersistenceExceptionTranslationPostProcessor`).

**Q: Describe the bean lifecycle. Where do `@PostConstruct` and `BeanPostProcessor` fit?**  
**A:** Lifecycle: instantiate → populate properties → `Aware` callbacks → **`BeanPostProcessor.postProcessBeforeInitialization`** → **`@PostConstruct`** / `InitializingBean` / custom init → **`BeanPostProcessor.postProcessAfterInitialization`** → bean ready. Destroy: **`@PreDestroy`** / `DisposableBean` / destroy-method. `BeanPostProcessor` runs for *every* bean; `@PostConstruct` runs per bean after DI, before it’s fully “ready.”

---

## Beans, Scopes, DI

**Q: What are the default bean scopes? When would you use `prototype`?**  
**A:** Default is **singleton** (one per container). Others: **prototype** (new per injection/getBean), **request**, **session**, **application** (web). Use **prototype** when each use must be a **new instance** (e.g. stateful processor, per-request DTO factory). Watch out: injecting prototype into singleton gives **one** prototype instance forever — use `ObjectFactory`/`Provider` or `@Lookup` for a new one each time.

**Q: How do you resolve “multiple beans of type X” — `@Primary` vs `@Qualifier`?**  
**A:** **`@Primary`**: mark one candidate as default; it’s chosen when no qualifier is specified. **`@Qualifier`**: explicitly pick a bean by name or custom qualifier at the injection point. Use `@Primary` for a global default, `@Qualifier` when you need to choose per injection.

**Q: What is a circular dependency? When does Spring fail to resolve it?**  
**A:** A depends on B, B on C, C on A. Spring usually resolves it by **lazily** injecting one dependency (via setter/field) so it can create the cycle. It **fails** when the cycle is **purely constructor-based**: all depend on each other via constructors. Fix: **`@Lazy`** on one constructor argument, use setter/field for one leg, or **refactor** (extract shared logic, events).

**Q: Why prefer constructor injection over field injection?**  
**A:** **Immutability** (fields can be `final`), **testability** (easy to pass mocks without reflection), **clear required dependencies**, and **avoiding** circular dependency issues when combined with `@Lazy`. Field injection hides dependencies and makes testing harder.

---

## AOP & Proxies

**Q: How does Spring AOP work? JDK proxy vs CGLIB?**  
**A:** Spring AOP is **proxy-based**. When you call a bean method, you’re calling the **proxy**, which runs **advice** (e.g. `@Transactional`) then delegates to the **target**. If the bean **implements an interface**, Spring uses **JDK dynamic proxy** (interface-based). Otherwise **CGLIB** proxy (subclass). CGLIB can’t proxy `final` methods/classes.

**Q: Why doesn’t `@Transactional` work when I call another `@Transactional` method in the same class?**  
**A:** **Self-invocation**: the call doesn’t go through the **proxy**. The caller runs inside the same object, so the callee is **not** invoked via the proxy and **no** transaction aspect runs. Fix: move the callee to **another bean** and call that, or **inject self** and call through the proxy (or `AopContext.currentProxy()` with `exposeProxy = true`).

**Q: Does `@Transactional` work on private methods?**  
**A:** **No.** Proxies typically intercept **public** methods. Private methods are never proxied, so the transaction aspect is never applied.

---

## Transactions

**Q: Explain `REQUIRED` vs `REQUIRES_NEW` propagation.**  
**A:** **REQUIRED**: join existing transaction if present, otherwise create one. **REQUIRES_NEW**: **always** start a **new** transaction; suspend the current one if it exists. Callee commits/rollbacks **independently**. Example: outer method (REQUIRED) calls inner (REQUIRES_NEW). Inner commits; if outer later throws, outer rolls back but **inner’s commit is kept**. Use for audit logs, independent units of work.

**Q: When does `@Transactional` roll back? What about checked exceptions?**  
**A:** By default, rollback only for **unchecked** (RuntimeException and subclasses). **Checked exceptions do not** roll back. Use **`rollbackFor = Exception.class`** (or specific checked types) to roll back on those too.

**Q: What is `NESTED` propagation?**  
**A:** Run within the **existing** transaction using a **savepoint**. If the nested logic fails, roll back to the savepoint; outer tx can continue. Requires **JDBC** savepoint support. Differs from **REQUIRES_NEW**: no new physical transaction, same connection.

**Q: What does `readOnly = true` on `@Transactional` do?**  
**A:** Hints to the persistence layer (e.g. Hibernate) that the tx is **read-only**, enabling optimizations (e.g. no flush, read replicas). It does **not** enforce read-only at app level; the DB can still see writes if you do them.

---

## Spring MVC / REST

**Q: Describe the request flow through Spring MVC.**  
**A:** Request → **DispatcherServlet** → **HandlerMapping** (finds controller) → **HandlerAdapter** (invokes controller) → **Controller** returns model/view or `@ResponseBody` → **ViewResolver** (if view) or **HttpMessageConverter** (if REST) → **Response**.

**Q: What’s the difference between a Filter and an Interceptor?**  
**A:** **Filters** are Servlet API; they run **before** the **DispatcherServlet** and can act on any request. **Interceptors** are Spring MVC; they run **around** controller execution (before/after handler, after view render). Use **filters** for auth, encoding, logging at Servlet level; **interceptors** for request/response handling tied to Spring MVC.

**Q: How do you handle exceptions globally in a REST API?**  
**A:** Use **`@ControllerAdvice`** (or `@RestControllerAdvice`) with **`@ExceptionHandler`**. Each handler method maps an exception type to a response (status, body). Single place for error DTOs and logging.

---

## Spring Data JPA

**Q: What is the N+1 problem? How do you fix it?**  
**A:** One query loads N entities; each has a **lazy** association. Accessing that association in a loop causes **N** extra queries → **1 + N** total. Fix: **`JOIN FETCH`** in JPQL (e.g. `SELECT e FROM Order e JOIN FETCH e.items`), or **`@EntityGraph`**, so associations are loaded in the **initial** query.

**Q: What is `LazyInitializationException`? When does it occur?**  
**A:** Happens when you access a **lazy** association **outside** the persistence context (e.g. after the session is closed or outside a `@Transactional` method). Fix: access lazy data **inside** a transactional boundary, or use **`JOIN FETCH`** / **`@EntityGraph`** to load it eagerly in one query.

**Q: When do you use `@Modifying` with `@Query`?**  
**A:** For **UPDATE** or **DELETE** JPQL/Native queries. Without `@Modifying`, Spring Data assumes a **SELECT**. Use **`clearAutomatically = true`** if you want the persistence context cleared after the update so subsequent reads see fresh DB state.

---

## Spring Security

**Q: How does Spring Security authenticate a request?**  
**A:** A **filter** (e.g. `UsernamePasswordAuthenticationFilter`) extracts credentials, builds **Authentication** (e.g. `UsernamePasswordAuthenticationToken`), and calls **`AuthenticationManager`**. The manager uses **AuthenticationProvider**s (e.g. `DaoAuthenticationProvider`) to validate and return a fully populated **Authentication**. It’s stored in **`SecurityContextHolder`** (typically thread-local). Later filters (e.g. **`FilterSecurityInterceptor`**) use it for **authorization**.

**Q: Why doesn’t `@PreAuthorize` work when I call the method from another method in the same class?**  
**A:** Same **self-invocation** issue as `@Transactional` and `@Async`: the call doesn’t go through the **proxy**, so the security aspect never runs. Call the method on **another bean** or through the **injected self** (proxy).

**Q: How would you implement stateless JWT-based auth in Spring Security?**  
**A:** Add a **filter** that runs before **UsernamePasswordAuthenticationFilter**. It reads **JWT** from `Authorization` header, validates it (signature, expiry), builds **Authentication** (e.g. `UsernamePasswordAuthenticationToken`), and sets it in **`SecurityContextHolder`**. Use **stateless** session (e.g. `sessionManagement().sessionCreationPolicy(Stateless)`). No server-side session; each request is validated via JWT.

---

## Async

**Q: Why doesn’t `@Async` work when I call the async method from the same class?**  
**A:** **Self-invocation**: the call doesn’t go through the **proxy**, so the `@Async` aspect (which runs the method in another thread) is never applied. The method runs **synchronously** in the same thread. Fix: call the `@Async` method on **another bean**, or **inject self** and call via the proxy.

**Q: What thread runs `@Async` methods? How do you customize it?**  
**A:** By default, a **`SimpleAsyncTaskExecutor`** (new thread per task). Define a **`TaskExecutor`** bean (e.g. **`ThreadPoolTaskExecutor`** with core/max pool, queue) and it will be used for `@Async`. Configurable via `@Async("executorBeanName")` if you have multiple executors.

**Q: Are `@Async` methods transactional?**  
**A:** They run in a **different thread**. If the async method is `@Transactional`, it runs in its **own** transaction. There is **no** shared transaction with the caller. The caller’s tx commits independently of the async work.

---

## Configuration & Profiles

**Q: How do you externalize configuration? What’s the order of precedence?**  
**A:** **`application.properties`** / **`application.yml`**, **profile-specific** files (`application-{profile}.yml`), **env vars**, **system properties**, **command-line args**. **Higher** precedence overrides lower (e.g. env over props). **`@ConfigurationProperties`** binds a prefix to a bean; **`@Value`** injects single properties.

**Q: What are Spring profiles? How do you activate them?**  
**A:** Profiles **partition** config and beans (e.g. `dev`, `prod`). **`@Profile("dev")`** registers beans only when that profile is active. Activate via **`spring.profiles.active`** (properties/env) or **`SPRING_PROFILES_ACTIVE`**, or **`--spring.profiles.active=dev`** on command line.

**Q: How does `@ConfigurationProperties` work?**  
**A:** Binds **`application.properties`** / **`application.yml`** entries under a **prefix** (e.g. `app.feature`) to a **POJO** with matching field names. Supports nested objects, lists, type conversion. Use **`@EnableConfigurationProperties(MyProps.class)`** or **`@ConfigurationPropertiesScan`**. Can add **`@Validated`** and JSR-303 for validation.

---

## Actuator & Observability

**Q: What is Spring Boot Actuator? Name important endpoints.**  
**A:** Adds **production-ready** endpoints for **monitoring** and **operations**. Examples: **`/actuator/health`** (liveness/readiness), **`/actuator/metrics`** (Micrometer metrics), **`/actuator/info`**, **`/actuator/env`**, **`/actuator/configprops`**. Expose via **`management.endpoints.web.exposure.include`**; restrict in prod (avoid `*`).

**Q: How do you add a custom health indicator?**  
**A:** Implement **`HealthIndicator`**, define a **`health()`** method returning **`Health.up()`** / **`Health.down()`** (with optional details). Register as a **bean**. It’s automatically picked up and included in **`/actuator/health`**.

---

## Testing

**Q: What is `@WebMvcTest`? What does it load?**  
**A:** **Slice** test that loads **only** the web layer: MVC config, **`@Controller`**/`@ControllerAdvice` beans. **No** full context, no **`@Service`**/`@Repository`** (unless mocked). Use **`@MockBean`** to replace or add beans. Good for **controller unit tests** with **MockMvc**.

**Q: What is `@MockBean`? How does it affect the application context?**  
**A:** **`@MockBean`** adds or **replaces** a bean in the **test** context with a **Mockito** mock. Used in **slice** or **`@SpringBootTest`** when you need to mock a dependency. **Important:** it **changes** the context definition, so **context caching** may not reuse that context for other tests. Use **`@DirtiesContext`** if you need a fresh context.

**Q: What is context caching in Spring tests? When would you use `@DirtiesContext`?**  
**A:** Spring **reuses** the same **ApplicationContext** across tests when the **configuration** is identical (same config classes, profile, etc.). Speeds up suites. **`@DirtiesContext`** marks the context as dirty **after** (or **before**) the test, so the **next** test gets a **new** context. Use when a test **modifies** shared state (e.g. **`@MockBean`**, static config, embedded DB) and you want isolation.

---

## Performance & Production

**Q: What is lazy initialization in Spring Boot? Trade-offs?**  
**A:** **`spring.main.lazy-initialization=true`**: beans are created **on first use** instead of at startup. **Pros:** faster startup, lower memory if many beans unused. **Cons:** first request can be slower; some misconfigs only show up at runtime.

**Q: How do you tune the DB connection pool in Spring Boot?**  
**A:** **HikariCP** is the default with **`spring-boot-starter-jdbc`** / **`-data-jpa`**. Use **`spring.datasource.hikari.*`**: **`maximum-pool-size`**, **`minimum-idle`**, **`connection-timeout`**, **`idle-timeout`**. Size pool based on DB capacity and concurrency; avoid oversized pools.

---

## Design & Best Practices

**Q: How would you structure a layered Spring Boot application?**  
**A:** **Controller** (REST/MVC) → **Service** (business logic, `@Transactional`) → **Repository** (data access). Keep **entities** in persistence layer; use **DTOs** for API. **Config** (`@Configuration`), **security**, **aspects** in dedicated packages. Avoid business logic in controllers and persistence logic in services.

**Q: How do you ensure a Spring Boot app is production-ready?**  
**A:** **Health** and **metrics** (Actuator); **security** on Actuator and app endpoints; **externalized config** (profiles, env); **connection pooling** tuned; **logging** (levels, structure); **graceful shutdown**; **distributed tracing** if microservices. Use **`@Profile("prod")`** for prod-only beans.

---

Use this **Q&A** for quick rehearsal. For deeper understanding, refer to **STUDY-GUIDE.md** and **CHEAT-SHEET.md**.
