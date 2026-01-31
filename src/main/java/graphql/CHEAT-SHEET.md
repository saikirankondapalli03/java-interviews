# GraphQL – Interview Cheat Sheet & Q&A

## 1. What is GraphQL?

- **Definition:** A query language for APIs and a runtime to execute those queries with your existing data (e.g. Java services, DBs).
- **Who:** Spec by Facebook; implementations in many languages (Java: graphql-java, Spring for GraphQL).
- **Key idea:** Client sends a **query** that describes exactly the **shape** of data it wants; server returns JSON in that shape. No fixed “endpoints” like REST; one endpoint (e.g. `/graphql`), many possible queries.

## 2. GraphQL vs REST (Classic Interview Question)

| Aspect | REST | GraphQL |
|--------|------|--------|
| Endpoints | Many URLs (e.g. `/books`, `/books/1`) | Single endpoint (e.g. `/graphql`) |
| Data shape | Server-defined (often over-fetch or under-fetch) | Client-defined in the query |
| Over-fetching | Possible (e.g. GET returns full object when you need one field) | Avoided (client asks only for needed fields) |
| Under-fetching | Possible (need multiple round-trips for related data) | Avoided (one query can get book + author) |
| Versioning | Often URL or headers (v1, v2) | Add/deprecate fields in schema; no URL versioning |
| Caching | HTTP cache by URL | Harder (single URL); often use normalized caches (e.g. Apollo) |

**When to use GraphQL:** Mobile/apps needing flexible payloads, BFFs, when many clients need different views of the same data.  
**When REST is fine:** Simple CRUD, public APIs where HTTP caching is critical, teams not wanting to adopt GraphQL.

## 3. Core Concepts

- **Schema:** Defines types, `Query` (read), and `Mutation` (write). Entry point for the API.
- **Type:** Object with fields (e.g. `Book { id, title, author }`). Scalars: `ID`, `String`, `Int`, `Float`, `Boolean`.
- **Query:** Read operation; corresponds to `Query` type in schema (e.g. `bookById(id: ID!): Book`).
- **Mutation:** Write operation; corresponds to `Mutation` type (e.g. `addBook(...): Book`).
- **Resolver:** Code that returns data for a field. “Query resolver” for root fields; “field resolver” for fields on types (e.g. `Book.author`).
- **N+1:** If each `Book` triggers a separate call to resolve `author`, you get N+1 queries; fix with batching/dataloader (mentioned in interviews).

## 4. Schema Syntax (Quick Reference)

```graphql
# Object type
type Book {
  id: ID!
  title: String!
  pageCount: Int
  author: Author
}

# Query (read)
type Query {
  bookById(id: ID!): Book
  allBooks: [Book!]!
}

# Mutation (write)
type Mutation {
  addBook(title: String!, authorId: ID!): Book!
}

# ! = non-null; [X] = list of X
```

## 5. Java / Spring for GraphQL (This Project)

- **Schema:** `.graphqls` files in `src/main/resources/graphql/` (classpath).
- **Resolvers:** Controllers with:
  - `@QueryMapping` — root `Query` fields.
  - `@MutationMapping` — root `Mutation` fields.
  - `@SchemaMapping(typeName = "Book", field = "author")` — field on type `Book`.
- **Arguments:** `@Argument String id` etc. Method name can match field name (e.g. `bookById(@Argument String id)`).

## 6. Interview Q&A (Short Answers)

**Q: What is over-fetching and how does GraphQL help?**  
A: Over-fetching is when the API returns more data than the client needs. In GraphQL the client requests only the fields it needs, so the response is minimal.

**Q: What is under-fetching?**  
A: When one REST call doesn’t give enough data and the client must make multiple calls. In GraphQL one query can request related data (e.g. book + author) in a single round-trip.

**Q: Does GraphQL replace REST?**  
A: No. It’s an alternative. REST is still widely used; GraphQL fits well for flexible, client-driven APIs and BFFs.

**Q: How do you secure a GraphQL API?**  
A: Same ideas as REST: authentication (e.g. JWT), authorization (check permissions per field or type), rate limiting, and validating query depth/complexity to prevent abuse.

**Q: What is the N+1 problem in GraphQL?**  
A: When resolving a list (e.g. 100 books), if each book’s `author` is resolved with a separate call, you get 1 + 100 calls. Fix: batching or DataLoader to batch loads by key.

**Q: What are subscriptions?**  
A: GraphQL feature for real-time updates (e.g. WebSocket). Client subscribes; server pushes events. Not all implementations support it; Spring for GraphQL can work with RSocket/WebSocket.

**Q: Can you version a GraphQL API?**  
A: Prefer evolving the schema: add new fields, deprecate old ones with `@deprecated`, and avoid breaking changes. No URL versioning like REST v1/v2.

## 7. Wiz-style Queries (Mental Model)

If you’ve seen “Wiz queries,” they’re usually GraphQL queries against a security/cloud API: you request specific entities and relationships (e.g. findings, assets, policies) in one request. Same idea as this project: define types and relationships in the schema, then query exactly what you need. Saying “I’ve written/run GraphQL queries and built a small API with schema and resolvers” is accurate after this prep.

---

## 8. Resume Problem Statement + Why GraphQL Was Used

**Problem (from resume – TIAA, Cybersecurity API Engineering):**  
*“Built security governance platform using Python ETL pipelines (aggregating **Wiz, Snyk** data); developed **Angular** dashboard for displaying summary of **security posture visibility** for all applications.”*

**Why GraphQL was used (in that context):**

- **Wiz exposes a GraphQL API.** To build the governance platform we had to pull security data (findings, assets, policies, compliance posture) from Wiz. Wiz’s API is GraphQL-based, so the ETL/backend queries Wiz via GraphQL.
- **Right-sized payloads:** The dashboard needed specific entities and relationships (e.g. findings by severity, assets by type, policy status). With GraphQL we request exactly those fields and nested relations in one query instead of multiple REST calls or large fixed responses.
- **Single round-trip for related data:** One query can ask for “findings + their severity + affected resources” in the shape the dashboard needs, avoiding under-fetching and extra round-trips.
- **Evolving needs:** As we added more widgets or filters to the Angular dashboard, we could extend the GraphQL query (new fields or arguments) without new REST endpoints.

So in this project, **GraphQL was used because the upstream provider (Wiz) offers GraphQL**, and it fit the need to aggregate and shape security data efficiently for a unified visibility dashboard. For interview: “On the security governance platform we queried Wiz’s GraphQL API to pull exactly the security posture data we needed for the dashboard, in one request per view, and fed that into our ETL and Angular front end.”

---

## 9. Is This Enough for the Interview?

**Short answer: Yes, for most backend/Java interviews** where GraphQL is one of several topics.

**You’re in good shape for:**
- Defining GraphQL and when to use it vs REST
- Explaining over-fetching, under-fetching, schema, resolvers, Query vs Mutation
- Describing what you built: schema + Spring for GraphQL + `@QueryMapping` / `@MutationMapping` / `@SchemaMapping` (field resolvers)
- Answering N+1 (what it is, fix: batching/DataLoader), security (auth, depth/complexity limits), versioning, subscriptions (concept only)

**Optional deep-dive if they push:**
- **DataLoader:** “We’d batch author loads by ID so 100 books don’t trigger 100 DB calls; Spring for GraphQL supports DataLoader.” No need to implement unless the role is very GraphQL-heavy.
- **Query complexity / depth:** “We’d limit depth or complexity to avoid expensive queries; libraries support this.”

**If the JD is “GraphQL engineer” or “GraphQL-first”:** Add one small DataLoader example or mention you’ve read the Spring for GraphQL docs on batching. For typical backend roles, this project + the cheat sheet is sufficient.

---

Use this cheat sheet for quick recall before an interview; pair it with running this project and modifying one query or one resolver to reinforce the concepts.
