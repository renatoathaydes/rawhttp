package rawhttp.core;

import java.io.IOException;

@FunctionalInterface
interface IOBiConsumer<T, U> {
    void accept(T t, U u) throws IOException;
}
