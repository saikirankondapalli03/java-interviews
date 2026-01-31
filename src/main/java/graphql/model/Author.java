package graphql.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Domain model for Author. Maps to GraphQL type "Author".
 * Used by schema and resolvers.
 */
public class Author {

    private String id;
    private String name;
    private List<Book> books;  // populated by field resolver when client asks for it

    public Author() {
        this.books = new ArrayList<>();
    }

    public Author(String id, String name) {
        this.id = id;
        this.name = name;
        this.books = new ArrayList<>();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public List<Book> getBooks() { return books; }
    public void setBooks(List<Book> books) { this.books = books; }
}
