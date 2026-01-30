# CyberArk Sidecar Container - Complete Implementation & Lifecycle

## 🎯 Overview: The Challenge

**Problem:** We need to inject CyberArk credentials into Spring Boot application without storing them in ConfigMaps or application.properties.

**Requirements:**
- Credentials stored in CyberArk (accessed via REST API)
- Application needs `cyberark.database.username` and `cyberark.database.password` properties
- Use `@Value("${cyberark.database.username}")` in application code
- No CyberArk API calls in application code - handled at infrastructure level

**Solution:** Custom PropertySource that fetches credentials from CyberArk API during bean lifecycle initialization.

---

## 🏗️ Architecture: How It Works

```
┌─────────────────────────────────────────────────────────────┐
│                    APPLICATION STARTUP                      │
│                                                             │
│  1. Spring loads application.properties                    │
│     (cyberark.database.username=USER_NAME)                 │
│                                                             │
│  2. Environment initialized with standard PropertySources   │
│     - System Properties                                     │
│     - Environment Variables                                 │
│     - application.properties                                │
│                                                             │
│  3. ❌ NO CyberArkPropertySource yet                       │
└────────────────────┬────────────────────────────────────────┘
                     ↓
┌─────────────────────────────────────────────────────────────┐
│              BEAN LIFECYCLE: CyberArkConfig                 │
│                                                             │
│  Phase 1: Constructor                                       │
│    - environment = null ❌                                  │
│                                                             │
│  Phase 2: Dependency Injection                             │
│    - environment = <injected> ✅                           │
│                                                             │
│  Phase 3: @PostConstruct                                   │
│    ├─ Call CyberArk REST API                               │
│    ├─ Get credentials: {USER_NAME: "sai", PASSWORD: "sai123"}│
│    ├─ Map to Spring properties:                            │
│    │    cyberark.database.username → "sai"                │
│    │    cyberark.database.password → "sai123"              │
│    ├─ Create CyberArkPropertySource                        │
│    └─ Add to Environment (HIGHEST PRIORITY) ✅            │
│                                                             │
│  ✅ PropertySource now available in Environment             │
└────────────────────┬────────────────────────────────────────┘
                     ↓
┌─────────────────────────────────────────────────────────────┐
│            BEAN LIFECYCLE: DatabaseConfig                  │
│                                                             │
│  Phase 1: Constructor                                       │
│    - userName = null ❌                                    │
│                                                             │
│  Phase 2: Dependency Injection                             │
│    - Spring sees @Value("${cyberark.database.username}")  │
│    - Queries Environment                                    │
│    - Environment checks CyberArkPropertySource ✅          │
│    - Gets "sai" → Injects into userName ✅                 │
│                                                             │
│  Phase 3: @PostConstruct                                   │
│    - userName = "sai" ✅ (ready to use)                   │
│    - Create DataSource with credentials                    │
└─────────────────────────────────────────────────────────────┘
```

---

## 📋 Complete Implementation

### Step 1: Custom PropertySource

```java
package com.example.config;

import org.springframework.core.env.PropertySource;
import java.util.Map;

/**
 * Custom PropertySource that provides properties from CyberArk API.
 * This extends Spring's PropertySource abstraction to integrate with CyberArk.
 */
public class CyberArkPropertySource extends PropertySource<Map<String, String>> {
    
    /**
     * Constructor
     * @param name Name of this PropertySource (for logging/debugging)
     * @param source Map containing property key-value pairs from CyberArk
     */
    public CyberArkPropertySource(String name, Map<String, String> source) {
        super(name, source);
    }
    
    /**
     * Called by Spring's Environment when resolving @Value placeholders.
     * 
     * When Spring sees @Value("${cyberark.database.username}"), it:
     * 1. Extracts key: "cyberark.database.username"
     * 2. Calls this method with that key
     * 3. Returns the value if found, null otherwise
     */
    @Override
    public Object getProperty(String name) {
        if (name == null || this.source == null) {
            return null;
        }
        
        // Return value from our CyberArk credentials map
        return this.source.get(name);
    }
    
    /**
     * Check if this PropertySource contains a specific property.
     * Used by Environment to determine if property exists.
     */
    @Override
    public boolean containsProperty(String name) {
        return this.source != null && this.source.containsKey(name);
    }
}
```

**Key Points:**
- Extends `PropertySource<Map<String, String>>` - Spring's abstraction for property sources
- `getProperty(String name)` is called by Spring's `Environment` during `@Value` resolution
- The `source` Map contains credentials fetched from CyberArk API

---

### Step 2: CyberArk Client (REST API Caller)

```java
package com.example.config;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import java.util.HashMap;
import java.util.Map;

/**
 * Client to interact with CyberArk REST API.
 * Fetches secrets from CyberArk vault.
 */
@Component
public class CyberArkClient {
    
    private final RestTemplate restTemplate;
    private final String cyberArkUrl;
    private final String appId;
    private final String safe;
    
    public CyberArkClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
        // These would come from environment variables or ConfigMap
        this.cyberArkUrl = System.getenv("CYBERARK_URL");
        this.appId = System.getenv("CYBERARK_APP_ID");
        this.safe = System.getenv("CYBERARK_SAFE");
    }
    
    /**
     * Fetches secrets from CyberArk API.
     * 
     * CyberArk API Response Format:
     * {
     *   "USER_NAME": "sai",
     *   "PASSWORD": "sai123"
     * }
     * 
     * @return Map of CyberArk keys to their values
     */
    public Map<String, String> fetchSecrets() {
        try {
            // Step 1: Authenticate to CyberArk
            String token = authenticate();
            
            // Step 2: Fetch secrets from CyberArk Safe
            String url = String.format("%s/api/safes/%s/secrets", cyberArkUrl, safe);
            
            Map<String, Object> response = restTemplate.postForObject(
                url,
                createAuthHeaders(token),
                Map.class
            );
            
            // Step 3: Map CyberArk response to Spring property keys
            return mapToSpringProperties(response);
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch secrets from CyberArk", e);
        }
    }
    
    /**
     * Maps CyberArk API response to Spring property keys.
     * 
     * CyberArk has: USER_NAME, PASSWORD
     * Spring needs: cyberark.database.username, cyberark.database.password
     */
    private Map<String, String> mapToSpringProperties(Map<String, Object> cyberArkResponse) {
        Map<String, String> springProperties = new HashMap<>();
        
        // Map CyberArk keys to Spring property keys
        if (cyberArkResponse.containsKey("USER_NAME")) {
            springProperties.put("cyberark.database.username", 
                String.valueOf(cyberArkResponse.get("USER_NAME")));
        }
        
        if (cyberArkResponse.containsKey("PASSWORD")) {
            springProperties.put("cyberark.database.password", 
                String.valueOf(cyberArkResponse.get("PASSWORD")));
        }
        
        return springProperties;
    }
    
    private String authenticate() {
        // CyberArk authentication logic
        // Returns authentication token
        return "auth-token";
    }
    
    private Map<String, String> createAuthHeaders(String token) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + token);
        return headers;
    }
}
```

---

### Step 3: Configuration Class (Lifecycle Hook)

```java
package com.example.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MutablePropertySources;

import javax.annotation.PostConstruct;
import java.util.Map;

/**
 * Configuration class that integrates CyberArk credentials into Spring's Environment.
 * 
 * CRITICAL: This must be a @Configuration class (not @Component) to ensure
 * it's processed early in the bean creation lifecycle, BEFORE other beans
 * that use @Value annotations try to resolve properties.
 */
@Configuration
public class CyberArkConfig {
    
    @Autowired
    private Environment environment;
    
    @Autowired
    private CyberArkClient cyberArkClient;
    
    /**
     * This method runs during the @PostConstruct phase of THIS bean's lifecycle.
     * 
     * BEAN LIFECYCLE TIMELINE:
     * 1. Constructor runs (environment = null)
     * 2. Dependency Injection (environment injected ✅)
     * 3. @PostConstruct runs ← WE ARE HERE
     *    - Call CyberArk API
     *    - Create PropertySource
     *    - Add to Environment
     * 
     * IMPORTANT: This runs BEFORE other beans (like DatabaseConfig) are created,
     * so when they try to resolve @Value, the PropertySource already exists.
     */
    @PostConstruct
    public void loadCyberArkSecrets() {
        System.out.println("=== CyberArkConfig @PostConstruct: Loading secrets ===");
        
        try {
            // Step 1: Fetch credentials from CyberArk REST API
            Map<String, String> secrets = cyberArkClient.fetchSecrets();
            
            System.out.println("Fetched secrets from CyberArk: " + secrets.keySet());
            
            // Step 2: Create custom PropertySource with CyberArk credentials
            CyberArkPropertySource cyberArkPropertySource = 
                new CyberArkPropertySource("cyberArk", secrets);
            
            // Step 3: Add PropertySource to Environment
            // Cast to ConfigurableEnvironment to access mutable PropertySources
            if (environment instanceof ConfigurableEnvironment) {
                ConfigurableEnvironment configurableEnv = (ConfigurableEnvironment) environment;
                MutablePropertySources propertySources = configurableEnv.getPropertySources();
                
                // addFirst() gives highest priority - checked FIRST when resolving properties
                propertySources.addFirst(cyberArkPropertySource);
                
                System.out.println("✅ CyberArkPropertySource added to Environment");
                System.out.println("PropertySource order:");
                propertySources.forEach(ps -> 
                    System.out.println("  - " + ps.getName())
                );
            }
            
        } catch (Exception e) {
            throw new RuntimeException(
                "Failed to load CyberArk secrets. Application cannot start.", e);
        }
    }
}
```

**Critical Points:**
- `@Configuration` ensures early bean creation (before `@Component` beans)
- `@PostConstruct` runs after DI phase, before other beans are created
- `addFirst()` gives highest priority - checked first during property resolution
- Must cast `Environment` to `ConfigurableEnvironment` to access `MutablePropertySources`

---

### Step 4: Application Code (Using @Value)

```java
package com.example.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.annotation.PostConstruct;

/**
 * Example service that uses CyberArk credentials via @Value.
 * 
 * This demonstrates that application code has ZERO knowledge of CyberArk.
 * It just uses standard Spring @Value annotation, and Spring's property
 * resolution mechanism handles the rest.
 */
@Component
public class DatabaseConfig {
    
    /**
     * Spring will resolve this during Dependency Injection phase:
     * 1. Extract key: "cyberark.database.username"
     * 2. Query Environment.getProperty("cyberark.database.username")
     * 3. Environment checks PropertySources in order:
     *    - CyberArkPropertySource (added by CyberArkConfig) ✅ FOUND "sai"
     * 4. Inject value: userName = "sai"
     */
    @Value("${cyberark.database.username}")
    private String userName;
    
    @Value("${cyberark.database.password}")
    private String password;
    
    /**
     * Constructor runs FIRST in bean lifecycle.
     * At this point, @Value fields are still NULL.
     */
    public DatabaseConfig() {
        System.out.println("=== DatabaseConfig Constructor ===");
        System.out.println("userName: " + userName);  // null ❌
        System.out.println("password: " + password);  // null ❌
    }
    
    /**
     * @PostConstruct runs AFTER Dependency Injection phase.
     * At this point, @Value fields are populated ✅
     */
    @PostConstruct
    public void init() {
        System.out.println("=== DatabaseConfig @PostConstruct ===");
        System.out.println("userName: " + userName);  // "sai" ✅
        System.out.println("password: " + password);  // "sai123" ✅
        
        // Now we can safely create DataSource with credentials
        createDataSource();
    }
    
    private void createDataSource() {
        // Use userName and password to create DataSource
        System.out.println("Creating DataSource with username: " + userName);
    }
}
```

---

## 🔄 Complete Lifecycle Flow

### Timeline: Step-by-Step

```
T0: Application Startup
    ├─ SpringApplication.run()
    ├─ Create ApplicationContext
    ├─ Load application.properties → Environment
    └─ Scan for @Configuration, @Component classes
    
T1: Create CyberArkConfig Bean
    ├─ Phase 1: Constructor
    │   └─ environment = null ❌
    │
    ├─ Phase 2: Dependency Injection
    │   └─ environment = <injected> ✅
    │   └─ cyberArkClient = <injected> ✅
    │
    └─ Phase 3: @PostConstruct
        ├─ Call CyberArk API
        ├─ Get credentials: {USER_NAME: "sai", PASSWORD: "sai123"}
        ├─ Map to Spring properties
        ├─ Create CyberArkPropertySource
        └─ Add to Environment ✅
        
T2: Create DatabaseConfig Bean
    ├─ Phase 1: Constructor
    │   └─ userName = null ❌
    │   └─ password = null ❌
    │
    ├─ Phase 2: Dependency Injection
    │   ├─ Spring sees @Value("${cyberark.database.username}")
    │   ├─ Extracts key: "cyberark.database.username"
    │   ├─ Calls Environment.getProperty("cyberark.database.username")
    │   ├─ Environment checks PropertySources:
    │   │   └─ CyberArkPropertySource.getProperty() ✅ Returns "sai"
    │   ├─ Spring injects: userName = "sai" ✅
    │   │
    │   ├─ Same process for password
    │   └─ Spring injects: password = "sai123" ✅
    │
    └─ Phase 3: @PostConstruct
        └─ userName = "sai" ✅, password = "sai123" ✅
        
T3: Application Ready
    └─ All beans initialized, credentials available
```

---

## 🎤 Interview Explanation: 3-Minute Version

### **Minute 1: The Problem & Architecture**

*"We needed to inject CyberArk credentials into our Spring Boot application without storing them in ConfigMaps or application.properties. The credentials are stored in CyberArk and accessed via REST API.*

*The solution leverages Spring's PropertySource abstraction. Spring's Environment maintains a list of PropertySources - like System Properties, Environment Variables, and application.properties. When you use @Value annotation, Spring queries these PropertySources in order until it finds the property.*

*We created a custom PropertySource that wraps our CyberArk client. When Spring queries this PropertySource, it calls the CyberArk API and returns the credential value. This way, application code just uses standard @Value annotation - it has zero knowledge of CyberArk."*

### **Minute 2: Bean Lifecycle & Implementation**

*"The key is understanding Spring's bean lifecycle. We need to add our PropertySource to the Environment BEFORE other beans try to resolve @Value annotations.*

*We use a @Configuration class called CyberArkConfig. @Configuration beans are created early in the lifecycle. In its @PostConstruct method - which runs after dependency injection but before other beans are created - we call the CyberArk API, fetch the credentials, create our custom PropertySource, and add it to the Environment with highest priority using addFirst().*

*Then, when DatabaseConfig bean is created, during its dependency injection phase, Spring sees @Value annotations, queries the Environment, finds our CyberArkPropertySource first, gets the values, and injects them into the fields. By the time @PostConstruct runs in DatabaseConfig, the credentials are already available."*

### **Minute 3: The Engineering Details**

*"The implementation involves three components: First, a CyberArkPropertySource that extends Spring's PropertySource class and implements getProperty() method - this is called by Spring's Environment during property resolution.*

*Second, a CyberArkClient that makes REST API calls to CyberArk and maps the response to Spring property keys - like mapping USER_NAME from CyberArk to cyberark.database.username for Spring.*

*Third, the CyberArkConfig that hooks into bean lifecycle via @PostConstruct to register the PropertySource early. We cast Environment to ConfigurableEnvironment to access MutablePropertySources and add our PropertySource at the top of the list.*

*The beauty is that this integrates seamlessly with Spring's existing property resolution mechanism. AbstractPropertyResolver in Spring's Environment handles the lookup, and our custom PropertySource fits right into that flow. Application code doesn't change - it just uses @Value as usual."*

---

## 🔑 Key Concepts Explained

### 1. PropertySource Abstraction

**What it is:**
- Spring's abstraction for sources of key-value pairs
- Examples: System Properties, Environment Variables, application.properties

**Why it matters:**
- Allows extending Spring's property system
- Custom PropertySource integrates seamlessly with @Value resolution
- No changes needed to application code

**How it works:**
```java
// Spring's Environment calls this when resolving @Value
PropertySource.getProperty("cyberark.database.username")
    → Returns "sai" or null
```

### 2. Environment & MutablePropertySources

**What it is:**
- `Environment` = Spring's central property registry
- `MutablePropertySources` = List of PropertySources that can be modified

**Why it matters:**
- We need to add our PropertySource to this list
- Must cast `Environment` to `ConfigurableEnvironment` to access it

**How it works:**
```java
ConfigurableEnvironment env = (ConfigurableEnvironment) environment;
MutablePropertySources sources = env.getPropertySources();
sources.addFirst(cyberArkPropertySource);  // Highest priority
```

### 3. Bean Lifecycle Order

**What it is:**
- Constructor → Dependency Injection → @PostConstruct
- @Configuration beans created before @Component beans

**Why it matters:**
- PropertySource must be added BEFORE beans that use @Value are created
- @PostConstruct in @Configuration runs early enough

**How it works:**
```
CyberArkConfig @PostConstruct runs FIRST
    → Adds PropertySource ✅
    
DatabaseConfig DI phase runs AFTER
    → Finds PropertySource ✅
    → Resolves @Value ✅
```

### 4. @Value Resolution Process

**What it is:**
- Spring's mechanism to inject property values into fields

**Why it matters:**
- Happens during Dependency Injection phase
- Queries Environment, which checks PropertySources in order

**How it works:**
```
@Value("${cyberark.database.username}")
    ↓
Extract key: "cyberark.database.username"
    ↓
Environment.getProperty("cyberark.database.username")
    ↓
Check PropertySources IN ORDER:
    1. CyberArkPropertySource ✅ FOUND "sai"
    ↓
Inject: userName = "sai"
```

### 5. AbstractPropertyResolver

**What it is:**
- Spring's internal class that resolves property placeholders
- Used by Environment to query PropertySources

**Why it matters:**
- Our PropertySource integrates with this resolver
- No custom resolution logic needed - Spring handles it

**How it works:**
```java
// Spring internally does this:
AbstractPropertyResolver resolver = ...;
String value = resolver.getProperty("cyberark.database.username");
    → Queries PropertySources
    → Calls CyberArkPropertySource.getProperty()
    → Returns "sai"
```

---

## 🚨 Common Pitfalls & Solutions

### Pitfall 1: PropertySource Added Too Late

**Problem:**
```java
@Component  // ❌ Created AFTER DatabaseConfig
public class CyberArkConfig {
    @PostConstruct
    public void setup() {
        // Too late! DatabaseConfig already tried to resolve @Value
    }
}
```

**Solution:**
```java
@Configuration  // ✅ Created BEFORE @Component beans
public class CyberArkConfig {
    @PostConstruct
    public void setup() {
        // Runs early enough ✅
    }
}
```

### Pitfall 2: Wrong PropertySource Priority

**Problem:**
```java
propertySources.addLast(cyberArkSource);  // ❌ Checked LAST
// If application.properties has same key, it wins
```

**Solution:**
```java
propertySources.addFirst(cyberArkSource);  // ✅ Checked FIRST
// CyberArk values take precedence
```

### Pitfall 3: Using @Value in Constructor

**Problem:**
```java
public DatabaseConfig() {
    System.out.println(userName);  // ❌ null - not injected yet
}
```

**Solution:**
```java
@PostConstruct
public void init() {
    System.out.println(userName);  // ✅ Available after DI phase
}
```

### Pitfall 4: Not Handling CyberArk API Failures

**Problem:**
```java
@PostConstruct
public void loadSecrets() {
    Map<String, String> secrets = cyberArkClient.fetchSecrets();
    // ❌ If API fails, secrets is null → NPE
}
```

**Solution:**
```java
@PostConstruct
public void loadSecrets() {
    try {
        Map<String, String> secrets = cyberArkClient.fetchSecrets();
        if (secrets == null || secrets.isEmpty()) {
            throw new IllegalStateException("No secrets fetched from CyberArk");
        }
        // ... create PropertySource
    } catch (Exception e) {
        throw new RuntimeException("Failed to load CyberArk secrets", e);
        // Fail fast - don't start with missing credentials
    }
}
```

---

## 📊 PropertySource Priority Order

After adding CyberArkPropertySource, Spring checks in this order:

```
1. CyberArkPropertySource          ← Our custom source (HIGHEST PRIORITY)
2. System Properties (-D flags)
3. Environment Variables
4. application-{profile}.properties
5. application.properties
6. Default values in @Value("${key:default}")
```

**Why order matters:**
- If same key exists in multiple sources, first match wins
- CyberArk values override application.properties
- Allows fallback: CyberArk → Environment Variables → application.properties

---

## ✅ Interview Checklist

When explaining this implementation, cover:

- [ ] **Problem Statement**: Need CyberArk credentials without ConfigMaps
- [ ] **Solution Approach**: Custom PropertySource extending Spring's abstraction
- [ ] **Bean Lifecycle**: Why @Configuration and @PostConstruct timing matters
- [ ] **PropertySource**: How it integrates with Spring's Environment
- [ ] **Property Resolution**: How @Value queries Environment → PropertySources
- [ ] **Implementation Details**: CyberArkClient, PropertySource, Configuration class
- [ ] **Lifecycle Order**: Why CyberArkConfig must run before DatabaseConfig
- [ ] **AbstractPropertyResolver**: How Spring internally resolves properties
- [ ] **Error Handling**: Fail-fast if CyberArk API unavailable
- [ ] **Benefits**: Application code unchanged, zero CyberArk knowledge

---

## 🎓 Advanced: Lazy Property Resolution

For even better performance, you can implement lazy resolution:

```java
public class CyberArkPropertySource extends PropertySource<CyberArkClient> {
    
    private Map<String, String> cache;
    
    public CyberArkPropertySource(String name, CyberArkClient client) {
        super(name, client);
    }
    
    @Override
    public Object getProperty(String name) {
        // Lazy load: fetch from CyberArk only when property is accessed
        if (cache == null) {
            cache = this.source.fetchSecrets();
        }
        return cache.get(name);
    }
}
```

**Benefits:**
- Only calls CyberArk API when property is actually used
- Useful if not all properties are needed at startup

---

## 🔄 Industry Standard for Key Refresh

### **Interview Question: "What if keys get refreshed in CyberArk? How does the current app know about it?"**

### **🎤 Interview Answer:**

**"The current implementation fetches credentials once at startup. In production, the industry standard is to use a sidecar pattern with automatic refresh.**

**Most common approach: Sidecar container (like CyberArk Conjur or HashiCorp Vault Agent) that:**
- *Runs alongside the app in the same Kubernetes pod*
- *Periodically fetches credentials from CyberArk/Vault (every 5-15 minutes)*
- *Writes them to a shared volume (emptyDir) or updates a ConfigMap*
- *The application reads from this shared location*

**How the app knows credentials changed:**
- *File watcher: App watches the shared volume/file for changes*
- *Periodic re-read: App re-reads credentials from the file every X minutes*
- *Connection pool refresh: On connection failure, retry with newly read credentials*

**Why this pattern works:**
- *Decouples credential refresh from application code*
- *Kubernetes-native pattern*
- *Application doesn't need direct CyberArk API access*
- *Sidecar handles authentication, retries, and error handling*

**Alternative approaches (less common):**
- *Periodic refresh in app code: @Scheduled task that calls CyberArk API and updates PropertySource*
- *Webhook/event-driven: If CyberArk supports notifications (rare)*

**The key insight is that credentials typically rotate on a schedule (e.g., every 24 hours), so frequent refresh every 5-15 minutes ensures the app always has valid credentials without overwhelming the CyberArk API."**

---

## 📝 Summary

**The Complete Flow:**

1. **Application Startup**: Environment initialized with standard PropertySources
2. **CyberArkConfig Bean Created**: @PostConstruct calls CyberArk API, creates PropertySource, adds to Environment
3. **DatabaseConfig Bean Created**: @Value resolution queries Environment, finds CyberArkPropertySource, gets credentials
4. **Credentials Injected**: Fields populated during DI phase
5. **Application Ready**: All beans initialized with CyberArk credentials

**Key Insight:**
This solution leverages Spring's extensible property system. By creating a custom PropertySource and registering it early in the bean lifecycle, we integrate CyberArk seamlessly without changing application code. The bean lifecycle ensures proper ordering, and Spring's AbstractPropertyResolver handles the resolution automatically.

---

## 🔗 Related Spring Concepts

- **PropertySource**: Abstraction for property storage
- **Environment**: Central property registry
- **MutablePropertySources**: Modifiable list of PropertySources
- **AbstractPropertyResolver**: Internal resolver for property placeholders
- **Bean Lifecycle**: Constructor → DI → @PostConstruct
- **@Configuration vs @Component**: Bean creation order
- **@Value Resolution**: Happens during DI phase
- **ConfigurableEnvironment**: Mutable Environment interface

---

**This implementation demonstrates deep understanding of Spring Boot internals: PropertySource abstraction, bean lifecycle, dependency injection timing, and how to extend Spring's property resolution mechanism.**
