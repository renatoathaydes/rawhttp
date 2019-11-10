package rawhttp.core;


import java.io.IOException;

/**
 * Supplier that can throw {@link IOException}.
 *
 * @param <T> the return type
 */
@FunctionalInterface
public interface IOSupplier<T> {

    /**
     * Gets a result.
     *
     * @return the result
     * @throws IOException if something goes wrong
     */
    T get() throws IOException;
}
