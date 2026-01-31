# gRPC Interview Prep – This Project

## Goal

After going through this module you will be able to:

- Explain what gRPC is, when to use it vs REST/GraphQL, and core concepts (proto, service, unary/streaming).
- Read and write a simple `.proto` and implement the service in Java.
- Answer common interview questions and run the server + client (or grpcurl).

## What’s in This Repo

| Location | Purpose |
|----------|--------|
| `src/main/java/gRpc/` | All gRPC-related **Java** code: server impl, client, main classes, this README, CHEAT-SHEET. |
| `src/main/proto/bookstore.proto` | Protocol buffer schema (messages + service). Maven plugin expects `.proto` under `src/main/proto`. |
| `target/generated-sources/protobuf/` | Generated Java from `.proto` (messages in `java/grpc/`, stubs in `grpc-java/grpc/`). Do not edit. |

## How to Run

1. **Generate stubs (after changing `.proto`):**
   ```bash
   mvn generate-sources
   ```

2. **Start the server:**
   ```bash
   mvn exec:java -Dexec.mainClass="gRpc.GrpcServerMain"
   ```
   Or run `gRpc.GrpcServerMain` from your IDE. Server listens on **port 9090**.

3. **Run the client:**
   In another terminal (with server running):
   ```bash
   mvn exec:java -Dexec.mainClass="gRpc.GrpcClientMain"
   ```
   Or run `gRpc.GrpcClientMain` from your IDE.

4. **Optional – grpcurl (if installed):**
   ```bash
   grpcurl -plaintext localhost:9090 list
   grpcurl -plaintext localhost:9090 grpc.Bookstore/ListBooks {}
   grpcurl -plaintext -d '{"name":"New Author"}' localhost:9090 grpc.Bookstore/CreateAuthor
   ```

## Project Layout (Concepts)

- **Proto** (`bookstore.proto`): defines `Book`, `Author`, request/response messages, and `Bookstore` service with unary RPCs (GetBook, ListBooks, CreateBook, ListAuthors, CreateAuthor).
- **Generated code**: `grpc.Book`, `grpc.Author`, `grpc.BookstoreGrpc.BookstoreImplBase` (server base), `BookstoreGrpc.newBlockingStub(channel)` (client).
- **Your code**: `gRpc.BookstoreServiceImpl` extends `BookstoreImplBase` and implements each RPC; `GrpcServerMain` builds a Netty server and registers the service; `GrpcClientMain` uses a blocking stub to call the server.

## How to Use This for Interviews

1. **Conceptual:** Read `CHEAT-SHEET.md` (gRPC vs REST, proto, unary/streaming, HTTP/2, language support).
2. **Hands-on:** Run server and client; add a new RPC in `.proto`, regenerate, implement in `BookstoreServiceImpl`, and call it from the client.
3. **Resume:** You can say you’ve implemented a gRPC service (proto + server + client) in Java for learning and interview prep.

## Proto Location Note

- **Java and docs:** All under `src/main/java/gRpc/`.
- **Proto file:** Under `src/main/proto/` so the Maven protobuf plugin finds it; generated code goes to `target/generated-sources/protobuf/`. This is the usual Maven convention.
