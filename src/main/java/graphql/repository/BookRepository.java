package graphql.repository;

import graphql.model.Book;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Repository
public class BookRepository {

    private final Map<String, Book> store = new ConcurrentHashMap<>();
    private final AtomicLong idGen = new AtomicLong(1);

    public Book save(Book book) {
        if (book.getId() == null || book.getId().isEmpty()) {
            book.setId("book-" + idGen.getAndIncrement());
        }
        store.put(book.getId(), book);
        return book;
    }

    public Optional<Book> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }

    public List<Book> findAll() {
        return store.values().stream().collect(Collectors.toList());
    }

    public List<Book> findByAuthorId(String authorId) {
        return store.values().stream()
                .filter(b -> authorId.equals(b.getAuthorId()))
                .collect(Collectors.toList());
    }
}
