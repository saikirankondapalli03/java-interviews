package graphql.model;

/**
 * Domain model for Book. Maps to GraphQL type "Book".
 * Used by schema and resolvers.
 */
public class Book {

    private String id;
    private String title;
    private Integer pageCount;
    private String authorId;

    public Book() {}

    public Book(String id, String title, Integer pageCount, String authorId) {
        this.id = id;
        this.title = title;
        this.pageCount = pageCount;
        this.authorId = authorId;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public Integer getPageCount() { return pageCount; }
    public void setPageCount(Integer pageCount) { this.pageCount = pageCount; }
    public String getAuthorId() { return authorId; }
    public void setAuthorId(String authorId) { this.authorId = authorId; }
}
