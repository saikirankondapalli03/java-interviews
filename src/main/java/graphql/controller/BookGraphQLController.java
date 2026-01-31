package graphql.controller;

import graphql.model.Author;
import graphql.model.Book;
import graphql.repository.AuthorRepository;
import graphql.repository.BookRepository;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

import java.util.List;

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
    // When client requests Book.author, this resolves Author from Book
    @SchemaMapping(typeName = "Book", field = "author")
    public Author author(Book book) {
        if (book.getAuthorId() == null) return null;
        return authorRepository.findById(book.getAuthorId()).orElse(null);
    }

    // When client requests Author.books, this resolves list of Book from Author
    @SchemaMapping(typeName = "Author", field = "books")
    public List<Book> books(Author author) {
        if (author.getId() == null) return List.of();
        return bookRepository.findByAuthorId(author.getId());
    }
}
