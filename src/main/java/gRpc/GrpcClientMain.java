package gRpc;

import grpc.*;
import io.grpc.ManagedChannel;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;

/**
 * Simple gRPC client: creates author and book, then lists books.
 * Run GrpcServerMain first, then run this class (or use grpcurl).
 */
public class GrpcClientMain {

    private static final String HOST = "localhost";
    private static final int PORT = 9090;

    public static void main(String[] args) throws InterruptedException {
        ManagedChannel channel = NettyChannelBuilder.forAddress(HOST, PORT)
                .usePlaintext()
                .build();
        try {
            BookstoreGrpc.BookstoreBlockingStub stub = BookstoreGrpc.newBlockingStub(channel);

            // Create author
            CreateAuthorResponse authorResp = stub.createAuthor(
                    CreateAuthorRequest.newBuilder().setName("Test Author").build());
            String authorId = authorResp.getAuthor().getId();
            System.out.println("Created author: " + authorId + " - " + authorResp.getAuthor().getName());

            // Create book
            CreateBookResponse bookResp = stub.createBook(
                    CreateBookRequest.newBuilder()
                            .setTitle("Test Book")
                            .setPageCount(100)
                            .setAuthorId(authorId)
                            .build());
            System.out.println("Created book: " + bookResp.getBook().getId() + " - " + bookResp.getBook().getTitle());

            // List books
            ListBooksResponse listResp = stub.listBooks(ListBooksRequest.getDefaultInstance());
            System.out.println("Books count: " + listResp.getBooksCount());
            listResp.getBooksList().forEach(b ->
                    System.out.println("  " + b.getId() + " | " + b.getTitle() + " | pages " + b.getPageCount()));
        } finally {
            channel.shutdown().awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);
        }
    }
}
