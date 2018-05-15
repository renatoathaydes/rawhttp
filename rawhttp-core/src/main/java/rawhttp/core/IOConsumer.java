package rawhttp.core;

import java.io.IOException;

/**
 * A Consumer that can throw {@link IOException}.
 *
 * @param <T> the argument type
 */
@FunctionalInterface
public interface IOConsumer<T> {
    void accept(T t) throws IOException;
}
