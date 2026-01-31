package graphql.controller;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.dataloader.DataLoader;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.BatchMapping;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

import graphql.model.Author;
import graphql.model.Book;
import graphql.repository.AuthorRepository;
import graphql.repository.BookRepository;

/**
 * Spring for GraphQL controller: maps schema Query/Mutation and field resolvers.
 * - @QueryMapping: root "Query" fields (e.g. bookById, allBooks)
 * - @MutationMapping: root "Mutation" fields (e.g. addBook)
 * - @SchemaMapping: resolve a field on a type (e.g. Book.author, Author.books)
 */
@Controller
public class BookGraphQLController {

    private final BookRepository bookRepository;
    private final AuthorRepository authorRepository;

    public BookGraphQLController(BookRepository bookRepository, AuthorRepository authorRepository) {
        this.bookRepository = bookRepository;
        this.authorRepository = authorRepository;
    }

    // ---------- Query (read) ----------
    @QueryMapping
    public Book bookById(@Argument String id) {
        return bookRepository.findById(id).orElse(null);
    }

    @QueryMapping
    public List<Book> allBooks() {
        return bookRepository.findAll();
    }

    @QueryMapping
    public Author authorById(@Argument String id) {
        return authorRepository.findById(id).orElse(null);
    }

    @QueryMapping
    public List<Author> allAuthors() {
        return authorRepository.findAll();
    }

    // ---------- Mutation (write) ----------
    @MutationMapping
    public Book addBook(@Argument String title,
                        @Argument Integer pageCount,
                        @Argument String authorId) {
        Book book = new Book(null, title, pageCount, authorId);
        return bookRepository.save(book);
    }

    @MutationMapping
    public Author addAuthor(@Argument String name) {
        Author author = new Author(null, name);
        return authorRepository.save(author);
    }

    // ---------- Field resolvers (nested data) ----------
    // DataLoader fix for N+1: instead of @SchemaMapping (1 call per book), @BatchMapping
    // collects all books and loads authors in ONE batched call via DataLoader.
    // Query: allBooks { id title author { name } } → 1 call for books + 1 batched call for authors.
    @BatchMapping
    public Map<Book, Author> author(List<Book> books) {
        List<String> authorIds = books.stream()
                .map(Book::getAuthorId)
                .filter(id -> id != null)
                .distinct()
                .collect(Collectors.toList());
        List<Author> authors = authorRepository.findByIdIn(authorIds);
        Map<String, Author> byId = authors.stream()
                .collect(Collectors.toMap(Author::getId, Function.identity()));
        return books.stream()
                .collect(Collectors.toMap(Function.identity(), b ->
                        b.getAuthorId() == null ? null : byId.get(b.getAuthorId())));
    }

    // Explicit DataLoader: inject DataLoader, call dataLoader.load(authorId).
    // Batches all authorIds in the request and runs one BookRepository.findByAuthorIdIn call.
    @SchemaMapping(typeName = "Author", field = "books")
    public CompletableFuture<List<Book>> books(Author author, DataLoader<String, List<Book>> authorBooks) {
        if (author.getId() == null) return CompletableFuture.completedFuture(List.of());
        return authorBooks.load(author.getId());
    }
}
