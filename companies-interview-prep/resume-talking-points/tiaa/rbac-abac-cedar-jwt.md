# RBAC + ABAC with Cedar Policy Engine & JWT Integration - Complete Implementation Guide

## 🎯 Overview: The Challenge

**Problem:** We need to implement fine-grained authorization in a microservices architecture that combines role-based access (RBAC) and attribute-based access (ABAC) controls.

**Requirements:**
- Support both RBAC (who can do what) and ABAC (when/under what conditions)
- Centralized policy management (like AWS IAM policies)
- JWT-based authentication with authorization claims
- Dynamic policy evaluation based on context
- Scalable across multiple microservices

**Solution:** Cedar policy engine with JWT integration, implementing PEP (Policy Enforcement Point) and PDP (Policy Decision Point) pattern.

---

## 🏗️ Architecture: How It Works

```
┌─────────────────────────────────────────────────────────────────┐
│                    REQUEST FLOW                                  │
│                                                                 │
│  1. Client sends request with JWT token                         │
│     JWT Payload:                                                │
│     {                                                           │
│       "sub": "user:12345",        // Principal                  │
│       "roles": ["teller"],         // RBAC roles                │
│       "department": "retail",      // ABAC attributes           │
│       "appId": "payment-service",  // Policy selection          │
│       "env": "prod"                // Environment               │
│     }                                                           │
└────────────────────┬────────────────────────────────────────────┘
                     ↓
┌─────────────────────────────────────────────────────────────────┐
│              PEP (Policy Enforcement Point)                      │
│              API Gateway / Service Filter                       │
│                                                                 │
│  Phase 1: Extract JWT                                           │
│    ├─ Validate signature & expiration                          │
│    ├─ Extract claims: sub, roles, attributes                   │
│    └─ Build principal from JWT                                  │
│                                                                 │
│  Phase 2: Build Authorization Request                          │
│    ├─ Principal: from JWT "sub" claim                          │
│    ├─ Action: from HTTP method + endpoint                      │
│    ├─ Resource: from URL path or request body                  │
│    └─ Context: JWT claims + request metadata                  │
│                                                                 │
│  Phase 3: Call PDP                                              │
│    └─ Send authorization request to PDP                        │
└────────────────────┬────────────────────────────────────────────┘
                     ↓
┌─────────────────────────────────────────────────────────────────┐
│              PDP (Policy Decision Point)                        │
│              Cedar Policy Engine                                │
│                                                                 │
│  Phase 1: Policy Selection                                      │
│    ├─ Load policies for appId + env from JWT                   │
│    └─ Select relevant policies for action/resource             │
│                                                                 │
│  Phase 2: Policy Evaluation                                     │
│    ├─ Evaluate RBAC: principal in Role::"teller"?              │
│    ├─ Evaluate ABAC: resource.balance > 1000?                 │
│    ├─ Evaluate ABAC: principal.department == "retail"?         │
│    └─ Evaluate ABAC: context.timeOfDay >= 9?                  │
│                                                                 │
│  Phase 3: Decision                                              │
│    └─ Return: ALLOW or DENY                                    │
└────────────────────┬────────────────────────────────────────────┘
                     ↓
┌─────────────────────────────────────────────────────────────────┐
│              PEP: Enforce Decision                              │
│                                                                 │
│  If ALLOW:                                                      │
│    └─ Forward request to microservice ✅                       │
│                                                                 │
│  If DENY:                                                       │
│    └─ Return 403 Forbidden ❌                                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## 📋 Core Concepts

### What is Cedar?

Cedar is a policy language designed for writing authorization policies and making authorization decisions. It follows the **PARC model**:
- **Principal**: Who is making the request (user, role, service)
- **Action**: What operation they want to perform
- **Resource**: What they want to access
- **Context**: Additional attributes for dynamic evaluation

### RBAC vs ABAC

| Aspect | RBAC | ABAC |
|--------|------|------|
| **Basis** | User's role/identity | Attributes/context |
| **Evaluation** | "Is user in admin role?" | "Is balance > 1000?" |
| **Flexibility** | Static (role assignment) | Dynamic (context-dependent) |
| **Example** | `principal in Role::"admin"` | `resource.balance > 1000` |
| **Use Case** | "Only admins can delete" | "Can transfer if balance > 1000" |

### Combined RBAC + ABAC

Cedar allows combining both models in a single policy:

```cedar
permit(
    principal in Role::"teller",  // RBAC: Must be teller role
    action == Action::"withdraw",
    resource == Resource::"account"
) when {
    resource.balance > 1000 &&  // ABAC: Balance check
    principal.department == "retail" &&  // ABAC: Department check
    context.timeOfDay >= 9 && context.timeOfDay <= 17  // ABAC: Time check
};
```

**Key Insight:** RBAC answers "who can do what", ABAC answers "when and under what conditions". Together they provide fine-grained access control.

---

## 🔑 PEP vs PDP: The Critical Distinction

### PEP (Policy Enforcement Point)
**Where the decision is APPLIED**

**Responsibilities:**
- Intercepts requests at API Gateway or microservice level
- Extracts JWT token and validates it
- Builds authorization request (Principal, Action, Resource, Context)
- Calls PDP for decision
- Enforces decision (allows or denies request)

**Location:** API Gateway, Service Filter, Interceptor

**In Business Terms:** The "entitlement client" or "authorization middleware"

### PDP (Policy Decision Point)
**Where the decision is MADE**

**Responsibilities:**
- Centralized authorization service
- Loads relevant Cedar policies
- Evaluates policies against authorization request
- Returns Allow/Deny decision
- Can be centralized service or embedded SDK

**Location:** Separate authorization service or embedded library

**In Business Terms:** The "policy engine" or "authorization service"

**One-Line Summary:** PEP enforces, PDP decides. Request hits PEP → PEP asks PDP → PDP evaluates policies → PEP allows or denies based on PDP's answer.

---

## 🔄 JWT Integration: How It Works

### JWT Structure

**JWT = Who they are + Roles + Attributes + Context**

```json
{
  "sub": "user:12345",              // Principal identifier
  "roles": ["teller", "retail"],     // RBAC: User roles
  "department": "retail",            // ABAC: User attributes
  "region": "us-east",               // ABAC: User attributes
  "appId": "payment-service",        // Context: Policy selection
  "env": "prod",                     // Context: Environment
  "iat": 1516239022,                 // Issued at
  "exp": 1516242622                  // Expiration
}
```

### JWT Claims Mapping

| JWT Claim | Purpose | Used For |
|-----------|---------|----------|
| `sub` | Principal identifier | Building Principal in authorization request |
| `roles` | RBAC roles | Policy evaluation: `principal in Role::"teller"` |
| `department`, `region`, etc. | ABAC attributes | Policy evaluation: `principal.department == "retail"` |
| `appId` | Policy selection | Which app's policies to load |
| `env` | Policy selection | Which environment (dev/stg/prod) |

### Critical Understanding: JWT Doesn't Do RBAC/ABAC

**Common Misconception:** "JWT implements RBAC/ABAC"

**Reality:** 
- **JWT = Carrier of identity and attributes** (who they are, what roles they have, what attributes they possess)
- **Cedar Policies = The logic that implements RBAC/ABAC** (the rules that use JWT data to decide allow/deny)

**Analogy:** JWT is like an ID card with your name, role, and department. Cedar policies are the security rules that say "Only tellers from retail department can withdraw during business hours."

---

## 📋 Complete Implementation Flow

### Step 1: Authentication Service Issues JWT

```java
// Auth Service - After successful login
public String generateJwt(User user) {
    Map<String, Object> claims = new HashMap<>();
    
    // Principal
    claims.put("sub", "user:" + user.getId());
    
    // RBAC: Roles
    claims.put("roles", user.getRoles());  // ["teller", "retail"]
    
    // ABAC: User attributes
    claims.put("department", user.getDepartment());  // "retail"
    claims.put("region", user.getRegion());  // "us-east"
    
    // Context: Policy selection
    claims.put("appId", "payment-service");
    claims.put("env", "prod");
    
    // Standard claims
    claims.put("iat", System.currentTimeMillis() / 1000);
    claims.put("exp", (System.currentTimeMillis() / 1000) + 3600);
    
    return Jwts.builder()
        .setClaims(claims)
        .signWith(SignatureAlgorithm.HS256, secretKey)
        .compact();
}
```

**Key Points:**
- Auth service populates JWT with all necessary claims
- Roles come from user's role assignments (RBAC)
- Attributes come from user profile (ABAC)
- Context fields (`appId`, `env`) determine which policies to load

---

### Step 2: PEP Implementation (Policy Enforcement Point)

```java
@Component
public class AuthorizationFilter implements Filter {
    
    private final CedarEngine cedarEngine;  // PDP
    private final JwtValidator jwtValidator;
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, 
                        FilterChain chain) throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        
        // Step 1: Extract and validate JWT
        String jwtToken = extractJwt(httpRequest);
        Claims jwtClaims = jwtValidator.validate(jwtToken);
        
        // Step 2: Build Principal from JWT
        Principal principal = Principal.of(jwtClaims.getSubject());
        
        // Step 3: Extract Action from request
        Action action = Action.of(
            httpRequest.getMethod().toLowerCase(),  // "post"
            extractActionFromPath(httpRequest.getRequestURI())  // "transfer"
        );
        
        // Step 4: Extract Resource from request
        Resource resource = Resource.of(
            "Account",
            extractAccountId(httpRequest)  // "account::12345"
        );
        
        // Step 5: Build Context from JWT + Request
        Context context = Context.empty()
            .with("roles", jwtClaims.get("roles", List.class))
            .with("department", jwtClaims.get("department", String.class))
            .with("timeOfDay", getCurrentHour())
            .with("ipAddress", httpRequest.getRemoteAddr());
        
        // Step 6: Load resource attributes for ABAC (if needed)
        // Example: Fetch account balance from database
        Account account = accountService.getAccount(extractAccountId(httpRequest));
        context = context.with("balance", account.getBalance());
        
        // Step 7: Build Authorization Request
        AuthorizationRequest authRequest = AuthorizationRequest.builder()
            .principal(principal)
            .action(action)
            .resource(resource)
            .context(context)
            .build();
        
        // Step 8: Call PDP (Policy Decision Point)
        AuthorizationDecision decision = cedarEngine.isAuthorized(authRequest);
        
        // Step 9: Enforce Decision
        if (decision.isAllowed()) {
            chain.doFilter(request, response);  // ✅ Allow request
        } else {
            HttpServletResponse httpResponse = (HttpServletResponse) response;
            httpResponse.setStatus(403);
            httpResponse.getWriter().write("Forbidden: " + decision.getReason());
            return;  // ❌ Deny request
        }
    }
    
    private String extractJwt(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        throw new UnauthorizedException("Missing JWT token");
    }
}
```

**Key Points:**
- PEP extracts JWT and validates it
- Builds authorization request from JWT claims + HTTP request
- Calls PDP for decision
- Enforces decision (allows or blocks request)

---

### Step 3: PDP Implementation (Policy Decision Point)

```java
@Service
public class CedarEngine {
    
    private final PolicyStore policyStore;
    private final CedarEvaluator evaluator;
    
    /**
     * Evaluates authorization request against Cedar policies.
     * This is the PDP (Policy Decision Point).
     */
    public AuthorizationDecision isAuthorized(AuthorizationRequest request) {
        
        // Step 1: Policy Selection
        // Load policies based on appId and env from context
        String appId = request.getContext().get("appId", String.class);
        String env = request.getContext().get("env", String.class);
        
        List<Policy> policies = policyStore.loadPolicies(appId, env);
        
        // Step 2: Policy Evaluation
        // Cedar evaluates policies in order
        for (Policy policy : policies) {
            EvaluationResult result = evaluator.evaluate(
                policy,
                request.getPrincipal(),
                request.getAction(),
                request.getResource(),
                request.getContext()
            );
            
            // Step 3: Decision Logic
            if (result.isExplicitDeny()) {
                return AuthorizationDecision.deny(
                    "Policy " + policy.getId() + " explicitly denies access"
                );
            }
            
            if (result.isExplicitAllow()) {
                return AuthorizationDecision.allow(
                    "Policy " + policy.getId() + " allows access"
                );
            }
        }
        
        // Default: Deny if no policy allows
        return AuthorizationDecision.deny("No policy allows this access");
    }
}
```

**Key Points:**
- PDP loads relevant policies
- Evaluates each policy against authorization request
- Returns explicit Allow or Deny
- Default deny if no policy allows

---

### Step 4: Policy Storage & Loading

```java
@Service
public class PolicyStore {
    
    /**
     * Policy storage structure:
     * 
     * payment-service/
     *   ├── dev/
     *   │   └── policy.cedar
     *   ├── stg/
     *   │   └── policy.cedar
     *   └── prod/
     *       └── policy.cedar
     */
    public List<Policy> loadPolicies(String appId, String env) {
        String policyPath = String.format("%s/%s/policy.cedar", appId, env);
        
        // Load from file system, database, or configuration service
        String policyContent = loadPolicyFile(policyPath);
        
        // Parse Cedar policy syntax
        return CedarParser.parse(policyContent);
    }
}
```

---

## 🔄 GitHub-Based Policy Retrieval: TIAA Implementation Pattern

### Scenario: Policies Stored in GitHub Repository

**TIAA Implementation Structure:**
```
GitHub Repository: tiaa-cedar-policies/
├── a64064/                    (cmdbId)
│   ├── dev/
│   │   └── dev.cedar
│   ├── uat/
│   │   └── uat.cedar
│   └── prod/
│       └── prod.cedar
├── a64065/
│   ├── dev/
│   │   └── dev.cedar
│   └── prod/
│       └── prod.cedar
└── ...
```

**Key Question:** How are these policies retrieved from GitHub and used by the PDP?

### Approach 1: GitHub API with Caching (Most Common)

**How It Works:**
1. PDP needs policies → Calls GitHub API to fetch policy file
2. Policies cached in memory/Redis for performance
3. Cache refreshed periodically or via webhooks

```java
@Service
public class GitHubPolicyStore {
    
    private final GitHubClient githubClient;
    private final Cache<String, List<Policy>> policyCache;
    private final String repoOwner = "tiaa-org";
    private final String repoName = "cedar-policies";
    private final String branch = "main";
    
    /**
     * Load policies from GitHub for specific cmdbId and environment
     * 
     * @param cmdbId Application CMDB ID (e.g., "a64064")
     * @param env Environment (dev, uat, prod)
     * @return List of parsed Cedar policies
     */
    public List<Policy> loadPolicies(String cmdbId, String env) {
        // Build cache key
        String cacheKey = String.format("%s:%s", cmdbId, env);
        
        // Check cache first
        List<Policy> cached = policyCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        
        // Build GitHub file path
        String filePath = String.format("%s/%s/%s.cedar", cmdbId, env, env);
        
        try {
            // Fetch file content from GitHub API
            String policyContent = githubClient.getFileContent(
                repoOwner,
                repoName,
                branch,
                filePath
            );
            
            // Parse Cedar policies
            List<Policy> policies = CedarParser.parse(policyContent);
            
            // Cache for 5 minutes
            policyCache.put(cacheKey, policies, Duration.ofMinutes(5));
            
            return policies;
            
        } catch (FileNotFoundException e) {
            log.warn("Policy file not found: {} for cmdbId: {}, env: {}", 
                filePath, cmdbId, env);
            return Collections.emptyList();
        }
    }
}

// GitHub API Client Implementation
@Component
public class GitHubClient {
    
    private final RestTemplate restTemplate;
    private final String githubToken; // GitHub Personal Access Token
    
    public String getFileContent(String owner, String repo, String branch, String path) {
        String url = String.format(
            "https://api.github.com/repos/%s/%s/contents/%s?ref=%s",
            owner, repo, path, branch
        );
        
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + githubToken);
        headers.set("Accept", "application/vnd.github.v3+json");
        
        HttpEntity<String> entity = new HttpEntity<>(headers);
        
        ResponseEntity<GitHubFileResponse> response = restTemplate.exchange(
            url, HttpMethod.GET, entity, GitHubFileResponse.class
        );
        
        GitHubFileResponse fileResponse = response.getBody();
        
        // Decode base64 content
        byte[] decodedBytes = Base64.getDecoder().decode(fileResponse.getContent());
        return new String(decodedBytes);
    }
}

// Response DTO
@Data
class GitHubFileResponse {
    private String name;
    private String path;
    private String content;  // Base64 encoded
    private String encoding;
    private String sha;
}
```

**Key Points:**
- **GitHub API:** Uses REST API to fetch file content
- **Caching:** Policies cached to avoid API rate limits
- **Path Construction:** `{cmdbId}/{env}/{env}.cedar`
- **Error Handling:** Returns empty list if policy not found

---

### Approach 2: Webhook-Based Cache Refresh (Real-time Updates)

**How It Works:**
1. GitHub webhook triggers on policy file changes
2. Webhook handler invalidates cache for that specific policy
3. Next request fetches fresh policy from GitHub

```java
@RestController
@RequestMapping("/webhooks")
public class GitHubWebhookController {
    
    private final GitHubPolicyStore policyStore;
    
    /**
     * GitHub webhook endpoint for policy changes
     * Configured in GitHub: Settings → Webhooks → Add webhook
     * Payload URL: https://your-service.com/webhooks/github/policy-update
     */
    @PostMapping("/github/policy-update")
    public ResponseEntity<?> handlePolicyUpdate(@RequestBody GitHubWebhookPayload payload) {
        
        // Verify webhook signature (security)
        if (!verifyWebhookSignature(payload)) {
            return ResponseEntity.status(401).build();
        }
        
        // Extract changed files
        for (GitHubCommit commit : payload.getCommits()) {
            for (String filePath : commit.getModified()) {
                // Parse cmdbId and env from path: "a64064/prod/prod.cedar"
                PolicyPathInfo pathInfo = parsePolicyPath(filePath);
                
                if (pathInfo != null) {
                    // Invalidate cache for this policy
                    policyStore.invalidateCache(pathInfo.getCmdbId(), pathInfo.getEnv());
                    
                    log.info("Cache invalidated for cmdbId: {}, env: {}", 
                        pathInfo.getCmdbId(), pathInfo.getEnv());
                }
            }
        }
        
        return ResponseEntity.ok().build();
    }
    
    private PolicyPathInfo parsePolicyPath(String filePath) {
        // Pattern: "{cmdbId}/{env}/{env}.cedar"
        Pattern pattern = Pattern.compile("([^/]+)/([^/]+)/([^/]+)\\.cedar");
        Matcher matcher = pattern.matcher(filePath);
        
        if (matcher.matches()) {
            String cmdbId = matcher.group(1);
            String env = matcher.group(2);
            return new PolicyPathInfo(cmdbId, env);
        }
        
        return null;
    }
}

@Data
class PolicyPathInfo {
    private final String cmdbId;
    private final String env;
}
```

**Benefits:**
- **Real-time updates:** Policies refreshed immediately on GitHub changes
- **Efficient:** Only invalidates affected policies
- **No polling:** Event-driven cache refresh

---

### Approach 3: CI/CD Pipeline Deployment (Production Pattern)

**How It Works:**
1. Policy changes committed to GitHub
2. CI/CD pipeline (GitHub Actions/Jenkins) triggered
3. Pipeline validates policies, then deploys to policy store (S3/Database/Cache)
4. PDP reads from deployed policy store (not directly from GitHub)

```yaml
# .github/workflows/deploy-policies.yml
name: Deploy Cedar Policies

on:
  push:
    paths:
      - '**/*.cedar'

jobs:
  deploy:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      
      - name: Validate Cedar Policies
        run: |
          # Validate syntax
          for file in $(find . -name "*.cedar"); do
            cedar validate $file
          done
      
      - name: Deploy to S3
        run: |
          aws s3 sync . s3://tiaa-cedar-policies/ \
            --exclude "*" \
            --include "*.cedar"
      
      - name: Invalidate CloudFront Cache
        run: |
          aws cloudfront create-invalidation \
            --distribution-id $CLOUDFRONT_DIST_ID \
            --paths "/*"
```

**PDP Implementation:**
```java
@Service
public class S3PolicyStore {
    
    private final S3Client s3Client;
    private final String bucketName = "tiaa-cedar-policies";
    
    public List<Policy> loadPolicies(String cmdbId, String env) {
        String s3Key = String.format("%s/%s/%s.cedar", cmdbId, env, env);
        
        // Fetch from S3 (with CloudFront CDN for performance)
        String policyContent = s3Client.getObjectAsString(bucketName, s3Key);
        
        return CedarParser.parse(policyContent);
    }
}
```

**Benefits:**
- **Separation:** GitHub = source of truth, S3 = deployment target
- **Validation:** CI/CD validates policies before deployment
- **Performance:** S3/CloudFront faster than GitHub API
- **Reliability:** No dependency on GitHub API availability

---

### Approach 4: Git Clone + Local File System (Hybrid Approach)

**How It Works:**
1. Service starts → Clones GitHub repository locally
2. Background job periodically pulls latest changes
3. PDP reads from local file system

```java
@Service
public class GitClonePolicyStore {
    
    private final File localRepoPath;
    private final ScheduledExecutorService gitPullScheduler;
    private final String repoUrl = "https://github.com/tiaa-org/cedar-policies.git";
    
    @PostConstruct
    public void initialize() {
        // Clone repository on startup
        cloneRepository();
        
        // Pull changes every 5 minutes
        gitPullScheduler.scheduleAtFixedRate(
            this::pullLatestChanges,
            5, 5, TimeUnit.MINUTES
        );
    }
    
    public List<Policy> loadPolicies(String cmdbId, String env) {
        String filePath = String.format("%s/%s/%s/%s.cedar", 
            localRepoPath.getAbsolutePath(), cmdbId, env, env);
        
        try {
            String policyContent = Files.readString(Paths.get(filePath));
            return CedarParser.parse(policyContent);
        } catch (IOException e) {
            log.error("Failed to read policy file: {}", filePath, e);
            return Collections.emptyList();
        }
    }
    
    private void cloneRepository() {
        try {
            Git.cloneRepository()
                .setURI(repoUrl)
                .setDirectory(localRepoPath)
                .setCredentialsProvider(new UsernamePasswordCredentialsProvider(
                    githubUsername, githubToken))
                .call();
        } catch (GitAPIException e) {
            log.error("Failed to clone repository", e);
            throw new RuntimeException("Policy repository initialization failed", e);
        }
    }
    
    private void pullLatestChanges() {
        try {
            Git git = Git.open(localRepoPath);
            git.pull()
                .setCredentialsProvider(new UsernamePasswordCredentialsProvider(
                    githubUsername, githubToken))
                .call();
            log.info("Pulled latest policy changes from GitHub");
        } catch (Exception e) {
            log.error("Failed to pull latest changes", e);
        }
    }
}
```

**Benefits:**
- **Fast reads:** Local file system access
- **Offline capability:** Works even if GitHub API is down
- **Simple:** No API rate limits

---

### Complete Integration: PDP with GitHub Policy Store

```java
@Service
public class CedarEngine {
    
    private final GitHubPolicyStore policyStore;
    
    /**
     * Evaluates authorization request against Cedar policies.
     * Policies are loaded from GitHub based on cmdbId and env.
     */
    public AuthorizationDecision isAuthorized(AuthorizationRequest request) {
        
        // Extract cmdbId and env from JWT context
        String cmdbId = request.getContext().get("cmdbId", String.class);
        String env = request.getContext().get("env", String.class);
        
        // Load policies from GitHub for this cmdbId and env
        List<Policy> policies = policyStore.loadPolicies(cmdbId, env);
        
        if (policies.isEmpty()) {
            log.warn("No policies found for cmdbId: {}, env: {}", cmdbId, env);
            return AuthorizationDecision.deny("No policies configured");
        }
        
        // Evaluate policies
        for (Policy policy : policies) {
            EvaluationResult result = evaluator.evaluate(
                policy,
                request.getPrincipal(),
                request.getAction(),
                request.getResource(),
                request.getContext()
            );
            
            if (result.isExplicitDeny()) {
                return AuthorizationDecision.deny(
                    "Policy " + policy.getId() + " explicitly denies access"
                );
            }
            
            if (result.isExplicitAllow()) {
                return AuthorizationDecision.allow(
                    "Policy " + policy.getId() + " allows access"
                );
            }
        }
        
        return AuthorizationDecision.deny("No policy allows this access");
    }
}
```

**JWT Structure for TIAA:**
```json
{
  "sub": "user:12345",
  "roles": ["teller"],
  "department": "retail",
  "cmdbId": "a64064",      // ← Used to select policy folder
  "env": "prod",            // ← Used to select environment folder
  "iat": 1516239022,
  "exp": 1516242622
}
```

---

### Summary: GitHub Policy Retrieval Approaches

| Approach | Pros | Cons | Use Case |
|----------|------|------|----------|
| **GitHub API + Cache** | Simple, no infrastructure | Rate limits, API dependency | Small scale, development |
| **Webhook + Cache** | Real-time updates | Webhook setup complexity | Production with frequent changes |
| **CI/CD + S3** | Fast, reliable, validated | More moving parts | Production at scale |
| **Git Clone + Local** | Fast reads, offline capable | Disk space, sync complexity | On-premise deployments |

**TIAA Pattern Recommendation:**
- **Development:** GitHub API + Cache
- **Production:** CI/CD + S3 (or similar object store)
- **Hybrid:** Webhook invalidation + GitHub API for cache refresh

**Key Implementation Details:**
1. **Path Construction:** `{cmdbId}/{env}/{env}.cedar`
2. **Caching:** Essential to avoid GitHub API rate limits
3. **Error Handling:** Graceful fallback if policy not found
4. **Security:** GitHub token authentication, webhook signature verification
5. **Monitoring:** Track cache hit rates, API call volumes, policy load failures

---

## 🔄 Cache Refresh Mechanisms: When Policies Change in GitHub

### The Problem: Policy Update → Cache Refresh

**Question:** App team changes policy in GitHub. Does cache refresh automatically? Is there a trigger?

**Answer:** Depends on implementation. Here are the common approaches:

---

### Approach 1: Manual Cache Refresh (TTL-Based)

**How It Works:**
```
App Team commits policy change to GitHub
    ↓
GitHub repository updated ✅
    ↓
Cache still has old policy (TTL: 5 minutes)
    ↓
After 5 minutes → Cache expires
    ↓
Next request → Fetches fresh policy from GitHub API
    ↓
Cache updated with new policy ✅
```

**Timeline:**
- **Policy changed:** T+0 seconds
- **Cache refresh:** T+5 minutes (when TTL expires)
- **Delay:** Up to 5 minutes

**Pros:**
- Simple implementation
- No additional infrastructure

**Cons:**
- **Delayed refresh:** Up to cache TTL duration
- Not real-time

**Use Case:** Development/testing environments

---

### Approach 2: Webhook-Based Real-Time Refresh ⭐ (Recommended)

**How It Works:**
```
App Team commits policy change to GitHub
    ↓
GitHub automatically sends Webhook POST request
    ↓
Your Service receives webhook:
   POST /webhooks/github/policy-update
   Body: {
     "commits": [{
       "modified": ["a64064/prod/prod.cedar"],
       "sha": "abc123..."
     }]
   }
    ↓
Webhook Handler:
   1. Parses changed file path
   2. Extracts cmdbId: "a64064", env: "prod"
   3. Invalidates cache for that specific policy
    ↓
Cache entry removed immediately ✅
    ↓
Next request → Fetches fresh policy from GitHub API
    ↓
New policy loaded ✅ (Real-time!)
```

**Timeline:**
- **Policy changed:** T+0 seconds
- **Webhook received:** T+1-2 seconds
- **Cache invalidated:** T+1-2 seconds
- **Next request:** Fetches fresh policy immediately
- **Total delay:** ~1-2 seconds (real-time!)

**Implementation:**
```java
@RestController
@RequestMapping("/webhooks")
public class GitHubWebhookController {
    
    private final GitHubPolicyStore policyStore;
    
    @PostMapping("/github/policy-update")
    public ResponseEntity<?> handlePolicyUpdate(@RequestBody GitHubWebhookPayload payload) {
        
        // Verify webhook signature (security)
        if (!verifyWebhookSignature(payload)) {
            return ResponseEntity.status(401).build();
        }
        
        // Extract changed files from commits
        for (GitHubCommit commit : payload.getCommits()) {
            for (String filePath : commit.getModified()) {
                // Parse: "a64064/prod/prod.cedar" → cmdbId="a64064", env="prod"
                PolicyPathInfo pathInfo = parsePolicyPath(filePath);
                
                if (pathInfo != null) {
                    // Invalidate cache immediately
                    policyStore.invalidateCache(pathInfo.getCmdbId(), pathInfo.getEnv());
                    
                    log.info("Cache invalidated for cmdbId: {}, env: {}", 
                        pathInfo.getCmdbId(), pathInfo.getEnv());
                }
            }
        }
        
        return ResponseEntity.ok().build();
    }
    
    private PolicyPathInfo parsePolicyPath(String filePath) {
        // Pattern: "{cmdbId}/{env}/{env}.cedar"
        Pattern pattern = Pattern.compile("([^/]+)/([^/]+)/([^/]+)\\.cedar");
        Matcher matcher = pattern.matcher(filePath);
        
        if (matcher.matches()) {
            String cmdbId = matcher.group(1);
            String env = matcher.group(2);
            return new PolicyPathInfo(cmdbId, env);
        }
        
        return null;
    }
}
```

**GitHub Webhook Configuration:**
```
Settings → Webhooks → Add webhook
- Payload URL: https://your-service.com/webhooks/github/policy-update
- Content type: application/json
- Events: "Just the push event"
- Secret: [Generate secret for signature verification]
```

**Pros:**
- ✅ **Real-time refresh:** Seconds, not minutes
- ✅ **Efficient:** Only affected policy cache invalidated
- ✅ **Event-driven:** No polling overhead

**Cons:**
- Requires webhook setup
- Need webhook endpoint implementation
- Security: Must verify webhook signatures

**Use Case:** Production environments with frequent policy changes

---

### Approach 3: CI/CD Pipeline Deployment

**How It Works:**
```
App Team commits policy change to GitHub
    ↓
GitHub Actions CI/CD Pipeline triggered automatically
    ↓
Pipeline Steps:
   1. Validates Cedar policy syntax ✅
   2. Tests policy logic ✅
   3. Deploys to S3/Object Store ✅
   4. Invalidates CloudFront/CDN cache ✅
    ↓
S3/Object Store updated with new policy ✅
    ↓
Next request → Fetches from S3 (not GitHub)
    ↓
New policy loaded ✅
```

**Timeline:**
- **Policy changed:** T+0 seconds
- **CI/CD pipeline:** T+30 seconds - 2 minutes
- **S3 updated:** T+30 seconds - 2 minutes
- **CDN cache invalidated:** T+30 seconds - 2 minutes
- **Total delay:** ~1-2 minutes

**Benefits:**
- ✅ Policies validated before deployment
- ✅ Fast reads (S3/CDN, not GitHub API)
- ✅ No GitHub API dependency
- ✅ Easy rollback capability

**Use Case:** Enterprise production environments

---

### Approach 4: Polling-Based Refresh (Fallback)

**How It Works:**
```
Background Job runs every 2 minutes:
    ↓
For each cmdbId + env combination:
   1. Check GitHub API for file SHA (commit hash)
   2. Compare with cached SHA
   3. If different → Invalidate cache
    ↓
Next request → Fetches fresh policy
```

**Timeline:**
- **Policy changed:** T+0 seconds
- **Next poll:** T+0 to 2 minutes (random)
- **Cache refresh:** T+0 to 2 minutes
- **Total delay:** Up to 2 minutes

**Pros:**
- Simple fallback mechanism
- Works if webhooks fail

**Cons:**
- ❌ Delayed refresh (up to polling interval)
- ❌ Extra API calls (rate limit risk)
- ❌ Less efficient than webhooks

**Use Case:** Fallback mechanism or when webhooks unavailable

---

### Comparison: Cache Refresh Approaches

| Approach | Refresh Speed | Setup Complexity | Reliability | Best For |
|----------|--------------|------------------|-------------|----------|
| **TTL-Based** | 5 min delay | Simple | High | Dev/Testing |
| **Webhook** | Real-time (1-2 sec) | Medium | High | Production ⭐ |
| **CI/CD + S3** | 1-2 min delay | Complex | Very High | Enterprise |
| **Polling** | 2 min delay | Medium | Medium | Fallback |

---

### Real-World TIAA Implementation (Hybrid Pattern)

**Most Likely Approach:**

```
1. Primary: GitHub Webhook configured
   ↓
2. Policy change → Webhook fires immediately
   ↓
3. Cache invalidated for specific cmdbId + env
   ↓
4. Next request fetches from GitHub API
   ↓
5. New policy cached for 5 minutes
   ↓
6. Fallback: If webhook fails, TTL-based refresh still works
```

**Why This Works:**
- ✅ **Real-time updates:** Webhook provides immediate refresh
- ✅ **Performance:** Caching reduces GitHub API calls
- ✅ **Resilience:** TTL-based fallback if webhook fails
- ✅ **Efficiency:** Only affected policies invalidated

**Interview Answer:**
*"We implemented GitHub webhooks for real-time cache invalidation. When app teams change policies in GitHub, GitHub automatically sends a webhook to our service. We parse the changed file path to extract the cmdbId and environment, then immediately invalidate only that specific policy cache. The next authorization request fetches the fresh policy from GitHub API. This gives us real-time policy updates within seconds, while maintaining performance through caching. As a fallback, we also have TTL-based cache expiration in case webhooks fail."*

---

### Key Takeaways

1. **Without webhook:** Cache refreshes when TTL expires (5-10 min delay)
2. **With webhook:** Cache refreshes immediately (1-2 sec delay) ⭐
3. **CI/CD approach:** GitHub = source, S3 = deployment target (most reliable)
4. **Hybrid:** Webhook + TTL fallback = best of both worlds

**Critical Implementation Details:**
- **Webhook security:** Always verify webhook signatures
- **Selective invalidation:** Only invalidate affected policies, not entire cache
- **Monitoring:** Track webhook delivery, cache hit rates, refresh delays
- **Error handling:** Graceful fallback if webhook fails

---

**Example Policy File (`payment-service/prod/policy.cedar`):**

```cedar
// RBAC + ABAC Combined Policy
permit(
    principal in Role::"teller",  // RBAC: Must be teller role
    action == Action::"withdraw",
    resource == Resource::"account"
) when {
    resource.balance > 1000 &&  // ABAC: Balance check
    principal.department == "retail" &&  // ABAC: Department check
    context.timeOfDay >= 9 && context.timeOfDay <= 17  // ABAC: Time check
};

// RBAC-Only Policy
permit(
    principal in Role::"admin",
    action == Action::"delete",
    resource
);

// ABAC-Only Policy
permit(
    principal,
    action == Action::"transfer",
    resource == Resource::"account"
) when {
    resource.balance >= 100 && 
    principal.region == resource.region
};
```

---

## 🔄 Complete Request Flow: Step-by-Step

### Timeline: End-to-End Authorization

```
T0: Client Request
    ├─ Client sends: POST /api/accounts/12345/withdraw
    └─ Headers: Authorization: Bearer <JWT>
    
T1: PEP - Extract JWT
    ├─ Extract token from Authorization header
    ├─ Validate signature & expiration ✅
    └─ Decode claims:
        {
          "sub": "user:12345",
          "roles": ["teller"],
          "department": "retail",
          "appId": "payment-service",
          "env": "prod"
        }
    
T2: PEP - Build Authorization Request
    ├─ Principal: Principal.of("user:12345")
    ├─ Action: Action.of("post", "withdraw")
    ├─ Resource: Resource.of("Account", "account::12345")
    └─ Context:
        {
          "roles": ["teller"],
          "department": "retail",
          "timeOfDay": 14,
          "balance": 5000  // Fetched from database
        }
    
T3: PEP - Call PDP
    └─ Send authorization request to CedarEngine
    
T4: PDP - Policy Selection
    ├─ Extract appId: "payment-service"
    ├─ Extract env: "prod"
    └─ Load policies from: payment-service/prod/policy.cedar
    
T5: PDP - Policy Evaluation
    ├─ Policy 1: permit(principal in Role::"teller", ...)
    │   ├─ Check: Is "user:12345" in Role::"teller"?
    │   └─ ✅ YES (from JWT roles claim)
    │
    ├─ Policy 1: when { resource.balance > 1000 && ... }
    │   ├─ Check: Is 5000 > 1000? ✅ YES
    │   ├─ Check: Is "retail" == "retail"? ✅ YES
    │   └─ Check: Is 14 >= 9 && 14 <= 17? ✅ YES
    │
    └─ Result: EXPLICIT ALLOW ✅
    
T6: PDP - Return Decision
    └─ Return: AuthorizationDecision.allow()
    
T7: PEP - Enforce Decision
    └─ Forward request to microservice ✅
    
T8: Microservice Processes Request
    └─ Withdraw operation executes
```

---

## 🎤 Interview Explanation: 2-Minute Version

### **Minute 1: Architecture & Concepts**

*"We implemented RBAC and ABAC using Cedar policy engine with JWT integration. Cedar is a policy language that evaluates authorization requests using Principal, Action, Resource, and Context - the PARC model.*

*RBAC answers 'who can do what' - like 'only tellers can withdraw'. ABAC answers 'when and under what conditions' - like 'can withdraw if balance > 1000 and during business hours'. Cedar allows combining both in a single policy.*

*The architecture follows PEP-PDP pattern. PEP is the Policy Enforcement Point - it sits at the API gateway or service filter, extracts JWT, builds authorization request, and calls PDP. PDP is the Policy Decision Point - it loads Cedar policies and evaluates them, returning Allow or Deny. PEP then enforces that decision."*

### **Minute 2: JWT Integration & Implementation**

*"JWT carries the identity and attributes needed for authorization. The auth service issues JWT with principal ID, roles for RBAC, and attributes like department for ABAC. It also includes context like appId and env to determine which policies to load.*

*The PEP extracts JWT, validates it, and builds the authorization request. It maps JWT claims to Cedar's Principal, extracts Action from HTTP method and path, Resource from URL, and builds Context from JWT claims plus request metadata. For ABAC checks like balance, it fetches resource attributes from the database.*

*The PDP loads policies based on appId and env from JWT, evaluates each policy against the request, and returns Allow or Deny. Policies can combine RBAC checks like 'principal in Role::teller' with ABAC checks like 'resource.balance > 1000'. The PEP enforces the decision - allows request or returns 403."*

---

## 🔑 Key Concepts Explained

### 1. Entitlement vs Policy

**What is an Entitlement?**
- **Business term:** The actual permission/right a user/role has to perform an action on a resource
- **Example:** "User is entitled to withdraw from accounts with balance > 1000"

**What is a Policy?**
- **Technical term:** The rule/statement that defines what is allowed/denied
- **Example:** Cedar policy code that evaluates to allow/deny

**Relationship:**
- Policy **defines** the entitlement
- Entitlement is what the user **has** (the outcome of policy evaluation)
- In business context, "entitlement" often refers to both the policy and the permission

**In Practice:**
- "Entitlement client" = PEP (Policy Enforcement Point)
- "Entitlement management" = Managing policies that define entitlements
- "Check entitlement" = Evaluate policy to determine if user has permission

### 2. JWT Role in Authorization

**What JWT Provides:**
- **Principal identity:** `sub` claim identifies who is making the request
- **RBAC data:** `roles` claim provides roles for RBAC evaluation
- **ABAC attributes:** Custom claims like `department`, `region` for ABAC evaluation
- **Context:** `appId`, `env` for policy selection

**What JWT Does NOT Do:**
- JWT does **not** implement RBAC/ABAC logic
- JWT is **not** the authorization decision
- JWT is **not** the policy

**Correct Understanding:**
- JWT = **Carrier** of identity and attributes
- Cedar Policies = **Logic** that implements RBAC/ABAC
- PDP = **Evaluator** that uses JWT data + policies to make decisions

### 3. PEP (Policy Enforcement Point)

**Responsibilities:**
1. **Intercept requests** at API gateway or service level
2. **Extract JWT** from Authorization header
3. **Validate JWT** (signature, expiration, issuer)
4. **Build authorization request:**
   - Principal from JWT `sub`
   - Action from HTTP method + path
   - Resource from URL or request body
   - Context from JWT claims + request metadata
5. **Call PDP** with authorization request
6. **Enforce decision:** Allow request or return 403

**Implementation Locations:**
- API Gateway filter/interceptor
- Spring Boot filter/interceptor
- Service mesh sidecar
- Microservice AOP aspect

**In Business Terms:** "Entitlement client" or "authorization middleware"

### 4. PDP (Policy Decision Point)

**Responsibilities:**
1. **Load policies** based on appId and env
2. **Evaluate policies** against authorization request
3. **Return decision:** Allow or Deny

**Policy Evaluation:**
- Checks RBAC: `principal in Role::"teller"`?
- Checks ABAC: `resource.balance > 1000`?
- Checks ABAC: `principal.department == "retail"`?
- Checks ABAC: `context.timeOfDay >= 9`?

**Decision Logic:**
- If any policy explicitly denies → DENY
- If any policy explicitly allows → ALLOW
- If no policy matches → DENY (default deny)

**Implementation:**
- Centralized authorization service
- Embedded SDK/library
- Cedar policy engine

**In Business Terms:** "Policy engine" or "authorization service"

### 5. Policy Evaluation Order

**Cedar Evaluation Rules:**
1. Policies evaluated in order
2. **Explicit Deny** always wins (even if later policy allows)
3. **Explicit Allow** grants access (unless earlier policy denied)
4. **No match** = Deny (default deny)

**Example:**
```cedar
// Policy 1: Deny after hours
forbid(
    principal,
    action == Action::"withdraw",
    resource
) when {
    context.timeOfDay < 9 || context.timeOfDay > 17
};

// Policy 2: Allow tellers
permit(
    principal in Role::"teller",
    action == Action::"withdraw",
    resource
);
```

**Evaluation:**
- If timeOfDay = 20 (8 PM):
  - Policy 1: DENY ✅ (explicit deny wins)
- If timeOfDay = 14 (2 PM):
  - Policy 1: No match
  - Policy 2: ALLOW ✅

### 6. ABAC Resource Attributes

**Challenge:** ABAC policies need resource attributes (e.g., `resource.balance`)

**Solution:** PEP fetches resource attributes before calling PDP

**Example:**
```java
// In PEP, before calling PDP
String accountId = extractAccountId(request);
Account account = accountService.getAccount(accountId);

Context context = Context.empty()
    .with("balance", account.getBalance())  // ABAC attribute
    .with("accountStatus", account.getStatus())
    .with("ownerId", account.getOwnerId());
```

**Cedar Policy:**
```cedar
permit(
    principal,
    action == Action::"transfer",
    resource == Resource::"account"
) when {
    resource.balance >= 100 &&  // Uses balance from context
    resource.ownerId == principal.id  // Owner check
};
```

---

## 🚨 Common Pitfalls & Solutions

### Pitfall 1: Confusing JWT with Authorization Logic

**Problem:**
```java
// ❌ Thinking JWT implements RBAC/ABAC
if (jwtClaims.get("roles").contains("admin")) {
    allow();
}
```

**Solution:**
```java
// ✅ JWT provides data, policies implement logic
AuthorizationRequest request = buildRequest(jwtClaims, httpRequest);
AuthorizationDecision decision = pdp.evaluate(request);
// Policy engine evaluates: principal in Role::"admin" && ...
```

**Key Insight:** JWT is data carrier, not authorization logic.

### Pitfall 2: Not Loading Resource Attributes for ABAC

**Problem:**
```java
// ❌ Missing resource attributes
Context context = Context.empty()
    .with("roles", roles)
    .with("department", department);
// Policy needs resource.balance but it's not in context
```

**Solution:**
```java
// ✅ Fetch resource attributes before calling PDP
Account account = accountService.getAccount(accountId);
Context context = Context.empty()
    .with("balance", account.getBalance())  // ✅ ABAC attribute
    .with("accountStatus", account.getStatus());
```

### Pitfall 3: Wrong Policy Selection

**Problem:**
```java
// ❌ Hardcoded policy path
List<Policy> policies = loadPolicies("payment-service", "prod");
// Doesn't work for multiple apps/environments
```

**Solution:**
```java
// ✅ Use appId and env from JWT
String appId = jwtClaims.get("appId", String.class);
String env = jwtClaims.get("env", String.class);
List<Policy> policies = policyStore.loadPolicies(appId, env);
```

### Pitfall 4: Not Handling Policy Evaluation Errors

**Problem:**
```java
// ❌ No error handling
AuthorizationDecision decision = pdp.evaluate(request);
if (decision.isAllowed()) {
    // What if evaluation throws exception?
}
```

**Solution:**
```java
// ✅ Handle errors gracefully
try {
    AuthorizationDecision decision = pdp.evaluate(request);
    if (decision.isAllowed()) {
        chain.doFilter(request, response);
    } else {
        sendForbidden(response);
    }
} catch (PolicyEvaluationException e) {
    // Log error, default deny
    log.error("Policy evaluation failed", e);
    sendForbidden(response);
}
```

### Pitfall 5: Missing Context for ABAC Checks

**Problem:**
```java
// ❌ Missing time context
Context context = Context.empty()
    .with("roles", roles)
    .with("department", department);
// Policy checks context.timeOfDay but it's missing
```

**Solution:**
```java
// ✅ Include all necessary context
Context context = Context.empty()
    .with("roles", roles)
    .with("department", department)
    .with("timeOfDay", getCurrentHour())  // ✅ Time check
    .with("ipAddress", request.getRemoteAddr())
    .with("userAgent", request.getHeader("User-Agent"));
```

---

## 📊 Policy Storage Structure

```
Policy Storage:
├── payment-service/
│   ├── dev/
│   │   └── policy.cedar
│   ├── stg/
│   │   └── policy.cedar
│   └── prod/
│       └── policy.cedar
├── user-service/
│   ├── dev/
│   │   └── policy.cedar
│   └── prod/
│       └── policy.cedar
└── admin-service/
    └── prod/
        └── policy.cedar
```

**Policy Selection Logic:**
- Extract `appId` from JWT → `"payment-service"`
- Extract `env` from JWT → `"prod"`
- Load policies from: `payment-service/prod/policy.cedar`

**Benefits:**
- Policies isolated by application
- Environment-specific policies (dev/stg/prod)
- Easy to update policies without code changes

---

## ✅ Interview Checklist

When explaining RBAC/ABAC implementation, cover:

- [ ] **Problem Statement**: Need fine-grained authorization combining RBAC and ABAC
- [ ] **Solution Approach**: Cedar policy engine with PEP-PDP pattern
- [ ] **Cedar Concepts**: PARC model (Principal, Action, Resource, Context)
- [ ] **RBAC vs ABAC**: Role-based vs attribute-based, when to use each
- [ ] **Combined Policies**: How Cedar combines RBAC and ABAC in single policy
- [ ] **JWT Integration**: How JWT provides principal, roles, and attributes
- [ ] **PEP Responsibilities**: Extract JWT, build request, call PDP, enforce decision
- [ ] **PDP Responsibilities**: Load policies, evaluate, return Allow/Deny
- [ ] **Policy Selection**: Using appId and env from JWT
- [ ] **Resource Attributes**: Fetching ABAC attributes (balance, status) before evaluation
- [ ] **Context Building**: Combining JWT claims with request metadata
- [ ] **Decision Enforcement**: PEP allows or denies based on PDP decision
- [ ] **Entitlement Terminology**: Business vs technical terms
- [ ] **Error Handling**: Graceful failure, default deny

---

## 🎓 Advanced: Entitlement Client Pattern

### Spring Boot Entitlement Client

```java
@Component
public class EntitlementClient {
    
    private final CedarEngine pdp;
    
    /**
     * Generic authorization check - PEP implementation
     */
    public AuthorizationDecision checkAuthorization(
            HttpServletRequest request,
            String action,
            String resourceType,
            String resourceId) {
        
        // Extract JWT
        Claims jwtClaims = extractAndValidateJwt(request);
        
        // Build authorization request
        AuthorizationRequest authRequest = AuthorizationRequest.builder()
            .principal(Principal.of(jwtClaims.getSubject()))
            .action(Action.of(request.getMethod().toLowerCase(), action))
            .resource(Resource.of(resourceType, resourceId))
            .context(buildContext(jwtClaims, request, resourceId))
            .build();
        
        // Call PDP
        return pdp.isAuthorized(authRequest);
    }
    
    private Context buildContext(Claims jwtClaims, HttpServletRequest request, String resourceId) {
        Context context = Context.empty();
        
        // From JWT
        context = context.with("roles", jwtClaims.get("roles", List.class));
        context = context.with("department", jwtClaims.get("department", String.class));
        context = context.with("appId", jwtClaims.get("appId", String.class));
        context = context.with("env", jwtClaims.get("env", String.class));
        
        // From request
        context = context.with("timeOfDay", getCurrentHour());
        context = context.with("ipAddress", request.getRemoteAddr());
        
        // From resource (ABAC attributes)
        if (resourceId != null) {
            Account account = accountService.getAccount(resourceId);
            context = context.with("balance", account.getBalance());
            context = context.with("accountStatus", account.getStatus());
        }
        
        return context;
    }
}
```

### AOP Aspect for Authorization

```java
@Aspect
@Component
public class AuthorizationAspect {
    
    @Autowired
    private EntitlementClient entitlementClient;
    
    @Around("@annotation(requireAuth)")
    public Object checkAuthorization(ProceedingJoinPoint joinPoint, RequireAuthorization requireAuth) {
        
        HttpServletRequest request = getCurrentRequest();
        
        // Extract action and resource from annotation
        String action = requireAuth.action();
        String resourceType = requireAuth.resourceType();
        String resourceId = extractResourceId(joinPoint, requireAuth);
        
        // Check authorization via entitlement client
        AuthorizationDecision decision = entitlementClient.checkAuthorization(
            request, action, resourceType, resourceId
        );
        
        if (!decision.isAllowed()) {
            throw new ForbiddenException("Access denied: " + decision.getReason());
        }
        
        // Proceed if allowed
        return joinPoint.proceed();
    }
}
```

### Usage in Controller

```java
@RestController
@RequestMapping("/api/accounts")
public class AccountController {
    
    @PostMapping("/{accountId}/withdraw")
    @RequireAuthorization(action = "withdraw", resourceType = "Account")
    public ResponseEntity<?> withdraw(
            @PathVariable String accountId,
            @RequestBody WithdrawRequest request) {
        
        // Authorization already checked by AOP aspect ✅
        // Just implement business logic
        accountService.withdraw(accountId, request.getAmount());
        return ResponseEntity.ok().build();
    }
}
```

**Benefits:**
- Single entitlement client handles all authorization
- Generic annotation-based approach
- No authorization logic in controllers
- Easy to test and maintain

---

## 📝 Summary

**The Complete Flow:**

1. **Authentication:** Auth service issues JWT with principal, roles, and attributes
2. **Request Arrives:** Client sends request with JWT token
3. **PEP Intercepts:** Extracts JWT, validates it, builds authorization request
4. **Context Building:** Combines JWT claims with request metadata and resource attributes
5. **PDP Evaluates:** Loads policies, evaluates against authorization request
6. **Decision Made:** Returns Allow or Deny based on policy evaluation
7. **PEP Enforces:** Allows request or returns 403 Forbidden
8. **Request Processed:** If allowed, microservice processes request

**Key Insights:**

1. **JWT is data carrier, not authorization logic** - It provides identity, roles, and attributes, but policies implement RBAC/ABAC logic
2. **PEP enforces, PDP decides** - Clear separation of concerns
3. **RBAC + ABAC combined** - Cedar allows both in single policy for fine-grained control
4. **Context is critical** - ABAC needs resource attributes and request metadata
5. **Policy selection** - Use appId and env from JWT to load correct policies
6. **Entitlement terminology** - In business context, "entitlement" refers to permissions, policies, and the system managing them

**This implementation demonstrates:**
- Understanding of authorization patterns (RBAC, ABAC, PEP-PDP)
- JWT integration for identity and attributes
- Policy-based authorization with Cedar
- Microservices authorization architecture
- Separation of concerns (PEP vs PDP)

---

## 🔗 Related Concepts

- **RBAC (Role-Based Access Control):** Access based on user roles
- **ABAC (Attribute-Based Access Control):** Access based on attributes and context
- **Cedar Policy Language:** Policy language for authorization
- **JWT (JSON Web Token):** Token format for identity and claims
- **PEP (Policy Enforcement Point):** Where authorization is enforced
- **PDP (Policy Decision Point):** Where authorization decisions are made
- **Entitlement Management:** Managing user permissions and policies
- **Microservices Security:** Authorization in distributed systems
- **Policy-Based Authorization:** Declarative authorization rules

---

**This implementation demonstrates deep understanding of authorization architectures, policy-based access control, JWT integration, and microservices security patterns.**