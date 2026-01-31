package graphql.config;

import graphql.model.Book;
import graphql.repository.BookRepository;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.execution.BatchLoaderRegistry;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Explicit DataLoader registration for N+1 fix (Author.books).
 * Demonstrates the "inject BatchLoaderRegistry, register batch function, use dataLoader.load()" pattern.
 * Example: allAuthors { id name books { title } } → 1 call for authors + 1 batched call for books.
 */
@Configuration
public class DataLoaderConfig {

    @SuppressWarnings("unchecked")
    public DataLoaderConfig(BatchLoaderRegistry registry, BookRepository bookRepository) {
        registry.forName("authorBooks")
                .registerBatchLoader((keys, env) -> {
                    List<String> authorIds = (List<String>) (List<?>) keys;
                    Map<String, List<Book>> byAuthorId = bookRepository.findByAuthorIdIn(authorIds);
                    // Return Flux<List<Book>> in same order as keys (DataLoader contract)
                    return Flux.fromIterable(
                            authorIds.stream()
                                    .map(id -> byAuthorId.getOrDefault(id, List.<Book>of()))
                                    .collect(Collectors.toList()));
                });
    }
}
