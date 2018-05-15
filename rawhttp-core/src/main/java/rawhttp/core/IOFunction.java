package rawhttp.core;


import java.io.IOException;

/**
 * Function that can throw {@link IOException}.
 *
 * @param <T> the argument type
 * @param <R> the return type
 */
@FunctionalInterface
public interface IOFunction<T, R> {

    /**
     * Applies this function to the given argument.
     *
     * @param t the function argument
     * @return the function result
     */
    R apply(T t) throws IOException;
}
