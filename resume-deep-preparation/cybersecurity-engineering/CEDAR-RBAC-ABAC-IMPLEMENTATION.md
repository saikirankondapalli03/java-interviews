# Cedar Policy Engine: RBAC + ABAC Implementation Guide

## Overview

This document explains the internal implementation of **Role-Based Access Control (RBAC)** and **Attribute-Based Access Control (ABAC)** using **Cedar policies** in a microservices architecture, similar to AWS IAM policies.

## Table of Contents

1. [Core Concepts](#core-concepts)
2. [Architecture](#architecture)
3. [JWT Fundamentals (Interview Revision)](#jwt-fundamentals-interview-revision)
4. [JWT Integration](#jwt-integration)
5. [Policy Structure](#policy-structure)
6. [Implementation Flow](#implementation-flow)
7. [Code Examples](#code-examples)
8. [Interview Explanation](#interview-explanation)

---

## Core Concepts

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

---

## Architecture

### Policy Decision Point (PDP) vs Policy Enforcement Point (PEP)

```
┌─────────────┐         ┌──────────────┐         ┌─────────────┐
│   Client    │────────▶│  API Gateway │────────▶│ Microservice│
│  (with JWT) │         │   (PEP)      │         │   (PEP)     │
└─────────────┘         └──────────────┘         └─────────────┘
                               │                         │
                               │ Authorization Request   │
                               │ (Principal, Action,     │
                               │  Resource, Context)     │
                               ▼                         ▼
                        ┌──────────────────────────────────┐
                        │   Cedar Policy Engine (PDP)      │
                        │   - Loads policies from storage  │
                        │   - Evaluates against JWT claims │
                        │   - Returns Allow/Deny decision  │
                        └──────────────────────────────────┘
```

**PEP (Policy Enforcement Point):**
- Intercepts requests at API Gateway or microservice level
- Extracts JWT token and request details
- Builds authorization request
- Calls PDP for decision
- Enforces decision (allows or denies request)

**PDP (Policy Decision Point):**
- Centralized authorization service
- Loads relevant Cedar policies
- Evaluates policies against authorization request
- Returns Allow/Deny decision
- Can be centralized service or embedded SDK

### Policy Storage Structure

```
App ID: "payment-service"
├── dev/
│   └── policy.cedar
├── stg/
│   └── policy.cedar
└── prod/
    └── policy.cedar
```

Each policy file contains:
- Resource definitions (what can be accessed)
- Action definitions (what operations are allowed)
- Principal definitions (who/what can access)
- Conditions combining RBAC and ABAC

---

## JWT Fundamentals (Interview Revision)

### Authentication vs Authorization: Is JWT Used For Both?

**Short Answer:** JWT is primarily used for **authorization**, but it can also be used for **authentication** depending on the architecture.

**Detailed Explanation:**

#### Authentication (Who are you?)
- **Purpose**: Verifies the identity of a user/service
- **Question**: "Are you who you claim to be?"
- **Example**: Login with username/password → Server verifies credentials → Issues JWT
- **JWT Role**: JWT can **prove** authentication was successful (contains identity claims)

#### Authorization (What can you do?)
- **Purpose**: Determines what resources/actions a user can access
- **Question**: "Are you allowed to perform this action?"
- **Example**: User with JWT tries to delete a file → System checks JWT claims (roles, permissions) → Allows or denies
- **JWT Role**: JWT **carries** authorization information (roles, permissions, attributes)

#### JWT's Dual Role:

```
┌─────────────────────────────────────────────────────────┐
│                    Authentication Flow                   │
│                                                          │
│  1. User logs in with credentials                       │
│  2. Server validates credentials                        │
│  3. Server issues JWT (proves authentication succeeded) │
│  4. JWT contains: sub (user ID), iat, exp              │
└─────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────┐
│                    Authorization Flow                    │
│                                                          │
│  1. User sends request with JWT                         │
│  2. Server validates JWT signature/expiration           │
│  3. Server extracts claims (roles, permissions)         │
│  4. Server makes authorization decision                 │
│  5. JWT carries authorization data (roles, attributes)  │
└─────────────────────────────────────────────────────────┘
```

**In Practice:**
- **JWT for Authentication**: JWT proves that authentication already happened (stateless session token)
- **JWT for Authorization**: JWT carries authorization data (roles, permissions, attributes) used to make access control decisions

**Common Pattern:**
1. **Login endpoint** → Validates credentials → Issues JWT (authentication)
2. **Protected endpoints** → Validate JWT → Extract claims → Make authorization decisions (authorization)

### What is JWT?

**JWT (JSON Web Token)** is a compact, URL-safe token format for securely transmitting information between parties as a JSON object.

**Key Characteristics:**
- **Stateless**: No server-side session storage needed
- **Self-contained**: Token contains all necessary information
- **Signed**: Can be verified to ensure integrity
- **Compact**: Can be sent via URL, POST, or HTTP header

### JWT Structure

JWT consists of three parts separated by dots (`.`):

```
header.payload.signature
```

#### 1. Header
Contains metadata about the token:
```json
{
  "alg": "HS256",  // Algorithm used for signature (HS256, RS256, ES256)
  "typ": "JWT"     // Type of token
}
```
**Base64Url encoded** → `eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9`

#### 2. Payload (Claims)
Contains the actual data (claims):
```json
{
  "sub": "user:12345",              // Subject (user ID)
  "name": "John Doe",               // User's name
  "roles": ["admin", "user"],       // User roles
  "iat": 1516239022,                // Issued at (timestamp)
  "exp": 1516242622,                // Expiration time (timestamp)
  "iss": "auth-service",            // Issuer
  "aud": "api-service"              // Audience
}
```
**Base64Url encoded** → `eyJzdWIiOiJ1c2VyOjEyMzQ1IiwibmFtZSI6IkpvaG4gRG9lIn0`

#### 3. Signature
Ensures token integrity and authenticity:
```
HMACSHA256(
  base64UrlEncode(header) + "." + base64UrlEncode(payload),
  secret
)
```
**Base64Url encoded** → `SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c`

**Complete JWT Example:**
```
eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ1c2VyOjEyMzQ1IiwibmFtZSI6IkpvaG4gRG9lIiwicm9sZXMiOlsiYWRtaW4iLCJ1c2VyIl19.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c
```

### JWT Claims

#### Standard Claims (RFC 7519)
| Claim | Full Name | Description | Example |
|-------|-----------|-------------|---------|
| `sub` | Subject | Unique identifier for the user | `"user:12345"` |
| `iss` | Issuer | Who issued the token | `"auth-service"` |
| `aud` | Audience | Who the token is intended for | `"api-service"` |
| `exp` | Expiration | Token expiration time (Unix timestamp) | `1516242622` |
| `iat` | Issued At | When token was issued (Unix timestamp) | `1516239022` |
| `nbf` | Not Before | Token not valid before this time | `1516239022` |
| `jti` | JWT ID | Unique identifier for the token | `"abc123"` |

#### Public Claims
- Custom claims defined in IANA registry or as URIs
- Examples: `name`, `email`, `roles`, `permissions`

#### Private Claims
- Custom claims agreed upon between parties
- Examples: `department`, `accountStatus`, `appId`, `env`

### JWT Validation Process

**Step-by-step validation:**

```java
public boolean validateJwt(String token) {
    // 1. Parse token into header, payload, signature
    String[] parts = token.split("\\.");
    if (parts.length != 3) {
        return false;  // Invalid format
    }
    
    // 2. Decode and parse header
    String headerJson = base64UrlDecode(parts[0]);
    JsonObject header = parseJson(headerJson);
    String algorithm = header.get("alg");
    
    // 3. Verify signature
    String signature = parts[2];
    String expectedSignature = computeSignature(
        parts[0] + "." + parts[1], 
        algorithm, 
        secretKey
    );
    if (!signature.equals(expectedSignature)) {
        return false;  // Token tampered with
    }
    
    // 4. Decode and parse payload
    String payloadJson = base64UrlDecode(parts[1]);
    JsonObject payload = parseJson(payloadJson);
    
    // 5. Validate expiration (exp claim)
    long exp = payload.get("exp");
    if (System.currentTimeMillis() / 1000 > exp) {
        return false;  // Token expired
    }
    
    // 6. Validate not-before (nbf claim) - if present
    if (payload.has("nbf")) {
        long nbf = payload.get("nbf");
        if (System.currentTimeMillis() / 1000 < nbf) {
            return false;  // Token not yet valid
        }
    }
    
    // 7. Validate issuer (iss claim) - if required
    if (expectedIssuer != null) {
        String iss = payload.get("iss");
        if (!expectedIssuer.equals(iss)) {
            return false;  // Wrong issuer
        }
    }
    
    // 8. Validate audience (aud claim) - if required
    if (expectedAudience != null) {
        String aud = payload.get("aud");
        if (!expectedAudience.equals(aud)) {
            return false;  // Wrong audience
        }
    }
    
    return true;  // Token is valid
}
```

### JWT Signing Algorithms

#### Symmetric Algorithms (Shared Secret)
- **HS256** (HMAC SHA-256): Most common, requires shared secret
- **HS384**, **HS512**: Stronger variants

**Pros:**
- Simple to implement
- Fast verification
- Good for single-service or trusted services

**Cons:**
- Secret must be shared (security risk)
- All services need the same secret

#### Asymmetric Algorithms (Public/Private Key)
- **RS256** (RSA SHA-256): Most common asymmetric
- **ES256** (ECDSA P-256): Smaller keys, faster
- **PS256** (RSA-PSS SHA-256): More secure than RS256

**Pros:**
- Only issuer needs private key
- Services only need public key (can be published)
- Better for distributed systems

**Cons:**
- Slower than symmetric
- More complex key management

**Recommendation:**
- **RS256** for production microservices (better security)
- **HS256** for development or single-service apps

### JWT Security Considerations

#### 1. Token Storage
**❌ Don't store in localStorage** (XSS vulnerability)
```javascript
// BAD - vulnerable to XSS
localStorage.setItem('token', jwt);
```

**✅ Prefer httpOnly cookies** (XSS protection)
```javascript
// GOOD - httpOnly cookies not accessible to JavaScript
// Set by server: Set-Cookie: token=...; HttpOnly; Secure; SameSite=Strict
```

**✅ Or use memory** (cleared on page close)
```javascript
// GOOD - stored in memory, cleared on refresh
let token = null;  // In-memory variable
```

#### 2. Token Expiration
- **Short-lived access tokens**: 15 minutes - 1 hour
- **Long-lived refresh tokens**: 7-30 days (stored securely)
- **Refresh token rotation**: Issue new refresh token on each use

#### 3. Token Revocation
**Problem**: JWT is stateless - can't revoke without checking a blacklist

**Solutions:**
- **Token blacklist**: Store revoked tokens in Redis/DB (check on each request)
- **Short expiration**: Use short-lived tokens (15 min) + refresh tokens
- **Token versioning**: Include version in JWT, invalidate by incrementing version

#### 4. Algorithm Confusion Attack
**Problem**: Attacker changes `alg: none` or `alg: HS256` to bypass signature verification

**Solution**: Always explicitly specify allowed algorithms
```java
Jwts.parser()
    .setSigningKey(publicKey)
    .requireAlgorithm("RS256")  // Explicitly require RS256
    .parseClaimsJws(token);
```

#### 5. Information Disclosure
**Problem**: JWT payload is Base64 encoded (not encrypted) - anyone can decode

**Solution**: 
- Don't store sensitive data (passwords, SSN, credit cards)
- Use JWE (JSON Web Encryption) if encryption needed
- Only store necessary claims

### Common JWT Interview Questions

#### Q1: What's the difference between JWT and session tokens?

| Aspect | JWT | Session Token |
|--------|-----|---------------|
| **Storage** | Client-side | Server-side (database/cache) |
| **State** | Stateless | Stateful |
| **Scalability** | Easy to scale (no shared state) | Requires shared session store |
| **Revocation** | Difficult (need blacklist) | Easy (delete session) |
| **Size** | Larger (contains claims) | Smaller (just ID) |
| **Use Case** | Microservices, distributed systems | Traditional web apps |

#### Q2: How do you handle JWT token refresh?

**Pattern: Access Token + Refresh Token**

```java
// Login response
{
  "accessToken": "eyJ...",      // Short-lived (15 min)
  "refreshToken": "eyJ...",     // Long-lived (7 days)
  "expiresIn": 900              // 15 minutes
}

// When access token expires:
POST /api/refresh
Authorization: Bearer <refreshToken>

// Response:
{
  "accessToken": "eyJ...",      // New access token
  "refreshToken": "eyJ...",     // New refresh token (rotation)
  "expiresIn": 900
}
```

**Benefits:**
- Access tokens are short-lived (less risk if stolen)
- Refresh tokens can be revoked
- Refresh token rotation prevents replay attacks

#### Q3: How do you revoke a JWT token?

**Option 1: Token Blacklist**
```java
// On logout/revocation
tokenBlacklistService.add(tokenId, expirationTime);

// On each request
if (tokenBlacklistService.isBlacklisted(tokenId)) {
    throw new UnauthorizedException();
}
```

**Option 2: Short Expiration + Refresh Token**
- Access tokens expire quickly (15 min)
- If compromised, impact is limited
- Refresh tokens can be revoked

**Option 3: Token Versioning**
```json
{
  "sub": "user:123",
  "tokenVersion": 5,  // Increment to invalidate all tokens
  "exp": 1516242622
}
```

#### Q4: What happens if a JWT is stolen?

**Immediate Actions:**
1. **Revoke refresh token** (if using refresh token pattern)
2. **Add to blacklist** (if using blacklist)
3. **Change user password** (forces new token issuance)
4. **Rotate signing keys** (invalidates all tokens - nuclear option)

**Prevention:**
- Short token expiration
- Use HTTPS only
- Store tokens securely (httpOnly cookies)
- Implement token rotation
- Monitor for suspicious activity

#### Q5: Can you decode a JWT without the secret?

**Yes!** The payload is Base64 encoded, not encrypted. Anyone can decode it:

```bash
# Decode JWT payload (no secret needed)
echo "eyJzdWIiOiJ1c2VyOjEyMzQ1In0" | base64 -d
# Output: {"sub":"user:12345"}
```

**But you CANNOT:**
- Modify the payload (signature verification will fail)
- Create a valid token (need the secret/key)
- Verify the signature (need the secret/key)

#### Q6: What's the difference between JWT and OAuth2?

| Aspect | JWT | OAuth2 |
|--------|-----|--------|
| **What it is** | Token format | Authorization framework |
| **Purpose** | How to encode token data | How to obtain authorization |
| **Relationship** | OAuth2 can use JWT as access token | JWT is a token format used by OAuth2 |
| **Scope** | Token structure | Complete authorization flow |

**OAuth2 Flow with JWT:**
1. Client requests authorization (OAuth2)
2. Authorization server issues JWT access token (JWT format)
3. Client uses JWT to access protected resources

#### Q7: How do you secure JWT in a microservices architecture?

**Best Practices:**
1. **Use RS256** (asymmetric) - only auth service has private key
2. **Publish public keys** via JWKS endpoint (`/.well-known/jwks.json`)
3. **Validate on each service** - don't trust other services
4. **Short expiration** - 15 minutes for access tokens
5. **Include audience claim** - restrict token to specific services
6. **Use HTTPS** - prevent token interception
7. **Validate all claims** - exp, iss, aud, nbf

```java
// Microservice validates JWT using public key from JWKS
Jwts.parser()
    .setSigningKeyResolver(new SigningKeyResolverAdapter() {
        @Override
        public Key resolveSigningKey(JwsHeader header, Claims claims) {
            // Fetch public key from JWKS endpoint
            return fetchPublicKeyFromJWKS(header.getKeyId());
        }
    })
    .requireIssuer("auth-service")
    .requireAudience("payment-service")
    .parseClaimsJws(token);
```

### JWT vs Other Token Formats

| Format | Use Case | Pros | Cons |
|--------|----------|------|------|
| **JWT** | Stateless auth, microservices | Self-contained, scalable | Can't revoke easily |
| **Opaque Token** | Traditional sessions | Easy revocation | Requires database lookup |
| **SAML** | Enterprise SSO | Mature, enterprise-ready | Complex, XML-based |
| **PASETO** | JWT alternative | Simpler, more secure | Less adoption |

---

## JWT Integration

### JWT Token Structure

JWT tokens carry the principal and context needed for policy evaluation:

```json
{
  "sub": "user:john.doe",           // Principal identifier
  "roles": ["customer", "premium"],  // RBAC roles
  "department": "retail",            // ABAC attributes
  "accountStatus": "active",         // ABAC attributes
  "appId": "payment-service",        // Which app's policies to use
  "env": "prod",                     // Which environment (dev/stg/prod)
  "iat": 1234567890,
  "exp": 1234571490
}
```

### JWT Claims Mapping

| JWT Claim | Cedar Component | Purpose |
|-----------|----------------|---------|
| `sub` | Principal | User/service identifier |
| `roles` | Principal (RBAC) | Role-based authorization |
| `department`, `accountStatus` | Context (ABAC) | Attribute-based authorization |
| `appId` | Policy Selection | Which app's policies to load |
| `env` | Policy Selection | Which environment (dev/stg/prod) |

---

## Policy Structure

### Basic Cedar Policy Syntax

```cedar
permit(
    principal == <PRINCIPAL>,
    action == <ACTION>,
    resource == <RESOURCE>
) when { <CONDITIONS> };
```

### RBAC-Only Policy

```cedar
// Policy: admin-policy.cedar
permit(
    principal in Role::"admin",  // RBAC check: Is user in admin role?
    action == Action::"delete",
    resource == Resource::"account"
);
```

**Internal Flow:**
1. Request: `principal=User::"john.doe"`, `action=delete`, `resource=account`
2. Policy engine checks: Is `john.doe` in `Role::"admin"`?
3. If yes → permit; if no → deny

### ABAC-Only Policy

```cedar
// Policy: high-balance-policy.cedar
permit(
    principal == User::"john.doe",
    action == Action::"transfer",
    resource == Resource::"account"
) when {
    resource.balance > 1000  // ABAC check: Is balance > 1000?
};
```

**Internal Flow:**
1. Request: `principal=User::"john.doe"`, `action=transfer`, `resource=account::123`
2. Policy engine fetches `account::123.balance` from context/entity store
3. Evaluates: `balance > 1000`
4. If true → permit; if false → deny

### Combined RBAC + ABAC Policy

```cedar
// payment-service/prod/policy.cedar

permit(
    principal in Role::"customer" || principal in Role::"teller",  // RBAC
    action == Action::"transfer",
    resource == Resource::"account"
) when {
    // ABAC checks
    resource.balance >= 1000 &&  // Account has sufficient balance
    principal.accountStatus == "active" &&  // User account is active
    context.transferAmount <= resource.balance &&  // Transfer amount valid
    context.timeOfDay >= 9 && context.timeOfDay <= 17  // Business hours
};
```

---

## Implementation Flow

### Complete Request Flow

**Step 1: Request arrives with JWT**
```http
POST /api/accounts/12345/transfer
Authorization: Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...
Content-Type: application/json

{
  "amount": 500,
  "toAccount": "67890"
}
```

**Step 2: API Gateway (PEP) extracts JWT and builds authorization request**

**Step 3: Cedar PDP evaluates policies**

**Step 4: Decision returned to PEP**

**Step 5: Request allowed or denied**

### Detailed Evaluation Process

```
1. Request arrives:
   - Principal: User::"john.doe" (or Role::"admin")
   - Action: Action::"transfer"
   - Resource: Account::"12345"
   - Context: { balance: 5000, timeOfDay: 14, department: "retail" }

2. Policy Engine:
   a. Loads relevant Cedar policy for the app/environment
   b. Evaluates PRINCIPAL:
      - RBAC: Checks if user has required role
      - ABAC: Checks principal attributes (department, clearance level, etc.)
   
   c. Evaluates ACTION:
      - Is this action allowed for this principal-resource combination?
   
   d. Evaluates RESOURCE:
      - ABAC: Checks resource attributes (balance, status, etc.)
   
   e. Evaluates CONDITIONS (when clause):
      - All ABAC checks: balance > 1000, time constraints, etc.

3. Decision:
   - If ALL checks pass → PERMIT
   - If ANY check fails → DENY (default deny)
```

### Authorization Algorithm

Cedar follows three key principles:

1. **Default Deny**: No authorization unless explicitly permitted
2. **Forbid Overrides Permit**: Explicit forbid always wins
3. **Skip on Error**: If evaluation error occurs, skip that policy (don't fail open)

---

## Code Examples

### PEP Implementation (Policy Enforcement Point)

```java
// Pseudo-code for PEP
public class CedarAuthorizationFilter {
    
    private final CedarPolicyDecisionPoint cedarPDP;
    private final AccountService accountService;
    
    public AuthorizationDecision authorize(HttpRequest request) {
        // 1. Extract JWT
        JwtToken jwt = extractJwt(request);
        
        // 2. Validate JWT
        if (!jwt.isValid()) {
            return AuthorizationDecision.DENY;
        }
        
        // 3. Parse JWT claims
        String principal = jwt.getClaim("sub");  // "user:john.doe"
        List<String> roles = jwt.getClaim("roles");  // ["customer"]
        Map<String, Object> attributes = jwt.getClaims();  // All ABAC attributes
        
        // 4. Extract action and resource from request
        String action = mapHttpMethodToAction(request.getMethod());  // "transfer"
        String resource = extractResource(request.getPath());  // "account::12345"
        
        // 5. Build context (for ABAC)
        Map<String, Object> context = new HashMap<>();
        context.put("balance", accountService.getBalance("12345"));  // 5000
        context.put("timeOfDay", getCurrentHour());  // 14
        context.put("transferAmount", request.getBody().get("amount"));  // 500
        context.putAll(attributes);  // Add JWT claims to context
        
        // 6. Call Cedar PDP
        AuthorizationRequest authRequest = AuthorizationRequest.builder()
            .principal(principal)
            .action(action)
            .resource(resource)
            .context(context)
            .appId(jwt.getClaim("appId"))
            .environment(jwt.getClaim("env"))
            .build();
            
        return cedarPDP.isAuthorized(authRequest);
    }
    
    private String mapHttpMethodToAction(String method) {
        switch (method) {
            case "GET": return "read";
            case "POST": return "create";
            case "PUT": return "update";
            case "DELETE": return "delete";
            default: return method.toLowerCase();
        }
    }
    
    private String extractResource(String path) {
        // Extract resource from path: /api/accounts/12345 -> account::12345
        String[] parts = path.split("/");
        if (parts.length >= 4) {
            return parts[2] + "::" + parts[3];  // account::12345
        }
        return "unknown";
    }
}
```

### PDP Implementation (Policy Decision Point)

```java
// Pseudo-code for Cedar PDP
public class CedarPolicyDecisionPoint {
    
    private final CedarPolicyStore policyStore;
    private final Cache<String, List<CedarPolicy>> policyCache;
    
    public AuthorizationDecision isAuthorized(AuthorizationRequest request) {
        // 1. Load relevant policies based on appId and env
        String appId = request.getAppId();
        String env = request.getEnvironment();
        String cacheKey = appId + ":" + env;
        
        List<CedarPolicy> policies = policyCache.get(cacheKey, () -> {
            return policyStore.loadPolicies(appId, env);
        });
        
        // 2. Evaluate each policy
        boolean hasPermit = false;
        boolean hasForbid = false;
        
        for (CedarPolicy policy : policies) {
            // Check if policy matches this request
            if (!policy.matches(request)) {
                continue;
            }
            
            // Evaluate RBAC: Check if principal has required role
            if (policy.hasRoleRequirement()) {
                List<String> userRoles = (List<String>) request.getContext().get("roles");
                if (userRoles == null || !userRoles.contains(policy.getRequiredRole())) {
                    continue;  // Skip this policy
                }
            }
            
            // Evaluate ABAC: Check conditions
            if (policy.hasConditions()) {
                try {
                    boolean conditionsMet = evaluateConditions(
                        policy.getConditions(), 
                        request.getContext()
                    );
                    if (!conditionsMet) {
                        continue;  // Skip this policy
                    }
                } catch (Exception e) {
                    // Skip on error - don't fail open
                    log.warn("Error evaluating policy conditions", e);
                    continue;
                }
            }
            
            // Policy matched and conditions met
            if (policy.getEffect() == Effect.PERMIT) {
                hasPermit = true;
            } else if (policy.getEffect() == Effect.FORBID) {
                hasForbid = true;
                break;  // Forbid overrides permit
            }
        }
        
        // 3. Make decision
        if (hasForbid) {
            return AuthorizationDecision.DENY;  // Forbid overrides permit
        }
        if (hasPermit) {
            return AuthorizationDecision.ALLOW;
        }
        
        // Default deny if no policy permits
        return AuthorizationDecision.DENY;
    }
    
    private boolean evaluateConditions(
        List<Condition> conditions, 
        Map<String, Object> context
    ) {
        for (Condition condition : conditions) {
            // Example: "resource.balance > 1000"
            if (condition.getType() == ConditionType.GREATER_THAN) {
                Object value = getValueFromContext(condition.getField(), context);
                Object threshold = condition.getThreshold();
                if (value == null || !compareGreaterThan(value, threshold)) {
                    return false;
                }
            }
            
            // Example: "context.timeOfDay >= 9 && context.timeOfDay <= 17"
            if (condition.getType() == ConditionType.RANGE) {
                Object value = getValueFromContext(condition.getField(), context);
                if (value == null || !isInRange(value, condition.getMin(), condition.getMax())) {
                    return false;
                }
            }
            
            // Example: "principal.accountStatus == 'active'"
            if (condition.getType() == ConditionType.EQUALS) {
                Object value = getValueFromContext(condition.getField(), context);
                if (!Objects.equals(value, condition.getExpectedValue())) {
                    return false;
                }
            }
        }
        return true;
    }
    
    private Object getValueFromContext(String fieldPath, Map<String, Object> context) {
        // Handle nested paths like "resource.balance" or "principal.accountStatus"
        String[] parts = fieldPath.split("\\.");
        Object current = context;
        
        for (String part : parts) {
            if (current instanceof Map) {
                current = ((Map<String, Object>) current).get(part);
            } else {
                return null;
            }
        }
        return current;
    }
}
```

### Policy Store Implementation

```java
public class CedarPolicyStore {
    
    // Could be stored in:
    // - S3/GCS buckets (appId/env/policy.cedar)
    // - Database (policies table)
    // - Config service (Consul, etcd)
    // - Git repository
    
    private final StorageService storageService;
    private final CedarPolicyParser parser;
    
    public List<CedarPolicy> loadPolicies(String appId, String env) {
        String policyPath = String.format("%s/%s/policy.cedar", appId, env);
        
        // Load from storage
        String policyContent = storageService.read(policyPath);
        
        // Parse Cedar policy syntax
        return parser.parse(policyContent);
    }
}
```

### Complete Example: Transfer Request

**JWT Token:**
```json
{
  "sub": "user:john.doe",
  "roles": ["customer"],
  "department": "retail",
  "accountStatus": "active",
  "appId": "payment-service",
  "env": "prod",
  "iat": 1234567890,
  "exp": 1234571490
}
```

**Cedar Policy (payment-service/prod/policy.cedar):**
```cedar
permit(
    principal in Role::"customer",  // RBAC: Check JWT roles claim
    action == Action::"transfer",
    resource == Resource::"account"
) when {
    resource.balance >= 1000 &&  // ABAC: From context
    principal.accountStatus == "active" &&  // ABAC: From JWT
    context.timeOfDay >= 9 && context.timeOfDay <= 17  // ABAC: From context
};
```

**Request Flow:**
1. **Request**: `POST /api/accounts/12345/transfer` with JWT
2. **PEP extracts**:
   - Principal: `user:john.doe` (from JWT `sub`)
   - Roles: `["customer"]` (from JWT `roles`)
   - Action: `transfer` (from HTTP method/path)
   - Resource: `account::12345` (from path)
   - Context: `{balance: 5000, timeOfDay: 14, accountStatus: "active"}` (from JWT + DB)
3. **PDP loads** `payment-service/prod/policy.cedar`
4. **PDP evaluates**:
   - RBAC: `"customer" in ["customer"]` → ✅
   - ABAC: `5000 >= 1000` → ✅
   - ABAC: `"active" == "active"` → ✅
   - ABAC: `14 >= 9 && 14 <= 17` → ✅
5. **Decision**: **ALLOW**
6. **Request proceeds** to business logic

---

## Key Implementation Details

### JWT Validation

Before policy evaluation, JWT must be validated:

```java
public boolean validateJwt(JwtToken jwt) {
    // 1. Signature verification (RS256/HS256)
    if (!jwt.verifySignature()) {
        return false;
    }
    
    // 2. Expiration check
    if (jwt.isExpired()) {
        return false;
    }
    
    // 3. Issuer validation
    if (!jwt.getIssuer().equals(expectedIssuer)) {
        return false;
    }
    
    // 4. Token revocation check (optional - check against blacklist)
    if (tokenRevocationService.isRevoked(jwt.getId())) {
        return false;
    }
    
    return true;
}
```

### Context Enrichment

Context combines static and dynamic attributes:

```java
Map<String, Object> context = new HashMap<>();

// Static attributes from JWT
context.put("roles", jwt.getClaim("roles"));
context.put("department", jwt.getClaim("department"));
context.put("accountStatus", jwt.getClaim("accountStatus"));

// Dynamic attributes fetched at runtime
context.put("balance", accountService.getBalance(accountId));
context.put("timeOfDay", getCurrentHour());
context.put("transferAmount", request.getBody().get("amount"));

// Resource attributes
context.put("resource.balance", accountService.getBalance(accountId));
context.put("resource.status", accountService.getStatus(accountId));
```

### Performance Optimizations

1. **Policy Caching**: Policies don't change frequently, cache them
2. **Decision Caching**: Cache Allow/Deny decisions for short TTL (5-30 seconds)
3. **Batch Evaluation**: Evaluate multiple requests together
4. **Async Context Fetching**: Fetch dynamic attributes in parallel

```java
// Policy caching
private final Cache<String, List<CedarPolicy>> policyCache = 
    Caffeine.newBuilder()
        .maximumSize(1000)
        .expireAfterWrite(1, TimeUnit.HOURS)
        .build();

// Decision caching
private final Cache<String, AuthorizationDecision> decisionCache = 
    Caffeine.newBuilder()
        .maximumSize(10000)
        .expireAfterWrite(10, TimeUnit.SECONDS)
        .build();
```

### Security Considerations

1. **Default Deny**: If no policy permits, deny by default
2. **Forbid Overrides Permit**: Explicit forbid always wins
3. **Policy Versioning**: Track policy changes for audit
4. **Audit Logging**: Log all authorization decisions

```java
public AuthorizationDecision isAuthorized(AuthorizationRequest request) {
    AuthorizationDecision decision = evaluatePolicies(request);
    
    // Audit logging
    auditLogger.log(new AuditEvent(
        request.getPrincipal(),
        request.getAction(),
        request.getResource(),
        decision,
        System.currentTimeMillis()
    ));
    
    return decision;
}
```

---

## Policy Logic Clarification: Understanding the `when` Clause

### Common Misunderstanding

**Question:** "Does `resource.balance >= 1000` mean that only users with balance > 1000 are allowed to invoke the method?"

**Answer:** No! The `when` clause uses **AND logic** - **ALL conditions must be true simultaneously** for the permit to be granted.

### Correct Interpretation

```cedar
permit(
    principal in Role::"customer",  // Condition 1: Must have "customer" role
    action == Action::"transfer",
    resource == Resource::"account"
) when {
    resource.balance >= 1000 &&           // Condition 2: Balance must be >= 1000
    principal.accountStatus == "active" && // Condition 3: Account must be active
    context.timeOfDay >= 9 &&             // Condition 4: Time must be >= 9 AM
    context.timeOfDay <= 17               // Condition 5: Time must be <= 5 PM
};
```

**This policy permits the transfer action ONLY when:**
1. ✅ User has "customer" role (RBAC check)
2. ✅ Account balance is >= 1000 (ABAC check)
3. ✅ Account status is "active" (ABAC check)
4. ✅ Current time is between 9 AM and 5 PM (ABAC check)

**If ANY condition fails, the request is DENIED.**

### Example Scenarios

| Scenario | Balance | Status | Time | Role | Result |
|----------|---------|--------|------|------|--------|
| Scenario 1 | 5000 | active | 14:00 | customer | ✅ **ALLOW** (all conditions met) |
| Scenario 2 | 500 | active | 14:00 | customer | ❌ **DENY** (balance < 1000) |
| Scenario 3 | 5000 | suspended | 14:00 | customer | ❌ **DENY** (status != "active") |
| Scenario 4 | 5000 | active | 20:00 | customer | ❌ **DENY** (outside business hours) |
| Scenario 5 | 5000 | active | 14:00 | admin | ❌ **DENY** (wrong role) |

### Comparison with FastAPI Entitlement Client Pattern

**Multiple decorators are overkill!** Instead, you would use a single **entitlement client** that handles all authorization checks generically:

```python
# FastAPI with Entitlement Client (Actual Pattern)
from entitlement_client import EntitlementClient

entitlement_client = EntitlementClient(authorization_service_url="...")

@entitlement_check(
    action="transfer",
    resource="account",
    entitlement_client=entitlement_client
)
@router.post("/api/accounts/{account_id}/transfer")
async def transfer_money(account_id: str, transfer_request: TransferRequest):
    # Business logic here
    # The entitlement client internally:
    # 1. Extracts principal, roles from JWT
    # 2. Fetches context (balance, accountStatus, timeOfDay)
    # 3. Calls authorization service API
    # 4. Returns Allow/Deny decision
    ...
```

**How Entitlement Client Works:**
1. **Extracts inputs generically** from request (JWT, path params, body, etc.)
2. **Builds authorization request** with principal, action, resource, context
3. **Calls authorization service API** (which evaluates Cedar policies)
4. **Returns Allow/Deny** decision
5. **Enforces decision** (allows request to proceed or denies with 403)

**This is exactly the Cedar PEP (Policy Enforcement Point) pattern!**
- **Entitlement Client** = PEP (Policy Enforcement Point)
- **Authorization Service** = PDP (Policy Decision Point) with Cedar policies

### Spring Boot Equivalent (Entitlement Client Pattern)

In Spring Boot, you would use a single **entitlement client** with a generic annotation or interceptor:

```java
// Simple annotation - just specifies action and resource
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireAuthorization {
    String action();      // e.g., "transfer"
    String resource();    // e.g., "account"
}

// Usage in controller - clean and simple!
@RestController
@RequestMapping("/api/accounts")
public class AccountController {
    
    @RequireAuthorization(action = "transfer", resource = "account")
    @PostMapping("/{accountId}/transfer")
    public ResponseEntity<TransferResponse> transfer(
            @PathVariable String accountId,
            @RequestBody TransferRequest request) {
        // Business logic here
        // Authorization already checked by entitlement client
        ...
    }
}

// Entitlement Client (PEP) - handles all authorization generically
@Component
public class EntitlementClient {
    
    @Autowired
    private AuthorizationServiceClient authServiceClient;  // Calls authorization service API
    
    @Autowired
    private AccountService accountService;  // To fetch balance, etc.
    
    public AuthorizationDecision checkAuthorization(
            HttpServletRequest request,
            String action,
            String resource) {
        
        // 1. Extract principal and roles from JWT
        JwtToken jwt = extractJwt(request);
        String principal = jwt.getClaim("sub");
        List<String> roles = jwt.getClaim("roles");
        Map<String, Object> attributes = jwt.getClaims();
        
        // 2. Extract resource ID from request
        String resourceId = extractResourceId(request, resource);
        
        // 3. Build context (balance, time, accountStatus, etc.)
        Map<String, Object> context = buildContext(request, resourceId, attributes);
        
        // 4. Call authorization service API
        AuthorizationRequest authRequest = AuthorizationRequest.builder()
            .principal(principal)
            .action(action)
            .resource(resource + "::" + resourceId)
            .context(context)
            .build();
            
        return authServiceClient.isAuthorized(authRequest);  // Returns Allow/Deny
    }
    
    private Map<String, Object> buildContext(
            HttpServletRequest request, 
            String resourceId,
            Map<String, Object> jwtAttributes) {
        Map<String, Object> context = new HashMap<>();
        
        // Add JWT attributes
        context.putAll(jwtAttributes);
        
        // Add dynamic attributes
        context.put("balance", accountService.getBalance(resourceId));
        context.put("timeOfDay", getCurrentHour());
        context.put("resource.balance", accountService.getBalance(resourceId));
        context.put("resource.status", accountService.getStatus(resourceId));
        
        return context;
    }
}

// AOP Aspect - intercepts @RequireAuthorization and calls entitlement client
@Aspect
@Component
public class AuthorizationAspect {
    
    @Autowired
    private EntitlementClient entitlementClient;
    
    @Around("@annotation(requireAuthorization)")
    public Object checkAuthorization(ProceedingJoinPoint joinPoint, 
                                    RequireAuthorization annotation) {
        HttpServletRequest request = getRequest(joinPoint);
        
        AuthorizationDecision decision = entitlementClient.checkAuthorization(
            request,
            annotation.action(),
            annotation.resource()
        );
        
        if (decision == AuthorizationDecision.DENY) {
            throw new ForbiddenException("Access denied");
        }
        
        return joinPoint.proceed();
    }
}
```

**Key Benefits:**
1. **Single annotation** per endpoint - just action and resource
2. **Entitlement client** handles all context building generically
3. **Authorization service** (PDP) evaluates Cedar policies
4. **Authorization logic** is externalized in policy files, not code
5. **Easy to maintain** - change policies without code changes

### Architecture Mapping: Entitlement Client = PEP

```
┌─────────────────────────────────────────────────────────────┐
│                    Your FastAPI/Spring Boot App              │
│                                                              │
│  @entitlement_check(action="transfer", resource="account")  │
│  @PostMapping("/transfer")                                  │
│  def transfer_money(...):                                   │
│      # Business logic                                       │
│                                                              │
│  ┌──────────────────────────────────────────────────────┐  │
│  │         Entitlement Client (PEP)                      │  │
│  │  - Extracts JWT, principal, roles                     │  │
│  │  - Builds context (balance, time, status)             │  │
│  │  - Calls authorization service API                    │  │
│  │  - Returns Allow/Deny                                 │  │
│  └──────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
                            │
                            │ HTTP Request
                            │ (Principal, Action, Resource, Context)
                            ▼
┌─────────────────────────────────────────────────────────────┐
│              Authorization Service (PDP)                     │
│                                                              │
│  ┌──────────────────────────────────────────────────────┐  │
│  │         Cedar Policy Engine                           │  │
│  │  - Loads policies from storage                        │  │
│  │  - Evaluates RBAC (role checks)                       │  │
│  │  - Evaluates ABAC (balance, time, status checks)      │  │
│  │  - Returns Allow/Deny decision                        │  │
│  └──────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
                            │
                            │ Policy Storage
                            ▼
                payment-service/prod/policy.cedar
```

**The entitlement client is your PEP (Policy Enforcement Point)** - it:
- Intercepts requests
- Extracts authorization data
- Calls the authorization service (PDP)
- Enforces the decision

**The authorization service is your PDP (Policy Decision Point)** - it:
- Loads Cedar policies
- Evaluates them against the request
- Returns Allow/Deny

This architecture keeps authorization logic **centralized** and **decoupled** from business logic!

---

## Interview Explanation

### Concise Version (30 seconds)

"I implemented Cedar-based authorization using a centralized Policy Decision Point (PDP) architecture. When requests arrive, the Policy Enforcement Point (PEP) extracts JWT tokens containing principal, roles, and attributes. The PEP builds authorization requests with principal, action, resource, and enriched context—combining JWT claims with dynamic attributes like account balance. The Cedar PDP loads relevant policies based on appId and environment, then evaluates both RBAC (role checks) and ABAC (contextual attribute checks). This provides fine-grained, context-aware access control while keeping authorization logic decoupled from business logic."

### Detailed Version (2-3 minutes)

"I implemented a Cedar-based authorization system similar to AWS IAM policies for our microservices architecture. The system uses a centralized Policy Decision Point (PDP) pattern where Policy Enforcement Points (PEPs) are deployed at API Gateway and individual microservices.

When a request arrives with a JWT token, the PEP extracts the principal identifier, RBAC roles, and ABAC attributes from the JWT claims. It then enriches the context with dynamic attributes like account balance fetched from the database, current time, and transaction amounts. This creates a complete authorization request with principal, action, resource, and context.

The authorization request is sent to our Cedar PDP, which loads the relevant policies based on the appId and environment specified in the JWT. Each microservice has its own policy configuration organized by environment (dev/stg/prod), stored in a centralized policy store.

The PDP evaluates policies by first checking RBAC conditions—verifying if the user has the required role from the JWT's roles claim. Then it evaluates ABAC conditions—checking if contextual attributes like balance, time of day, and account status meet the policy requirements. The evaluation follows Cedar's principles: default deny, forbid overrides permit, and skip on error.

This architecture provides several benefits: fine-grained access control at the microservice level, context-aware decisions based on real-time attributes, improved security posture through default deny, and clear separation of authorization logic from business logic. It's similar to how AWS IAM policies work, but tailored for our microservices ecosystem."

### Key Points to Emphasize

1. **Architecture**: Centralized PDP with distributed PEPs
2. **JWT Integration**: JWT carries principal, roles, and attributes
3. **RBAC**: Role-based checks using JWT roles claim
4. **ABAC**: Attribute-based checks using context (balance, time, etc.)
5. **Policy Organization**: Per-app, per-environment policy structure
6. **Benefits**: Fine-grained, context-aware, secure, decoupled

---

## Additional Resources

### Cedar Policy Language
- Official Documentation: https://docs.cedarpolicy.com/
- Policy Syntax: https://docs.cedarpolicy.com/policies/syntax.html
- Authorization Guide: https://docs.cedarpolicy.com/auth/authorization.html

### AWS Verified Permissions
- Similar implementation using Cedar
- Reference architecture for microservices

### Best Practices

1. **Policy Design**:
   - Start with least privilege
   - Use RBAC for broad access control
   - Use ABAC for fine-grained conditions
   - Combine both for comprehensive security

2. **Performance**:
   - Cache policies (they change infrequently)
   - Cache decisions (short TTL)
   - Minimize context fetching (batch when possible)

3. **Security**:
   - Always default deny
   - Validate JWT thoroughly
   - Audit all decisions
   - Version policies for rollback

4. **Maintainability**:
   - Organize policies by app and environment
   - Use clear naming conventions
   - Document policy intent
   - Test policies thoroughly

---

## Summary

This implementation provides:

✅ **Fine-grained access control** at microservice level  
✅ **Context-aware authorization** based on real-time attributes  
✅ **Improved security posture** through default deny  
✅ **Scalable architecture** with centralized PDP  
✅ **Clear separation** of authorization and business logic  
✅ **Flexible policy model** combining RBAC and ABAC  

The system enables secure, scalable, and maintainable authorization across microservices while providing the flexibility to make dynamic access control decisions based on both static roles and contextual attributes.
