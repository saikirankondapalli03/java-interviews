package grpcdemo;

import grpc.Author;
import grpc.Book;
import grpc.BookstoreGrpc;
import grpc.CreateAuthorRequest;
import grpc.CreateAuthorResponse;
import grpc.CreateBookRequest;
import grpc.CreateBookResponse;
import grpc.GetBookRequest;
import grpc.GetBookResponse;
import grpc.ListAuthorsRequest;
import grpc.ListAuthorsResponse;
import grpc.ListBooksRequest;
import grpc.ListBooksResponse;
import io.grpc.stub.StreamObserver;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * gRPC service implementation. Extends the generated BookstoreGrpc.BookstoreImplBase.
 * Handles GetBook, ListBooks, CreateBook, ListAuthors, CreateAuthor (unary RPCs).
 */
public class BookstoreServiceImpl extends BookstoreGrpc.BookstoreImplBase {

    private final Map<String, Book> books = new ConcurrentHashMap<>();
    private final Map<String, Author> authors = new ConcurrentHashMap<>();
    private final AtomicLong bookIdGen = new AtomicLong(1);
    private final AtomicLong authorIdGen = new AtomicLong(1);

    @Override
    public void getBook(GetBookRequest request, StreamObserver<GetBookResponse> responseObserver) {
        Book book = books.get(request.getId());
        responseObserver.onNext(GetBookResponse.newBuilder().setBook(book != null ? book : Book.getDefaultInstance()).build());
        responseObserver.onCompleted();
    }

    @Override
    public void listBooks(ListBooksRequest request, StreamObserver<ListBooksResponse> responseObserver) {
        List<Book> list = List.copyOf(books.values());
        responseObserver.onNext(ListBooksResponse.newBuilder().addAllBooks(list).build());
        responseObserver.onCompleted();
    }

    @Override
    public void createBook(CreateBookRequest request, StreamObserver<CreateBookResponse> responseObserver) {
        String id = "book-" + bookIdGen.getAndIncrement();
        Book book = Book.newBuilder()
                .setId(id)
                .setTitle(request.getTitle())
                .setPageCount(request.getPageCount())
                .setAuthorId(request.getAuthorId())
                .build();
        books.put(id, book);
        responseObserver.onNext(CreateBookResponse.newBuilder().setBook(book).build());
        responseObserver.onCompleted();
    }

    @Override
    public void listAuthors(ListAuthorsRequest request, StreamObserver<ListAuthorsResponse> responseObserver) {
        List<Author> list = List.copyOf(authors.values());
        responseObserver.onNext(ListAuthorsResponse.newBuilder().addAllAuthors(list).build());
        responseObserver.onCompleted();
    }

    @Override
    public void createAuthor(CreateAuthorRequest request, StreamObserver<CreateAuthorResponse> responseObserver) {
        String id = "author-" + authorIdGen.getAndIncrement();
        Author author = Author.newBuilder().setId(id).setName(request.getName()).build();
        authors.put(id, author);
        responseObserver.onNext(CreateAuthorResponse.newBuilder().setAuthor(author).build());
        responseObserver.onCompleted();
    }
}
