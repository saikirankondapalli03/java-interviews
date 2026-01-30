# Banking Data Monetization Platforms: A Senior Engineer's Perspective

## 1. Business Motivation

### Why Banks Moved from Free to Paid APIs

**The Screen Scraping Problem (2010-2018):**
- Aggregators like Mint, Plaid scraped customer login credentials
- Banks faced liability for breached accounts, fraud losses
- No control over data access patterns or partner quality
- Customer support burden from broken integrations
- Zero revenue while bearing all the risk

**The API Monetization Shift:**
Banks realized they were sitting on goldmines. Instead of fighting aggregators, they decided to profit from controlled data access.

### Revenue Models

**Per API Call Pricing:**
- Account balance check: $0.01
- Transaction history (30 days): $0.05
- Identity verification: $0.25
- Real-time payment initiation: $1.50

**Tiered Partner Plans:**
- Startup: 10K calls/month, $500 base fee
- Growth: 100K calls/month, $2K base fee + volume discounts
- Enterprise: Custom pricing, dedicated support, SLA guarantees

**SLA-Based Premium Tiers:**
- Standard: 99.5% uptime, 2-second response time
- Premium: 99.9% uptime, 500ms response time (+50% cost)
- Mission Critical: 99.99% uptime, 200ms response time (+200% cost)

## 2. Core Platform Components

```
┌─────────────────┐    ┌──────────────┐    ┌─────────────────┐
│   Fintech App   │───▶│  API Gateway │───▶│  Core Banking   │
│   (Plaid, etc)  │    │   + Auth     │    │    Systems      │
└─────────────────┘    └──────────────┘    └─────────────────┘
                              │
                              ▼
                    ┌──────────────────┐
                    │ Metering/Billing │
                    │   + Entitlement  │
                    └──────────────────┘
```

### API Gateway & Authentication

**OAuth2 Flow with Banking Twist:**
1. Partner redirects customer to bank's consent page
2. Customer authenticates with bank (MFA required)
3. Bank shows granular consent screen: "Allow Plaid to access your checking account balance and last 90 days of transactions?"
4. Bank issues scoped access token with embedded consent ID
5. Token includes: partner_id, customer_id, consent_id, scopes, expiry

**Scope Examples:**
- `accounts:read` - Account numbers, types, balances
- `transactions:read:90d` - Transaction history (90-day limit)
- `identity:read` - Customer name, address, phone
- `payments:initiate` - ACH/wire transfer capabilities

### Usage Metering & Billing

**Real-time Metering:**
Every API call hits a Redis cluster that tracks:
```
Key: partner_123:customer_456:2024-01-15
Value: {account_reads: 45, transaction_reads: 12, payment_initiations: 2}
```

**Async Billing Pipeline:**
- Hourly jobs aggregate Redis counters into PostgreSQL
- Daily jobs calculate partner bills based on contract terms
- Monthly invoicing with detailed usage breakdowns

**Why Not Pure Real-time Billing?**
- Latency: Adding billing logic to every API call adds 50-100ms
- Complexity: Contract calculations (volume discounts, SLA credits) are expensive
- Reliability: Billing failures shouldn't block customer transactions

### Entitlement Management

**Contract Enforcement Engine:**
Before each API call, check:
1. Partner has valid contract for this API
2. Customer consent covers requested data scope
3. Partner hasn't exceeded rate limits or quotas
4. No active security flags on partner account

**Example Decision Matrix:**
```
Partner: Plaid (Enterprise Tier)
Customer: John Doe (Consent ID: abc123)
Request: GET /accounts/transactions?days=30

Checks:
✓ Contract allows transaction API
✓ Consent abc123 includes transactions:read:90d
✓ Rate limit: 47/50 calls this minute
✓ Monthly quota: 89,432/100,000 calls
✓ No security flags
→ ALLOW
```

### Partner Onboarding

**Sandbox Environment:**
- Synthetic customer data (realistic but fake)
- All APIs available with 100ms artificial latency
- Rate limits enforced but no billing
- Detailed logging for debugging integration issues

**Production Promotion Checklist:**
- Security review of partner application
- Penetration testing of integration
- Business agreement signed with liability terms
- Technical integration tested with real (consented) customer data
- Monitoring and alerting configured

## 3. Consent and Compliance

### Consent Capture & Storage

**Consent Database Schema (Simplified):**
```
consent_records:
- consent_id (UUID)
- customer_id 
- partner_id
- granted_scopes (JSON array)
- granted_at (timestamp)
- expires_at (timestamp)
- revoked_at (nullable timestamp)
- legal_basis (explicit_consent, legitimate_interest)
```

**Granular Consent UI:**
Instead of "Allow access to your account," banks show:
- ✓ Account balances (required for this service)
- ✓ Transaction history - last 90 days (required)
- ✗ Investment account data (optional - uncheck to deny)
- ✗ Loan information (optional)

### Consent Revocation

**What Happens When Customer Revokes:**
1. Customer clicks "Revoke Access" in bank's privacy dashboard
2. Consent record updated: `revoked_at = NOW()`
3. All active access tokens for that consent immediately invalidated
4. Partner's next API call returns `403 Consent Revoked`
5. Partner must handle graceful degradation of their service

**Race Condition Handling:**
- API calls in-flight when consent revoked still complete (avoid partial state)
- Token validation includes microsecond-precision revocation checks
- Partner gets webhook notification of revocation (eventual consistency)

### Audit & Compliance

**SOX Requirements:**
- Immutable audit logs of all data access
- Quarterly access reviews: "Why did Plaid access John's account 1,247 times?"
- Segregation of duties: Different teams manage consent vs. billing

**GDPR Compliance:**
- Right to be forgotten: Customer can request complete data deletion
- Data portability: Customer can export their consent history
- Breach notification: Partners notified within 72 hours of security incidents

**Audit Log Example:**
```
{
  "timestamp": "2024-01-15T14:30:22.123Z",
  "partner_id": "plaid_prod",
  "customer_id": "cust_789",
  "consent_id": "consent_abc123",
  "api_endpoint": "/v1/accounts/transactions",
  "response_code": 200,
  "data_elements_accessed": ["transaction_amount", "merchant_name", "transaction_date"],
  "ip_address": "203.0.113.42",
  "user_agent": "PlaidAPI/2.1.0"
}
```

## 4. System Design Considerations

### High-Scale Traffic Patterns

**Aggregator Traffic Characteristics:**
- Morning spikes: 8-10 AM (users checking balances)
- End-of-month surges: Account reconciliation
- Plaid alone: 50M+ API calls per day for large banks
- Bursty patterns: Partner batch jobs can hit 1000 RPS suddenly

**Caching Strategy:**
```
┌─────────────┐    ┌─────────────┐    ┌─────────────┐
│   Partner   │───▶│ Redis Cache │───▶│ Core System │
│   Request   │    │ (30s TTL)   │    │ (Mainframe) │
└─────────────┘    └─────────────┘    └─────────────┘
```

**Cache Key Strategy:**
- Account balance: `balance:acct_123` (TTL: 30 seconds)
- Transaction history: `txns:acct_123:2024-01-15` (TTL: 24 hours)
- Customer profile: `profile:cust_456` (TTL: 1 hour)

### Rate Limiting & Abuse Detection

**Multi-Layer Rate Limiting:**
1. **Global Partner Limit:** 1000 RPS across all customers
2. **Per-Customer Limit:** 10 RPS per customer (prevent account enumeration)
3. **API-Specific Limits:** Payment initiation limited to 1 per minute

**Abuse Detection Patterns:**
- Sequential account number scanning
- Unusual geographic access patterns
- Sudden traffic spikes (10x normal volume)
- High error rates (potential credential stuffing)

### Data Freshness Trade-offs

**Real-time vs. Cached Data:**
- **Account Balance:** Real-time (customers expect accuracy)
- **Transaction History:** 30-second cache (acceptable for most use cases)
- **Customer Profile:** 1-hour cache (rarely changes)

**Consistency Challenges:**
- Customer makes purchase → balance changes → cached balance now stale
- Solution: Cache invalidation on transaction posting (complex but necessary)

## 5. Failure Scenarios

### Partner Exceeds Quota

**Soft Limit Approach:**
1. Partner hits 90% of monthly quota → Warning email sent
2. Partner hits 100% → API calls return `429 Quota Exceeded`
3. Partner can purchase additional quota or wait for monthly reset

**Hard Limit Enforcement:**
```
if (partner.monthly_usage >= partner.quota_limit) {
    return HTTP_429("Monthly quota exceeded. Upgrade plan or wait for reset.");
}
```

### Billing System Downtime

**Graceful Degradation Strategy:**
1. API calls continue to work (customer experience preserved)
2. Usage data queued in Redis with extended TTL
3. Billing reconciliation runs when system recovers
4. Partners notified of potential billing delays

**Financial Risk Mitigation:**
- Emergency rate limits activated during billing outages
- Maximum 24-hour usage buffer before API shutdown
- Partner contracts include force majeure clauses

### Consent Revocation Race Conditions

**Scenario:** Customer revokes consent while partner is mid-batch-processing their data.

**Solution - Graceful Handling:**
1. In-flight API calls complete with current data
2. New API calls immediately fail with `403 Consent Revoked`
3. Partner receives webhook: `{"event": "consent.revoked", "consent_id": "abc123"}`
4. Partner must stop processing and delete cached data

## 6. Security Mindset

### Least Privilege Implementation

**Token Scoping Example:**
Instead of broad access, tokens are laser-focused:
```
Token for "Budget App":
- Scopes: ["accounts:read", "transactions:read:30d"]
- Excludes: Investment accounts, loan data, payment capabilities

Token for "Payment App":
- Scopes: ["accounts:read", "payments:initiate:ach"]
- Excludes: Transaction history, investment data
```

### Default-Deny Architecture

**API Gateway Decision Flow:**
```
1. Valid token? → NO → 401 Unauthorized
2. Token expired? → YES → 401 Token Expired  
3. Consent active? → NO → 403 Consent Revoked
4. Scope covers request? → NO → 403 Insufficient Scope
5. Rate limit OK? → NO → 429 Rate Limited
6. All checks pass → ALLOW
```

### Data Exfiltration Prevention

**Monitoring Patterns:**
- Partner downloading full transaction history for all customers (unusual)
- API calls from unexpected IP ranges
- Bulk data requests outside normal business hours
- Partners sharing tokens across different applications

**Technical Controls:**
- API responses limited to 1000 records per call (pagination required)
- Watermarking: Subtle identifiers in API responses to trace data leaks
- Honeypot accounts: Fake customer data that triggers alerts if accessed

## Key Trade-offs for System Design Interviews

1. **Latency vs. Accuracy:** Real-time data is slower but more accurate
2. **Security vs. Usability:** More security checks add latency
3. **Consistency vs. Availability:** Strong consistency can impact uptime
4. **Cost vs. Performance:** Caching reduces load but increases complexity
5. **Compliance vs. Speed:** Audit logging adds overhead to every request

The key insight: Banking data monetization isn't just about APIs—it's about building a regulated, auditable, profitable business on top of sensitive financial data while maintaining customer trust and regulatory compliance.