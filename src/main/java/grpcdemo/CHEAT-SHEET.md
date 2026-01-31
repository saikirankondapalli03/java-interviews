# gRPC – Interview Cheat Sheet & Q&A

## 1. What is gRPC?

- **Definition:** A high-performance RPC (Remote Procedure Call) framework. You define services and messages in **Protocol Buffers (proto)**; gRPC generates client and server code and runs over **HTTP/2**.
- **Who:** Open source (Google); supports many languages (Java, Go, Python, etc.).
- **Key idea:** Strongly typed contract (`.proto`), binary serialization (small and fast), one RPC = one method call (e.g. `GetBook(request)` returns `GetBookResponse`).

## 2. gRPC vs REST (Classic Interview Question)

| Aspect | REST | gRPC |
|--------|------|------|
| Contract | Often OpenAPI/Swagger or informal | Explicit: `.proto` (messages + services) |
| Payload | JSON (or XML) | Binary (Protocol Buffers) by default |
| Transport | HTTP/1.1 or HTTP/2 | HTTP/2 |
| Streaming | Typically request/response | Native: unary, server stream, client stream, bidirectional |
| Use case | Public APIs, web/mobile, caching | Internal services, microservices, low latency |

**When to use gRPC:** Service-to-service, polyglot teams, need for streaming or performance.  
**When REST is better:** Browser clients, public APIs, simple CRUD where JSON and HTTP caching matter.

## 3. Core Concepts

- **Protocol Buffers (proto):** Schema language. You define **messages** (like DTOs) and **services** (method names, request/response types). `protoc` compiles them to Java (or other) classes.
- **Service:** Set of RPCs. Each RPC has one request and one response type (for unary).
- **Unary RPC:** One request → one response (like a normal HTTP POST).
- **Streaming RPC:** Server stream (one request → many responses), client stream (many requests → one response), or bidirectional (many ↔ many).
- **Channel:** Connection to a gRPC server. Client uses a **stub** (blocking or async) to call methods on the channel.

## 4. Proto Syntax (Quick Reference)

```protobuf
syntax = "proto3";

option java_package = "grpc";
option java_multiple_files = true;

message Book {
  string id = 1;
  string title = 2;
  int32 page_count = 3;
  string author_id = 4;
}

message GetBookRequest { string id = 1; }
message GetBookResponse { Book book = 1; }

service Bookstore {
  rpc GetBook(GetBookRequest) returns (GetBookResponse);
  rpc ListBooks(ListBooksRequest) returns (ListBooksResponse);
}
```

- **Field numbers** (1, 2, 3…) are part of the wire format; don’t change them for existing fields.
- **java_package** / **java_multiple_files** control generated Java.

## 5. Java / This Project

- **Generated:** `grpc.Book`, `grpc.BookstoreGrpc.BookstoreImplBase`, `BookstoreGrpc.newBlockingStub(channel)`.
- **Server:** Extend `BookstoreImplBase`, override each RPC (e.g. `getBook`), use `StreamObserver` to send response and call `onCompleted()`.
- **Client:** Build a `ManagedChannel`, create a blocking stub with `BookstoreGrpc.newBlockingStub(channel)`, then call e.g. `stub.getBook(GetBookRequest.newBuilder().setId("1").build())`.
- **Build:** `mvn generate-sources` runs protobuf + gRPC plugins; generated code lives under `target/generated-sources/protobuf/`.

## 6. Interview Q&A (Short Answers)

**Q: What is the difference between gRPC and REST?**  
A: gRPC uses a strict contract (proto), binary serialization, and HTTP/2; it’s well-suited for internal services and streaming. REST is often JSON over HTTP, good for public APIs and caching.

**Q: What are Protocol Buffers?**  
A: A schema and serialization format. You define messages and services in `.proto`; the compiler generates types and serialization code. Binary, compact, and backward-compatible when you follow rules (e.g. don’t reuse field numbers).

**Q: What types of RPC does gRPC support?**  
A: Unary (1 request, 1 response), server streaming (1 request, N responses), client streaming (N requests, 1 response), and bidirectional streaming (N requests, N responses).

**Q: Why HTTP/2?**  
A: Multiplexing (many requests over one connection), binary framing, and flow control; fits RPC and streaming well.

**Q: How do you do error handling in gRPC?**  
A: Use **status codes** and **metadata**. Return `responseObserver.onError(Status.INVALID_ARGUMENT.withDescription("...").asException())` or use `StatusRuntimeException`. Client checks `Status` from the exception.

**Q: How do you secure gRPC?**  
A: TLS for transport; authentication via metadata (e.g. JWT in headers). Interceptors on server/client for auth and logging.

**Q: What is a stub?**  
A: Client-side object that looks like the service interface. You call methods on the stub (e.g. `stub.getBook(...)`); the stub serializes the request, sends it over the channel, and deserializes the response.

**Q: Can browsers call gRPC directly?**  
A: Not typically. Browsers don’t expose low-level HTTP/2 gRPC APIs. Use gRPC-Web (proxy) or a REST gateway in front of gRPC for browser clients.

## 7. Streaming (Mental Model)

- **Unary:** Request → Response (what this project uses).
- **Server stream:** Request → Response1, Response2, … (e.g. live updates, pagination).
- **Client stream:** Request1, Request2, … → Response (e.g. upload batch).
- **Bidirectional:** Request/Response streams in both directions (e.g. chat).

---

## 8. Resume Problem Statement + Why gRPC Was Used

**Problem (from resume – Fidelity, Bond Beacon):**  
*“Led migration from **Tibco EMS** to **Apache Kafka**; refactored **order processing** (Limit/Market/Bid Wanted Orders) supporting the **FIX protocol**.”*

**Why gRPC was used (in that context):**

- **Service-to-service, low latency:** Order-processing systems have multiple internal services (e.g. order ingestion, validation, routing, execution). These services need to talk to each other with minimal latency and a clear contract. gRPC’s binary Protocol Buffers and HTTP/2 multiplexing reduce payload size and round-trips compared to REST/JSON.
- **Strongly typed contract:** The FIX protocol and order types (Limit/Market/Bid Wanted) map well to a `.proto` schema. Generated client and server code enforce the contract at compile time and avoid schema drift between services.
- **Polyglot and streaming:** With Kafka in the mix, some components may be in Java, others in Go or Python. gRPC has first-class support across languages from a single `.proto`. If we need to stream order updates (e.g. server streaming status changes), gRPC supports it natively.
- **Internal only:** These calls are between backend services on EKS or the same network, not from browsers. So we don’t need REST for caching or browser compatibility; gRPC is a better fit for internal microservice communication.

So in this kind of architecture, **gRPC was used for internal order-processing service calls** to get a strict contract, lower latency, and efficient binary serialization. For interview: “On the Bond Beacon order-processing migration we used gRPC for service-to-service communication between order services—strong typing from proto, HTTP/2 for performance, and a good fit for our internal microservices alongside Kafka.”

---

Use this cheat sheet for quick recall before an interview; pair it with running the server and client and adding one new RPC to reinforce the flow.
