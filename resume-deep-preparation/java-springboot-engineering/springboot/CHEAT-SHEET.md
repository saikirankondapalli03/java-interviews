# Spring Boot — Cheat Sheet

Quick reference for concepts, annotations, and gotchas. Use with **STUDY-GUIDE.md** and **INTERVIEW-Q&A.md**.

---

## Core Annotations

| Annotation | Purpose |
|------------|---------|
| `@SpringBootApplication` | `@Configuration` + `@EnableAutoConfiguration` + `@ComponentScan` |
| `@Configuration` | Class declares `@Bean`s |
| `@Bean` | Method return value registered as bean |
| `@Component` / `@Service` / `@Repository` / `@Controller` | Stereotypes; component scan |
| `@Autowired` | DI (constructor preferred over field) |
| `@Qualifier("name")` | Pick specific bean when multiple candidates |
| `@Primary` | Default bean when multiple candidates |
| `@Value("${key}")` | Inject property |
| `@ConfigurationProperties(prefix="app")` | Type-safe config binding |
| `@Profile("dev")` | Bean only when profile active |
| `@Lazy` | Initialize on first use / break circular dep |
| `@Scope("prototype")` | New instance per injection (default: singleton) |
| `@PostConstruct` / `@PreDestroy` | Lifecycle callbacks |

---

## AOP & Cross-Cutting

| Annotation | Purpose | Proxy? |
|------------|---------|--------|
| `@Transactional` | Tx boundary | Yes — **self-invocation bypasses** |
| `@Async` | Run in another thread | Yes — same |
| `@Cacheable` | Cache method result | Yes — same |
| `@PreAuthorize` | Method security | Yes — same |

**Rule:** Same-bean call → no proxy → aspect **not** applied. Use **another bean** or **inject self**.

---

## Transaction Propagation

| Value | Behavior |
|-------|----------|
| **REQUIRED** | Join existing tx; create if none (default) |
| **REQUIRES_NEW** | New tx; suspend current. Independent commit/rollback |
| **NESTED** | Savepoint in existing tx |
| **SUPPORTS** | Use tx if exists; else none |
| **NOT_SUPPORTED** | Suspend tx; run non-transactional |
| **MANDATORY** | Must have tx; else exception |
| **NEVER** | Must not have tx; else exception |

**`rollbackFor`:** Default = unchecked only. Use `rollbackFor = Exception.class` to include checked.

---

## MVC / REST

| Annotation | Purpose |
|------------|---------|
| `@RestController` | `@Controller` + `@ResponseBody` |
| `@RequestBody` | Deserialize request body (e.g. JSON) |
| `@ResponseBody` | Serialize return value |
| `@GetMapping` / `@PostMapping` etc. | HTTP method + path |
| `@PathVariable` | Path segment |
| `@RequestParam` | Query param |
| `@ExceptionHandler` | Handle exception in controller |
| `@ControllerAdvice` | Global `@ExceptionHandler` etc. |

**Flow:** Request → **DispatcherServlet** → **HandlerMapping** → **Controller** → **ViewResolver** / **HttpMessageConverter** → Response.

---

## Spring Data JPA

| Concept | Fix |
|--------|-----|
| **N+1** | `JOIN FETCH` in JPQL, `@EntityGraph` |
| **LazyInitializationException** | Use tx; or `JOIN FETCH` / eager load |
| **UPDATE/DELETE `@Query`** | Add `@Modifying`; optionally `clearAutomatically = true` |

---

## Security

- **Filter chain** → **Authentication** (e.g. `UsernamePasswordAuthenticationFilter`) → **SecurityContextHolder**.
- **Authorization:** `FilterSecurityInterceptor` (URL) or **method security** (`@PreAuthorize`).
- **Stateless JWT:** Filter validates JWT → sets **Authentication** → `sessionManagement().sessionCreationPolicy(Stateless)`.

---

## Async

- `@EnableAsync` + `@Async` on method.
- Return `void`, `Future<T>`, or `CompletableFuture<T>`.
- Custom **`TaskExecutor`** bean used by `@Async`.
- **Same-bean call** → runs **synchronously**. Use **another bean** or **inject self**.

---

## Config Precedence (higher overrides lower)

1. Command-line args  
2. `SPRING_APPLICATION_JSON` (env)  
3. Java system properties  
4. OS env vars  
5. `application-{profile}.yml` (external > jar)  
6. `application.yml` (external > jar)  
7. `@PropertySource`  
8. Defaults  

**Profiles:** `spring.profiles.active` or `SPRING_PROFILES_ACTIVE`.

---

## Actuator

| Endpoint | Purpose |
|----------|---------|
| `/actuator/health` | Health (DB, disk, etc.) |
| `/actuator/metrics` | Micrometer metrics |
| `/actuator/info` | App info |
| `/actuator/env` | Environment (restrict in prod) |

**Expose:** `management.endpoints.web.exposure.include=health,info,metrics`.  
**Custom health:** Implement **`HealthIndicator`**, expose as bean.

---

## Testing

| Annotation | Use |
|------------|-----|
| `@SpringBootTest` | Full context (integration) |
| `@WebMvcTest` | Web slice; MockMvc; mock services |
| `@DataJpaTest` | JPA + in-memory DB; `@Transactional` rollback |
| `@MockBean` | Replace/add bean with Mockito mock |
| `@DirtiesContext` | New context after (or before) test |
| `@ActiveProfiles("test")` | Activate profile |

---

## Pitfalls One-Liner

| Issue | Cause | Fix |
|-------|--------|-----|
| `@Transactional` no effect (same class) | Self-invocation | Other bean or inject self |
| `@Transactional` on private | Not proxied | Use public |
| `@Async` sync (same class) | Self-invocation | Other bean or inject self |
| N+1 | Lazy in loop | `JOIN FETCH` / `@EntityGraph` |
| `LazyInitializationException` | Lazy outside session/tx | Tx or eager fetch |
| Circular dep (constructor) | All ctor-injected | `@Lazy` on one; or refactor |
| No rollback on checked exception | Default `rollbackFor` | `rollbackFor = Exception.class` |

---

## Property Snippets

```properties
# Profiles
spring.profiles.active=dev

# Server
server.port=8080

# JPA
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.show-sql=false

# Datasource / Hikari
spring.datasource.url=jdbc:...
spring.datasource.hikari.maximum-pool-size=10

# Actuator
management.endpoints.web.exposure.include=health,info,metrics
management.endpoint.health.show-details=when-authorized

# Lazy init
spring.main.lazy-initialization=true
```

---

## Bean Lifecycle ( condensed )

```
Instantiate → Inject → Aware → BeanPostProcessor (before) → @PostConstruct
  → init → BeanPostProcessor (after) → READY
  → @PreDestroy / destroy
```

**`BeanPostProcessor`** runs for **every** bean. **`@PostConstruct`** runs **after** DI, **before** bean is ready.
