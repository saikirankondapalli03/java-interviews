package graphql.repository;

import graphql.model.Author;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Repository
public class AuthorRepository {

    private final Map<String, Author> store = new ConcurrentHashMap<>();
    private final AtomicLong idGen = new AtomicLong(1);

    public Author save(Author author) {
        if (author.getId() == null || author.getId().isEmpty()) {
            author.setId("author-" + idGen.getAndIncrement());
        }
        store.put(author.getId(), author);
        return author;
    }

    public Optional<Author> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }

    public List<Author> findAll() {
        return store.values().stream().collect(Collectors.toList());
    }

    /** Batch load authors by IDs—used by DataLoader to avoid N+1. */
    public List<Author> findByIdIn(List<String> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        return ids.stream()
                .distinct()
                .map(store::get)
                .filter(a -> a != null)
                .collect(Collectors.toList());
    }
}
