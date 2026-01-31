package graphql;

import graphql.model.Author;
import graphql.model.Book;
import graphql.repository.AuthorRepository;
import graphql.repository.BookRepository;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

/**
 * Spring Boot app that exposes GraphQL at POST /graphql.
 * Run: mvn spring-boot:run -Dspring-boot.run.arguments=--spring.main.web-application-type=servlet
 * Or run this class from IDE, then use GraphiQL at http://localhost:8080/graphql (if enabled).
 */
@SpringBootApplication
@ComponentScan(basePackages = "graphql")
public class GraphQLApplication {

    public static void main(String[] args) {
        SpringApplication.run(GraphQLApplication.class, args);
    }

    @Bean
    public ApplicationRunner seedData(BookRepository bookRepo, AuthorRepository authorRepo) {
        return args -> {
            Author a1 = authorRepo.save(new Author(null, "J.K. Rowling"));
            Author a2 = authorRepo.save(new Author(null, "George Orwell"));
            bookRepo.save(new Book(null, "Harry Potter and the Philosopher's Stone", 223, a1.getId()));
            bookRepo.save(new Book(null, "1984", 328, a2.getId()));
        };
    }
}
