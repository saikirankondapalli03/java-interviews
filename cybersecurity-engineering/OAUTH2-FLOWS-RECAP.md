# OAuth2 Flows: Complete Recap Guide

## Overview

This document provides a comprehensive recap of OAuth2 authorization flows. OAuth2 is an authorization framework that enables applications to obtain limited access to user accounts on an HTTP service. It works by delegating user authentication to the service that hosts the user account and authorizing third-party applications to access the user account.

## Table of Contents

1. [Core Concepts](#core-concepts)
2. [OAuth2 Roles](#oauth2-roles)
3. [Authorization Code Flow](#authorization-code-flow)
4. [Authorization Code Flow with PKCE](#authorization-code-flow-with-pkce)
5. [Implicit Flow (Deprecated)](#implicit-flow-deprecated)
6. [Client Credentials Flow](#client-credentials-flow)
7. [Resource Owner Password Credentials Flow (Deprecated)](#resource-owner-password-credentials-flow-deprecated)
8. [Device Flow](#device-flow)
9. [Refresh Token Flow](#refresh-token-flow)
10. [Flow Comparison](#flow-comparison)
11. [Security Best Practices](#security-best-practices)

---

## Core Concepts

### What is OAuth2?

**OAuth2** (Open Authorization 2.0) is an authorization framework that allows third-party services to access user resources without exposing user credentials. It's about **authorization**, not authentication.

### Key Terminology

| Term | Description | Example |
|------|-------------|---------|
| **Resource Owner** | The user who owns the protected resource | John wants to share his Google photos |
| **Client** | Application requesting access | Photo printing app |
| **Authorization Server** | Issues access tokens after authenticating the resource owner | Google OAuth server |
| **Resource Server** | Hosts protected resources, accepts access tokens | Google Photos API |
| **Access Token** | Token used to access protected resources | `eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...` |
| **Refresh Token** | Token used to obtain new access tokens | `refresh_token_abc123...` |
| **Authorization Code** | Temporary code exchanged for access token | `4/0AY0e-g7...` |
| **Redirect URI** | Where authorization server sends user after authorization | `https://app.com/callback` |
| **Scope** | Permissions requested by client | `read:photos`, `write:photos` |

### OAuth2 vs Authentication

**OAuth2 is NOT authentication** - it's authorization. However, it's often used alongside OpenID Connect (OIDC) for authentication:

- **OAuth2**: "Can this app access my photos?" (Authorization)
- **OIDC**: "Who is this user?" (Authentication)
- **OAuth2 + OIDC**: "Who is this user AND can this app access their photos?"

---

## OAuth2 Roles

```
┌─────────────────┐
│ Resource Owner  │  The user who owns the data
│    (User)       │
└─────────────────┘
         │
         │ Grants permission
         ▼
┌─────────────────┐         ┌──────────────────┐
│     Client      │────────▶│ Authorization    │
│  (Application)  │         │    Server        │
└─────────────────┘         └──────────────────┘
         │                           │
         │ Uses access token         │ Issues tokens
         ▼                           │
┌─────────────────┐                 │
│   Resource      │◀────────────────┘
│    Server       │
│  (API/Service)  │
└─────────────────┘
```

---

## Authorization Code Flow

### Overview

The **Authorization Code Flow** is the most secure and commonly used OAuth2 flow. It's designed for server-side applications where the client secret can be kept confidential.

### Flow Diagram

```
┌─────────────┐                                              ┌──────────────────┐
│   Browser   │                                              │  Authorization   │
│   (User)    │                                              │     Server       │
└──────┬──────┘                                              └────────┬─────────┘
       │                                                              │
       │ 1. User clicks "Login with Google"                         │
       │───────────────────────────────────────────────────────────▶│
       │                                                              │
       │ 2. Redirect to authorization endpoint                       │
       │    GET /authorize?client_id=...&redirect_uri=...&scope=... │
       │                                                              │
       │ 3. User authenticates and grants permission                 │
       │◀───────────────────────────────────────────────────────────│
       │                                                              │
       │ 4. Authorization code returned                              │
       │    Redirect: https://app.com/callback?code=AUTHORIZATION_CODE
       │───────────────────────────────────────────────────────────▶│
       │                                                              │
       │                                                              │
┌──────┴──────┐                                              ┌────────┴─────────┐
│   Client    │                                              │  Authorization   │
│ Application │                                              │     Server       │
│  (Backend)  │                                              └────────┬─────────┘
└──────┬──────┘                                                       │
       │                                                              │
       │ 5. Exchange authorization code for tokens                    │
       │    POST /token                                              │
       │    {                                                        │
       │      grant_type: "authorization_code",                     │
       │      code: "AUTHORIZATION_CODE",                           │
       │      redirect_uri: "https://app.com/callback",             │
       │      client_id: "...",                                     │
       │      client_secret: "..."                                  │
       │    }                                                        │
       │───────────────────────────────────────────────────────────▶│
       │                                                              │
       │ 6. Access token + refresh token returned                    │
       │◀───────────────────────────────────────────────────────────│
       │    {                                                        │
       │      access_token: "eyJ...",                               │
       │      refresh_token: "abc123...",                           │
       │      expires_in: 3600,                                     │
       │      token_type: "Bearer"                                   │
       │    }                                                        │
       │                                                              │
       │                                                              │
┌──────┴──────┐                                              ┌────────┴─────────┐
│   Client    │                                              │   Resource       │
│ Application │                                              │     Server       │
└──────┬──────┘                                              └────────┬─────────┘
       │                                                              │
       │ 7. Use access token to access protected resources           │
       │    GET /api/photos                                           │
       │    Authorization: Bearer eyJ...                            │
       │───────────────────────────────────────────────────────────▶│
       │                                                              │
       │ 8. Protected resource returned                              │
       │◀───────────────────────────────────────────────────────────│
       │    { photos: [...] }                                        │
```

### Step-by-Step Explanation

1. **User initiates login**: User clicks "Login with Google" button in your app
2. **Redirect to authorization server**: Browser redirects to Google's authorization endpoint with:
   - `client_id`: Your app's identifier
   - `redirect_uri`: Where to send user after authorization
   - `scope`: Permissions requested (e.g., `read:photos`)
   - `response_type`: `code` (requesting authorization code)
   - `state`: Random string for CSRF protection
3. **User authenticates**: User logs into Google and grants permissions
4. **Authorization code returned**: Google redirects back to your app with authorization code
5. **Exchange code for tokens**: Your backend server exchanges the code for access token (using client_secret)
6. **Access protected resources**: Use access token to call Google APIs

### Example Scenario

**Scenario**: Photo printing app wants to access user's Google Photos

**Step 1**: User clicks "Import from Google Photos"
```
User → App: Clicks button
App → Browser: Redirect to https://accounts.google.com/authorize?
    client_id=photo-app-123&
    redirect_uri=https://photoapp.com/callback&
    scope=photos.readonly&
    response_type=code&
    state=xyz123
```

**Step 2**: User authenticates and grants permission
```
Browser → Google: Shows login page
User → Google: Enters credentials
Google → User: Shows "Allow Photo App to access your photos?"
User → Google: Clicks "Allow"
```

**Step 3**: Authorization code received
```
Google → Browser: Redirect to https://photoapp.com/callback?
    code=4/0AY0e-g7abc123...&
    state=xyz123
```

**Step 4**: Exchange code for tokens
```
App Backend → Google: POST /token
    grant_type=authorization_code
    code=4/0AY0e-g7abc123...
    redirect_uri=https://photoapp.com/callback
    client_id=photo-app-123
    client_secret=secret_xyz789

Google → App Backend: {
    access_token: "ya29.a0AfH6SMC...",
    refresh_token: "1//04abc123...",
    expires_in: 3600,
    token_type: "Bearer"
}
```

**Step 5**: Access protected resources
```
App Backend → Google Photos API: GET /v1/photos
    Authorization: Bearer ya29.a0AfH6SMC...

Google Photos API → App Backend: {
    photos: [
        { id: "photo1", url: "..." },
        { id: "photo2", url: "..." }
    ]
}
```

### When to Use

✅ **Use Authorization Code Flow when:**
- Server-side web applications (can securely store client_secret)
- Mobile apps with backend server
- Applications that need refresh tokens
- High security requirements

❌ **Don't use when:**
- Pure client-side apps (JavaScript SPAs) - use PKCE instead
- Mobile apps without backend - use PKCE instead

---

## Authorization Code Flow with PKCE

### Overview

**PKCE (Proof Key for Code Exchange)** is an extension to Authorization Code Flow designed for public clients (apps that cannot securely store client_secret), such as mobile apps and SPAs.

### Flow Diagram

```
┌─────────────┐                                              ┌──────────────────┐
│   Browser   │                                              │  Authorization   │
│   (User)    │                                              │     Server       │
└──────┬──────┘                                              └────────┬─────────┘
       │                                                              │
       │ 1. Generate code_verifier and code_challenge                │
       │    code_verifier = random_string                            │
       │    code_challenge = SHA256(code_verifier)                   │
       │                                                              │
       │ 2. Redirect to authorization endpoint                       │
       │    GET /authorize?                                          │
       │      client_id=...&                                         │
       │      code_challenge=...&                                    │
       │      code_challenge_method=S256&                            │
       │      redirect_uri=...&                                      │
       │      scope=...                                              │
       │───────────────────────────────────────────────────────────▶│
       │                                                              │
       │ 3. User authenticates and grants permission                 │
       │◀───────────────────────────────────────────────────────────│
       │                                                              │
       │ 4. Authorization code returned                              │
       │    Redirect: ...?code=AUTHORIZATION_CODE                    │
       │───────────────────────────────────────────────────────────▶│
       │                                                              │
       │                                                              │
┌──────┴──────┐                                              ┌────────┴─────────┐
│   Client    │                                              │  Authorization   │
│ Application │                                              │     Server       │
│  (SPA/Mobile)│                                             └────────┬─────────┘
└──────┬──────┘                                                       │
       │                                                              │
       │ 5. Exchange authorization code for tokens                    │
       │    POST /token                                              │
       │    {                                                        │
       │      grant_type: "authorization_code",                     │
       │      code: "AUTHORIZATION_CODE",                           │
       │      redirect_uri: "https://app.com/callback",             │
       │      client_id: "...",                                     │
       │      code_verifier: "original_random_string"               │
       │    }                                                        │
       │───────────────────────────────────────────────────────────▶│
       │                                                              │
       │ 6. Server verifies:                                         │
       │    SHA256(code_verifier) == code_challenge?                 │
       │                                                              │
       │ 7. Access token + refresh token returned                    │
       │◀───────────────────────────────────────────────────────────│
       │    {                                                        │
       │      access_token: "eyJ...",                               │
       │      refresh_token: "abc123...",                           │
       │      expires_in: 3600                                      │
       │    }                                                        │
```

### PKCE Components

- **code_verifier**: Random string (43-128 characters, URL-safe)
- **code_challenge**: SHA256 hash of code_verifier (Base64URL encoded)
- **code_challenge_method**: `S256` (SHA256) or `plain`

### Example Scenario

**Scenario**: React SPA wants to access user's GitHub repositories

**Step 1**: Generate PKCE values
```
Client generates:
  code_verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
  code_challenge = SHA256(code_verifier) = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM"
```

**Step 2**: Authorization request
```
Browser → GitHub: GET /authorize?
    client_id=github-app-123&
    redirect_uri=https://myapp.com/callback&
    scope=repo&
    response_type=code&
    code_challenge=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM&
    code_challenge_method=S256&
    state=xyz123
```

**Step 3**: Exchange code with verifier
```
SPA → GitHub: POST /token
    grant_type=authorization_code
    code=abc123...
    redirect_uri=https://myapp.com/callback
    client_id=github-app-123
    code_verifier=dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk

GitHub verifies:
    SHA256(code_verifier) == code_challenge? ✅

GitHub → SPA: {
    access_token: "gho_abc123...",
    refresh_token: "ghr_xyz789...",
    expires_in: 28800
}
```

### When to Use

✅ **Use PKCE when:**
- Single Page Applications (SPAs)
- Mobile apps (iOS, Android)
- Desktop applications
- Any public client (cannot store client_secret securely)

❌ **Don't use when:**
- Server-side applications (use regular Authorization Code Flow)

---

## Implicit Flow (Deprecated)

### Overview

**Implicit Flow** was designed for browser-based applications but is now **deprecated** due to security concerns. It returns access tokens directly in the URL fragment, which is vulnerable to token leakage.

### Flow Diagram

```
┌─────────────┐                                              ┌──────────────────┐
│   Browser   │                                              │  Authorization   │
│   (User)    │                                              │     Server       │
└──────┬──────┘                                              └────────┬─────────┘
       │                                                              │
       │ 1. Redirect to authorization endpoint                       │
       │    GET /authorize?                                          │
       │      client_id=...&                                         │
       │      redirect_uri=...&                                      │
       │      response_type=token&  ← Note: "token" not "code"       │
       │      scope=...                                              │
       │───────────────────────────────────────────────────────────▶│
       │                                                              │
       │ 2. User authenticates and grants permission                 │
       │◀───────────────────────────────────────────────────────────│
       │                                                              │
       │ 3. Access token returned directly in URL fragment           │
       │    Redirect: https://app.com/callback#                      │
       │      access_token=eyJ...&                                   │
       │      expires_in=3600&                                       │
       │      token_type=Bearer                                      │
       │───────────────────────────────────────────────────────────▶│
       │                                                              │
       │    ⚠️ Token visible in browser history, logs, referrer     │
```

### Why It's Deprecated

1. **Token in URL**: Access token appears in URL fragment, browser history, server logs
2. **No refresh token**: Cannot refresh expired tokens
3. **No code exchange**: No intermediate step to verify client
4. **CSRF vulnerability**: Easier to intercept tokens

### Example Scenario

**Scenario**: Old JavaScript app (deprecated pattern)

```
Browser → Google: GET /authorize?
    client_id=old-app-123&
    redirect_uri=https://oldapp.com/callback&
    response_type=token&  ← Direct token request
    scope=profile

Google → Browser: Redirect to https://oldapp.com/callback#
    access_token=ya29.a0AfH6SMC...&
    expires_in=3600&
    token_type=Bearer

⚠️ Problem: Token is in URL, visible everywhere!
```

### Migration Path

**Replace Implicit Flow with:**
- **Authorization Code Flow with PKCE** for SPAs
- **Authorization Code Flow** for server-side apps

---

## Client Credentials Flow

### Overview

**Client Credentials Flow** is used for **machine-to-machine** communication where there is no user involved. The client authenticates itself and gets an access token directly.

### Flow Diagram

```
┌─────────────┐                                              ┌──────────────────┐
│   Client    │                                              │  Authorization   │
│ Application │                                              │     Server       │
│  (Service)  │                                              └────────┬─────────┘
└──────┬──────┘                                                       │
       │                                                              │
       │ 1. Request access token                                     │
       │    POST /token                                              │
       │    {                                                        │
       │      grant_type: "client_credentials",                     │
       │      client_id: "service-123",                             │
       │      client_secret: "secret_xyz",                           │
       │      scope: "api.read api.write"                            │
       │    }                                                        │
       │───────────────────────────────────────────────────────────▶│
       │                                                              │
       │ 2. Validate client credentials                              │
       │                                                              │
       │ 3. Access token returned                                    │
       │◀───────────────────────────────────────────────────────────│
       │    {                                                        │
       │      access_token: "eyJ...",                               │
       │      expires_in: 3600,                                     │
       │      token_type: "Bearer",                                 │
       │      scope: "api.read api.write"                            │
       │    }                                                        │
       │                                                              │
       │                                                              │
┌──────┴──────┐                                              ┌────────┴─────────┐
│   Client    │                                              │   Resource       │
│ Application │                                              │     Server       │
└──────┬──────┘                                              └────────┬─────────┘
       │                                                              │
       │ 4. Use access token to access protected resources           │
       │    GET /api/data                                             │
       │    Authorization: Bearer eyJ...                            │
       │───────────────────────────────────────────────────────────▶│
       │                                                              │
       │ 5. Protected resource returned                              │
       │◀───────────────────────────────────────────────────────────│
       │    { data: [...] }                                          │
```

### Step-by-Step Explanation

1. **Client authenticates**: Service sends client_id and client_secret
2. **Token issued**: Authorization server validates credentials and issues access token
3. **Access resources**: Service uses access token to call APIs
4. **No refresh token**: Typically no refresh token (service can re-authenticate)

### Example Scenario

**Scenario**: Microservice A needs to call Microservice B's API

**Step 1**: Service A requests token
```
Service A → Auth Server: POST /token
    grant_type=client_credentials
    client_id=service-a-123
    client_secret=secret_abc789
    scope=api.read api.write

Auth Server → Service A: {
    access_token: "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
    expires_in: 3600,
    token_type: "Bearer",
    scope: "api.read api.write"
}
```

**Step 2**: Service A calls Service B
```
Service A → Service B: GET /api/users
    Authorization: Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...

Service B validates token → Service A: {
    users: [
        { id: 1, name: "John" },
        { id: 2, name: "Jane" }
    ]
}
```

### When to Use

✅ **Use Client Credentials Flow when:**
- Microservice-to-microservice communication
- Scheduled jobs/background tasks
- API-to-API integration
- No user involved (machine-to-machine)

❌ **Don't use when:**
- User authentication is required
- User-specific resources need to be accessed
- User consent is needed

---

## Resource Owner Password Credentials Flow (Deprecated)

### Overview

**Resource Owner Password Credentials Flow** allows the client to collect user credentials directly and exchange them for tokens. This flow is **deprecated** because it requires the client to handle user passwords, which is a security risk.

### Flow Diagram

```
┌─────────────┐                                              ┌──────────────────┐
│   Browser   │                                              │  Authorization   │
│   (User)    │                                              │     Server       │
└──────┬──────┘                                              └────────┬─────────┘
       │                                                              │
       │ 1. User enters username and password                        │
       │    (in client application)                                   │
       │                                                              │
       │                                                              │
┌──────┴──────┐                                              ┌────────┴─────────┐
│   Client    │                                              │  Authorization   │
│ Application │                                              │     Server       │
└──────┬──────┘                                              └────────┬─────────┘
       │                                                              │
       │ 2. Exchange credentials for tokens                           │
       │    POST /token                                              │
       │    {                                                        │
       │      grant_type: "password",                                │
       │      username: "user@example.com",                          │
       │      password: "user_password",                             │
       │      client_id: "...",                                     │
       │      client_secret: "...",                                 │
       │      scope: "read write"                                    │
       │    }                                                        │
       │───────────────────────────────────────────────────────────▶│
       │                                                              │
       │ 3. Validate credentials                                     │
       │                                                              │
       │ 4. Access token + refresh token returned                    │
       │◀───────────────────────────────────────────────────────────│
       │    {                                                        │
       │      access_token: "eyJ...",                               │
       │      refresh_token: "abc123...",                           │
       │      expires_in: 3600                                      │
       │    }                                                        │
       │                                                              │
       │ ⚠️ Security Risk: Client handles user passwords!            │
```

### Why It's Deprecated

1. **Password handling**: Client application sees user passwords
2. **Phishing risk**: Malicious apps can steal credentials
3. **No user consent**: User doesn't see what permissions are granted
4. **Trust requirement**: User must trust client with credentials

### Example Scenario

**Scenario**: Old mobile app (deprecated pattern)

```
User → Mobile App: Enters username/password
Mobile App → Auth Server: POST /token
    grant_type=password
    username=user@example.com
    password=MyPassword123
    client_id=mobile-app-123
    client_secret=secret_xyz
    scope=read write

⚠️ Problem: Mobile app has user's password!

Auth Server → Mobile App: {
    access_token: "eyJ...",
    refresh_token: "abc123...",
    expires_in: 3600
}
```

### Migration Path

**Replace Password Flow with:**
- **Authorization Code Flow with PKCE** for mobile apps
- **Authorization Code Flow** for web apps

---

## Device Flow

### Overview

**Device Flow** is designed for devices with limited input capabilities (smart TVs, IoT devices, command-line tools) where users cannot easily enter credentials.

### Flow Diagram

```
┌─────────────┐                                              ┌──────────────────┐
│   Device    │                                              │  Authorization   │
│  (TV/IoT)   │                                              │     Server       │
└──────┬──────┘                                              └────────┬─────────┘
       │                                                              │
       │ 1. Request device code                                     │
       │    POST /device                                             │
       │    {                                                        │
       │      client_id: "device-app-123",                          │
       │      scope: "read write"                                    │
       │    }                                                        │
       │───────────────────────────────────────────────────────────▶│
       │                                                              │
       │ 2. Device code + user code + verification URL returned     │
       │◀───────────────────────────────────────────────────────────│
       │    {                                                        │
       │      device_code: "abc123...",                              │
       │      user_code: "BDWD-HQPK",                                │
       │      verification_uri: "https://example.com/device",        │
       │      verification_uri_complete: "https://example.com/device?user_code=BDWD-HQPK",
       │      expires_in: 1800,                                      │
       │      interval: 5                                            │
       │    }                                                        │
       │                                                              │
       │ 3. Display user code to user                                │
       │    "Go to https://example.com/device"                       │
       │    "Enter code: BDWD-HQPK"                                  │
       │                                                              │
       │                                                              │
┌──────┴──────┐                                              ┌────────┴─────────┐
│   Browser   │                                              │  Authorization   │
│   (User)    │                                              │     Server       │
└──────┬──────┘                                              └────────┬─────────┘
       │                                                              │
       │ 4. User visits verification URL and enters user code        │
       │───────────────────────────────────────────────────────────▶│
       │                                                              │
       │ 5. User authenticates and grants permission                 │
       │◀───────────────────────────────────────────────────────────│
       │                                                              │
       │                                                              │
┌──────┴──────┐                                              ┌────────┴─────────┐
│   Device    │                                              │  Authorization   │
│  (TV/IoT)   │                                              │     Server       │
└──────┬──────┘                                              └────────┬─────────┘
       │                                                              │
       │ 6. Poll for access token                                    │
       │    POST /token                                              │
       │    {                                                        │
       │      grant_type: "urn:ietf:params:oauth:grant-type:device_code",
       │      device_code: "abc123...",                              │
       │      client_id: "device-app-123"                           │
       │    }                                                        │
       │───────────────────────────────────────────────────────────▶│
       │                                                              │
       │ 7. Check if user authorized                                 │
       │    - If pending: return "authorization_pending"            │
       │    - If authorized: return access token                    │
       │◀───────────────────────────────────────────────────────────│
       │    {                                                        │
       │      access_token: "eyJ...",                               │
       │      refresh_token: "abc123...",                           │
       │      expires_in: 3600                                      │
       │    }                                                        │
```

### Step-by-Step Explanation

1. **Device requests code**: Device requests device_code and user_code
2. **Codes displayed**: Device shows user_code and verification URL to user
3. **User authorizes**: User visits URL on another device, enters code, and authorizes
4. **Device polls**: Device polls token endpoint until user authorizes
5. **Token received**: Once authorized, device receives access token

### Example Scenario

**Scenario**: Smart TV app wants to access user's Spotify playlists

**Step 1**: TV requests device code
```
Smart TV → Spotify: POST /device
    client_id=spotify-tv-app
    scope=playlist-read

Spotify → Smart TV: {
    device_code: "abc123xyz789...",
    user_code: "BDWD-HQPK",
    verification_uri: "https://accounts.spotify.com/device",
    verification_uri_complete: "https://accounts.spotify.com/device?user_code=BDWD-HQPK",
    expires_in: 1800,
    interval: 5
}
```

**Step 2**: TV displays code
```
TV Screen shows:
┌─────────────────────────────┐
│  Go to:                     │
│  accounts.spotify.com/device│
│                             │
│  Enter code:                │
│  BDWD-HQPK                  │
└─────────────────────────────┘
```

**Step 3**: User authorizes on phone
```
User → Phone Browser: Visits accounts.spotify.com/device
User → Phone Browser: Enters code "BDWD-HQPK"
Spotify → User: "Allow Smart TV to access your playlists?"
User → Spotify: Clicks "Allow"
```

**Step 4**: TV polls for token
```
Smart TV → Spotify: POST /token
    grant_type=urn:ietf:params:oauth:grant-type:device_code
    device_code=abc123xyz789...
    client_id=spotify-tv-app

(First few attempts)
Spotify → Smart TV: {
    error: "authorization_pending"
}

(After user authorizes)
Spotify → Smart TV: {
    access_token: "BQCabc123...",
    refresh_token: "AQDxyz789...",
    expires_in: 3600
}
```

### When to Use

✅ **Use Device Flow when:**
- Smart TVs
- IoT devices
- Command-line tools
- Devices with limited input capabilities
- Devices without a browser

❌ **Don't use when:**
- Devices with full browser support (use Authorization Code Flow)
- Server-side applications
- Mobile apps (use PKCE)

---

## Refresh Token Flow

### Overview

**Refresh Token Flow** is used to obtain new access tokens when the current access token expires. Refresh tokens are long-lived and can be used to get new access tokens without re-authenticating the user.

### Flow Diagram

```
┌─────────────┐                                              ┌──────────────────┐
│   Client    │                                              │  Authorization   │
│ Application │                                              │     Server       │
└──────┬──────┘                                              └────────┬─────────┘
       │                                                              │
       │ 1. Access token expired or about to expire                  │
       │    Current token: expires_in: 0                             │
       │                                                              │
       │ 2. Request new access token using refresh token             │
       │    POST /token                                              │
       │    {                                                        │
       │      grant_type: "refresh_token",                          │
       │      refresh_token: "abc123...",                           │
       │      client_id: "...",                                     │
       │      client_secret: "...",  (if confidential client)       │
       │      scope: "read write"  (optional - can request new scope)
       │    }                                                        │
       │───────────────────────────────────────────────────────────▶│
       │                                                              │
       │ 3. Validate refresh token                                   │
       │    - Check if token exists                                  │
       │    - Check if token is revoked                              │
       │    - Check if token is expired                              │
       │                                                              │
       │ 4. New access token (+ optionally new refresh token)         │
       │◀───────────────────────────────────────────────────────────│
       │    {                                                        │
       │      access_token: "eyJ...",  ← New token                  │
       │      refresh_token: "xyz789...",  ← New refresh token (if rotated)
       │      expires_in: 3600                                       │
       │    }                                                        │
       │                                                              │
       │ 5. Use new access token for API calls                       │
```

### Step-by-Step Explanation

1. **Access token expires**: Current access token is expired or about to expire
2. **Request refresh**: Client sends refresh token to token endpoint
3. **New token issued**: Authorization server validates refresh token and issues new access token
4. **Token rotation**: Some servers issue new refresh token (rotation) for security

### Example Scenario

**Scenario**: Mobile app needs to refresh expired access token

**Step 1**: Access token expired
```
App → API: GET /api/photos
    Authorization: Bearer expired_token_xyz...

API → App: 401 Unauthorized
    {
        error: "invalid_token",
        error_description: "Token expired"
    }
```

**Step 2**: Refresh access token
```
App → Auth Server: POST /token
    grant_type=refresh_token
    refresh_token=1//04abc123xyz789...
    client_id=mobile-app-123
    client_secret=secret_xyz

Auth Server → App: {
    access_token: "ya29.new_token_abc...",
    refresh_token: "1//04new_refresh_xyz...",  ← Rotated
    expires_in: 3600,
    token_type: "Bearer"
}
```

**Step 3**: Use new token
```
App → API: GET /api/photos
    Authorization: Bearer ya29.new_token_abc...

API → App: {
    photos: [...]
}
```

### Refresh Token Rotation

**Best Practice**: Rotate refresh tokens on each use

```
Old refresh token: abc123...
    ↓ (used)
New refresh token: xyz789...
    ↓ (used)
New refresh token: def456...
```

**Benefits:**
- If old token is stolen, it becomes invalid after first use
- Limits impact of token theft
- Better security posture

### When to Use

✅ **Use Refresh Token Flow when:**
- Access token expires
- You want to avoid re-authenticating user
- Implementing "remember me" functionality
- Long-lived sessions

❌ **Don't use when:**
- Initial token acquisition (use other flows)
- Refresh token is expired/revoked (user must re-authenticate)

---

## Flow Comparison

| Flow | Use Case | User Involved | Client Type | Refresh Token | Security Level |
|------|----------|---------------|-------------|---------------|----------------|
| **Authorization Code** | Server-side web apps | ✅ Yes | Confidential | ✅ Yes | ⭐⭐⭐⭐⭐ |
| **Authorization Code + PKCE** | SPAs, mobile apps | ✅ Yes | Public | ✅ Yes | ⭐⭐⭐⭐⭐ |
| **Implicit** | ❌ Deprecated | ✅ Yes | Public | ❌ No | ⭐⭐ (deprecated) |
| **Client Credentials** | Machine-to-machine | ❌ No | Confidential | ❌ Usually no | ⭐⭐⭐⭐ |
| **Password** | ❌ Deprecated | ✅ Yes | Confidential | ✅ Yes | ⭐ (deprecated) |
| **Device** | Smart TVs, IoT | ✅ Yes | Public | ✅ Yes | ⭐⭐⭐⭐ |
| **Refresh Token** | Token renewal | ✅ Yes (indirect) | Any | ✅ Yes | ⭐⭐⭐⭐ |

### Decision Tree

```
Do you need user authentication?
│
├─ No → Use Client Credentials Flow
│
└─ Yes → What type of client?
    │
    ├─ Server-side app (can store client_secret)?
    │   └─ Use Authorization Code Flow
    │
    ├─ SPA or Mobile app (cannot store client_secret)?
    │   └─ Use Authorization Code Flow with PKCE
    │
    └─ Limited input device (TV, IoT)?
        └─ Use Device Flow
```

---

## Security Best Practices

### 1. Use HTTPS Always

**❌ Never use HTTP:**
```
http://example.com/authorize  ← Vulnerable to MITM attacks
```

**✅ Always use HTTPS:**
```
https://example.com/authorize  ← Encrypted, secure
```

### 2. Validate Redirect URIs

**✅ Whitelist redirect URIs:**
```
Allowed redirect URIs:
- https://app.com/callback
- https://app.com/auth/callback

Reject any other redirect URI!
```

### 3. Use State Parameter (CSRF Protection)

**✅ Include state in authorization request:**
```
GET /authorize?
    client_id=...&
    redirect_uri=...&
    state=random_csrf_token_xyz123

Validate state on callback!
```

### 4. Store Tokens Securely

**❌ Don't store in localStorage (XSS risk):**
```javascript
localStorage.setItem('token', access_token);  // Vulnerable to XSS
```

**✅ Use httpOnly cookies or secure storage:**
```javascript
// Server sets cookie
Set-Cookie: token=...; HttpOnly; Secure; SameSite=Strict

// Or use secure keychain (mobile)
Keychain.setItem('token', access_token);
```

### 5. Implement Token Rotation

**✅ Rotate refresh tokens:**
```
Old refresh token → New access token + New refresh token
```

### 6. Use Short-Lived Access Tokens

**✅ Recommended expiration times:**
- Access token: 15 minutes - 1 hour
- Refresh token: 7-30 days

### 7. Validate Tokens Properly

**✅ Always validate:**
- Signature
- Expiration (exp)
- Issuer (iss)
- Audience (aud)
- Token revocation status

### 8. Use PKCE for Public Clients

**✅ Always use PKCE for:**
- SPAs
- Mobile apps
- Desktop apps

### 9. Scope Limitation

**✅ Request minimum required scopes:**
```
scope=read:photos  ← Good: Only read access

scope=read:photos write:photos delete:photos admin  ← Bad: Too broad
```

### 10. Monitor and Audit

**✅ Log and monitor:**
- Token issuance
- Token usage
- Failed authentication attempts
- Token revocation

---

## Common OAuth2 Interview Questions

### Q1: What's the difference between OAuth2 and OIDC?

| Aspect | OAuth2 | OIDC |
|--------|--------|------|
| **Purpose** | Authorization | Authentication + Authorization |
| **Token** | Access token | ID token (JWT) + Access token |
| **User Info** | No user info | User profile information |
| **Use Case** | "Can app access my photos?" | "Who is this user?" |

**OIDC = OAuth2 + Authentication**

### Q2: Why is Implicit Flow deprecated?

1. **Token in URL**: Access token appears in browser history, logs, referrer headers
2. **No refresh token**: Cannot refresh expired tokens
3. **Security risk**: Easier to intercept tokens
4. **Replaced by**: Authorization Code Flow with PKCE

### Q3: What is PKCE and why is it needed?

**PKCE (Proof Key for Code Exchange)** is a security extension for public clients:

- **Problem**: Public clients (SPAs, mobile apps) cannot securely store client_secret
- **Solution**: PKCE uses code_verifier and code_challenge to prove code ownership
- **Benefit**: Prevents authorization code interception attacks

### Q4: When should you use Client Credentials Flow?

**Use when:**
- Microservice-to-microservice communication
- Scheduled jobs/background tasks
- API-to-API integration
- No user involved (machine-to-machine)

**Don't use when:**
- User authentication is required
- User-specific resources need to be accessed

### Q5: How do you handle token expiration?

1. **Short-lived access tokens**: 15 minutes - 1 hour
2. **Long-lived refresh tokens**: 7-30 days
3. **Refresh token flow**: Exchange refresh token for new access token
4. **Token rotation**: Issue new refresh token on each refresh

### Q6: What is the difference between access token and refresh token?

| Aspect | Access Token | Refresh Token |
|--------|--------------|---------------|
| **Purpose** | Access protected resources | Obtain new access tokens |
| **Lifetime** | Short (15 min - 1 hour) | Long (7-30 days) |
| **Exposed** | Sent with every API request | Only sent to token endpoint |
| **Revocation** | Harder to revoke (stateless) | Easier to revoke |

### Q7: How do you revoke OAuth2 tokens?

**Methods:**
1. **Token revocation endpoint**: `POST /revoke` with token
2. **Refresh token rotation**: Old refresh token becomes invalid
3. **User password change**: Invalidate all tokens
4. **Token blacklist**: Store revoked tokens, check on each request

### Q8: What is the state parameter used for?

**State parameter** prevents CSRF attacks:

1. **Generate random state**: `state=xyz123`
2. **Include in authorization request**: `GET /authorize?state=xyz123&...`
3. **Validate on callback**: Ensure state matches
4. **Prevents**: Attackers from tricking users into authorizing malicious apps

---

## Summary

### Key Takeaways

1. **Authorization Code Flow**: Most secure, use for server-side apps
2. **Authorization Code Flow + PKCE**: Use for SPAs and mobile apps
3. **Client Credentials Flow**: Use for machine-to-machine communication
4. **Device Flow**: Use for devices with limited input capabilities
5. **Refresh Token Flow**: Use to renew expired access tokens
6. **Implicit Flow**: ❌ Deprecated - don't use
7. **Password Flow**: ❌ Deprecated - don't use

### Security Checklist

✅ Use HTTPS always  
✅ Validate redirect URIs  
✅ Use state parameter (CSRF protection)  
✅ Store tokens securely (httpOnly cookies)  
✅ Implement token rotation  
✅ Use short-lived access tokens  
✅ Validate tokens properly  
✅ Use PKCE for public clients  
✅ Request minimum scopes  
✅ Monitor and audit  

---

## Additional Resources

### OAuth2 Specifications
- RFC 6749: OAuth 2.0 Authorization Framework
- RFC 7636: Proof Key for Code Exchange (PKCE)
- RFC 8628: OAuth 2.0 Device Authorization Grant

### OpenID Connect
- OIDC Core 1.0: Authentication layer on top of OAuth2

### Best Practices
- OAuth 2.0 Security Best Current Practice (RFC 8252)
- OAuth 2.0 for Native Apps (RFC 8252)

---

**Remember**: OAuth2 is about **authorization** (what can you do), not **authentication** (who are you). For authentication, use OpenID Connect (OIDC) which builds on OAuth2.
