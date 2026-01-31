# GraphQL Interview Prep – This Project

## Goal

After going through this module you will be able to:

- Explain what GraphQL is, when to use it vs REST, and core concepts (schema, query, mutation, resolvers).
- Read and write a simple schema and implement it in Java with Spring for GraphQL.
- Answer common interview questions and run live queries against this app.

## What’s in This Repo

| Location | Purpose |
|----------|--------|
| `src/main/java/graphql/` | All GraphQL-related **Java** code (models, repos, controller, main). |
| `src/main/resources/graphql/schema.graphqls` | Schema (types, Query, Mutation). Spring loads `.graphqls` from classpath `graphql/`. |
| `graphql/README.md` (this file) | How to run and what to practice. |
| `graphql/CHEAT-SHEET.md` | Concepts + interview Q&A. |

## How to Run

1. **From project root:**
   ```bash
   mvn spring-boot:run -Dspring-boot.run.mainClass=graphql.GraphQLApplication
   ```
2. **From IDE:** Run `graphql.GraphQLApplication` as a Java application.

Then:

- **GraphQL endpoint:** `http://localhost:8080/graphql` (POST with JSON body).
- **GraphiQL UI (if enabled):** `http://localhost:8080/graphql` — use the browser UI to type queries and see docs.

## Sample Queries to Try

**Query – get one book and its author:**
```graphql
query {
  bookById(id: "book-1") {
    id
    title
    pageCount
    author {
      id
      name
    }
  }
}
```

**Query – list all books with author:**
```graphql
query {
  allBooks {
    id
    title
    author { name }
  }
}
```

**Mutation – add author then book:**
```graphql
mutation {
  addAuthor(name: "New Author") { id name }
  addBook(title: "New Book", authorId: "author-1") { id title }
}
```

Run these in GraphiQL or via curl/Postman (POST to `/graphql` with `Content-Type: application/json` and body `{"query":"..."}`).

## How to Use This for Interviews

1. **Conceptual:** Read `CHEAT-SHEET.md` and make sure you can explain schema, resolvers, over-fetching/under-fetching, and when to use GraphQL vs REST.
2. **Hands-on:** Run the app, change the schema or add a new field, implement the resolver, and run a query. That’s enough to say “I’ve built a small GraphQL API with Spring.”
3. **Resume:** You can say you’ve implemented a GraphQL API (schema + queries/mutations + field resolvers) with Spring Boot and used it for learning and interview prep.

## Schema Location Note

- **Java:** All under `src/main/java/graphql/`.
- **Schema:** Under `src/main/resources/graphql/` so Spring for GraphQL finds it on the classpath. This is the standard convention; the “graphql files” you own are the `.graphqls` file and the Java in the `graphql` package.
