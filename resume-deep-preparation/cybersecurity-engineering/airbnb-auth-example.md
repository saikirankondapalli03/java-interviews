# Airbnb "Sign in with Google" - OAuth2 Flow Examples

## Overview

This document explains how Airbnb implements "Sign in with Google" using OAuth2 flows. We'll cover both **Regular Authorization Code Flow** (for server-side apps) and **Authorization Code Flow with PKCE** (for SPAs/mobile apps).

---

## Scenario: Airbnb "Sign in with Google"

**Goal**: Allow users to sign in to Airbnb using their Google account without sharing their Google password with Airbnb.

**What happens**:
1. User clicks "Sign in with Google" on Airbnb
2. Google authenticates the user
3. Google provides authorization code
4. Airbnb exchanges code for tokens
5. Airbnb gets user info (email, name, picture) from Google
6. Airbnb creates/logs in the user to their own system

---

## Flow 1: Regular Authorization Code Flow (Server-Side Web App)

### When to Use
- Traditional server-side web application
- Can securely store `client_secret` on backend
- Confidential client

### Complete Flow

#### Step 1: User Clicks "Sign in with Google"
```
User → Airbnb Website: Clicks "Sign in with Google" button
```

#### Step 2: Redirect to Google Authorization Server
```
Airbnb Frontend → Browser: Redirect to Google
GET https://accounts.google.com/authorize?
  client_id=airbnb-app-id&
  redirect_uri=https://airbnb.com/auth/callback&
  scope=openid email profile&
  response_type=code&
  state=xyz123
```

#### Step 3: User Authenticates at Google
```
Browser → Google: Shows Google login page
User → Google: Enters username/password
Google → User: Shows "Allow Airbnb to access your profile?"
User → Google: Clicks "Allow"
```

#### Step 4: Authorization Code Received
```
Google → Browser: Redirect back to Airbnb
GET https://airbnb.com/auth/callback?
  code=4/0AY0e-g7abc123...&
  state=xyz123
```

#### Step 5: Frontend Sends Code to Backend
```
Airbnb Frontend → Airbnb Backend:
POST /api/auth/google/callback
{
  code: "4/0AY0e-g7abc123..."
}
```

#### Step 6: Backend Exchanges Code for Tokens
```
Airbnb Backend → Google Authorization Server:
POST https://oauth2.googleapis.com/token
Content-Type: application/x-www-form-urlencoded

grant_type=authorization_code&
code=4/0AY0e-g7abc123...&
client_id=airbnb-app-id&
client_secret=airbnb-secret-xyz&
redirect_uri=https://airbnb.com/auth/callback

Google Authorization Server validates:
✓ Code exists and not expired
✓ client_id matches
✓ client_secret is correct
✓ redirect_uri matches original request

Google Authorization Server → Airbnb Backend:
{
  "access_token": "ya29.a0AfH6SMC...",
  "id_token": "eyJhbGciOiJSUzI1NiIs...",
  "refresh_token": "1//04abc123...",
  "expires_in": 3600,
  "token_type": "Bearer"
}
```

#### Step 7: Backend Gets User Info from Google
```
Airbnb Backend → Google Resource Server (UserInfo API):
GET https://www.googleapis.com/oauth2/v2/userinfo
Authorization: Bearer ya29.a0AfH6SMC...

Google Resource Server → Airbnb Backend:
{
  "id": "123456789",
  "email": "user@gmail.com",
  "verified_email": true,
  "name": "John Doe",
  "picture": "https://lh3.googleusercontent.com/...",
  "locale": "en"
}
```

#### Step 8: Backend Creates/Logs In User
```
Airbnb Backend Logic:
1. Check if user exists in Airbnb DB (by email or google_id)
2. If NO:
   - Create new user account
   - Store: email, name, picture, google_id
3. If YES:
   - Update last_login timestamp
   - Log them in
4. Generate Airbnb session token/cookie
5. Return success response

Airbnb Backend → Airbnb Frontend:
{
  "session_token": "airbnb_session_xyz...",
  "user": {
    "id": 123,
    "email": "user@gmail.com",
    "name": "John Doe",
    "picture": "https://..."
  }
}
```

#### Step 9: User Logged In
```
Airbnb Frontend:
- Stores session token (httpOnly cookie)
- Redirects to Airbnb dashboard
- User is now logged in to Airbnb
```

### Flow Diagram

```
┌─────────────────┐
│   User Browser  │
└────────┬────────┘
         │
         │ 1. Clicks "Sign in with Google"
         ▼
┌─────────────────┐
│ Airbnb Frontend │
└────────┬────────┘
         │
         │ 2. Redirect to Google
         ▼
┌─────────────────┐
│ Google Auth     │
│    Server       │
└────────┬────────┘
         │
         │ 3. User authenticates
         │
         │ 4. Returns authorization code
         ▼
┌─────────────────┐
│ Airbnb Frontend │
└────────┬────────┘
         │
         │ 5. Sends code to backend
         ▼
┌─────────────────┐
│ Airbnb Backend  │
└────────┬────────┘
         │
         │ 6. Exchanges code for tokens
         │    (with client_secret)
         ▼
┌─────────────────┐
│ Google Auth     │
│    Server       │
└────────┬────────┘
         │
         │ 7. Returns tokens
         ▼
┌─────────────────┐
│ Airbnb Backend  │
└────────┬────────┘
         │
         │ 8. Gets user info
         ▼
┌─────────────────┐
│ Google Resource │
│    Server       │
│  (UserInfo API) │
└────────┬────────┘
         │
         │ 9. Returns user details
         ▼
┌─────────────────┐
│ Airbnb Backend  │
└────────┬────────┘
         │
         │ 10. Creates/logs in user
         │     Returns session
         ▼
┌─────────────────┐
│ Airbnb Frontend │
└─────────────────┘
```

### Key Points

- ✅ **Authorization code** is used by **Airbnb backend** (not frontend)
- ✅ **Google Authorization Server** validates the code
- ✅ **client_secret** is stored securely on backend (never exposed to frontend)
- ✅ **Access token** is used to call Google UserInfo API
- ✅ **Resource Server** (Google UserInfo API) validates token locally (no call to Auth Server)
- ✅ **Purpose**: Authentication - identify user and create/login to Airbnb account

---

## Flow 2: Authorization Code Flow with PKCE (SPA/Mobile App)

### When to Use
- Single Page Application (React/Vue/Angular)
- Mobile app (iOS/Android)
- Cannot securely store `client_secret`
- Public client

### Complete Flow

#### Step 1: Frontend Generates PKCE Values
```
Airbnb Frontend (React SPA):
// Generate random code_verifier
code_verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"

// Calculate code_challenge (SHA256 hash, Base64URL encoded)
code_challenge = SHA256(code_verifier) 
               = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM"

// Store code_verifier in memory (sessionStorage/localStorage)
sessionStorage.setItem('code_verifier', code_verifier)
```

#### Step 2: User Clicks "Sign in with Google"
```
User → Airbnb Frontend: Clicks "Sign in with Google" button
```

#### Step 3: Redirect to Google with PKCE Challenge
```
Airbnb Frontend → Browser: Redirect to Google
GET https://accounts.google.com/authorize?
  client_id=airbnb-app-id&
  redirect_uri=https://airbnb.com/auth/callback&
  scope=openid email profile&
  response_type=code&
  code_challenge=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM&
  code_challenge_method=S256&
  state=xyz123
```

#### Step 4: User Authenticates at Google
```
Browser → Google: Shows Google login page
User → Google: Enters username/password
Google → User: Shows "Allow Airbnb to access your profile?"
User → Google: Clicks "Allow"
```

#### Step 5: Authorization Code Received
```
Google → Browser: Redirect back to Airbnb
GET https://airbnb.com/auth/callback?
  code=4/0AY0e-g7abc123...&
  state=xyz123
```

#### Step 6: Frontend Exchanges Code with PKCE Verifier
```
Airbnb Frontend → Google Authorization Server:
POST https://oauth2.googleapis.com/token
Content-Type: application/x-www-form-urlencoded

grant_type=authorization_code&
code=4/0AY0e-g7abc123...&
client_id=airbnb-app-id&
redirect_uri=https://airbnb.com/auth/callback&
code_verifier=dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk

// NO client_secret! (Public client)

Google Authorization Server validates:
✓ Code exists and not expired
✓ client_id matches
✓ redirect_uri matches
✓ PKCE verification:
  SHA256(code_verifier) == code_challenge?
  SHA256("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk")
  == "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM"? ✓

Google Authorization Server → Airbnb Frontend:
{
  "access_token": "ya29.a0AfH6SMC...",
  "id_token": "eyJhbGciOiJSUzI1NiIs...",
  "refresh_token": "1//04abc123...",
  "expires_in": 3600,
  "token_type": "Bearer"
}

// Clear code_verifier from storage
sessionStorage.removeItem('code_verifier')
```

#### Step 7: Frontend Gets User Info from Google
```
Airbnb Frontend → Google Resource Server (UserInfo API):
GET https://www.googleapis.com/oauth2/v2/userinfo
Authorization: Bearer ya29.a0AfH6SMC...

Google Resource Server → Airbnb Frontend:
{
  "id": "123456789",
  "email": "user@gmail.com",
  "verified_email": true,
  "name": "John Doe",
  "picture": "https://lh3.googleusercontent.com/...",
  "locale": "en"
}
```

#### Step 8: Frontend Sends User Info to Airbnb Backend
```
Airbnb Frontend → Airbnb Backend:
POST /api/auth/google/callback
{
  "email": "user@gmail.com",
  "name": "John Doe",
  "google_id": "123456789",
  "picture": "https://...",
  "access_token": "ya29.a0AfH6SMC..."  // Optional: for backend to verify
}

Airbnb Backend:
- Optionally verifies access_token with Google
- Checks if user exists in Airbnb DB
- Creates/logs in user
- Generates Airbnb session token

Airbnb Backend → Airbnb Frontend:
{
  "session_token": "airbnb_session_xyz...",
  "user": {
    "id": 123,
    "email": "user@gmail.com",
    "name": "John Doe"
  }
}
```

#### Step 9: User Logged In
```
Airbnb Frontend:
- Stores session token (httpOnly cookie or secure storage)
- Redirects to Airbnb dashboard
- User is now logged in to Airbnb
```

### Flow Diagram

```
┌─────────────────┐
│ Airbnb Frontend │
│   (React SPA)   │
└────────┬────────┘
         │
         │ 1. Generate PKCE:
         │    code_verifier = random
         │    code_challenge = SHA256(code_verifier)
         │
         │ 2. Redirect to Google with code_challenge
         ▼
┌─────────────────┐
│ Google Auth     │
│    Server       │
└────────┬────────┘
         │
         │ 3. User authenticates
         │
         │ 4. Returns authorization code
         ▼
┌─────────────────┐
│ Airbnb Frontend │
└────────┬────────┘
         │
         │ 5. Exchange code with code_verifier
         │    (NO client_secret!)
         ▼
┌─────────────────┐
│ Google Auth     │
│    Server       │
└────────┬────────┘
         │
         │ 6. Validates PKCE:
         │    SHA256(code_verifier) == code_challenge?
         │
         │ 7. Returns tokens
         ▼
┌─────────────────┐
│ Airbnb Frontend │
└────────┬────────┘
         │
         │ 8. Get user info using access_token
         ▼
┌─────────────────┐
│ Google Resource │
│    Server       │
│  (UserInfo API) │
└────────┬────────┘
         │
         │ 9. Returns user details
         ▼
┌─────────────────┐
│ Airbnb Frontend │
└────────┬────────┘
         │
         │ 10. Send user info to Airbnb backend
         ▼
┌─────────────────┐
│ Airbnb Backend │
└─────────────────┘
         │
         │ Creates/logs in user
         │ Returns session
```

### Key Points

- ✅ **PKCE** is required for public clients (SPA/Mobile)
- ✅ **code_verifier** is generated by frontend and kept secret
- ✅ **code_challenge** is sent in authorization request
- ✅ **NO client_secret** needed (public client)
- ✅ **Google validates PKCE** by comparing SHA256(code_verifier) with code_challenge
- ✅ **Purpose**: Same as regular flow - authentication and account creation/login

---

## Comparison: Regular Flow vs PKCE Flow

| Aspect | Regular Flow | PKCE Flow |
|--------|-------------|-----------|
| **Client Type** | Confidential | Public |
| **App Type** | Server-side web app | SPA / Mobile app |
| **client_secret** | ✅ Required | ❌ Not needed |
| **PKCE** | ❌ Not needed | ✅ Required |
| **code_verifier** | ❌ No | ✅ Yes (random string) |
| **code_challenge** | ❌ No | ✅ Yes (SHA256 of verifier) |
| **Where code exchanged** | Backend | Frontend |
| **Security** | High (client_secret protected) | High (PKCE protects code) |

---

## Common Questions

### Q1: Where is the authorization code used?
**A**: The authorization code is used by:
- **Regular Flow**: Airbnb backend server
- **PKCE Flow**: Airbnb frontend (SPA)

### Q2: Who validates the authorization code?
**A**: Google Authorization Server validates the code by checking:
- Code exists and not expired
- client_id matches
- redirect_uri matches
- client_secret is correct (regular flow) OR PKCE verification passes (PKCE flow)

### Q3: How is the code validated?
**A**: Google Authorization Server:
1. Looks up the code in its database
2. Checks expiration (usually 10 minutes)
3. Verifies it hasn't been used before (one-time use)
4. Validates client credentials:
   - Regular: Checks client_secret
   - PKCE: Verifies SHA256(code_verifier) == code_challenge

### Q4: Does the Resource Server call Authorization Server?
**A**: Usually **NO**. Google Resource Server (UserInfo API) validates the access token locally:
- Checks JWT signature
- Verifies expiration
- Validates issuer and audience
- No need to call Authorization Server for every request

### Q5: What is the Resource Server in this case?
**A**: Google's **UserInfo API** (`https://www.googleapis.com/oauth2/v2/userinfo`) is the Resource Server. It provides:
- User email
- User name
- User picture
- User ID

**NOT** Google Photos API or any other Google service.

### Q6: What's the purpose of this flow?
**A**: **Authentication** - To identify the user and create/log them into Airbnb's system. The user doesn't need to create a separate Airbnb account - they can use their Google account.

---

## Security Best Practices

### For Regular Flow (Server-Side)
- ✅ Store `client_secret` securely on backend (environment variables, secrets manager)
- ✅ Never expose `client_secret` to frontend
- ✅ Use HTTPS for all communications
- ✅ Validate `state` parameter to prevent CSRF attacks
- ✅ Use short-lived access tokens (15 min - 1 hour)

### For PKCE Flow (SPA/Mobile)
- ✅ Always use PKCE for public clients
- ✅ Generate strong random `code_verifier` (43-128 characters)
- ✅ Use SHA256 for `code_challenge` (S256 method)
- ✅ Store `code_verifier` securely (sessionStorage, not localStorage)
- ✅ Clear `code_verifier` after use
- ✅ Use HTTPS for all communications
- ✅ Validate `state` parameter to prevent CSRF attacks

---

## Summary

### Regular Authorization Code Flow
1. User clicks "Sign in with Google"
2. Redirect to Google with authorization request
3. User authenticates
4. Google returns authorization code
5. **Backend** exchanges code for tokens (with client_secret)
6. Backend gets user info from Google
7. Backend creates/logs in user
8. User logged in to Airbnb

### PKCE Flow
1. Frontend generates PKCE values (code_verifier, code_challenge)
2. User clicks "Sign in with Google"
3. Redirect to Google with code_challenge
4. User authenticates
5. Google returns authorization code
6. **Frontend** exchanges code for tokens (with code_verifier, NO client_secret)
7. Frontend gets user info from Google
8. Frontend sends user info to backend
9. Backend creates/logs in user
10. User logged in to Airbnb

Both flows achieve the same goal: **authenticate user with Google and create/login to Airbnb account**.
