package grpcdemo;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import io.grpc.Server;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;

/**
 * Starts a gRPC server on port 9090 exposing the Bookstore service.
 * Run this class, then run GrpcClientMain (or use grpcurl) to call the service.
 */
public class GrpcServerMain {

    private static final int PORT = 9090;
    private Server server;

    private void start() throws IOException {
        server = NettyServerBuilder.forPort(PORT)
                .addService(new BookstoreServiceImpl())
                .build()
                .start();
        System.out.println("gRPC server started on port " + PORT);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.err.println("Shutting down gRPC server...");
            try {
                if (server != null) {
                    server.shutdown().awaitTermination(5, TimeUnit.SECONDS);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                e.printStackTrace();
            }
        }));
    }

    private void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        GrpcServerMain main = new GrpcServerMain();
        main.start();
        main.blockUntilShutdown();
    }
}
