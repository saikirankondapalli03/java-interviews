# TIAA: Azure AD JWT Token Automation with MCP Client

## 🎯 Overview

TIAA implemented an automated JWT token acquisition flow using an MCP (Microsoft Client Protocol) client executable. This eliminates manual user intervention - developers simply run an executable that handles the entire Azure AD authentication and token storage process end-to-end.

---

## 🔄 Complete Flow

### User Experience (Zero Intervention)

```bash
# Developer just runs:
./mcp-client --config config.json

# That's it! ✅
```

**No manual steps required:**
- ❌ No browser login
- ❌ No copy-paste tokens
- ❌ No manual token storage
- ✅ Fully automated

---

### Behind the Scenes: What Happens

```
User executes: ./mcp-client --config config.json
    ↓
MCP Client automatically:
    1. Reads Azure AD credentials from config
       - Client ID (Service Principal)
       - Client Secret
       - Tenant ID
       - Scopes/permissions
    ↓
    2. Authenticates with Azure AD
       - Uses OAuth2 Client Credentials Flow
       - POST to Azure AD token endpoint
       - Exchanges credentials for JWT token
    ↓
    3. Receives JWT token from Azure AD
       {
         "access_token": "eyJhbGciOiJSUzI1NiIs...",
         "token_type": "Bearer",
         "expires_in": 3600
       }
    ↓
    4. Stores JWT token locally
       - Location: ~/.home/token.json (or ~/.tiaa/token.json)
       - Format: JSON with token + metadata
       - Includes expiration timestamp
    ↓
Done! ✅ Token ready for application use
```

---

## 📋 Technical Details

### MCP Client Configuration

**config.json:**
```json
{
  "azure": {
    "tenantId": "12345678-1234-1234-1234-123456789012",
    "clientId": "app-client-id-here",
    "clientSecret": "client-secret-here",
    "scope": "api://tiaa-entitlement-service/.default"
  },
  "token": {
    "storagePath": "~/.home/token.json",
    "refreshThreshold": 300
  }
}
```

### Azure AD Authentication Flow

**OAuth2 Client Credentials Flow:**

```http
POST https://login.microsoftonline.com/{tenantId}/oauth2/v2.0/token
Content-Type: application/x-www-form-urlencoded

client_id={clientId}
&client_secret={clientSecret}
&scope={scope}
&grant_type=client_credentials
```

**Response:**
```json
{
  "access_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "token_type": "Bearer",
  "expires_in": 3600,
  "ext_expires_in": 3600
}
```

### Token Storage Format

**~/.home/token.json:**
```json
{
  "access_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "token_type": "Bearer",
  "expires_at": 1706544000,
  "issued_at": 1706540400,
  "expires_in": 3600
}
```

---

## 🔧 Implementation Pattern

### MCP Client Executable Logic

```java
public class MCPClient {
    
    public void authenticateAndStoreToken(Config config) {
        // Step 1: Authenticate with Azure AD
        AzureADTokenResponse tokenResponse = authenticateWithAzureAD(
            config.getTenantId(),
            config.getClientId(),
            config.getClientSecret(),
            config.getScope()
        );
        
        // Step 2: Calculate expiration timestamp
        long expiresAt = System.currentTimeMillis() / 1000 + tokenResponse.getExpiresIn();
        
        // Step 3: Create token metadata
        TokenMetadata tokenMetadata = TokenMetadata.builder()
            .accessToken(tokenResponse.getAccessToken())
            .tokenType(tokenResponse.getTokenType())
            .expiresAt(expiresAt)
            .issuedAt(System.currentTimeMillis() / 1000)
            .build();
        
        // Step 4: Store token locally
        String storagePath = expandPath(config.getTokenStoragePath());
        writeTokenToFile(storagePath, tokenMetadata);
        
        System.out.println("✅ Token stored at: " + storagePath);
    }
    
    private AzureADTokenResponse authenticateWithAzureAD(
            String tenantId, String clientId, String clientSecret, String scope) {
        
        String tokenEndpoint = String.format(
            "https://login.microsoftonline.com/%s/oauth2/v2.0/token",
            tenantId
        );
        
        HttpPost request = new HttpPost(tokenEndpoint);
        request.setHeader("Content-Type", "application/x-www-form-urlencoded");
        
        List<NameValuePair> params = new ArrayList<>();
        params.add(new BasicNameValuePair("client_id", clientId));
        params.add(new BasicNameValuePair("client_secret", clientSecret));
        params.add(new BasicNameValuePair("scope", scope));
        params.add(new BasicNameValuePair("grant_type", "client_credentials"));
        
        request.setEntity(new UrlEncodedFormEntity(params));
        
        // Execute request and parse response
        HttpResponse response = httpClient.execute(request);
        return parseTokenResponse(response);
    }
}
```

---

## 🚀 Application Usage

### Reading Stored Token

```java
@Service
public class TokenReader {
    
    public String getStoredJWTToken() {
        String tokenPath = System.getProperty("user.home") + "/.home/token.json";
        
        try {
            TokenMetadata tokenMetadata = readTokenFromFile(tokenPath);
            
            // Check if token is expired
            if (isTokenExpired(tokenMetadata)) {
                throw new TokenExpiredException("Token expired. Run mcp-client to refresh.");
            }
            
            return tokenMetadata.getAccessToken();
            
        } catch (FileNotFoundException e) {
            throw new TokenNotFoundException(
                "Token not found. Run mcp-client to authenticate."
            );
        }
    }
    
    private boolean isTokenExpired(TokenMetadata tokenMetadata) {
        long currentTime = System.currentTimeMillis() / 1000;
        return currentTime >= tokenMetadata.getExpiresAt();
    }
}
```

### Using Token for Authorization

```java
@Component
public class AuthorizationFilter implements Filter {
    
    private final TokenReader tokenReader;
    private final CedarEngine cedarEngine;
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, 
                        FilterChain chain) throws IOException, ServletException {
        
        // Read JWT token from local storage
        String jwtToken = tokenReader.getStoredJWTToken();
        
        // Validate and parse JWT
        Claims jwtClaims = jwtValidator.validate(jwtToken);
        
        // Extract cmdbId and env for policy selection
        String cmdbId = jwtClaims.get("cmdbId", String.class);
        String env = jwtClaims.get("env", String.class);
        
        // Build authorization request and evaluate with Cedar
        // ... (rest of authorization flow)
    }
}
```

---

## ✅ Benefits

1. **Zero Manual Intervention**
   - Developers just run executable
   - No browser logins or token copying

2. **Automated Token Management**
   - Token acquisition handled automatically
   - Local storage for easy access

3. **Service Principal Based**
   - Uses Azure AD service principal
   - No user credentials required
   - Suitable for CI/CD pipelines

4. **Secure Storage**
   - Token stored in user home directory
   - File permissions can be restricted
   - No tokens in code or config files

5. **Integration Ready**
   - Applications can read token from known location
   - Token refresh can be automated
   - Works with Cedar policy evaluation

---

## 🔐 Security Considerations

1. **Client Secret Management**
   - Store client secret securely (not in code)
   - Use Azure Key Vault or environment variables
   - Rotate secrets regularly

2. **Token Storage**
   - Store in user home directory with restricted permissions
   - Consider encrypting token file
   - Set appropriate file permissions (600)

3. **Token Expiration**
   - Check expiration before use
   - Implement automatic refresh if needed
   - Handle expired tokens gracefully

4. **Service Principal Permissions**
   - Use least privilege principle
   - Grant only required scopes
   - Regular access reviews

---

## 📝 Interview Talking Points

**When explaining this flow:**

1. **Problem:** Manual token acquisition is tedious and error-prone
2. **Solution:** MCP client executable automates entire flow
3. **Flow:** Azure AD authentication → Token retrieval → Local storage
4. **Usage:** Applications read token from local storage for authorization
5. **Benefits:** Zero manual steps, automated, secure, CI/CD friendly

**Key Points:**
- "We implemented an automated JWT token acquisition flow using an MCP client executable"
- "Developers simply run the executable, which handles Azure AD authentication and stores the token locally"
- "Applications read the stored token for Cedar policy-based authorization"
- "This eliminates manual intervention and works seamlessly in CI/CD pipelines"

---

## 🔗 Related Concepts

- **OAuth2 Client Credentials Flow:** Service-to-service authentication
- **Azure AD Service Principal:** Application identity in Azure AD
- **JWT Token Storage:** Local file-based token management
- **Cedar Policy Evaluation:** Using JWT tokens for authorization decisions
- **Automated Authentication:** Zero-touch token acquisition

---

**This pattern demonstrates understanding of:**
- Automated authentication flows
- Azure AD integration
- Token management best practices
- Developer experience optimization
- Service-to-service authentication patterns
