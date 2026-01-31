# Where are the models?

In gRPC, **models are not hand-written** in this folder. They are **generated** from the `.proto` file.

## Generated models (package `grpc`)

After you run `mvn generate-sources`, these types are created under  
`target/generated-sources/protobuf/java/grpc/`:

| Type | Description |
|------|-------------|
| `grpc.Book` | Book message (id, title, pageCount, authorId) |
| `grpc.Author` | Author message (id, name) |
| `grpc.GetBookRequest` / `grpc.GetBookResponse` | GetBook RPC request/response |
| `grpc.ListBooksRequest` / `grpc.ListBooksResponse` | ListBooks RPC request/response |
| `grpc.CreateBookRequest` / `grpc.CreateBookResponse` | CreateBook RPC request/response |
| `grpc.ListAuthorsRequest` / `grpc.ListAuthorsResponse` | ListAuthors RPC request/response |
| `grpc.CreateAuthorRequest` / `grpc.CreateAuthorResponse` | CreateAuthor RPC request/response |

Your code in `grpcdemo` uses them via `import grpc.*` or `import grpc.Book`, etc.  
Source of truth: `src/main/proto/bookstore.proto`.

## Compared to GraphQL

In the GraphQL module we have hand-written `graphql.model.Book` and `graphql.model.Author` because the schema is just a contract; in gRPC the proto is compiled into Java classes, so **the generated classes are the models** and you don’t duplicate them here.
