package grpcdemo.model;

import grpc.Author;
import grpc.Book;

/**
 * Reference to gRPC-generated model types. The actual "models" in this project
 * are the generated classes in package {@code grpc} from
 * {@code src/main/proto/bookstore.proto} (e.g. {@link Book}, {@link Author},
 * and all *Request/*Response types). They are produced by
 * {@code mvn generate-sources} and live under
 * target/generated-sources/protobuf/java/grpc/.
 *
 * <p>This class exists only so the model package is present and the generated
 * types are documented; use {@code grpc.Book}, {@code grpc.Author}, etc.
 * directly in your code.
 */
public final class GeneratedModelsReference {

    private GeneratedModelsReference() {}

    /** Domain entity: generated from message Book in bookstore.proto */
    @SuppressWarnings("unused")
    public static Class<Book> bookType() {
        return Book.class;
    }

    /** Domain entity: generated from message Author in bookstore.proto */
    @SuppressWarnings("unused")
    public static Class<Author> authorType() {
        return Author.class;
    }
}
