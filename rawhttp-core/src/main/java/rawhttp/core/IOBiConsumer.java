package rawhttp.core;

import java.io.IOException;

/**
 * A BiConsumer that can throw {@link IOException}.
 *
 * @param <T> the first argument type
 * @param <U> the second argument type
 */
@FunctionalInterface
public interface IOBiConsumer<T, U> {
    void accept(T t, U u) throws IOException;
}
